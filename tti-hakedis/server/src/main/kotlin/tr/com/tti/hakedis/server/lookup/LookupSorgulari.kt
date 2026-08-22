package tr.com.tti.hakedis.server.lookup

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import tr.com.tti.hakedis.domain.workorder.Scope

/** [sahaAdi] envanterdeki "Technical Consultant → Saha" karşılığı; elle kablo eklerken seçilir (Faz V). */
data class BolgeYaniti(val kod: String, val ad: String, val dosyaKodu: String, val sahaAdi: String)

/**
 * [kableZorunlu] veritabanından değil **domain enum'undan** türer (`Scope.kableZorunluMu`, Faz W).
 *
 * Yeni bir sütun açılmadı: `EnumSenkronDogrulayici` `kapsam` tablosu ile `Scope` enum'unun aynı kod
 * kümesini taşıdığını sunucu açılışında kilitliyor, dolayısıyla türetme güvenli ve kural tek
 * kaynakta kalıyor. İstemciler bu bayrağı okur; `kod == "YENI_TESIS"` diye kendi listesini tutmaz.
 */
data class KapsamYaniti(val kod: String, val ad: String, val kableZorunlu: Boolean)

@Repository
class LookupSorgulari(private val jdbc: JdbcClient) {
    fun bolgeler(): List<BolgeYaniti> = jdbc.sql("select kod, ad, dosya_kodu, xlsx_saha_adi from bolge order by sira")
        .query(::satirdanBolge)
        .list()

    fun kapsamlar(): List<KapsamYaniti> = jdbc.sql("select kod, ad from kapsam order by sira")
        .query { sonuc, _ ->
            val kod = sonuc.getString("kod")
            KapsamYaniti(kod, sonuc.getString("ad"), Scope.valueOf(kod).kableZorunluMu)
        }.list()

    /** Bölgenin tam bilgisi — SP No üretimi `dosya_kodu`'na ihtiyaç duyuyor. */
    fun bolge(bolgeId: Int): BolgeYaniti? = jdbc.sql("select kod, ad, dosya_kodu, xlsx_saha_adi from bolge where id = :id")
        .param("id", bolgeId)
        .query(::satirdanBolge)
        .optional()
        .orElse(null)

    private fun satirdanBolge(sonuc: java.sql.ResultSet, satirNo: Int) = BolgeYaniti(
        kod = sonuc.getString("kod"),
        ad = sonuc.getString("ad"),
        dosyaKodu = sonuc.getString("dosya_kodu"),
        sahaAdi = sonuc.getString("xlsx_saha_adi"),
    )

    fun bolgeKodu(bolgeId: Int): String? = jdbc.sql("select kod from bolge where id = :id")
        .param("id", bolgeId)
        .query(String::class.java)
        .optional()
        .orElse(null)

    /** İstemciden gelen bölge kodunun karşılığı; tanımsız kod için `null`. */
    fun bolgeId(kod: String): Int? = jdbc.sql("select id from bolge where kod = :kod")
        .param("kod", kod)
        .query(Int::class.java)
        .optional()
        .orElse(null)

    fun kapsamId(kod: String): Int? = jdbc.sql("select id from kapsam where kod = :kod")
        .param("kod", kod)
        .query(Int::class.java)
        .optional()
        .orElse(null)
}
