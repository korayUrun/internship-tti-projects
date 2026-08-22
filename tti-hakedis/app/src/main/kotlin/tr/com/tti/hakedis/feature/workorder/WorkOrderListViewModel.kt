package tr.com.tti.hakedis.feature.workorder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tr.com.tti.hakedis.data.FileStorage
import tr.com.tti.hakedis.data.Session
import tr.com.tti.hakedis.data.api.ContractDto
import tr.com.tti.hakedis.data.api.HakedisApi
import tr.com.tti.hakedis.data.api.WorkOrderDto
import tr.com.tti.hakedis.data.api.WorkOrderStatusDto
import tr.com.tti.hakedis.data.api.userMessage
import tr.com.tti.hakedis.domain.document.DocumentType
import tr.com.tti.hakedis.feature.LoadState
import java.io.File

/**
 * İş listesi — uygulamanın **çalışma ekranı** (Faz S, web ile parite).
 *
 * Her iş tek bir kart: üstte tanımlayıcılar, altında üç aşama satırı. Ayrı bir "İş Detayı" ekranı
 * yok; oradaki her bilgi buraya sığıyordu ve ikinci bir tık gerektiriyordu.
 *
 * Süzgeç **sunucuda** uygulanır (`GET /api/isler?durum=`); cihazda liste süzülmez — yetki süzgeci
 * zaten SQL'de ve ikinci bir süzme kuralı iki yerde bakım demek olurdu.
 */
data class WorkOrderListUiState(
    val loadState: LoadState = LoadState.Loading,
    val items: List<WorkOrderDto> = emptyList(),
    val status: WorkOrderStatusDto? = null,
    /** Sözleşme seçici açıkken hangi (iş, aşama) için açıldığı. */
    val contractPrompt: ContractPrompt? = null,
    val contracts: List<ContractDto> = emptyList(),
    /**
     * `kapsam.kod → ad` eşlemesi. İşin yanıtında yalnızca **kod** var (`DEPLASE_IYILESTIRME`);
     * okunabilir ad `/api/kapsamlar`'da. Sunucu da kendi Excel'inde aynı eşlemeyi kuruyor
     * (`IsServisi`), mobil onu tekrar etmiyor — aynı uçtan alıyor.
     */
    val scopeNames: Map<String, String> = emptyMap(),
    /** Belge açıldığında ekranın oraya gitmesi için tetikleyici; okunduktan sonra temizlenir. */
    val openDocumentId: Long? = null,
    val busy: Boolean = false,
    /** İş listesi Excel'i indiriliyor. */
    val exporting: Boolean = false,
    /** İnen dosya; ekran paylaşım penceresini açtıktan sonra temizler. */
    val exported: File? = null,
    val error: String? = null,
)

/** "Güncelle" henüz açılmamış bir aşamada tıklandığında sorulan sözleşme. */
data class ContractPrompt(val workOrderId: Long, val type: DocumentType)

sealed interface WorkOrderListEvent {
    data class StatusChanged(val status: WorkOrderStatusDto?) : WorkOrderListEvent

    /** Aşama düğmesi: belge yoksa sözleşme sorar, varsa doğrudan açar. */
    data class StageClicked(val workOrderId: Long, val type: DocumentType, val documentId: Long?) : WorkOrderListEvent

    data class ContractChosen(val contractCode: String) : WorkOrderListEvent

    data object ContractPromptDismissed : WorkOrderListEvent

    data object DocumentOpened : WorkOrderListEvent

    /** "İşleri Excel olarak indir" — dosya sunucuda üretilir, cihaz indirip paylaşır. */
    data object ExportRequested : WorkOrderListEvent

    data object ExportShared : WorkOrderListEvent

    data object Refresh : WorkOrderListEvent
}

