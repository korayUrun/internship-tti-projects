package tr.com.tti.hakedis.server.kable

import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tr.com.tti.hakedis.server.web.BulunamadiException
import tr.com.tti.hakedis.server.web.GecersizIstekException
import tr.com.tti.hakedis.server.web.KuralIhlaliException
import tr.com.tti.hakedis.server.yetki.OturumKullanicisi
import tr.com.tti.hakedis.tools.kable.KableSurumYazici
import tr.com.tti.hakedis.tools.kable.KableXlsxParser
import javax.sql.DataSource

/** Yüklemenin cevabı: oluşan sürüm + yürürlükteki envantere göre farkı. */
data class KableSurumYuklemeSonucu(val surum: KableSurumYaniti, val fark: KableSurumFarki)

/** Yürürlüğe almanın raporu — izdüşümün gerçekte ne yaptığı. */
data class KableIzdusumSonucu(
    val surum: KableSurumYaniti,
    val eklenen: Int,
    val guncellenen: Int,
    val pasiflesen: Int,
    val aktifKableSayisi: Int,
    val bolgeDagilimi: List<BolgeAdedi>,
)

/**
 * Kable envanteri sürümleri (Faz U) — yöneticinin NOS+'tan aldığı tam envanteri sisteme alması.
 *
 * **Yükleme envanteri değiştirmez.** [yukle] dosyayı okuyup sürüm olarak saklar ve farkı döner;
 * canlı envanter ancak [yururlugeAl] ile değişir. Ayrım bilinçli: içe aktarım yıkıcıdır (dosyada
 * olmayan her kable pasifleşir), dolayısıyla yanlış bir dosya tek tıkla envanteri eritebilirdi ve
 * hiçbir hata çıkmazdı. Geri dönüş de aynı mekanizma — eski sürüm için [yururlugeAl].
 *
 * Parse ve yazma **yeniden yazılmaz**: `:tools:kable-import` içindeki [KableXlsxParser] ve
 * [KableSurumYazici] kullanılır; CLI de aynı koddan geçer (CLAUDE.md §7/1).
 */
@Service
class KableSurumServisi(private val sorgular: KableSurumSorgulari, private val veriKaynagi: DataSource) {
    @Transactional
    fun yukle(icerik: ByteArray, dosyaAdi: String?): KableSurumYuklemeSonucu {
        if (icerik.isEmpty()) throw GecersizIstekException("Envanter dosyası boş.")

        val satirlar = KableXlsxParser.parse(icerik.inputStream())
        // Saha adları yazmadan önce doğrulanır: aksi hâlde eksik eşleşme ancak "Yürürlüğe Al"
        // anında patlar ve yönetici, yükleyip bıraktığı bir sürümün kullanılamaz olduğunu
        // günler sonra öğrenirdi.
        sahaAdlariniDogrula(satirlar.map { it.sahaAdi }.distinct())

        val surumId = yazici().surumEkle(
            dosyaAdi = dosyaAdi?.trim().orEmpty().ifEmpty { "envanter.xlsx" },
            satirlar = satirlar,
            yukleyenId = OturumKullanicisi.zorunlu().id,
        )

        return KableSurumYuklemeSonucu(surum = surumZorunlu(surumId), fark = sorgular.fark(surumId))
    }

    fun fark(surumId: Long): KableSurumFarki {
        surumZorunlu(surumId)
        return sorgular.fark(surumId)
    }

    @Transactional
    fun yururlugeAl(surumId: Long): KableIzdusumSonucu {
        surumZorunlu(surumId)

        val sonuc = yazici().yururlugeAl(surumId)
        val envanter = sorgular.envanter()

        return KableIzdusumSonucu(
            surum = surumZorunlu(surumId),
            eklenen = sonuc.eklenen,
            guncellenen = sonuc.guncellenen,
            pasiflesen = sonuc.pasiflesen,
            aktifKableSayisi = envanter.aktifKableSayisi,
            bolgeDagilimi = envanter.bolgeDagilimi,
        )
    }

    /**
     * Yürürlükte **olmayan** bir sürümü siler — yanlış yüklenmiş dosyayı listeden temizlemenin yolu.
     *
     * Yürürlükteki sürüm silinemez: `kable` tablosu onun izdüşümüdür ve silinseydi envanterin hangi
     * dosyadan geldiği okunamaz hâle gelirdi.
     */
    @Transactional
    fun sil(surumId: Long) {
        val surum = surumZorunlu(surumId)
        if (surum.yururlukte) {
            throw KuralIhlaliException(
                "Yürürlükteki sürüm silinemez. Önce başka bir sürümü yürürlüğe alın.",
            )
        }
        DataSourceUtils.getConnection(veriKaynagi)
            .prepareStatement("delete from kable_surum where id = ?")
            .use { ifade ->
                ifade.setLong(1, surumId)
                ifade.executeUpdate()
            }
    }

    /**
     * Dosyadaki saha adlarının bölge karşılığı var mı.
     *
     * Eşleşme `bolge.xlsx_saha_adi` sütununda durur ve yalnızca migration ile değişir; envanterde
     * yeni bir saha adı belirdiğinde yöneticinin yapabileceği bir şey yok — o yüzden mesaj, hangi
     * adların tanımsız olduğunu ve neye ihtiyaç duyulduğunu açıkça söyler.
     */
    private fun sahaAdlariniDogrula(sahaAdlari: List<String>) {
        val tanimli = DataSourceUtils.getConnection(veriKaynagi)
            .prepareStatement("select xlsx_saha_adi from bolge")
            .use { ifade ->
                ifade.executeQuery().use { sonuc ->
                    buildSet { while (sonuc.next()) add(sonuc.getString(1)) }
                }
            }

        val eksik = sahaAdlari.filterNot { it in tanimli }
        if (eksik.isNotEmpty()) {
            throw GecersizIstekException(
                "Şu saha adlarının bölge karşılığı yok: ${eksik.sorted().joinToString(", ")}. " +
                    "Tanımlı olanlar: ${tanimli.sorted().joinToString(", ")}. " +
                    "Bölge eşleşmesi tanımlanmadan bu dosya yüklenemez.",
            )
        }
    }

    /**
     * Yazıcı, **çalışan transaction'ın** bağlantısı üzerinde kurulur (`DataSourceUtils`).
     * `veriKaynagi.connection` denseydi havuzdan ayrı bir bağlantı gelir, sürüm kaydı ile `kable`
     * izdüşümü farklı transaction'lara düşer ve yarıda kalan bir işlem envanteri hiçbir sürümün
     * izdüşümü olmadığı hâlde bırakabilirdi.
     */
    private fun yazici() = KableSurumYazici(DataSourceUtils.getConnection(veriKaynagi))

    private fun surumZorunlu(surumId: Long): KableSurumYaniti =
        sorgular.bul(surumId) ?: throw BulunamadiException("Kable sürümü bulunamadı: $surumId")
}
