package tr.com.tti.hakedis.server.isler

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import tr.com.tti.hakedis.domain.document.DocumentFileNamer

/**
 * SP No — `(bölge, yıl)` başına **boşluksuz ve çakışmasız** seri: `2026-BURSA-001`.
 *
 * `BelgeNumaraUretici` ile aynı kalıp ve aynı gerekçelerle: sequence kullanılamaz (her ikili için
 * bağımsız seri gerekiyor ve sequence rollback'te tüketilmiş kalır), numara tek bir atomik
 * `insert ... on conflict do update ... returning` ile üretilir ve **çağıran tarafın
 * `@Transactional` olması şarttır** — iş insert'i düşerse sayaç artışı da geri alınır.
 *
 * Bölge kısmı `bolge.dosya_kodu`'dur (belge numarasıyla aynı kaynak) ve ASCII'ye sanitize edilir:
 * numara dosya adlarına ve arama kutularına giriyor, Türkçe karakter oralarda sorun çıkarıyor.
 */
@Repository
class IsNumaraUretici(private val jdbc: JdbcClient) {
    fun siradakiSpNo(bolgeId: Int, bolgeDosyaKodu: String, yil: Int): String {
        val sira = jdbc.sql(
            """
            insert into is_sayac (bolge_id, yil, son_no)
            values (:bolgeId, :yil, 1)
            on conflict (bolge_id, yil) do update set son_no = is_sayac.son_no + 1
            returning son_no
            """.trimIndent(),
        ).param("bolgeId", bolgeId)
            .param("yil", yil)
            .query(Int::class.java)
            .single()

        return "$yil-${DocumentFileNamer.sanitize(bolgeDosyaKodu)}-${sira.toString().padStart(SIRA_BASAMAK, '0')}"
    }

    private companion object {
        /** 999'u aşan bölge/yıl serisi doğal olarak dört basamağa taşar; kalıp bozulmaz, uzar. */
        const val SIRA_BASAMAK = 3
    }
}
