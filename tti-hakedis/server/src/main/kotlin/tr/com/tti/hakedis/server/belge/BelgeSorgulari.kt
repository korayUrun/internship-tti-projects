package tr.com.tti.hakedis.server.belge

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import tr.com.tti.hakedis.domain.document.DocumentStatus
import tr.com.tti.hakedis.domain.document.DocumentType
import tr.com.tti.hakedis.domain.pricing.PricedRow
import java.math.BigDecimal
import java.time.Instant

/** Belgenin kimliği ve durumu — satırlar ve tutarlar ayrı sorgulanır. */
/** İşin iptal edilmemiş bir aşaması — iade hangi aşamayı geri alacağını buradan bulur. */
data class AcikAsama(val belgeId: Long, val tur: DocumentType, val durum: DocumentStatus)

data class BelgeOzeti(
    val id: Long,
    val isId: Long,
    val belgeNo: String,
    val tur: DocumentType,
    val durum: DocumentStatus,
    val bolgeKodu: String,
    val yil: Int,
    val siraNo: Int,
    /** Belgenin sözleşmesi — `fiyat_versiyonu` üzerinden türer, `belge`de ayrı sütun yok (Faz N). */
    val sozlesmeId: Long,
    val sozlesmeKodu: String,
    val sozlesmeAdi: String,
    val fiyatVersiyonuId: Long,
    val olusturma: Instant,
    val gonderme: Instant?,
    val tamamlanma: Instant?,
    val iptalGerekce: String?,
    /** Dolu ise bu belge en az bir kez yöneticiden geri gelmiştir; yeniden gönderimde temizlenmez. */
    val iade: Iade?,
    val sonGuncelleme: SonGuncelleme,
)

/** Yöneticinin bir aşamayı geri göndermesinin izi (Faz V). */
data class Iade(val gerekce: String, val edenAdSoyad: String, val tarih: Instant)

/** Kaydedilmiş belge satırı: miktar + **dondurulmuş** birim fiyat. Tutar saklanmaz, hesaplanır. */
data class BelgeSatiri(
    val pozId: String,
    val pozNo: String,
    val ad: String,
    val birim: String,
    val miktar: BigDecimal,
    val birimFiyat: BigDecimal,
)

/**
 * Belgeye en son kim, ne zaman dokundu.
 *
 * `olusturan` bunu karşılamıyor: belgeyi açan ile miktarı giren farklı kişiler olabilir ve iş
 * listesi satır ekranı her aşamanın yanında "değiştirilme tarihi + kim" gösteriyor.
 */
data class SonGuncelleme(val an: Instant, val adSoyad: String, val rol: String)

/** Liste satırı: belgenin kimliği, durumu ve onu ayırt eden iş bilgileri. */
data class BelgeListeSatiri(
    val isId: Long,
    val id: Long,
    val belgeNo: String,
    val tur: DocumentType,
    val durum: DocumentStatus,
    val bolgeKodu: String = "",
    val bolgeAdi: String = "",
    /** Belgenin bölgesinin aktif bölge sorumlusu (madde 16). */
    val sorumluAdSoyad: String = "",
    /** Belgeyi fiilen açan kişi — sorumludan farklı olabilir (ör. yönetici açtıysa). */
    val olusturanAdSoyad: String = "",
    val spNo: String = "",
    /** Aynı kablo üzerinde birden fazla iş olabilir (E7); [ekAraligi] ile birlikte satırı ayırt eder. */
    /** Kablosuz proje olabilir (Faz W); `getString` null döner, tip `String?` olmak zorunda. */
    val kableNosid: String? = null,
    val ekAraligi: String = "",
    val route: String = "",
    val yil: Int = 0,
    val siraNo: Int = 0,
    val olusturma: Instant,
    val gonderme: Instant? = null,
    val tamamlanma: Instant?,
    val sonGuncelleme: SonGuncelleme? = null,
    /**
     * Dolu ise bu aşama en az bir kez yöneticiden geri gelmiştir. Durum metnini de bölüyor: iade
     * edilmiş taslak ile hiç gönderilmemiş taslak aynı şey değil.
     */
    val iadeGerekce: String? = null,
) {
    val iadeEdildi: Boolean get() = iadeGerekce != null
}

