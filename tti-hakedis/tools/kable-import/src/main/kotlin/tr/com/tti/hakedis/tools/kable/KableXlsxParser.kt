package tr.com.tti.hakedis.tools.kable

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.InputStream

/**
 * Kable envanteri (.xlsx) okuyucusu.
 *
 * Dosyada iki sayfa vardır:
 *  - `cable` — kable kayıtları. 80'den fazla sütunun yalnızca altısı alınır; gerisi ya sabit
 *    (status = IN SERVICE, typeofservice = Cable) ya da bu uygulamada kullanılmıyor.
 *  - `Saha`  — "Technical Consultant" → saha eşleşme tablosu. Yetkilendirmenin kaynağı budur.
 *
 * Fail loud: eksik sayfa/sütun, boş veya tekrar eden `nosid`, eşleşme tablosunda bulunmayan danışman
 * durumunda hiç yazmadan hata verilir. Sessizce atlanan satır, sessizce yanlış bölgeye düşen kable demektir.
 */
object KableXlsxParser {
    private const val SAYFA_KABLE = "cable"
    private const val SAYFA_SAHA = "Saha"

    private const val SUTUN_NOSID = "nosid"
    private const val SUTUN_DANISMAN = "Technical Consultant"
    private const val SUTUN_ADRES_A = "addressA"
    private const val SUTUN_ADRES_B = "addressB"
    private const val SUTUN_SEHIR_A = "cityA"
    private const val SUTUN_SEHIR_B = "cityB"

    /** Konum yolunun alan adları ve kuralları [KonumYolu]'nda — parser ile elle giriş aynı kaynağı kullanır. */
    private val KONUM_ALANLARI = KonumYolu.ALANLAR

    fun parse(dosya: File): List<KableSatiri> {
        if (!dosya.isFile) throw KableParseException("Envanter dosyası bulunamadı: ${dosya.absolutePath}")
        return dosya.inputStream().use { parse(it) }
    }

    /**
     * Aynı okuyucunun akış hâli — yöneticinin web'den yüklediği dosya diske yazılmadan okunur
     * (`MultipartFile`). Parse mantığı ikinci kez yazılmaz (CLAUDE.md §7/1).
     */
    fun parse(girdi: InputStream): List<KableSatiri> {
        XSSFWorkbook(girdi).use { workbook ->
            val sahaSayfasi = workbook.getSheet(SAYFA_SAHA)
                ?: throw KableParseException("'$SAYFA_SAHA' sayfası yok — danışman/bölge eşleşmesi okunamıyor.")
            val kableSayfasi = workbook.getSheet(SAYFA_KABLE)
                ?: throw KableParseException("'$SAYFA_KABLE' sayfası yok.")

            val danismanSaha = sahaEslesmesiOku(sahaSayfasi)
            return kableSatirlariOku(kableSayfasi, danismanSaha)
        }
    }

    /** `Saha` sayfası: A sütunu danışman adı, B sütunu saha etiketi. İlk satır başlıktır. */
    private fun sahaEslesmesiOku(sayfa: Sheet): Map<String, String> {
        val eslesme = LinkedHashMap<String, String>()
        for (satirNo in 1..sayfa.lastRowNum) {
            val satir = sayfa.getRow(satirNo) ?: continue
            val danisman = metin(satir.getCell(0)) ?: continue
            val saha = metin(satir.getCell(1))
                ?: throw KableParseException("'$SAYFA_SAHA' satır ${satirNo + 1}: '$danisman' için saha boş.")
            eslesme[danisman] = saha
        }
        if (eslesme.isEmpty()) throw KableParseException("'$SAYFA_SAHA' sayfası boş.")
        return eslesme
    }

