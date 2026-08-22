package tr.com.tti.hakedis.server.kable

import org.dhatim.fastexcel.Workbook
import tr.com.tti.hakedis.tools.kable.KonumYolu
import java.io.ByteArrayOutputStream

/** Excel'e yazılacak tek kablo satırı — `kable_surum_satiri` ile aynı alanlar. */
data class KableCiktiSatiri(
    val nosid: String,
    val danismanAd: String,
    val sahaAdi: String,
    val adresA: String,
    val adresB: String?,
    val sehirA: String?,
    val sehirB: String?,
)

/**
 * Yürürlükteki envanterin `.xlsx` hâli (Faz V) — "Envanteri İndir".
 *
 * Veritabanı asıl kaynaktır (kullanıcı kararı): yüklenen ham dosya saklanmıyor, bu çıktı her zaman
 * **güncel** hâli üretiyor. Elle eklenen kablolar da içinde.
 *
 * Düzen `KableXlsxParser`'ın beklediğinin aynısıdır — dosya doğrudan geri yüklenebilir ve fark
 * `0 yeni · 0 değişen · 0 düşen` çıkar. `KableCiktisiTest` bunu kilitliyor: çıktı ile parser
 * ayrışırsa hata "yeniden yükleyince envanter eridi" biçiminde değil, testte görünür.
 *
 * **Konum yolu bölünmüyor.** `adres_a` tek bir metin olarak `addressA` sütununa yazılıyor; parser
 * boş bıraktığımız `countryA`, `cityA` … sütunlarını atlayacak ve aynı yolu yeniden kuracak
 * ([KonumYolu] boş segmentleri atlıyor). Bölmeye kalkmak, "AND Plaza > Ground Floor" gibi birleşik
 * segmentleri geri ayırmayı gerektirirdi ve tersi tek anlamlı değil.
 */
object KableCiktisi {
    const val DOSYA_ADI = "TTI_KABLO_ENVANTERI.xlsx"

    private const val UYGULAMA = "TTI Saha Hakediş"
    private const val SURUM = "1.0"
    private const val BASLIK_DOLGU = "E8F0FF"

    /** Parser'ın adıyla aradığı sütunlar; sıra serbest ama hepsi bulunmak zorunda. */
    private val SABIT_SUTUNLAR = listOf("nosid", "Technical Consultant", "addressA", "addressB", "cityA", "cityB")

    /**
     * ⚠️ `distinct()` **zorunlu**: `KonumYolu.ALANLAR` içindeki `address` ve `city`, sonek eklenince
     * sabit sütunlarla (`addressA`, `cityA` …) çakışıyor. Tekrarlı liste iki farklı indeks üretir;
     * parser `putIfAbsent` ile **ilkini** seçer, yazıcı ise sonuncusuna yazardı ve dosya sessizce
     * boş `addressA` ile inerdi. Parser aynı yerde aynı `distinct()`'i kullanıyor.
     */
    private val SUTUNLAR = (SABIT_SUTUNLAR + listOf("A", "B").flatMap { yon -> KonumYolu.ALANLAR.map { it + yon } }).distinct()

    fun uret(satirlar: List<KableCiktiSatiri>): ByteArray {
        val cikti = ByteArrayOutputStream()
        val kitap = Workbook(cikti, UYGULAMA, SURUM)

        yazKable(kitap.newWorksheet("cable"), satirlar)
        yazSaha(kitap.newWorksheet("Saha"), satirlar)

        kitap.finish()
        return cikti.toByteArray()
    }

