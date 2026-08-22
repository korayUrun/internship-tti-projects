package tr.com.tti.hakedis.feature.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tr.com.tti.hakedis.data.Session
import tr.com.tti.hakedis.data.api.DocumentListDto
import tr.com.tti.hakedis.data.api.HakedisApi
import tr.com.tti.hakedis.data.api.userMessage
import tr.com.tti.hakedis.domain.document.DocumentStatus
import tr.com.tti.hakedis.domain.document.DocumentType
import tr.com.tti.hakedis.feature.LoadState

data class DocumentArchiveUiState(
    val loadState: LoadState = LoadState.Loading,
    val items: List<DocumentListDto> = emptyList(),
    val type: DocumentType? = null,
    val status: DocumentStatus? = null,
    val error: String? = null,
)

sealed interface DocumentArchiveEvent {
    data object Refresh : DocumentArchiveEvent
    data class TypeChanged(val value: DocumentType?) : DocumentArchiveEvent
    data class StatusChanged(val value: DocumentStatus?) : DocumentArchiveEvent
}

/**
 * Belge arşivi — alt sekme çubuğunun ikinci sekmesi.
 *
 * Adı `DocumentViewModel` ile çakışmasın diye `Archive`: o, tek bir belgenin **düzenleme** ekranını
 * sürüyor. Buradan bir satıra dokunulduğunda aynı belge ekranı **salt okunur** açılır — indirme
 * bölümü orada zaten var, ikinci kez yazılmıyor.
 */
class DocumentArchiveViewModel(private val api: HakedisApi, private val session: Session) : ViewModel() {

    private val _uiState = MutableStateFlow(DocumentArchiveUiState())
    val uiState: StateFlow<DocumentArchiveUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: DocumentArchiveEvent) {
        when (event) {
            DocumentArchiveEvent.Refresh -> load()
            is DocumentArchiveEvent.TypeChanged -> {
                _uiState.value = _uiState.value.copy(type = event.value)
                load()
            }
            is DocumentArchiveEvent.StatusChanged -> {
                _uiState.value = _uiState.value.copy(status = event.value)
                load()
            }
        }
    }

    /**
     * ⚠️ Hata yakalama `runCatching` ile, tür daraltmadan.
     *
     * ~~`catch (hata: ApiException)`~~ yalnızca sunucunun döndürdüğü hataları tutuyordu; VPN
     * koptuğunda atılan `NetworkException` — yani **sahanın en olağan hatası** — coroutine'den kaçıp
     * uygulamayı `CrashActivity`'ye düşürüyordu. Diğer bütün ViewModel'ler zaten bu kalıpta.
     *
     * `userMessage(TAG)` ayrıca `Log.e` yazıyor: eski hâl `hata.message`'ı doğrudan gösterdiği için
     * mesajı `null` olan bir istisna ekranda **boş bir kutu** olarak görünürdü ve hiçbir iz kalmazdı
     * (CLAUDE.md §12/0 ve "fail loud").
     */
    private fun load() {
        val durum = _uiState.value
        viewModelScope.launch {
            // Liste boşaltılmıyor: tazelemede ekranın bir an "yükleniyor"a düşmesi titreme yaratıyor.
            _uiState.value = durum.copy(
                loadState = if (durum.items.isEmpty()) LoadState.Loading else durum.loadState,
                error = null,
            )
            runCatching { api.documents(durum.type, durum.status) }
                .onSuccess { liste ->
                    _uiState.value = _uiState.value.copy(loadState = LoadState.Ready, items = liste, error = null)
                }
                .onFailure { hata ->
                    if (session.expiredSession(hata)) return@onFailure
                    _uiState.value = _uiState.value.copy(
                        loadState = LoadState.Failed,
                        error = hata.userMessage(TAG),
                    )
                }
        }
    }

    private companion object {
        const val TAG = "DocumentArchiveViewModel"
    }
}
