package tr.com.tti.hakedis.server.cikti

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import tr.com.tti.hakedis.domain.document.DocumentModel
import tr.com.tti.hakedis.domain.document.DocumentModelBuilder
import tr.com.tti.hakedis.domain.document.DocumentType
import tr.com.tti.hakedis.domain.document.DocumentVariant
import tr.com.tti.hakedis.domain.workorder.Region
import tr.com.tti.hakedis.domain.workorder.Scope
import tr.com.tti.hakedis.domain.workorder.WorkOrder
import java.math.BigDecimal
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDateTime
import java.util.zip.ZipFile

/**
 * Excel çıktısının iki pazarlıksız kuralı burada kilitleniyor:
 *
 * 1. **Tutar gerçek sayı, formül değil.** Formül yazılsaydı dosyayı açan Excel sürümü yuvarlamayı kendi
 *    kuralıyla yapardı ve uygulamadaki tutar ile dosyadaki tutar ayrışabilirdi.
 * 2. **Sütun düzeni belge türüne bağlı** (Faz G kararı) ve `TEKLIF` varyantında para hücreleri boş.
 */
class ExcelUreticiTest {
    /**
     * Logo **bilerek var olmayan bir yola** bağlanıyor: bu testlerin konusu hücre içerikleri ve
     * sütun düzeni, görsel değil. `MarkaLogosu` dosyayı bulamayınca sessizce atlıyor ve üretim
     * aksamıyor — testin logo dosyasının varlığına bağlanmaması da bu sayede.
     */
    private val uretici = ExcelUretici(MarkaLogosu("/logo/testte-yok.png"))
    private val etiketler = CiktiEtiketleri()

    /**
     * `r5` bilinçli olarak **farklı** hakediş ve keşif birim fiyatı taşıyor: iki belge ayrı fiyat
     * versiyonlarına çivilenmiş olabilir (V6) ve keşif tutarının hangi fiyattan hesaplandığı test
     * edilebilir olmalı. Hakediş `500 × 4 = 2000`, keşif `648,358… × 3 = 1945,08`.
     */
    private val satirlar = listOf(
        CiktiSatiri("r4", "1", listOf(1), "HAVAİ GÜZERGAH İMALATLARI", "", grupMu = true, fiyatliMi = false, BigDecimal.ZERO, null),
        CiktiSatiri("r5", "1.1", listOf(1, 1), "Direk Dikme-montaj", "Ad.", false, true, BigDecimal("648.35852723050107"), BigDecimal("3")),
        CiktiSatiri("r6", "1.2", listOf(1, 2), "Direk Dikme-demontaj", "Ad.", false, true, BigDecimal("100"), null),
    )

    /** Hakediş senaryosu: kendi miktarı/fiyatı ayrı, keşif miktarı/fiyatı ayrı. */
    private val hakedisSatirlari = listOf(
        CiktiSatiri("r4", "1", listOf(1), "HAVAİ GÜZERGAH İMALATLARI", "", grupMu = true, fiyatliMi = false, BigDecimal.ZERO, null),
        CiktiSatiri(
            "r5", "1.1", listOf(1, 1), "Direk Dikme-montaj", "Ad.", false, true,
            birimFiyat = BigDecimal("500"), miktar = BigDecimal("4"),
            kesifMiktar = BigDecimal("3"), kesifBirimFiyat = BigDecimal("648.35852723050107"),
        ),
        CiktiSatiri("r6", "1.2", listOf(1, 2), "Direk Dikme-demontaj", "Ad.", false, true, BigDecimal("100"), null),
    )

