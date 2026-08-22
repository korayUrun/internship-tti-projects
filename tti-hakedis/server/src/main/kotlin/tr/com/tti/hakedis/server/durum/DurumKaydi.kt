package tr.com.tti.hakedis.server.durum

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant

/** Bir durum müdahalesinin okunabilir hâli — belge ekranındaki "Geçmiş" katmanı bunu basar. */
data class DurumKaydiYaniti(val onceki: String?, val yeni: String, val gerekce: String, val kullaniciAdSoyad: String, val zaman: Instant)

/**
 * Yöneticinin durum müdahalelerinin izi (Faz V).
 *
 * Yönetici artık projeyi ve belgeyi her durumdan her duruma çekebiliyor; bu yetkinin bedeli iz
 * bırakmasıdır. Gerekçesiz bir müdahale "bu kapanmış proje neden yeniden açıldı" sorusunu cevapsız
 * bırakırdı.
 *
 * **Otomatik geçişler buraya yazılmaz** — fizibilite açılışındaki `null → ACIK` ve hakediş
 * onayındaki `→ KAPANDI` sistemin kendi akışıdır, kimsenin müdahalesi değil. Kayıt "kim, neden"
 * sorusunun cevabıdır.
 *
 * Tek tablo iki hedefe hizmet ediyor (`is_id` XOR `belge_id`): iki ayrı tablo aynı beş sütunu ve
 * aynı yazma kodunu ikizlerdi.
 */
@Repository
class DurumKaydiSorgulari(private val jdbc: JdbcClient) {
    fun isYaz(isId: Long, onceki: String?, yeni: String, gerekce: String, kullaniciId: Long): Int =
        yaz("is_id", isId, onceki, yeni, gerekce, kullaniciId)

    fun belgeYaz(belgeId: Long, onceki: String, yeni: String, gerekce: String, kullaniciId: Long): Int =
        yaz("belge_id", belgeId, onceki, yeni, gerekce, kullaniciId)

    fun isKayitlari(isId: Long): List<DurumKaydiYaniti> = kayitlar("is_id", isId)

    fun belgeKayitlari(belgeId: Long): List<DurumKaydiYaniti> = kayitlar("belge_id", belgeId)

    /**
     * Sütun adı doğrudan SQL'e gömülüyor; parametreleştirilemez ve gömülmesi güvenli çünkü **kod
     * sabiti**, kullanıcı girdisi değil. İki hedefin tek fonksiyonda birleşmesinin bedeli budur.
     */
    private fun yaz(sutun: String, hedefId: Long, onceki: String?, yeni: String, gerekce: String, kullaniciId: Long): Int = jdbc.sql(
        """
            insert into durum_kaydi ($sutun, onceki, yeni, gerekce, kullanici_id)
            values (:hedef, :onceki, :yeni, :gerekce, :kullanici)
        """.trimIndent(),
    ).param("hedef", hedefId)
        .param("onceki", onceki)
        .param("yeni", yeni)
        .param("gerekce", gerekce)
        .param("kullanici", kullaniciId)
        .update()

    private fun kayitlar(sutun: String, hedefId: Long): List<DurumKaydiYaniti> = jdbc.sql(
        """
        select d.onceki, d.yeni, d.gerekce, k.ad_soyad, d.zaman
          from durum_kaydi d
          join kullanici k on k.id = d.kullanici_id
         where d.$sutun = :hedef
         order by d.zaman desc, d.id desc
        """.trimIndent(),
    ).param("hedef", hedefId)
        .query { sonuc, _ ->
            DurumKaydiYaniti(
                onceki = sonuc.getString("onceki"),
                yeni = sonuc.getString("yeni"),
                gerekce = sonuc.getString("gerekce"),
                kullaniciAdSoyad = sonuc.getString("ad_soyad"),
                zaman = sonuc.getTimestamp("zaman").toInstant(),
            )
        }.list()
}
