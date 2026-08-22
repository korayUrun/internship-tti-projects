package tr.com.tti.hakedis.feature.document

import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tr.com.tti.hakedis.data.FileStorage
import tr.com.tti.hakedis.data.Session
import tr.com.tti.hakedis.data.api.AttachmentType
import tr.com.tti.hakedis.data.api.DocumentDto
import tr.com.tti.hakedis.data.api.HakedisApi
import tr.com.tti.hakedis.data.api.MaterialDto
import tr.com.tti.hakedis.data.api.MaterialEntry
import tr.com.tti.hakedis.data.api.OutputFormat
import tr.com.tti.hakedis.data.api.PozDto
import tr.com.tti.hakedis.data.api.RowEntry
import tr.com.tti.hakedis.data.api.WorkOrderDto
import tr.com.tti.hakedis.data.api.userMessage
import tr.com.tti.hakedis.domain.document.DocumentVariant
import tr.com.tti.hakedis.domain.pricing.MoneyFormatter
import tr.com.tti.hakedis.domain.pricing.PricedRow
import tr.com.tti.hakedis.domain.pricing.PricingCalculator
import tr.com.tti.hakedis.domain.pricing.QuantityInput
import tr.com.tti.hakedis.domain.pricing.QuantityParser
import tr.com.tti.hakedis.domain.workorder.RouteFileValidator
import tr.com.tti.hakedis.export.PdfPreviewRenderer
import tr.com.tti.hakedis.feature.LoadState
import java.io.File
import java.math.BigDecimal
import java.util.Locale

/**
 * Belge ekranı — üç belge türü için **tek** ViewModel.
 *
 * Akış üç türde de aynı; üç ayrı ekran yazmak aynı tabloyu üç kez bakımda tutmak olurdu. Fiyat
 * kaynağı da dallanmıyor: her belge **kendi** sözleşmesinin yayındaki sürümüne çivili ve poz listesi
 * oradan geliyor. ~~keşif/hakediş işin pazarlık fiyatlarını gösterir~~ — işe özel fiyat listesi
 * Faz N'de kaldırıldı.
 *
 * Ekler **üç belge türünde de** var (Faz P): güzergâh dosyası zorunlu, görsel opsiyonel.
 * EK3 malzeme listesi **yalnızca hakedişte** (Faz Y).
 */
