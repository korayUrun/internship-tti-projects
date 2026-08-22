package tr.com.tti.hakedis.server.belge

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tr.com.tti.hakedis.server.web.GecersizIstekException

/**
 * Bozuk/yanlış dosyanın **400** ürettiğini kilitler.
 *
 * Bu yol sessizce açıktaydı: POI, xlsx olmayan bir dosyayı `NotOfficeXmlFileException` ile — yani bir
 * **RuntimeException** ile — reddediyor; okuyucu yalnızca `IOException` yakaladığı için istek 500'e
 * düşüyor ve kullanıcı "İşlem tamamlanamadı, yönetici ile iletişime geçin" görüyordu. Oysa yapması
 * gereken tek şey doğru dosyayı seçmek.
 */
class MiktarOkuyucuTest {
    @Test
    fun `xlsx olmayan dosya anlasilir hata verir`() {
        val hata = assertThrows<GecersizIstekException> {
            MiktarOkuyucu.oku("bu bir xlsx degil".toByteArray(), null)
        }

        assertThat(hata.message).contains("geçerli bir .xlsx değil")
    }

    @Test
    fun `PDF icerikli dosya da anlasilir hata verir`() {
        // Adı `.xlsx` olan ama içeriği PDF olan dosya — yanlış eki seçmenin en sık hâli.
        val pdf = "%PDF-1.7\n%sahte".toByteArray()

        val hata = assertThrows<GecersizIstekException> { MiktarOkuyucu.oku(pdf, null) }

        assertThat(hata.message).contains("geçerli bir .xlsx değil")
    }

    @Test
    fun `bos dosya anlasilir hata verir`() {
        val hata = assertThrows<GecersizIstekException> { MiktarOkuyucu.oku(ByteArray(0), null) }

        assertThat(hata.message).contains("geçerli bir .xlsx değil")
    }
}
