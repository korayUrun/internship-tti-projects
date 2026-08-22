package tr.com.tti.hakedis.server.ek

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant

/** Kayıtlı ek dosya. [depoYolu] `YUKLEME_KOK`'e görelidir; kök değişince satır güncellenmez. */
data class EkKaydi(
    val id: Long,
    val belgeId: Long,
    val tur: EkTuru,
    val dosyaAdi: String,
    val depoYolu: String,
    val boyutBayt: Long,
    val yukleme: Instant,
    val yukleyenAdSoyad: String?,
)

/**
 * Ek sorguları.
 *
 * Bölge süzgeci **SQL'de**, `belge.bolge_id` üzerinden: `ek` tablosunda bölge alanı yok, bu yüzden
 * her sorgu belgeye join'lenir. Ek kimliğiyle gelen uçlarda (indirme, silme) süzgeci atlamak, başka
 * bölgenin güzergâh dosyasını sızdırmak demektir — ve bu hata sessizdir (CLAUDE.md §7/3).
 */
@Repository
class EkSorgulari(private val jdbc: JdbcClient) {
    fun listele(belgeId: Long): List<EkKaydi> = jdbc.sql(
        """
        select e.id, e.belge_id, e.tur, e.dosya_adi, e.depo_yolu, e.boyut_bayt, e.yukleme, k.ad_soyad
          from ek e
          left join kullanici k on k.id = e.yukleyen_id
         where e.belge_id = :belgeId
         order by e.tur, e.id
        """.trimIndent(),
    ).param("belgeId", belgeId)
        .query(::satir)
        .list()

    /** Tek ek — bölge süzgeci belge üzerinden uygulanır; başka bölgenin eki `null` döner (404). */
    fun bul(bolgeId: Int?, ekId: Long): EkKaydi? = jdbc.sql(
        """
        select e.id, e.belge_id, e.tur, e.dosya_adi, e.depo_yolu, e.boyut_bayt, e.yukleme, k.ad_soyad
          from ek e
          join belge bg on bg.id = e.belge_id
          left join kullanici k on k.id = e.yukleyen_id
         where e.id = :ekId
           and (cast(:bolgeId as int) is null or bg.bolge_id = cast(:bolgeId as int))
        """.trimIndent(),
    ).param("bolgeId", bolgeId)
        .param("ekId", ekId)
        .query(::satir)
        .optional()
        .orElse(null)

    /**
     * Satır **yolsuz** açılır ve yol id belli olunca yazılır: dosya adı `ekId` taşıyor (çakışma
     * önleme, bkz. [EkYolu]), id ise ancak insert'ten sonra bilinir. İkisi aynı transaction'da
     * olduğu için arada yolsuz bir satır dışarıdan hiç görünmez.
     */
    fun olustur(belgeId: Long, tur: EkTuru, dosyaAdi: String, boyutBayt: Long, yukleyenId: Long): Long = jdbc.sql(
        """
        insert into ek (belge_id, tur, dosya_adi, depo_yolu, boyut_bayt, yukleyen_id)
        values (:belgeId, :tur, :dosyaAdi, '', :boyut, :yukleyen)
        returning id
        """.trimIndent(),
    ).param("belgeId", belgeId)
        .param("tur", tur.name)
        .param("dosyaAdi", dosyaAdi)
        .param("boyut", boyutBayt)
        .param("yukleyen", yukleyenId)
        .query(Long::class.java)
        .single()

    fun yoluYaz(ekId: Long, depoYolu: String): Int = jdbc.sql("update ek set depo_yolu = :yol where id = :id")
        .param("yol", depoYolu)
        .param("id", ekId)
        .update()

    fun sil(ekId: Long): Int = jdbc.sql("delete from ek where id = :id").param("id", ekId).update()

    /** Hakediş gönderiminin ön koşulu: EK1 yüklenmiş mi (NEWPHASES.md Faz F). */
    fun varMi(belgeId: Long, tur: EkTuru): Boolean = jdbc.sql(
        "select count(*) from ek where belge_id = :belgeId and tur = :tur",
    ).param("belgeId", belgeId)
        .param("tur", tur.name)
        .query(Int::class.java)
        .single() > 0

    private fun satir(sonuc: ResultSet, satirNo: Int): EkKaydi = EkKaydi(
        id = sonuc.getLong("id"),
        belgeId = sonuc.getLong("belge_id"),
        tur = EkTuru.valueOf(sonuc.getString("tur")),
        dosyaAdi = sonuc.getString("dosya_adi"),
        depoYolu = sonuc.getString("depo_yolu"),
        boyutBayt = sonuc.getLong("boyut_bayt"),
        yukleme = sonuc.getTimestamp("yukleme").toInstant(),
        yukleyenAdSoyad = sonuc.getString("ad_soyad"),
    )
}
