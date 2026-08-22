package tr.com.tti.hakedis.domain.document

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

class DocumentFileNamerTest {
    @Test
    fun `belge numarasi sablona uyar`() {
        val no = DocumentFileNamer.documentNo("IST-AVR", 2026, DocumentType.KESIF, 3)

        assertThat(no).isEqualTo("IST-AVR_2026_KESIF_003")
    }

    @Test
    fun `sira numarasi uc haneye tamamlanir - 999 sonrasi dogal olarak tasar`() {
        assertThat(DocumentFileNamer.documentNo("ANKARA", 2026, DocumentType.FIZIBILITE, 1))
            .isEqualTo("ANKARA_2026_FIZIBILITE_001")
        assertThat(DocumentFileNamer.documentNo("ANKARA", 2026, DocumentType.FIZIBILITE, 1000))
            .isEqualTo("ANKARA_2026_FIZIBILITE_1000")
    }

    @Test
    fun `dosya adi varyantla biter ve zaman damgasi tasimaz`() {
        val no = DocumentFileNamer.documentNo("IST-AVR", 2026, DocumentType.KESIF, 3)

        assertThat(DocumentFileNamer.baseName(no, DocumentVariant.FIYATLI))
            .isEqualTo("TTI_IST-AVR_2026_KESIF_003_FIYATLI")
        assertThat(DocumentFileNamer.baseName(no, DocumentVariant.TEKLIF))
            .isEqualTo("TTI_IST-AVR_2026_KESIF_003_TEKLIF")
    }

    @Test
    fun `ayni belge iki kez uretilince ayni dosya adi cikar`() {
        val no = DocumentFileNamer.documentNo("BURSA", 2026, DocumentType.HAKEDIS, 12)

        assertThat(DocumentFileNamer.baseName(no, DocumentVariant.FIYATLI))
            .isEqualTo(DocumentFileNamer.baseName(no, DocumentVariant.FIYATLI))
    }

    @Test
    fun `sira numarasi pozitif olmali`() {
        try {
            DocumentFileNamer.documentNo("IZMIR", 2026, DocumentType.KESIF, 0)
            throw AssertionError("Sıfır sıra numarası kabul edilmemeliydi")
        } catch (beklenen: IllegalArgumentException) {
            assertThat(beklenen).hasMessageThat().contains("Sıra numarası")
        }
    }

    @Test
    fun `Turkce karakterler ASCII'ye cevrilir`() {
        assertThat(DocumentFileNamer.sanitize("Şişli Güzergâh")).isEqualTo("Sisli_Guzergah")
        assertThat(DocumentFileNamer.sanitize("İzmir çıkış")).isEqualTo("Izmir_cikis")
    }

    @Test
    fun `bosluklar alt cizgi olur ozel karakterler atilir`() {
        assertThat(DocumentFileNamer.sanitize("SP 123 / 45*?")).isEqualTo("SP_123_45")
    }

    @Test
    fun `Turkce locale altinda da bozulmaz`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertThat(DocumentFileNamer.documentNo("IZMIR", 2026, DocumentType.KESIF, 1))
                .isEqualTo("IZMIR_2026_KESIF_001")
        } finally {
            Locale.setDefault(original)
        }
    }
}
