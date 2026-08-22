package tr.com.tti.hakedis.tools

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFCell
import org.apache.poi.xssf.usermodel.XSSFSheet
import tr.com.tti.hakedis.domain.contract.Contract
import tr.com.tti.hakedis.domain.contract.ContractMeta
import tr.com.tti.hakedis.domain.contract.PozItem
import tr.com.tti.hakedis.domain.contract.parseSortKey
import java.math.BigDecimal
import java.math.MathContext
import java.util.Locale

class ContractParseException(message: String) : IllegalStateException(message)

/**
 * Bir sözleşme sheet'ini [Contract]'a çevirir.
 *
 * Kaynak dosyalar elle düzenlendiği ve farklı firmalardan geldiği için biçim tam oturmuş değildir;
 * bu yüzden:
 * - başlık satırı sabit satır numarasından değil, poz no başlığı aranarak bulunur,
 * - **her sütunun birden çok kabul edilen adı vardır** (aşağıdaki listeler),
 * - anlamlandırılamayan satır **sessizce atlanmaz**, hata fırlatılır (CLAUDE.md §7 "fail loud").
 *
 * Ad listeleri **öncelik sırasındadır**: aynı sayfada birden fazla aday varsa ilk yazan kullanılır ve
 * durum uyarı olarak bildirilir. Sessiz seçim yapılmaz — hangi sütunun fiyat sayıldığı bir para
 * kararıdır ve yanlış sütun seçilse hiçbir hata çıkmazdı.
 */
object ContractXlsxParser {

    /**
     * Poz numarası sütunu — aynı zamanda **başlık satırının işareti**: bu adlardan biri bir hücrede
     * tam olarak geçiyorsa o satır başlık satırıdır. `MiktarOkuyucu` da aynı listeyi kullanır ki iki
     * okuyucu başlık satırını farklı yerde bulmasın.
     */
    val POZ_NO_TITLES = listOf("ISC_POZNO", "POZ", "Poz No")

    private val NAME_TITLES = listOf("ISC_ADI", "MALZEME VEYA İŞİN CİNSİ", "İş Kalemi")
    private val UNIT_TITLES = listOf("ISC_BIRIM", "ÖLÇÜ", "Birim")
    private val PRICE_TITLES = listOf("ESK Sonrası Güncel Fiyat", "Br Fiyat", "Birim Fiyat")

    fun parse(sheet: XSSFSheet, warnings: MutableList<String>): Contract {
        val headerRow = findHeaderRow(sheet)
        val columns = mapColumns(sheet, headerRow, warnings)
        val title = findTitle(sheet, headerRow.rowNum) ?: run {
            warnings += "'${sheet.sheetName}': başlık satırı yok, sheet adı başlık olarak kullanıldı."
            cleanSheetName(sheet.sheetName)
        }

        val items = mutableListOf<PozItem>()
        for (rowIndex in (headerRow.rowNum + 1)..sheet.lastRowNum) {
            val row = sheet.getRow(rowIndex) ?: continue
            items += parseRow(sheet, row, columns) ?: continue
        }

        if (items.isEmpty()) {
            throw ContractParseException("'${sheet.sheetName}': hiç poz satırı bulunamadı.")
        }

        return Contract(
            meta = ContractMeta(
                id = slugify(cleanSheetName(sheet.sheetName)),
                name = cleanSheetName(sheet.sheetName),
                title = title,
                sheetName = sheet.sheetName,
            ),
            items = items,
        )
    }

    private fun parseRow(sheet: XSSFSheet, row: Row, columns: Columns): PozItem? {
        val pozNo = readPozNo(row.getCell(columns.pozNo))
        val name = readText(row.getCell(columns.name))

        // Tamamen boş satır ya da "Genel Toplam" gibi alt bilgi satırı: poz numarası da adı da yok.
        if (pozNo.isEmpty() && name.isEmpty()) return null

        if (pozNo.isEmpty()) {
            throw ContractParseException(
                "'${sheet.sheetName}' satır ${row.rowNum + 1}: poz numarası boş ama iş kalemi dolu ('$name').",
            )
        }
        if (name.isEmpty()) {
            throw ContractParseException(
                "'${sheet.sheetName}' satır ${row.rowNum + 1}: '$pozNo' poz numarasının iş kalemi adı boş.",
            )
        }

        val sortKey = parseSortKey(pozNo)
        if (sortKey.isEmpty()) {
            throw ContractParseException(
                "'${sheet.sheetName}' satır ${row.rowNum + 1}: '$pozNo' poz numarasından sıralama anahtarı üretilemedi.",
            )
        }

        val unit = readText(row.getCell(columns.unit))
        val isGroup = unit.isEmpty()
        val unitPrice = readPrice(sheet, row, columns.price)

        if (isGroup && unitPrice.signum() != 0) {
            throw ContractParseException(
                "'${sheet.sheetName}' satır ${row.rowNum + 1}: '$pozNo' birimsiz (grup) görünüyor ama fiyatı var ($unitPrice).",
            )
        }

        return PozItem(
            id = "r${row.rowNum + 1}",
            pozNo = pozNo,
            sortKey = sortKey,
            name = name,
            unit = unit,
            unitPrice = unitPrice,
            isGroup = isGroup,
            isPriced = !isGroup && unitPrice.signum() > 0,
        )
    }

