package tr.com.tti.hakedis.server.auth

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class KullaniciDetaylariServisi(private val jdbc: JdbcClient) : UserDetailsService {
    override fun loadUserByUsername(username: String): HakedisKullanicisi = jdbc.sql(
        """
            select id, kullanici_adi, sifre_hash, ad_soyad, rol, bolge_id, aktif
              from kullanici
             where kullanici_adi = :kullaniciAdi
        """.trimIndent(),
    ).param("kullaniciAdi", username)
        .query { sonuc, _ ->
            HakedisKullanicisi(
                id = sonuc.getLong("id"),
                kullaniciAdi = sonuc.getString("kullanici_adi"),
                sifreHash = sonuc.getString("sifre_hash"),
                adSoyad = sonuc.getString("ad_soyad"),
                rol = Rol.valueOf(sonuc.getString("rol")),
                // Yöneticide bolge_id NULL'dır; getInt() 0 döndüreceği için wasNull ile ayrılır.
                bolgeId = sonuc.getInt("bolge_id").takeIf { !sonuc.wasNull() },
                aktif = sonuc.getBoolean("aktif"),
            )
        }.optional()
        .orElseThrow { UsernameNotFoundException("Kullanıcı bulunamadı") }
}
