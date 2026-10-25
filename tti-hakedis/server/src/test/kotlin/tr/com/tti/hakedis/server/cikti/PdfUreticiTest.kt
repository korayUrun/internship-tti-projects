package tr.com.tti.hakedis.server.cikti

import com.google.common.truth.Truth.assertThat
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tr.com.tti.hakedis.domain.document.DocumentAttachments
import tr.com.tti.hakedis.domain.document.DocumentHeader
import tr.com.tti.hakedis.domain.document.DocumentModel
import tr.com.tti.hakedis.domain.document.DocumentRow
import tr.com.tti.hakedis.domain.document.DocumentSection
import tr.com.tti.hakedis.domain.document.DocumentType
import tr.com.tti.hakedis.domain.document.DocumentVariant
import tr.com.tti.hakedis.domain.document.PdfLabels
import tr.com.tti.hakedis.domain.document.PdfTemplate
import tr.com.tti.hakedis.domain.workorder.Region
import tr.com.tti.hakedis.domain.workorder.Scope
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * PDF üretiminin uçtan uca kanıtı: şablonun ürettiği XHTML gerçekten bastırılıyor, Türkçe glifler
 * gömülü fontla basılıyor ve **metin olarak aranabiliyor**.
 *
 * Aranabilirlik önemsiz bir ayrıntı değil: gömme yapılmazsa harfler ya kaybolur ya kutu olur, ve bu
 * ancak birisi çıktıyı gözle açtığında fark edilir. Metin çıkarımı bunu makinenin görebileceği hâle
 * getiriyor.
 */
class PdfUreticiTest {
    private val uretici = PdfUretici()
    private val createdAt = LocalDateTime.of(2026, 7, 30, 14, 30)

    private fun model(
        variant: DocumentVariant = DocumentVariant.FIYATLI,
        type: DocumentType = DocumentType.KESIF,
        kesifQuantity: BigDecimal? = null,
        kesifAmount: BigDecimal? = null,
        kesifTotal: BigDecimal? = null,
    ) = DocumentModel(
        documentNo = "IST-AVR_2026_${type.name}_003",
        type = type,
        variant = variant,
        title = "Rota-1: Edirne-Kırklareli-Tekirdağ",
        header = DocumentHeader(
            region = Region.IST_AVR,
            scope = Scope.YENI_TESIS,
            contractName = "2026 Sample Rota-1",
            spNo = "SP-12345",
            route = "Rota-1",
            ekAraligi = "EK-4 / EK-5",
            kableId = "mtc55169a.cable*ist-ist",
            adresA = "AND Plaza - Umut Sokak",
            adresB = null,
        ),
        sections = listOf(
            DocumentSection(
                groupPozNo = "1",
                groupName = "HAVAİ GÜZERGAH İMALATLARI",
                rows = listOf(
                    DocumentRow(
                        pozNo = "1.1",
                        name = "Direk Dikme-montaj",
                        unit = "Ad.",
                        quantity = BigDecimal("3"),
                        unitPrice = BigDecimal("648.35852723050107").takeIf { variant.fiyatGoster },
                        amount = BigDecimal("1945.08").takeIf { variant.fiyatGoster },
                        kesifQuantity = kesifQuantity,
                        kesifAmount = kesifAmount?.takeIf { variant.fiyatGoster },
                    ),
                ),
            ),
        ),
        total = BigDecimal("1945.08").takeIf { variant.fiyatGoster },
        kesifTotal = kesifTotal?.takeIf { variant.fiyatGoster },
        attachments = DocumentAttachments(routeFileName = "guzergah.kmz", imagePaths = emptyList()),
        createdAt = createdAt,
    )

    private val labels = PdfLabels(
        brandName = "Türk Telekom International",
        reportTitle = "İstanbul Avrupa · SP-12345",
        typeValue = "1. Keşif",
        regionLabel = "Bölge",
        regionValue = "İstanbul Avrupa",
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
        columnKesifQuantity = "1. Keşif Talep",
        columnKesifAmount = "1.Keşif Tutar",
        totalLabel = "Toplam",
        routeFileLabel = "EK1",
        routeFileMissing = "yok",
        imagesLabel = "EK2",
        imagesValue = "0 adet",
    )

    private fun metin(icerik: ByteArray): String = Loader.loadPDF(icerik).use { PDFTextStripper().getText(it) }

    private fun uret(model: DocumentModel): ByteArray = uretici.uret(PdfTemplate.render(model, labels))

    @Test
    fun `gecerli bir PDF uretilir`() {
        val icerik = uret(model())

        assertThat(String(icerik.copyOf(5), Charsets.ISO_8859_1)).isEqualTo("%PDF-")
    }

    @Test
    fun `tutar ve toplam metin olarak bulunabiliyor`() {
        val metin = metin(uret(model()))

        assertThat(metin).contains("1.945,08 ₺")
        assertThat(metin).contains("IST-AVR_2026_KESIF_003")
    }

