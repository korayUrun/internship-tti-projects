package tr.com.tti.hakedis.server.web

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import tr.com.tti.hakedis.server.arsiv.ArsivDizini
import tr.com.tti.hakedis.server.ek.YuklemeDizini

/**
 * İki dizin ayrı ayrı raporlanır: arşiv üretilen çıktının aynası, yükleme kökü ise kullanıcının
 * yüklediği asıl veridir. Biri yazılamaz hâldeyken diğerinin çalışması mümkün, sorunun hangisinde
 * olduğu tek bakışta görünmeli.
 */
data class SaglikYaniti(
    val durum: String,
    val surum: String,
    val veritabani: Boolean,
    val arsivYazilabilir: Boolean,
    val yuklemeYazilabilir: Boolean,
)

@RestController
class SaglikDenetleyici(private val jdbc: JdbcClient, private val arsiv: ArsivDizini, private val yukleme: YuklemeDizini) {
    @GetMapping("/api/saglik")
    fun saglik(): SaglikYaniti {
        val veritabani = runCatching { jdbc.sql("select 1").query(Int::class.java).single() == 1 }
            .getOrDefault(false)
        return SaglikYaniti(
            durum = if (veritabani) "AYAKTA" else "BOZUK",
            // Çalışan sürüm dışarıdan görünmeli. İmaj etiketi değişmezdir ama hangi etiketin ayakta
            // olduğunu sormanın tek güvenilir yolu uygulamanın kendisine sormaktır — `docker ps`
            // yalnızca konteynerin ne ile BAŞLATILDIĞINI gösterir. Konteynerde SURUM ortam
            // değişkeni compose'daki imaj etiketiyle aynı değeri taşır.
            surum = System.getenv("SURUM") ?: "bilinmiyor",
            veritabani = veritabani,
            arsivYazilabilir = arsiv.yazilabilirMi(),
            yuklemeYazilabilir = yukleme.yazilabilirMi(),
        )
    }
}
