package tr.com.tti.hakedis.server.belge

import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFSheet
import tr.com.tti.hakedis.domain.pricing.QuantityInput
import tr.com.tti.hakedis.domain.pricing.QuantityParser
import tr.com.tti.hakedis.server.sozlesme.PozSablonu
import tr.com.tti.hakedis.server.sozlesme.XlsxOkuyucu
import tr.com.tti.hakedis.server.web.GecersizIstekException
import tr.com.tti.hakedis.tools.ContractXlsxParser
import java.math.BigDecimal

/** Okuma sonucu: satırlar + parse sırasında birikmiş bilgilendirme uyarıları. */
data class OkunanMiktarListesi(val satirlar: List<YuklenenSatir>, val uyarilar: List<String>)

/**
 * Yüklenen miktar dosyasını okur.
 *
 * Poz/ad/birim okuma işi **`ContractXlsxParser`'a devredilir** — sözleşme okuma mantığı ikinci kez
 * yazılmaz (CLAUDE.md §7/1). Parser'ın bilmediği iki sütun burada okunur ve satır numarası üzerinden
 * parser'ın çıktısıyla eşleştirilir (`PozItem.id` = `"r{satır}"`): `POZ_KIMLIK` ve `Miktar`.
 *
 * Dosyanın açılması, sayfa seçimi ve POI istisnalarının 400'e çevrilmesi `XlsxOkuyucu`'dadır —
 * sözleşme yükleme ucu aynı kuralları kullanır.
 */
object MiktarOkuyucu {
    fun oku(icerik: ByteArray, sayfaAdi: String?): OkunanMiktarListesi {
        val uyarilar = mutableListOf<String>()

        return XlsxOkuyucu.sayfada(icerik, sayfaAdi) { sayfa ->
            val contract = ContractXlsxParser.parse(sayfa, uyarilar)
            val kimlikSutunu = sutunIndeksi(sayfa, PozSablonu.KIMLIK_BASLIGI)
            val miktarSutunu = sutunIndeksi(sayfa, PozSablonu.MIKTAR_BASLIGI)
                ?: throw GecersizIstekException(
                    "Dosyada '${PozSablonu.MIKTAR_BASLIGI}' sütunu yok. Miktar şablonunu indirip onu doldurun.",
                )

            if (kimlikSutunu == null) {
                uyarilar += "Dosyada ${PozSablonu.KIMLIK_BASLIGI} sütunu yok; eşleştirme satır sırasına göre " +
                    "yapılacak ve her satır poz no, ad ve birim ile teyit edilecek."
            }

            val satirlar = contract.items.map { poz ->
                val satirNo = satirNumarasi(poz.id)
                YuklenenSatir(
                    pozId = kimlikSutunu?.let { kimlikOku(sayfa, satirNo, it) },
                    satirKodu = poz.id,
                    pozNo = poz.pozNo,
                    ad = poz.name,
                    birim = poz.unit,
                    miktar = miktarOku(sayfa, satirNo, miktarSutunu),
                )
            }
            OkunanMiktarListesi(satirlar, uyarilar)
        }
    }

    /**
     * Başlık satırındaki [baslik] sütununun indeksi.
     *
     * Başlık satırının **hangi satır olduğu** parser ile aynı kurala bağlı (`POZ_NO_TITLES`) ve
     * karşılaştırma da onun `matchesTitle`'ı ile yapılıyor: iki okuyucu başlık satırını farklı yerde
     * bulursa `POZ_KIMLIK` ve `Miktar` sütunları başka bir satırdan okunur ve hiçbir hata çıkmaz.
     */
    private fun sutunIndeksi(sayfa: XSSFSheet, baslik: String): Int? {
        for (satirNo in 0..sayfa.lastRowNum) {
            val satir = sayfa.getRow(satirNo) ?: continue
            val hucreler = satir.associate { it.columnIndex to metin(satir, it.columnIndex) }
            if (hucreler.values.none { deger -> ContractXlsxParser.POZ_NO_TITLES.any { ContractXlsxParser.matchesTitle(deger, it) } }) {
                continue
            }
            return hucreler.entries.firstOrNull { ContractXlsxParser.matchesTitle(it.value, baslik) }?.key
        }
        return null
    }

    private fun kimlikOku(sayfa: XSSFSheet, satirNo: Int, sutun: Int): Long? {
        val hucre = sayfa.getRow(satirNo)?.getCell(sutun) ?: return null
        return when (hucre.cellType) {
            CellType.NUMERIC -> hucre.numericCellValue.toLong()
            CellType.STRING -> hucre.stringCellValue.trim().toLongOrNull()
            else -> null
        }
    }

    /**
     * Miktar hücresi.
     *
     * Sayısal hücrede `BigDecimal.valueOf(double)` kullanılır, `BigDecimal(double)` **değil**: ikincisi
     * `12.5`'i `12.5000000000000017763568394002504646778106689453125` yapar (CLAUDE.md §5).
     *
     * Metin hücrede çözümleme `QuantityParser`'a devredilir — `,`/`.` ayrımı ve binlik grup kuralı
     * ekranla birebir aynı kalsın diye. Çözülemeyen metin `Gecersiz` döner ve eşleştirici satırı
     * sorun olarak raporlar; sessizce 0 yazılmaz.
     */
    private fun miktarOku(sayfa: XSSFSheet, satirNo: Int, sutun: Int): OkunanMiktar {
        val hucre = sayfa.getRow(satirNo)?.getCell(sutun) ?: return OkunanMiktar.Bos
        return when (hucre.cellType) {
            CellType.BLANK -> OkunanMiktar.Bos
            CellType.NUMERIC -> OkunanMiktar.Deger(BigDecimal.valueOf(hucre.numericCellValue))
            CellType.STRING -> when (val sonuc = QuantityParser.parse(hucre.stringCellValue)) {
                QuantityInput.Empty -> OkunanMiktar.Bos
                is QuantityInput.Invalid -> OkunanMiktar.Gecersiz(hucre.stringCellValue.trim())
                is QuantityInput.Valid -> OkunanMiktar.Deger(sonuc.value)
            }

            else -> OkunanMiktar.Gecersiz(hucre.toString())
        }
    }

    private fun metin(satir: Row, sutun: Int): String {
        val hucre = satir.getCell(sutun) ?: return ""
        return if (hucre.cellType == CellType.STRING) hucre.stringCellValue.trim() else ""
    }

    /** `PozItem.id` biçimi `"r{1-tabanlı satır}"`; buradan 0-tabanlı POI satır numarası çıkar. */
    private fun satirNumarasi(pozSatirKodu: String): Int = (pozSatirKodu.removePrefix("r").toIntOrNull() ?: 1) - 1
}
