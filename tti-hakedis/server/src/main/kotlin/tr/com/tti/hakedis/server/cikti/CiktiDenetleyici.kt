package tr.com.tti.hakedis.server.cikti

import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tr.com.tti.hakedis.domain.document.DocumentVariant
import tr.com.tti.hakedis.server.yetki.OturumKullanicisi

/**
 * Belge çıktısı indirme uçları.
 *
 * `varyant` verilmezse `FIYATLI`: gündelik kullanım iç kullanımdır, fiyatsız kopya bilinçli bir
 * taleptir (aracı firmaya gönderilecek `TEKLIF`).
 *
 * Yetki ayrı bir kural gerektirmez: hangi belgenin görülebileceğini `CiktiSorgulari` SQL süzgeci
 * belirler, başka bölgenin belgesi 404 döner.
 */
@RestController
@RequestMapping("/api/belgeler")
class CiktiDenetleyici(private val servis: CiktiServisi) {
    @GetMapping("/{id}/pdf")
    fun pdf(
        @PathVariable id: Long,
        @RequestParam(required = false, defaultValue = "FIYATLI") varyant: DocumentVariant,
    ): ResponseEntity<ByteArray> = indir(servis.pdf(id, varyant, OturumKullanicisi.bolgeSuzgeci()), MediaType.APPLICATION_PDF)

    @GetMapping("/{id}/excel")
    fun excel(
        @PathVariable id: Long,
        @RequestParam(required = false, defaultValue = "FIYATLI") varyant: DocumentVariant,
    ): ResponseEntity<ByteArray> = indir(servis.excel(id, varyant, OturumKullanicisi.bolgeSuzgeci()), MediaType.parseMediaType(XLSX_TIPI))

    private fun indir(cikti: Cikti, tip: MediaType): ResponseEntity<ByteArray> = ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(cikti.dosyaAdi).build().toString())
        .contentType(tip)
        .body(cikti.icerik)

    private companion object {
        const val XLSX_TIPI = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }
}
