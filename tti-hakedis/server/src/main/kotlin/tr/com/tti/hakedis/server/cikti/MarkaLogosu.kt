package tr.com.tti.hakedis.server.cikti

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tr.com.tti.hakedis.server.ek.PngDogrulayici
import java.util.Base64

/**
 * PDF'in sağ üst köşesindeki TTI logosu.
 *
 * Görüntü HTML'e **base64 `data:` URI** olarak gömülür (`PdfTemplate.headerBlock`): üretici tarafın
 * dosya sistemine ya da bir ağ adresine erişmesi gerekmez, PDF tek parça üretilir.
 *
 * Dosya **isteğe bağlıdır**: yoksa şablon marka adını metin olarak basar ve belge üretimi aksamaz.
 * Bu, logonun kullanılabilir bir PNG'si gelene kadar (açık soru 8) sistemin çalışmasını sağlar;
 * dosya konduğunda kod değişmeden devreye girer.
 *
 * @param kaynakYolu jar içindeki yol. Testler kendi dosyalarını vermek için değiştirir.
 */
@Component
class MarkaLogosu(private val kaynakYolu: String = VARSAYILAN_YOL) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Dosyanın ham içeriği — **Excel** çıktısı bunu kullanır (`ExcelUretici`, fastexcel `addImage`
     * bayt dizisi ister). Bir kez okunur; logo her belgede aynı.
     */
    val baytlar: ByteArray? by lazy { oku() }

    /**
     * Aynı içeriğin base64'ü — **PDF** şablonu `data:` URI olarak gömüyor.
     *
     * [baytlar]'dan türetiliyor: dosya iki kez okunmaz ve iki çıktı aynı doğrulamadan geçer. Ayrı
     * okunsalardı biri PNG denetimini atlayabilir ve fark ancak bozuk çıktıda görülürdü.
     */
    val base64: String? by lazy { baytlar?.let { Base64.getEncoder().encodeToString(it) } }

    private fun oku(): ByteArray? {
        val icerik = javaClass.getResourceAsStream(kaynakYolu)?.use { it.readBytes() }
        if (icerik == null || icerik.isEmpty()) {
            log.info("Marka logosu bulunamadı ({}); çıktılarda marka adı metin olarak basılacak.", kaynakYolu)
            return null
        }
        if (!PngDogrulayici.pngMi(icerik)) {
            // Sessizce metne düşmek yerine sebebi söylenir: dosya konmuş ama biçimi yanlış demektir.
            log.warn("Marka logosu PNG değil ({}); çıktılarda marka adı metin olarak basılacak.", kaynakYolu)
            return null
        }

        return icerik
    }

    private companion object {
        const val VARSAYILAN_YOL = "/logo/tti-logo.png"
    }
}
