package tr.com.tti.hakedis.server.sozlesme

import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import tr.com.tti.hakedis.server.web.BulunamadiException
import tr.com.tti.hakedis.server.web.GecersizIstekException

/** Sözleşmenin düzenlenebilir alanları. Kod ve poz listesi değişmez. */
data class SozlesmeBilgiIstegi(val ad: String?, val baslik: String?, val aktif: Boolean?)

/**
 * Sözleşme yönetimi — **yalnızca yönetici** (yetki `GuvenlikYapilandirmasi`'nda uca bağlı).
 *
 * Kapsam Faz N'de iki eyleme inmişti (yükle, format indir); Faz V'de **yeni kalem ekleme** eklendi ve
 * silme kaldırıldı. 18 Ağustos 2026'da **silme koşullu olarak geri geldi** (kullanıcı isteği):
 * yalnızca hiç belge açılmamış sözleşmeler silinebilir (bkz. [sil]). Ad/başlık/aktiflik düzenlemesi
 * kalıyor çünkü dosya yeniden yüklemeden düzeltilebilir olmalı.
 *
 * ~~Referans birim fiyat düzenleme (taslak aç → satır düzenle → yayınla)~~ kaldırıldı ve **geri
 * gelmedi**: var olan bir fiyatı yerinde değiştirmek, açık bir taslağın ekranıyla belgeye yazılan
 * fiyatı ayrıştırır (§7/4b) ve hiçbir hata çıkmaz. Fiyat değişikliğinin yolu hâlâ "indir → düzelt →
 * yeni kodla yükle → eskisini pasife çek".
 *
 * Kalem **eklemek** bu gerekçenin dışında kalıyor: hiçbir mevcut `satir_kodu` kaymıyor, hiçbir fiyat
 * değişmiyor, dolayısıyla geçmiş belgelerin bağları da tutarları da olduğu gibi duruyor.
 */
