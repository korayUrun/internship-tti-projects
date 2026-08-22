package tr.com.tti.hakedis.server.sozlesme

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import tr.com.tti.hakedis.domain.pricing.MoneyFormatter
import java.math.BigDecimal

data class SozlesmeYaniti(val kod: String, val ad: String, val baslik: String)

/**
 * Poz satırı. Alan adları `:core:domain`'deki `PozItem` ile bilinçli olarak aynı tutuldu; mobil ve web
 * aynı sözleşmeyi aynı isimlerle görür.
 *
 * [unitPrice] JSON'a **string** olarak yazılır (bkz. `ParaJsonYapilandirmasi`): ham hassasiyet 17 haneye
 * kadar çıkıyor ve JSON sayısına çevrilirse istemcide `double`'a düşüp kuruş kaydırır.
 *
 * [unitPrice] **2 haneye yuvarlanmış** gelir (Faz P): belgeye çivilenen fiyat da yuvarlanmıştır,
 * dolayısıyla ekranda görünen fiyat hesapta kullanılan fiyattır ve `fiyat × miktar = tutar` tam
 * tutar. Sözleşmenin ham fiyatı `poz_fiyat`'ta dokunulmadan durur.
 *
 * Ad melez: gövdesi türediği `PozItem` alanıyla aynı kalsın, soneki sunucunun ürettiği gösterim
 * metinlerinin (`toplamGosterim`, `tutarGosterim`) kuralına uysun diye. Alan **sunucuda** üretilir:
 * para formatlaması `MoneyFormatter`'da tek yerde kalır, web ve mobil kendi biçimini yazmaz (§5).
 */
data class PozYaniti(
    val id: String,
    val pozNo: String,
    val sortKey: List<Int>,
    val name: String,
    val unit: String,
    val unitPrice: BigDecimal,
    val unitPriceGosterim: String,
    // Jackson, `isXxx` getter'ını `xxx` özelliği sayıp öneki kırpar; alan adları `PozItem` ile birebir
    // aynı kalmalı ki mobil ve web sözleşmeyi aynı isimlerle görsün.
    @get:JsonProperty("isGroup") val isGroup: Boolean,
    @get:JsonProperty("isPriced") val isPriced: Boolean,
)

@Repository
class SozlesmeSorgulari(private val jdbc: JdbcClient) {
    fun sozlesmeler(): List<SozlesmeYaniti> = jdbc.sql("select kod, ad, baslik from sozlesme where aktif order by ad")
        .query { sonuc, _ ->
            SozlesmeYaniti(sonuc.getString("kod"), sonuc.getString("ad"), sonuc.getString("baslik"))
        }.list()

    fun varMi(kod: String): Boolean = sozlesmeId(kod) != null

    /** Fiyat şablonunun başlığı için sözleşme bilgisi. */
    fun bilgi(sozlesmeId: Long): SozlesmeYaniti? = jdbc.sql("select kod, ad, baslik from sozlesme where id = :id")
        .param("id", sozlesmeId)
        .query { sonuc, _ -> SozlesmeYaniti(sonuc.getString("kod"), sonuc.getString("ad"), sonuc.getString("baslik")) }
        .optional()
        .orElse(null)

    /**
     * Sözleşmenin kimliği — **aktiflik süzgeci yok**.
     *
     * `aktif` yalnızca "bu sözleşmeyle yeni iş açılabilir mi" sorusunun cevabıdır ([aktifSozlesmeId]).
     * Varlık kontrolüne uygulanamaz: pasife çekilen bir sözleşmenin **açık belgeleri** poz listesini
     * çekemez hâle gelir, ekran "Belge yüklenemedi" der ve belge fiilen kullanılamaz olur.
     */
    fun sozlesmeId(kod: String): Long? = jdbc.sql("select id from sozlesme where kod = :kod")
        .param("kod", kod)
        .query(Long::class.java)
        .optional()
        .orElse(null)

