package tr.com.tti.hakedis.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tr.com.tti.hakedis.data.Session
import tr.com.tti.hakedis.data.Settings
import tr.com.tti.hakedis.data.api.UserDto

data class AccountUiState(
    val user: UserDto? = null,
    /** Bağlı olunan sunucu — **salt okunur**; derleme zamanında sabit (`res/values/config.xml`). */
    val serverAddress: String = "",
    val logoutAsked: Boolean = false,
)

sealed interface AccountEvent {
    data object LogoutAsked : AccountEvent
    data object LogoutDismissed : AccountEvent
    data object LogoutConfirmed : AccountEvent
}

/**
 * Hesap sekmesi.
 *
 * Üst şeritte sıkışık duran üç şeyi topluyor: kim giriş yapmış, hangi sunucuya bağlı, ve çıkış.
 *
 * ~~Sunucu adresi burada değiştirilebilir (karar J1)~~ — **kaldırıldı** (kullanıcı isteği,
 * 19 Ağustos 2026). Adres yalnızca **gösteriliyor**: hangi sunucuya bağlı olunduğu bir destek
 * sorusunda ilk sorulan şey ve okunması zararsız, değiştirilmesi ise artık mümkün değil.
 */
class AccountViewModel(private val session: Session, private val settings: Settings) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AccountUiState(user = session.user.value, serverAddress = settings.serverAddress()),
    )
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            session.user.collect { user -> _uiState.value = _uiState.value.copy(user = user) }
        }
    }

    fun onEvent(event: AccountEvent) {
        when (event) {
            AccountEvent.LogoutAsked -> _uiState.value = _uiState.value.copy(logoutAsked = true)
            AccountEvent.LogoutDismissed -> _uiState.value = _uiState.value.copy(logoutAsked = false)

            // Oturum düştüğü anda `HakedisNavHost` giriş ekranına iner; ekranı burada kapatmaya
            // gerek yok, kabuk zaten yeniden kurulur.
            AccountEvent.LogoutConfirmed -> viewModelScope.launch { session.logout() }
        }
    }
}
