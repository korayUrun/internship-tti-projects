package tr.com.tti.hakedis.server.cikti

import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import tr.com.tti.hakedis.domain.document.DocumentVariant
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Belge nihai hâline geldi — çıktısı arşive yazılabilir.
 *
 * `BelgeServisi` yayımlar, dinleyici **commit'ten sonra** çalışır. Olay kullanılıyor çünkü işlem
 * geri alınırsa (rollback) arşive düşmüş bir belge kalmamalı; ayrıca `BelgeServisi`'nin çıktı
 * üretimini tanıması gerekmiyor.
 */
data class BelgeKesinlestiOlayi(val belgeId: Long)

/**
 * Gönderilen ve onaylanan belgelerin PDF + Excel çıktısını **kimse indirmeden** üretip arşive yazar.
 *
 * Önceden arşiv yalnızca biri belgeyi indirdiğinde doluyordu (`CiktiServisi.yayinla`); indirmeyen bir
 * bölge sorumlusunun belgesi diskte hiç oluşmuyordu. Oysa arşivin varlık nedeni tam da uygulamaya
 * ulaşılamadığı durum (CLAUDE.md §7/6) — o durumda "önce birinin indirmiş olması" koşulu anlamsız.
 *
 * **Yalnızca `FIYATLI`** üretilir (kullanıcı kararı): `TEKLIF` kopyası aracı firmaya gönderilmek üzere
 * bilinçli olarak indirilen bir şey, arşivde iki katı dosya tutmayı gerektirmiyor.
 *
 * Üretim **asenkron**: PDF/Excel üretimi saniyeler sürebiliyor ve kullanıcının "Gönder" isteğini
 * bekletmemeli. Başarısızlık kullanıcıya yansımaz — belge veritabanında duruyor, dosya adı kararlı
 * olduğu için bir sonraki üretim (indirme ya da yeniden gönderim) aynı yolun üzerine yazar.
 */
@Component
class OtomatikArsivleme(private val cikti: CiktiServisi) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Sınırlı ve daemon: yavaş bir arşiv yolu sunucuyu iş parçacığıyla doldurmasın, kapanmayı geciktirmesin. */
    private val havuz: ScheduledExecutorService = Executors.newScheduledThreadPool(HAVUZ_BOYUTU) { gorev ->
        Thread(gorev, "otomatik-arsiv").apply { isDaemon = true }
    }

    /**
     * `AFTER_COMMIT`: belge gerçekten kaydedildikten sonra üretilir. Aynı transaction içinde
     * üretilseydi, geri alınan bir gönderimin çıktısı arşivde kalırdı.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun belgeKesinlesti(olay: BelgeKesinlestiOlayi) {
        havuz.execute { uret(olay.belgeId, ilkDeneme = true) }
    }

    private fun uret(belgeId: Long, ilkDeneme: Boolean) {
        // Bölge süzgeci `null`: bu çağrının oturumu yok. Yetki, olayı doğuran `gonder`/`onayla`
        // isteğinde zaten doğrulandı — burada yeniden süzmek belgeyi kendi arşivinden saklardı.
        runCatching {
            cikti.pdf(belgeId, DocumentVariant.FIYATLI, bolgeSuzgeci = null)
            cikti.excel(belgeId, DocumentVariant.FIYATLI, bolgeSuzgeci = null)
        }.onSuccess {
            log.info("Belge arşive yazıldı (otomatik): {}", belgeId)
        }.onFailure { hata ->
            if (ilkDeneme) {
                log.warn("Otomatik arşivleme başarısız, yeniden denenecek: belge {} ({})", belgeId, hata.message)
                havuz.schedule({ uret(belgeId, ilkDeneme = false) }, YENIDEN_DENEME_SANIYE, TimeUnit.SECONDS)
            } else {
                // Sessizce yutulmaz: belge veritabanında duruyor, arayüzden indirilince yine üretilir.
                log.warn("Otomatik arşivleme ikinci denemede de başarısız: belge {} ({})", belgeId, hata.message)
            }
        }
    }

    @PreDestroy
    fun kapat() {
        havuz.shutdown()
    }

    private companion object {
        const val HAVUZ_BOYUTU = 2
        const val YENIDEN_DENEME_SANIYE = 30L
    }
}
