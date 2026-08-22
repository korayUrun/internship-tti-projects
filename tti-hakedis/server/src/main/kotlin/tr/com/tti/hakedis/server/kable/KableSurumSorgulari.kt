package tr.com.tti.hakedis.server.kable

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant

/** Bir bölgede kaç kable olduğu — hem yürürlükteki envanterde hem bir sürüm dosyasında. */
data class BolgeAdedi(val bolgeKodu: String, val adet: Int)

/** Sürüm listesindeki tek satır. */
data class KableSurumYaniti(
    val id: Long,
    val dosyaAdi: String,
    val satirSayisi: Int,
    val yukleyen: String?,
    val yuklenme: Instant,
    val yururlugeAlinma: Instant?,
    val yururlukte: Boolean,
)

/** Kablo Envanteri ekranının tek çağrıda ihtiyaç duyduğu her şey. */
data class KableEnvanteriYaniti(val aktifKableSayisi: Int, val bolgeDagilimi: List<BolgeAdedi>, val surumler: List<KableSurumYaniti>)

/**
 * Bir sürümün **yürürlükteki** envantere göre farkı — yazma öncesi "ne olacak" cevabı.
 *
 * [acikProjeliDusen] kritik alan: pasifleşecek kablolardan kaç tanesinin üzerinde hâlâ yürüyen bir
 * proje var. Yöneticiyi engellemez (kullanıcı kararı, 9 Ağustos 2026), ama körlemesine onaylamasını
 * da engeller.
 */
data class KableSurumFarki(
    val surumId: Long,
    val yururluktekiSurumId: Long?,
    val yeni: Int,
    val degisen: Int,
    val ayni: Int,
    val dusen: Int,
    val dusenOrnekleri: List<String>,
    val acikProjeliDusen: Int,
    val bolgeDagilimi: List<BolgeAdedi>,
)

/**
 * Kable sürümlerinin salt okunur sorguları.
 *
 * Yazma yolu burada değil: sürüm yazma ve `kable` izdüşümü `:tools:kable-import` içindeki
 * `KableSurumYazici`'dadır — CLI ile ortak (CLAUDE.md §7/1).
 */
@Repository
class KableSurumSorgulari(private val jdbc: JdbcClient) {
    fun envanter(): KableEnvanteriYaniti = KableEnvanteriYaniti(
        aktifKableSayisi = jdbc.sql("select count(*) from kable where aktif").query(Int::class.java).single(),
        bolgeDagilimi = jdbc.sql(
            """
            select b.kod, count(*) as adet
              from kable k
              join bolge b on b.id = k.bolge_id
             where k.aktif
             group by b.kod
             order by adet desc, b.kod
            """.trimIndent(),
        ).query { sonuc, _ -> BolgeAdedi(sonuc.getString("kod"), sonuc.getInt("adet")) }.list(),
        surumler = surumler(),
    )

    fun surumler(): List<KableSurumYaniti> = jdbc.sql(
        """
        select s.id, s.dosya_adi, s.satir_sayisi, s.yuklenme, s.yururluge_alinma, s.yururlukte,
               k.ad_soyad as yukleyen
          from kable_surum s
          left join kullanici k on k.id = s.yukleyen_id
         order by s.yuklenme desc, s.id desc
        """.trimIndent(),
    ).query { sonuc, _ ->
        KableSurumYaniti(
            id = sonuc.getLong("id"),
            dosyaAdi = sonuc.getString("dosya_adi"),
            satirSayisi = sonuc.getInt("satir_sayisi"),
            yukleyen = sonuc.getString("yukleyen"),
            yuklenme = sonuc.getTimestamp("yuklenme").toInstant(),
            yururlugeAlinma = sonuc.getTimestamp("yururluge_alinma")?.toInstant(),
            yururlukte = sonuc.getBoolean("yururlukte"),
        )
    }.list()

    fun bul(surumId: Long): KableSurumYaniti? = surumler().firstOrNull { it.id == surumId }

    /**
     * Yürürlükteki envanterin dışa aktarım satırları (Faz V).
     *
     * Kaynak `kable` (izdüşüm), `kable_surum_satiri` değil: elle yapılan düzeltmeler de dahil olmak
     * üzere **şu an geçerli olan** hâl budur. `saha_adi` bölgeden çözülüyor.
     */
    fun disaAktarim(): List<KableCiktiSatiri> = jdbc.sql(
        """
        select k.nosid, k.danisman_ad, b.xlsx_saha_adi as saha_adi, k.adres_a, k.adres_b, k.sehir_a, k.sehir_b
          from kable k
          join bolge b on b.id = k.bolge_id
         where k.aktif
         order by k.nosid
        """.trimIndent(),
    ).query { sonuc, _ ->
        KableCiktiSatiri(
            nosid = sonuc.getString("nosid"),
            danismanAd = sonuc.getString("danisman_ad"),
            sahaAdi = sonuc.getString("saha_adi"),
            adresA = sonuc.getString("adres_a"),
            adresB = sonuc.getString("adres_b"),
            sehirA = sonuc.getString("sehir_a"),
            sehirB = sonuc.getString("sehir_b"),
        )
    }.list()

