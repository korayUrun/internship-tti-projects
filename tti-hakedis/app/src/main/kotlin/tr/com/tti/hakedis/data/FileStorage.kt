package tr.com.tti.hakedis.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class FileStorageException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Yükleme ve indirme sırasında kullanılan **geçici** dosyalar.
 *
 * Uygulama Faz J'de ince istemciye döndü: ek dosyalar ve çıktılar sunucuda yaşıyor, cihazda
 * kalıcı kopya tutulmuyor. Bu yüzden her şey `cacheDir` altında — seçilen dosya yüklenene kadar,
 * indirilen belge paylaşılana kadar durur.
 *
 * Dış `content://` URI'leri kalıcı değildir (CLAUDE.md §12/4), bu yüzden seçilen dosya **hemen**
 * kopyalanır. Görseller ayrıca küçültülür: tabletten çekilen bir fotoğraf 8 MB olabiliyor; VPN
 * üzerinden yüklenmesi hem yavaş, hem de sunucunun 10 MB sınırına yakın.
 */
class FileStorage(private val context: Context) {

    /** Yüklenmeyi bekleyen ekler ve kamera çıktıları. */
    private val uploadDir: File
        get() = File(context.cacheDir, "temp").apply { mkdirs() }

    /** Sunucudan indirilen PDF/Excel ve ek dosyalar; paylaşım buradan yapılır. */
    val downloadDir: File
        get() = File(context.cacheDir, "indirilen").apply { mkdirs() }

    /** Seçilen dosyanın kullanıcıya görünen adı; bulunamazsa URI'nin son parçası. */
    fun displayName(uri: Uri): String {
        val fromResolver = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
        return fromResolver ?: uri.lastPathSegment?.substringAfterLast('/').orEmpty()
    }

    /** EK1: güzergâh dosyası olduğu gibi kopyalanır (içeriğe dokunulmaz). */
    suspend fun copyForUpload(uri: Uri, fileName: String): File = withContext(Dispatchers.IO) {
        val target = File(uploadDir, "${UUID.randomUUID()}-$fileName")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: throw FileStorageException("Dosya açılamadı: $uri")
        } catch (e: Exception) {
            Log.e(TAG, "Ek dosyası kopyalanamadı", e)
            target.delete()
            throw FileStorageException("Dosya kopyalanamadı.", e)
        }
        target
    }

    /** EK2: görsel küçültülüp JPEG olarak hazırlanır (hedef ≤ 1,5 MB). */
    suspend fun prepareImage(uri: Uri): File = withContext(Dispatchers.IO) {
        val temp = File(uploadDir, "in-${UUID.randomUUID()}")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: throw FileStorageException("Görsel açılamadı: $uri")
            compress(temp)
        } catch (e: Exception) {
            Log.e(TAG, "Görsel hazırlanamadı", e)
            throw FileStorageException("Görsel eklenemedi.", e)
        } finally {
            temp.delete()
        }
    }

    /** Kameradan gelen dosya da aynı küçültme sürecinden geçer. */
    suspend fun prepareCameraImage(file: File): File = withContext(Dispatchers.IO) {
        try {
            compress(file)
        } catch (e: Exception) {
            Log.e(TAG, "Kamera görseli hazırlanamadı", e)
            throw FileStorageException("Fotoğraf kaydedilemedi.", e)
        } finally {
            file.delete()
        }
    }

    fun createCameraFile(): File = File(uploadDir, "camera-${UUID.randomUUID()}.jpg")

    /** Paylaşım ve harici görüntüleyici için `content://` URI (CLAUDE.md §12/2). */
    fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /**
     * İnen dosyayı kullanıcının seçtiği hedefe (`ACTION_CREATE_DOCUMENT` sonucu) kopyalar.
     *
     * Sistem kaydetme ekranı kullanılıyor: izin gerektirmez ve minSdk 26'da da çalışır. Doğrudan
     * `İndirilenler`e yazmak `MediaStore.Downloads` ister, o da API 29+ — hedef tabletlerin sürümü
     * bilinmediği için (açık soru 9) minSdk yükseltmek yerine her sürümde çalışan yol seçildi.
     */
    suspend fun copyToUri(source: File, target: Uri) = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(target)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw FileStorageException("Hedef dosya açılamadı: $target")
        } catch (e: Exception) {
            Log.e(TAG, "Dosya kaydedilemedi: ${source.name}", e)
            throw FileStorageException("Dosya kaydedilemedi.", e)
        }
    }

    fun delete(file: File) {
        runCatching { file.delete() }.onFailure { Log.e(TAG, "Dosya silinemedi: ${file.name}", it) }
    }

    /** [keepDays] günden eski geçici dosyaları siler. Cihazda korunması gereken veri yok. */
    suspend fun cleanupOldFiles(keepDays: Long = DEFAULT_KEEP_DAYS) = withContext(Dispatchers.IO) {
        val threshold = System.currentTimeMillis() - keepDays * MILLIS_PER_DAY
        var deleted = 0
        listOf(uploadDir, downloadDir).forEach { directory ->
            directory.listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < threshold && file.delete()) deleted++
            }
        }
        if (deleted > 0) Log.i(TAG, "$deleted eski geçici dosya silindi (> $keepDays gün)")
    }

    private fun compress(source: File): File {
        val target = File(uploadDir, "${UUID.randomUUID()}.jpg")
        val bitmap = decodeScaled(source, MAX_IMAGE_DIMENSION)
            ?: throw FileStorageException("Görsel okunamadı: ${source.name}")
        val oriented = applyExifRotation(source, bitmap)

        try {
            var quality = INITIAL_QUALITY
            do {
                target.outputStream().use { output -> oriented.compress(Bitmap.CompressFormat.JPEG, quality, output) }
                quality -= QUALITY_STEP
            } while (target.length() > MAX_IMAGE_BYTES && quality >= MIN_QUALITY)
        } finally {
            oriented.recycle()
            if (oriented !== bitmap) bitmap.recycle()
        }
        return target
    }

    private fun decodeScaled(file: File, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= maxDimension || bounds.outHeight / (sampleSize * 2) >= maxDimension) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    }

    /** Kamera görselleri EXIF ile döndürülmüş gelebilir; sunucuda yan yatmasın diye düzeltilir. */
    private fun applyExifRotation(source: File, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            when (ExifInterface(source.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)

        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private companion object {
        const val TAG = "FileStorage"
        const val MAX_IMAGE_BYTES = 1_500_000L
        const val MAX_IMAGE_DIMENSION = 2048
        const val INITIAL_QUALITY = 90
        const val MIN_QUALITY = 50
        const val QUALITY_STEP = 10
        const val DEFAULT_KEEP_DAYS = 7L
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}
