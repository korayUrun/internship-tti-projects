package tr.com.tti.hakedis.server.kable

import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tr.com.tti.hakedis.server.web.BulunamadiException
import tr.com.tti.hakedis.server.web.GecersizIstekException
import tr.com.tti.hakedis.tools.kable.KableElleYazici
import tr.com.tti.hakedis.tools.kable.KableSatiri
import tr.com.tti.hakedis.tools.kable.KonumYolu
import javax.sql.DataSource

/** Elle kablo ekleme/düzenleme isteği (Faz V). Uç adresleri [UcIstegi] ile gelir. */
data class KableIstegi(
    val nosid: String?,
    /** Bölgenin `xlsx_saha_adi` değeri; arayüz bunu bölge listesinden seçtiriyor. */
    val sahaAdi: String?,
    val danismanAd: String?,
    val sehirA: String?,
    val sehirB: String?,
    val ucA: UcIstegi?,
    val ucB: UcIstegi?,
)

/**
 * Yöneticinin arayüzden tek kablo eklemesi/düzenlemesi (Faz V).
 *
 * **Yeni sürüm doğmaz** (kullanıcı kararı): düzeltme anında geçerli olur. Tutarlılık, yazmanın
 * `kable` ile yürürlükteki `kable_surum_satiri`'na **birlikte** gitmesiyle korunuyor —
 * `KableElleYazici` bunu tek transaction'da yapıyor.
 *
 * Bağlantı `DataSourceUtils` ile alınıyor: `veriKaynagi.connection` denseydi havuzdan ayrı bir
 * bağlantı gelir ve iki yazma farklı transaction'lara düşerdi (`KableSurumServisi` ile aynı tuzak).
 */
@Service
class KableElleServisi(private val veriKaynagi: DataSource, private val sorgular: KableSorgulari) {
    @Transactional
    fun ekle(istek: KableIstegi): KableYaniti = yaz(istek, yeniKayit = true)

    @Transactional
    fun guncelle(nosid: String, istek: KableIstegi): KableYaniti = yaz(istek.copy(nosid = nosid), yeniKayit = false)

    private fun yaz(istek: KableIstegi, yeniKayit: Boolean): KableYaniti {
        val satir = KableSatiri(
            nosid = zorunlu(istek.nosid, "Kablo kimliği"),
            danismanAd = zorunlu(istek.danismanAd, "Danışman adı"),
            sahaAdi = zorunlu(istek.sahaAdi, "Saha"),
            // A ucu zorunlu, B ucu değil: envanterin kendisinde de 943 kablonun bir kısmında B ucu boş.
            adresA = KonumGirdisi.yol(istek.ucA) ?: throw GecersizIstekException("Uç A konumu boş olamaz."),
            adresB = KonumGirdisi.yol(istek.ucB),
            sehirA = KonumGirdisi.sehir(istek.ucA, istek.sehirA),
            sehirB = KonumGirdisi.sehir(istek.ucB, istek.sehirB),
        )

        val baglanti = DataSourceUtils.getConnection(veriKaynagi)
        val yazilan = KableElleYazici(baglanti).yaz(satir, yeniKayit)

        return sorgular.bul(yazilan) ?: throw BulunamadiException("Kablo yazıldı ama okunamadı: $yazilan")
    }

    private fun zorunlu(deger: String?, alanAdi: String): String =
        KonumYolu.sadelestir(deger) ?: throw GecersizIstekException("$alanAdi boş olamaz.")
}