/**
 * Belge sorguları. Bölge süzgeci `belge.bolge_id` üzerinden **SQL'de** uygulanır: `belge.bolge_id`
 * iş oluşturulurken kableden kopyalandığı için geçmiş belgelerin yetkisi de sabit kalır.
 */
@Repository
class BelgeSorgulari(private val jdbc: JdbcClient) {
    fun bul(bolgeId: Int?, id: Long): BelgeOzeti? = jdbc.sql(
        """
        select bg.id, bg.is_id, bg.belge_no, bg.tur, bg.durum, b.kod as bolge_kodu, bg.yil, bg.sira_no,
               s.id as sozlesme_id, s.kod as sozlesme_kodu, s.ad as sozlesme_adi,
               bg.fiyat_versiyonu_id, bg.olusturma, bg.gonderme, bg.tamamlanma,
               bg.iptal_gerekce, bg.iade_gerekce, bg.iade_tarihi, ie.ad_soyad as iade_eden_ad,
               bg.guncelleme, g.ad_soyad as guncelleyen_ad, g.rol as guncelleyen_rol
          from belge bg
          join bolge b on b.id = bg.bolge_id
          join fiyat_versiyonu fv on fv.id = bg.fiyat_versiyonu_id
          join sozlesme s on s.id = fv.sozlesme_id
          join kullanici g on g.id = coalesce(bg.guncelleyen_id, bg.olusturan_id)
          left join kullanici ie on ie.id = bg.iade_eden_id
         where bg.id = :id
           and (cast(:bolgeId as int) is null or bg.bolge_id = cast(:bolgeId as int))
        """.trimIndent(),
    ).param("bolgeId", bolgeId)
        .param("id", id)
        .query { sonuc, _ ->
            BelgeOzeti(
                id = sonuc.getLong("id"),
                isId = sonuc.getLong("is_id"),
                belgeNo = sonuc.getString("belge_no"),
                tur = DocumentType.valueOf(sonuc.getString("tur")),
                durum = DocumentStatus.valueOf(sonuc.getString("durum")),
                bolgeKodu = sonuc.getString("bolge_kodu"),
                yil = sonuc.getInt("yil"),
                siraNo = sonuc.getInt("sira_no"),
                sozlesmeId = sonuc.getLong("sozlesme_id"),
                sozlesmeKodu = sonuc.getString("sozlesme_kodu"),
                sozlesmeAdi = sonuc.getString("sozlesme_adi"),
                fiyatVersiyonuId = sonuc.getLong("fiyat_versiyonu_id"),
                olusturma = sonuc.getTimestamp("olusturma").toInstant(),
                gonderme = sonuc.getTimestamp("gonderme")?.toInstant(),
                tamamlanma = sonuc.getTimestamp("tamamlanma")?.toInstant(),
                iptalGerekce = sonuc.getString("iptal_gerekce"),
                iade = sonuc.getString("iade_gerekce")?.let { gerekce ->
                    Iade(
                        gerekce = gerekce,
                        edenAdSoyad = sonuc.getString("iade_eden_ad"),
                        tarih = sonuc.getTimestamp("iade_tarihi").toInstant(),
                    )
                },
                sonGuncelleme = SonGuncelleme(
                    an = sonuc.getTimestamp("guncelleme").toInstant(),
                    adSoyad = sonuc.getString("guncelleyen_ad"),
                    rol = sonuc.getString("guncelleyen_rol"),
                ),
            )
        }.optional()
        .orElse(null)

    /** Satırlar sözleşmenin kaynak sırasında döner; çıktı bu sırayı korumak zorunda (CLAUDE.md §11). */
    fun satirlar(belgeId: Long): List<BelgeSatiri> = jdbc.sql(
        """
        select p.satir_kodu, p.poz_no, p.ad, p.birim, bs.miktar, bs.birim_fiyat
          from belge_satir bs
          join poz p on p.id = bs.poz_id
         where bs.belge_id = :belgeId
         order by p.sira
        """.trimIndent(),
    ).param("belgeId", belgeId)
        .query { sonuc, _ ->
            BelgeSatiri(
                pozId = sonuc.getString("satir_kodu"),
                pozNo = sonuc.getString("poz_no"),
                ad = sonuc.getString("ad"),
                birim = sonuc.getString("birim"),
                miktar = sonuc.getBigDecimal("miktar"),
                birimFiyat = sonuc.getBigDecimal("birim_fiyat"),
            )
        }.list()

