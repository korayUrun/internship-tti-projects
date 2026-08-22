package tr.com.tti.hakedis.domain.pricing

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal

class MoneyFormatterTest {
    @Test
    fun `para birimi tr-TR formatinda gosterilir`() {
        assertThat(MoneyFormatter.money(BigDecimal("1234.56"))).isEqualTo("1.234,56 ₺")
    }

    @Test
    fun `iki basamaga yuvarlanir`() {
        assertThat(MoneyFormatter.money(BigDecimal("648.35852723050107"))).isEqualTo("648,36 ₺")
    }

    @Test
    fun `kurus her zaman iki hane gosterilir`() {
        assertThat(MoneyFormatter.money(BigDecimal("5"))).isEqualTo("5,00 ₺")
        assertThat(MoneyFormatter.money(BigDecimal("0"))).isEqualTo("0,00 ₺")
    }

    @Test
    fun `milyonluk tutarda binlik ayiricilar dogru`() {
        assertThat(MoneyFormatter.money(BigDecimal("1234567.891"))).isEqualTo("1.234.567,89 ₺")
    }

    /**
     * Sözleşmenin ham fiyatı 14 ondalığa kadar çıkabiliyor; gösterim **her zaman** 2 haneye yuvarlar
     * (Faz P). Belgeye çivilenen fiyat da yuvarlandığı için gösterilen sayı hesapta kullanılan
     * sayının aynısıdır: `371,87 × 2 = 743,74` elde de tabloda da aynı.
     */
    @Test
    fun `ham sozlesme fiyati iki haneye yuvarlanir`() {
        assertThat(MoneyFormatter.money(BigDecimal("371.86716779978428"))).isEqualTo("371,87 ₺")
        assertThat(MoneyFormatter.money(BigDecimal("648.35852723050107"))).isEqualTo("648,36 ₺")
        assertThat(MoneyFormatter.money(BigDecimal("5"))).isEqualTo("5,00 ₺")
    }

    @Test
    fun `sembolsuz deger de ayni sayiyi verir`() {
        assertThat(MoneyFormatter.moneyValue(BigDecimal("1234.56"))).isEqualTo("1.234,56")
    }

    @Test
    fun `miktar gereksiz sifirlarla gosterilmez`() {
        assertThat(MoneyFormatter.quantity(BigDecimal("12.50"))).isEqualTo("12,5")
        assertThat(MoneyFormatter.quantity(BigDecimal("1234"))).isEqualTo("1.234")
        assertThat(MoneyFormatter.quantity(BigDecimal("0.125"))).isEqualTo("0,125")
    }
}
