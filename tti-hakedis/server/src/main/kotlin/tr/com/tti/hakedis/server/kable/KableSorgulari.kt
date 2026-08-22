package tr.com.tti.hakedis.server.kable

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

data class KableYaniti(
    val nosid: String,
    val bolgeKodu: String,
    val adresA: String,
    val adresB: String?,
    val sehirA: String?,
    val sehirB: String?,
    /** Danışman ve saha adı: elle düzenleme formunun ön dolumu için (Faz V). */
    val danismanAd: String,
    val sahaAdi: String,
)

/**
 * Kable sorguları.
 *
 * ~~Bölge süzgeci~~ Faz O'da kaldırıldı (kullanıcı kararı, 3 Ağustos 2026): **her kullanıcı her
 * kabloyu görebilir ve seçebilir.** İşin bölgesi kableden değil, işi açan kullanıcıdan gelir
 * (`IsServisi.olustur`), dolayısıyla yetki ayrımı orada korunuyor.
 */
@Repository
class KableSorgulari(private val jdbc: JdbcClient) {
    /**
     * Kable araması. **Aramasız çağrı boş liste döner** — envanterin tamamı (900+ satır) listelenmiyor:
     * kullanıcı `mtc` ön dolu kutuya numara yazdıkça sonuç geliyor. Eşiğin altındaki arama da boş
     * döner; iki karakterlik bir sorgu pratikte tüm envanteri getirirdi.
     */
    fun ara(arama: String?, limit: Int): List<KableYaniti> {
        val desen = arama?.trim()?.takeIf { it.length >= EN_AZ_ARAMA } ?: return emptyList()

        return jdbc.sql(
            """
            select k.nosid, b.kod as bolge_kodu, k.adres_a, k.adres_b, k.sehir_a, k.sehir_b,
                   k.danisman_ad, b.xlsx_saha_adi as saha_adi
              from kable k
              join bolge b on b.id = k.bolge_id
             where k.aktif
               -- Parametre açıkça tip dönüşümüyle yazılır: tek başına kullanılan bir bağlama
               -- değişkeninin tipini PostgreSQL çıkaramaz ve sorgu düşer.
               and (k.nosid ilike '%' || cast(:arama as text) || '%'
                    or k.adres_a ilike '%' || cast(:arama as text) || '%'
                    or k.adres_b ilike '%' || cast(:arama as text) || '%')
             order by k.nosid
             limit :limit
            """.trimIndent(),
        ).param("arama", desen)
            .param("limit", limit)
            .query(::satirdanYanit)
            .list()
    }

    fun bul(nosid: String): KableYaniti? = jdbc.sql(
        """
            select k.nosid, b.kod as bolge_kodu, k.adres_a, k.adres_b, k.sehir_a, k.sehir_b,
                   k.danisman_ad, b.xlsx_saha_adi as saha_adi
              from kable k
              join bolge b on b.id = k.bolge_id
             where k.aktif
               and k.nosid = :nosid
        """.trimIndent(),
    ).param("nosid", nosid)
        .query(::satirdanYanit)
        .optional()
        .orElse(null)

    private fun satirdanYanit(sonuc: java.sql.ResultSet, satirNo: Int) = KableYaniti(
        nosid = sonuc.getString("nosid"),
        bolgeKodu = sonuc.getString("bolge_kodu"),
        adresA = sonuc.getString("adres_a"),
        adresB = sonuc.getString("adres_b"),
        sehirA = sonuc.getString("sehir_a"),
        sehirB = sonuc.getString("sehir_b"),
        danismanAd = sonuc.getString("danisman_ad"),
        sahaAdi = sonuc.getString("saha_adi"),
    )

    private companion object {
        /** Altındaki aramalar pratikte tüm envanteri getirirdi; `mtc` ön dolu kutu bunu zaten aşıyor. */
        const val EN_AZ_ARAMA = 3
    }
}