    private fun findHeaderRow(sheet: XSSFSheet): Row {
        for (rowIndex in 0..sheet.lastRowNum) {
            val row = sheet.getRow(rowIndex) ?: continue
            if (row.any { cell -> POZ_NO_TITLES.any { matchesTitle(readText(cell), it) } }) return row
        }
        throw ContractParseException("'${sheet.sheetName}': başlık satırı bulunamadı (${POZ_NO_TITLES.joinToString(" / ")}).")
    }

    private fun mapColumns(sheet: XSSFSheet, headerRow: Row, warnings: MutableList<String>): Columns {
        val titles = headerRow.associate { cell -> cell.columnIndex to readText(cell) }

        /**
         * Kabul edilen adlardan **ilk bulunanın** sütun indeksi.
         *
         * Birden fazla aday varsa seçim uyarıya yazılır: aynı sayfada hem `Br Fiyat` hem
         * `Birim Fiyat` bulunması meşrudur (biri eski, biri güncel olabilir) ve hangisinin
         * okunduğunu söylemeden geçmek, yanlış fiyatla sözleşme yüklemenin sessiz yolu olurdu.
         */
        fun columnOf(adaylar: List<String>, alanAdi: String): Int {
            val bulunanlar = adaylar.mapNotNull { ad -> titles.entries.firstOrNull { matchesTitle(it.value, ad) }?.let { ad to it.key } }
            val (secilen, sutun) = bulunanlar.firstOrNull()
                ?: throw ContractParseException(
                    "'${sheet.sheetName}': $alanAdi sütunu bulunamadı (${adaylar.joinToString(" / ")}).",
                )

            if (bulunanlar.size > 1) {
                warnings += "'${sheet.sheetName}': $alanAdi için birden fazla sütun var " +
                    "(${bulunanlar.joinToString(", ") { it.first }}); '$secilen' kullanıldı."
            }
            return sutun
        }

        return Columns(
            pozNo = columnOf(POZ_NO_TITLES, "poz no"),
            name = columnOf(NAME_TITLES, "iş kalemi adı"),
            unit = columnOf(UNIT_TITLES, "birim"),
            price = columnOf(PRICE_TITLES, "fiyat"),
        )
    }

    /**
     * Sütun başlığı karşılaştırması. `uppercase(Locale.ROOT)` kullanılmaz:
     * `"Fiyat"` → `"FIYAT"` olur ve Türkçe `"FİYAT"` ile eşleşmez (CLAUDE.md §5 locale tuzağı).
     * Bunun yerine fazla boşluklar sadeleştirilip büyük/küçük harf duyarsız karşılaştırma yapılır.
     *
     * `equals(ignoreCase = true)` Unicode varsayılan katlamasını kullandığı için `İ ↔ i` ve `I ↔ ı`
     * kendiliğinden eşleşir — `MALZEME VEYA İŞİN CİNSİ` ile `Malzeme veya işin cinsi` aynı sayılır.
     */
    fun matchesTitle(cellText: String, expected: String): Boolean =
        cellText.replace(Regex("\\s+"), " ").trim().equals(expected, ignoreCase = true)

    /** Başlık satırının üstündeki ilk dolu hücrenin metni; PDF başlığı olarak kullanılır. */
    private fun findTitle(sheet: XSSFSheet, headerRowIndex: Int): String? {
        for (rowIndex in 0 until headerRowIndex) {
            val row = sheet.getRow(rowIndex) ?: continue
            val text = row.firstNotNullOfOrNull { cell -> readText(cell).takeIf { it.isNotEmpty() } }
            if (text != null) return text
        }
        return null
    }

