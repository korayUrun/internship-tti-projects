package tr.com.tti.hakedis.server.sozlesme

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tr.com.tti.hakedis.server.web.BulunamadiException

@RestController
@RequestMapping("/api/sozlesmeler")
class SozlesmeDenetleyici(private val sorgular: SozlesmeSorgulari) {
    // Sözleşmeler bölgeye göre süzülmez: her bölge sorumlusu tüm sözleşmeleri görebilir.
    // Süzgeç belge ve kable tarafındadır.
    @GetMapping
    fun sozlesmeler(): List<SozlesmeYaniti> = sorgular.sozlesmeler()

    @GetMapping("/{kod}/pozlar")
    fun pozlar(@PathVariable kod: String): List<PozYaniti> {
        if (!sorgular.varMi(kod)) throw BulunamadiException("Sözleşme bulunamadı: $kod")
        return sorgular.pozlar(kod)
    }
}
