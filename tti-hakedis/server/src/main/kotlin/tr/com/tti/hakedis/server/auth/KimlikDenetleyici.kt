package tr.com.tti.hakedis.server.auth

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tr.com.tti.hakedis.server.lookup.LookupSorgulari
import tr.com.tti.hakedis.server.yetki.OturumKullanicisi

data class GirisIstegi(val kullaniciAdi: String, val sifre: String)

/**
 * `bolgeAdi` kodun yanında ayrıca taşınır: kenar çubuğu kullanıcı adı yerine **bölge adını** yazıyor
 * (`App.tsx`) ve istemcinin bunun için ayrıca `/api/bolgeler` çekip kod→ad eşlemesi kurması gerekirdi.
 */
data class KullaniciYaniti(val kullaniciAdi: String, val adSoyad: String, val rol: Rol, val bolgeKodu: String?, val bolgeAdi: String?)

@RestController
@RequestMapping("/api/auth")
class KimlikDenetleyici(
    private val kimlikYoneticisi: AuthenticationManager,
    private val baglamDeposu: SecurityContextRepository,
    private val lookup: LookupSorgulari,
) {
    @PostMapping("/giris")
    fun giris(@RequestBody istek: GirisIstegi, httpIstek: HttpServletRequest, httpYanit: HttpServletResponse): KullaniciYaniti {
        val kimlik = kimlikYoneticisi.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(istek.kullaniciAdi, istek.sifre),
        )

        // Oturum sabitleme (session fixation) saldırısına karşı: girişten önce varsa eski oturum düşürülür.
        httpIstek.getSession(false)?.invalidate()
        httpIstek.getSession(true)

        val baglam = SecurityContextHolder.createEmptyContext().apply { authentication = kimlik }
        SecurityContextHolder.setContext(baglam)
        baglamDeposu.saveContext(baglam, httpIstek, httpYanit)

        return yanit(kimlik.principal as HakedisKullanicisi)
    }

    @PostMapping("/cikis")
    fun cikis(httpIstek: HttpServletRequest) {
        httpIstek.getSession(false)?.invalidate()
        SecurityContextHolder.clearContext()
    }

    @GetMapping("/ben")
    fun ben(): KullaniciYaniti = yanit(OturumKullanicisi.zorunlu())

    private fun yanit(kullanici: HakedisKullanicisi): KullaniciYaniti {
        // Tek sorgu: kod ve ad aynı satırdan gelir, iki ayrı lookup çağrısı açılmaz.
        val bolge = kullanici.bolgeId?.let { lookup.bolge(it) }
        return KullaniciYaniti(
            kullaniciAdi = kullanici.username,
            adSoyad = kullanici.adSoyad,
            rol = kullanici.rol,
            bolgeKodu = bolge?.kod,
            bolgeAdi = bolge?.ad,
        )
    }
}
