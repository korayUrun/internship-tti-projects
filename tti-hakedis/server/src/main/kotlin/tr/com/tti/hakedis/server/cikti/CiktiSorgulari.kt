package tr.com.tti.hakedis.server.cikti

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import tr.com.tti.hakedis.domain.document.DocumentType
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.Instant

/** Çıktının başlık bloğu: belge kimliği + işin dondurulmuş tanımı. */
data class CiktiBasligi(
    val belgeId: Long,
    val belgeNo: String,
    val tur: DocumentType,
    val yil: Int,
    val olusturma: Instant,
    val bolgeKodu: String,
    val bolgeDosyaKodu: String,
    val bolgeAdi: String,
    val kapsamKodu: String,
    val kapsamAdi: String,
    val sozlesmeAdi: String,
    /** Kaynak Excel'in 1. satırı — PDF başlığı olarak bu metin kullanılır (CLAUDE.md §4). */
    val sozlesmeBasligi: String,
    val spNo: String,
    val route: String,
    val ekAraligi: String,
    /**
     * Kablo ve uçlar **boş olabilir** (Faz W): "Yeni Tesis" kapsamında kablo seçimi zorunlu değil.
     * ⚠️ Tipler `String?` olmak zorunda — `getString` null döner ve non-null alana yazılırsa NPE.
     * Boş alanın satırı çıktıda **hiç basılmaz** (`PdfTemplate`, `ExcelUretici`).
     */
    val kableNosid: String?,
    val adresA: String?,
    val adresB: String?,
    /**
     * Excel imza bloğundaki "Bölge Sorumlusu" adı — belgenin **bölgesinin** aktif sorumlusu,
     * belgeyi açan kişi değil (imza satırı rolü gösteriyor, kaydı gireni değil).
     *
     * `null` olabilir: sorumlusu atanmamış bölgede hücre boş kalır, çıktı üretimi durmaz.
     * ⚠️ Sorumlu değişirse eski belgelerin çıktısında yeni isim görünür — V12'nin bilinçli olarak
     * kabul ettiği davranışın aynısı (aynı rol, aynı kişi kabulü).
     */
    val bolgeSorumlusu: String? = null,
)

/**
 * Çıktının tek satırı — sözleşmenin **her** poz satırı, grup başlıkları dahil.
 *
 * [miktar] `null` ise bu satıra miktar girilmemiştir. Ayrım korunur: PDF yalnızca dolu satırları
 * listeler, Excel sözleşmenin tüm satırlarını kaynak sırayla yazar ve boş bırakır (CLAUDE.md §11).
 */
data class CiktiSatiri(
    val satirKodu: String,
    val pozNo: String,
    val sortKey: List<Int>,
    val ad: String,
    val birim: String,
    val grupMu: Boolean,
    val fiyatliMi: Boolean,
    val birimFiyat: BigDecimal,
    val miktar: BigDecimal?,
    /**
     * Aynı **işin keşif belgesindeki** miktar — yalnızca hakediş çıktısında kullanılır, diğer
     * türlerde okunmaz. `null` ise o poza keşifte miktar girilmemiş (ya da keşif belgesi yok).
     */
    val kesifMiktar: BigDecimal? = null,
    /**
     * Keşif satırının **kendi dondurulmuş** birim fiyatı. Hakedişin fiyatı kullanılamaz: iki belge
     * farklı fiyat versiyonlarına çivilenmiş olabilir (V6) ve hakediş fiyatıyla hesaplanan bir
     * "1.Keşif Tutar" sütunu keşif belgesinde yazandan farklı çıkardı — yani yalan söylerdi.
     */
    val kesifBirimFiyat: BigDecimal? = null,
)

/**
 * Belge çıktısının veri kaynağı. Bölge süzgeci **SQL'de**; başka bölgenin belgesi `null` döner (404).
 */
