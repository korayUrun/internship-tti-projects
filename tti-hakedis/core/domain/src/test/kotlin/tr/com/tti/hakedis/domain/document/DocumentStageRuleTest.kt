package tr.com.tti.hakedis.domain.document

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tr.com.tti.hakedis.domain.workorder.WorkOrderStatus

class DocumentStageRuleTest {
    private fun karar(tur: DocumentType, mevcut: Map<DocumentType, DocumentStatus> = emptyMap(), isDurumu: WorkOrderStatus? = null) =
        DocumentStageRule.acilabilirMi(tur, DocumentStageState(isDurumu, mevcut))

    private fun reddeden(karar: StageDecision): StageRejection = (karar as StageDecision.Reddedildi).gerekce

    /** Fizibilite işleme alınmış — keşif açmanın ön koşulu. */
    private val fizibiliteIsleniyor = mapOf(DocumentType.FIZIBILITE to DocumentStatus.ISLENIYOR)

    /** Hakediş açmanın ön koşulu: fizibilite tamam, keşif işleme alınmış. */
    private val hakedise = mapOf(
        DocumentType.FIZIBILITE to DocumentStatus.TAMAMLANDI,
        DocumentType.KESIF to DocumentStatus.ISLENIYOR,
    )

    // --- Durum kümesi: yeknesek dört durum (Ağustos 2026, müdür isteği) -------------------------

    /**
     * Liste `belge_durum_check` kısıtının Kotlin ikizidir. `EnumSenkronDogrulayici` yalnızca lookup
     * tablolarını denetliyor, bu enum'u değil — migration ile enum'un birlikte değişmesini kilitleyen
     * tek yer burası.
     */
    @Test
    fun `belge durumlari yeknesek dorttur`() {
        assertThat(DocumentStatus.entries).containsExactly(
            DocumentStatus.ONAY_BEKLIYOR,
            DocumentStatus.ISLENIYOR,
            DocumentStatus.TAMAMLANDI,
            DocumentStatus.IPTAL,
        ).inOrder()
    }

    /** Yalnızca onay bekleyen aşama düzenlenebilir; işleme alındıktan sonra kilitlenir. */
    @Test
    fun `yalnizca onay bekleyen duzenlenebilir`() {
        assertThat(DocumentStatus.entries.filter { it.duzenlenebilirMi })
            .containsExactly(DocumentStatus.ONAY_BEKLIYOR)
    }

    @Test
    fun `acik durumlar onay bekleyen ve isleniyendir`() {
        assertThat(DocumentStatus.entries.filter { it.acikMi })
            .containsExactly(DocumentStatus.ONAY_BEKLIYOR, DocumentStatus.ISLENIYOR)
    }

    // --- Onay sonrası durum türe bağlı ---------------------------------------------------------

    @Test
    fun `fizibilite ve kesif onaylaninca isleniyor olur`() {
        assertThat(DocumentStageRule.onaySonrasiDurum(DocumentType.FIZIBILITE))
            .isEqualTo(DocumentStatus.ISLENIYOR)
        assertThat(DocumentStageRule.onaySonrasiDurum(DocumentType.KESIF))
            .isEqualTo(DocumentStatus.ISLENIYOR)
    }

    @Test
    fun `hakedis onaylaninca dogrudan tamamlanir`() {
        assertThat(DocumentStageRule.onaySonrasiDurum(DocumentType.HAKEDIS))
            .isEqualTo(DocumentStatus.TAMAMLANDI)
    }

    @Test
    fun `onay bekleyen kesif duzenlenebilir - isleme alinca kilitlenir`() {
        assertThat(DocumentStageRule.duzenlenebilirMi(DocumentType.KESIF, DocumentStatus.ONAY_BEKLIYOR)).isTrue()
        assertThat(DocumentStageRule.duzenlenebilirMi(DocumentType.KESIF, DocumentStatus.ISLENIYOR)).isFalse()
        assertThat(DocumentStageRule.duzenlenebilirMi(DocumentType.KESIF, DocumentStatus.TAMAMLANDI)).isFalse()
    }

