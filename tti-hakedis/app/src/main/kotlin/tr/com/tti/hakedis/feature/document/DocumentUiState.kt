package tr.com.tti.hakedis.feature.document

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import tr.com.tti.hakedis.data.api.AttachmentDto
import tr.com.tti.hakedis.data.api.AttachmentType
import tr.com.tti.hakedis.data.api.ContractDto
import tr.com.tti.hakedis.data.api.DocumentDto
import tr.com.tti.hakedis.data.api.OutputFormat
import tr.com.tti.hakedis.data.api.WorkOrderDto
import tr.com.tti.hakedis.domain.document.DocumentType
import tr.com.tti.hakedis.domain.document.DocumentVariant
import tr.com.tti.hakedis.domain.pricing.QuantityError
import tr.com.tti.hakedis.feature.LoadState
import java.io.File

/**
 * Tablodaki tek satırın görüntülenecek hâli.
 *
 * Tutarlar `PricingCalculator` ile **türetilip** biçimlendirilmiş metin olarak gelir; Composable
 * hesap yapmaz (CLAUDE.md §13). Mobil sunucuyla **aynı domain sınıflarını** çağırdığı için sonuç
 * sunucununkiyle birebir aynıdır — web'den ayrıldığı tek nokta budur: web hesap yapamaz ve tutarı
 * sunucudan hazır alır, mobil aynı kodu çalıştırdığı için anında geri bildirim verebilir.
 */
data class PozRowUi(
    val id: String,
    val pozNo: String,
    val name: String,
    val unit: String,
    val unitPriceText: String,
    val isGroup: Boolean,
    val isPriced: Boolean,
    val quantityText: String,
    val amountText: String,
    val quantityError: QuantityError? = null,
)

/**
 * EK3 "TTI Kullanılan Malzemeler" satırının ekrandaki hâli — üç alan da **ham metin**.
 *
 * Miktar sayıya çevrilmez: kullanıcı `12,` yazarken alan zıplamamalı ve ayrıştırma zaten sunucuda
 * (CLAUDE.md §5). Sınıfın `data class` olması bilinçli — kaydedilmiş anlık görüntüyle karşılaştırma
 * liste eşitliğiyle yapılıyor (webdeki `JSON.stringify` karşılaştırmasının tipli karşılığı).
 */
data class MaterialRowUi(val ad: String = "", val miktar: String = "", val seriNo: String = "") {
    /** Tamamen boş satır kaydedilmez (sunucu de eler) ve "kaydedilmemiş değişiklik" saymaz. */
    val blank: Boolean get() = ad.isBlank() && miktar.isBlank() && seriNo.isBlank()
}

/** Hangi malzeme alanı değişti — üç ayrı olay yerine tek olay: `copy` dalı tek yerde kalsın. */
enum class MaterialField {
    AD,
    MIKTAR,
    SERI_NO,
}

/**
 * Gönderme ön koşulu. Kural **sunucudadır**; bu liste onu tekrar yazmaz, önceden bildirir.
 *
 * ⚠️ **Kaydedilmemiş EK3 satırı buraya eklenmez.** Bu enum'un her değeri [DocumentUiState.canSubmit]
 * üzerinden "Gönder" düğmesini **kapatıyor**; malzeme kuralı ise tam tersini istiyor: düğme açık
 * kalır ve uyarı `gonder`/`submit` anında verilir (19 Ağustos 2026 kullanıcı bildirimi — kapalı
 * düğmeye tıklamak hiçbir geri bildirim üretmiyor). Taşıyıcısı
 * [DocumentUiState.materialsUnsaved] + [LocalError.UNSAVED_MATERIALS].
 */
enum class SubmitBlocker {
    NO_QUANTITY,
    INVALID_QUANTITY,
    MISSING_EK1,
}

/**
 * Sunucuya hiç gitmeden, cihazda verilen ret kararı.
 *
 * Sunucudan gelen hatalar metin olarak taşınır (`ApiException` mesajı olduğu gibi gösterilir), ama
 * bu karar bize ait: metni ViewModel yazamaz (CLAUDE.md §1/6 — görünen metin `strings.xml`'de).
 */
