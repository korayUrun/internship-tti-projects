package tr.com.tti.hakedis.server.ek

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * İmzanın gerçekten PNG olduğunun kanıtı **içerikten** gelir, dosya adından değil.
 *
 * Bu kontrol atlanırsa hata yükleme anında değil, aylar sonra PDF üretilirken ortaya çıkar — o an
 * kullanıcı ekranda olmaz ve belgeyi kimse üretemez.
 */
class PngDogrulayiciTest {
    private val pngBasligi = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    @Test
    fun `PNG imzasiyla baslayan icerik kabul edilir`() {
        assertThat(PngDogrulayici.pngMi(pngBasligi + ByteArray(120))).isTrue()
    }

    @Test
    fun `adi png olan baska bir dosya reddedilir`() {
        // "imza.png" adıyla gönderilmiş bir PDF (%PDF-1.7) ve bir JPEG (FF D8 FF).
        assertThat(PngDogrulayici.pngMi("%PDF-1.7\n".toByteArray())).isFalse()
        assertThat(PngDogrulayici.pngMi(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))).isFalse()
    }

    @Test
    fun `bozulmus PNG basligi reddedilir`() {
        // Satır sonu dönüştüren bir aktarım `0D 0A` baytlarını bozar; PNG imzası bunu yakalasın diye
        // bu baytları taşır (RFC 2083 §3.1).
        val bozuk = pngBasligi.copyOf().also { it[4] = 0x0A }

        assertThat(PngDogrulayici.pngMi(bozuk)).isFalse()
    }

    @Test
    fun `bos ve kisa icerik reddedilir`() {
        assertThat(PngDogrulayici.pngMi(ByteArray(0))).isFalse()
        assertThat(PngDogrulayici.pngMi(pngBasligi.copyOf(4))).isFalse()
    }
}