    /** Bir işin belgeleri — aşama kuralının (a) kısmı için tür → durum eşlemesi. */
    fun isinBelgeDurumlari(isId: Long): Map<DocumentType, DocumentStatus> = jdbc.sql(
        "select tur, durum from belge where is_id = :isId",
    ).param("isId", isId)
        .query { sonuc, _ ->
            DocumentType.valueOf(sonuc.getString("tur")) to DocumentStatus.valueOf(sonuc.getString("durum"))
        }.list()
        .toMap()

    /**
     * İşin **iptal edilmemiş** aşamaları — tür başına en fazla bir satır.
     *
     * ⚠️ [isinBelgeDurumlari]'ndan farkı sadece süzgeç değil, **doğruluk**: o sorgu iptal edilmiş
     * satırları da çekiyor ve `toMap()` aynı tür için son satırı tutuyor. Bir aşama iade edilip
     * yeniden açıldığında o türde iki satır olur (biri `IPTAL`, biri aktif); hangisinin haritada
     * kalacağı satır sırasına bağlı kalır ve aktif aşama sessizce görünmez olabilir. Buradaki
     * `durum <> 'IPTAL'` süzgeci `belge_is_tur_ux` kısmi indeksiyle birleşince tür başına tek satır
     * garantisi verir.
     */
    fun isinAcikAsamalari(isId: Long): List<AcikAsama> = jdbc.sql(
        "select id, tur, durum from belge where is_id = :isId and durum <> 'IPTAL'",
    ).param("isId", isId)
        .query { sonuc, _ ->
            AcikAsama(
                belgeId = sonuc.getLong("id"),
                tur = DocumentType.valueOf(sonuc.getString("tur")),
                durum = DocumentStatus.valueOf(sonuc.getString("durum")),
            )
        }.list()

    /**
     * Verilen işlerin belgeleri; iş listesi satır ekranında her aşamanın yanında tarih, son
     * güncelleyen ve toplam gösterilir. Bölge süzgeci burada da uygulanır.
     */
    fun isBelgeleri(bolgeId: Int?, isIdler: Collection<Long>): List<BelgeListeSatiri> {
        if (isIdler.isEmpty()) return emptyList()
        return jdbc.sql(
            """
            select bg.is_id, bg.id, bg.belge_no, bg.tur, bg.durum, bg.olusturma, bg.gonderme, bg.tamamlanma,
                   bg.iade_gerekce,
                   bg.guncelleme, g.ad_soyad as guncelleyen_ad, g.rol as guncelleyen_rol
              from belge bg
              join kullanici g on g.id = coalesce(bg.guncelleyen_id, bg.olusturan_id)
             where bg.is_id in (:isIdler)
               and (cast(:bolgeId as int) is null or bg.bolge_id = cast(:bolgeId as int))
             order by bg.is_id, bg.tur
            """.trimIndent(),
        ).param("bolgeId", bolgeId)
            .param("isIdler", isIdler)
            .query { sonuc, _ ->
                BelgeListeSatiri(
                    isId = sonuc.getLong("is_id"),
                    id = sonuc.getLong("id"),
                    belgeNo = sonuc.getString("belge_no"),
                    tur = DocumentType.valueOf(sonuc.getString("tur")),
                    durum = DocumentStatus.valueOf(sonuc.getString("durum")),
                    olusturma = sonuc.getTimestamp("olusturma").toInstant(),
                    gonderme = sonuc.getTimestamp("gonderme")?.toInstant(),
                    tamamlanma = sonuc.getTimestamp("tamamlanma")?.toInstant(),
                    sonGuncelleme = SonGuncelleme(
                        an = sonuc.getTimestamp("guncelleme").toInstant(),
                        adSoyad = sonuc.getString("guncelleyen_ad"),
                        rol = sonuc.getString("guncelleyen_rol"),
                    ),
                    iadeGerekce = sonuc.getString("iade_gerekce"),
                )
            }.list()
    }

