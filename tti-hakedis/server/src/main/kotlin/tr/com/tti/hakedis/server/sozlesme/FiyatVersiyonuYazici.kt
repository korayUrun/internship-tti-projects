package tr.com.tti.hakedis.server.sozlesme

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

/** `fiyat_versiyonu.durum` — şemadaki `check` kısıtının Kotlin karşılığı. */
enum class FiyatVersiyonuDurumu { TASLAK, YAYINDA, ARSIV }

/**
 * Fiyat sürümlerini yazar — `poz_fiyat`'a yazan **tek** yer.
 *
 * **Yayınlanmış bir sürüm asla değişmez.** Geçmiş belgeler `belge.fiyat_versiyonu_id` ile sürümlere
 * çivili (CLAUDE.md §7/4), dolayısıyla yazma yalnızca sürüm **doğarken** olur; var olan bir sürümün
 * fiyatını güncelleyen bir yol bilinçli olarak yoktur. Sözleşme fiyatı değişecekse sözleşme yeni bir
 * kodla yüklenir ve eskisi pasife çekilir (Faz N).
 */
@Repository
class FiyatVersiyonuYazici(private val jdbc: JdbcClient) {
    /** Yeni sürüm satırı. [sira] gösterim içindir ("Fiyat listesi v3"). */
    @Suppress("LongParameterList")
    fun versiyonOlustur(
        sozlesmeId: Long,
        ad: String,
        aciklama: String?,
        durum: FiyatVersiyonuDurumu,
        sira: Int?,
        olusturanId: Long,
        kaynakDosyaAdi: String?,
        kaynakOzet: String?,
    ): Long = jdbc.sql(
        """
        insert into fiyat_versiyonu
            (sozlesme_id, ad, gecerlilik_baslangic, durum, sira, aciklama, olusturan_id,
             kaynak_dosya_adi, kaynak_ozet)
        values (:sozlesmeId, :ad, :baslangic, :durum, :sira, :aciklama, :olusturan,
                :dosyaAdi, :ozet)
        returning id
        """.trimIndent(),
    ).param("sozlesmeId", sozlesmeId)
        .param("ad", ad)
        .param("baslangic", LocalDate.now())
        .param("durum", durum.name)
        .param("sira", sira)
        .param("aciklama", aciklama)
        .param("olusturan", olusturanId)
        .param("dosyaAdi", kaynakDosyaAdi)
        .param("ozet", kaynakOzet)
        .query(Long::class.java)
        .single()

    /**
     * Fiyatları yazar. Ham hassasiyet korunur: `BigDecimal` doğrudan `numeric` sütuna gider, hiçbir
     * yerde `double`'a çevrilmez (CLAUDE.md §5).
     */
    fun fiyatlariYaz(versiyonId: Long, fiyatlar: Map<Long, BigDecimal>): Int = fiyatlar.entries.sumOf { (pozId, fiyat) ->
        jdbc.sql("insert into poz_fiyat (poz_id, versiyon_id, birim_fiyat) values (:pozId, :versiyonId, :fiyat)")
            .param("pozId", pozId)
            .param("versiyonId", versiyonId)
            .param("fiyat", fiyat)
            .update()
    }
}
