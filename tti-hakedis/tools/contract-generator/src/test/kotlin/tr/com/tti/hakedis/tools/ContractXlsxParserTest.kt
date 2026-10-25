package tr.com.tti.hakedis.tools

import com.google.common.truth.Truth.assertThat
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Test
import java.math.BigDecimal

/**
 * Parser'ın davranışı — bir HTTP yükleme ucuna bağlanacağı için artık testli olmak zorunda.
 * Excel bellekte kurulur; dosya sistemine dokunulmaz.
 */
class ContractXlsxParserTest {
    private val uyarilar = mutableListOf<String>()

    /**
     * Kaynak dosyanın düzenini taklit eder: 1. satır sözleşme başlığı, 2. satır sütun başlıkları,
     * 3. satırdan itibaren pozlar. [satirlar] her biri `pozNo, ad, birim, fiyat` dörtlüsü; `birim` boşsa
     * satır grup başlığıdır.
     */
    private fun sayfa(
        baslik: String = "Rota-1: Edirne-Kırklareli",
        satirlar: List<List<String?>>,
        baslikSatiriniAtla: Boolean = false,
        basliklar: List<String> = listOf("ISC_POZNO", "ISC_ADI", "ISC_BIRIM", "ESK Sonrası Güncel Fiyat"),
    ): XSSFSheet {
        val kitap = XSSFWorkbook()
        val sayfa = kitap.createSheet("2026_Sample_Rota-1 Br Fiyatlar")

        sayfa.createRow(0).createCell(0).setCellValue(baslik)

        if (!baslikSatiriniAtla) {
            val basliklarSatiri = sayfa.createRow(1)
            basliklar.forEachIndexed { i, ad -> basliklarSatiri.createCell(i).setCellValue(ad) }
        }

        satirlar.forEachIndexed { indeks, hucreler ->
            val satir = sayfa.createRow(2 + indeks)
            hucreler.forEachIndexed { sutun, deger ->
                if (deger != null) satir.createCell(sutun).setCellValue(deger)
            }
        }
        return sayfa
    }

    private fun coz(sayfa: XSSFSheet) = ContractXlsxParser.parse(sayfa, uyarilar)

    // --- Grup satırı tespiti -------------------------------------------------------------------

    @Test
    fun `birimi olmayan satir grup basligidir`() {
        val contract = coz(
            sayfa(
                satirlar = listOf(
                    listOf("1", "HAVAİ GÜZERGAH İMALATLARI", "", null),
                    listOf("1.1", "Direk Dikme-montaj", "Ad.", "648.35852723050107"),
                ),
            ),
        )

        val grup = contract.items[0]
        assertThat(grup.isGroup).isTrue()
        assertThat(grup.isPriced).isFalse()

        val kalem = contract.items[1]
        assertThat(kalem.isGroup).isFalse()
        assertThat(kalem.isPriced).isTrue()
    }

    @Test
    fun `birimsiz ama fiyatli satir hata verir`() {
        val hata = runCatching {
            coz(sayfa(satirlar = listOf(listOf("1", "Şüpheli başlık", "", "100"))))
        }.exceptionOrNull()

        assertThat(hata).isInstanceOf(ContractParseException::class.java)
    }

    // --- Ham hassasiyet ------------------------------------------------------------------------

    @Test
    fun `17 haneli fiyat bozulmadan okunur`() {
        val contract = coz(
            sayfa(satirlar = listOf(listOf("1.1", "Direk Dikme-montaj", "Ad.", "648.35852723050107"))),
        )

        assertThat(contract.items.single().unitPrice).isEqualTo(BigDecimal("648.35852723050107"))
    }

    // --- Poz numarası ve sıralama --------------------------------------------------------------