    fun yururluktekiSurumId(): Long? = jdbc.sql("select id from kable_surum where yururlukte")
        .query(Long::class.java)
        .optional()
        .orElse(null)

    /**
     * Sürümün yürürlükteki envantere göre farkı.
     *
     * Karşılaştırma `kable` tablosunun **aktif** satırlarına karşı yapılır, önceki sürümün satırlarına
     * karşı değil: yöneticinin sorusu "şu an ne var, ne olacak" — izdüşümün kendisi de aynı tabloyu
     * hedefliyor. Hiç yürürlükte sürüm yoksa (yeni kurulum) her satır "yeni" sayılır.
     */
    fun fark(surumId: Long): KableSurumFarki {
        // `is not distinct from` kullanılıyor: adres_b/sehir_* null olabilir ve `=` ile iki null
        // karşılaştırması null döner — o satırlar sessizce "değişmiş" sayılırdı.
        val sayilar = jdbc.sql(
            """
            select count(*) filter (where k.nosid is null) as yeni,
                   count(*) filter (
                       where k.nosid is not null
                         and not (k.adres_a     is not distinct from s.adres_a
                              and k.adres_b     is not distinct from s.adres_b
                              and k.sehir_a     is not distinct from s.sehir_a
                              and k.sehir_b     is not distinct from s.sehir_b
                              and k.danisman_ad is not distinct from s.danisman_ad)
                   ) as degisen,
                   count(*) filter (
                       where k.nosid is not null
                         and k.adres_a     is not distinct from s.adres_a
                         and k.adres_b     is not distinct from s.adres_b
                         and k.sehir_a     is not distinct from s.sehir_a
                         and k.sehir_b     is not distinct from s.sehir_b
                         and k.danisman_ad is not distinct from s.danisman_ad
                   ) as ayni
              from kable_surum_satiri s
              left join kable k on k.aktif and k.nosid = s.nosid
             where s.surum_id = :sid
            """.trimIndent(),
        ).param("sid", surumId)
            .query { sonuc, _ -> Triple(sonuc.getInt("yeni"), sonuc.getInt("degisen"), sonuc.getInt("ayni")) }
            .single()

        val dusenler = jdbc.sql(
            """
            select k.nosid
              from kable k
             where k.aktif
               and not exists (
                     select 1 from kable_surum_satiri s
                      where s.surum_id = :sid and s.nosid = k.nosid
                   )
             order by k.nosid
            """.trimIndent(),
        ).param("sid", surumId).query { sonuc, _ -> sonuc.getString("nosid") }.list()

        // Açık proje: durumu KAPANDI ya da IPTAL olmayan iş kaydı. Kapanmış projeler zaten
        // dokunulmaz; uyarının konusu yürüyen işler.
        val acikProjeli = if (dusenler.isEmpty()) {
            0
        } else {
            jdbc.sql(
                """
                select count(distinct i.kable_nosid)
                  from is_kaydi i
                 where i.durum not in ('KAPANDI', 'IPTAL')
                   and i.kable_nosid in (:nosidler)
                """.trimIndent(),
            ).param("nosidler", dusenler)
                .query(Int::class.java)
                .single()
        }

        return KableSurumFarki(
            surumId = surumId,
            yururluktekiSurumId = yururluktekiSurumId(),
            yeni = sayilar.first,
            degisen = sayilar.second,
            ayni = sayilar.third,
            dusen = dusenler.size,
            dusenOrnekleri = dusenler.take(DUSEN_ORNEK_SAYISI),
            acikProjeliDusen = acikProjeli,
            bolgeDagilimi = surumBolgeDagilimi(surumId),
        )
    }

    /**
     * Sürüm dosyasının bölge dağılımı. Bölge, `bolge.xlsx_saha_adi` eşleşmesinden **sorgu anında**
     * çözülür — sürüm satırı `saha_adi` saklıyor (V13), `bolge_id` değil.
     */
    private fun surumBolgeDagilimi(surumId: Long): List<BolgeAdedi> = jdbc.sql(
        """
        select coalesce(b.kod, '(eşleşmeyen)') as kod, count(*) as adet
          from kable_surum_satiri s
          left join bolge b on b.xlsx_saha_adi = s.saha_adi
         where s.surum_id = :sid
         group by coalesce(b.kod, '(eşleşmeyen)')
         order by adet desc, kod
        """.trimIndent(),
    ).param("sid", surumId)
        .query { sonuc, _ -> BolgeAdedi(sonuc.getString("kod"), sonuc.getInt("adet")) }
        .list()

    private companion object {
        /** Düşen kableler listelenmez, örneklenir: 900 nosid'lik bir uyarı okunmaz. */
        const val DUSEN_ORNEK_SAYISI = 10
    }
}