    /**
     * Bu sözleşmeyle kaç belge açılmış — **silmenin tek kapısı** (bkz. [SozlesmeYazici.sil]).
     *
     * Bağ `belge.fiyat_versiyonu_id` üzerinden kuruluyor: `belge`de sözleşme sütunu yok, belgenin
     * sözleşmesi çivilendiği fiyat sürümünden türer (V10). Durum süzgeci **yok** — iptal edilmiş bir
     * belge de o sözleşmeye çivili bir kayıttır ve tutarı `belge_satir`'da durur.
     */
    fun belgeSayisi(sozlesmeId: Long): Int = jdbc.sql(
        """
        select count(*)
          from belge b
          join fiyat_versiyonu fv on fv.id = b.fiyat_versiyonu_id
         where fv.sozlesme_id = :sozlesmeId
        """.trimIndent(),
    ).param("sozlesmeId", sozlesmeId)
        .query(Int::class.java)
        .single()

    /**
     * Yeni kalemin gireceği `sira`: seçilen grubun **son satırından sonrası** (Faz V).
     *
     * Bir sonraki grup başlığının sırası aranıyor; yoksa kalem listenin sonuna gider. [grupSatirKodu]
     * `null` ise doğrudan sona eklenir — grup seçmek zorunlu değil, ama seçilirse kalem başlığının
     * altında kalmalı; aksi hâlde Excel çıktısında ait olduğu bölümden kopardı.
     *
     * Boş sözleşmede `1` döner.
     */
    fun yeniPozSirasi(sozlesmeId: Long, grupSatirKodu: String?): Int = jdbc.sql(
        """
        select coalesce(
                 case when cast(:grup as text) is null then null else (
                   select min(sonraki.sira)
                     from poz grup
                     join poz sonraki
                       on sonraki.sozlesme_id = grup.sozlesme_id
                      and sonraki.grup_mu
                      and sonraki.sira > grup.sira
                    where grup.sozlesme_id = :sid
                      and grup.satir_kodu = cast(:grup as text)
                      and grup.grup_mu
                 ) end,
                 (select max(sira) + 1 from poz where sozlesme_id = :sid),
                 1
               )
        """.trimIndent(),
    ).param("sid", sozlesmeId)
        .param("grup", grupSatirKodu)
        .query(Int::class.java)
        .single()

    /**
     * Elle eklenen kalemler için sıradaki `m{n}` satır kodu.
     *
     * Ayrı bir harf uzayı kullanmak zorunlu: parser `r{satır}` üretiyor ve aynı uzaydan devam
     * etmek, sözleşmenin ileride yeniden okunması hâlinde çakışma riski taşırdı.
     */
    fun sonrakiElleSatirKodu(sozlesmeId: Long): String {
        val sonraki = jdbc.sql(
            """
            select coalesce(max(cast(substring(satir_kodu from 2) as integer)), 0) + 1
              from poz
             where sozlesme_id = :sid and satir_kodu ~ '^m[0-9]+$'
            """.trimIndent(),
        ).param("sid", sozlesmeId)
            .query(Int::class.java)
            .single()
        return "m$sonraki"
    }

    /** Yeni iş açılabilecek sözleşme; pasif sözleşme `null` döner. */
    fun aktifSozlesmeId(kod: String): Long? = jdbc.sql("select id from sozlesme where kod = :kod and aktif")
        .param("kod", kod)
        .query(Long::class.java)
        .optional()
        .orElse(null)

    /**
     * Sözleşmenin yayındaki fiyat versiyonu — **üç belge türü de** buna çivilenir (Faz N).
     * Yayında versiyon yoksa `null`: belge açılamaz, çünkü hangi fiyatın geçerli olduğu belirsizdir.
     *
     * Tekillik `fiyat_versiyonu_yayin_ux` ile garanti; ~~`is_id is null` süzgeci~~ V10'da gerekmez
     * oldu, işe özel sürüm kavramı kalktı.
     */
    fun referansFiyatVersiyonuId(sozlesmeId: Long): Long? = jdbc.sql(
        "select id from fiyat_versiyonu where sozlesme_id = :sid and durum = 'YAYINDA'",
    ).param("sid", sozlesmeId)
        .query(Long::class.java)
        .optional()
        .orElse(null)

