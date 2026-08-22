package tr.com.tti.hakedis.domain.document

import tr.com.tti.hakedis.domain.pricing.MoneyFormatter
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * PDF şablonu: XHTML + CSS. Referans düzen "Hasar Tespit Tutanağı" (CLAUDE.md §10).
 *
 * Saf Kotlin'dir ve `:core:domain` içinde durur: PDF sunucuda üretilir, ama şablon istemciyle
 * paylaşılan çıktı modelinin bir parçasıdır — tek şablon, tek çıktı.
 *
 * Kullanıcı metinleri (`<`, `&`, `"` içerebilir) **HTML-escape** edilerek gömülür.
 * Sayfa kırılımında tablo başlığının tekrarı `thead { display: table-header-group }` ile sağlanır.
 *
 * Sütun düzeni her iki varyantta **aynıdır**; `TEKLIF` varyantında birim fiyat ve tutar hücreleri
 * boş basılır (model o alanları `null` verir), böylece firma kendi fiyatını doldurabilir.
 *
 * ### Üretici motorun dayattığı iki kural (Faz G)
 * 1. **Çıktı geçerli XHTML olmak zorundadır.** openhtmltopdf girdiyi XML olarak ayrıştırır: kapanışsız
 *    `<meta>` / `<img>` ya da tanımsız bir HTML varlığı (`&nbsp;`) dosyayı üretmez, **hata fırlatır**.
 *    Bu yüzden boş elemanlar `/>` ile kapanır ve kaçış yalnızca XML'in beş öntanımlı varlığını üretir.
 * 2. **Flexbox yoktur.** Motor CSS 2.1 tabanlıdır; `display: flex` sessizce yok sayılır ve başlık
 *    bloğu alt alta düşerdi. Üst blok bu yüzden `<table>` ile hizalanır — eski bir teknik, ama
 *    yazdırma motorlarında çalışan tek teknik.
 */