    /**
     * Web'in ana listesi: satır satır belge + durum + bölge + sorumlu.
     *
     * Bölge süzgeci **SQL'de**: yönetici (`bolgeId = null`) hepsini, bölge sorumlusu yalnızca kendi
     * bölgesini görür. `kableNosid` ve `ekAraligi` birlikte taşınır — aynı kablo üzerinde birden fazla
     * iş olabildiği için (E7) satırlar bunlarla ayrılır.
     */
    @Suppress("LongParameterList")
    fun listele(
        bolgeId: Int?,
        tur: DocumentType?,
        durum: DocumentStatus?,
        bolgeKodu: String?,
        yil: Int?,
        ara: String?,
        limit: Int,
    ): List<BelgeListeSatiri> = jdbc.sql(
        """
            select bg.id, bg.is_id, bg.belge_no, bg.tur, bg.durum, b.kod as bolge_kodu, b.ad as bolge_adi,
                   coalesce(srm.ad_soyad, '—') as sorumlu, k.ad_soyad as olusturan,
                   i.sp_no, i.kable_nosid, i.ek_araligi, i.route,
                   bg.yil, bg.sira_no, bg.olusturma, bg.gonderme, bg.tamamlanma,
                   bg.iade_gerekce,
                   bg.guncelleme, g.ad_soyad as guncelleyen_ad, g.rol as guncelleyen_rol
              from belge bg
              join bolge b on b.id = bg.bolge_id
              join is_kaydi i on i.id = bg.is_id
              join kullanici k on k.id = bg.olusturan_id
              -- Sorumlu = belgenin bölgesinin **aktif bölge sorumlusu** (kullanıcı kararı, madde 16),
              -- oluşturandan ayrı: yöneticinin açtığı belgede sorumlu yine bölge sorumlusudur.
              left join kullanici srm on srm.bolge_id = bg.bolge_id and srm.rol = 'BOLGE_SORUMLUSU' and srm.aktif
              join kullanici g on g.id = coalesce(bg.guncelleyen_id, bg.olusturan_id)
             where (cast(:bolgeId as int) is null or bg.bolge_id = cast(:bolgeId as int))
               and (cast(:tur as text) is null or bg.tur = cast(:tur as text))
               and (cast(:durum as text) is null or bg.durum = cast(:durum as text))
               and (cast(:bolgeKodu as text) is null or b.kod = cast(:bolgeKodu as text))
               and (cast(:yil as int) is null or bg.yil = cast(:yil as int))
               -- Parametre açıkça tip dönüşümüyle yazılır: tek başına kullanılan bir bağlama
               -- değişkeninin tipini PostgreSQL çıkaramaz ve sorgu hazırlıkta düşer.
               and (cast(:ara as text) is null
                    or bg.belge_no   ilike '%' || cast(:ara as text) || '%'
                    or i.sp_no       ilike '%' || cast(:ara as text) || '%'
                    or i.kable_nosid ilike '%' || cast(:ara as text) || '%')
             order by bg.id desc
             limit :limit
        """.trimIndent(),
    ).param("bolgeId", bolgeId)
        .param("tur", tur?.name)
        .param("durum", durum?.name)
        .param("bolgeKodu", bolgeKodu?.takeIf { it.isNotBlank() })
        .param("yil", yil)
        // Eşik altı arama yok sayılır ve TAM liste döner — `IsSorgulari` ile aynı karar.
        .param("ara", ara?.trim()?.takeIf { it.length >= EN_AZ_ARAMA })
        .param("limit", limit)
        .query { sonuc, _ ->
            BelgeListeSatiri(
                isId = sonuc.getLong("is_id"),
                id = sonuc.getLong("id"),
                belgeNo = sonuc.getString("belge_no"),
                tur = DocumentType.valueOf(sonuc.getString("tur")),
                durum = DocumentStatus.valueOf(sonuc.getString("durum")),
                bolgeKodu = sonuc.getString("bolge_kodu"),
                bolgeAdi = sonuc.getString("bolge_adi"),
                sorumluAdSoyad = sonuc.getString("sorumlu"),
                olusturanAdSoyad = sonuc.getString("olusturan"),
                spNo = sonuc.getString("sp_no"),
                kableNosid = sonuc.getString("kable_nosid"),
                ekAraligi = sonuc.getString("ek_araligi"),
                route = sonuc.getString("route"),
                yil = sonuc.getInt("yil"),
                siraNo = sonuc.getInt("sira_no"),
                olusturma = sonuc.getTimestamp("olusturma").toInstant(),
                gonderme = sonuc.getTimestamp("gonderme")?.toInstant(),
                tamamlanma = sonuc.getTimestamp("tamamlanma")?.toInstant(),
                sonGuncelleme = SonGuncelleme(
                    an = sonuc.getTimestamp("guncelleme").toInstant(),
                    adSoyad = sonuc.getString("guncelleyen_ad"),
                    rol = sonuc.getString("guncelleyen_rol"),
                ),
                iadeGerekce = sonuc.getString("iade_gerekce"),
            )
        }.list()

