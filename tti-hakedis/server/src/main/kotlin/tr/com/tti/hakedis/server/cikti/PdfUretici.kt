package tr.com.tti.hakedis.server.cikti

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import com.openhtmltopdf.util.XRLog
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class PdfUretimHatasi(mesaj: String, sebep: Throwable? = null) : RuntimeException(mesaj, sebep)

/**
 * XHTML → PDF. Motor **openhtmltopdf** (LGPL 2.1); iText 5/7 AGPL olduğu için kullanılamaz
 * (CLAUDE.md §6 lisans notu).
 *
 * Şablonun kendisi `:core:domain`'deki `PdfTemplate`'tir — bu sınıf yalnızca bastırma işini yapar,
 * tek satır metin üretmez. Böylece "tek şablon, tek çıktı" kuralı sunucuya taşınırken bozulmaz.
 *
 * ### Font neden gömülüyor
 * PDF'in yerleşik (base-14) fontları `ş`, `ğ`, `İ` gliflerini **taşımaz**: gömülmezse bu harfler ya
 * kaybolur ya da kutu olarak basılır, üstelik metin araması da tutmaz. Konteyner imajında (JRE) sistem
 * fontu da bulunmaz. DejaVu Sans jar'ın içindedir; çalışma ortamına hiçbir şey kurulmaz.
 *
 * Kalın yüz **ayrıca** gömülür: motor sentetik kalınlaştırma yapmaz, kayıtlı tek yüz varsa `<th>` ve
 * grup satırları normal ağırlıkta basılır ve tablo düzleşir.
 */
@Component
class PdfUretici {
    /** Font baytları bir kez okunur; her üretimde jar'dan yeniden okumak boşuna I/O olurdu. */
    private val normalFont: ByteArray by lazy { fontOku(NORMAL_FONT) }
    private val kalinFont: ByteArray by lazy { fontOku(KALIN_FONT) }

    init {
        // Motor her belge için onlarca satır INFO döküyor (stil eşleştirme, font yükleme, süreler).
        // Bu gürültü sunucu log'unu doldurup gerçekten önemli olan WARN'ları görünmez kılıyor —
        // ör. arşiv kopyasının başarısız olduğunu söyleyen satırı.
        XRLog.setLoggingEnabled(false)
    }

    fun uret(xhtml: String): ByteArray {
        val cikti = ByteArrayOutputStream(BASLANGIC_TAMPONU)

        try {
            PdfRendererBuilder()
                .useFastMode()
                .useFont({ ByteArrayInputStream(normalFont) }, FONT_AILESI, NORMAL_AGIRLIK, BaseRendererBuilder.FontStyle.NORMAL, true)
                .useFont({ ByteArrayInputStream(kalinFont) }, FONT_AILESI, KALIN_AGIRLIK, BaseRendererBuilder.FontStyle.NORMAL, true)
                .withHtmlContent(xhtml, null)
                .toStream(cikti)
                .run()
        } catch (hata: Exception) {
            // Fail loud: yarım bir PDF döndürmek, kullanıcının bozuk dosyayı imzalı belge sanmasıdır.
            throw PdfUretimHatasi("PDF üretilemedi.", hata)
        }

        return cikti.toByteArray()
    }

    private fun fontOku(yol: String): ByteArray = javaClass.getResourceAsStream(yol)?.use { it.readBytes() }
        ?: throw PdfUretimHatasi("Gömülü font bulunamadı: $yol")

    private companion object {
        /** `PdfTemplate` CSS'indeki `font-family` ile **birebir aynı** olmalı. */
        const val FONT_AILESI = "DejaVu Sans"
        const val NORMAL_FONT = "/fonts/DejaVuSans.ttf"
        const val KALIN_FONT = "/fonts/DejaVuSans-Bold.ttf"
        const val NORMAL_AGIRLIK = 400
        const val KALIN_AGIRLIK = 700
        const val BASLANGIC_TAMPONU = 64 * 1024
    }
}
