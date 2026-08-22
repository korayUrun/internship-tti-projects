package tr.com.tti.hakedis.server.web

import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.AuthenticationException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.servlet.resource.NoResourceFoundException
import tr.com.tti.hakedis.tools.ContractParseException
import tr.com.tti.hakedis.tools.kable.KableParseException

data class HataYaniti(val hata: String, val mesaj: String)

/** İstenen kaynak yok — ya da isteyenin görmeye yetkisi yok (ikisi dışarıdan ayırt edilemez). */
class BulunamadiException(mesaj: String) : RuntimeException(mesaj)

/** İş kuralı ihlali: aşama kuralı, açık keşif çakışması, bölge uyuşmazlığı gibi. */
class KuralIhlaliException(mesaj: String) : RuntimeException(mesaj)

/** İsteğin kendisi hatalı: zorunlu alan boş, tanımsız kod, negatif miktar gibi. */
class GecersizIstekException(mesaj: String) : RuntimeException(mesaj)

/**
 * Tüm uçlar için tek biçimli, Türkçe hata gövdesi.
 *
 * Beklenmeyen hatalarda istemciye ayrıntı verilmez (yığın izi ve SQL mesajı sızdırmaz), ancak sunucu
 * tarafında tam iz loglanır — sessizce yutulmaz (CLAUDE.md: fail loud).
 */