    /**
     * Verilen belgelerin satırları — liste ucunun toplamlarını hesaplamak için tek toplu sorgu.
     *
     * Toplam **SQL'de `sum()` ile hesaplanmaz**: yuvarlama kuralı (satır bazında `setScale(2, HALF_UP)`,
     * sonra toplama) yalnızca `PricingCalculator`'da yaşar (CLAUDE.md §5). SQL'de toplamak o kuralı
     * ikinci kez, farklı bir dilde yazmak olurdu.
     */
    fun belgelerinSatirlari(belgeIdler: Collection<Long>): Map<Long, List<PricedRow>> {
        if (belgeIdler.isEmpty()) return emptyMap()
        return jdbc.sql("select belge_id, miktar, birim_fiyat from belge_satir where belge_id in (:idler)")
            .param("idler", belgeIdler)
            .query { sonuc, _ ->
                sonuc.getLong("belge_id") to PricedRow(sonuc.getBigDecimal("birim_fiyat"), sonuc.getBigDecimal("miktar"))
            }.list()
            .groupBy({ it.first }, { it.second })
    }

    /** Girilmiş miktarlar, **kalıcı `poz.id`** anahtarıyla — miktar şablonunu doldurmak için. */
    fun pozKimligineGoreMiktarlar(belgeId: Long): Map<Long, BigDecimal> = jdbc.sql(
        "select poz_id, miktar from belge_satir where belge_id = :id",
    ).param("id", belgeId)
        .query { sonuc, _ -> sonuc.getLong("poz_id") to sonuc.getBigDecimal("miktar") }
        .list()
        .toMap()

    fun satirSayisi(belgeId: Long): Int = jdbc.sql("select count(*) from belge_satir where belge_id = :id")
        .param("id", belgeId)
        .query(Int::class.java)
        .single()

    fun olustur(
        isId: Long,
        tur: DocumentType,
        bolgeId: Int,
        yil: Int,
        siraNo: Int,
        belgeNo: String,
        fiyatVersiyonuId: Long,
        olusturanId: Long,
    ): Long = jdbc.sql(
        """
        insert into belge (is_id, tur, bolge_id, yil, sira_no, belge_no, durum, fiyat_versiyonu_id,
                           olusturan_id, guncelleme, guncelleyen_id)
        values (:isId, :tur, :bolgeId, :yil, :siraNo, :belgeNo, 'ONAY_BEKLIYOR', :versiyonId,
                :olusturanId, now(), :olusturanId)
        returning id
        """.trimIndent(),
    ).param("isId", isId)
        .param("tur", tur.name)
        .param("bolgeId", bolgeId)
        .param("yil", yil)
        .param("siraNo", siraNo)
        .param("belgeNo", belgeNo)
        .param("versiyonId", fiyatVersiyonuId)
        .param("olusturanId", olusturanId)
        .query(Long::class.java)
        .single()

    fun satirlariSil(belgeId: Long): Int = jdbc.sql("delete from belge_satir where belge_id = :id")
        .param("id", belgeId)
        .update()

