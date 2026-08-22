package tr.com.tti.hakedis.server.isler

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/**
 * "Yeni Tesis" projelerine otomatik `mtc#####` kimliği — tek, **genel** dizi, `mtc90000`'den başlar
 * *(12 Ağustos 2026, kullanıcı kararı)*.
 *
 * `IsNumaraUretici`/`BelgeNumaraUretici` ile aynı kalıp ve gerekçe: sequence kullanılmaz (rollback'te
 * boşluk bırakır), numara tek atomik `insert ... on conflict do update ... returning` ile üretilir ve
 * **çağıranın `@Transactional` olması şarttır** — proje insert'i düşerse sayaç artışı da geri alınır
 * (`IsServisi.olustur` zaten `@Transactional`).
 *
 * Kimlik `is_kaydi.kable_nosid`'e (Kablo ID alanı) yazılır; envantere eklenmez — "sadece proje
 * etiketi" kararı (bkz. `V16__mtc_sayac.sql`).
 */
@Repository
class MtcNumaraUretici(private val jdbc: JdbcClient) {
    fun siradaki(): String {
        val sira = jdbc.sql(
            """
            insert into mtc_sayac (id, son_no)
            values (true, :ilk)
            on conflict (id) do update set son_no = mtc_sayac.son_no + 1
            returning son_no
            """.trimIndent(),
        ).param("ilk", ILK_NO)
            .query(Int::class.java)
            .single()

        return "$ONEK$sira"
    }

    private companion object {
        const val ONEK = "mtc"

        /** İlk atanan numara; envanterdeki gerçek kablo kimlikleriyle çakışmayan yüksek aralık. */
        const val ILK_NO = 90000
    }
}
