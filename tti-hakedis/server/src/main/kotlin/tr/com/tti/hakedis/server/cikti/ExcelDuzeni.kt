package tr.com.tti.hakedis.server.cikti

import tr.com.tti.hakedis.domain.document.DocumentType

/**
 * Excel çıktısının sütun düzeni — **belge türüne bağlı** (NEWPHASES.md, Faz G kararı).
 *
 * - `KESIF` ve `HAKEDIS` kaynak şablonun **8 sütununu aynı şekilde** kullanır. Kaynak zaten bir
 *   keşif↔hakediş karşılaştırma sayfası; ikisini ayrı düzenlere bölmek, aynı işin iki belgesini yan
 *   yana koyan kişiyi sütun eşlemeye zorlardı. Keşifte "Saha Uygulama"nın boş kalması eksik değil,
 *   **henüz yaşanmamış aşamanın** göstergesidir.
 * - `FIZIBILITE` ayrışır: kaynaktaki hiçbir miktar sütunu ona karşılık gelmiyor ("1. Keşif Talep"
 *   kelime anlamıyla keşif talebi). Asıl gerekçe `TEKLIF` varyantı — o dosya **aracı firmaya gidiyor**
 *   ve boş bırakılmış "Saha Uygulama" / "Kesin Hakediş Tutar" başlıkları firmanın yanlış hücreye
 *   yazması için açık davetiye olurdu.
 *
 * İlk üç başlık kaynak dosyanın kendi başlıklarıdır (`ISC_POZNO`, `ISC_ADI`, `ISC_BIRIM`); çeviri
 * değil **veri sadakati** meselesi olduğu için etiket yapılandırmasına alınmadı.
 */
data class ExcelDuzeni(
    val basliklar: List<String>,
    /** Bu belgenin miktarının yazılacağı sütun. */
    val miktarSutunu: Int,
    /** Bu belgenin tutarının yazılacağı sütun. */
    val tutarSutunu: Int,
    /**
     * Aynı işin **keşif** miktarının yazılacağı sütun — yalnızca hakedişte dolu. Keşif belgesinde
     * `null`: orada 4. sütun zaten belgenin kendi miktarı, ikinci kez yazmak aynı değeri iki kez
     * basmak olurdu.
     */
    val kesifMiktarSutunu: Int? = null,
    /** Aynı işin keşif tutarının yazılacağı sütun — yalnızca hakedişte dolu. */
    val kesifTutarSutunu: Int? = null,
    /**
     * "Toplam" etiketinin yazılacağı sütun.
     *
     * Eskiden `tutarSutunu - 1` sabitti; hakedişte bu **6. sütun**, yani tam "1.Keşif Tutar"
     * sütunudur. O sütun veriyle dolunca etiket keşif genel toplamının üzerine oturuyordu — hata
     * vermeden, sessizce. Etiket artık düzenin kendi kararı.
     */
    val toplamEtiketSutunu: Int,
) {
    val sonSutun: Int get() = basliklar.lastIndex

    companion object {
        private const val KAYNAK_POZNO = "ISC_POZNO"
        private const val KAYNAK_ADI = "ISC_ADI"
        private const val KAYNAK_BIRIM = "ISC_BIRIM"

        fun of(tur: DocumentType, etiketler: CiktiEtiketleri): ExcelDuzeni {
            val ortak = listOf(KAYNAK_POZNO, KAYNAK_ADI, KAYNAK_BIRIM, etiketler.sutunBirimFiyat)

            return when (tur) {
                DocumentType.FIZIBILITE -> ExcelDuzeni(
                    basliklar = ortak + listOf(etiketler.sutunFizibiliteMiktar, etiketler.sutunFizibiliteTutar),
                    miktarSutunu = 4,
                    tutarSutunu = 5,
                    toplamEtiketSutunu = 4,
                )

                // Kaynak 8 sütun; keşif sol çifti (talep/tutar), hakediş sağ çifti (saha/kesin) doldurur.
                DocumentType.KESIF -> kaynakDuzeni(ortak, etiketler, miktarSutunu = 4, tutarSutunu = 6)

                // Hakediş kendi çiftini (5/7) doldurur VE keşif çiftini (4/6) aynı işin keşif
                // belgesinden okur: iki aşama yan yana karşılaştırılabilsin (kullanıcı isteği,
                // 31 Temmuz 2026). Sapma sütunu Faz K'de gelecek.
                DocumentType.HAKEDIS -> kaynakDuzeni(
                    ortak,
                    etiketler,
                    miktarSutunu = 5,
                    tutarSutunu = 7,
                    kesifMiktarSutunu = 4,
                    kesifTutarSutunu = 6,
                )
            }
        }

        private fun kaynakDuzeni(
            ortak: List<String>,
            etiketler: CiktiEtiketleri,
            miktarSutunu: Int,
            tutarSutunu: Int,
            kesifMiktarSutunu: Int? = null,
            kesifTutarSutunu: Int? = null,
        ) = ExcelDuzeni(
            basliklar = ortak + listOf(
                etiketler.sutunKesifMiktar,
                etiketler.sutunSahaMiktar,
                etiketler.sutunKesifTutar,
                etiketler.sutunHakedisTutar,
            ),
            miktarSutunu = miktarSutunu,
            tutarSutunu = tutarSutunu,
            kesifMiktarSutunu = kesifMiktarSutunu,
            kesifTutarSutunu = kesifTutarSutunu,
            // Hakedişte 4. ve 6. sütunlar veri taşıyor; etiket "İş Kalemi" sütununa alınır.
            toplamEtiketSutunu = if (kesifTutarSutunu != null) 1 else tutarSutunu - 1,
        )
    }
}
