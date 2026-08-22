package tr.com.tti.hakedis.server.sozlesme

import org.apache.poi.EmptyFileException
import org.apache.poi.UnsupportedFileFormatException
import org.apache.poi.ooxml.POIXMLException
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import tr.com.tti.hakedis.server.web.GecersizIstekException
import tr.com.tti.hakedis.tools.ContractParseException

/**
 * Yüklenen bir `.xlsx`'i açar, sayfayı seçer ve **POI'nin istisnalarını 400'e çevirir**.
 *
 * İki çağıranı var — fiyat listesi yükleme (`FiyatListesiOkuyucu`) ve sözleşme yükleme
 * (`SozlesmeYuklemeServisi`). Yakalama listesi kopyalanmadı: aşağıdaki `UnsupportedFileFormatException`
 * dalı bir kez unutulduğunda yönetici yanlış dosya seçtiğinde 500 + "Yönetici ile iletişime geçin"
 * görüyordu — kendisi yönetici olduğu hâlde. Kural tek yerde durursa ikinci uçta yeniden unutulamaz.
 */
object XlsxOkuyucu {
    /**
     * [icerik]'i açar, [sayfaAdi] (ya da tek sayfa) üzerinde [islem]'i çalıştırır.
     *
     * Tek sayfalı dosyada sayfa kendiliğinden seçilir; çok sayfalıda [sayfaAdi] zorunludur — yanlış
     * sayfayı sessizce okuyup yanlış fiyatları yazmak, kullanıcıya sayfa adı sormaktan çok daha kötüdür.
     */
    fun <T> sayfada(icerik: ByteArray, sayfaAdi: String?, islem: (XSSFSheet) -> T): T = try {
        XSSFWorkbook(icerik.inputStream()).use { kitap -> islem(sayfaSec(kitap, sayfaAdi)) }
    } catch (hata: ContractParseException) {
        throw GecersizIstekException("Dosya okunamadı: ${hata.message}")
    } catch (hata: java.io.IOException) {
        throw GecersizIstekException("$BOZUK_DOSYA ${hata.message}")
    } catch (hata: EmptyFileException) {
        throw GecersizIstekException("$BOZUK_DOSYA dosya boş.")
    } catch (hata: UnsupportedFileFormatException) {
        // POI, xlsx olmayan bir dosyayı (PDF, eski .xls, ODF, bozuk ZIP) `NotOfficeXmlFileException`
        // ile reddediyor — bu bir **RuntimeException** (`UnsupportedFileFormatException` →
        // `IllegalArgumentException`), `IOException` değil. Yalnızca `IOException` yakalamak bu yolu
        // açıkta bırakıyordu.
        throw GecersizIstekException("$BOZUK_DOSYA ${hata.message}")
    } catch (hata: POIXMLException) {
        throw GecersizIstekException("$BOZUK_DOSYA ${hata.message}")
    }

    /** Dosyadaki sayfa adları — "hangisini istiyorsunuz" sorusunu sorabilmek için. */
    fun sayfaAdlari(icerik: ByteArray): List<String> = try {
        XSSFWorkbook(icerik.inputStream()).use { kitap -> (0 until kitap.numberOfSheets).map { kitap.getSheetName(it) } }
    } catch (hata: java.io.IOException) {
        throw GecersizIstekException("$BOZUK_DOSYA ${hata.message}")
    } catch (hata: EmptyFileException) {
        throw GecersizIstekException("$BOZUK_DOSYA dosya boş.")
    } catch (hata: UnsupportedFileFormatException) {
        throw GecersizIstekException("$BOZUK_DOSYA ${hata.message}")
    } catch (hata: POIXMLException) {
        throw GecersizIstekException("$BOZUK_DOSYA ${hata.message}")
    }

    private const val BOZUK_DOSYA = "Dosya geçerli bir .xlsx değil ya da bozuk:"

    private fun sayfaSec(kitap: XSSFWorkbook, sayfaAdi: String?): XSSFSheet {
        val adlar = (0 until kitap.numberOfSheets).map { kitap.getSheetName(it) }

        if (sayfaAdi != null) {
            return kitap.getSheet(sayfaAdi)
                ?: throw GecersizIstekException("'$sayfaAdi' adlı sayfa dosyada yok. Sayfalar: ${adlar.joinToString(", ")}")
        }
        if (kitap.numberOfSheets == 1) return kitap.getSheetAt(0)

        throw GecersizIstekException(
            "Dosyada birden fazla sayfa var; hangisinin okunacağını belirtin. Sayfalar: ${adlar.joinToString(", ")}",
        )
    }
}
