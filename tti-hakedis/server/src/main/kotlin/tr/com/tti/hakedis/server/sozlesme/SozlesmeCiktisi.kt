package tr.com.tti.hakedis.server.sozlesme

import org.dhatim.fastexcel.Workbook
import tr.com.tti.hakedis.domain.document.DocumentFileNamer
import java.io.ByteArrayOutputStream

/**
 * Var olan bir sözleşmenin **dolu** `.xlsx` hâli — satır menüsündeki "İndir".
 *
 * Sözleşmeler değiştirilemez (§7/4): fiyat ya da poz değişikliği yeni bir yükleme gerektirir. Bu
 * çıktı o akışın **girdisidir** — indirilen dosya Excel'de düzeltilip mevcut yükleme akışıyla yeni
 * bir kodla geri yüklenir. Bu yüzden sütun düzeni [SozlesmeSablonu] ile **birebir aynı** olmak
 * zorunda; ayrışırsa indirilen dosya geri yüklenemez ve hata ancak yükleme denenince çıkar.
 *
 * `SozlesmeCiktisiTest` bu eşdeğerliği kilitliyor: üretilen dosya `ContractXlsxParser`'dan geçmeli.
 */
object SozlesmeCiktisi {
    private const val UYGULAMA = "TTI Saha Hakediş"
    private const val SURUM = "1.0"
    private const val BASLIK_DOLGU = "E8F0FF"
    private const val GRUP_DOLGU = "EDF1F6"
    private const val PARA_BICIMI = "#,##0.00"

    private const val BASLIK_SATIRI = 1
    private const val ILK_POZ_SATIRI = 2
    private const val SON_SUTUN = 3

    /** Excel'in sayfa adında kabul etmediği karakterler. */
    private val FORBIDDEN_SHEET_CHARS = charArrayOf(':', '\\', '/', '?', '*', '[', ']')

    fun dosyaAdi(kod: String): String = "TTI_SOZLESME_${DocumentFileNamer.sanitize(kod)}.xlsx"

    /**
     * Sayfa adı **sözleşmenin kodudur**, sabit bir "Sozlesme" değil.
     *
     * Yeni kod sayfa adından türetiliyor (`SozlesmeYuklemeServisi`); sabit bir ad kullanılsaydı her
     * indirilen dosya geri yüklendiğinde `sozlesme` kodunu üretir ve yönetici adı sıfırdan yazmak
     * zorunda kalırdı. Kodun kendisi inince yapılacak tek şey onu değiştirmek — ki yükleme akışı
     * zaten bunu zorunlu kılıyor: aynı kod ikinci kez yazılamaz.
     *
     * Excel sayfa adı 31 karakterle sınırlı ve `: \ / ? * [ ]` kabul etmiyor.
     */
    private fun sayfaAdi(kod: String): String =
        kod.map { if (it in FORBIDDEN_SHEET_CHARS) '-' else it }.joinToString("").take(31).ifBlank { "Sozlesme" }

    fun uret(kod: String, baslik: String, pozlar: List<PozYaniti>): ByteArray {
        val cikti = ByteArrayOutputStream()
        val kitap = Workbook(cikti, UYGULAMA, SURUM)
        val sayfa = kitap.newWorksheet(sayfaAdi(kod))

        // 1. satır sözleşme başlığı — PDF başlığı olarak da bu basılıyor (CLAUDE.md §4).
        sayfa.value(0, 0, baslik)
        sayfa.range(0, 0, 0, SON_SUTUN).style().bold().fontSize(12).set()

        // Başlıklar boş şablonla AYNI listeden: indirilen dolu dosya doğrudan geri yüklenebilmeli.
        SozlesmeSablonu.BASLIKLAR.forEachIndexed { sutun, ad -> sayfa.value(BASLIK_SATIRI, sutun, ad) }
        sayfa.range(BASLIK_SATIRI, 0, BASLIK_SATIRI, SON_SUTUN).style()
            .bold()
            .fillColor(BASLIK_DOLGU)
            .borderStyle("thin")
            .set()

        pozlar.forEachIndexed { sira, poz ->
            val satir = ILK_POZ_SATIRI + sira
            sayfa.value(satir, 0, poz.pozNo)
            sayfa.value(satir, 1, poz.name)

            if (poz.isGroup) {
                // Grup satırı birimsiz ve fiyatsız yazılır; parser birimsiz satırı grup sayıyor.
                sayfa.range(satir, 0, satir, SON_SUTUN).style().bold().fillColor(GRUP_DOLGU).set()
            } else {
                sayfa.value(satir, 2, poz.unit)
                // Ham fiyat yazılır (yuvarlanmış değil): dosya sözleşmenin kaynağı, çıktısı değil.
                // Yuvarlama belgeye çivilenirken yapılır (§5) ve burada yapılsaydı geri yüklenen
                // sözleşme sessizce kuruş kaybederdi.
                if (poz.isPriced) {
                    sayfa.value(satir, 3, poz.unitPrice)
                    sayfa.style(satir, 3).format(PARA_BICIMI).set()
                }
            }
        }

        sayfa.width(0, 12.0)
        sayfa.width(1, 60.0)
        sayfa.width(2, 10.0)
        sayfa.width(3, 20.0)

        kitap.finish()
        return cikti.toByteArray()
    }
}
