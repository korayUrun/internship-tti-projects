package tr.com.tti.hakedis.domain.document

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tr.com.tti.hakedis.domain.contract.Contract
import tr.com.tti.hakedis.domain.contract.ContractMeta
import tr.com.tti.hakedis.domain.contract.PozItem
import tr.com.tti.hakedis.domain.contract.parseSortKey
import tr.com.tti.hakedis.domain.workorder.Attachment
import tr.com.tti.hakedis.domain.workorder.AttachmentKind
import tr.com.tti.hakedis.domain.workorder.Region
import tr.com.tti.hakedis.domain.workorder.Scope
import tr.com.tti.hakedis.domain.workorder.WorkOrder
import java.math.BigDecimal
import java.time.LocalDateTime

class DocumentModelBuilderTest {
    private val createdAt = LocalDateTime.of(2026, 7, 28, 14, 30)

    private val contract = Contract(
        meta = ContractMeta(
            id = "2026-sample-rota-1",
            name = "2026 Sample Rota-1",
            title = "Rota-1: Edirne-Kırklareli-Tekirdağ",
            sheetName = "2026_Sample_Rota-1 Br Fiyatlar",
        ),
        items = listOf(
            group("g1", "1", "HAVAİ GÜZERGAH İMALATLARI"),
            item("r5", "1.1", "Direk Dikme-montaj", "648.35852723050107"),
            item("r6", "1.2", "Direk Dikme-demontaj", "373.75962157993592"),
            group("g2", "2", "KABLO ÇEKİM İMALATLARI"),
            item("r11", "2.1", "Havai kablo çekimi", "35.330091343077179", unit = "Mt"),
        ),
    )

    private val workOrder = WorkOrder(
        region = Region.IST_AVR,
        scope = Scope.ARIZA_SONRASI_ONARIM,
        contractId = "2026-sample-rota-1",
        spNo = "  SP-12345  ",
        route = "Rota-1",
        ekAraligi = "EK-4 / EK-5",
        kableId = "mtc55169a.cable*ist-ist",
        adresA = "  AND Plaza - Umut Sokak 10-12  ",
        adresB = "Acibadem Caddesi 150",
    )

    @Test
    fun `sadece miktari girilmis satirlar modele girer`() {
        val model = build(mapOf("r5" to BigDecimal("3")))

        assertThat(model.rowCount).isEqualTo(1)
        assertThat(model.sections.single().rows.single().pozNo).isEqualTo("1.1")
    }

    @Test
    fun `altinda dolu satir kalmayan grup basligi modele girmez`() {
        val model = build(mapOf("r11" to BigDecimal("10")))

        assertThat(model.sections).hasSize(1)
        assertThat(model.sections.single().groupPozNo).isEqualTo("2")
    }

    @Test
    fun `grup basligi altindaki dolu satirlarla birlikte korunur`() {
        val model = build(mapOf("r5" to BigDecimal("1"), "r11" to BigDecimal("2")))

        assertThat(model.sections.map { it.groupPozNo }).containsExactly("1", "2").inOrder()
        assertThat(model.sections.map { it.rows.size }).containsExactly(1, 1).inOrder()
    }

    @Test
    fun `satir tutari PricingCalculator ile ayni - kurus testi`() {
        val model = build(mapOf("r5" to BigDecimal("3")))

        val row = model.sections.single().rows.single()
        assertThat(row.amount).isEqualTo(BigDecimal("1945.08"))
        assertThat(model.total).isEqualTo(BigDecimal("1945.08"))
    }

    @Test
    fun `sifir girilmis satir listelenir - girilmemis satir listelenmez`() {
        // Anahtarın bulunmaması "girilmemiş"tir; ZERO ise "0 girilmiş" ve listelenmelidir.
        val model = build(mapOf("r5" to BigDecimal.ZERO))

        assertThat(model.rowCount).isEqualTo(1)
        assertThat(model.sections.single().rows.single().pozNo).isEqualTo("1.1")
    }

    @Test
    fun `TEKLIF varyantinda para alanlari bos - miktarlar durur`() {
        val model = build(mapOf("r5" to BigDecimal("3")), variant = DocumentVariant.TEKLIF)

        val row = model.sections.single().rows.single()
        assertThat(row.quantity).isEqualTo(BigDecimal("3"))
        assertThat(row.unitPrice).isNull()
        assertThat(row.amount).isNull()
        assertThat(model.total).isNull()
    }

    @Test
    fun `FIYATLI varyantinda para alanlari dolu`() {
        val model = build(mapOf("r5" to BigDecimal("3")), variant = DocumentVariant.FIYATLI)

        val row = model.sections.single().rows.single()
        assertThat(row.unitPrice).isEqualTo(BigDecimal("648.35852723050107"))
        assertThat(row.amount).isEqualTo(BigDecimal("1945.08"))
        assertThat(model.total).isEqualTo(BigDecimal("1945.08"))
    }

