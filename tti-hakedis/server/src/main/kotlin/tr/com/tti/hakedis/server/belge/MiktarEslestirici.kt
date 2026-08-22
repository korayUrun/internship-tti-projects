package tr.com.tti.hakedis.server.belge

import tr.com.tti.hakedis.server.sozlesme.PozSablonu
import tr.com.tti.hakedis.server.sozlesme.ReferansPoz
import java.math.BigDecimal

/** Miktar hücresinin üç hâli. "Boş" ile "0 girilmiş" **ayrı** durumlardır (CLAUDE.md §5). */
sealed interface OkunanMiktar {
    data object Bos : OkunanMiktar

    data class Deger(val value: BigDecimal) : OkunanMiktar

    data class Gecersiz(val ham: String) : OkunanMiktar
}

/** Yüklenen dosyadan okunan tek satır. [pozId] yalnızca `POZ_KIMLIK` sütunu varsa doludur. */
data class YuklenenSatir(
    val pozId: Long?,
    /** `"r5"` — Excel satır numarasından türeyen kod; kimlik sütunu yoksa tek eşleştirme anahtarı. */
    val satirKodu: String,
    val pozNo: String,
    val ad: String,
    val birim: String,
    val miktar: OkunanMiktar,
)

/** Eşleşmiş kalem: hangi poza hangi miktar yazılacak. */
data class EslesenKalem(val pozId: Long, val satirKodu: String, val pozNo: String, val miktar: BigDecimal)

/** Kullanıcıya gösterilecek tek sapma. Satır bazlı olması "hangi satırı düzelteceğim" sorusunu cevaplar. */
data class EslestirmeSorunu(val satirKodu: String, val pozNo: String, val aciklama: String)

/**
 * Eşleştirme sonucu. [gecerliMi] değilse **hiçbir şey yazılmaz** — kısmi yükleme yok.
 *
 * [uyarilar] engellemez, işaretler (ör. kimlik sütunu silinmiş).
 */
data class EslestirmeSonucu(
    val kimlikSutunuKullanildi: Boolean,
    val eslesenler: List<EslesenKalem>,
    val sorunlar: List<EslestirmeSorunu>,
    val uyarilar: List<String>,
) {
    val gecerliMi: Boolean get() = sorunlar.isEmpty()
}

/**
 * Yüklenen miktar listesini belgenin poz listesiyle eşleştirir — **para hatasının en sinsi biçimine
 * karşı savunma** (NEWPHASES.md E10; fiyat listesi için yazılmıştı, Faz P'de miktara uyarlandı).
 *
 * Kök sorun aynı: `poz.satir_kodu` (`"r5"`) fiziksel Excel satır numarasıdır. Yüklenen dosyada bir
 * satır eklenmiş/silinmişse tüm alttaki kodlar kayar; naif bir eşleştirme miktarları **yanlış
 * kalemlere** yazar ve hiçbir hata çıkmaz. Yanlış satıra yazılan miktar da doğrudan para hatasıdır,
 * bu yüzden iki katmanlı savunma aynen korundu.
 *
 * İki eşleştirme yolu var:
 * 1. **Kimlik sütunu** (`POZ_KIMLIK` = veritabanındaki `poz.id`) — sistemin ürettiği şablonda bulunur.
 *    Satır konumundan tamamen bağımsızdır: satırlar eklenip silinse, sıralansa bile eşleşme bozulmaz.
 * 2. **Satır kodu** — kimlik sütunu yoksa. Bu yolda her satır `poz_no` + `ad` + `birim` üçlüsüyle
 *    **teyit edilir**; kayma varsa bu üçlü tutmaz ve satır sorun olarak raporlanır.
 *
 * Fiyat listesinden **farklı** iki kural (miktarın doğası gereği):
 * - Boş hücre hata değil, "girilmemiş" demektir; dolayısıyla "her kalem gelmeli" şartı yoktur.
 * - `0` geçerli bir miktardır ve yazılır — "girilmemiş" ile "0 girilmiş" ayrı tutulur.
 */