    /**
     * Satırı yazarken birim fiyat, belgenin **çivilendiği** fiyat versiyonundan kopyalanır — fiyat
     * dondurmanın ikinci yarısı. Yönetici sonradan aynı versiyonun fiyatını elle değiştirse bile
     * belgenin tutarı bu kopya sayesinde değişmez.
     *
     * Kopya SQL içinde alınır: birim fiyatı Kotlin'e taşıyıp geri yazmak, 17 haneli değerin gereksizce
     * bir tur daha dönüştürülmesi olurdu.
     *
     * **`round(..., 2)` pazarlıksız** (Faz P, kullanıcı kararı): belgeye çivilenen fiyat 2 hanelidir,
     * böylece `gösterilen fiyat × miktar = tutar` tam tutar. Aynı yuvarlama poz listesi ve çıktı
     * sorgularında da var (`SozlesmeSorgulari.belgeninPozlari`, `CiktiSorgulari.satirlar`); üçü
     * ayrışırsa ekranda girilebilir görünen satır başka fiyatla yazılır ve **hiçbir hata çıkmaz**.
     *
     * Fiyatlılık koşulu `poz.fiyatli_mi` değil **sürümdeki fiyattır**: `fiyatli_mi` sözleşmenin ilk
     * yüklendiği andaki hâlidir, oysa bir sonraki fiyat sürümünde fiyatlanan poza giriş yapılabilmeli
     * (poz listesi ucu `isPriced`'ı da aynı kuralla türetiyor — ikisi ayrışırsa ekranda girilebilir
     * görünen satır sessizce yazılmazdı).
     *
     * @return yazılan satır sayısı (0 → poz bu sözleşmede yok, grup satırı ya da bu sürümde fiyatsız)
     */
    fun satirYaz(belgeId: Long, sozlesmeId: Long, versiyonId: Long, pozId: String, miktar: BigDecimal): Int = jdbc.sql(
        """
        insert into belge_satir (belge_id, poz_id, miktar, birim_fiyat)
        select :belgeId, p.id, :miktar, round(pf.birim_fiyat, 2)
          from poz p
          join poz_fiyat pf on pf.poz_id = p.id and pf.versiyon_id = :versiyonId
         where p.sozlesme_id = :sozlesmeId
           and p.satir_kodu = :pozId
           and not p.grup_mu
           and pf.birim_fiyat > 0
        """.trimIndent(),
    ).param("belgeId", belgeId)
        .param("miktar", miktar)
        .param("versiyonId", versiyonId)
        .param("sozlesmeId", sozlesmeId)
        .param("pozId", pozId)
        .update()

    /**
     * Miktar yazıldıktan sonra "son güncelleyen" izini tazeler. Ayrı bir çağrı olması bilinçli:
     * satır yazımı poz başına bir insert, iz ise belge başına tek update.
     */
    fun guncellemeIzi(belgeId: Long, kullaniciId: Long): Int = jdbc.sql(
        "update belge set guncelleme = now(), guncelleyen_id = :kullanici where id = :id",
    ).param("id", belgeId)
        .param("kullanici", kullaniciId)
        .update()

    /**
     * Belgenin çivilendiği fiyat sürümünü — dolayısıyla sözleşmesini — değiştirir.
     *
     * Yalnızca `TASLAK`'ta; koşul `where` cümlesinde. Satırların silinmesi çağıranın işidir: eski
     * sözleşmenin poz kimlikleri yeni sözleşmede başka kalemlere denk gelir ve miktarlar sessizce
     * yanlış satıra yapışırdı.
     */
    fun sozlesmeDegistir(belgeId: Long, versiyonId: Long, kullaniciId: Long): Int = jdbc.sql(
        """
        update belge
           set fiyat_versiyonu_id = :versiyon, guncelleme = now(), guncelleyen_id = :kullanici
         where id = :id and durum = 'ONAY_BEKLIYOR'
        """.trimIndent(),
    ).param("id", belgeId)
        .param("versiyon", versiyonId)
        .param("kullanici", kullaniciId)
        .update()

    /**
     * "Gönder" — durum değiştirmez, yalnızca **gönderim izini** (`gonderme`) atar (yeknesek model).
     *
     * Aşama zaten `ONAY_BEKLIYOR` doğar ve öyle kalır; gönderme, "kullanıcı hazır, yönetici onaylasın"
     * sinyalidir. İçerik hâlâ düzenlenebilir (müdür kararı: onaya kadar düzeltme serbest). Yönetici
     * ancak `gonderme` dolu bir aşamayı işleme alabilir.
     *
     * Koşul `where` cümlesinde: onay bekleyen olmayan (işleme alınmış/iptal) belge gönderilemez.
     */
    fun gonderIsaretle(belgeId: Long, gonderenId: Long): Int = jdbc.sql(
        """
        update belge
           set gonderme = now(), guncelleme = now(), guncelleyen_id = :gonderen
         where id = :id and durum = 'ONAY_BEKLIYOR'
        """.trimIndent(),
    ).param("id", belgeId)
        .param("gonderen", gonderenId)
        .update()

