package tr.com.tti.hakedis.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tr.com.tti.hakedis.data.api.ApiException
import tr.com.tti.hakedis.data.api.HakedisApi
import tr.com.tti.hakedis.data.api.UserDto

/**
 * Giriş yapmış kullanıcı — uygulama ömrü boyunca tek kaynak.
 *
 * Oturum yalnızca bellekte (karar J2): süreç öldüğünde kullanıcı yeniden giriş yapar. Sunucu oturumu
 * 8 saat geçerli olduğu için uygulamayı gün içinde arka plana almak yeniden giriş gerektirmez.
 */
class Session(private val api: HakedisApi, private val settings: Settings) {

    private val _user = MutableStateFlow<UserDto?>(null)
    val user: StateFlow<UserDto?> = _user.asStateFlow()

    suspend fun login(userName: String, password: String): UserDto {
        val user = api.login(userName, password)
        settings.writeLastUserName(userName)
        _user.value = user
        return user
    }

    suspend fun logout() {
        api.logout()
        _user.value = null
    }

    /**
     * Oturum düştüyse (401) kullanıcıyı giriş ekranına döndürür ve `true` verir.
     *
     * Ekranlar hata yakalarken bunu çağırır; 401'i tek tek yorumlamazlar. Sunucu oturumu 8 saatte
     * dolduğu için bu, sahada gerçekten yaşanan bir durum: kullanıcı ekranda "yetkiniz yok" mesajı
     * yerine doğrudan giriş ekranını görmeli.
     */
    fun expiredSession(error: Throwable): Boolean {
        if (error is ApiException && error.status == UNAUTHORIZED) {
            _user.value = null
            return true
        }
        return false
    }

    private companion object {
        const val UNAUTHORIZED = 401
    }
}
