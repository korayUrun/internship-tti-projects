package tr.com.tti.hakedis.server.yetki

import org.springframework.security.core.context.SecurityContextHolder
import tr.com.tti.hakedis.server.auth.HakedisKullanicisi
import tr.com.tti.hakedis.server.auth.Rol

/**
 * Oturum açmış kullanıcıya ve **bölge süzgecine** tek erişim noktası.
 *
 * Süzgeç değeri her sorguya `where (:bolgeId is null or ... = :bolgeId)` biçiminde verilir:
 * yönetici için `null` (tüm bölgeler), bölge sorumlusu için kendi bölgesi.
 *
 * Süzgecin **SQL'de** olması pazarlıksızdır. `select * from ...` çekip Kotlin'de `filter` etmek,
 * süzgeci her uçta elle tekrarlamak demektir; biri unutulduğunda hiçbir hata çıkmaz, sadece başka
 * bölgenin verisi görünür — fark edilmesi en zor hata türü (CLAUDE.md §7).
 */
object OturumKullanicisi {
    fun zorunlu(): HakedisKullanicisi {
        val kimlik = SecurityContextHolder.getContext().authentication
            ?: throw IllegalStateException("Oturum yok — bu uç kimlik doğrulaması gerektiriyor.")
        return kimlik.principal as? HakedisKullanicisi
            ?: throw IllegalStateException("Beklenmeyen oturum tipi: ${kimlik.principal?.javaClass?.name}")
    }

    /** Yönetici için `null` (süzgeç yok), bölge sorumlusu için kendi bölge kimliği. */
    fun bolgeSuzgeci(): Int? = zorunlu().takeIf { it.rol == Rol.BOLGE_SORUMLUSU }?.bolgeId
}