    private fun baslik(tur: DocumentType) = CiktiBasligi(
        belgeId = 1,
        belgeNo = "IST-AVR_2026_${tur.name}_003",
        tur = tur,
        yil = 2026,
        olusturma = Instant.parse("2026-07-30T11:30:00Z"),
        bolgeKodu = "IST_AVR",
        bolgeDosyaKodu = "IST-AVR",
        bolgeAdi = "İstanbul Avrupa",
        kapsamKodu = "YENI_TESIS",
        kapsamAdi = "Yeni Tesis",
        sozlesmeAdi = "2026 Sample Rota-1",
        sozlesmeBasligi = "Rota-1: Edirne-Kırklareli",
        spNo = "SP-12345",
        route = "Rota-1",
        ekAraligi = "EK-4 / EK-5",
        kableNosid = "mtc55169a.cable*ist-ist",
        adresA = "AND Plaza",
        adresB = null,
    )

    private fun uret(tur: DocumentType, varyant: DocumentVariant = DocumentVariant.FIYATLI): ByteArray =
        uretici.uret(baslik(tur), satirlar, model(tur, varyant, satirlar), etiketler)

    private fun model(
        tur: DocumentType,
        varyant: DocumentVariant = DocumentVariant.FIYATLI,
        kaynak: List<CiktiSatiri> = satirlar,
    ): DocumentModel {
        val baslik = baslik(tur)
        return DocumentModelBuilder.build(
            documentNo = baslik.belgeNo,
            type = tur,
            variant = varyant,
            contract = sozlesme(baslik, kaynak),
            workOrder = WorkOrder(region = Region.IST_AVR, scope = Scope.YENI_TESIS, spNo = baslik.spNo),
            quantities = kaynak.mapNotNull { s -> s.miktar?.let { s.satirKodu to it } }.toMap(),
            createdAt = LocalDateTime.of(2026, 7, 30, 14, 30),
        )
    }

    /** Sayısal hücreler sayfada durur (`t="n"` + `<v>`). */
    private fun sayilar(tur: DocumentType, varyant: DocumentVariant = DocumentVariant.FIYATLI): String =
        girdi(uret(tur, varyant), "xl/worksheets/")

    /**
     * Metinler sayfada değil, **paylaşılan dize tablosunda** durur: fastexcel her metni bir kez yazıp
     * hücreye indeksini koyuyor (`t="s"`). Sıralama ilk kullanım sırasıdır, yani belge sırasıdır.
     */
    private fun metinler(tur: DocumentType, varyant: DocumentVariant = DocumentVariant.FIYATLI): String =
        kacislariCoz(girdi(uret(tur, varyant), "xl/sharedStrings"))

    /**
     * fastexcel ASCII dışı harfleri sayısal karakter başvurusu olarak yazar (`Kırklareli` →
     * `K&#x131;rklareli`). Excel bunu açarken çözer; test de aynısını yapmalı, yoksa "Türkçe karakter
     * kayboldu" gibi yanlış bir sonuca varır.
     */
    private fun kacislariCoz(xml: String): String = Regex("&#x([0-9a-fA-F]+);").replace(xml) { eslesme ->
        eslesme.groupValues[1].toInt(16).toChar().toString()
    }

