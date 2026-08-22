package tr.com.tti.hakedis.server.sozlesme

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tr.com.tti.hakedis.domain.contract.Contract
import tr.com.tti.hakedis.domain.contract.PozItem
import tr.com.tti.hakedis.domain.contract.parseSortKey
import tr.com.tti.hakedis.domain.pricing.QuantityInput
import tr.com.tti.hakedis.domain.pricing.QuantityParser
import tr.com.tti.hakedis.server.web.BulunamadiException
import tr.com.tti.hakedis.server.web.GecersizIstekException
import tr.com.tti.hakedis.server.web.KuralIhlaliException
import tr.com.tti.hakedis.server.yetki.OturumKullanicisi
import tr.com.tti.hakedis.tools.ContractXlsxParser
import java.security.MessageDigest

/** Yükleme öncesi "ne olacak" cevabı — hiçbir şey yazılmaz. */
data class SozlesmeOnizleme(
    val kod: String,
    val ad: String,
    val baslik: String,
    val sayfaAdi: String,
    val sayfaAdlari: List<String>,
    val pozSayisi: Int,
    val grupSayisi: Int,
    val fiyatliSayisi: Int,
    val fiyatsizSayisi: Int,
    /**
     * Dosyadan türeyen kodda zaten bir sözleşme var mı.
     *
     * ~~Yükleme reddedilir~~ *(18 Ağustos 2026)*: çakışma artık **engel değil**, sunucu boştaki ilk
     * koda kayıyor. Alan yine de dönüyor çünkü arayüz bunu bilgi olarak gösteriyor — aynı dosyayı
     * ikinci kez yüklediğini fark etmeyen yönetici sessizce iki kayıt üretmesin.
     */
    val kodKullanimda: Boolean,
    val uyarilar: List<String>,
)

/**
 * Yükleme isteği: dosyadan türeyen değerler yönetici tarafından düzeltilebilir.
 *
 * [oncekiKod] doluysa bu bir **revizyondur**: yeni sözleşme o koda bağlanır (`onceki_id`) ve eskisi
 * pasife alınır (müdür isteği, Ağustos 2026). Geçmiş belgeler eski sözleşmede kalır.
 */
data class SozlesmeYuklemeAlanlari(val kod: String?, val ad: String?, val baslik: String?, val oncekiKod: String? = null)

/**
 * Sözleşmeye elle eklenen iş kalemi (Faz V).
 *
 * [birimFiyat] metin olarak alınır: JSON sayısına çevrilirse istemcide `double`'a düşer ve kuruş
 * kayar (CLAUDE.md §5). Ayrıştırma `QuantityParser` ile — `,` ve `.` ikisi de kabul edilir.
 *
 * [grupSatirKodu] kalemin altına gireceği grup başlığı; boşsa kalem listenin sonuna eklenir.
 */
data class PozEklemeIstegi(
    val pozNo: String?,
    val ad: String?,
    val birim: String?,
    val birimFiyat: String?,
    val grupSatirKodu: String? = null,
)

/**
 * Yöneticinin yüklediği sözleşme `.xlsx`'ini sisteme alır.
 *
 * Parse **yeniden yazılmaz**: `:tools:contract-generator`'ın [ContractXlsxParser]'ı kullanılır — aynı
 * dosya biçimini iki farklı kod yolunun okuması, birinin diğerinden sessizce ayrışması demektir
 * (CLAUDE.md §7: iş mantığı iki kez yazılamaz). Sözleşme kaynaklarının build zamanında JSON'a
 * çevrildiği eski yol da (`tools/Main.kt`) aynı parser'ı çağırıyor.
 *
 * Yükleme tek bir transaction'dır: sözleşme, poz listesi ve referans fiyat sürümü ya birlikte oluşur
 * ya hiç oluşmaz. Yarım kalmış bir sözleşme (pozu var, fiyatı yok) belge açılışını 409 ile kilitlerdi.
 */
