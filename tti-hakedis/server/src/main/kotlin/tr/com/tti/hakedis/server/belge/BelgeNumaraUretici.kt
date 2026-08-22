package tr.com.tti.hakedis.server.belge

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import tr.com.tti.hakedis.domain.document.DocumentType

/**
 * Belge sıra numarası — `(bölge, yıl, tür)` başına ayrı, **boşluksuz ve çakışmasız** seri.
 *
 * Sequence kullanılamaz: her üçlü için bağımsız bir seri gerekiyor ve sequence'lar geri alınmaz
 * (rollback numarayı tüketir, seride boşluk kalır).
 *
 * Numara tek bir atomik `insert ... on conflict do update ... returning` ifadesiyle üretilir:
 * - **Çift numara olamaz:** `on conflict` yolu satırı kilitler, aynı üçlüyü isteyen ikinci işlem
 *   birincinin commit'ini bekler ve güncel değeri okur (`son_no + 1`).
 * - **Boşluk olamaz:** ifade çağıranın transaction'ı içinde çalışır; belge insert'i başarısız olursa
 *   sayaç artışı da geri alınır. Bu yüzden çağıran tarafın `@Transactional` olması **şart**.
 */
@Repository
class BelgeNumaraUretici(private val jdbc: JdbcClient) {
    fun siradakiNo(bolgeId: Int, yil: Int, tur: DocumentType): Int = jdbc.sql(
        """
        insert into belge_sayac (bolge_id, yil, tur, son_no)
        values (:bolgeId, :yil, :tur, 1)
        on conflict (bolge_id, yil, tur) do update set son_no = belge_sayac.son_no + 1
        returning son_no
        """.trimIndent(),
    ).param("bolgeId", bolgeId)
        .param("yil", yil)
        .param("tur", tur.name)
        .query(Int::class.java)
        .single()
}