    /**
     * Sözleşmenin pozları, **referans** fiyat versiyonunun birim fiyatlarıyla. Fizibilite ekranı bunu
     * kullanır: bölge sorumlusu fiyatı salt okunur görür, yalnızca miktar girer.
     *
     * Sıra kaynak Excel satır sırasıdır; Excel çıktısı bu sırayı birebir korumak zorunda.
     *
     * Sözleşme başına tek `YAYINDA` sürüm olduğu (`fiyat_versiyonu_yayin_ux`) için join satırı
     * çoğaltmaz. Bu, poz sayısının doğrulama betiğinde kilitlenmesinin sebebidir: indeks düşerse
     * liste sessizce yüzlerce satıra çıkar ve fiyatlar karışır.
     */
    fun pozlar(sozlesmeKodu: String): List<PozYaniti> = jdbc.sql(
        """
            select p.satir_kodu, p.poz_no, p.sort_key, p.ad, p.birim, p.grup_mu, p.fiyatli_mi,
                   round(coalesce(pf.birim_fiyat, 0), 2) as birim_fiyat
              from poz p
              join sozlesme s on s.id = p.sozlesme_id
              left join fiyat_versiyonu fv
                     on fv.sozlesme_id = s.id and fv.durum = 'YAYINDA'
              left join poz_fiyat pf on pf.poz_id = p.id and pf.versiyon_id = fv.id
             where s.kod = :kod
             order by p.sira
        """.trimIndent(),
    ).param("kod", sozlesmeKodu)
        .query(::satirdanPoz)
        .list()

    /**
     * Belgenin pozları, **belgenin kendi fiyat sürümünden**. Belge ekranı bunu kullanır.
     *
     * Neden ayrı bir sorgu: [pozlar] sözleşmenin **canlı** yayında sürümünü okuyordu, oysa belge
     * açıldığı sürüme çivilidir (`belge.fiyat_versiyonu_id`) ve satır yazılırken fiyat oradan
     * kopyalanır (`BelgeSorgulari.satirYaz`). Yönetici yeni bir referans sürüm yayınladığı an ikisi
     * ayrışıyordu: bölge sorumlusu ekranda yeni fiyatı görüp belgeye eski fiyatı yazıyor ve **hiçbir
     * hata çıkmıyordu**. Ekranda görünen fiyat, hesapta kullanılan fiyat olmak zorunda (CLAUDE.md §5).
     *
     * Sözleşme de buradan türer: belge kendi sözleşmesini seçtiği için (Faz N) poz listesi işin
     * değil, **belgenin çivilendiği sürümün** sözleşmesinden okunur.
     */
    fun belgeninPozlari(belgeId: Long): List<PozYaniti> = jdbc.sql(
        """
            select p.satir_kodu, p.poz_no, p.sort_key, p.ad, p.birim, p.grup_mu,
                   round(coalesce(pf.birim_fiyat, 0), 2) as birim_fiyat
              from belge b
              join fiyat_versiyonu fv on fv.id = b.fiyat_versiyonu_id
              join poz p on p.sozlesme_id = fv.sozlesme_id
              left join poz_fiyat pf on pf.poz_id = p.id and pf.versiyon_id = b.fiyat_versiyonu_id
             where b.id = :belgeId
             order by p.sira
        """.trimIndent(),
    ).param("belgeId", belgeId)
        .query(::satirdanPoz)
        .list()

    private fun satirdanPoz(sonuc: java.sql.ResultSet, satirNo: Int): PozYaniti {
        val sortKey = (sonuc.getArray("sort_key").array as Array<*>).map { (it as Number).toInt() }
        val birimFiyat = sonuc.getBigDecimal("birim_fiyat")
        val grupMu = sonuc.getBoolean("grup_mu")
        return PozYaniti(
            id = sonuc.getString("satir_kodu"),
            pozNo = sonuc.getString("poz_no"),
            sortKey = sortKey,
            name = sonuc.getString("ad"),
            unit = sonuc.getString("birim"),
            unitPrice = birimFiyat,
            unitPriceGosterim = MoneyFormatter.money(birimFiyat),
            isGroup = grupMu,
            // Fiyatlılık **sürüme** bağlıdır, sözleşmeye değil: `poz.fiyatli_mi` sözleşmenin ilk
            // yüklendiği andaki hâli, oysa bir sonraki sürümde fiyatlanan poza giriş yapılabilmeli.
            isPriced = !grupMu && birimFiyat.signum() > 0,
        )
    }

    // --- Yönetim (yalnızca yönetici) ------------------------------------------------------------

