package tr.com.tti.hakedis.server.auth

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * Yönetim ekranının gördüğü kullanıcı satırı.
 *
 * `sifre_hash` **hiçbir koşulda dışarı verilmez**: BCrypt hash'i sızdırmak çevrimdışı kaba kuvvet
 * denemesine kapı açar ve hiçbir ekranın ona ihtiyacı yok.
 */
data class KullaniciYonetimYaniti(
    val id: Long,
    val kullaniciAdi: String,
    val adSoyad: String,
    val rol: Rol,
    val bolgeKodu: String?,
    val bolgeAdi: String?,
    val aktif: Boolean,
    val olusturma: Instant,
)

@Repository
class KullaniciSorgulari(private val jdbc: JdbcClient) {
    fun listele(): List<KullaniciYonetimYaniti> = jdbc.sql(
        """
            select k.id, k.kullanici_adi, k.ad_soyad, k.rol, k.aktif, k.olusturma,
                   b.kod as bolge_kod, b.ad as bolge_ad
              from kullanici k
              left join bolge b on b.id = k.bolge_id
             order by k.rol, b.sira nulls first, k.kullanici_adi
        """.trimIndent(),
    ).query { sonuc, _ ->
        KullaniciYonetimYaniti(
            id = sonuc.getLong("id"),
            kullaniciAdi = sonuc.getString("kullanici_adi"),
            adSoyad = sonuc.getString("ad_soyad"),
            rol = Rol.valueOf(sonuc.getString("rol")),
            bolgeKodu = sonuc.getString("bolge_kod"),
            bolgeAdi = sonuc.getString("bolge_ad"),
            aktif = sonuc.getBoolean("aktif"),
            olusturma = sonuc.getObject("olusturma", java.time.OffsetDateTime::class.java).toInstant(),
        )
    }.list()

    fun bul(id: Long): KullaniciYonetimYaniti? = listele().firstOrNull { it.id == id }

    /**
     * Kullanıcı adı çakışması — **büyük/küçük harf duyarsız**.
     *
     * Tablodaki `unique` kısıt harf duyarlı ve giriş sorgusu birebir eşleşme yapıyor; `Ankara` ile
     * `ankara` iki ayrı hesap olabilirdi ve kimse hangisiyle gireceğini bilemezdi. Kısıt yine son
     * savunma hattı, ama ondan gelen hata mesajı ("bu belge zaten oluşturulmuş") burada yanıltıcı
     * olurdu — bu yüzden ön kontrol yapılıyor.
     */
    fun kullaniciAdiVarMi(kullaniciAdi: String, haric: Long): Boolean = jdbc.sql(
        "select count(*) from kullanici where lower(kullanici_adi) = lower(:ad) and id <> :haric",
    ).param("ad", kullaniciAdi)
        .param("haric", haric)
        .query(Int::class.java)
        .single() > 0

    fun kullaniciAdiYaz(id: Long, kullaniciAdi: String): Int = jdbc.sql("update kullanici set kullanici_adi = :ad where id = :id")
        .param("ad", kullaniciAdi)
        .param("id", id)
        .update()

    fun sifreYaz(id: Long, hash: String): Int = jdbc.sql("update kullanici set sifre_hash = :hash where id = :id")
        .param("hash", hash)
        .param("id", id)
        .update()

    fun aktiflikYaz(id: Long, aktif: Boolean): Int = jdbc.sql("update kullanici set aktif = :aktif where id = :id")
        .param("aktif", aktif)
        .param("id", id)
        .update()

    /**
     * Bölgenin **başka bir aktif** bölge sorumlusu var mı — `kullanici_bolge_ux`'un ön kontrolü.
     *
     * Kısıt son savunma hattı, ama ondan gelen ham hata yanıltıcı; ön kontrol anlaşılır 409 üretir.
     */
    fun bolgeAktifSorumlusuVarMi(bolgeId: Int, haric: Long): Boolean = jdbc.sql(
        """
        select count(*) from kullanici
         where bolge_id = :bolge and rol = 'BOLGE_SORUMLUSU' and aktif and id <> :haric
        """.trimIndent(),
    ).param("bolge", bolgeId)
        .param("haric", haric)
        .query(Int::class.java)
        .single() > 0

    /** Yeni kullanıcı; `aktif` varsayılan true. `bolgeId` yalnızca bölge sorumlusunda dolu. */
    fun olustur(kullaniciAdi: String, adSoyad: String, rol: Rol, bolgeId: Int?, hash: String): Long = jdbc.sql(
        """
        insert into kullanici (kullanici_adi, ad_soyad, sifre_hash, rol, bolge_id)
        values (:ad, :adSoyad, :hash, :rol, :bolge)
        returning id
        """.trimIndent(),
    ).param("ad", kullaniciAdi)
        .param("adSoyad", adSoyad)
        .param("hash", hash)
        .param("rol", rol.name)
        .param("bolge", bolgeId)
        .query(Long::class.java)
        .single()

    /** Ad soyad / rol / bölge düzenlemesi — kullanıcı adı ve parola ayrı uçlardan değişir (madde 13). */
    fun bilgiYaz(id: Long, adSoyad: String, rol: Rol, bolgeId: Int?): Int = jdbc.sql(
        "update kullanici set ad_soyad = :adSoyad, rol = :rol, bolge_id = :bolge where id = :id",
    ).param("adSoyad", adSoyad)
        .param("rol", rol.name)
        .param("bolge", bolgeId)
        .param("id", id)
        .update()
}