@Repository
class CiktiSorgulari(private val jdbc: JdbcClient) {
    fun baslik(bolgeId: Int?, belgeId: Long): CiktiBasligi? = jdbc.sql(
        """
        select bg.id, bg.belge_no, bg.tur, bg.yil, bg.olusturma,
               b.kod as bolge_kodu, b.dosya_kodu, b.ad as bolge_adi,
               kp.kod as kapsam_kodu, kp.ad as kapsam_adi,
               s.ad as sozlesme_adi, s.baslik as sozlesme_baslik,
               i.sp_no, i.route, i.ek_araligi, i.kable_nosid, i.adres_a, i.adres_b,
               bs.ad_soyad as bolge_sorumlusu
          from belge bg
          join bolge b on b.id = bg.bolge_id
          join is_kaydi i on i.id = bg.is_id
          join kapsam kp on kp.id = i.kapsam_id
          join fiyat_versiyonu fv on fv.id = bg.fiyat_versiyonu_id
          join sozlesme s on s.id = fv.sozlesme_id
          -- İmza bloğundaki "Bölge Sorumlusu" adı. Belgeyi AÇAN kişi değil, bölgenin sorumlusu:
          -- belgeyi yönetici de açmış olabilir ve imza satırı rolü gösteriyor. `left join` —
          -- sorumlusu atanmamış bir bölgede çıktı üretimi durmamalı, hücre boş kalır.
          -- `kullanici_bolge_ux` (V1) bölge başına tek AKTİF sorumlu garantiliyor: tek satır döner.
          left join kullanici bs
                 on bs.bolge_id = bg.bolge_id and bs.rol = 'BOLGE_SORUMLUSU' and bs.aktif
         where bg.id = :id
           and (cast(:bolgeId as int) is null or bg.bolge_id = cast(:bolgeId as int))
        """.trimIndent(),
    ).param("bolgeId", bolgeId)
        .param("id", belgeId)
        .query { sonuc, _ ->
            CiktiBasligi(
                belgeId = sonuc.getLong("id"),
                belgeNo = sonuc.getString("belge_no"),
                tur = DocumentType.valueOf(sonuc.getString("tur")),
                yil = sonuc.getInt("yil"),
                olusturma = sonuc.getTimestamp("olusturma").toInstant(),
                bolgeKodu = sonuc.getString("bolge_kodu"),
                bolgeDosyaKodu = sonuc.getString("dosya_kodu"),
                bolgeAdi = sonuc.getString("bolge_adi"),
                kapsamKodu = sonuc.getString("kapsam_kodu"),
                kapsamAdi = sonuc.getString("kapsam_adi"),
                sozlesmeAdi = sonuc.getString("sozlesme_adi"),
                sozlesmeBasligi = sonuc.getString("sozlesme_baslik"),
                spNo = sonuc.getString("sp_no"),
                route = sonuc.getString("route"),
                ekAraligi = sonuc.getString("ek_araligi"),
                kableNosid = sonuc.getString("kable_nosid"),
                adresA = sonuc.getString("adres_a"),
                adresB = sonuc.getString("adres_b"),
                bolgeSorumlusu = sonuc.getString("bolge_sorumlusu"),
            )
        }.optional()
        .orElse(null)

    /**
     * Belgenin sözleşmesindeki **tüm** poz satırları, kaynak sırasıyla; her satırda o belge için
     * geçerli birim fiyat ve (varsa) girilmiş miktar.
     *
     * Birim fiyat üç kaynaktan bu öncelikle alınır:
     * 1. `belge_satir.birim_fiyat` — satır yazılırken **dondurulmuş** kopya. Yönetici versiyondaki
     *    fiyatı elle değiştirse bile çıktı bu değeri gösterir; fiyat dondurmanın görünür sonucu budur.
     * 2. `poz_fiyat` — belgenin çivilendiği versiyonun fiyatı. Miktarı girilmemiş satırlar için
     *    "Br Fiyat" sütunu yine dolu olmalı: `TEKLIF` çıktısında firma o satırı da fiyatlayabilsin.
     * 3. `0` — bu sözleşmede fiyatlandırılmamış satır (`fiyatli_mi = false`).
     *
     * Ayrıca aynı **işin keşif belgesi** self-join ile okunur: hakediş çıktısı "1. Keşif Talep" ve
     * "1.Keşif Tutar" sütunlarını doldurur. `kg.durum <> 'IPTAL'` koşulu **zorunlu** — V5 tam unique
     * kısıtı kaldırıp `belge_is_tur_ux ... where durum <> 'IPTAL'` kısmi indeksine geçti; koşulsuz
     * join iptal edilmiş keşif belgeleri yüzünden satırları çoğaltırdı.
     */
    fun satirlar(belgeId: Long): List<CiktiSatiri> = jdbc.sql(
        """
        select p.satir_kodu, p.poz_no, p.sort_key, p.ad, p.birim, p.grup_mu, p.fiyatli_mi,
               round(coalesce(bs.birim_fiyat, pf.birim_fiyat, 0), 2) as birim_fiyat,
               bs.miktar,
               kbs.miktar as kesif_miktar,
               kbs.birim_fiyat as kesif_birim_fiyat
          from belge bg
          join fiyat_versiyonu fv on fv.id = bg.fiyat_versiyonu_id
          join poz p on p.sozlesme_id = fv.sozlesme_id
          left join poz_fiyat pf on pf.poz_id = p.id and pf.versiyon_id = bg.fiyat_versiyonu_id
          left join belge_satir bs on bs.belge_id = bg.id and bs.poz_id = p.id
          left join belge kg on kg.is_id = bg.is_id and kg.tur = 'KESIF' and kg.durum <> 'IPTAL'
          left join belge_satir kbs on kbs.belge_id = kg.id and kbs.poz_id = p.id
         where bg.id = :id
         order by p.sira
        """.trimIndent(),
    ).param("id", belgeId)
        .query(::satir)
        .list()

    private fun satir(sonuc: ResultSet, satirNo: Int): CiktiSatiri {
        val sortKey = (sonuc.getArray("sort_key").array as Array<*>).map { (it as Number).toInt() }
        return CiktiSatiri(
            satirKodu = sonuc.getString("satir_kodu"),
            pozNo = sonuc.getString("poz_no"),
            sortKey = sortKey,
            ad = sonuc.getString("ad"),
            birim = sonuc.getString("birim"),
            grupMu = sonuc.getBoolean("grup_mu"),
            fiyatliMi = sonuc.getBoolean("fiyatli_mi"),
            birimFiyat = sonuc.getBigDecimal("birim_fiyat"),
            miktar = sonuc.getBigDecimal("miktar"),
            kesifMiktar = sonuc.getBigDecimal("kesif_miktar"),
            kesifBirimFiyat = sonuc.getBigDecimal("kesif_birim_fiyat"),
        )
    }
}
