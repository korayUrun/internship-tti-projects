package tr.com.tti.hakedis.server.ek

import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import tr.com.tti.hakedis.domain.document.DocumentFileNamer

/**
 * EK1 (güzergâh) ve EK2 (saha görselleri) uçları.
 *
 * Yükleme, silme ve listeleme belge altında (`/api/belgeler/{id}/ekler`), tek dosya işlemleri ek
 * kimliği altında (`/api/ekler/{id}`) toplanır. İkisinin de bölge yetkisi ve belge durumu kontrolü
 * `EkServisi`'nde, tek yerde.
 */
@RestController
class EkDenetleyici(private val servis: EkServisi) {
    @GetMapping("/api/belgeler/{belgeId}/ekler")
    fun listele(@PathVariable belgeId: Long): List<EkYaniti> = servis.listele(belgeId)

    /**
     * Ek yükleme. `tur` = `EK1_ROTA` | `EK2_GORSEL`.
     *
     * Yalnızca **hakediş taslağı** ek kabul eder; gönderilmiş belgeye ek eklenemez (D2).
     */
    @PostMapping("/api/belgeler/{belgeId}/ekler")
    @ResponseStatus(HttpStatus.CREATED)
    fun yukle(@PathVariable belgeId: Long, @RequestParam tur: EkTuru, @RequestParam dosya: MultipartFile): EkYaniti =
        servis.yukle(belgeId, tur, dosya)

    /** Dosyanın kendisi. Başka bölgenin eki **404** döner — varlığı da sızmaz. */
    @GetMapping("/api/ekler/{ekId}")
    fun indir(@PathVariable ekId: Long): ResponseEntity<ByteArray> {
        val ek = servis.indir(ekId)
        val indirmeAdi = DocumentFileNamer.sanitize(ek.dosyaAdi).ifEmpty { "ek" }

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(indirmeAdi).build().toString())
            .contentType(icerikTipi(ek.uzanti))
            .body(ek.icerik)
    }

    @DeleteMapping("/api/ekler/{ekId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun sil(@PathVariable ekId: Long) = servis.sil(ekId)

    /**
     * Uzantıdan içerik tipi. Doğrulama uzantı üzerinden yapıldığı için (MIME tutarsız — CLAUDE.md
     * §12/3) istemcinin gönderdiği `Content-Type` saklanmaz; indirmede yeniden türetilir.
     */
    private fun icerikTipi(uzanti: String): MediaType = when (uzanti) {
        "kmz" -> MediaType.parseMediaType("application/vnd.google-earth.kmz")
        "kml" -> MediaType.parseMediaType("application/vnd.google-earth.kml+xml")
        "png" -> MediaType.IMAGE_PNG
        "jpg", "jpeg" -> MediaType.IMAGE_JPEG
        else -> MediaType.APPLICATION_OCTET_STREAM
    }
}