    @Test
    fun `tekrar eden poz numarasi satir kodlariyla ayrilir`() {
        val contract = coz(
            sayfa(
                satirlar = listOf(
                    listOf("4.1", "Birinci", "Mt.", "10"),
                    listOf("4.1", "İkinci", "Mt.", "20"),
                ),
            ),
        )

        // Kimlik poz numarasından değil, Excel satır numarasından türer.
        assertThat(contract.items.map { it.id }).containsExactly("r3", "r4").inOrder()
        assertThat(contract.items.map { it.pozNo }).containsExactly("4.1", "4.1")
    }

    @Test
    fun `4 nokta 10 duz string siralamasindan farkli sortKey uretir`() {
        val contract = coz(
            sayfa(
                satirlar = listOf(
                    listOf("4.9", "Dokuz", "Mt.", "10"),
                    listOf("4.10", "On", "Mt.", "20"),
                ),
            ),
        )

        assertThat(contract.items[0].sortKey).isEqualTo(listOf(4, 9))
        assertThat(contract.items[1].sortKey).isEqualTo(listOf(4, 10))
    }

    // --- Sütun adı eşanlamlıları ---------------------------------------------------------------

    /**
     * Sözleşmeler farklı firmalardan farklı başlıklarla geliyor; aynı sütun her dosyada aynı adı
     * taşımıyor. Kabul edilen adların **hepsi** testli: biri listeden düşerse o firmanın dosyası
     * yüklenemez hâle gelir ve bunu ancak yönetici yükleme denerken fark ederdi.
     */
    @Test
    fun `poz no adi ve birim sutunlari esanlamlilariyla da okunur`() {
        val contract = coz(
            sayfa(
                basliklar = listOf("POZ", "MALZEME VEYA İŞİN CİNSİ", "ÖLÇÜ", "Birim Fiyat"),
                satirlar = listOf(
                    listOf("1", "HAVAİ GÜZERGAH İMALATLARI", "", null),
                    listOf("1.1", "Direk Dikme-montaj", "Ad.", "648.36"),
                ),
            ),
        )

        assertThat(contract.items).hasSize(2)
        assertThat(contract.items[0].isGroup).isTrue()
        assertThat(contract.items[1].name).isEqualTo("Direk Dikme-montaj")
        assertThat(contract.items[1].unit).isEqualTo("Ad.")
        assertThat(contract.items[1].unitPrice).isEqualTo(BigDecimal("648.36"))
    }

    @Test
    fun `br fiyat basligi da kabul edilir`() {
        val contract = coz(
            sayfa(
                basliklar = listOf("ISC_POZNO", "ISC_ADI", "ISC_BIRIM", "Br Fiyat"),
                satirlar = listOf(listOf("1.1", "Direk", "Ad.", "10.5")),
            ),
        )

        assertThat(contract.items.single().unitPrice).isEqualTo(BigDecimal("10.5"))
    }

    /**
     * Şablonun (ve indirilen dolu sözleşmenin) taşıdığı okunur başlıklar — 18 Ağustos 2026'da
     * `ISC_*` teknik adlarının yerine geçtiler.
     *
     * Bu test `SozlesmeSablonu.BASLIKLAR` ile parser arasındaki bağı kilitler: biri değişip diğeri
     * kalırsa hiçbir derleme hatası çıkmaz, yönetici dosyayı doldurup yükleyene kadar da fark
     * edilmez.
     */
    @Test
    fun `sablonun okunur basliklari kabul edilir`() {
        val contract = coz(
            sayfa(
                basliklar = listOf("Poz No", "İş Kalemi", "Birim", "Br Fiyat"),
                satirlar = listOf(
                    listOf("1", "HAVAİ GÜZERGAH İMALATLARI", null, null),
                    listOf("1.1", "Direk Dikme-montaj", "Ad.", "648,3585272"),
                ),
            ),
        )

        assertThat(contract.items).hasSize(2)
        assertThat(contract.items[0].isGroup).isTrue()
        assertThat(contract.items[1].pozNo).isEqualTo("1.1")
        assertThat(contract.items[1].name).isEqualTo("Direk Dikme-montaj")
        assertThat(contract.items[1].unit).isEqualTo("Ad.")
        assertThat(contract.items[1].unitPrice).isEqualTo(BigDecimal("648.3585272"))
    }