    /**
     * `ONAY_BEKLIYOR → [hedef]` — yöneticinin "İşleme Al"ı. Hedef **domain'de** kararlaştırılır
     * (`DocumentStageRule.onaySonrasiDurum`): hakediş doğrudan `TAMAMLANDI`, diğerleri `ISLENIYOR`.
     * `tamamlanma` yalnızca `TAMAMLANDI` hedefinde dolar. İçerik bu andan sonra kilitlenir.
     */
    fun isleme(belgeId: Long, onaylayanId: Long, hedef: DocumentStatus): Int = jdbc.sql(
        """
        update belge
           set durum = cast(:hedef as text),
               tamamlanma = case when cast(:hedef as text) = 'TAMAMLANDI' then now() else tamamlanma end,
               guncelleme = now(), guncelleyen_id = :onaylayan
         where id = :id and durum = 'ONAY_BEKLIYOR'
        """.trimIndent(),
    ).param("id", belgeId)
        .param("hedef", hedef.name)
        .param("onaylayan", onaylayanId)
        .update()

    /**
     * Bir önceki aşamayı `ISLENIYOR → TAMAMLANDI` çeker — sonraki aşama açıldığı anda (tek transaction).
     *
     * Koşul `durum = 'ISLENIYOR'`: yalnızca işleme alınmış bir aşama tamamlanır; başka bir durumdaysa
     * (yarışta değişmişse) 0 satır güncellenir ve çağıran taraf bunu 409'a çevirir.
     */
    fun asamaTamamla(isId: Long, tur: DocumentType): Int = jdbc.sql(
        """
        update belge
           set durum = 'TAMAMLANDI', tamamlanma = coalesce(tamamlanma, now()), guncelleme = now()
         where is_id = :isId and tur = cast(:tur as text) and durum = 'ISLENIYOR'
        """.trimIndent(),
    ).param("isId", isId)
        .param("tur", tur.name)
        .update()

    /**
     * [asamaTamamla]'nın tersi: bir önceki aşamayı `TAMAMLANDI → ISLENIYOR` çeker — **iade** için.
     *
     * Ara aşama zaten yalnızca sonraki aşama açıldığı anda `TAMAMLANDI` oluyor; o aşama iade edilince
     * önceki aşamanın "tamamlandı" olması için sebep kalmaz. Geri alınmazsa aşama kapısı kapalı
     * kalır (`acilabilir` bir önceki aşamanın `ISLENIYOR` olmasını arıyor) ve bölge sorumlusu iade
     * edilen aşamayı **yeniden açamaz** — iadenin amacı boşa çıkardı.
     *
     * `tamamlanma` temizleniyor: aşama artık tamamlanmış değil ve tarih orada kalsaydı çıktıda
     * gerçekleşmemiş bir tamamlanma tarihi görünürdü.
     */
    fun asamaGeriAc(isId: Long, tur: DocumentType): Int = jdbc.sql(
        """
        update belge
           set durum = 'ISLENIYOR', tamamlanma = null, guncelleme = now()
         where is_id = :isId and tur = cast(:tur as text) and durum = 'TAMAMLANDI'
        """.trimIndent(),
    ).param("isId", isId)
        .param("tur", tur.name)
        .update()

    /**
     * Yöneticinin serbest durum müdahalesi: her durumdan her duruma (Faz V).
     *
     * Zaman damgaları hedeften türetilir — elle taşınsalardı çağıran her seferinde dört alanı doğru
     * kurmak zorunda kalırdı. `iptal_*` üçlüsü `belge_iptal_izi_tutarli` kısıtı gereği hedef `IPTAL`
     * değilse **temizlenir**; kısıt yerinde olduğu için unutulması derlemede değil çalışma anında
     * patlardı, o yüzden kural buraya, tek yere yazılıyor.
     *
     * `iade_*` izine dokunulmaz: "bu aşama bir kez iade edilmişti" bilgisi kalıcıdır.
     */
    fun durumDegistir(belgeId: Long, mevcut: DocumentStatus, hedef: DocumentStatus, gerekce: String, kullaniciId: Long): Int = jdbc.sql(
        """
            update belge
               set durum = cast(:hedef as text),
                   gonderme = case when cast(:hedef as text) = 'ONAY_BEKLIYOR' then null else coalesce(gonderme, now()) end,
                   tamamlanma = case when cast(:hedef as text) = 'TAMAMLANDI' then coalesce(tamamlanma, now()) end,
                   iptal_gerekce = case when cast(:hedef as text) = 'IPTAL' then cast(:gerekce as text) end,
                   iptal_eden_id = case when cast(:hedef as text) = 'IPTAL' then cast(:kullanici as bigint) end,
                   iptal_tarihi = case when cast(:hedef as text) = 'IPTAL' then now() end,
                   guncelleme = now(), guncelleyen_id = :kullanici
             where id = :id and durum = cast(:mevcut as text)
        """.trimIndent(),
    ).param("id", belgeId)
        .param("mevcut", mevcut.name)
        .param("hedef", hedef.name)
        .param("gerekce", gerekce)
        .param("kullanici", kullaniciId)
        .update()

