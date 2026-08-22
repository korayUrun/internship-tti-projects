package tr.com.tti.hakedis.server.kable

import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * Kablo envanteri sürümleri — **yalnızca yönetici** (yetki `GuvenlikYapilandirmasi`'nda uca bağlı).
 *
 * Ekranın gördüğü akış üç uçtan ibaret: envanteri listele → dosya yükle (sürüm oluşur, canlı veri
 * değişmez) → sürümü yürürlüğe al. Geri dönmek dördüncü bir uç değil, aynı `yururluk` ucunun eski
 * bir sürümle çağrılmasıdır.
 *
 * Kable **arama** uçları (`/api/kableler`) bundan etkilenmez: herkes arar, bölge kısıtı yoktur (Faz O).
 */
@RestController
@RequestMapping("/api/kable-surumleri")
class KableSurumDenetleyici(private val sorgular: KableSurumSorgulari, private val servis: KableSurumServisi) {
    /** Ekranın tek açılış çağrısı: yürürlükteki envanterin özeti + tüm sürümler. */
    @GetMapping
    fun envanter(): KableEnvanteriYaniti = sorgular.envanter()

    /**
     * Yürürlükteki envanterin `.xlsx` hâli (Faz V) — elle yapılan düzeltmeler dahil.
     *
     * Veritabanı asıl kaynak: yüklenen ham dosya saklanmıyor, bu çıktı her zaman güncel hâli üretiyor
     * ve doğrudan [yukle]'ye geri verilebilir.
     */
    @GetMapping("/envanter.xlsx")
    fun disaAktar(): ResponseEntity<ByteArray> = ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(KableCiktisi.DOSYA_ADI).build().toString(),
        )
        .contentType(MediaType.parseMediaType(XLSX_TIPI))
        .body(KableCiktisi.uret(sorgular.disaAktarim()))

    /**
     * Dosyayı okur ve **sürüm olarak saklar**; `kable` tablosuna dokunmaz.
     *
     * Yanıttaki [KableSurumYuklemeSonucu.fark], yöneticinin yürürlüğe almadan önce göreceği "ne
     * olacak" özetidir.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun yukle(@RequestParam dosya: MultipartFile): KableSurumYuklemeSonucu = servis.yukle(dosya.bytes, dosya.originalFilename)

    /** Sürümün yürürlükteki envantere göre farkı — yazma yok. Geri dönüş onayı da bunu kullanır. */
    @GetMapping("/{id}/fark")
    fun fark(@PathVariable id: Long): KableSurumFarki = servis.fark(id)

    /** Sürümü yürürlüğe alır: `kable` tablosu bu sürümün izdüşümü olur. Geri dönüş de bu uçtur. */
    @PostMapping("/{id}/yururluk")
    fun yururlugeAl(@PathVariable id: Long): KableIzdusumSonucu = servis.yururlugeAl(id)

    /** Yürürlükte olmayan sürümü siler (yürürlüktekinde 409). */
    @DeleteMapping("/{id}")
    fun sil(@PathVariable id: Long) = servis.sil(id)
}

private const val XLSX_TIPI = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