    /** Yönetim listesi: pasif sözleşmeler de görünür, sürüm ve poz sayısı bilgisiyle. */
    fun yonetimListesi(): List<SozlesmeYonetimYaniti> = jdbc.sql(
        """
            select s.kod, s.ad, s.baslik, s.aktif, s.kaynak_dosya_adi, s.olusturma,
                   (select count(*) from poz p where p.sozlesme_id = s.id) as poz_sayisi,
                   yayin.sira as yayin_sira, yayin.gecerlilik_baslangic as yayin_tarihi,
                   onceki.kod as onceki_kod, yeni.kod as yeni_kod
              from sozlesme s
              left join fiyat_versiyonu yayin
                     on yayin.sozlesme_id = s.id and yayin.durum = 'YAYINDA'
              -- Revizyon zinciri: bu sözleşme neyi revize etti (onceki) ve neyle revize edildi (yeni).
              left join sozlesme onceki on onceki.id = s.onceki_id
              left join sozlesme yeni on yeni.onceki_id = s.id
             order by s.aktif desc, s.ad
        """.trimIndent(),
    ).query { sonuc, _ ->
        SozlesmeYonetimYaniti(
            kod = sonuc.getString("kod"),
            ad = sonuc.getString("ad"),
            baslik = sonuc.getString("baslik"),
            aktif = sonuc.getBoolean("aktif"),
            pozSayisi = sonuc.getInt("poz_sayisi"),
            kaynakDosyaAdi = sonuc.getString("kaynak_dosya_adi"),
            olusturma = sonuc.getTimestamp("olusturma")?.toInstant(),
            yayindakiSurum = sonuc.getObject("yayin_sira") as Int?,
            yayindakiSurumTarihi = sonuc.getDate("yayin_tarihi")?.toLocalDate(),
            oncekiKod = sonuc.getString("onceki_kod"),
            yeniKod = sonuc.getString("yeni_kod"),
        )
    }.list()

    /**
     * Fiyat listesi eşleştirmesi için referans poz listesi — dışa açılan DTO değil, içsel kimlikleri
     * (`poz.id`) taşır. Şablon üretimi ve yüklenen dosyanın doğrulanması bunu kullanır.
     */
    fun eslestirmePozlari(sozlesmeId: Long): List<ReferansPoz> = jdbc.sql(
        """
            select p.id, p.satir_kodu, p.poz_no, p.ad, p.birim, p.grup_mu, p.sira,
                   pf.birim_fiyat as referans_fiyat
              from poz p
              left join fiyat_versiyonu fv
                     on fv.sozlesme_id = p.sozlesme_id and fv.durum = 'YAYINDA'
              left join poz_fiyat pf on pf.poz_id = p.id and pf.versiyon_id = fv.id
             where p.sozlesme_id = :sid
             order by p.sira
        """.trimIndent(),
    ).param("sid", sozlesmeId)
        .query { sonuc, _ ->
            ReferansPoz(
                pozId = sonuc.getLong("id"),
                satirKodu = sonuc.getString("satir_kodu"),
                pozNo = sonuc.getString("poz_no"),
                ad = sonuc.getString("ad"),
                birim = sonuc.getString("birim"),
                grupMu = sonuc.getBoolean("grup_mu"),
                referansFiyat = sonuc.getBigDecimal("referans_fiyat"),
            )
        }.list()
}

/** Yönetim listesi satırı. Para alanı yok — sürüm bilgisi ve sayım. */
data class SozlesmeYonetimYaniti(
    val kod: String,
    val ad: String,
    val baslik: String,
    val aktif: Boolean,
    val pozSayisi: Int,
    val kaynakDosyaAdi: String?,
    val olusturma: java.time.Instant?,
    /** Yayındaki referans sürümün numarası; hiç fiyat listesi yoksa `null`. */
    val yayindakiSurum: Int?,
    val yayindakiSurumTarihi: java.time.LocalDate?,
    /** Bu sözleşme bir revizyonsa, revize ettiği (önceki) sözleşmenin kodu. */
    val oncekiKod: String? = null,
    /** Bu sözleşme revize edildiyse, onun yerine geçen (yeni) sözleşmenin kodu. */
    val yeniKod: String? = null,
)

/** Eşleştirmenin referans tarafı: veritabanındaki kalıcı `poz.id` ile birlikte. */
data class ReferansPoz(
    val pozId: Long,
    val satirKodu: String,
    val pozNo: String,
    val ad: String,
    val birim: String,
    val grupMu: Boolean,
    /** Referans birim fiyat; grup satırlarında `null`. 10 kat sapma uyarısının dayanağı. */
    val referansFiyat: BigDecimal?,
)
