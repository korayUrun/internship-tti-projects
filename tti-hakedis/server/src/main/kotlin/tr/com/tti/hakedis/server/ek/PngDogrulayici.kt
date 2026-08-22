package tr.com.tti.hakedis.server.ek

/**
 * Bir baytın gerçekten PNG olduğunu **içeriğinden** doğrular.
 *
 * Ek dosyalarda uzantı doğrulaması yeterli (MIME tutarsız, CLAUDE.md §12/3), ama PDF'e gömülecek
 * görüntülerde değil: `logo.png` adlı bir PDF ya da rastgele bir bayt yığını sisteme girerse hata,
 * aylar sonra belge üretilirken ortaya çıkar — üretim anında, kullanıcı olmadan. Sekiz baytlık
 * kontrol bunu yükleme/okuma anına çeker. Bugünkü tek kullanıcısı marka logosudur (`MarkaLogosu`).
 *
 * PNG imzası (RFC 2083 §3.1): `89 50 4E 47 0D 0A 1A 0A`. Son dört bayt bilinçli seçilmiştir —
 * satır sonu dönüştüren bir aktarım dosyayı bozmuşsa imza da bozulur ve fark edilir.
 */
object PngDogrulayici {
    /** `89 50 4E 47 0D 0A 1A 0A` */
    private val PNG_IMZASI = "89504E470D0A1A0A".chunked(2).map { it.toInt(16).toByte() }

    fun pngMi(icerik: ByteArray): Boolean {
        if (icerik.size < PNG_IMZASI.size) return false
        return PNG_IMZASI.indices.all { icerik[it] == PNG_IMZASI[it] }
    }
}