    private fun kableSatirlariOku(sayfa: Sheet, danismanSaha: Map<String, String>): List<KableSatiri> {
        val baslikSatiri = sayfa.getRow(sayfa.firstRowNum)
            ?: throw KableParseException("'$SAYFA_KABLE' sayfasında başlık satırı yok.")
        val sutunlar = sutunIndeksleri(baslikSatiri)

        val satirlar = ArrayList<KableSatiri>()
        val gorulenNosid = HashSet<String>()

        for (satirNo in (sayfa.firstRowNum + 1)..sayfa.lastRowNum) {
            val satir = sayfa.getRow(satirNo) ?: continue
            val excelSatirNo = satirNo + 1

            val nosid = metin(satir.getCell(sutunlar.getValue(SUTUN_NOSID)))
                ?: if (satirBos(satir)) continue else throw KableParseException("Satır $excelSatirNo: '$SUTUN_NOSID' boş.")
            if (!gorulenNosid.add(nosid)) {
                throw KableParseException("Satır $excelSatirNo: '$nosid' envanterde birden fazla kez geçiyor.")
            }

            val danisman = metin(satir.getCell(sutunlar.getValue(SUTUN_DANISMAN)))
                ?: throw KableParseException("Satır $excelSatirNo ($nosid): '$SUTUN_DANISMAN' boş.")
            val saha = danismanSaha[danisman]
                ?: throw KableParseException(
                    "Satır $excelSatirNo ($nosid): '$danisman' '$SAYFA_SAHA' sayfasında yok. " +
                        "Eşleşme tablosuna eklenmeli, yoksa kable yanlış bölgeye düşer.",
                )
            // Adres alanının kendisi zorunlu kalıyor: konum yolu ülke/şehirle dolu görünse bile
            // adressiz bir kable eksik veridir ve sessizce geçmemeli.
            if (metin(satir.getCell(sutunlar.getValue(SUTUN_ADRES_A))) == null) {
                throw KableParseException("Satır $excelSatirNo ($nosid): '$SUTUN_ADRES_A' boş.")
            }

            satirlar += KableSatiri(
                nosid = nosid,
                danismanAd = danisman,
                sahaAdi = saha,
                adresA = konumYolu(satir, sutunlar, "A")
                    ?: throw KableParseException("Satır $excelSatirNo ($nosid): A ucunun konum bilgisi boş."),
                adresB = konumYolu(satir, sutunlar, "B"),
                sehirA = metin(satir.getCell(sutunlar.getValue(SUTUN_SEHIR_A))),
                sehirB = metin(satir.getCell(sutunlar.getValue(SUTUN_SEHIR_B))),
            )
        }

        if (satirlar.isEmpty()) throw KableParseException("'$SAYFA_KABLE' sayfasında veri satırı yok.")
        return satirlar
    }

    /**
     * Bir ucun konum yolu; hiçbir segment yoksa `null` (B ucu boş olabilir).
     *
     * @param yon `"A"` ya da `"B"` — sütun adlarının soneki (`countryA`, `countryB`).
     */
    private fun konumYolu(satir: Row, sutunlar: Map<String, Int>, yon: String): String? =
        KonumYolu.kur { ad -> metin(satir.getCell(sutunlar.getValue(ad + yon))) }

    private fun sutunIndeksleri(baslikSatiri: Row): Map<String, Int> {
        val beklenen = listOf(
            SUTUN_NOSID,
            SUTUN_DANISMAN,
            SUTUN_ADRES_A,
            SUTUN_ADRES_B,
            SUTUN_SEHIR_A,
            SUTUN_SEHIR_B,
        ).plus(listOf("A", "B").flatMap { yon -> KONUM_ALANLARI.map { it + yon } }).distinct()
        val bulunan = HashMap<String, Int>()
        for (sutunNo in 0 until baslikSatiri.lastCellNum) {
            val baslik = metin(baslikSatiri.getCell(sutunNo)) ?: continue
            beklenen.firstOrNull { it.equals(baslik, ignoreCase = true) }?.let { bulunan.putIfAbsent(it, sutunNo) }
        }
        val eksik = beklenen - bulunan.keys
        if (eksik.isNotEmpty()) throw KableParseException("'$SAYFA_KABLE' sayfasında eksik sütun: $eksik")
        return bulunan
    }

    private fun satirBos(satir: Row): Boolean = (0 until satir.lastCellNum).all { metin(satir.getCell(it)) == null }

    /** Hücreyi metne çevirir; sadeleştirme kuralları [KonumYolu.sadelestir]'de (elle girişle ortak). */
    private fun metin(hucre: Cell?): String? {
        if (hucre == null) return null
        val ham = when (hucre.cellType) {
            CellType.STRING -> hucre.stringCellValue
            CellType.NUMERIC -> hucre.numericCellValue.toString()
            CellType.BOOLEAN -> hucre.booleanCellValue.toString()
            CellType.FORMULA -> runCatching { hucre.stringCellValue }.getOrNull()
            else -> null
        } ?: return null
        return KonumYolu.sadelestir(ham)
    }
}