object PdfTemplate {
    private val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.ROOT)

    /** Poz No · İş Kalemi · Birim · Br Fiyat · Miktar · Tutar */
    private const val COLUMN_COUNT = 6

    /**
     * Hakediş iki sütun fazla taşır: **1. Keşif Talep** ve **1.Keşif Tutar** (31 Temmuz 2026 kullanıcı
     * isteği). Sıra Excel çıktısıyla birebir aynı tutuluyor — iki dosyayı yan yana koyan kişi sütun
     * eşlemek zorunda kalmasın:
     * `Poz No · İş Kalemi · Birim · Br Fiyat · 1. Keşif Talep · Miktar · 1.Keşif Tutar · Tutar`
     */
    private const val HAKEDIS_COLUMN_COUNT = 8

    private fun columnCount(model: DocumentModel): Int = if (model.type == DocumentType.HAKEDIS) HAKEDIS_COLUMN_COUNT else COLUMN_COUNT

    /** Hakedişte iki keşif hücresi de basılır; diğer türlerde satır altı sütunda kalır. */
    private fun kesifSutunlariVarMi(model: DocumentModel): Boolean = model.type == DocumentType.HAKEDIS

    fun render(model: DocumentModel, labels: PdfLabels): String =
        """
        <!DOCTYPE html>
        <html xmlns="http://www.w3.org/1999/xhtml" lang="tr">
        <head>
          <meta charset="utf-8"/>
          <title>${model.documentNo.escapeHtml()}</title>
          <style>${css(model)}</style>
        </head>
        <body>
          ${headerBlock(model, labels)}
          ${fieldsBlock(model, labels)}
          ${itemsTable(model, labels)}
          ${footerBlock(model, labels)}
        </body>
        </html>
        """.trimIndent()

    /**
     * Hakediş **yatay** basılır: 8 sütun A4 dikeye sığmıyor, hücreler sarınca
     * "Br Fiyat × Miktar = Tutar" kontrolü okunamaz hâle geliyordu. Diğer türler 6 sütunla dikey
     * kalır — dosyayı yazdıran için tanıdık düzen bozulmasın.
     */
    private fun css(model: DocumentModel): String =
        """
        @page { size: A4 ${if (model.type == DocumentType.HAKEDIS) "landscape" else "portrait"}; margin: 14mm 12mm; }
        /* Font adı, üreticinin gömdüğü aile adıyla birebir aynı olmalı (PdfUretici.FONT_AILESI).
           `sans-serif` bırakılırsa motor base-14 fontlarına düşer ve Türkçe glifler (ş, ğ, İ) kaybolur. */
        body { font-family: "DejaVu Sans", sans-serif; font-size: 9.5pt; color: #0F1B2D; margin: 0; }
        /* Flexbox yok (CSS 2.1 motoru): üst blok tabloyla hizalanır. */
        table.top { width: 100%; border-collapse: collapse;
                    border-bottom: 2px solid #0B5FFF; padding-bottom: 6px; margin-bottom: 10px; }
        table.top td { vertical-align: top; padding: 0 0 6px 0; }
        table.top td.brand { text-align: right; width: 30%; }
        .title { font-size: 13pt; font-weight: bold; }
        .docno { font-size: 9pt; color: #5B6B80; margin-top: 2px; }
        .brand { font-size: 9pt; font-weight: bold; color: #0B5FFF; }
        /* `max-width` yatay logoların hücreyi taşırmasına karşı emniyet: TTI logosu geniş bir yazı
           bloğu, yalnızca yüksekliğe göre ölçeklenirse başlık sütununa girebilir. */
        .brand img.logo { height: 14mm; max-width: 100%; }
        table.fields { width: 100%; border-collapse: collapse; margin-bottom: 12px; }
        table.fields td { padding: 2px 4px; vertical-align: top; }
        table.fields td.label { width: 22%; color: #5B6B80; font-weight: bold; }
        table.items { width: 100%; border-collapse: collapse; }
        table.items thead { display: table-header-group; }
        table.items th, table.items td { border: 1px solid #D6E0EE; padding: 3px 5px; }
        table.items th { background: #E8F0FF; text-align: left; font-size: 9pt; }
        table.items tr { page-break-inside: avoid; }
        td.num, th.num { text-align: right; white-space: nowrap; }
        tr.group td { background: #EDF1F6; font-weight: bold; }
        tr.total td { background: #E8F0FF; font-weight: bold; }
        .footer { margin-top: 12px; font-size: 9pt; color: #5B6B80; }
        .footer div { margin-bottom: 2px; }
        /* EK3 — sayfa sonunda, kalem tablosuyla aynı dilde ama daha dar. */
        .materials { margin-top: 14px; }
        .materials-title { font-size: 10pt; font-weight: bold; color: #0F1B2D; margin-bottom: 4px; }
        .materials table { width: 60%; }
        """.trimIndent()

    private fun headerBlock(model: DocumentModel, labels: PdfLabels): String {
        // Logo HTML'e base64 olarak gömülür: üretici tarafın dosya sistemine erişmesi gerekmez.
        val brand = labels.logoBase64
            ?.let { "<img class=\"logo\" src=\"data:image/png;base64,$it\" alt=\"\"/>" }
            ?: labels.brandName.escapeHtml()

        return """
            <table class="top">
              <tr>
                <td>
                  <div class="title">${labels.reportTitle.escapeHtml()}</div>
                  <div class="docno">${model.documentNo.escapeHtml()} · ${labels.typeValue.escapeHtml()}</div>
                </td>
                <td class="brand">$brand</td>
              </tr>
            </table>
        """.trimIndent()
    }

    private fun fieldsBlock(model: DocumentModel, labels: PdfLabels): String {
        val rows = buildList {
            add(labels.regionLabel to labels.regionValue)
            add(labels.scopeLabel to labels.scopeValue)
            add(labels.contractLabel to model.header.contractName)
            add(labels.spNoLabel to model.header.spNo)
            add(labels.routeLabel to model.header.route)
            add(labels.ekAraligiLabel to model.header.ekAraligi)
            // Üçü de koşullu (Faz W): "Yeni Tesis" projesinde kablo seçilmemiş olabilir ve boş bir
            // etiket satırı basmak eksik veri izlenimi verirdi. `adresB` bu deseni zaten kullanıyordu.
            model.header.kableId?.let { add(labels.kableIdLabel to it) }
            model.header.adresA?.let { add(labels.adresALabel to it) }
            model.header.adresB?.let { add(labels.adresBLabel to it) }
            add(labels.createdAtLabel to DATE_TIME_FORMAT.format(model.createdAt))
        }.joinToString("\n") { (label, value) ->
            "<tr><td class=\"label\">${label.escapeHtml()}</td><td>${value.escapeHtml()}</td></tr>"
        }
        return "<table class=\"fields\">$rows</table>"
    }

    private fun itemsTable(model: DocumentModel, labels: PdfLabels): String {
        val kesifVar = kesifSutunlariVarMi(model)
        val sutunSayisi = columnCount(model)

        val headerCells = buildString {
            append("<th>${labels.columnPozNo.escapeHtml()}</th>")
            append("<th>${labels.columnName.escapeHtml()}</th>")
            append("<th>${labels.columnUnit.escapeHtml()}</th>")
            append("<th class=\"num\">${labels.columnUnitPrice.escapeHtml()}</th>")
            if (kesifVar) append("<th class=\"num\">${labels.columnKesifQuantity.escapeHtml()}</th>")
            append("<th class=\"num\">${labels.columnQuantity.escapeHtml()}</th>")
            if (kesifVar) append("<th class=\"num\">${labels.columnKesifAmount.escapeHtml()}</th>")
            append("<th class=\"num\">${labels.columnAmount.escapeHtml()}</th>")
        }

        val bodyRows = buildString {
            model.sections.forEach { section ->
                if (section.groupName.isNotEmpty()) {
                    append(
                        "<tr class=\"group\"><td>${section.groupPozNo.escapeHtml()}</td>" +
                            "<td colspan=\"${sutunSayisi - 1}\">${section.groupName.escapeHtml()}</td></tr>",
                    )
                }
                section.rows.forEach { row -> append(itemRow(row, kesifVar)) }
            }
            model.total?.let { toplam ->
                // Hakedişte toplam satırı İKİ tutar hücresi taşır (keşif toplamı + kesin toplam);
                // etiketin colspan'i ona göre daralır, yoksa satır tablodan taşar ve XHTML bozulur.
                val tutarHucreleri = if (kesifVar) {
                    "<td class=\"num\">${model.kesifTotal?.let { MoneyFormatter.money(it) }.orEmpty().escapeHtml()}</td>" +
                        "<td class=\"num\">${MoneyFormatter.money(toplam).escapeHtml()}</td>"
                } else {
                    "<td class=\"num\">${MoneyFormatter.money(toplam).escapeHtml()}</td>"
                }
                val etiketGenisligi = if (kesifVar) sutunSayisi - 2 else sutunSayisi - 1
                append(
                    "<tr class=\"total\"><td colspan=\"$etiketGenisligi\">" +
                        "${labels.totalLabel.escapeHtml()}</td>$tutarHucreleri</tr>",
                )
            }
        }

        return """
            <table class="items">
              <thead><tr>$headerCells</tr></thead>
              <tbody>$bodyRows</tbody>
            </table>
        """.trimIndent()
    }

    private fun itemRow(row: DocumentRow, kesifVar: Boolean): String = buildString {
        append("<tr>")
        append("<td>${row.pozNo.escapeHtml()}</td>")
        append("<td>${row.name.escapeHtml()}</td>")
        append("<td>${row.unit.escapeHtml()}</td>")
        // Birim fiyat **yuvarlanmadan** basılır: tutar ham fiyattan hesaplanıyor (§5), dolayısıyla
        // 2 haneye yuvarlanmış bir fiyat belgede "Br Fiyat × Miktar ≠ Tutar" gibi görünür ve belgeyi
        // eline alan (özellikle aracı firma) hesabı hatalı sanır. Gösterilen fiyat, kullanılan fiyat.
        append("<td class=\"num\">${row.unitPrice.moneyText().escapeHtml()}</td>")
        // Keşif miktarı para değil: `TEKLIF` varyantında da basılır. Keşif tutarı para, gizlenir.
        if (kesifVar) {
            append("<td class=\"num\">${row.kesifQuantity?.let { MoneyFormatter.quantity(it) }.orEmpty().escapeHtml()}</td>")
        }
        append("<td class=\"num\">${MoneyFormatter.quantity(row.quantity).escapeHtml()}</td>")
        if (kesifVar) append("<td class=\"num\">${row.kesifAmount.moneyText().escapeHtml()}</td>")
        append("<td class=\"num\">${row.amount.moneyText().escapeHtml()}</td>")
        append("</tr>")
    }

    private fun footerBlock(model: DocumentModel, labels: PdfLabels): String =
        """
        <div class="footer">
          <div>${labels.routeFileLabel.escapeHtml()}: ${(model.attachments.routeFileName ?: labels.routeFileMissing).escapeHtml()}</div>
          <div>${labels.imagesLabel.escapeHtml()}: ${labels.imagesValue.escapeHtml()}</div>
          <div>${labels.createdAtLabel.escapeHtml()}: ${DATE_TIME_FORMAT.format(model.createdAt)}</div>
        </div>
        ${materialsBlock(model, labels)}
        """.trimIndent()

    /**
     * EK3 "TTI Kullanılan Malzemeler" — yalnızca hakedişte dolu, boşsa **hiç basılmaz**.
     *
     * `<table>` kullanılıyor çünkü motor CSS 2.1 tabanlı ve `display: flex` sessizce yok sayılıyor
     * (CLAUDE.md §10). Üç alan da opsiyonel olduğu için boş hücreler normaldir.
     */
    private fun materialsBlock(model: DocumentModel, labels: PdfLabels): String {
        val materials = model.attachments.materials
        if (materials.isEmpty()) return ""

        val rows = materials.joinToString("") { material ->
            "<tr>" +
                "<td>${material.ad.orEmpty().escapeHtml()}</td>" +
                "<td class=\"num\">${material.miktar?.stripTrailingZeros()?.toPlainString().orEmpty().escapeHtml()}</td>" +
                "<td>${material.seriNo.orEmpty().escapeHtml()}</td>" +
                "</tr>"
        }

        return """
        <div class="materials">
          <div class="materials-title">${labels.materialsLabel.escapeHtml()}</div>
          <table>
            <thead>
              <tr>
                <th>${labels.materialName.escapeHtml()}</th>
                <th class="num">${labels.materialQuantity.escapeHtml()}</th>
                <th>${labels.materialSerial.escapeHtml()}</th>
              </tr>
            </thead>
            <tbody>$rows</tbody>
          </table>
        </div>
        """.trimIndent()
    }

    /** `TEKLIF` varyantında para alanları `null`'dır ve hücre boş basılır. */
    private fun BigDecimal?.moneyText(): String = this?.let(MoneyFormatter::money).orEmpty()

    /** Yuvarlanmamış gösterim — yalnızca birim fiyat için; tutar ve toplam zaten kuruşa yuvarlanmıştır. */

    /** Kullanıcı metni HTML'i bozmasın diye kaçış (CLAUDE.md §10). */
    private fun String.escapeHtml(): String = buildString(length) {
        for (char in this@escapeHtml) {
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(char)
            }
        }
    }
}