    /**
     * Poz numaraları Excel'de kimi zaman **sayı** olarak tutulmuş; `4.6` değeri
     * `4.5999999999999996` şeklinde okunur. Sayısal hücrelerde bu kayan nokta gürültüsü temizlenir.
     * Metin hücreleri olduğu gibi korunur (`4.10`, `4.4.`, `6,23`).
     */
    private fun readPozNo(cell: Cell?): String {
        if (cell == null) return ""
        return when (cell.cellType) {
            CellType.NUMERIC -> {
                val raw = (cell as XSSFCell).rawValue ?: return ""
                BigDecimal(raw).round(MathContext(12)).stripTrailingZeros().toPlainString()
            }
            else -> readText(cell)
        }
    }

    private fun readPrice(sheet: XSSFSheet, row: Row, columnIndex: Int): BigDecimal {
        val cell = row.getCell(columnIndex) ?: return BigDecimal.ZERO
        return when (cell.cellType) {
            CellType.BLANK -> BigDecimal.ZERO
            // Ham metinden okunur: `getNumericCellValue()` Double üzerinden geçip hassasiyet kaybettirirdi.
            CellType.NUMERIC -> (cell as XSSFCell).rawValue?.let(::BigDecimal) ?: BigDecimal.ZERO
            CellType.STRING -> fiyatMetniniCoz(cell.stringCellValue) ?: throw ContractParseException(
                "'${sheet.sheetName}' satır ${row.rowNum + 1}: fiyat okunamadı ('${cell.stringCellValue}').",
            )
            // Formül hesaplanamaz: POI'nin önbelleklediği sonuç `Double`'dır ve para hesabında Double
            // yasak (CLAUDE.md §5). Fail loud — kullanıcıya ne yapması gerektiği söylenir.
            CellType.FORMULA -> throw ContractParseException(
                "'${sheet.sheetName}' satır ${row.rowNum + 1}: fiyat hücresi formül içeriyor. " +
                    "Fiyat sütununu değer olarak yapıştırıp (Özel Yapıştır → Değerler) tekrar yükleyin.",
            )
            else -> throw ContractParseException(
                "'${sheet.sheetName}' satır ${row.rowNum + 1}: fiyat hücresi beklenmedik tipte (${cell.cellType}).",
            )
        }
    }

    private fun readText(cell: Cell?): String = when {
        cell == null -> ""
        cell.cellType == CellType.STRING -> cell.stringCellValue.trim()
        cell.cellType == CellType.NUMERIC -> (cell as XSSFCell).rawValue?.trim().orEmpty()
        cell.cellType == CellType.BOOLEAN -> cell.booleanCellValue.toString()
        else -> ""
    }

    /** `2026_Güvenay_Rota-1 Br Fiyatlar` → `2026 Güvenay Rota-1` */
    private fun cleanSheetName(sheetName: String): String = sheetName.replace('_', ' ')
        .replace(Regex("(?i)br\\s+fiyatlar"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    /**
     * Dosya adı/kimlik üretimi. Türkçe karakter dönüşümü ve küçük harfe çevirme
     * **daima `Locale.ROOT`** ile yapılır — `"I".lowercase()` Türkçe locale'de `"ı"` üretip kimliği bozardı.
     *
     * `public`: sunucu sözleşme kodunu üretirken (`SozlesmeYuklemeServisi`) aynı alfabeyi kullanmak
     * zorunda. İkinci bir kopya yazılsaydı, biri Türkçe karakteri diğerinden farklı katladığında
     * kimlik sessizce ayrışırdı (CLAUDE.md §7/1).
     */
    fun slugify(text: String): String {
        val ascii = buildString {
            for (char in text) {
                append(TURKISH_TO_ASCII[char] ?: char)
            }
        }
        return ascii.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
    }

    private val TURKISH_TO_ASCII = mapOf(
        'ç' to 'c', 'Ç' to 'C',
        'ğ' to 'g', 'Ğ' to 'G',
        'ı' to 'i', 'İ' to 'I',
        'ö' to 'o', 'Ö' to 'O',
        'ş' to 's', 'Ş' to 'S',
        'ü' to 'u', 'Ü' to 'U',
    )

    private data class Columns(val pozNo: Int, val name: Int, val unit: Int, val price: Int)
}