    /**
     * **Açık** belgeyi iptal eder (taslak ya da onay bekleyen). Onaylanmış belgeye bu uçtan
     * dokunulmaz; yöneticinin serbest geçişi ([durumDegistir]) ayrı bir karardır ve gerekçe ister.
     * Satırlar silinmez: iptal, ne yazıldığının kaydını da korur.
     */
    fun iptalEt(belgeId: Long, gerekce: String, iptalEdenId: Long): Int = jdbc.sql(
        """
        update belge
           set durum = 'IPTAL', iptal_gerekce = :gerekce, iptal_eden_id = :eden, iptal_tarihi = now(),
               guncelleme = now(), guncelleyen_id = :eden
         where id = :id and durum in ('ONAY_BEKLIYOR', 'ISLENIYOR')
        """.trimIndent(),
    ).param("id", belgeId)
        .param("gerekce", gerekce)
        .param("eden", iptalEdenId)
        .update()

    /**
     * İade için iptal — [iptalEt]'ten tek farkı **`TAMAMLANDI` aşamayı da** kapsaması.
     *
     * Ayrı bir metot, çünkü iki işlemin kapsamı bilinçli olarak farklı: "Projeyi İptal Et" yalnızca
     * **açık** bir belgeyi çöpe atar; "Önceki aşamaya iade et" ise yöneticinin onayladığı ama sonra
     * beğenmediği bir hakedişi de geri alabilmeli (§ "Yöneticinin durum yetkisi sınırsızdır").
     * [iptalEt]'in koşulunu gevşetmek, iptal akışını da sessizce genişletirdi.
     *
     * `durum <> 'IPTAL'`: zaten iade edilmiş bir aşama ikinci kez iade edilemez — sayaç 0 döner ve
     * servis anlaşılır bir hata üretir.
     */
    fun iadeEt(belgeId: Long, gerekce: String, edenId: Long): Int = jdbc.sql(
        """
        update belge
           set durum = 'IPTAL', iptal_gerekce = :gerekce, iptal_eden_id = :eden, iptal_tarihi = now(),
               guncelleme = now(), guncelleyen_id = :eden
         where id = :id and durum <> 'IPTAL'
        """.trimIndent(),
    ).param("id", belgeId)
        .param("gerekce", gerekce)
        .param("eden", edenId)
        .update()

    /**
     * İş iptal edilirken onun **açık** (tamamlanmamış) aşamalarını da kapatır: iş kuyruktan çıkıyorsa
     * onay bekleyen ya da işleme alınmış aşamasının açık kalması anlamsız olurdu. Tamamlanmışlara
     * dokunulmaz.
     */
    fun isinTaslaklariniIptalEt(isId: Long, gerekce: String, iptalEdenId: Long): Int = jdbc.sql(
        """
        update belge
           set durum = 'IPTAL', iptal_gerekce = :gerekce, iptal_eden_id = :eden, iptal_tarihi = now(),
               guncelleme = now(), guncelleyen_id = :eden
         where is_id = :isId and durum in ('ONAY_BEKLIYOR', 'ISLENIYOR')
        """.trimIndent(),
    ).param("isId", isId)
        .param("gerekce", gerekce)
        .param("eden", iptalEdenId)
        .update()

    private companion object {
        /** Bu uzunluğun altındaki arama yok sayılır — `KableSorgulari`/`IsSorgulari` ile aynı eşik. */
        const val EN_AZ_ARAMA = 3
    }
}
