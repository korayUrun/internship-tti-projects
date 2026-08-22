package tr.com.tti.hakedis.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tr.com.tti.hakedis.data.Session
import tr.com.tti.hakedis.data.Settings
import tr.com.tti.hakedis.data.api.userMessage

/**
 * Giriş ekranının durumu.
 *
 * Parola **hiçbir yere yazılmaz** (karar J2) — yalnızca kullanıcı adı son girişten hatırlanır.
 *
 * ~~Sunucu adresi burada ayarlanır (karar J1)~~ — **kaldırıldı** (kullanıcı isteği, 19 Ağustos
 * 2026): adres derleme zamanında sabit (`res/values/config.xml`), ekranda alanı yok.
 */
data class LoginUiState(
    val userName: String = "",
    val password: String = "",
    val busy: Boolean = false,
    /** Sunucudan gelen hata mesajı, olduğu gibi (`HataYakalayici` Türkçe ve eyleme dönük yazıyor). */
    val error: String? = null,
) {
    val canSubmit: Boolean get() = !busy && userName.isNotBlank() && password.isNotEmpty()
}

sealed interface LoginEvent {
    data class UserNameChanged(val value: String) : LoginEvent

    data class PasswordChanged(val value: String) : LoginEvent

    data object Submitted : LoginEvent
}

class LoginViewModel(private val session: Session, private val settings: Settings) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState(userName = settings.lastUserName()))
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEvent(event: LoginEvent) {
        when (event) {
            is LoginEvent.UserNameChanged -> _uiState.update { it.copy(userName = event.value, error = null) }
            is LoginEvent.PasswordChanged -> _uiState.update { it.copy(password = event.value, error = null) }
            LoginEvent.Submitted -> submit()
        }
    }

    private fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return

        _uiState.update { it.copy(busy = true, error = null) }

        viewModelScope.launch {
            runCatching { session.login(state.userName.trim(), state.password) }
                .onSuccess {
                    // Parola bellekte de tutulmaz; oturum açıldıktan sonra alanın hiçbir işlevi yok.
                    _uiState.update { it.copy(busy = false, password = "") }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(busy = false, error = error.userMessage(TAG)) }
                }
        }
    }

    private companion object {
        const val TAG = "LoginViewModel"
    }
}
