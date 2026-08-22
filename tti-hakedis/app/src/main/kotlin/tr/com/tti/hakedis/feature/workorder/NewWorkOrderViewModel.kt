package tr.com.tti.hakedis.feature.workorder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tr.com.tti.hakedis.data.Session
import tr.com.tti.hakedis.data.api.CableDto
import tr.com.tti.hakedis.data.api.EndpointRequest
import tr.com.tti.hakedis.data.api.HakedisApi
import tr.com.tti.hakedis.data.api.ScopeDto
import tr.com.tti.hakedis.data.api.WarningDto
import tr.com.tti.hakedis.data.api.WorkOrderRequest
import tr.com.tti.hakedis.data.api.userMessage
import tr.com.tti.hakedis.feature.LoadState

/**
 * Yeni iş. Dört alan kaldı; **doğrulamanın kararı sunucudadır** (`WorkOrderValidator`) — buradaki
 * kontrol yalnızca "Kaydet" düğmesini erken açmamak içindir.
 *
 * Faz N/O'da üç alan ekrandan çıktı: **bölge** oturumdan gelir, **SP No** sunucu üretir
 * (`2026-BURSA-001`), **sözleşme** artık işin değil her belgenin kendi seçimi.
 */
data class NewWorkOrderUiState(
    val loadState: LoadState = LoadState.Loading,
    val scopes: List<ScopeDto> = emptyList(),
    val cables: List<CableDto> = emptyList(),
    val regionCode: String = "",
    val regionName: String = "",
    val scopeCode: String = "",
    /** Kutu `mtc` ön dolu açılır: envanterdeki her kimlik bu önekle başlıyor. */
    val cableQuery: String = VARSAYILAN_ARAMA,
    val searching: Boolean = false,
    val selectedCable: CableDto? = null,
    /**
     * Yeni Tesis projesinde elle girilen uç konumları. Anahtarlar sunucudaki `KonumYolu.ALANLAR`
     * ile birebir aynı; yolu (`>>`, bina `>` kat) **sunucu** kurar, mobil ayraç birleştirmez.
     *
     * ~~Faz W: uçlar opsiyoneldi~~ — 12 Ağustos 2026'dan beri **ülke/şehir/adres zorunlu** ve Kablo
     * ID'yi sistem üretir (mtc…), kullanıcı yazmaz.
     */
    val endpointA: Map<String, String> = emptyMap(),
    val endpointB: Map<String, String> = emptyMap(),
    val route: String = "",
    val ekAraligi: String = "",
    val saving: Boolean = false,
    val error: String? = null,
    /**
     * Proje oluştu → ekran "Bu proje açıldı" penceresini gösterir; kapatma yolu proje listesine
     * gitmektir (kullanıcı isteği, 4 Ağustos 2026).
     *
     * ~~Oluşur oluşmaz listeye dönülür, onay diyaloğu yok~~ (D1/5) — üretilen SP No'yu kullanıcıya
     * gösteren tek an burasıydı ve liste anında açılınca numara görülmeden kayboluyordu.
     */
    val createdId: Long? = null,
    /** Sunucunun ürettiği proje numarası; açılış penceresinde gösterilir. */
    val createdSpNo: String? = null,
    val warnings: List<WarningDto> = emptyList(),
) {
    /**
     * Kablo bu kapsamda zorunlu mu — **sunucu söyler** (`ScopeDto.kableZorunlu`, Faz W). Mobil
     * `kod == "YENI_TESIS"` diye kendi listesini tutmaz (CLAUDE.md §7/1).
     *
     * Kapsam seçilmemişken zorunlu sayılıyor: sunucudaki `WorkOrderValidator` de öyle davranıyor ve
     * ikisi ayrışsaydı düğme açık görünüp istek 400 alırdı.
     */
    val cableRequired: Boolean
        get() = scopes.firstOrNull { it.kod == scopeCode }?.kableZorunlu ?: true

    /** Uç segmentleri yalnızca kablonun zorunlu olmadığı kapsamda sorulur (orada seçim de yok). */
    val endpointsManual: Boolean get() = !cableRequired

    val missing: Boolean
        get() = scopeCode.isBlank() ||
            (cableRequired && selectedCable == null) ||
            route.isBlank() ||
            ekAraligi.isBlank() ||
            // Yeni Tesis'te Kablo ID sistemce atanır; onun yerine uç adresleri (ülke/şehir/adres) zorunlu.
            (endpointsManual && (!ucTam(endpointA) || !ucTam(endpointB)))

    /** Sunucu bu uzunluğun altındaki aramada boş liste döner (`KableSorgulari.EN_AZ_ARAMA`). */
    val queryTooShort: Boolean get() = cableQuery.trim().length < EN_AZ_ARAMA

    /**
     * Liste sınıra dayandı: sonuçlar kırpılmış olabilir ve kullanıcıya söylenir.
     *
     * Sessiz kırpma, aradığı kabloyu listede göremeyen kullanıcıya "bu kablo sistemde yok"
     * dedirtiyordu — oysa `mtc2` gibi kısa bir arama yüzlerce kabloyla eşleşiyor.
     */
    val cablesTruncated: Boolean get() = cables.size >= ARAMA_SINIRI

    /**
     * Segmentlerden istek gövdesi. Yeni Tesis'te uçlar zorunlu ve daima gönderilir (boş segmentleri
     * sunucu zaten yola katmaz); envanterli kapsamda `null` — uçlar envanterden gelir.
     */
    fun uc(segmentler: Map<String, String>): EndpointRequest? {
        if (!endpointsManual) return null
        val dolu = segmentler.filterValues { it.isNotBlank() }
        return EndpointRequest(dolu)
    }

    /** Bir uçta zorunlu üç segmentin (ülke/şehir/adres) hepsi dolu mu? */
    private fun ucTam(segmentler: Map<String, String>): Boolean = ZORUNLU_UC_ALANLARI.all { segmentler[it].orEmpty().isNotBlank() }

    companion object {
        const val VARSAYILAN_ARAMA = "mtc"
        const val EN_AZ_ARAMA = 3

        /** Bir aramada gösterilen en fazla kablo; envanterin tamamı 900+ satır. */
        const val ARAMA_SINIRI = 50

        /**
         * Yeni Tesis'te bir uçta dolu olması zorunlu segmentler — sunucudaki
         * `WorkOrderValidator.ZORUNLU_UC_ALANLARI` ve web `ZORUNLU_UC_ALANLARI` ile aynı.
         */
        val ZORUNLU_UC_ALANLARI = listOf("country", "city", "address")
    }
}