class WorkOrderListViewModel(private val api: HakedisApi, private val session: Session, private val fileStorage: FileStorage) :
    ViewModel() {

    private val _uiState = MutableStateFlow(WorkOrderListUiState())
    val uiState: StateFlow<WorkOrderListUiState> = _uiState.asStateFlow()

    init {
        load()
        loadScopeNames()
    }

    fun onEvent(event: WorkOrderListEvent) {
        when (event) {
            is WorkOrderListEvent.StatusChanged -> {
                _uiState.update { it.copy(status = event.status) }
                load()
            }

            is WorkOrderListEvent.StageClicked ->
                if (event.documentId != null) {
                    _uiState.update { it.copy(openDocumentId = event.documentId) }
                } else {
                    _uiState.update { it.copy(contractPrompt = ContractPrompt(event.workOrderId, event.type)) }
                    loadContracts()
                }

            is WorkOrderListEvent.ContractChosen -> createDocument(event.contractCode)

            WorkOrderListEvent.ContractPromptDismissed -> _uiState.update { it.copy(contractPrompt = null, error = null) }

            WorkOrderListEvent.DocumentOpened -> _uiState.update { it.copy(openDocumentId = null, contractPrompt = null) }

            WorkOrderListEvent.ExportRequested -> export()

            WorkOrderListEvent.ExportShared -> _uiState.update { it.copy(exported = null) }

            WorkOrderListEvent.Refresh -> load()
        }
    }

    private fun load() {
        _uiState.update { it.copy(loadState = LoadState.Loading, error = null) }
        viewModelScope.launch {
            runCatching { api.workOrders(_uiState.value.status) }
                .onSuccess { list -> _uiState.update { it.copy(loadState = LoadState.Ready, items = list) } }
                .onFailure { error ->
                    if (session.expiredSession(error)) return@onFailure
                    _uiState.update { it.copy(loadState = LoadState.Failed, error = error.userMessage(TAG)) }
                }
        }
    }

    /**
     * Kapsam adları bir kez çekilir (liste sabit sayılır, oturum boyunca değişmiyor).
     *
     * ⚠️ Hata **yutulur**: kapsam adı bir süs, listenin kendisi değil. `/api/kapsamlar` düşerse kart
     * ham koda geri düşer (`scopeNames[kod] ?: kod`) ve proje listesi çalışmaya devam eder — burada
     * `error` yazmak, asıl veri gelmişken ekrana ilgisiz bir hata şeridi asmak olurdu.
     */
    private fun loadScopeNames() {
        viewModelScope.launch {
            val kapsamlar = runCatching { api.scopes() }.getOrNull() ?: return@launch
            _uiState.update { it.copy(scopeNames = kapsamlar.associate { kapsam -> kapsam.kod to kapsam.ad }) }
        }
    }

    /** Sözleşme listesi yalnızca seçici açılınca çekilir; liste kısa ve nadiren değişiyor. */
    private fun loadContracts() {
        if (_uiState.value.contracts.isNotEmpty()) return
        viewModelScope.launch {
            runCatching { api.contracts() }
                .onSuccess { list -> _uiState.update { it.copy(contracts = list) } }
                .onFailure { error ->
                    if (session.expiredSession(error)) return@onFailure
                    _uiState.update { it.copy(error = error.userMessage(TAG)) }
                }
        }
    }

    private fun createDocument(contractCode: String) {
        val prompt = _uiState.value.contractPrompt ?: return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching { api.createDocument(prompt.workOrderId, prompt.type, contractCode) }
                .onSuccess { belge -> _uiState.update { it.copy(busy = false, contractPrompt = null, openDocumentId = belge.id) } }
                .onFailure { error ->
                    if (session.expiredSession(error)) return@onFailure
                    _uiState.update { it.copy(busy = false, error = error.userMessage(TAG)) }
                }
        }
    }

    /** Dosya ekrandaki süzgeçle iner: kullanıcı ne görüyorsa onu indirmiş olmalı. */
    private fun export() {
        _uiState.update { it.copy(exporting = true, exported = null, error = null) }
        viewModelScope.launch {
            runCatching { api.downloadWorkOrderList(_uiState.value.status, fileStorage.downloadDir) }
                .onSuccess { file -> _uiState.update { it.copy(exporting = false, exported = file) } }
                .onFailure { error ->
                    if (session.expiredSession(error)) return@onFailure
                    _uiState.update { it.copy(exporting = false, error = error.userMessage(TAG)) }
                }
        }
    }

    private companion object {
        const val TAG = "WorkOrderListViewModel"
    }
}
