package tr.com.tti.hakedis.server.auth

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

enum class Rol { BOLGE_SORUMLUSU, YONETICI }

/**
 * Oturum açmış kullanıcı.
 *
 * [bolgeId] yetkilendirmenin taşıyıcısıdır: bölge sorumlusunda dolu, yöneticide `null`.
 * `null` "bölgesiz" değil, **"tüm bölgeler"** anlamına gelir ve sorgularda süzgeci devre dışı bırakır
 * (bkz. `BolgeSuzgeci`).
 */
class HakedisKullanicisi(
    val id: Long,
    private val kullaniciAdi: String,
    private val sifreHash: String,
    val adSoyad: String,
    val rol: Rol,
    val bolgeId: Int?,
    private val aktif: Boolean,
) : UserDetails {
    override fun getAuthorities(): Collection<GrantedAuthority> = listOf(SimpleGrantedAuthority("ROLE_${rol.name}"))

    override fun getPassword(): String = sifreHash

    override fun getUsername(): String = kullaniciAdi

    override fun isEnabled(): Boolean = aktif

    override fun isAccountNonExpired(): Boolean = aktif

    override fun isAccountNonLocked(): Boolean = aktif

    override fun isCredentialsNonExpired(): Boolean = aktif
}