sealed interface NewWorkOrderEvent {
    data class ScopeChanged(val code: String) : NewWorkOrderEvent

    /** Yeni Tesis projesinde bir uç segmentinin değişmesi. [yon] `"A"` ya da `"B"`. */
    data class EndpointChanged(val yon: String, val alan: String, val deger: String) : NewWorkOrderEvent

    data class CableQueryChanged(val query: String) : NewWorkOrderEvent

    data class CableSelected(val cable: CableDto?) : NewWorkOrderEvent

    data class RouteChanged(val value: String) : NewWorkOrderEvent

    data class EkAraligiChanged(val value: String) : NewWorkOrderEvent

    data object Submitted : NewWorkOrderEvent

    data object Retry : NewWorkOrderEvent
}

class NewWorkOrderViewModel(private val api: HakedisApi, private val session: Session) : ViewModel() {

    private val _uiState = MutableStateFlow(NewWorkOrderUiState(regionCode = session.user.value?.bolgeKodu.orEmpty()))
    val uiState: StateFlow<NewWorkOrderUiState> = _uiState.asStateFlow()

    /** Yürüyen kable araması; yeni tuş vuruşu geldiğinde iptal edilir. */
    private var searchJob: Job? = null

    init {
        load()
    }

    fun onEvent(event: NewWorkOrderEvent) {
        when (event) {
            is NewWorkOrderEvent.ScopeChanged -> _uiState.update { it.copy(scopeCode = event.code) }
            is NewWorkOrderEvent.EndpointChanged -> _uiState.update { durum ->
                if (event.yon == "A") {
                    durum.copy(endpointA = durum.endpointA + (event.alan to event.deger))
                } else {
                    durum.copy(endpointB = durum.endpointB + (event.alan to event.deger))
                }
            }
            is NewWorkOrderEvent.CableQueryChanged -> {
                _uiState.update { it.copy(cableQuery = event.query, selectedCable = null) }
                searchCables(event.query)
            }

            is NewWorkOrderEvent.CableSelected -> _uiState.update { it.copy(selectedCable = event.cable) }
            is NewWorkOrderEvent.RouteChanged -> _uiState.update { it.copy(route = event.value) }
            is NewWorkOrderEvent.EkAraligiChanged -> _uiState.update { it.copy(ekAraligi = event.value) }
            NewWorkOrderEvent.Submitted -> submit()
            NewWorkOrderEvent.Retry -> load()
        }
    }