@RestControllerAdvice
class HataYakalayici {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BulunamadiException::class)
    fun bulunamadi(hata: BulunamadiException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(HataYaniti("BULUNAMADI", hata.message.orEmpty()))

    /**
     * **Var olmayan uç → 404**, 500 değil.
     *
     * Spring bir isteğe denk gelen `@RequestMapping` bulamazsa statik kaynak çözücüye düşüyor ve o da
     * `NoResourceFoundException` fırlatıyor. Yakalanmazsa jenerik `Exception` dalına düşüp
     * "İşlem tamamlanamadı, yönetici ile iletişime geçin" diyen bir 500 üretiyordu.
     *
     * Faz M–P'de bir düzine uç kaldırıldı (imza, onay, fiyat listesi, fiyat yönetimi). Eski bir APK
     * ya da açık kalmış bir tarayıcı sekmesi o uçları çağırmaya devam edebilir; aldıkları cevap
     * "böyle bir şey yok" olmalı, "sunucu bozuldu" değil.
     */
    @ExceptionHandler(NoResourceFoundException::class)
    fun ucYok(hata: NoResourceFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(HataYaniti("BULUNAMADI", "Böyle bir uç yok: ${hata.resourcePath}"))

    /**
     * **Kaldırılmış metot → 405**, 500 değil.
     *
     * [NoResourceFoundException]'ın ikizi: yol duruyor ama o metot artık yok. Faz V'de
     * `DELETE /api/sozlesmeler/{kod}` kaldırıldı; yol `PUT` için hâlâ eşleştiği için Spring statik
     * kaynak çözücüye düşmüyor, `HttpRequestMethodNotSupportedException` fırlatıyor. Yakalanmazsa
     * jenerik dala düşüp **500 + yığın izi** üretiyordu: istemci hatası sunucu çökmüşü gibi görünüyor
     * ve log her denemede stack trace ile doluyordu.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun metotYok(hata: HttpRequestMethodNotSupportedException) = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .body(HataYaniti("METOT_YOK", "Bu uç ${hata.method} metodunu desteklemiyor."))

    @ExceptionHandler(KuralIhlaliException::class)
    fun kuralIhlali(hata: KuralIhlaliException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(HataYaniti("KURAL_IHLALI", hata.message.orEmpty()))

    @ExceptionHandler(GecersizIstekException::class)
    fun gecersizIstek(hata: GecersizIstekException) = ResponseEntity.badRequest().body(HataYaniti("GECERSIZ_ISTEK", hata.message.orEmpty()))

    /**
     * Bozuk sözleşme/fiyat listesi xlsx'i bir **istemci** hatasıdır, sunucu hatası değil.
     *
     * Aksi hâlde jenerik `Exception` dalına düşüp 500 + "Yönetici ile iletişime geçin" dönerdi; oysa
     * parser'ın mesajları satır numaralı ve doğrudan kullanıcıya gösterilmeye uygun.
     */
    @ExceptionHandler(ContractParseException::class)
    fun bozukDosya(hata: ContractParseException) =
        ResponseEntity.badRequest().body(HataYaniti("GECERSIZ_ISTEK", "Dosya okunamadı: ${hata.message.orEmpty()}"))

    /**
     * Bozuk kable envanteri xlsx'i — aynı gerekçe (Faz U).
     *
     * `KableXlsxParser` mesajları satır numaralı ve doğrudan gösterilmeye uygun: "Satır 412 (mtc…):
     * 'Technical Consultant' boş." Yöneticinin dosyayı düzeltmek için ihtiyacı olan tam bilgi budur.
     */
    @ExceptionHandler(KableParseException::class)
    fun bozukEnvanter(hata: KableParseException) =
        ResponseEntity.badRequest().body(HataYaniti("GECERSIZ_ISTEK", "Dosya okunamadı: ${hata.message.orEmpty()}"))

    /**
     * Kısıt ihlali de bir kural ihlalidir, sunucu hatası değil.
     *
     * Aşama kuralı ve numaralandırma hem servis katmanında hem veritabanı kısıtlarıyla korunuyor
     * (`belge_acik_tur_ux`, `belge (is_id, tur)`, `belge_no`). Servis kontrolü anlaşılır mesaj üretmek
     * içindir; iki eşzamanlı istek aynı kontrolü birlikte geçerse kuralı **veritabanı** durdurur ve
     * buraya düşer. 500 dönmek yanıltıcı olurdu: istek reddedildi, sistem bozulmadı.
     */
    @ExceptionHandler(DuplicateKeyException::class)
    fun cakisma(hata: DuplicateKeyException): ResponseEntity<HataYaniti> {
        log.warn("Kısıt ihlali (eşzamanlı istek olabilir): {}", hata.message)
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            HataYaniti(
                "KURAL_IHLALI",
                "Aynı kayıt üzerinde eşzamanlı bir işlem var ya da bu belge zaten oluşturulmuş. Sayfayı yenileyip tekrar deneyin.",
            ),
        )
    }

    /**
     * Multipart sınırı aşıldı (`spring.servlet.multipart.max-file-size`).
     *
     * 500 dönmek yanıltıcı olurdu: sistem bozulmadı, dosya fazla büyük. 413 + sınırın kendisini
     * söyleyen bir mesaj, kullanıcının yapması gerekeni (küçült ya da parçala) doğrudan anlatır.
     */
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun cokBuyukDosya(hata: MaxUploadSizeExceededException): ResponseEntity<HataYaniti> {
        log.info("Yükleme sınırı aşıldı: {}", hata.message)
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
            HataYaniti("DOSYA_COK_BUYUK", "Dosya çok büyük. Tek dosya en fazla 10 MB olabilir."),
        )
    }

    @ExceptionHandler(AuthenticationException::class)
    fun kimlikHatasi(hata: AuthenticationException): ResponseEntity<HataYaniti> {
        // Kullanıcı adı mı parola mı yanlış, ayırt edilmez: hesap taramasını kolaylaştırır.
        log.info("Başarısız giriş denemesi: {}", hata.javaClass.simpleName)
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(HataYaniti("GECERSIZ_KIMLIK", "Kullanıcı adı veya parola hatalı."))
    }

    @ExceptionHandler(Exception::class)
    fun beklenmeyen(hata: Exception): ResponseEntity<HataYaniti> {
        log.error("Beklenmeyen hata", hata)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(HataYaniti("SUNUCU_HATASI", "İşlem tamamlanamadı. Yönetici ile iletişime geçin."))
    }
}
