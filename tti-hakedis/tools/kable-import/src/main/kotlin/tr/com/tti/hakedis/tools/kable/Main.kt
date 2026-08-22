package tr.com.tti.hakedis.tools.kable

import java.io.File
import java.sql.DriverManager
import kotlin.system.exitProcess

/**
 * Kable envanterini (.xlsx) veritabanına aktarır.
 *
 *     ./gradlew :tools:kable-import:run
 *     ./gradlew :tools:kable-import:run --args="baska/dosya.xlsx"
 *
 * Veritabanı bilgileri ortam değişkenlerinden okunur (parola komut satırında görünmesin):
 *   DB_URL, DB_KULLANICI, DB_PAROLA
 *
 * Faz U'dan beri bu yol da **sürüm üzerinden** işler: dosya önce `kable_surum` olarak saklanır,
 * sonra yürürlüğe alınır. Böylece `kable` tablosu her zaman bir sürümün izdüşümüdür ve CLI'dan
 * yapılan bir içe aktarım da web arayüzünden geri alınabilir. Araç, arayüzün olmadığı ilk kurulum
 * ve kurtarma durumları için duruyor; günlük yükleme yöneticinin ekranından yapılır.
 */
fun main(args: Array<String>) {
    val dosyaYolu = args.firstOrNull() ?: "tools/kable/cable_data_28072026.xlsx"
    val url = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/tti"
    val kullanici = System.getenv("DB_KULLANICI") ?: "tti_hakedis"
    val parola = System.getenv("DB_PAROLA") ?: ""

    try {
        val satirlar = KableXlsxParser.parse(File(dosyaYolu))
        println("Okundu: ${satirlar.size} kable · $dosyaYolu")

        // Transaction burada sarılıyor: sürüm kaydı, satırları ve `kable` izdüşümü ya birlikte
        // yazılır ya hiç yazılmaz (KableYazici artık commit atmıyor).
        val (surumId, sonuc) = DriverManager.getConnection(url, kullanici, parola).use { baglanti ->
            baglanti.createStatement().use { it.execute("set search_path to hakedis") }
            baglanti.autoCommit = false
            try {
                val yazici = KableSurumYazici(baglanti)
                val id = yazici.surumEkle(File(dosyaYolu).name, satirlar, yukleyenId = null)
                val ozet = yazici.yururlugeAl(id)
                baglanti.commit()
                id to ozet
            } catch (hata: Exception) {
                baglanti.rollback()
                throw hata
            }
        }

        println("Sürüm $surumId oluşturuldu ve yürürlüğe alındı.")
        println("Yazıldı: ${sonuc.eklenen} yeni, ${sonuc.guncellenen} güncellendi, ${sonuc.pasiflesen} pasifleştirildi")
        println("Bölge dağılımı:")
        sonuc.bolgeDagilimi.forEach { (bolge, adet) -> println("  %-10s %4d".format(bolge, adet)) }
        println("  %-10s %4d".format("TOPLAM", sonuc.bolgeDagilimi.values.sum()))
    } catch (hata: KableParseException) {
        System.err.println("İçe aktarım durdu — hiçbir şey yazılmadı.")
        System.err.println("  ${hata.message}")
        exitProcess(1)
    } catch (hata: Exception) {
        System.err.println("İçe aktarım başarısız: ${hata.message}")
        exitProcess(1)
    }
}