    private fun load() {
        _uiState.update { it.copy(loadState = LoadState.Loading, error = null) }
        viewModelScope.launch {
            runCatching { Lists(scopes = api.scopes(), regionNames = api.regions().associate { it.kod to it.ad }) }
                .onSuccess { lists ->
                    _uiState.update { state ->
                        state.copy(
                            loadState = LoadState.Ready,
                            scopes = lists.scopes,
                            regionName = lists.regionNames[state.regionCode] ?: state.regionCode,
                        )
                    }
                }
                .onFailure { error ->
                    if (session.expiredSession(error)) return@onFailure
                    _uiState.update { it.copy(loadState = LoadState.Failed, error = error.userMessage(TAG)) }
                }
        }
    }

    /**
     * Kable araması **sunucuda** yapılır (Faz O).
     *
     * Envanterin tamamı artık çekilmiyor: bölge kısıtı kalktığı için liste 900+ satıra çıktı ve VPN
     * üzerinden her açılışta indirmek anlamsız. Önceki arama iptal edilir — yavaş bir yanıt, sonradan
     * yazılmış bir aramanın sonucunu ezmemeli.
     */
    private fun searchCables(query: String) {
        searchJob?.cancel()
        if (query.trim().length < NewWorkOrderUiState.EN_AZ_ARAMA) {
            _uiState.update { it.copy(cables = emptyList(), searching = false) }
            return
        }

        _uiState.update { it.copy(searching = true) }
        searchJob = viewModelScope.launch {
            delay(ARAMA_BEKLEME_MS)
            runCatching { api.cables(query.trim(), NewWorkOrderUiState.ARAMA_SINIRI) }
                .onSuccess { list -> _uiState.update { it.copy(cables = list, searching = false) } }
                .onFailure { error ->
                    if (session.expiredSession(error)) return@onFailure
                    _uiState.update { it.copy(cables = emptyList(), searching = false, error = error.userMessage(TAG)) }
                }
        }
    }

    private fun submit() {
        val state = _uiState.value
        if (state.missing || state.saving) return

        _uiState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                api.createWorkOrder(
                    WorkOrderRequest(
                        kapsamKodu = state.scopeCode,
                        // Zorunlu kapsamda envanterden seçilen kablonun nosid'i; Yeni Tesis'te Kablo
                        // ID'yi **sunucu üretir** (mtc…), istemci göndermez.
                        kableNosid = if (state.cableRequired) state.selectedCable?.nosid else null,
                        route = state.route.trim(),
                        ekAraligi = state.ekAraligi.trim(),
                        // Yeni Tesis'te uçlar zorunlu ve gönderilir; envanterli kapsamda `null` (uçlar
                        // envanterden gelir, sunucu istekteki segmentleri zaten yok sayar).
                        ucA = state.uc(state.endpointA),
                        ucB = state.uc(state.endpointB),
                    ),
                )
            }.onSuccess { created ->
                // Uyarılar proje oluştuktan SONRA gösterilir ve hiçbir şeyi durdurmaz (D1/4): aynı
                // kablo üzerinde birden fazla proje meşrudur.
                _uiState.update {
                    it.copy(saving = false, createdId = created.id, createdSpNo = created.spNo, warnings = created.uyarilar)
                }
            }.onFailure { error ->
                if (session.expiredSession(error)) return@onFailure
                _uiState.update { it.copy(saving = false, error = error.userMessage(TAG)) }
            }
        }
    }

    private data class Lists(val scopes: List<ScopeDto>, val regionNames: Map<String, String>)

    private companion object {
        const val TAG = "NewWorkOrderViewModel"

        /** Her tuş vuruşunda VPN üzerinden istek atılmasın. */
        const val ARAMA_BEKLEME_MS = 300L
    }
}