@Service
class SozlesmeYuklemeServisi(
    private val sozlesmeler: SozlesmeSorgulari,
    private val yazici: SozlesmeYazici,
    private val fiyatYazici: FiyatVersiyonuYazici,
) {
    fun onizle(icerik: ByteArray, sayfaAdi: String?): SozlesmeOnizleme {
        val sayfalar = XlsxOkuyucu.sayfaAdlari(icerik)
        val (contract, uyarilar) = oku(icerik, sayfaAdi)

        return SozlesmeOnizleme(
            kod = contract.meta.id,
            ad = contract.meta.name,
            baslik = contract.meta.title,
            sayfaAdi = contract.meta.sheetName,
            sayfaAdlari = sayfalar,
            pozSayisi = contract.items.size,
            grupSayisi = contract.items.count { it.isGroup },
            fiyatliSayisi = contract.items.count { it.isPriced },
            fiyatsizSayisi = contract.items.count { !it.isGroup && !it.isPriced },
            kodKullanimda = sozlesmeler.varMi(contract.meta.id),
            uyarilar = uyarilar,
        )
    }

    @Transactional
    fun yukle(icerik: ByteArray, sayfaAdi: String?, dosyaAdi: String?, alanlar: SozlesmeYuklemeAlanlari): String {
        val (contract, _) = oku(icerik, sayfaAdi)

        val ad = temizle(alanlar.ad, contract.meta.name, "Sözleşme adı")
        val baslik = temizle(alanlar.baslik, contract.meta.title, "Sözleşme başlığı")

        // Kod artık kullanıcıya SORULMUYOR (18 Ağustos 2026, kullanıcı isteği): içsel bir anahtardır
        // (URL parçası + tekillik kilidi) ve yöneticinin karar vereceği bir şey değil. Verilen değer
        // ne olursa olsun slug'a indirgenir — reddedilmez; kullanıcının yazdığı ad büyük harf, `_`
        // ya da boşluk içerebilir ve bu bir hata sebebi olmamalı.
        // ~~Kalıba uymayan kod 400, çakışan kod 409~~: ikisi de yüklemeyi durduruyordu.
        val kod = benzersizKod(ContractXlsxParser.slugify(alanlar.kod ?: contract.meta.id))

        // Revizyon: düzenlenen (önceki) sözleşmeyi çöz ve yeni sözleşmeyi ona bağla. Yeni sürüm
        // **PASİF** doğar ve **eski sözleşmeye dokunulmaz** (kullanıcı kararı): revizyon yüklemek
        // çalışan sözleşmenin aktifliğini değiştirmemeli; yönetici yeni sürümü kendisi aktife alır.
        // Geçmiş belgeler zaten eski sözleşmede/sürümde kalır (CLAUDE.md §7/4).
        val oncekiKod = alanlar.oncekiKod?.trim()?.takeIf { it.isNotEmpty() }
        val oncekiId = oncekiKod?.let {
            if (it == kod) throw GecersizIstekException("Revizyon yeni bir kod gerektirir; önceki kodla aynı olamaz.")
            sozlesmeler.sozlesmeId(it) ?: throw BulunamadiException("Revize edilecek sözleşme bulunamadı: $it")
        }

        val sozlesmeId = yazici.sozlesmeOlustur(
            kod = kod,
            ad = ad,
            baslik = baslik,
            sayfaAdi = contract.meta.sheetName,
            olusturanId = OturumKullanicisi.zorunlu().id,
            kaynakDosyaAdi = dosyaAdi,
            kaynakOzet = ozet(icerik),
            oncekiId = oncekiId,
            aktif = oncekiId == null,
        )

        val pozKimlikleri = yazici.pozlariYaz(sozlesmeId, contract.items)

        val versiyonId = fiyatYazici.versiyonOlustur(
            sozlesmeId = sozlesmeId,
            ad = "$ad ilk fiyat listesi",
            aciklama = dosyaAdi?.let { "$it dosyasından yüklendi" },
            durum = FiyatVersiyonuDurumu.YAYINDA,
            sira = 1,
            olusturanId = OturumKullanicisi.zorunlu().id,
            kaynakDosyaAdi = dosyaAdi,
            kaynakOzet = ozet(icerik),
        )

        // Grup başlıkları ve fiyatlandırılmamış kalemler için satır yazılmaz; poz listesi sorguları
        // `coalesce(pf.birim_fiyat, 0)` kullandığı için bu satırlar fiyatsız görünür ve giriş kabul
        // etmez (CLAUDE.md §4/5). Fiyatı sonradan gelirse düzenleme taslağıyla eklenir.
        val fiyatlar = contract.items
            .filter { it.isPriced }
            .associate { (pozKimlikleri[it.id] ?: error("Poz kimliği bulunamadı: ${it.id}")) to it.unitPrice }
        fiyatYazici.fiyatlariYaz(versiyonId, fiyatlar)

        return kod
    }

    /**
     * Sözleşmeye yeni bir iş kalemi ekler (Faz V) ve güncel poz listesini döner.
     *
     * **Neden güvenli:** mevcut hiçbir `poz.satir_kodu` değişmiyor, hiçbir birim fiyat
     * güncellenmiyor. Dolayısıyla geçmiş belgelerin bağları da tutarları da olduğu gibi kalıyor —
     * "geçmiş belge tutarı değişmez" kararı bu tasarımla otomatik sağlanıyor, ayrı bir koruma
     * gerekmiyor.
     *
     * **Kabul edilen görünür sonuç:** yeni kalem bu sözleşmeye bağlı **tüm** belgelerde görünür.
     * Kullanıcı kararı bu yönde ("düzenlendikten sonra eskilerde de görünsün"). Tamamlanmış bir
     * belgenin Excel çıktısında bir boş satır artar; PDF (yalnızca dolu satırları listeler) ve
     * toplamlar etkilenmez.
     *
     * Fiyat sözleşmenin **yayındaki** sürümüne yazılır: belgeler o sürüme çivili ve kalem ancak
     * orada fiyatlanırsa girilebilir olur (`BelgeSorgulari.satirYaz` fiyatlılığı sürümden okuyor).
     */
    /**
     * Sözleşmeyi siler — **yalnızca hiç belge açılmamışsa**.
     *
     * Silme Faz V'de kaldırılmıştı; 18 Ağustos 2026'da **kullanımda olmayan** sözleşmelerle sınırlı
     * olarak geri geldi (kullanıcı kararı). Kaldırılma gerekçesi "poz listesini ve fiyat sürümlerini
     * birlikte götüren, geri dönüşü olmayan işlem" idi; buradaki koşul o gerekçenin can alıcı
     * kısmını ortadan kaldırıyor — belgesi olmayan bir sözleşmenin ardında donmuş tutar yok, silinen
     * tek şey yanlış yüklenmiş bir dosyanın kendisi.
     *
     * Kullanımdaysa 409 döner ve kullanıcı **pasife almaya** yönlendirilir: geçmiş hakedişin çivili
     * olduğu fiyat sürümünü silmek, tamamlanmış işin tutarını yok etmek demektir (§7/4).
     */
    @Transactional
    fun sil(kod: String) {
        val sozlesmeId = sozlesmeler.sozlesmeId(kod) ?: throw BulunamadiException("Sözleşme bulunamadı: $kod")

        val belgeSayisi = sozlesmeler.belgeSayisi(sozlesmeId)
        if (belgeSayisi > 0) {
            throw KuralIhlaliException(
                "Bu sözleşmeyle $belgeSayisi belge açılmış; silinemez. " +
                    "Kullanımdan çıkarmak için sözleşmeyi pasife alın — mevcut belgeler çalışmaya devam eder, " +
                    "yalnızca yeni belge açılamaz.",
            )
        }

        yazici.sil(sozlesmeId)
    }

    @Transactional
    fun pozEkle(kod: String, istek: PozEklemeIstegi): List<PozYaniti> {
        val sozlesmeId = sozlesmeler.sozlesmeId(kod) ?: throw BulunamadiException("Sözleşme bulunamadı: $kod")
        val versiyonId = sozlesmeler.referansFiyatVersiyonuId(sozlesmeId)
            ?: throw KuralIhlaliException("$kod sözleşmesinde yayında fiyat listesi yok; kalem eklenemez.")

        val pozNo = zorunlu(istek.pozNo, "Poz numarası")
        val ad = zorunlu(istek.ad, "İş kalemi adı")
        val birim = zorunlu(istek.birim, "Birim")
        val fiyat = when (val girdi = QuantityParser.parse(istek.birimFiyat.orEmpty())) {
            is QuantityInput.Valid -> girdi.value
            else -> throw GecersizIstekException("Birim fiyat geçerli bir sayı olmalı (ör. 1.234,56).")
        }
        // Fiyatsız kalem eklemenin anlamı yok: girilemeyen bir satır zaten sözleşmede var olanlarla
        // aynı görünürdü ve kullanıcı neden giriş yapamadığını anlamazdı.
        if (fiyat.signum() <= 0) throw GecersizIstekException("Birim fiyat sıfırdan büyük olmalı.")

        val poz = PozItem(
            id = sozlesmeler.sonrakiElleSatirKodu(sozlesmeId),
            pozNo = pozNo,
            // Sıralama anahtarı elle hesaplanmaz: "4.10" → [4, 10] kuralı domain'de tek yerde
            // (`parseSortKey`) ve parser da onu kullanıyor.
            sortKey = parseSortKey(pozNo),
            name = ad,
            unit = birim,
            unitPrice = fiyat,
            isGroup = false,
            isPriced = true,
        )
        val pozId = yazici.pozEkle(
            sozlesmeId = sozlesmeId,
            satirKodu = poz.id,
            poz = poz,
            sira = sozlesmeler.yeniPozSirasi(sozlesmeId, istek.grupSatirKodu?.trim()?.takeIf { it.isNotEmpty() }),
        )
        fiyatYazici.fiyatlariYaz(versiyonId, mapOf(pozId to fiyat))

        return sozlesmeler.pozlar(kod)
    }

    /**
     * Yüklenen dosyanın SHA-256 özeti — aynı dosyanın ikinci kez yüklendiği sonradan anlaşılabilsin
     * diye `sozlesme.kaynak_ozet` ve `fiyat_versiyonu.kaynak_ozet` sütunlarına yazılır (V6/V8).
     */
    private fun ozet(icerik: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(icerik).joinToString("") { "%02x".format(it) }

    private fun oku(icerik: ByteArray, sayfaAdi: String?): Pair<Contract, List<String>> {
        if (icerik.isEmpty()) throw GecersizIstekException("Sözleşme dosyası boş.")

        val uyarilar = mutableListOf<String>()
        val contract = XlsxOkuyucu.sayfada(icerik, sayfaAdi) { sayfa -> ContractXlsxParser.parse(sayfa, uyarilar) }
        return contract to uyarilar
    }

    private fun zorunlu(deger: String?, alanAdi: String): String =
        deger?.trim()?.takeIf { it.isNotEmpty() } ?: throw GecersizIstekException("$alanAdi boş olamaz.")

    private fun temizle(verilen: String?, varsayilan: String, alanAdi: String): String {
        val deger = verilen?.trim().orEmpty().ifEmpty { varsayilan.trim() }
        if (deger.isEmpty()) throw GecersizIstekException("$alanAdi boş olamaz.")
        return deger
    }

    /**
     * Boştaki ilk kodu bulur: `taban`, dolu ise `taban-2`, `taban-3`…
     *
     * Çakışma artık kullanıcıya sorulmuyor, sessizce çözülüyor — kod içsel bir anahtar olduğu için
     * hangi sonekle kaydedildiğinin yöneticiye anlatacağı bir şey yok. Sayfa adı tamamen simgesel
     * olduğunda (`"!!!"` → boş slug) taban `KOD_TABANI`'na düşer; boş kod `unique` kısıtını değil,
     * URL'i bozardı.
     *
     * ⚠️ Yarış durumu: iki yükleme aynı anda aynı kodu seçebilir. Son sözü **veritabanı** söylüyor
     * (`sozlesme.kod` `unique`); ikinci yazma hata alır ve işlem geri sarılır. Tek yöneticili bir
     * ekranda kilit maliyetine değmez.
     */
    private fun benzersizKod(taban: String): String {
        val temel = taban.ifEmpty { KOD_TABANI }
        if (!sozlesmeler.varMi(temel)) return temel

        var sonek = 2
        while (sozlesmeler.varMi("$temel-$sonek")) sonek++
        return "$temel-$sonek"
    }

    private companion object {
        /** Dosyadan hiçbir harf/rakam türemediğinde kullanılan taban kod. */
        const val KOD_TABANI = "sozlesme"
    }
}