class DocumentViewModel(
    private val api: HakedisApi,
    private val session: Session,
    private val fileStorage: FileStorage,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val documentId: Long = checkNotNull(savedStateHandle["id"]) { "Belge kimliği rotadan gelmedi" }

    private val pdfPreviewRenderer = PdfPreviewRenderer()

    /** Poz listesi ve kullanıcının yazdığı ham metinler — ekranın kaynağı. */
    private var pozItems: List<PozDto> = emptyList()
    private var inputs: Map<String, String> = emptyMap()

    private var saveJob: Job? = null
    private var pendingCameraFile: File? = null

    /**
     * Sunucudaki malzeme listesinin anlık görüntüsü — webdeki `kayitli` JSON dizesinin tipli hâli.
     * Ekrandaki liste bundan farklıysa kaydedilmemiş değişiklik var demektir; [MaterialRowUi] bir
     * `data class` olduğu için liste eşitliği `JSON.stringify` karşılaştırmasını bedavaya veriyor.
     */
    private var savedMaterials: List<MaterialRowUi> = emptyList()

    private val _uiState = MutableStateFlow(DocumentUiState())
    val uiState: StateFlow<DocumentUiState> = _uiState.asStateFlow()

    /** Kamera hedefi: `TakePicture` sözleşmesi çekimden **önce** yazılacak URI'yi ister. */
    private val _cameraUri = MutableStateFlow<Uri?>(null)
    val cameraUri: StateFlow<Uri?> = _cameraUri.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: DocumentEvent) {
        when (event) {
            is DocumentEvent.QuantityChanged -> onQuantityChanged(event.pozId, event.text)
            is DocumentEvent.QueryChanged -> _uiState.update { it.copy(query = event.query).withRows() }
            is DocumentEvent.OnlyFilledChanged -> _uiState.update { it.copy(onlyFilled = event.enabled).withRows() }
            DocumentEvent.Submitted -> submit()
            is DocumentEvent.RouteFilePicked -> uploadRouteFile(event.uri)
            is DocumentEvent.ImagesPicked -> uploadImages(event.uris)
            DocumentEvent.CameraRequested -> requestCamera()
            is DocumentEvent.CameraFinished -> onCameraFinished(event.success)
            is DocumentEvent.AttachmentRemoved -> removeAttachment(event.id)
            is DocumentEvent.MaterialChanged -> onMaterialChanged(event.index, event.field, event.value)
            DocumentEvent.MaterialRowAdded -> updateMaterials { it + MaterialRowUi() }
            is DocumentEvent.MaterialRowRemoved -> updateMaterials { rows -> rows.filterIndexed { i, _ -> i != event.index } }
            DocumentEvent.MaterialsSaveRequested -> saveMaterials()
            DocumentEvent.ContractEditRequested -> loadContracts()
            is DocumentEvent.ContractChanged -> changeContract(event.contractCode)
            DocumentEvent.QuantityTemplateRequested -> downloadQuantityTemplate()
            is DocumentEvent.QuantityFilePicked -> uploadQuantities(event.uri)
            DocumentEvent.QuantityTemplateDismissed -> _uiState.update { it.copy(quantityTemplate = null) }
            is DocumentEvent.OutputRequested -> download(event.format, event.variant)
            DocumentEvent.DownloadDismissed -> _uiState.update { it.copy(downloaded = null, savedFileName = null) }
            is DocumentEvent.SaveRequested -> saveTo(event.target)
            DocumentEvent.ErrorDismissed -> _uiState.update { it.copy(error = null, localError = null) }
            DocumentEvent.Retry -> load()
            DocumentEvent.Refresh -> refreshIfIdle()
        }
    }

    /**
     * Ekrana geri dönüldüğünde ve "Yenile" ile çağrılır.
     *
     * ⚠️ **Kaydedilmemiş giriş varken yüklenmez.** [load] `inputs`'u sunucudan gelenle eziyor;
     * kullanıcı miktar yazıp henüz kaydedilmemişken ekran yenilenirse yazdığı sessizce kaybolurdu.
     * Bekleyen yazma zaten 600 ms içinde kendiliğinden tamamlanıyor, o zaman yenileme serbest kalır.
     *
     * ⚠️ **Malzemeler için aynısı, ama tehlikesi daha büyük:** EK3 satırları debounce'lu değil, açık
     * düğmeyle kaydediliyor — yani "kendiliğinden tamamlanma" yok. Ekran `LifecycleResumeEffect` ile
     * yenilendiği için kameradan, foto seçiciden, dosya seçiciden ve sistem kaydetme ekranından
     * **her dönüşte** buraya geliniyor; guard olmasa yazılmış malzeme satırları hiçbir hata
     * vermeden kaybolurdu. Web'de karşılığı yok (orada ekran yeniden yüklenmiyor).
     */
    private fun refreshIfIdle() {
        val state = _uiState.value
        if (state.unsaved || state.saving || state.submitting) return
        if (state.materialsUnsaved || state.materialsBusy) return
        load()
    }

    // --- Yükleme ------------------------------------------------------------

    private fun load() {
        _uiState.update { it.copy(loadState = LoadState.Loading, error = null) }
        viewModelScope.launch {
            runCatching {
                val document = api.document(documentId)
                val workOrder = api.workOrder(document.isId)
                // Poz listesi belgenin **kendi** fiyat sürümünden gelir; türe göre dallanma yok —
                // ayrım zaten `belge.fiyat_versiyonu_id`'de.
                val items = api.documentItems(document.id)
                Loaded(document, workOrder, items)
            }.onSuccess { loaded ->
                pozItems = loaded.items
                inputs = loaded.document.satirlar.associate { it.pozId to displayQuantity(it.miktar) }
                _uiState.update {
                    it.copy(
                        loadState = LoadState.Ready,
                        document = loaded.document,
                        workOrder = loaded.workOrder,
                        unsaved = false,
                    ).withRows()
                }
                // Ekler üç belge türünde de var (Faz P): güzergâh dosyası zorunlu, görsel opsiyonel.
                refreshAttachments()
                // EK3 yalnızca hakedişte çekilir: uç diğer türlerde de boş liste dönüyor ama VPN
                // üzerinden gereksiz bir gidiş-geliş olurdu. Gözlem modunda da çekilir.
                savedMaterials = emptyList()
                _uiState.update {
                    it.copy(materials = emptyList(), materialsUnsaved = false, materialsSaved = false)
                }
                if (_uiState.value.showsMaterials) refreshMaterials()
            }.onFailure { error ->
                if (session.expiredSession(error)) return@onFailure
                _uiState.update { it.copy(loadState = LoadState.Failed, error = error.userMessage(TAG)) }
            }
        }
    }

    private suspend fun refreshAttachments() {
        runCatching { api.attachments(documentId) }
            .onSuccess { attachments -> _uiState.update { it.copy(attachments = attachments) } }
            .onFailure { error ->
                if (!session.expiredSession(error)) _uiState.update { it.copy(error = error.userMessage(TAG)) }
            }
    }

    // --- EK3 malzemeleri (Faz Y) --------------------------------------------

    private suspend fun refreshMaterials() {
        runCatching { api.materials(documentId) }
            .onSuccess { list ->
                val rows = list.map { it.toRowUi() }
                savedMaterials = rows
                _uiState.update { it.copy(materials = rows, materialsUnsaved = false, materialsSaved = false) }
            }
            .onFailure { error ->
                if (!session.expiredSession(error)) _uiState.update { it.copy(error = error.userMessage(TAG)) }
            }
    }

    /**
     * Sunucu miktarı ham metin veriyor (`12.5`); ekranda `12,5` yazılır — poz miktarıyla aynı
     * gösterim. Web bunu yapamıyor (sayı biçimlendirmesi domain'de), mobil aynı domain sınıfını
     * çağırdığı için yapabiliyor. [savedMaterials] de bu dönüşümden geçtiği için karşılaştırma
     * tutarlı: `12,5` yaz → kaydet → sunucu `12.5` döner → ekranda `12,5` → "kaydedilmemiş" sönük.
     */
    private fun MaterialDto.toRowUi() = MaterialRowUi(
        ad = ad.orEmpty(),
        miktar = miktar?.let(::displayQuantity).orEmpty(),
        seriNo = seriNo.orEmpty(),
    )

    private fun onMaterialChanged(index: Int, field: MaterialField, value: String) = updateMaterials { rows ->
        rows.mapIndexed { i, row ->
            if (i != index) {
                row
            } else {
                when (field) {
                    MaterialField.AD -> row.copy(ad = value)
                    MaterialField.MIKTAR -> row.copy(miktar = value)
                    MaterialField.SERI_NO -> row.copy(seriNo = value)
                }
            }
        }
    }

    /**
     * Malzeme satırları **debounce'lu kaydedilmez** — miktarlardan ayrıldığı tek nokta. Yazma tam
     * değiştirme olduğu için debounce, yarısı yazılmış bir satırın sunucuya gidip listeyi ezmesi
     * demek olurdu; kayıt açık bir düğmeyle yapılıyor (web ile birebir).
     *
     * `localError` burada **temizlenmez**: her tuş vuruşunda ilgisiz bir `INVALID_ROUTE_EXTENSION`
     * şeridini yutardı.
     */
    private fun updateMaterials(transform: (List<MaterialRowUi>) -> List<MaterialRowUi>) {
        _uiState.update { state ->
            val rows = transform(state.materials)
            state.copy(
                materials = rows,
                // Tamamen boş satır sayılmaz: "yeni malzeme ekle" ile açılıp doldurulmayan satır
                // zaten kaydedilmiyor (sunucu eliyor); gönderimi ona takmak kullanıcıyı boş yere
                // durdururdu.
                materialsUnsaved = rows.filterNot { it.blank } != savedMaterials,
                materialsSaved = false,
            )
        }
    }

    private fun saveMaterials() {
        if (!_uiState.value.editable) return
        _uiState.update { it.copy(materialsBusy = true, error = null, localError = null, materialsSaved = false) }
        viewModelScope.launch {
            // Kırpma ve boş satır eleme **sunucuda** (`MalzemeIstegi.coz`); kuralı burada
            // tekrarlamak ikinci bir kural üretirdi (CLAUDE.md §7/1). Ham metin gider.
            val gonderilecek = _uiState.value.materials.map { MaterialEntry(it.ad, it.miktar, it.seriNo) }
            runCatching { api.writeMaterials(documentId, gonderilecek) }
                .onSuccess { list ->
                    // Sunucu boş satırları eleyip temizlenmiş listeyi döner; ekran onunla tazelenir
                    // ki kullanıcı **neyin kaydedildiğini** görsün.
                    val rows = list.map { it.toRowUi() }
                    savedMaterials = rows
                    _uiState.update {
                        it.copy(
                            materials = rows,
                            materialsBusy = false,
                            materialsUnsaved = false,
                            materialsSaved = true,
                        )
                    }
                }
                .onFailure { error ->
                    if (session.expiredSession(error)) return@onFailure
                    _uiState.update { it.copy(materialsBusy = false, error = error.userMessage(TAG)) }
                }
        }
    }

    // --- Sözleşme (Faz N) ---------------------------------------------------

    /** Sözleşme listesi yalnızca değiştirme açılınca çekilir; liste kısa ve nadiren değişiyor. */
    private fun loadContracts() {
        if (_uiState.value.contracts.isNotEmpty()) return
        viewModelScope.launch {
            runCatching { api.contracts() }
                .onSuccess { list -> _uiState.update { it.copy(contracts = list) } }
                .onFailure { error ->
                    if (!session.expiredSession(error)) _uiState.update { it.copy(error = error.userMessage(TAG)) }
                }
        }
    }

    /**
     * Sözleşmeyi değiştirir. Sunucu **girilmiş miktarları siler** (poz kimlikleri sözleşmeye özel),
     * bu yüzden ekran baştan yüklenir: hem yeni poz listesi hem boşalan miktarlar gelsin.
     */
    private fun changeContract(contractCode: String) {
        _uiState.update { it.copy(contractChanging = true, error = null) }
        viewModelScope.launch {
            runCatching { api.changeContract(documentId, contractCode) }
                .onSuccess {
                    _uiState.update { it.copy(contractChanging = false) }
                    load()
                }
                .onFailure { error ->
                    if (session.expiredSession(error)) return@onFailure
                    _uiState.update { it.copy(contractChanging = false, error = error.userMessage(TAG)) }
                }
        }
    }

    // --- Miktar Excel'i (Faz P) ---------------------------------------------

    /** Şablon **girilmiş miktarlarla dolu** iner: kullanıcı yalnızca değişecekleri düzeltir. */
    private fun downloadQuantityTemplate() {
        _uiState.update { it.copy(quantityFileBusy = true, error = null) }
        viewModelScope.launch {
            runCatching { api.downloadQuantityTemplate(documentId, fileStorage.downloadDir) }
                .onSuccess { file -> _uiState.update { it.copy(quantityFileBusy = false, quantityTemplate = file) } }
                .onFailure { error ->
                    if (session.expiredSession(error)) return@onFailure
                    _uiState.update { it.copy(quantityFileBusy = false, error = error.userMessage(TAG)) }
                }
        }
    }

    /**
     * Doldurulmuş şablonu yükler — **tam değiştirme**. Sunucu tek sorunlu satırda hiçbir şey yazmaz
     * ve satır satır neyin bozuk olduğunu döner; mesaj olduğu gibi gösterilir.
     */
    private fun uploadQuantities(uri: Uri) {
        _uiState.update { it.copy(quantityFileBusy = true, error = null, localError = null) }
        viewModelScope.launch {
            val dosyaAdi = fileStorage.displayName(uri).ifBlank { "miktar.xlsx" }
            runCatching { api.uploadQuantities(documentId, fileStorage.copyForUpload(uri, dosyaAdi), dosyaAdi) }
                .onSuccess {
                    _uiState.update { it.copy(quantityFileBusy = false) }
                    load()
                }
                .onFailure { error ->
                    if (session.expiredSession(error)) return@onFailure
                    _uiState.update { it.copy(quantityFileBusy = false, error = error.userMessage(TAG)) }
                }
        }
    }

    // --- Miktar girişi ------------------------------------------------------

    /**
     * Ham metin ekranın kaynağıdır (kullanıcı "12," yazarken alan zıplamasın); çözümlenmiş değer
     * sunucuya gider. Yazma **beklemeli** (debounce): her tuş vuruşunda VPN üzerinden istek atmak
     * hem yavaş hem gereksiz.
     */
    private fun onQuantityChanged(pozId: String, text: String) {
        inputs = inputs + (pozId to text)
        _uiState.update { it.copy(unsaved = true).withRows() }

        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            save()
        }
    }

    /**
     * Miktarları yazar ve **başarılıysa `true` döner.**
     *
     * Çağıranın (özellikle [submit]) sonucu bilmesi zorunlu: geçersiz ya da kaydedilememiş bir
     * değişiklikle gönderilen belge geri alınamaz (D2) ve kullanıcının son yazdığı miktarı içermez.
     * Bu, web'de canlı testte yakalanan 1 numaralı hatanın aynısı (Düzeltme Turu 1).
     */
    private suspend fun save(): Boolean {
        val invalid = inputs.filterValues { QuantityParser.parse(it) is QuantityInput.Invalid }.keys
        if (invalid.isNotEmpty()) {
            _uiState.update { it.copy(invalidPozNos = pozNosOf(invalid), saving = false) }
            return false
        }

        // Boş bırakılan poz gönderilmez: sunucu tam değiştirme yapıyor, yani gönderilmeyen satır
        // belgeden silinir. "Girilmemiş" ile "0 girilmiş" ayrımı böyle korunur (CLAUDE.md §5).
        val rows = inputs.mapNotNull { (pozId, text) ->
            (QuantityParser.parse(text) as? QuantityInput.Valid)?.let { RowEntry(pozId, it.value.toPlainString()) }
        }

        _uiState.update { it.copy(saving = true, invalidPozNos = emptyList(), error = null) }
        return runCatching { api.writeRows(documentId, rows) }
            .onSuccess { document ->
                _uiState.update { it.copy(saving = false, unsaved = false, document = document).withRows() }
            }
            .onFailure { error ->
                if (!session.expiredSession(error)) {
                    _uiState.update { it.copy(saving = false, error = error.userMessage(TAG)) }
                }
            }
            .isSuccess
    }

    private fun submit() {
        // EK3 kaydedilmemişse gönderme DURUR ve sebebi yazılır. Kontrol düğmeyi kapatmak yerine
        // burada: kapalı düğmeye tıklamak hiçbir geri bildirim üretmiyor ve kullanıcı "uyarmadı"
        // diye görüyor (kullanıcı bildirimi, 19 Ağustos 2026). Diğer eksikler düğmeyi kapatmaya
        // devam ediyor — onlar veri eksikliği, bu ise "kaydetmeyi unuttun" ve tek tıkla düzeltiliyor.
        //
        // `canSubmit`'ten **önce**: düğme bir gün tıklanabilir hâle gelse bile davranış web ile aynı kalsın.
        if (_uiState.value.materialsUnsaved) {
            _uiState.update { it.copy(localError = LocalError.UNSAVED_MATERIALS) }
            return
        }
        if (!_uiState.value.canSubmit) return
        // Bekleyen bir yazma varsa iptal edilir: gönderme, son yazılan miktarı içermelidir.
        saveJob?.cancel()

        _uiState.update { it.copy(submitting = true, error = null, localError = null) }
        viewModelScope.launch {
            if (!save()) {
                _uiState.update { it.copy(submitting = false) }
                return@launch
            }
            runCatching { api.submit(documentId) }
                .onSuccess { document ->
                    _uiState.update { it.copy(submitting = false, submitted = true, document = document).withRows() }
                }
                .onFailure { error ->
                    if (session.expiredSession(error)) return@onFailure
                    _uiState.update { it.copy(submitting = false, error = error.userMessage(TAG)) }
                }
        }
    }

    // --- Ekler --------------------------------------------------------------

    /** Doğrulama **uzantı üzerinden**: `.kmz`/`.kml` MIME tipleri cihazlar arasında tutarsız (§12/3). */
    private fun uploadRouteFile(uri: Uri) {
        val fileName = fileStorage.displayName(uri).ifBlank { "guzergah.kmz" }
        if (!RouteFileValidator.isAllowed(fileName)) {
            _uiState.update { it.copy(localError = LocalError.INVALID_ROUTE_EXTENSION) }
            return
        }
        upload(AttachmentType.EK1_ROTA, fileName) { fileStorage.copyForUpload(uri, fileName) }
    }

    private fun uploadImages(uris: List<Uri>) {
        uris.forEach { uri ->
            val fileName = fileStorage.displayName(uri).substringBeforeLast('.').ifBlank { "gorsel" } + ".jpg"
            upload(AttachmentType.EK2_GORSEL, fileName) { fileStorage.prepareImage(uri) }
        }
    }

    private fun upload(type: AttachmentType, fileName: String, prepare: suspend () -> File) {
        _uiState.update { it.copy(attachmentBusy = true, error = null, localError = null) }
        viewModelScope.launch {
            var prepared: File? = null
            runCatching {
                val file = prepare().also { prepared = it }
                api.uploadAttachment(documentId, type, file, fileName)
            }.onFailure { error ->
                if (!session.expiredSession(error)) _uiState.update { it.copy(error = error.userMessage(TAG)) }
            }
            // Yüklenen dosyanın cihazdaki kopyası tutulmaz: kaynak doğruluk yeri sunucu.
            prepared?.let(fileStorage::delete)
            _uiState.update { it.copy(attachmentBusy = false) }
            refreshAttachments()
        }
    }

    private fun requestCamera() {
        val file = fileStorage.createCameraFile()
        pendingCameraFile = file
        _cameraUri.value = fileStorage.uriFor(file)
    }

    private fun onCameraFinished(success: Boolean) {
        val file = pendingCameraFile
        pendingCameraFile = null
        _cameraUri.value = null

        if (!success || file == null) {
            file?.let(fileStorage::delete)
            return
        }
        upload(AttachmentType.EK2_GORSEL, "saha-${file.nameWithoutExtension}.jpg") {
            fileStorage.prepareCameraImage(file)
        }
    }

    private fun removeAttachment(id: Long) {
        _uiState.update { it.copy(attachmentBusy = true, error = null, localError = null) }
        viewModelScope.launch {
            runCatching { api.deleteAttachment(id) }
                .onFailure { error ->
                    if (!session.expiredSession(error)) _uiState.update { it.copy(error = error.userMessage(TAG)) }
                }
            _uiState.update { it.copy(attachmentBusy = false) }
            refreshAttachments()
        }
    }

    // --- Çıktı --------------------------------------------------------------

    /** PDF/Excel sunucuda üretilir; cihaz indirir, önizler ve paylaşır (CLAUDE.md §13). */
    private fun download(format: OutputFormat, variant: DocumentVariant) {
        _uiState.update { it.copy(downloading = true, downloaded = null, savedFileName = null, error = null) }
        viewModelScope.launch {
            runCatching { api.downloadOutput(documentId, format, variant, fileStorage.downloadDir) }
                .onSuccess { file ->
                    val preview = if (format == OutputFormat.PDF) pdfPreviewRenderer.render(file) else null
                    _uiState.update {
                        it.copy(
                            downloading = false,
                            downloaded = DownloadedOutput(
                                file = file,
                                format = format,
                                preview = preview?.firstPage?.asImageBitmap(),
                                pageCount = preview?.pageCount ?: 0,
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    if (session.expiredSession(error)) return@onFailure
                    _uiState.update { it.copy(downloading = false, error = error.userMessage(TAG)) }
                }
        }
    }

    /**
     * İnen dosyayı kullanıcının seçtiği hedefe kopyalar (sistem kaydetme ekranı).
     *
     * Diyalogdan vazgeçilmesi buraya hiç ulaşmaz — hedef `null` gelir ve ekran çağrı yapmaz.
     */
    private fun saveTo(target: Uri) {
        val file = _uiState.value.downloaded?.file ?: return
        viewModelScope.launch {
            runCatching { fileStorage.copyToUri(file, target) }
                .onSuccess { _uiState.update { it.copy(savedFileName = file.name, error = null) } }
                .onFailure { error -> _uiState.update { it.copy(error = error.userMessage(TAG)) } }
        }
    }

    // --- Türetme ------------------------------------------------------------

    /**
     * Satırlar ve toplam **state'te saklanmaz**, `UiState` üretilirken domain ile türetilir
     * (CLAUDE.md §7). Kullanılan sınıflar sunucudakiyle aynı: `PricingCalculator`, `MoneyFormatter`.
     */
    private fun DocumentUiState.withRows(): DocumentUiState {
        val quantities = inputs.mapNotNull { (id, text) ->
            (QuantityParser.parse(text) as? QuantityInput.Valid)?.let { id to it.value }
        }.toMap()

        // Toplama kuralı (satır bazında yuvarla, sonra topla) domain'de: yeniden yazılmaz.
        val pricedRows = pozItems.mapNotNull { item ->
            if (item.isGroup || !item.isPriced) return@mapNotNull null
            val quantity = quantities[item.id] ?: return@mapNotNull null
            PricedRow(BigDecimal(item.unitPrice), quantity)
        }

        return copy(
            rows = visibleItems(quantities).map { it.toRowUi(inputs[it.id].orEmpty()) },
            totalText = MoneyFormatter.money(PricingCalculator.total(pricedRows)),
            filledRowCount = quantities.size,
        )
    }

    /**
     * Süzgeç **alıcının** (yani üretilmekte olan yeni state'in) `query`/`onlyFilled` değerlerini
     * kullanır, `_uiState.value`'nun değil: `update { it.copy(query = …).withRows() }` çağrıldığında
     * akıştaki değer henüz eskidir ve arama bir tuş geriden gelirdi.
     */
    private fun DocumentUiState.visibleItems(quantities: Map<String, BigDecimal>): List<PozDto> {
        val normalized = query.trim().lowercase(TURKISH)
        val matching = pozItems.filter { item ->
            when {
                item.isGroup -> true
                onlyFilled && !quantities.containsKey(item.id) -> false
                normalized.isEmpty() -> true
                else ->
                    item.pozNo.lowercase(TURKISH).contains(normalized) ||
                        item.name.lowercase(TURKISH).contains(normalized)
            }
        }
        // Altında görünen kalemi kalmayan grup başlıklarını at.
        return matching.filterIndexed { index, item ->
            !item.isGroup || matching.getOrNull(index + 1)?.isGroup == false
        }
    }

    private fun PozDto.toRowUi(input: String): PozRowUi {
        val quantity = QuantityParser.parse(input)
        return PozRowUi(
            id = id,
            pozNo = pozNo,
            name = name,
            unit = unit,
            // Birim fiyat gösterimi **sunucuda** üretilir ve **2 haneye yuvarlıdır** (CLAUDE.md §5);
            // belgeye çivilenen fiyatla aynı değer.
            unitPriceText = if (isGroup || !isPriced) "" else unitPriceGosterim,
            isGroup = isGroup,
            isPriced = isPriced,
            quantityText = input,
            amountText = amountText(quantity),
            quantityError = (quantity as? QuantityInput.Invalid)?.error,
        )
    }

    private fun PozDto.amountText(quantity: QuantityInput): String {
        if (isGroup || !isPriced) return ""
        val value = (quantity as? QuantityInput.Valid)?.value ?: return ""
        return MoneyFormatter.money(PricingCalculator.rowAmount(BigDecimal(unitPrice), value))
    }

    private fun pozNosOf(ids: Set<String>): List<String> = pozItems.filter { it.id in ids }.map { it.pozNo }

    /** Sunucudan gelen ham miktarın ekranda görünecek hâli (`12.5` → `12,5`). */
    private fun displayQuantity(raw: String): String = runCatching { MoneyFormatter.quantity(BigDecimal(raw)) }.getOrDefault(raw)

    private data class Loaded(val document: DocumentDto, val workOrder: WorkOrderDto, val items: List<PozDto>)

    private companion object {
        const val TAG = "DocumentViewModel"

        /** Miktar yazarken her tuşta istek gitmesin diye bekleme süresi — web ile aynı. */
        const val SAVE_DELAY_MS = 600L

        /** Arama karşılaştırması kullanıcıya dönük olduğu için Türkçe locale (İ/ı doğru eşleşsin). */
        val TURKISH: Locale = Locale.forLanguageTag("tr-TR")
    }
}
