package tr.com.tti.hakedis.server.yetki

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import tr.com.tti.hakedis.server.auth.HakedisKullanicisi
import tr.com.tti.hakedis.server.auth.Rol

/**
 * Bölge süzgecinin doğru değeri ürettiğini kilitler.
 *
 * Yönetici için `null` dönmesi kritik: sorgularda `null` "süzgeç yok" anlamına geliyor. Yanlışlıkla
 * bölge sorumlusu için de `null` dönerse tüm bölgelerin verisi görünür ve hiçbir hata çıkmaz.
 */
class OturumKullanicisiTest {
    @AfterEach
    fun temizle() = SecurityContextHolder.clearContext()

    private fun oturumAc(rol: Rol, bolgeId: Int?) {
        val kullanici = HakedisKullanicisi(
            id = 1,
            kullaniciAdi = "deneme",
            sifreHash = "yok",
            adSoyad = "Deneme",
            rol = rol,
            bolgeId = bolgeId,
            aktif = true,
        )
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(kullanici, null, kullanici.authorities)
    }

    @Test
    fun `bolge sorumlusu icin kendi bolgesi dondurulur`() {
        oturumAc(Rol.BOLGE_SORUMLUSU, bolgeId = 4)

        assertThat(OturumKullanicisi.bolgeSuzgeci()).isEqualTo(4)
    }

    @Test
    fun `yonetici icin null dondurulur - tum bolgeler`() {
        oturumAc(Rol.YONETICI, bolgeId = null)

        assertThat(OturumKullanicisi.bolgeSuzgeci()).isNull()
    }

    @Test
    fun `oturum yoksa hata firlatilir`() {
        SecurityContextHolder.clearContext()

        assertThrows<IllegalStateException> { OturumKullanicisi.zorunlu() }
    }
}
