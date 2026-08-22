package tr.com.tti.hakedis.server.cikti

import org.springframework.stereotype.Service
import tr.com.tti.hakedis.domain.contract.Contract
import tr.com.tti.hakedis.domain.contract.ContractMeta
import tr.com.tti.hakedis.domain.contract.PozItem
import tr.com.tti.hakedis.domain.document.DocumentFileNamer
import tr.com.tti.hakedis.domain.document.DocumentModel
import tr.com.tti.hakedis.domain.document.DocumentModelBuilder
import tr.com.tti.hakedis.domain.document.DocumentVariant
import tr.com.tti.hakedis.domain.document.PdfLabels
import tr.com.tti.hakedis.domain.document.PdfTemplate
import tr.com.tti.hakedis.domain.pricing.PricedRow
import tr.com.tti.hakedis.domain.pricing.PricingCalculator
import tr.com.tti.hakedis.domain.workorder.Attachment
import tr.com.tti.hakedis.domain.workorder.AttachmentKind
import tr.com.tti.hakedis.domain.workorder.Region
import tr.com.tti.hakedis.domain.workorder.Scope
import tr.com.tti.hakedis.domain.workorder.WorkOrder
import tr.com.tti.hakedis.server.belge.BelgeMalzemeSorgulari
import tr.com.tti.hakedis.server.ek.EkSorgulari
import tr.com.tti.hakedis.server.ek.EkTuru
import tr.com.tti.hakedis.server.web.BulunamadiException
import java.math.BigDecimal
import java.time.ZoneId

/** Üretilmiş çıktı: indirme adı + baytlar. */
data class Cikti(val dosyaAdi: String, val icerik: ByteArray)

/**
 * Belge çıktısı üretimi — **yalnızca sunucuda** (CLAUDE.md §13: istemcide belge üretimi yasak).
 *
 * Akış her iki biçim için aynı: veritabanından çıktı modelini kur → `:core:domain`'deki ortak
 * `DocumentModelBuilder` ile modele çevir → biçime bastır → arşive **asenkron** kopyala.
 *
 * Modelin domain'de kurulması kritik: hangi satırın listeleneceği, grup başlığının korunup
 * korunmayacağı, `TEKLIF` varyantında hangi alanların boşalacağı ve toplamın nasıl hesaplanacağı
 * kararları mobil ile paylaşılan tek kaynakta yaşar; sunucu bu kararları yeniden yazmaz.
 */
