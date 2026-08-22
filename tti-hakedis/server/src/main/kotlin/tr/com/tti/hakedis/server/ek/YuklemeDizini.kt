package tr.com.tti.hakedis.server.ek

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tr.com.tti.hakedis.server.arsiv.VarsayilanKok
import java.nio.file.Files
import java.nio.file.Path

/**
 * Kullanıcının **yüklediği** dosyaların (EK1 güzergâh dosyası, EK2 görselleri) kök dizini.
 *
 * `ArsivDizini`'nden ayrıdır ve ayrı kalmalıdır: arşiv, sunucunun ürettiği PDF/Excel'in tek yönlü
 * aynasıdır ve kaybolursa yeniden üretilebilir; burası ise **asıl veridir** — güzergâh dosyası ve
 * ekler hiçbir yerden yeniden üretilemez. Aynı ağaca konsalardı arşivi temizleyen bir işlem kullanıcı
 * verisini de silerdi.
 *
 * Veritabanında yolun **köke göreli** hâli saklanır (`ek.depo_yolu`): ortak bir
 * ağ dizinine geçiş (`YUKLEME_KOK` değişikliği) hiçbir satırın güncellenmesini gerektirmez.
 */
@Component
class YuklemeDizini(@Value("\${yukleme.kok:}") kokYolu: String) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Değişken verilmezse işletim sistemine uygun varsayılan (bkz. `VarsayilanKok`). */
    val kok: Path = (kokYolu.takeIf { it.isNotBlank() }?.let { Path.of(it) } ?: VarsayilanKok.yukleme())
        .toAbsolutePath()
        .normalize()

    init {
        runCatching { Files.createDirectories(kok) }
            .onFailure { log.warn("Yükleme kökü oluşturulamadı: {} ({})", kok, it.message) }
        log.info("Yükleme kökü: {}", kok)
    }

    fun yazilabilirMi(): Boolean = Files.isDirectory(kok) && Files.isWritable(kok)

    /**
     * Dosyayı yazar; ara klasörleri açar. Üzerine yazma bilinçlidir — aynı belgeye yeniden yüklenen dosya aynı yolu
     * kullanır.
     *
     * Hata **yutulmaz**: yazma başarısız olursa çağıran transaction düşer ve veritabanında dosyası
     * olmayan bir kayıt kalmaz (fail loud, CLAUDE.md §7).
     */
    fun yaz(goreliYol: String, icerik: ByteArray) {
        val hedef = coz(goreliYol)
        Files.createDirectories(hedef.parent)
        Files.write(hedef, icerik)
    }

    fun oku(goreliYol: String): ByteArray = Files.readAllBytes(coz(goreliYol))

    fun varMi(goreliYol: String): Boolean = Files.isRegularFile(coz(goreliYol))

    /**
     * Dosyayı siler. Veritabanı satırı zaten silinmiş olduğu için başarısızlık işlemi geri almaz:
     * artakalan dosya zararsızdır, ama sessiz de bırakılmaz — `WARN` düşer.
     */
    fun sil(goreliYol: String) {
        runCatching { Files.deleteIfExists(coz(goreliYol)) }
            .onFailure { log.warn("Yüklenen dosya silinemedi: {} ({})", goreliYol, it.message) }
    }

    /**
     * Göreli yolu köke bağlar ve **kökün dışına çıkmadığını** doğrular.
     *
     * Yollar bu uygulamada üretiliyor (`EkYolu`), ama okuma yolu veritabanından geliyor: bir gün
     * elle düzenlenmiş ya da eski bir satır `../` taşırsa sunucu dosya sisteminde gezinmemeli.
     */
    private fun coz(goreliYol: String): Path {
        val cozulen = kok.resolve(goreliYol).normalize()
        require(cozulen.startsWith(kok)) { "Yükleme kökünün dışına çıkan yol: $goreliYol" }
        return cozulen
    }
}