    private fun yazKable(sayfa: org.dhatim.fastexcel.Worksheet, satirlar: List<KableCiktiSatiri>) {
        val sutunNo = SUTUNLAR.withIndex().associate { (indeks, ad) -> ad to indeks }
        SUTUNLAR.forEachIndexed { indeks, ad -> sayfa.value(0, indeks, ad) }
        sayfa.range(0, 0, 0, SUTUNLAR.size - 1).style().bold().fillColor(BASLIK_DOLGU).set()

        satirlar.forEachIndexed { indeks, satir ->
            val satirNo = indeks + 1
            sayfa.value(satirNo, sutunNo.getValue("nosid"), satir.nosid)
            sayfa.value(satirNo, sutunNo.getValue("Technical Consultant"), satir.danismanAd)
            ucYaz(sayfa, satirNo, sutunNo, "A", satir.adresA, satir.sehirA)
            ucYaz(sayfa, satirNo, sutunNo, "B", satir.adresB, satir.sehirB)
        }

        sayfa.width(sutunNo.getValue("nosid"), 18.0)
        sayfa.width(sutunNo.getValue("Technical Consultant"), 24.0)
        sayfa.width(sutunNo.getValue("addressA"), 70.0)
        sayfa.width(sutunNo.getValue("addressB"), 70.0)
    }

    /**
     * Bir ucun sütunlarını yazar.
     *
     * ⚠️ **`cityA` iki işi birden görüyor**: hem bağımsız şehir sütunu (`kable.sehir_a`) hem konum
     * yolunun `city` segmenti. Yolun tamamını `addressA`'ya yazıp `cityA`'yı da doldurursak parser
     * şehri yola bir kez daha ekler (`Izmir >> Turkiye >> Izmir >> …`) ve gidiş-dönüş bozulur.
     *
     * Çözüm: yolu şehir segmentinden bölmek. Şehirden önceki parça `countryA`'ya, sonraki parça
     * `addressA`'ya gider; parser üçünü aynı sırayla birleştirince **birebir aynı** yol çıkar
     * (`addressA` içindeki `>>` ayraçları tek bir segmentin içinde kalıyor, yeniden bölünmüyor).
     *
     * Şehir yolda bulunamazsa (parser'ın ürettiği veride olmayan bir tutarsızlık) yol olduğu gibi
     * `addressA`'ya yazılır ve şehir sütunu boş bırakılır — yol, şehri zaten içermediği için
     * kaybolan bir bilgi yok, tersi durumda ise tekrar üretilirdi.
     */
    private fun ucYaz(
        sayfa: org.dhatim.fastexcel.Worksheet,
        satirNo: Int,
        sutunNo: Map<String, Int>,
        yon: String,
        yol: String?,
        sehir: String?,
    ) {
        if (yol == null) return
        val segmentler = yol.split(KonumYolu.AYRAC)
        val sehirIndeksi = sehir?.let { segmentler.indexOf(it) } ?: -1

        if (sehirIndeksi < 0) {
            sayfa.value(satirNo, sutunNo.getValue("address$yon"), yol)
            return
        }

        segmentler.take(sehirIndeksi).joinToString(KonumYolu.AYRAC).takeIf { it.isNotEmpty() }
            ?.let { sayfa.value(satirNo, sutunNo.getValue("country$yon"), it) }
        sayfa.value(satirNo, sutunNo.getValue("city$yon"), segmentler[sehirIndeksi])
        segmentler.drop(sehirIndeksi + 1).joinToString(KonumYolu.AYRAC).takeIf { it.isNotEmpty() }
            ?.let { sayfa.value(satirNo, sutunNo.getValue("address$yon"), it) }
    }

    /**
     * `Saha` sayfası: danışman → saha eşleşmesi. Parser bu tabloyu **zorunlu** tutuyor ve dosyada
     * geçen her danışmanın burada karşılığı olmak zorunda; eşleşme kablolardan türetiliyor.
     */
    private fun yazSaha(sayfa: org.dhatim.fastexcel.Worksheet, satirlar: List<KableCiktiSatiri>) {
        sayfa.value(0, 0, "Technical Consultant")
        sayfa.value(0, 1, "Saha")
        sayfa.range(0, 0, 0, 1).style().bold().fillColor(BASLIK_DOLGU).set()

        satirlar.associate { it.danismanAd to it.sahaAdi }
            .toSortedMap()
            .onEachIndexed { indeks, (danisman, saha) ->
                sayfa.value(indeks + 1, 0, danisman)
                sayfa.value(indeks + 1, 1, saha)
            }

        sayfa.width(0, 24.0)
        sayfa.width(1, 20.0)
    }
}