@Service
class CiktiServisi(
    private val sorgular: CiktiSorgulari,
    private val ekler: EkSorgulari,
    private val malzemeler: BelgeMalzemeSorgulari,
    private val pdfUretici: PdfUretici,
    private val excelUretici: ExcelUretici,
    private val arsivAynasi: ArsivAynasi,
    private val etiketler: CiktiEtiketleri,
    private val logo: MarkaLogosu,
) {
    /**
     * [bolgeSuzgeci] `null` = süzgeç yok (yönetici ya da **oturumsuz iç çağrı**).
     *
     * HTTP uçları oturumun süzgecini geçer; otomatik arşivleme oturumsuz bir iş parçacığında koştuğu
     * için `null` geçer — yetkiyi çağrıyı doğuran `gonder`/`onayla` zaten doğrulamıştır. Süzgeç yine
     * SQL'e gidiyor (CLAUDE.md §7/3); değişen tek şey kaynağının açıkça verilmesi.
     */
    fun pdf(belgeId: Long, varyant: DocumentVariant, bolgeSuzgeci: Int?): Cikti {
        val (baslik, _, model) = hazirla(belgeId, varyant, bolgeSuzgeci)
        val icerik = pdfUretici.uret(PdfTemplate.render(model, etiketleriCevir(baslik, model)))

        return yayinla(baslik, varyant, PDF_UZANTISI, icerik)
    }

    fun excel(belgeId: Long, varyant: DocumentVariant, bolgeSuzgeci: Int?): Cikti {
        val (baslik, satirlar, model) = hazirla(belgeId, varyant, bolgeSuzgeci)
        val icerik = excelUretici.uret(baslik, satirlar, model, etiketler)

        return yayinla(baslik, varyant, XLSX_UZANTISI, icerik)
    }

    /**
     * Dosya adı **kararlıdır** (zaman damgası yok): aynı belgenin ikinci üretimi yeni bir dosya değil,
     * aynı dosyanın güncel hâlidir. Arşiv bu sayede çoğalmaz, üzerine yazılır.
     */
    private fun yayinla(baslik: CiktiBasligi, varyant: DocumentVariant, uzanti: String, icerik: ByteArray): Cikti {
        val dosyaAdi = "${DocumentFileNamer.baseName(baslik.belgeNo, varyant)}.$uzanti"
        arsivAynasi.kopyala(baslik.bolgeDosyaKodu, baslik.tur, dosyaAdi, icerik)

        return Cikti(dosyaAdi, icerik)
    }

    /** Poz kimliği → keşif belgesindeki fiyat + miktar. Keşif satırı olmayan poz map'e girmez. */
    private fun kesifSatirlari(satirlar: List<CiktiSatiri>): Map<String, PricedRow> = satirlar.mapNotNull { satir ->
        val miktar = satir.kesifMiktar ?: return@mapNotNull null
        val fiyat = satir.kesifBirimFiyat ?: return@mapNotNull null
        satir.satirKodu to PricedRow(fiyat, miktar)
    }.toMap()

    /**
     * Keşif belgesinin toplamı. Grup başlıkları map'e zaten girmiyor; hesap `PricingCalculator` ile
     * yapılır (CLAUDE.md §5 — toplama elle yazılmaz).
     */
    private fun kesifToplami(satirlar: List<CiktiSatiri>): BigDecimal? {
        val satirTutarlari = kesifSatirlari(satirlar).values.toList()
        return if (satirTutarlari.isEmpty()) null else PricingCalculator.total(satirTutarlari)
    }

    private fun hazirla(belgeId: Long, varyant: DocumentVariant, suzgec: Int?): CiktiVerisi {
        val baslik = sorgular.baslik(suzgec, belgeId)
            ?: throw BulunamadiException("Belge bulunamadı: $belgeId")
        val satirlar = sorgular.satirlar(belgeId)

        val belgeEkleri = ekler.listele(belgeId)
        val ek1 = belgeEkleri.firstOrNull { it.tur == EkTuru.EK1_ROTA }
        val ek2ler = belgeEkleri.filter { it.tur == EkTuru.EK2_GORSEL }

        val model = DocumentModelBuilder.build(
            documentNo = baslik.belgeNo,
            type = baslik.tur,
            variant = varyant,
            contract = sozlesme(baslik, satirlar),
            workOrder = isKaydi(baslik),
            quantities = satirlar.mapNotNull { satir -> satir.miktar?.let { satir.satirKodu to it } }.toMap(),
            // Hakediş çıktısı aynı işin keşif miktar/tutarını yan sütunda gösterir. Keşif tutarı
            // keşifin KENDİ dondurulmuş fiyatıyla hesaplanır; diğer türlerde bu map boş kalır.
            kesifRows = kesifSatirlari(satirlar),
            kesifTotal = kesifToplami(satirlar),
            createdAt = baslik.olusturma.atZone(ZoneId.systemDefault()).toLocalDateTime(),
            routeFile = ek1?.let { Attachment(it.id.toString(), it.dosyaAdi, it.depoYolu, it.boyutBayt, AttachmentKind.ROUTE_FILE) },
            images = ek2ler.map { Attachment(it.id.toString(), it.dosyaAdi, it.depoYolu, it.boyutBayt, AttachmentKind.IMAGE) },
            // EK3: sorgu türe bakmadan çalışır — diğer aşamalarda tablo zaten boş, çünkü yazma ucu
            // yalnızca hakedişte kabul ediyor. Türü burada bir kez daha denetlemek, kuralı ikinci bir
            // yerde yaşatmak olurdu.
            materials = malzemeler.liste(belgeId),
        )

        return CiktiVerisi(baslik, satirlar, model)
    }

    /**
     * Sözleşme, **belgenin gördüğü** hâliyle kurulur: birim fiyatlar belgenin çivilendiği versiyondan
     * (satır yazıldıysa satırın dondurduğu kopyadan) gelir. Yönetici sonradan fiyatı değiştirse bile
     * çıktı değişmez — fiyat dondurmanın görünür sonucu budur.
     */
    private fun sozlesme(baslik: CiktiBasligi, satirlar: List<CiktiSatiri>) = Contract(
        meta = ContractMeta(
            id = baslik.belgeNo,
            name = baslik.sozlesmeAdi,
            title = baslik.sozlesmeBasligi,
            sheetName = baslik.sozlesmeAdi,
        ),
        items = satirlar.map { satir ->
            PozItem(
                id = satir.satirKodu,
                pozNo = satir.pozNo,
                sortKey = satir.sortKey,
                name = satir.ad,
                unit = satir.birim,
                unitPrice = satir.birimFiyat,
                isGroup = satir.grupMu,
                isPriced = satir.fiyatliMi,
            )
        },
    )

    /** Bölge ve kapsam kodları `EnumSenkronDogrulayici` sayesinde enum'larla birebir; açılışta doğrulanır. */
    private fun isKaydi(baslik: CiktiBasligi) = WorkOrder(
        region = Region.valueOf(baslik.bolgeKodu),
        scope = Scope.valueOf(baslik.kapsamKodu),
        contractId = null,
        spNo = baslik.spNo,
        route = baslik.route,
        ekAraligi = baslik.ekAraligi,
        // Kablosuz projede üçü de boş kalır (Faz W); `DocumentModelBuilder` boş metni `null`'a
        // indiriyor ve çıktı o satırları hiç basmıyor.
        kableId = baslik.kableNosid.orEmpty(),
        adresA = baslik.adresA.orEmpty(),
        adresB = baslik.adresB,
    )

    /**
     * Şablonun ihtiyaç duyduğu metinler. Logo dosyası yoksa [MarkaLogosu] `null` döner ve şablon marka
     * adını metin olarak basar — çıktı üretimi logonun varlığına bağlı değildir.
     */
    private fun etiketleriCevir(baslik: CiktiBasligi, model: DocumentModel) = PdfLabels(
        brandName = etiketler.markaAdi,
        logoBase64 = logo.base64,
        reportTitle = "${baslik.bolgeAdi} · ${baslik.spNo}",
        typeValue = etiketler.turAdi(baslik.tur),
        regionLabel = etiketler.bolge,
        regionValue = baslik.bolgeAdi,
        scopeLabel = etiketler.kapsam,
        scopeValue = baslik.kapsamAdi,
        contractLabel = etiketler.sozlesme,
        spNoLabel = etiketler.spNo,
        routeLabel = etiketler.route,
        ekAraligiLabel = etiketler.ekAraligi,
        kableIdLabel = etiketler.kableId,
        adresALabel = etiketler.adresA,
        adresBLabel = etiketler.adresB,
        createdAtLabel = etiketler.olusturulma,
        columnPozNo = etiketler.sutunPozNo,
        columnName = etiketler.sutunAd,
        columnUnit = etiketler.sutunBirim,
        columnUnitPrice = etiketler.sutunBirimFiyat,
        columnQuantity = etiketler.sutunMiktar,
        columnAmount = etiketler.sutunTutar,
        // Etiketler `CiktiEtiketleri`'nde zaten vardı (Excel de aynılarını kullanıyor); yeni
        // yapılandırma anahtarı gerekmedi. Yalnızca hakediş şablonunda basılır.
        columnKesifQuantity = etiketler.sutunKesifMiktar,
        columnKesifAmount = etiketler.sutunKesifTutar,
        totalLabel = etiketler.toplam,
        routeFileLabel = etiketler.ek1,
        routeFileMissing = etiketler.ek1Yok,
        imagesLabel = etiketler.ek2,
        imagesValue = etiketler.ek2Metni(model.attachments.imageCount),
        materialsLabel = etiketler.ek3,
        materialName = etiketler.ek3MalzemeAdi,
        materialQuantity = etiketler.ek3Miktar,
        materialSerial = etiketler.ek3SeriNo,
    )

    private data class CiktiVerisi(val baslik: CiktiBasligi, val satirlar: List<CiktiSatiri>, val model: DocumentModel)

    private companion object {
        const val PDF_UZANTISI = "pdf"
        const val XLSX_UZANTISI = "xlsx"
    }
}
