package tr.com.tti.hakedis.domain.document

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tr.com.tti.hakedis.domain.workorder.Region
import tr.com.tti.hakedis.domain.workorder.Scope
import java.math.BigDecimal
import java.time.LocalDateTime
import javax.xml.parsers.DocumentBuilderFactory

class PdfTemplateTest {
    private val createdAt = LocalDateTime.of(2026, 7, 28, 14, 30)

    private fun model(variant: DocumentVariant, rowName: String = "Direk Dikme-montaj"): DocumentModel {
        val fiyatli = variant.fiyatGoster
        return DocumentModel(
            documentNo = "IST-AVR_2026_KESIF_003",
            type = DocumentType.KESIF,
            variant = variant,
            title = "Rota-1: Edirne-Kırklareli",
            header = DocumentHeader(
                region = Region.IST_AVR,
                scope = Scope.YENI_TESIS,
                contractName = "2026 Güvenay Rota-1",
                spNo = "SP-12345",
                route = "Rota-1",
                ekAraligi = "EK-4 / EK-5",
                kableId = "mtc55169a.cable*ist-ist",
                adresA = "AND Plaza - Umut Sokak 10-12",
                adresB = "Acibadem Caddesi 150",
            ),
            sections = listOf(
                DocumentSection(
                    groupPozNo = "1",
                    groupName = "HAVAİ GÜZERGAH İMALATLARI",
                    rows = listOf(
                        DocumentRow(
                            pozNo = "1.1",
                            name = rowName,
                            unit = "Ad.",
                            quantity = BigDecimal("3"),
                            unitPrice = BigDecimal("648.35852723050107").takeIf { fiyatli },
                            amount = BigDecimal("1945.08").takeIf { fiyatli },
                        ),
                    ),
                ),
            ),
            total = BigDecimal("1945.08").takeIf { fiyatli },
            attachments = DocumentAttachments(routeFileName = "guzergah.kmz", imagePaths = listOf("/a.jpg")),
            createdAt = createdAt,
        )
    }

    private val labels = PdfLabels(
        brandName = "Türk Telekom International",
        reportTitle = "İst-AVR · SP-12345",
        typeValue = "Keşif",
        regionLabel = "Bölge",
        regionValue = "İst-AVR",
        scopeLabel = "Kapsam",
        scopeValue = "Yeni Tesis",
        contractLabel = "Sözleşme",
        spNoLabel = "SP No",
        routeLabel = "Route",
        ekAraligiLabel = "Ek Aralığı",
        kableIdLabel = "KableID",
        adresALabel = "Uç A",
        adresBLabel = "Uç B",
        createdAtLabel = "Oluşturulma",
        columnPozNo = "Poz No",
        columnName = "İş Kalemi",
        columnUnit = "Birim",
        columnUnitPrice = "Br Fiyat",
        columnQuantity = "Miktar",
        columnAmount = "Tutar",
        totalLabel = "Toplam",
        routeFileLabel = "EK1",
        routeFileMissing = "yok",
        imagesLabel = "EK2",
        imagesValue = "1 görsel",
    )

    @Test
    fun `FIYATLI varyantta birim fiyat tutar ve toplam basilir`() {
        val html = PdfTemplate.render(model(DocumentVariant.FIYATLI), labels)

        // Birim fiyat da 2 haneye yuvarlı basılır (Faz P): ham 17 haneli değer çıktıda görünmez.
        assertThat(html).contains("648,36 ₺")
        assertThat(html).doesNotContain("648,35852723050107")
        assertThat(html).contains("1.945,08 ₺")
        assertThat(html).contains("Toplam")
    }

    @Test
    fun `TEKLIF varyantta para hucreleri bos ama sutunlar durur`() {
        val html = PdfTemplate.render(model(DocumentVariant.TEKLIF), labels)

        // Sütun başlıkları her iki varyantta da aynı; firma boş hücreleri dolduracak.
        assertThat(html).contains("Br Fiyat")
        assertThat(html).contains("Tutar")
        assertThat(html).contains("Miktar")
        // Ama hiçbir para değeri basılmaz.
        assertThat(html).doesNotContain("₺")
        assertThat(html).doesNotContain("1.945,08")
    }