    private fun sozlesme(baslik: CiktiBasligi, kaynak: List<CiktiSatiri> = satirlar) = tr.com.tti.hakedis.domain.contract.Contract(
        meta = tr.com.tti.hakedis.domain.contract.ContractMeta(
            baslik.belgeNo,
            baslik.sozlesmeAdi,
            baslik.sozlesmeBasligi,
            baslik.sozlesmeAdi,
        ),
        items = kaynak.map { satir ->
            tr.com.tti.hakedis.domain.contract.PozItem(
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

    /**
     * `.xlsx` bir zip'tir; hücre değerleri sayfanın XML'inde durur.
     *
     * `ZipInputStream` kullanılamıyor: fastexcel girdileri akış hâlinde yazdığı için boyutlar başlıkta
     * değil, veri tanımlayıcısında duruyor ve akış okuyucusu "invalid entry size" ile düşüyor. Rastgele
     * erişimli `ZipFile` bunu doğru okur, o da geçici bir dosya ister.
     */
    private fun girdi(icerik: ByteArray, onEk: String): String {
        val gecici = Files.createTempFile("belge", ".xlsx")
        return try {
            Files.write(gecici, icerik)
            ZipFile(gecici.toFile()).use { zip ->
                val girdi = zip.entries().asSequence().firstOrNull { it.name.startsWith(onEk) }
                    ?: error("Zip girdisi bulunamadı: $onEk")
                zip.getInputStream(girdi).use { it.readBytes().toString(Charsets.UTF_8) }
            }
        } finally {
            Files.deleteIfExists(gecici)
        }
    }

    @Test
    fun `tutar gercek sayi olarak yazilir - formul degil`() {
        val sayfa = sayilar(DocumentType.KESIF)

        // 3 × 648.35852723050107 → 1945.08 (satır bazında yuvarlama, CLAUDE.md §5)
        assertThat(sayfa).contains("<v>1945.08</v>")
        assertThat(sayfa).contains("t=\"n\"")
        assertThat(sayfa).doesNotContain("<f>")
    }

    @Test
    fun `ham birim fiyat hassasiyetiyle yazilir`() {
        assertThat(sayilar(DocumentType.KESIF)).contains("648.35852723050107")
    }

    @Test
    fun `TEKLIF varyantinda para hucreleri bos ama satirlar durur`() {
        val sayfa = sayilar(DocumentType.KESIF, DocumentVariant.TEKLIF)

        assertThat(sayfa).doesNotContain("1945.08")
        assertThat(sayfa).doesNotContain("648.35852723050107")
        // Miktar yerinde: firma neyi fiyatlayacağını görmeli.
        assertThat(sayfa).contains("<v>3</v>")
        assertThat(metinler(DocumentType.KESIF, DocumentVariant.TEKLIF)).contains("Direk Dikme-montaj")
    }

    @Test
    fun `sozlesmenin tum satirlari yazilir - miktari girilmemis kalem de`() {
        val metin = metinler(DocumentType.KESIF)

        assertThat(metin).contains("HAVAİ GÜZERGAH İMALATLARI")
        assertThat(metin).contains("Direk Dikme-montaj")
        // Miktarı girilmemiş kalem Excel'de durur (PDF'te durmaz) — Excel sözleşmenin aynasıdır.
        assertThat(metin).contains("Direk Dikme-demontaj")
    }

    @Test
    fun `kesif ve hakedis ayni sutun basliklarini kullanir`() {
        listOf(DocumentType.KESIF, DocumentType.HAKEDIS).forEach { tur ->
            val metin = metinler(tur)

            assertThat(metin).contains("1. Keşif Talep")
            assertThat(metin).contains("Saha Uygulama")
            assertThat(metin).contains("1.Keşif Tutar")
            assertThat(metin).contains("Kesin Hakediş Tutar")
        }
    }

    @Test
    fun `fizibilite kendi sutunlarini kullanir ve kesif sutunlarini tasimaz`() {
        val metin = metinler(DocumentType.FIZIBILITE)

        assertThat(metin).contains("Fizibilite Miktar")
        assertThat(metin).contains("Fizibilite Tutar")
        // TEKLIF varyantı aracı firmaya gidiyor: doldurulmayacak sütun başlığı hiç bulunmamalı.
        assertThat(metin).doesNotContain("Saha Uygulama")
        assertThat(metin).doesNotContain("Kesin Hakediş Tutar")
    }

    // --- Hakedişte keşif sütunları (kullanıcı isteği, 31 Temmuz 2026) -------------------------

    @Test
    fun `hakediste kesif miktari ve tutari yan sutuna yazilir`() {
        val sayfa = hakedisSayfasi()

        // Kendi tutarı: 500 × 4. Keşif tutarı: 648,358… × 3 = 1945,08.
        assertThat(sayfa).contains("<v>2000.00</v>")
        assertThat(sayfa).contains("<v>1945.08</v>")
        // Keşif miktarı (3) ile kendi miktarı (4) ayrı hücrelerde.
        assertThat(sayfa).contains("<v>3</v>")
        assertThat(sayfa).contains("<v>4</v>")
    }

    /**
     * Keşif tutarı **keşifin kendi dondurulmuş fiyatıyla** hesaplanmalı. Hakedişin fiyatı (500)
     * kullanılsaydı `500 × 3 = 1500` çıkardı ve sütun, keşif belgesinde yazandan farklı olurdu.
     */
    @Test
    fun `kesif tutari kesifin kendi fiyatiyla hesaplanir`() {
        val sayfa = hakedisSayfasi()

        assertThat(sayfa).contains("<v>1945.08</v>")
        assertThat(sayfa).doesNotContain("<v>1500.00</v>")
    }

    @Test
    fun `hakediste toplam satirinda kesif genel toplami da var`() {
        val sayfa = hakedisSayfasi()

        // Toplam satırı: kendi toplamı 2000, keşif toplamı 1945,08 — ikisi de sayısal hücre.
        assertThat(sayfa).contains("<v>1945.08</v>")
        assertThat(sayfa).contains("<v>2000.00</v>")
    }

    /**
     * "Toplam" etiketi eskiden `tutarSutunu - 1`'e, yani hakedişte tam "1.Keşif Tutar" sütununa
     * yazılıyordu. O sütun veriyle dolunca etiket keşif toplamının üzerine oturuyordu — hata
     * vermeden. Etiket artık "İş Kalemi" sütununda (indeks 1 = B).
     */
    @Test
    fun `toplam etiketi kesif tutar sutununu ezmiyor`() {
        val sayfa = hakedisSayfasi()

        val toplamSatiri = Regex("<row r=\"(\\d+)\"[^>]*>(?:(?!</row>).)*<c r=\"G\\1\"[^>]*><v>1945.08</v>", RegexOption.DOT_MATCHES_ALL)
        assertThat(toplamSatiri.containsMatchIn(sayfa)).isTrue()
    }

    @Test
    fun `TEKLIF varyantinda kesif tutari yok ama kesif miktari var`() {
        val sayfa = girdi(
            uretici.uret(
                baslik(DocumentType.HAKEDIS),
                hakedisSatirlari,
                model(DocumentType.HAKEDIS, DocumentVariant.TEKLIF, hakedisSatirlari),
                etiketler,
            ),
            "xl/worksheets/",
        )

        assertThat(sayfa).doesNotContain("<v>1945.08</v>")
        assertThat(sayfa).doesNotContain("<v>2000.00</v>")
        assertThat(sayfa).contains("<v>3</v>")
        assertThat(sayfa).contains("<v>4</v>")
    }

    /** Keşif belgesinde 4. sütun zaten kendi miktarı; keşif sütunu ikinci kez yazılmamalı. */
    @Test
    fun `kesif belgesinde kesif sutunu ayrica yazilmaz`() {
        val sayfa = girdi(
            uretici.uret(baslik(DocumentType.KESIF), hakedisSatirlari, model(DocumentType.KESIF, kaynak = hakedisSatirlari), etiketler),
            "xl/worksheets/",
        )

        // Keşif düzeninde `kesifTutarSutunu` yok → keşif fiyatından hesaplanan tutar hiç yazılmaz.
        assertThat(sayfa).doesNotContain("<v>1945.08</v>")
    }

    private fun hakedisSayfasi(): String = girdi(
        uretici.uret(
            baslik(DocumentType.HAKEDIS),
            hakedisSatirlari,
            model(DocumentType.HAKEDIS, kaynak = hakedisSatirlari),
            etiketler,
        ),
        "xl/worksheets/",
    )

    @Test
    fun `bilgi blogu tablonun ustunde`() {
        // Paylaşılan dize tablosunda sıra, ilk kullanım sırası — yani belgedeki sıradır.
        val metin = metinler(DocumentType.KESIF)

        assertThat(metin).contains("Rota-1: Edirne-Kırklareli")
        assertThat(metin.indexOf("SP-12345")).isLessThan(metin.indexOf("ISC_POZNO"))
    }
}