/** Şablonun ihtiyaç duyduğu tüm metinler; sunum katmanında çözülüp buraya verilir. */
data class PdfLabels(
    val brandName: String,
    /** TTI logosunun base64'ü; yoksa [brandName] metni yazılır. */
    val logoBase64: String? = null,
    /** Rapor başlığı: `Bölge/Saha + SP No`. */
    val reportTitle: String,
    /** Belge türünün okunur karşılığı ("Ön Fizibilite", "Keşif", "Hakediş"). */
    val typeValue: String,
    val regionLabel: String,
    val regionValue: String,
    val scopeLabel: String,
    val scopeValue: String,
    val contractLabel: String,
    val spNoLabel: String,
    val routeLabel: String,
    val ekAraligiLabel: String,
    val kableIdLabel: String,
    val adresALabel: String,
    val adresBLabel: String,
    val createdAtLabel: String,
    val columnPozNo: String,
    val columnName: String,
    val columnUnit: String,
    val columnUnitPrice: String,
    val columnQuantity: String,
    val columnAmount: String,
    /** "1. Keşif Talep" — yalnızca hakedişte basılır; diğer türlerde hiç kullanılmaz. */
    val columnKesifQuantity: String = "",
    /** "1.Keşif Tutar" — yalnızca hakedişte basılır. */
    val columnKesifAmount: String = "",
    val totalLabel: String,
    val routeFileLabel: String,
    val routeFileMissing: String,
    val imagesLabel: String,
    val imagesValue: String,
    /**
     * EK3 başlıkları — yalnızca hakedişte, malzeme girilmişse basılır. Varsayılanları var ki
     * bu bloğu hiç kullanmayan çağrı yerleri (mobil önizleme) parametre eklemek zorunda kalmasın.
     */
    val materialsLabel: String = "EK3 (TTI Kullanılan Malzemeler)",
    val materialName: String = "Malzeme Adı",
    val materialQuantity: String = "Malzeme Miktarı",
    val materialSerial: String = "Seri No",
)