enum class LocalError {
    /** Seçilen EK1 dosyasının uzantısı `.kmz`/`.kml` değil. */
    INVALID_ROUTE_EXTENSION,

    /** EK3'te kaydedilmemiş satır varken "Gönder"e basıldı; gönderme durdu (19 Ağustos 2026). */
    UNSAVED_MATERIALS,
}

/** İndirilmiş çıktı: paylaşıma ve önizlemeye hazır. */
data class DownloadedOutput(
    val file: File,
    val format: OutputFormat,
    /** PDF'in ilk sayfası — `PdfPreviewRenderer` ile üretilir; Excel'de `null`. */
    val preview: ImageBitmap? = null,
    val pageCount: Int = 0,
)

data class DocumentUiState(
    val loadState: LoadState = LoadState.Loading,
    /** "Gözlemle" ile açıldı: hiçbir giriş açık değil, yalnızca çıktı bölümü kullanılabilir. */
    val readOnly: Boolean = false,
    val document: DocumentDto? = null,
    val workOrder: WorkOrderDto? = null,
    val rows: List<PozRowUi> = emptyList(),
    val totalText: String = "",
    val query: String = "",
    val onlyFilled: Boolean = false,
    val filledRowCount: Int = 0,
    /** Geçersiz miktar taşıyan pozların numaraları — üstteki şerit bunları yazar. */
    val invalidPozNos: List<String> = emptyList(),
    val saving: Boolean = false,
    val unsaved: Boolean = false,
    val attachments: List<AttachmentDto> = emptyList(),
    val attachmentBusy: Boolean = false,
    /** EK3 satırları — yalnızca hakedişte doldurulur (bkz. [showsMaterials]). */
    val materials: List<MaterialRowUi> = emptyList(),
    val materialsBusy: Boolean = false,
    /** Ekrandaki liste sunucudakinden farklı mı. **[SubmitBlocker] değildir**; oradaki uyarıya bak. */
    val materialsUnsaved: Boolean = false,
    /** "Malzeme listesi kaydedildi." şeridi; sonraki değişiklikte söner. */
    val materialsSaved: Boolean = false,
    /** Belge ekranının üstündeki sözleşme seçimi için liste; yalnızca değiştirme açılınca çekilir. */
    val contracts: List<ContractDto> = emptyList(),
    val contractChanging: Boolean = false,
    /** Miktar şablonu indiriliyor / yükleniyor. */
    val quantityFileBusy: Boolean = false,
    /** İnen miktar şablonu — paylaşım/açma için. */
    val quantityTemplate: File? = null,
    val submitting: Boolean = false,
    val submitted: Boolean = false,
    val downloading: Boolean = false,
    val downloaded: DownloadedOutput? = null,
    /** Kullanıcının seçtiği hedefe kaydedilen dosyanın adı — "kaydedildi" bildirimi için. */
    val savedFileName: String? = null,
    /** Sunucudan gelen hata mesajı — olduğu gibi gösterilir, yeniden yazılmaz. */
    val error: String? = null,
    /** Cihazda verilen ret kararı; metni ekran çözer. */
    val localError: LocalError? = null,
) {
    /**
     * Düzenlenebilirlik **sunucudan gelir** (`DocumentStageRule.duzenlenebilirMi` sonucu). Buradaki
     * tek ek koşul, ekranın gözlem modunda açılmış olması.
     *
     * ~~Kural burada da yazılıydı~~ (Faz V'de kalktı): belge durumu + işin durumu ikilisi sunucuda,
     * webde ve burada üç kez duruyordu. `ONAY_BEKLIYOR` eklenirken biri unutulsaydı ekran kilitli
     * görünür ama sunucu yazmayı kabul ederdi — sessiz hata (CLAUDE.md §7/1).
     */
    val editable: Boolean get() = !readOnly && document?.duzenlenebilir == true

    /**
     * "Gönder" düğmesi **anlamlı mı** — kararı yine sunucu veriyor (`BelgeYaniti.gonderilebilir`),
     * web de aynı alanı okuyor (`belge.gonderilebilir && !salt`).
     *
     * Bugün sunucuda `gonderilebilir = duzenlenebilir`, yani davranış [editable] ile aynı. Ayrı
     * durmasının nedeni: alan çözülüp **hiç okunmuyordu** ve sunucu ikisini ayırdığı gün mobil
     * sessizce yanlış ekranı gösterirdi (CLAUDE.md §7/1). Girişlerin açıklığı hâlâ [editable].
     */
    val submitVisible: Boolean get() = !readOnly && document?.gonderilebilir == true

    /** EK3 yalnızca hakedişte (18 Ağustos 2026); sunucu diğer türlerde yazmayı 409 ile reddeder. */
    val showsMaterials: Boolean get() = document?.tur == DocumentType.HAKEDIS

    val hasRouteFile: Boolean get() = attachments.any { it.tur == AttachmentType.EK1_ROTA }

    val images: List<AttachmentDto> get() = attachments.filter { it.tur == AttachmentType.EK2_GORSEL }

    /**
     * Gönderme ön koşulları. Güvence **sunucuda** (`BelgeServisi`); bu liste kullanıcı 409 duvarına
     * toslamadan neyin eksik olduğunu görsün diye var.
     */
    val blockers: List<SubmitBlocker>
        get() = buildList {
            if (filledRowCount == 0) add(SubmitBlocker.NO_QUANTITY)
            if (invalidPozNos.isNotEmpty()) add(SubmitBlocker.INVALID_QUANTITY)
            // Güzergâh dosyası artık üç belge türünde de zorunlu (Faz P).
            if (!hasRouteFile) add(SubmitBlocker.MISSING_EK1)
        }

    val canSubmit: Boolean get() = submitVisible && !submitting && !saving && blockers.isEmpty()

    /**
     * Hakedişte tek çıktı üretilir; fiyatlı/fiyatsız ayrımı yalnızca fizibilite ve keşifte var
     * (Düzeltme Turu 1 kararı). Sunucu ucu değişmedi, gösterilen seçenek değişti.
     */
    val variants: List<DocumentVariant>
        get() = if (document?.tur == DocumentType.HAKEDIS) listOf(DocumentVariant.FIYATLI) else DocumentVariant.entries
}

