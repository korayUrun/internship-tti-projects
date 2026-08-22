package tr.com.tti.hakedis.server.sozlesme

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tr.com.tti.hakedis.server.web.GecersizIstekException
import java.io.ByteArrayOutputStream
import java.math.BigDecimal

/**
 * Yüklenen dosyanın açılması ve sayfa seçimi — **iki ucun paylaştığı** kurallar (fiyat listesi ve
 * sözleşme yükleme).
 *
 * Buradaki testlerin varlık nedeni: POI'nin hata sınıfları tek bir hiyerarşide değil.
 * `NotOfficeXmlFileException` bir `RuntimeException`'dır, `IOException` değil; yalnızca `IOException`
 * yakalayan eski kod yönetici yanlış dosyayı seçtiğinde 500 + "Yönetici ile iletişime geçin"
 * döndürüyordu — kendisi yönetici olduğu hâlde. Kural artık tek yerde ve testlerle kilitli.
 */
class XlsxOkuyucuTest {
    private val poz = ReferansPoz(1, "r3", "1.1", "Direk Dikme-montaj", "Ad.", grupMu = false, referansFiyat = BigDecimal("100"))

    private fun tekSayfali(): ByteArray = PozSablonu.uret("Başlık", "Tek Sayfa", listOf(poz))

    /** İki sayfalı bir kitap: fastexcel tek sayfa yazıyor, ikincisi POI ile ekleniyor. */
    private fun cokSayfali(): ByteArray = org.apache.poi.xssf.usermodel.XSSFWorkbook(tekSayfali().inputStream()).use { kitap ->
        kitap.createSheet("İkinci Sayfa")
        ByteArrayOutputStream().also { kitap.write(it) }.toByteArray()
    }

    @Test
    fun `tek sayfali dosyada sayfa kendiliginden secilir`() {
        val ad = XlsxOkuyucu.sayfada(tekSayfali(), sayfaAdi = null) { it.sheetName }

        assertThat(ad).isEqualTo("Tek Sayfa")
    }

    /**
     * Çok sayfalı dosyada sayfa **zorunlu**: yanlış sayfayı sessizce okuyup yanlış fiyatları yazmak,
     * kullanıcıya sayfa adı sormaktan çok daha kötü.
     */
    @Test
    fun `cok sayfali dosyada sayfa adi zorunlu ve secenekler mesajda`() {
        val hata = assertThrows<GecersizIstekException> {
            XlsxOkuyucu.sayfada(cokSayfali(), sayfaAdi = null) { it.sheetName }
        }

        assertThat(hata.message).contains("birden fazla sayfa")
        assertThat(hata.message).contains("Tek Sayfa")
        assertThat(hata.message).contains("İkinci Sayfa")
    }

    @Test
    fun `olmayan sayfa adi 400 uretir ve mevcut sayfalari sayar`() {
        val hata = assertThrows<GecersizIstekException> {
            XlsxOkuyucu.sayfada(cokSayfali(), sayfaAdi = "Yok Böyle") { it.sheetName }
        }

        assertThat(hata.message).contains("Yok Böyle")
        assertThat(hata.message).contains("İkinci Sayfa")
    }

    @Test
    fun `verilen sayfa adi secilir`() {
        val ad = XlsxOkuyucu.sayfada(cokSayfali(), sayfaAdi = "İkinci Sayfa") { it.sheetName }

        assertThat(ad).isEqualTo("İkinci Sayfa")
    }

    /** xlsx olmayan dosya: POI `RuntimeException` fırlatır, 500 değil **400** dönmeli. */
    @Test
    fun `xlsx olmayan dosya 400 uretir`() {
        val hata = assertThrows<GecersizIstekException> {
            XlsxOkuyucu.sayfada("bu bir xlsx degil".toByteArray(), sayfaAdi = null) { it.sheetName }
        }

        assertThat(hata.message).contains("geçerli bir .xlsx değil")
    }

    @Test
    fun `bos dosya 400 uretir`() {
        val hata = assertThrows<GecersizIstekException> {
            XlsxOkuyucu.sayfada(ByteArray(0), sayfaAdi = null) { it.sheetName }
        }

        assertThat(hata.message).contains("boş")
    }

    @Test
    fun `sayfa adlari listelenir`() {
        assertThat(XlsxOkuyucu.sayfaAdlari(cokSayfali())).containsExactly("Tek Sayfa", "İkinci Sayfa").inOrder()
    }
}