    @Test
    fun `iki varyant ayni miktarlari ve ayni belge numarasini tasir`() {
        val quantities = mapOf("r5" to BigDecimal("3"), "r11" to BigDecimal("120"))
        val fiyatli = build(quantities, variant = DocumentVariant.FIYATLI)
        val teklif = build(quantities, variant = DocumentVariant.TEKLIF)

        assertThat(teklif.documentNo).isEqualTo(fiyatli.documentNo)
        assertThat(teklif.sections.flatMap { it.rows }.map { it.quantity })
            .isEqualTo(fiyatli.sections.flatMap { it.rows }.map { it.quantity })
    }

    @Test
    fun `hic miktar girilmemisse model bos`() {
        val model = build(emptyMap())

        assertThat(model.isEmpty).isTrue()
        assertThat(model.sections).isEmpty()
    }

    @Test
    fun `baslik sozlesmeden gelir ve metin alanlari trimlenir`() {
        val model = build(mapOf("r5" to BigDecimal("1")))

        assertThat(model.title).isEqualTo("Rota-1: Edirne-Kırklareli-Tekirdağ")
        assertThat(model.header.spNo).isEqualTo("SP-12345")
        assertThat(model.header.contractName).isEqualTo("2026 Sample Rota-1")
        assertThat(model.createdAt).isEqualTo(createdAt)
    }

    @Test
    fun `kable uc adresleri basliga tasinir`() {
        val model = build(mapOf("r5" to BigDecimal("1")))

        assertThat(model.header.kableId).isEqualTo("mtc55169a.cable*ist-ist")
        assertThat(model.header.adresA).isEqualTo("AND Plaza - Umut Sokak 10-12")
        assertThat(model.header.adresB).isEqualTo("Acibadem Caddesi 150")
    }

    /**
     * Kablosuz "Yeni Tesis" projesi (Faz W): üç alan da boş gelir ve `null`'a iner. Boş metin
     * taşınsaydı çıktı "değer yok" ile "boş değer" arasında ayrım yapamaz, PDF'e boş etiket
     * satırları basılırdı.
     */
    @Test
    fun `kablosuz projede kable alanlari null olur`() {
        val model = DocumentModelBuilder.build(
            documentNo = "IZMIR_2026_FIZIBILITE_001",
            type = DocumentType.FIZIBILITE,
            variant = DocumentVariant.FIYATLI,
            contract = contract,
            workOrder = workOrder.copy(kableId = "", adresA = "   ", adresB = null),
            quantities = mapOf("r5" to BigDecimal("1")),
            createdAt = createdAt,
        )

        assertThat(model.header.kableId).isNull()
        assertThat(model.header.adresA).isNull()
        assertThat(model.header.adresB).isNull()
    }

    @Test
    fun `bos adresB null olarak tasinir`() {
        val model = DocumentModelBuilder.build(
            documentNo = "IST-AVR_2026_KESIF_003",
            type = DocumentType.KESIF,
            variant = DocumentVariant.FIYATLI,
            contract = contract,
            workOrder = workOrder.copy(adresB = "   "),
            quantities = mapOf("r5" to BigDecimal("1")),
            createdAt = createdAt,
        )

        assertThat(model.header.adresB).isNull()
    }

    @Test
    fun `ek ozeti modele tasinir`() {
        val model = build(
            quantities = mapOf("r5" to BigDecimal("1")),
            routeFile = Attachment("a1", "guzergah.kmz", "/data/a1.kmz", 1024, AttachmentKind.ROUTE_FILE),
            images = listOf(
                Attachment("i1", "foto1.jpg", "/data/i1.jpg", 2048, AttachmentKind.IMAGE),
                Attachment("i2", "foto2.jpg", "/data/i2.jpg", 2048, AttachmentKind.IMAGE),
            ),
        )

        assertThat(model.attachments.routeFileName).isEqualTo("guzergah.kmz")
        assertThat(model.attachments.imageCount).isEqualTo(2)
        assertThat(model.attachments.imagePaths).containsExactly("/data/i1.jpg", "/data/i2.jpg").inOrder()
    }

    private fun build(
        quantities: Map<String, BigDecimal>,
        variant: DocumentVariant = DocumentVariant.FIYATLI,
        routeFile: Attachment? = null,
        images: List<Attachment> = emptyList(),
    ) = DocumentModelBuilder.build(
        documentNo = "IST-AVR_2026_KESIF_003",
        type = DocumentType.KESIF,
        variant = variant,
        contract = contract,
        workOrder = workOrder,
        quantities = quantities,
        createdAt = createdAt,
        routeFile = routeFile,
        images = images,
    )

    private fun group(id: String, pozNo: String, name: String) = PozItem(
        id = id,
        pozNo = pozNo,
        sortKey = parseSortKey(pozNo),
        name = name,
        unit = "",
        unitPrice = BigDecimal.ZERO,
        isGroup = true,
        isPriced = false,
    )

    private fun item(id: String, pozNo: String, name: String, price: String, unit: String = "Ad.") = PozItem(
        id = id,
        pozNo = pozNo,
        sortKey = parseSortKey(pozNo),
        name = name,
        unit = unit,
        unitPrice = BigDecimal(price),
        isGroup = false,
        isPriced = true,
    )
}