sealed interface DocumentEvent {
    data class QuantityChanged(val pozId: String, val text: String) : DocumentEvent

    data class QueryChanged(val query: String) : DocumentEvent

    data class OnlyFilledChanged(val enabled: Boolean) : DocumentEvent

    data object Submitted : DocumentEvent

    data class RouteFilePicked(val uri: Uri) : DocumentEvent

    data class ImagesPicked(val uris: List<Uri>) : DocumentEvent

    data object CameraRequested : DocumentEvent

    data class CameraFinished(val success: Boolean) : DocumentEvent

    data class AttachmentRemoved(val id: Long) : DocumentEvent

    data class MaterialChanged(val index: Int, val field: MaterialField, val value: String) : DocumentEvent

    data object MaterialRowAdded : DocumentEvent

    data class MaterialRowRemoved(val index: Int) : DocumentEvent

    /** Malzemeler **açık düğmeyle** kaydedilir; miktarlardaki gibi debounce yok (bkz. ViewModel). */
    data object MaterialsSaveRequested : DocumentEvent

    /** Sözleşme seçici açıldı; liste ilk açılışta çekilir. */
    data object ContractEditRequested : DocumentEvent

    data class ContractChanged(val contractCode: String) : DocumentEvent

    data object QuantityTemplateRequested : DocumentEvent

    data class QuantityFilePicked(val uri: Uri) : DocumentEvent

    data object QuantityTemplateDismissed : DocumentEvent

    data class OutputRequested(val format: OutputFormat, val variant: DocumentVariant) : DocumentEvent

    data object DownloadDismissed : DocumentEvent

    /** Sistem kaydetme ekranından dönen hedef; inen dosya oraya kopyalanır. */
    data class SaveRequested(val target: Uri) : DocumentEvent

    data object ErrorDismissed : DocumentEvent

    data object Retry : DocumentEvent

    /**
     * Ekrana geri dönüldüğünde ve "Yenile"de. [Retry]'dan farkı: kaydedilmemiş giriş varsa
     * **atlanır** — yenileme kullanıcının yazdığı miktarı ezmemeli.
     */
    data object Refresh : DocumentEvent
}
