package tr.com.tti.hakedis.domain.pricing

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal

class QuantityParserTest {
    @Test
    fun `virgul ve nokta ayni sonucu verir`() {
        val virgullu = QuantityParser.parse("12,5")
        val noktali = QuantityParser.parse("12.5")

        assertThat(virgullu).isInstanceOf(QuantityInput.Valid::class.java)
        assertThat(noktali).isInstanceOf(QuantityInput.Valid::class.java)
        val virgulluDeger = (virgullu as QuantityInput.Valid).value
        val noktaliDeger = (noktali as QuantityInput.Valid).value
        assertThat(virgulluDeger.compareTo(noktaliDeger)).isEqualTo(0)
        assertThat(virgulluDeger.compareTo(BigDecimal("12.5"))).isEqualTo(0)
    }

    @Test
    fun `binlik ayirici ile ondalik birlikte kullanilabilir`() {
        val sonuc = QuantityParser.parse("1.234,5")

        assertThat((sonuc as QuantityInput.Valid).value.compareTo(BigDecimal("1234.5"))).isEqualTo(0)
    }

    @Test
    fun `bos giris Empty dondurur - sifir degil`() {
        assertThat(QuantityParser.parse("")).isEqualTo(QuantityInput.Empty)
        assertThat(QuantityParser.parse("   ")).isEqualTo(QuantityInput.Empty)
    }

    @Test
    fun `sifir gecerli bir degerdir`() {
        val sonuc = QuantityParser.parse("0")

        assertThat(sonuc).isInstanceOf(QuantityInput.Valid::class.java)
        assertThat((sonuc as QuantityInput.Valid).value.compareTo(BigDecimal.ZERO)).isEqualTo(0)
    }

    @Test
    fun `negatif deger reddedilir`() {
        val sonuc = QuantityParser.parse("-5")

        assertThat(sonuc).isEqualTo(QuantityInput.Invalid(QuantityError.NEGATIVE))
    }

    @Test
    fun `harf iceren giris reddedilir`() {
        assertThat(QuantityParser.parse("12a")).isEqualTo(QuantityInput.Invalid(QuantityError.NOT_A_NUMBER))
        assertThat(QuantityParser.parse("abc")).isEqualTo(QuantityInput.Invalid(QuantityError.NOT_A_NUMBER))
    }

    @Test
    fun `birden fazla ondalik ayirici reddedilir`() {
        assertThat(QuantityParser.parse("1,2,3")).isEqualTo(QuantityInput.Invalid(QuantityError.NOT_A_NUMBER))
    }

    @Test
    fun `bastaki ve sondaki bosluklar temizlenir`() {
        val sonuc = QuantityParser.parse("  7,25  ")

        assertThat((sonuc as QuantityInput.Valid).value.compareTo(BigDecimal("7.25"))).isEqualTo(0)
    }

    @Test
    fun `ayiriciyla biten giris tam sayi olarak okunur`() {
        val sonuc = QuantityParser.parse("15,")

        assertThat((sonuc as QuantityInput.Valid).value.compareTo(BigDecimal("15"))).isEqualTo(0)
    }
}