@RestController
@RequestMapping("/api/sozlesmeler")
class SozlesmeYonetimDenetleyici(
    private val sorgular: SozlesmeSorgulari,
    private val yazici: SozlesmeYazici,
    private val yukleme: SozlesmeYuklemeServisi,
) {
    /** Yönetim listesi: pasif sözleşmeler de görünür (salt okunur uç yalnızca aktifleri döner). */
    @GetMapping("/yonetim")
    fun yonetimListesi(): List<SozlesmeYonetimYaniti> = sorgular.yonetimListesi()

    /**
     * Var olan sözleşmenin **dolu** `.xlsx` hâli — satır menüsündeki "İndir".
     *
     * Sözleşme değiştirilemediği için bu dosya "düzelt ve yeni kodla yükle" akışının girdisidir:
     * sütun düzeni [SozlesmeSablonu] ile aynı, dolayısıyla indirilen dosya doğrudan geri yüklenebilir.
     *
     * Yol `/{kod}`'dan önce gelmesi yetmez; ayırt edici olan `.xlsx` uzantısıdır — Spring sabit yolu
     * değişkenli yola tercih eder (aynı kalıp `/api/isler/liste.xlsx`'te de var).
     */
    @GetMapping("/{kod}/liste.xlsx")
    fun sozlesmeExceli(@PathVariable kod: String): ResponseEntity<ByteArray> {
        val satir = sorgular.yonetimListesi().firstOrNull { it.kod == kod }
            ?: throw BulunamadiException("Sözleşme bulunamadı: $kod")

        return ResponseEntity.ok()
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(SozlesmeCiktisi.dosyaAdi(kod)).build().toString(),
            )
            .contentType(MediaType.parseMediaType(XLSX_TIPI))
            .body(SozlesmeCiktisi.uret(kod, satir.baslik, sorgular.pozlar(kod)))
    }

    /**
     * Boş sözleşme formatı: doldurulup [yukle] ile geri yüklenir.
     *
     * Ayrı bir uç olması bilinçli — biçim bilgisi dokümanda değil, indirilebilir bir dosyada duruyor.
     */
    @GetMapping("/format.xlsx")
    fun format(): ResponseEntity<ByteArray> = ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(SozlesmeSablonu.DOSYA_ADI).build().toString(),
        )
        .contentType(MediaType.parseMediaType(XLSX_TIPI))
        .body(SozlesmeSablonu.uret())

    /** Dosyayı **yazmadan** işler: hangi kod/ad/başlık türeyecek, kaç poz var, kod çakışıyor mu. */
    @PostMapping("/onizleme")
    fun onizle(@RequestParam dosya: MultipartFile, @RequestParam(required = false) sayfa: String?): SozlesmeOnizleme =
        yukleme.onizle(dosya.bytes, sayfa)

    /**
     * Yeni sözleşme + poz listesi + fiyat sürümü — tek transaction.
     *
     * [oncekiKod] doluysa bu bir **revizyondur**: yeni sözleşme o koda bağlanır ve eskisi pasife alınır
     * (Excel round-trip ile poz düzenleme, müdür isteği).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun yukle(
        @RequestParam dosya: MultipartFile,
        @RequestParam(required = false) sayfa: String?,
        @RequestParam(required = false) kod: String?,
        @RequestParam(required = false) ad: String?,
        @RequestParam(required = false) baslik: String?,
        @RequestParam(required = false) oncekiKod: String?,
    ): SozlesmeYonetimYaniti {
        val yeniKod = yukleme.yukle(dosya.bytes, sayfa, dosya.originalFilename, SozlesmeYuklemeAlanlari(kod, ad, baslik, oncekiKod))
        return yonetimSatiri(yeniKod)
    }

    /**
     * Ad, başlık ve aktiflik.
     *
     * Pasife çekmek yalnızca **yeni belge açılmasını** engeller; mevcut belgeler tam olarak çalışmaya
     * devam eder (`SozlesmeSorgulari.sozlesmeId` aktiflik süzgeci uygulamaz).
     */
    @PutMapping("/{kod}")
    fun bilgiGuncelle(@PathVariable kod: String, @RequestBody istek: SozlesmeBilgiIstegi): SozlesmeYonetimYaniti {
        val mevcut = yonetimSatiri(kod)

        val ad = istek.ad?.trim().orEmpty().ifEmpty { throw GecersizIstekException("Sözleşme adı boş olamaz.") }
        val baslik = istek.baslik?.trim().orEmpty().ifEmpty { throw GecersizIstekException("Sözleşme başlığı boş olamaz.") }

        yazici.bilgiGuncelle(kod, ad, baslik, istek.aktif ?: mevcut.aktif)
        return yonetimSatiri(kod)
    }

    /** Sözleşmeye yeni bir iş kalemi ekler (Faz V) ve güncel poz listesini döner. */
    @PostMapping("/{kod}/pozlar")
    fun pozEkle(@PathVariable kod: String, @RequestBody istek: PozEklemeIstegi): List<PozYaniti> = yukleme.pozEkle(kod, istek)

    /**
     * Sözleşmeyi siler — **yalnızca hiç belge açılmamışsa** (18 Ağustos 2026, kullanıcı isteği).
     *
     * ~~Faz V'de kaldırılmıştı~~: o hâlinde silme, geçmiş hakedişin çivili olduğu fiyat sürümünü de
     * götürebilen koşulsuz bir işlemdi. Geri gelen sürüm koşullu: kullanımdaki sözleşme **409** ile
     * reddedilir ve yönetici pasife almaya yönlendirilir. Silinebilen tek şey, ardında hiçbir belge
     * bırakmamış — yani yanlışlıkla yüklenmiş — bir sözleşmedir.
     */
    @DeleteMapping("/{kod}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun sil(@PathVariable kod: String) = yukleme.sil(kod)

    private fun yonetimSatiri(kod: String): SozlesmeYonetimYaniti = sorgular.yonetimListesi().firstOrNull { it.kod == kod }
        ?: throw BulunamadiException("Sözleşme bulunamadı: $kod")
}

private const val XLSX_TIPI = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