    /**
     * Birim fiyat PDF'e **yuvarlanmadan** basılır ve sütuna sığar.
     *
     * Belgeyi eline alan (özellikle aracı firma) `Br Fiyat × Miktar = Tutar` kontrolünü elle yapıyor;
     * 2 haneye yuvarlanmış fiyatla bu kontrol tutmuyor ve belge hatalı görünüyordu. 17 karakterlik
     * değer kırpılır ya da satır kayarsa metin araması tutmaz — bu test ikisini birden yakalar.
     */
    @Test
    fun `birim fiyat PDF'e iki haneye yuvarlanmis basilir`() {
        val metin = metin(uret(model()))

        // Faz P: belgeye çivilenen fiyat da 2 haneli; ham 17 haneli değer çıktıda hiç görünmez.
        assertThat(metin).contains("648,36 ₺")
        assertThat(metin).doesNotContain("648,35852723050107")
    }

    @Test
    fun `Turkce glifler metin olarak aranabiliyor - font gomme kaniti`() {
        val metin = metin(uret(model()))

        // Gömülü font olmadan bu harfler ya kaybolur ya kutu basılır; ikisinde de arama tutmaz.
        assertThat(metin).contains("İstanbul")
        assertThat(metin).contains("Sözleşme")
        assertThat(metin).contains("HAVAİ GÜZERGAH İMALATLARI")
        assertThat(metin).contains("Keşif")
    }

    @Test
    fun `TEKLIF varyantinda hicbir para degeri basilmaz`() {
        val metin = metin(uret(model(variant = DocumentVariant.TEKLIF)))

        assertThat(metin).doesNotContain("₺")
        assertThat(metin).doesNotContain("1.945,08")
        // Sütun başlıkları yerinde: firma boş hücreleri dolduracak.
        assertThat(metin).contains("Br Fiyat")
        assertThat(metin).contains("Miktar")
    }

    // --- Hakedişte keşif sütunları (31 Temmuz 2026 kullanıcı isteği) ---------------------------

    private fun hakedis(variant: DocumentVariant = DocumentVariant.FIYATLI) = model(
        variant = variant,
        type = DocumentType.HAKEDIS,
        kesifQuantity = BigDecimal("5"),
        kesifAmount = BigDecimal("3241.79"),
        kesifTotal = BigDecimal("3241.79"),
    )

    @Test
    fun `hakediste kesif sutunlari basilir`() {
        val metin = metin(uret(hakedis()))

        assertThat(metin).contains("1. Keşif Talep")
        assertThat(metin).contains("1.Keşif Tutar")
        assertThat(metin).contains("3.241,79 ₺")
    }

    @Test
    fun `kesif ve fizibilitede kesif sutunu basilmaz`() {
        val metin = metin(uret(model()))

        assertThat(metin).doesNotContain("1. Keşif Talep")
        assertThat(metin).doesNotContain("1.Keşif Tutar")
    }

    /**
     * 8 sütunlu hakediş sayfasında 17 karakterlik birim fiyatın **tek parça** kalması, sayfanın
     * yatay basılmasının kanıtı. Dikey kalsaydı hücre sarar, PDFBox metni satır sonuyla böler ve
     * bu arama tutmazdı.
     */
    @Test
    fun `hakediste birim fiyat tek parca olarak bulunabiliyor`() {
        val metin = metin(uret(hakedis()))

        // Yatay düzende hücre sarmıyor: fiyat tek parça aranabiliyor.
        assertThat(metin).contains("648,36 ₺")
    }

    @Test
    fun `hakedisin TEKLIF varyantinda kesif tutari da basilmaz`() {
        val metin = metin(uret(hakedis(DocumentVariant.TEKLIF)))

        assertThat(metin).doesNotContain("₺")
        assertThat(metin).doesNotContain("3.241,79")
        // Miktar para değil: keşif miktarı fiyatsız kopyada da görünür.
        assertThat(metin).contains("1. Keşif Talep")
    }

    @Test
    fun `logo base64 olarak gercekten PDF'e gomulur`() {
        // Zincirin tamamı: dosya → MarkaLogosu (base64) → şablon (`data:` URI) → motor (gömülü görüntü).
        // Motorun `data:` URI'yi desteklemesi kendiliğinden değil; desteklemeseydi logo sessizce kaybolurdu.
        val logolu = labels.copy(logoBase64 = MarkaLogosu("/logo/deneme-logo.png").base64)

        val icerik = uretici.uret(PdfTemplate.render(model(), logolu))

        Loader.loadPDF(icerik).use { belge ->
            val kaynaklar = belge.getPage(0).resources
            val gorseller = kaynaklar.xObjectNames.count { kaynaklar.getXObject(it) is PDImageXObject }

            assertThat(gorseller).isEqualTo(1)
        }
    }

    @Test
    fun `logo yoksa marka adi metin olarak basilir`() {
        val metin = metin(uret(model()))

        assertThat(metin).contains("Türk Telekom International")
    }

    @Test
    fun `bozuk XHTML sessizce yutulmaz`() {
        // Motor girdiyi XML olarak ayrıştırıyor; kapanmamış etiket yarım bir PDF değil, hata üretmeli.
        assertThrows<PdfUretimHatasi> { uretici.uret("<html><body><p>kapanmamis</body></html>") }
    }
}