object MiktarEslestirici {
    fun eslestir(referans: List<ReferansPoz>, yuklenen: List<YuklenenSatir>): EslestirmeSonucu {
        val sorunlar = mutableListOf<EslestirmeSorunu>()
        val uyarilar = mutableListOf<String>()

        // Kimlik sütunu ancak TÜM satırlarda doluysa kullanılır: kısmen dolu bir sütun, hangi satırın
        // hangi yolla eşleştiğini belirsiz bırakır ve sessiz karışmanın kapısını açar.
        val kimlikliMi = yuklenen.isNotEmpty() && yuklenen.all { it.pozId != null }

        val referansKimlige = referans.associateBy { it.pozId }
        val referansSatirKoduna = referans.associateBy { it.satirKodu }

        val eslesenler = mutableListOf<EslesenKalem>()
        val kullanilanPozlar = mutableSetOf<Long>()

        for (satir in yuklenen) {
            val ref = if (kimlikliMi) referansKimlige[satir.pozId] else referansSatirKoduna[satir.satirKodu]

            if (ref == null) {
                sorunlar += EslestirmeSorunu(
                    satir.satirKodu,
                    satir.pozNo,
                    if (kimlikliMi) {
                        "${PozSablonu.KIMLIK_BASLIGI} ${satir.pozId} bu sözleşmede yok — şablon başka bir sözleşmeye ait olabilir."
                    } else {
                        "Bu satırın sözleşmede karşılığı yok (fazla satır)."
                    },
                )
                continue
            }

            if (!kullanilanPozlar.add(ref.pozId)) {
                sorunlar += EslestirmeSorunu(satir.satirKodu, satir.pozNo, "Aynı kalem dosyada birden fazla kez var.")
                continue
            }

            // Kimlik sütunu yoksa satır kayması ancak bu teyitle yakalanır.
            if (!kimlikliMi) {
                val tutmayan = buildList {
                    if (satir.pozNo != ref.pozNo) add("poz no (dosyada '${satir.pozNo}', sözleşmede '${ref.pozNo}')")
                    if (satir.ad != ref.ad) add("iş kalemi adı")
                    if (satir.birim != ref.birim) add("birim (dosyada '${satir.birim}', sözleşmede '${ref.birim}')")
                }
                if (tutmayan.isNotEmpty()) {
                    sorunlar += EslestirmeSorunu(
                        satir.satirKodu,
                        satir.pozNo,
                        "Satır sözleşmeyle uyuşmuyor — ${tutmayan.joinToString(", ")}. " +
                            "Dosyada satır eklenmiş/silinmiş olabilir; miktar şablonunu indirip onu doldurun.",
                    )
                    continue
                }
            }

            when (val miktar = satir.miktar) {
                // Boş hücre = girilmemiş. Fiyat listesinden farkı bu: her kalemin dolu olması gerekmiyor.
                OkunanMiktar.Bos -> Unit

                is OkunanMiktar.Gecersiz -> sorunlar += EslestirmeSorunu(
                    satir.satirKodu,
                    satir.pozNo,
                    "Miktar sayıya çevrilemedi: '${miktar.ham}'.",
                )

                is OkunanMiktar.Deger -> when {
                    ref.grupMu -> sorunlar += EslestirmeSorunu(
                        satir.satirKodu,
                        satir.pozNo,
                        "Grup başlığına miktar girilmiş; başlık satırları miktarsız olmalı.",
                    )

                    miktar.value.signum() < 0 -> sorunlar += EslestirmeSorunu(
                        satir.satirKodu,
                        satir.pozNo,
                        "Miktar negatif olamaz: ${miktar.value}.",
                    )

                    // Fiyatlandırılmamış kaleme miktar yazılamaz; sunucu satırı zaten reddederdi,
                    // ama hata "bu pozlara miktar girilemez" diye toplu gelirdi. Burada satır satır.
                    ref.referansFiyat == null || ref.referansFiyat.signum() <= 0 -> sorunlar += EslestirmeSorunu(
                        satir.satirKodu,
                        satir.pozNo,
                        "Bu kalem sözleşmede fiyatlandırılmamış; miktar girilemez.",
                    )

                    else -> eslesenler += EslesenKalem(ref.pozId, ref.satirKodu, ref.pozNo, miktar.value)
                }
            }
        }

        return EslestirmeSonucu(kimlikliMi, eslesenler, sorunlar, uyarilar)
    }
}