    @Test
    fun `miktar her iki varyantta da gorunur`() {
        listOf(DocumentVariant.FIYATLI, DocumentVariant.TEKLIF).forEach { varyant ->
            assertThat(PdfTemplate.render(model(varyant), labels)).contains(">3<")
        }
    }

    @Test
    fun `belge numarasi ve tur basliga basilir`() {
        val html = PdfTemplate.render(model(DocumentVariant.FIYATLI), labels)

        assertThat(html).contains("IST-AVR_2026_KESIF_003")
        assertThat(html).contains("Keşif")
    }

    @Test
    fun `kable uc adresleri alan tablosuna girer`() {
        val html = PdfTemplate.render(model(DocumentVariant.FIYATLI), labels)

        assertThat(html).contains("Uç A")
        assertThat(html).contains("AND Plaza - Umut Sokak 10-12")
        assertThat(html).contains("Uç B")
        assertThat(html).contains("Acibadem Caddesi 150")
    }

    /**
     * Kablosuz "Yeni Tesis" projesinde (Faz W) KableID ve Uç A satırları da `adresB` gibi hiç
     * basılmaz — boş bir etiket satırı eksik veri izlenimi verirdi. XHTML iyi biçimli kalmalı:
     * motor girdiyi XML olarak ayrıştırıyor ve bozuk çıktı PDF'i üretilemez hâle getirir (§10).
     */
    @Test
    fun `kablosuz projede kable satirlari hic basilmaz`() {
        val temel = model(DocumentVariant.FIYATLI)
        val kablosuz = temel.copy(header = temel.header.copy(kableId = null, adresA = null, adresB = null))

        val html = PdfTemplate.render(kablosuz, labels)

        assertThat(html).doesNotContain("KableID")
        assertThat(html).doesNotContain("Uç A")
        assertThat(html).doesNotContain("Uç B")
        // Diğer alanlar yerinde: satırların atlanması tabloyu bozmamalı.
        assertThat(html).contains("IST-AVR_2026_KESIF_003")
        // Eksilen satırlar XHTML'i bozmamalı; motor girdiyi XML olarak ayrıştırıyor.
        DocumentBuilderFactory.newInstance()
            .apply { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            .newDocumentBuilder()
            .parse(html.byteInputStream())
    }

    @Test
    fun `adresB yoksa satiri hic basilmaz`() {
        val temel = model(DocumentVariant.FIYATLI)
        val adresBsiz = temel.copy(header = temel.header.copy(adresB = null))

        val html = PdfTemplate.render(adresBsiz, labels)

        assertThat(html).doesNotContain("Uç B")
    }

    @Test
    fun `kullanici metni HTML-escape edilir`() {
        val html = PdfTemplate.render(model(DocumentVariant.FIYATLI, rowName = "Kablo <b> & \"özel\""), labels)

        assertThat(html).contains("Kablo &lt;b&gt; &amp; &quot;özel&quot;")
        assertThat(html).doesNotContain("Kablo <b>")
    }

    @Test
    fun `sayfa kirilimi icin tablo basligi tekrarlanir`() {
        val html = PdfTemplate.render(model(DocumentVariant.FIYATLI), labels)

        assertThat(html).contains("thead { display: table-header-group; }")
    }

    @Test
    fun `sablon gecerli XHTML uretir`() {
        // PDF motoru girdiyi XML olarak ayrıştırıyor: kapanışsız bir etiket ya da tanımsız bir varlık
        // PDF'i bozmaz, ÜRETİLMESİNİ engeller. Bu yüzden iyi biçimlilik testle kilitleniyor.
        val fabrika = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }

        listOf(DocumentVariant.FIYATLI, DocumentVariant.TEKLIF).forEach { varyant ->
            val html = PdfTemplate.render(model(varyant), labels)

            fabrika.newDocumentBuilder().parse(html.byteInputStream())
        }
    }

    @Test
    fun `flexbox kullanilmaz - motor CSS 2 1 tabanli`() {
        val html = PdfTemplate.render(model(DocumentVariant.FIYATLI), labels)

        // `display: flex` sessizce yok sayılır ve başlık bloğu alt alta düşerdi.
        assertThat(html).doesNotContain("display: flex")
    }
}