    /** Büyük/küçük harf ve fazla boşluk ayrımı yok; `İ ↔ i` katlaması Unicode varsayılanından gelir. */
    @Test
    fun `baslik buyuk kucuk harf ve bosluk duyarsiz eslesir`() {
        val contract = coz(
            sayfa(
                basliklar = listOf("poz", "Malzeme veya  İşin Cinsi", " ölçü ", "birim fiyat"),
                satirlar = listOf(listOf("1.1", "Direk", "Ad.", "10")),
            ),
        )

        assertThat(contract.items.single().pozNo).isEqualTo("1.1")
    }

    /**
     * Aynı sayfada iki fiyat adayı varsa **sessizce** biri seçilmez: hangi sütunun okunduğu bir para
     * kararıdır ve yanlış seçimde hiçbir hata çıkmazdı.
     */
    @Test
    fun `iki fiyat sutunu varsa ilki kullanilir ve uyari birakir`() {
        val contract = coz(
            sayfa(
                basliklar = listOf("ISC_POZNO", "ISC_ADI", "ISC_BIRIM", "ESK Sonrası Güncel Fiyat", "Br Fiyat"),
                satirlar = listOf(listOf("1.1", "Direk", "Ad.", "10", "99")),
            ),
        )

        assertThat(contract.items.single().unitPrice).isEqualTo(BigDecimal("10"))
        assertThat(uyarilar).hasSize(1)
        assertThat(uyarilar.single()).contains("ESK Sonrası Güncel Fiyat")
        assertThat(uyarilar.single()).contains("Br Fiyat")
    }

    // --- Fail loud durumları -------------------------------------------------------------------

    @Test
    fun `eksik sutun basligi hata verir`() {
        val hata = runCatching {
            coz(sayfa(satirlar = listOf(listOf("1.1", "Direk", "Ad.", "10")), baslikSatiriniAtla = true))
        }.exceptionOrNull()

        assertThat(hata).isInstanceOf(ContractParseException::class.java)
    }

    /**
     * Formül hücresi reddedilir: POI'nin önbelleklediği sonuç `Double`'dır ve para hesabında Double
     * yasak (CLAUDE.md §5). Mesaj kullanıcıya ne yapması gerektiğini söylemeli.
     */
    @Test
    fun `formul iceren fiyat hucresi anlasilir hata verir`() {
        val sayfa = sayfa(satirlar = listOf(listOf("1.1", "Direk Dikme-montaj", "Ad.", null)))
        sayfa.getRow(2).createCell(3).cellFormula = "1+1"

        val hata = runCatching { coz(sayfa) }.exceptionOrNull()

        assertThat(hata).isInstanceOf(ContractParseException::class.java)
        assertThat(hata!!.message).contains("formül")
        assertThat(hata.message).contains("değer olarak")
    }

    @Test
    fun `okunamayan metin fiyat hata verir`() {
        val hata = runCatching {
            coz(sayfa(satirlar = listOf(listOf("1.1", "Direk", "Ad.", "bilinmiyor"))))
        }.exceptionOrNull()

        assertThat(hata).isInstanceOf(ContractParseException::class.java)
    }

    // --- Başlık --------------------------------------------------------------------------------

    @Test
    fun `pdf basligi ilk satirdan alinir`() {
        val contract = coz(
            sayfa(baslik = "Rota-1: Edirne-Kırklareli", satirlar = listOf(listOf("1.1", "Direk", "Ad.", "10"))),
        )

        assertThat(contract.meta.title).isEqualTo("Rota-1: Edirne-Kırklareli")
        assertThat(uyarilar).isEmpty()
    }

    @Test
    fun `baslik yoksa uyari birakir ve sayfa adina duser`() {
        val contract = coz(sayfa(baslik = "", satirlar = listOf(listOf("1.1", "Direk", "Ad.", "10"))))

        assertThat(contract.meta.title).isNotEmpty()
        assertThat(uyarilar).isNotEmpty()
    }
}