    @Test
    fun `onay bekleyen fizibilite duzenlenebilir`() {
        assertThat(DocumentStageRule.duzenlenebilirMi(DocumentType.FIZIBILITE, DocumentStatus.ONAY_BEKLIYOR)).isTrue()
        assertThat(DocumentStageRule.duzenlenebilirMi(DocumentType.FIZIBILITE, DocumentStatus.ISLENIYOR)).isFalse()
    }

    // --- Aşama sırası atlanamaz ----------------------------------------------------------------

    @Test
    fun `bos iste fizibilite acilabilir`() {
        assertThat(karar(DocumentType.FIZIBILITE)).isEqualTo(StageDecision.Izinli)
    }

    @Test
    fun `fizibilite olmadan kesif acilamaz`() {
        assertThat(reddeden(karar(DocumentType.KESIF))).isEqualTo(StageRejection.ONCEKI_ASAMA_YOK)
    }

    @Test
    fun `fizibilite isleme alinmadan kesif acilamaz`() {
        val karar = karar(DocumentType.KESIF, mapOf(DocumentType.FIZIBILITE to DocumentStatus.ONAY_BEKLIYOR))

        assertThat(reddeden(karar)).isEqualTo(StageRejection.ONCEKI_ASAMA_ISLEME_ALINMAMIS)
    }

    @Test
    fun `fizibilite isleme alindiysa kesif acilabilir`() {
        assertThat(karar(DocumentType.KESIF, fizibiliteIsleniyor)).isEqualTo(StageDecision.Izinli)
    }

    @Test
    fun `kesif isleme alinmadan hakedis acilamaz`() {
        val karar = karar(
            DocumentType.HAKEDIS,
            mapOf(
                DocumentType.FIZIBILITE to DocumentStatus.TAMAMLANDI,
                DocumentType.KESIF to DocumentStatus.ONAY_BEKLIYOR,
            ),
        )

        assertThat(reddeden(karar)).isEqualTo(StageRejection.ONCEKI_ASAMA_ISLEME_ALINMAMIS)
    }

    @Test
    fun `kesif isleme alindiysa hakedis acilabilir`() {
        assertThat(karar(DocumentType.HAKEDIS, hakedise)).isEqualTo(StageDecision.Izinli)
    }

    // --- Proje iptal ise yeni belge açılamaz ---------------------------------------------------

    @Test
    fun `iptal edilmis projeye yeni belge acilamaz`() {
        val karar = karar(DocumentType.FIZIBILITE, isDurumu = WorkOrderStatus.IPTAL)

        assertThat(reddeden(karar)).isEqualTo(StageRejection.IS_KAPANMIS)
    }

    // --- Aynı tür / iptal edilmiş belgeler -----------------------------------------------------

    @Test
    fun `ayni turden ikinci belge acilamaz`() {
        val karar = karar(DocumentType.FIZIBILITE, mapOf(DocumentType.FIZIBILITE to DocumentStatus.ONAY_BEKLIYOR))

        assertThat(reddeden(karar)).isEqualTo(StageRejection.ZATEN_VAR)
    }

    @Test
    fun `iptal edilmis belge yerine yenisi acilabilir`() {
        val karar = karar(DocumentType.FIZIBILITE, mapOf(DocumentType.FIZIBILITE to DocumentStatus.IPTAL))

        assertThat(karar).isEqualTo(StageDecision.Izinli)
    }

    @Test
    fun `iptal edilmis onceki asama kapanmis sayilmaz`() {
        val karar = karar(DocumentType.KESIF, mapOf(DocumentType.FIZIBILITE to DocumentStatus.IPTAL))

        assertThat(reddeden(karar)).isEqualTo(StageRejection.ONCEKI_ASAMA_YOK)
    }
}
