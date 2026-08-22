package tr.com.tti.hakedis.tools

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal

/**
 * Fiyat metni çözümlemesi — **para hatası üretebilecek tek satır** burada kilitlenir.
 *
 * Yönetici keyfi bir Excel yükleyebildiği için fiyat sütunu metin hücresi olarak gelebilir ve biçim
 * dosyadan dosyaya değişir. Eski kural tüm noktaları binlik sayıp siliyordu; `"1234.56"` → `123456`
 * hatası hiçbir uyarı vermeden fiyatı 100 kat büyütüyordu.
 */
class FiyatMetniTest {
    private fun coz(ham: String): BigDecimal? = fiyatMetniniCoz(ham)

    // --- 100× hatasının kendisi ---------------------------------------------------------------

    @Test
    fun `nokta ondalikli metin binlik sanilmaz`() {
        assertThat(coz("1234.56")).isEqualTo(BigDecimal("1234.56"))
    }

    @Test
    fun `ham hassasiyet bozulmadan gecer - 14 ondalik`() {
        // Kaynak sözleşmedeki gerçek değer. "3'ten fazla hane → binlik" varsayan bir kural bunu
        // 64835852723050107 yapardı.
        assertThat(coz("648.35852723050107")).isEqualTo(BigDecimal("648.35852723050107"))
    }

    // --- tr-TR biçimi ------------------------------------------------------------------------

    @Test
    fun `turkce biçim - binlik nokta ondalik virgul`() {
        assertThat(coz("1.234,56")).isEqualTo(BigDecimal("1234.56"))
        assertThat(coz("1.234.567,89")).isEqualTo(BigDecimal("1234567.89"))
    }

    @Test
    fun `ingilizce biçim - binlik virgul ondalik nokta`() {
        assertThat(coz("1,234.56")).isEqualTo(BigDecimal("1234.56"))
    }

    @Test
    fun `tek virgul ondaliktir`() {
        assertThat(coz("12,5")).isEqualTo(BigDecimal("12.5"))
        assertThat(coz("648,35852723050107")).isEqualTo(BigDecimal("648.35852723050107"))
    }

    /** Türkçe gelenekte `.` binliktir; `1.234` bin iki yüz otuz dörttür. */
    @Test
    fun `belirsiz uc haneli nokta binlik sayilir`() {
        assertThat(coz("1.234")).isEqualTo(BigDecimal("1234"))
    }

    /** Aynı belirsizlikte `,` ondalıktır — `1,234` bir tam iki yüz otuz dört binde. */
    @Test
    fun `belirsiz uc haneli virgul ondalik sayilir`() {
        assertThat(coz("1,234")).isEqualTo(BigDecimal("1.234"))
    }

    @Test
    fun `birden fazla ayirici binliktir`() {
        assertThat(coz("1.234.567")).isEqualTo(BigDecimal("1234567"))
        assertThat(coz("1,234,567")).isEqualTo(BigDecimal("1234567"))
    }

    // --- Kenar durumlar ----------------------------------------------------------------------

    @Test
    fun `ayirici yoksa oldugu gibi okunur`() {
        assertThat(coz("648")).isEqualTo(BigDecimal("648"))
    }

    @Test
    fun `bos metin sifirdir`() {
        assertThat(coz("")).isEqualTo(BigDecimal.ZERO)
        assertThat(coz("   ")).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `bosluklar ve para birimi temizlenir`() {
        assertThat(coz(" 1.234,56 ₺ ")).isEqualTo(BigDecimal("1234.56"))
        // Excel'den kopyalanan hücrelerde kırılmayan boşluk (U+00A0) sık görülür.
        assertThat(coz("1 234,56")).isEqualTo(BigDecimal("1234.56"))
    }

    @Test
    fun `cozulemeyen metin null doner`() {
        assertThat(coz("yok")).isNull()
        assertThat(coz("12,34,56.78")).isNull()
    }
}
