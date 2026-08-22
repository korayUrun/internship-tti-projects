package tr.com.tti.hakedis.export

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Önizleme ekranında gösterilecek PDF küçük resmi. */
data class PdfPreview(val firstPage: Bitmap, val pageCount: Int)

/**
 * Üretilen PDF'in ilk sayfasını görsele çevirir (Faz 5 önizleme).
 * Android'in kendi `PdfRenderer`'ı kullanılır; ek bir kütüphane gerekmez.
 */
class PdfPreviewRenderer {

    suspend fun render(file: File, targetWidthPx: Int = DEFAULT_WIDTH_PX): PdfPreview? = withContext(Dispatchers.IO) {
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    renderer.openPage(0).use { page ->
                        val height = (targetWidthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(targetWidthPx, height, Bitmap.Config.ARGB_8888)
                        // PDF şeffaf alanlar içerebilir; kâğıt beyazı zemin veriyoruz.
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        PdfPreview(firstPage = bitmap, pageCount = renderer.pageCount)
                    }
                }
            }
        }.onFailure { Log.e(TAG, "PDF önizlemesi oluşturulamadı: ${file.name}", it) }.getOrNull()
    }

    private companion object {
        const val TAG = "PdfPreviewRenderer"
        const val DEFAULT_WIDTH_PX = 700
    }
}
