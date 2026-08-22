package tr.com.tti.hakedis.server.auth

import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import tr.com.tti.hakedis.server.lookup.LookupSorgulari
import tr.com.tti.hakedis.server.web.BulunamadiException
import tr.com.tti.hakedis.server.web.GecersizIstekException
import tr.com.tti.hakedis.server.web.KuralIhlaliException
import tr.com.tti.hakedis.server.yetki.OturumKullanicisi

data class ParolaIstegi(val yeniSifre: String?)

data class AktiflikIstegi(val aktif: Boolean?)

data class KullaniciAdiIstegi(val kullaniciAdi: String?)

/** Yeni kullanıcı — kullanıcı adı ve başlangıç parolası dahil. */
data class KullaniciOlusturmaIstegi(
    val kullaniciAdi: String?,
    val adSoyad: String?,
    val rol: Rol?,
    val bolgeKodu: String?,
    val yeniSifre: String?,
)

/** Ad soyad / rol / bölge düzenlemesi — kullanıcı adı ve parola ayrı uçlardan (madde 13). */
data class KullaniciBilgiIstegi(val adSoyad: String?, val rol: Rol?, val bolgeKodu: String?)

/**
 * Kullanıcı yönetimi — **yalnızca yönetici** (yetki `GuvenlikYapilandirmasi`'nda uca bağlı).
 *
 * Kapsam bilinçli olarak dar: **liste, kullanıcı adı, parola sıfırlama, aktif/pasif**. Kullanıcı
 * *oluşturma* yok, çünkü kullanıcılar sabit (7 bölge sorumlusu + 1 yönetici) ve yeni bir bölge sorumlusu ancak yeni
 * bir bölge açıldığında gerekir — o zaman `bolge` tablosu da değişeceği için işlem zaten migration
 * ister (NEWPHASES.md: "kayıt ol yok, parolalar elle atanır").
 *
 * Fazın asıl amacı ilk kurulum parolalarının (`TTI111111` …) değiştirilmesidir.
 */
