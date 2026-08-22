package tr.com.tti.hakedis.server.sozlesme

import org.dhatim.fastexcel.Workbook
import java.io.ByteArrayOutputStream
import java.math.BigDecimal

/**
 * Miktar giriş şablonu (.xlsx) — yanlış satıra yazma riskine karşı **asıl çözüm**
 * (NEWPHASES.md E10/Katman 1; fiyat listesi için tasarlanmıştı, Faz P'de miktara uyarlandı).
 *
 * Şablonun ilk sütunu `POZ_KIMLIK`: veritabanındaki `poz.id`. Yükleme bu sütundan eşleştirir, satır
 * konumundan değil. Böylece dosyada satır eklenip silinse, satırlar sıralansa bile miktarlar doğru
 * kalemlere gider. Sütun silinirse eşleştirme Katman 2'ye (satır kodu + poz no/ad/birim teyidi) düşer.
 *
 * Sütun adları ve satır düzeni bilinçli olarak `ContractXlsxParser`'ın beklediği biçimde: 1. satır
 * başlık, 2. satır sütun adları, 3. satırdan itibaren pozlar. Böylece yüklenen dosya **aynı parse
 * mantığıyla** okunur, ikinci bir okuyucu yazılmaz. `Br Fiyat` sütunu bu yüzden korunuyor — parser
 * fiyat sütunu bulamazsa dosyayı hiç açamaz. Kullanıcı açısından da faydalı: miktarı girerken birim
 * fiyatı görüyor.
 *
 * Miktar sütunu **mevcut değerlerle dolu** iner: kullanıcı ekranda girdiği miktarları dosyada bulur
 * ve yalnızca değişecekleri düzeltir. Boş bırakılan hücre "girilmemiş" demektir.
 */
object PozSablonu {
    const val KIMLIK_BASLIGI = "POZ_KIMLIK"

    const val MIKTAR_BASLIGI = "Miktar"

    /** `ContractXlsxParser`'ın tanıdığı fiyat sütunu adlarından biri olmak zorunda. */
    private const val FIYAT_BASLIGI = "Br Fiyat"

    private const val UYGULAMA = "TTI Saha Hakediş"
    private const val SURUM = "1.0"
    private const val BASLIK_DOLGU = "E8F0FF"
    private const val GRUP_DOLGU = "EDF1F6"
    private const val PARA_BICIMI = "#,##0.00"
    private const val MIKTAR_BICIMI = "#,##0.###"

    /** Excel sayfa adı 31 karakteri geçemez ve `: \ / ? * [ ]` kabul etmez. */
    private const val EN_UZUN_SAYFA_ADI = 31

    private const val BASLIK_SATIRI = 1
    private const val ILK_POZ_SATIRI = 2
    private const val SUTUN_FIYAT = 4
    private const val SUTUN_MIKTAR = 5
    private const val SON_SUTUN = 5

    /** @param miktarlar `poz.id → girilmiş miktar`; boş harita tamamen boş bir şablon üretir. */
    fun uret(
        sozlesmeBasligi: String,
        sozlesmeAdi: String,
        pozlar: List<ReferansPoz>,
        miktarlar: Map<Long, BigDecimal> = emptyMap(),
    ): ByteArray {
        val cikti = ByteArrayOutputStream()
        val kitap = Workbook(cikti, UYGULAMA, SURUM)
        val sayfa = kitap.newWorksheet(sayfaAdi(sozlesmeAdi))

        sayfa.value(0, 0, sozlesmeBasligi)
        sayfa.range(0, 0, 0, SON_SUTUN).style().bold().fontSize(12).set()

        val basliklar = listOf(KIMLIK_BASLIGI, "ISC_POZNO", "ISC_ADI", "ISC_BIRIM", FIYAT_BASLIGI, MIKTAR_BASLIGI)
        basliklar.forEachIndexed { sutun, ad -> sayfa.value(BASLIK_SATIRI, sutun, ad) }
        sayfa.range(BASLIK_SATIRI, 0, BASLIK_SATIRI, SON_SUTUN).style()
            .bold()
            .fillColor(BASLIK_DOLGU)
            .borderStyle("thin")
            .set()

        pozlar.forEachIndexed { indeks, poz ->
            val satir = ILK_POZ_SATIRI + indeks
            sayfa.value(satir, 0, poz.pozId as Number)
            sayfa.value(satir, 1, poz.pozNo)
            sayfa.value(satir, 2, poz.ad)
            sayfa.value(satir, 3, poz.birim)
            // Grup satırları daima fiyatsız ve miktarsız kalır: eşleştirici başlığa değer girilmesini
            // reddediyor, parser da birimsiz satırın fiyatlı olmasını hata sayıyor.
            if (poz.grupMu) {
                sayfa.range(satir, 0, satir, SON_SUTUN).style().bold().fillColor(GRUP_DOLGU).set()
            } else {
                // Değerler `BigDecimal` olarak yazılır: `Double`'a çevrilirse ham hassasiyet kayar ve
                // dosya geri yüklendiğinde sayı sessizce değişmiş olurdu (CLAUDE.md §5).
                poz.referansFiyat?.let { sayfa.value(satir, SUTUN_FIYAT, it as Number) }
                sayfa.style(satir, SUTUN_FIYAT).format(PARA_BICIMI).set()
                miktarlar[poz.pozId]?.let { sayfa.value(satir, SUTUN_MIKTAR, it as Number) }
                sayfa.style(satir, SUTUN_MIKTAR).format(MIKTAR_BICIMI).set()
            }
        }

        sayfa.width(0, 12.0)
        sayfa.width(1, 12.0)
        sayfa.width(2, 60.0)
        sayfa.width(3, 10.0)
        sayfa.width(SUTUN_FIYAT, 18.0)
        sayfa.width(SUTUN_MIKTAR, 14.0)

        kitap.finish()
        return cikti.toByteArray()
    }

    private fun sayfaAdi(sozlesmeAdi: String): String = sozlesmeAdi
        .replace(Regex("[\\\\/:*?\\[\\]]"), "-")
        .take(EN_UZUN_SAYFA_ADI)
        .ifBlank { "Poz Listesi" }
}
