package tr.com.tti.hakedis.server.belge

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import tr.com.tti.hakedis.domain.document.DocumentMaterial

/**
 * EK3 "TTI Kullanılan Malzemeler" satırları (Faz X, 18 Ağustos 2026).
 *
 * Yalnızca hakediş belgelerinde doldurulur; tür kuralı [BelgeMalzemeServisi]'nde. Üç alanın da
 * boş olabilmesi bilinçlidir (kullanıcı kararı): saha bazen yalnızca seri no, bazen yalnızca ad
 * biliyor ve zorunluluk koymak eldeki eksik bilgiyi hiç kaydettirmemek demekti.
 */
@Repository
class BelgeMalzemeSorgulari(private val jdbc: JdbcClient) {

    /** Belgenin malzemeleri, kullanıcının girdiği sırayla. */
    fun liste(belgeId: Long): List<DocumentMaterial> = jdbc.sql(
        "select ad, miktar, seri_no from belge_malzeme where belge_id = :belgeId order by sira",
    ).param("belgeId", belgeId)
        .query { sonuc, _ ->
            DocumentMaterial(
                ad = sonuc.getString("ad"),
                miktar = sonuc.getBigDecimal("miktar"),
                seriNo = sonuc.getString("seri_no"),
            )
        }.list()

    fun sil(belgeId: Long): Int = jdbc.sql("delete from belge_malzeme where belge_id = :belgeId")
        .param("belgeId", belgeId)
        .update()

    /**
     * Satırları sırasıyla yazar. **Çağırmadan önce [sil] çalışmalıdır** — yazma "tam değiştirme"
     * semantiğinde (miktar satırlarıyla aynı): ekranda silinen bir satırın veritabanında kalması,
     * kullanıcının sildiğini sandığı malzemenin çıktıda görünmesi demek olurdu.
     */
    fun yaz(belgeId: Long, malzemeler: List<DocumentMaterial>) {
        malzemeler.forEachIndexed { indeks, malzeme ->
            jdbc.sql(
                """
                insert into belge_malzeme (belge_id, ad, miktar, seri_no, sira)
                values (:belgeId, :ad, :miktar, :seriNo, :sira)
                """.trimIndent(),
            ).param("belgeId", belgeId)
                .param("ad", malzeme.ad)
                .param("miktar", malzeme.miktar)
                .param("seriNo", malzeme.seriNo)
                .param("sira", indeks + 1)
                .update()
        }
    }
}