@RestController
@RequestMapping("/api/kullanicilar")
class KullaniciDenetleyici(
    private val sorgular: KullaniciSorgulari,
    private val sifreKodlayici: PasswordEncoder,
    private val lookup: LookupSorgulari,
) {
    @GetMapping
    fun listele(): List<KullaniciYonetimYaniti> = sorgular.listele()

    /**
     * Yeni kullanıcı (madde 12). Bölge sorumlusu bir bölgeye bağlı olmalı ve o bölgede başka aktif
     * sorumlu olmamalı; yöneticinin bölgesi olmaz. Parola yöneticinin verdiği değerdir (kullanıcıya
     * kendisi iletecek).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun olustur(@RequestBody istek: KullaniciOlusturmaIstegi): KullaniciYonetimYaniti {
        val kullaniciAdi = kullaniciAdiDogrula(istek.kullaniciAdi, haric = -1)
        val adSoyad = zorunluMetin(istek.adSoyad, "Ad soyad zorunludur.")
        val rol = istek.rol ?: throw GecersizIstekException("Rol zorunludur.")
        val bolgeId = bolgeDogrula(rol, istek.bolgeKodu, haric = -1)

        val sifre = istek.yeniSifre?.trim().orEmpty()
        if (sifre.length < EN_KISA_PAROLA) throw GecersizIstekException("Parola en az $EN_KISA_PAROLA karakter olmalı.")
        if (sifre.equals(kullaniciAdi, ignoreCase = true)) throw GecersizIstekException("Parola kullanıcı adıyla aynı olamaz.")

        val hash = sifreKodlayici.encode(sifre) ?: throw IllegalStateException("Parola kodlanamadı.")
        val id = sorgular.olustur(kullaniciAdi, adSoyad, rol, bolgeId, hash)
        return sorgular.bul(id) ?: throw BulunamadiException("Kullanıcı bulunamadı: $id")
    }

    /**
     * Ad soyad / rol / bölge düzenlemesi (madde 13). Kullanıcı adı ("Adı Değiştir") ve parola ayrı
     * uçlardan değişir. Rol/bölge tutarlılığı ve bölge başına tek aktif sorumlu kuralı burada denetlenir.
     */
    @PutMapping("/{id}")
    fun bilgiGuncelle(@PathVariable id: Long, @RequestBody istek: KullaniciBilgiIstegi): KullaniciYonetimYaniti {
        sorgular.bul(id) ?: throw BulunamadiException("Kullanıcı bulunamadı: $id")
        val adSoyad = zorunluMetin(istek.adSoyad, "Ad soyad zorunludur.")
        val rol = istek.rol ?: throw GecersizIstekException("Rol zorunludur.")
        val bolgeId = bolgeDogrula(rol, istek.bolgeKodu, haric = id)

        sorgular.bilgiYaz(id, adSoyad, rol, bolgeId)
        return sorgular.bul(id) ?: throw BulunamadiException("Kullanıcı bulunamadı: $id")
    }

    /**
     * Parolayı yöneticinin verdiği değerle değiştirir.
     *
     * Sunucu rastgele parola üretmiyor: yönetici parolayı kullanıcıya kendisi iletecek, o yüzden
     * ne yazdığını bilmesi gerekiyor. Kural asgari düzeyde tutuldu — sekiz karakter ve kullanıcı
     * adından farklı olması; kurumsal parola politikası bu uygulamanın işi değil.
     */
    @PutMapping("/{id}/parola")
    fun parolaSifirla(@PathVariable id: Long, @RequestBody istek: ParolaIstegi): KullaniciYonetimYaniti {
        val kullanici = sorgular.bul(id) ?: throw BulunamadiException("Kullanıcı bulunamadı: $id")
        val yeni = istek.yeniSifre?.trim().orEmpty()

        if (yeni.length < EN_KISA_PAROLA) {
            throw GecersizIstekException("Parola en az $EN_KISA_PAROLA karakter olmalı.")
        }
        if (yeni.equals(kullanici.kullaniciAdi, ignoreCase = true)) {
            throw GecersizIstekException("Parola kullanıcı adıyla aynı olamaz.")
        }

        val hash = sifreKodlayici.encode(yeni)
            ?: throw IllegalStateException("Parola kodlanamadı.")
        sorgular.sifreYaz(id, hash)
        return sorgular.bul(id) ?: throw BulunamadiException("Kullanıcı bulunamadı: $id")
    }

    /**
     * Kullanıcı adını değiştirir.
     *
     * Ad giriş kimliğidir: değiştikten sonra kullanıcı **yeni adla** giriş yapar. Açık oturumu
     * düşmez — oturumdaki kimlik anlık bir görüntüdür ve kimlik/rol/bölge değişmediği için geçerli
     * kalır; bir sonraki girişte yeni ad geçerlidir.
     */
    @PutMapping("/{id}/kullanici-adi")
    fun kullaniciAdi(@PathVariable id: Long, @RequestBody istek: KullaniciAdiIstegi): KullaniciYonetimYaniti {
        sorgular.bul(id) ?: throw BulunamadiException("Kullanıcı bulunamadı: $id")
        val yeni = kullaniciAdiDogrula(istek.kullaniciAdi, haric = id)

        sorgular.kullaniciAdiYaz(id, yeni)
        return sorgular.bul(id) ?: throw BulunamadiException("Kullanıcı bulunamadı: $id")
    }

    private fun kullaniciAdiDogrula(kullaniciAdi: String?, haric: Long): String {
        val yeni = kullaniciAdi?.trim().orEmpty()
        if (yeni.length < EN_KISA_KULLANICI_ADI) {
            throw GecersizIstekException("Kullanıcı adı en az $EN_KISA_KULLANICI_ADI karakter olmalı.")
        }
        if (yeni.any { it.isWhitespace() }) {
            throw GecersizIstekException("Kullanıcı adı boşluk içeremez.")
        }
        if (sorgular.kullaniciAdiVarMi(yeni, haric = haric)) {
            throw KuralIhlaliException("Bu kullanıcı adı zaten kullanılıyor: $yeni")
        }
        return yeni
    }

    private fun zorunluMetin(deger: String?, mesaj: String): String =
        deger?.trim()?.takeIf { it.isNotEmpty() } ?: throw GecersizIstekException(mesaj)

    /**
     * Rol/bölge tutarlılığı: yöneticinin bölgesi olmaz; bölge sorumlusu bir bölgeye bağlıdır ve o
     * bölgede **başka aktif sorumlu** olamaz (`kullanici_bolge_ux`). Düzenlemede kullanıcının kendisi
     * dışlanır (`haric`).
     */
    private fun bolgeDogrula(rol: Rol, bolgeKodu: String?, haric: Long): Int? {
        if (rol == Rol.YONETICI) return null
        val kod = bolgeKodu?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw GecersizIstekException("Bölge sorumlusu için bölge zorunludur.")
        val bolgeId = lookup.bolgeId(kod) ?: throw BulunamadiException("Bölge bulunamadı: $kod")
        if (sorgular.bolgeAktifSorumlusuVarMi(bolgeId, haric = haric)) {
            throw KuralIhlaliException("Bu bölgede zaten aktif bir sorumlu var. Önce onu pasife alın ya da başka bölge seçin.")
        }
        return bolgeId
    }

    /**
     * Kullanıcıyı devre dışı bırakır ya da geri açar.
     *
     * Yönetici **kendini** pasifleştiremez: tek yönetici hesabı kapanırsa sisteme yönetici olarak
     * girecek kimse kalmaz ve çıkış yolu yalnızca veritabanına elle müdahaledir.
     *
     * Bir bölgede yalnızca **bir aktif sorumlu** olabilir (`kullanici_bolge_ux`); ikinci bir kullanıcı
     * aynı bölgeye açılmaya çalışılırsa veritabanı reddeder ve istek 409 döner.
     */
    @PutMapping("/{id}/aktif")
    fun aktiflik(@PathVariable id: Long, @RequestBody istek: AktiflikIstegi): KullaniciYonetimYaniti {
        sorgular.bul(id) ?: throw BulunamadiException("Kullanıcı bulunamadı: $id")
        val aktif = istek.aktif ?: throw GecersizIstekException("`aktif` alanı zorunludur.")

        if (!aktif && id == OturumKullanicisi.zorunlu().id) {
            throw KuralIhlaliException("Kendi hesabınızı kapatamazsınız; sistemde yönetici kalmaz.")
        }

        sorgular.aktiflikYaz(id, aktif)
        return sorgular.bul(id) ?: throw BulunamadiException("Kullanıcı bulunamadı: $id")
    }

    private companion object {
        const val EN_KISA_PAROLA = 8
        const val EN_KISA_KULLANICI_ADI = 3
    }
}
