package tr.com.tti.hakedis.server.cikti

import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tr.com.tti.hakedis.domain.document.DocumentFileNamer
import tr.com.tti.hakedis.domain.document.DocumentType
import tr.com.tti.hakedis.server.arsiv.ArsivDizini
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Üretilen PDF/Excel'in arşiv klasörüne **tek yönlü** aynası: `{ARSIV_KOK}/YedekTTI/{BÖLGE}/{TÜR}/`.
 *
 * Amaç, uygulamaya veya veritabanına ulaşılamadığı bir durumda belgelere düz dosya sisteminden
 * erişebilmek (CLAUDE.md §7). Doğruluğun kaynağı **veritabanıdır**; arşiv yalnızca kopyadır ve
 * gerektiğinde yeniden üretilebilir — bu yüzden kopyalama **belge üretimini düşürmez**.
 *
 * Kopyalama **asenkrondur**: arşiv ileride bir ağ dizinine taşınabilir (`ARSIV_KOK`), ve yavaş ya da
 * geçici olarak erişilemez bir ağ yolu, kullanıcının indirme isteğini bekletmemeli.
 *
 * Başarısızlıkta bir kez daha denenir, sonra `WARN` düşer. Kalıcı kayıp da onarılabilir kalır: dosya
 * adı kararlıdır (zaman damgası yok), yani belgenin bir sonraki üretimi aynı yolun üzerine yazar.
 */
@Component
class ArsivAynasi(private val arsiv: ArsivDizini) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Havuz **sınırlıdır**: her indirme isteği bir iş parçacığı doğursaydı, erişilemez bir ağ arşivi
     * sunucuyu bekleyen iş parçacıklarıyla doldururdu. Daemon: kopyalama sunucunun kapanmasını geciktirmez.
     */
    private val havuz: ScheduledExecutorService = Executors.newScheduledThreadPool(HAVUZ_BOYUTU) { gorev ->
        Thread(gorev, "arsiv-aynasi").apply { isDaemon = true }
    }

    fun kopyala(bolgeDosyaKodu: String, tur: DocumentType, dosyaAdi: String, icerik: ByteArray) {
        havuz.execute { yaz(bolgeDosyaKodu, tur, dosyaAdi, icerik, ilkDeneme = true) }
    }

    private fun yaz(bolgeDosyaKodu: String, tur: DocumentType, dosyaAdi: String, icerik: ByteArray, ilkDeneme: Boolean) {
        val hedef = arsiv.kok
            .resolve(KOK_KLASOR)
            .resolve(DocumentFileNamer.sanitize(bolgeDosyaKodu))
            .resolve(klasorAdi(tur))
            .resolve(dosyaAdi)

        runCatching {
            Files.createDirectories(hedef.parent)
            Files.write(hedef, icerik)
        }.onFailure { hata ->
            if (ilkDeneme) {
                log.warn("Arşiv kopyası başarısız, yeniden denenecek: {} ({})", hedef, hata.message)
                val yenidenDene = Runnable { yaz(bolgeDosyaKodu, tur, dosyaAdi, icerik, ilkDeneme = false) }
                havuz.schedule(yenidenDene, YENIDEN_DENEME_SANIYE, TimeUnit.SECONDS)
            } else {
                // Sessizce yutulmaz. Belge veritabanında duruyor ve yeniden üretildiğinde aynı yola yazılacak.
                log.warn("Arşiv kopyası ikinci denemede de başarısız: {} ({})", hedef, hata.message)
            }
        }
    }

    @PreDestroy
    fun kapat() {
        havuz.shutdown()
    }

    /**
     * Belge türünün klasör adı — kullanıcının beklediği adlandırma (`1_Kesif`, `On_Fizibilite`).
     *
     * Enum adı (`KESIF`) değil, çünkü klasörü açan kişi uygulamanın enum'unu değil ekranda gördüğü
     * aşamayı arıyor. ASCII ve boşluksuz: klasör adı Docker bind mount'undan, yedekleme betiklerinden
     * ve Windows'tan geçiyor; Türkçe karakter ve boşluk bu yollarda kodlama/tırnak hatası üretiyor
     * (CLAUDE.md §5 locale tuzağı aynı aileden).
     */
    private fun klasorAdi(tur: DocumentType): String = when (tur) {
        DocumentType.FIZIBILITE -> "On_Fizibilite"
        DocumentType.KESIF -> "1_Kesif"
        DocumentType.HAKEDIS -> "Hakedis"
    }

    private companion object {
        const val HAVUZ_BOYUTU = 2
        const val YENIDEN_DENEME_SANIYE = 30L

        /**
         * Arşiv ağacının kökü: `{ARSIV_KOK}/YedekTTI/{BÖLGE}/{TÜR}/`.
         *
         * `ARSIV_KOK` başka dosyalar da barındırabilen bir klasöre işaret edebiliyor (`_yedek`
         * altındaki pg_dump'lar orada); belge ağacı kendi adlı klasörüne alınarak karışma önlenir.
         * ~~Yıl basamağı~~ kaldırıldı (kullanıcı kararı): yıl zaten dosya adında
         * (`TTI_BURSA_2026_KESIF_001_FIYATLI.pdf`), ayrı klasör tıklama derinliği ekliyordu.
         */
        const val KOK_KLASOR = "YedekTTI"
    }
}
