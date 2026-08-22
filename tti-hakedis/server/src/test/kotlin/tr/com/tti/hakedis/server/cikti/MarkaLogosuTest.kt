package tr.com.tti.hakedis.server.cikti

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import tr.com.tti.hakedis.server.ek.PngDogrulayici
import java.util.Base64

/**
 * Logo **isteğe bağlı** bir varlıktır: dosya yokken de belge üretilmeli.
 *
 * Bu testin asıl işi, logonun bir gün konduğunda kod değişmeden devreye gireceğini ve yanlış biçimli
 * bir dosyanın çıktıyı bozmayacağını kilitlemek.
 */
class MarkaLogosuTest {
    @Test
    fun `PNG varsa base64 olarak okunur`() {
        val logo = MarkaLogosu("/logo/deneme-logo.png")

        val base64 = logo.base64

        assertThat(base64).isNotNull()
        // Gerçekten çözülebilir bir PNG: şablon bunu `data:image/png;base64,...` olarak gömecek.
        assertThat(PngDogrulayici.pngMi(Base64.getDecoder().decode(base64))).isTrue()
    }

    @Test
    fun `dosya yoksa null doner - belge uretimi aksamaz`() {
        assertThat(MarkaLogosu("/logo/olmayan.png").base64).isNull()
    }

    @Test
    fun `adi png ama icerigi PNG olmayan dosya kullanilmaz`() {
        // Sessizce gömülseydi PDF motoru bozuk görüntüyle düşerdi; marka adı metnine düşmek doğrusu.
        assertThat(MarkaLogosu("/logo/bozuk-logo.png").base64).isNull()
    }

    @Test
    fun `logo sablona gomulunce data URI olarak cikar`() {
        val base64 = MarkaLogosu("/logo/deneme-logo.png").base64

        assertThat("data:image/png;base64,$base64").startsWith("data:image/png;base64,iVBOR")
    }
}
