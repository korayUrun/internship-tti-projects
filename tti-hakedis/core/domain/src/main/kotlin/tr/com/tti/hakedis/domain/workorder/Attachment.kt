package tr.com.tti.hakedis.domain.workorder

import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * Ek dosya türü ve **izin verilen uzantıları**.
 *
 * Doğrulama uzantı üzerinden yapılır, MIME tipi üzerinden değil: `.kmz`/`.kml` için MIME tipleri
 * cihazdan cihaza tutarsızdır (CLAUDE.md §12/3) ve tarayıcının/istemcinin gönderdiği `Content-Type`
 * istemci tarafından belirlendiği için sunucuda tek başına güvenilir bir ölçüt değildir.
 */
enum class AttachmentKind(val allowedExtensions: Set<String>) {
    /** EK1 — güzergâh dosyası (`.kmz` / `.kml`); hakedişte zorunlu, tekildir. */
    ROUTE_FILE(setOf("kmz", "kml")),

    /** EK2 — saha görseli, opsiyonel; adet sınırı yok. */
    IMAGE(setOf("jpg", "jpeg", "png")),
    ;

    fun accepts(fileName: String): Boolean = FileExtension.of(fileName) in allowedExtensions
}

/**
 * Dosya uzantısı çıkarımı — tek yer.
 *
 * Karşılaştırma teknik olduğu için **`Locale.ROOT`**: Türkçe locale `"I"` → `"ı"` yapıp `.KMZ`
 * uzantısını eşleşmez hâle getirirdi (CLAUDE.md §5).
 */
object FileExtension {
    fun of(fileName: String): String = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
}

/**
 * Uygulamanın kendi dizinine **kopyalanmış** ek dosya.
 * Dış `content://` URI'leri kalıcı olmadığı için saklanmaz (CLAUDE.md §12/4).
 *
 * @param storedPath `filesDir` altındaki mutlak yol
 */
@Serializable
data class Attachment(val id: String, val fileName: String, val storedPath: String, val sizeBytes: Long, val kind: AttachmentKind)

/**
 * EK1 doğrulaması — kural [AttachmentKind.ROUTE_FILE] üzerinde yaşar, bu nesne mobilin çağrı adıdır.
 * Sunucu ve mobil aynı uzantı kümesini kullanır; kural iki yerde yazılmaz (CLAUDE.md §7/1).
 */
object RouteFileValidator {
    fun isAllowed(fileName: String): Boolean = AttachmentKind.ROUTE_FILE.accepts(fileName)
}
