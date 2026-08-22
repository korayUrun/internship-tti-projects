package tr.com.tti.hakedis.server.isler

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import tr.com.tti.hakedis.domain.workorder.WorkOrderStatus
import java.sql.ResultSet
import java.time.Instant

/**
 * Projeye **dondurularak** yazılacak kablo bilgisi — insert'in tek parametresi.
 *
 * Dört alan da `null` olabilir: kapsamı "Yeni Tesis" olan projede kablo seçimi zorunlu değil ve
 * uçlar da boş bırakılabilir (Faz W). Kablo seçildiyse dördü de envanterden gelir; seçilmediyse
 * uçlar yöneticinin elle girdiği segmentlerden kurulur.
 *
 * Her iki hâlde de değer **donmuştur**: envanter yenilendiğinde geçmiş bir hakedişin adresi
 * değişmez (CLAUDE.md §7/5).
 */
data class ProjeKablosu(val kableId: Long? = null, val nosid: String? = null, val adresA: String? = null, val adresB: String? = null)

/**
 * Envanterden seçilen kable — arama sonucunun tam hâli.
 *
 * Envanter dışarıdan periyodik olarak yenilendiği için adresler işe kopyalanır: kopyalanmasaydı
 * envanter güncellendiğinde altı ay önce tamamlanmış bir hakedişin adresi kendiliğinden değişirdi.
 */
data class SecilenKable(
    val kableId: Long,
    val bolgeId: Int,
    val bolgeKodu: String,
    val nosid: String,
    val adresA: String,
    val adresB: String?,
)

/**
 * İşin yaşam döngüsü — `WorkOrderStatus`'ün sunucu karşılığı ve `is_kaydi_durum_check` kısıtının
 * Kotlin ikizi. Bu bir lookup tablosu değil, işin kendi durumu; `EnumSenkronDogrulayici` kapsamına
 * girmez. Sütun **nullable**: `null` = fizibilite bekleniyor.
 */
enum class IsDurumu {
    ONAY_BEKLIYOR,
    ISLENIYOR,
    TAMAMLANDI,
    IPTAL,
    ;

    fun domain(): WorkOrderStatus = WorkOrderStatus.valueOf(name)
}

/** Belge üretimi için gereken iş bilgisi — dışa açılan DTO'da yer almayan içsel kimlikleri taşır. */
data class IsOzeti(val id: Long, val bolgeId: Int, val bolgeDosyaKodu: String, val yil: Int, val spNo: String, val durum: IsDurumu?)

/**
 * İş kaydı sorguları.
 *
 * Bölge süzgeci **her sorgunun `where` cümlesinde** ve zorunlu parametredir; `null` yalnızca yönetici
 * içindir ve "tüm bölgeler" demektir. Süzgeci Kotlin'de yapmak yasaktır (CLAUDE.md §7/3).
 */
@Repository
class IsSorgulari(private val jdbc: JdbcClient) {
    private val temelSecim = """
        select i.id, b.kod as bolge_kodu, b.ad as bolge_adi, kp.kod as kapsam_kodu,
               i.kable_nosid, i.adres_a, i.adres_b, i.sp_no, i.route, i.ek_araligi, i.yil, i.olusturma,
               i.durum, i.iptal_gerekce
          from is_kaydi i
          join bolge b on b.id = i.bolge_id
          join kapsam kp on kp.id = i.kapsam_id
    """.trimIndent()

    /**
     * Bölgenin iş listesi. [spNo] ve [kableNosid] süzgeçleri istemci iş oluşturmadan önce "bu SP ile /
     * bu kabloda başka iş var mı" diye sorabilsin diye var (NEWPHASES.md D1/2, E7). Ayrı bir "kontrol"
     * ucu açılmaz.
     *
     * [durum] ve [bolgeKodu] arayüzün süzgeçleri. Belge listesindeki süzgeçlerle aynı gerekçe:
     * istemcide filtrelemek, aynı işi her uçta tekrarlamak ve yetki süzgecini gölgede bırakmak
     * olurdu (§7/3).
     *
     * ⚠️ [bolgeKodu] bir **görüntüleme** süzgecidir, yetki süzgeci değil: [bolgeId] her koşulda
     * uygulanmaya devam eder. Bölge sorumlusu başka bir bölge kodu gönderse bile sonuç boş döner,
     * çünkü iki koşul birlikte çalışır.
     *
     * [ara] arayüzün serbest metin aramasıdır; `KableSorgulari.ara` deseni (`ilike`, çok sütun).
     * Kableden **bilinçli bir sapma** var: orada eşik altı arama boş liste döndürür (900+ satırlık
     * envanter dökülmesin diye), burada `null` bağlanır ve **tam liste** döner — proje listesinin
     * durağan hâli "benim projelerim"dir ve ikinci karakteri yazarken ekranın boşalması arıza gibi
     * okunur.
     */
    fun listele(bolgeId: Int?, spNo: String?, kableNosid: String?, durum: IsDurumu?, bolgeKodu: String?, ara: String?): List<IsSatiri> =
        jdbc.sql(
            """
        $temelSecim
         where (cast(:bolgeId as int) is null or i.bolge_id = cast(:bolgeId as int))
           and (cast(:spNo as text) is null or i.sp_no = cast(:spNo as text))
           and (cast(:kableNosid as text) is null or i.kable_nosid = cast(:kableNosid as text))
           and (cast(:durum as text) is null or i.durum = cast(:durum as text))
           and (cast(:bolgeKodu as text) is null or b.kod = cast(:bolgeKodu as text))
           -- Parametre açıkça tip dönüşümüyle yazılır: tek başına kullanılan bir bağlama
           -- değişkeninin tipini PostgreSQL çıkaramaz ve sorgu hazırlıkta düşer.
           and (cast(:ara as text) is null
                or i.sp_no       ilike '%' || cast(:ara as text) || '%'
                or i.kable_nosid ilike '%' || cast(:ara as text) || '%'
                or i.route       ilike '%' || cast(:ara as text) || '%'
                or i.ek_araligi  ilike '%' || cast(:ara as text) || '%')
         order by i.id desc
            """.trimIndent(),
        ).param("bolgeId", bolgeId)
            .param("spNo", spNo?.takeIf { it.isNotBlank() })
            .param("kableNosid", kableNosid?.takeIf { it.isNotBlank() })
            .param("durum", durum?.name)
            .param("bolgeKodu", bolgeKodu?.takeIf { it.isNotBlank() })
            .param("ara", ara?.trim()?.takeIf { it.length >= EN_AZ_ARAMA })
            .query(::satirdanIs)
            .list()

    fun bul(bolgeId: Int?, id: Long): IsSatiri? = jdbc.sql(
        """
        $temelSecim
         where i.id = :id
           and (cast(:bolgeId as int) is null or i.bolge_id = cast(:bolgeId as int))
        """.trimIndent(),
    ).param("bolgeId", bolgeId)
        .param("id", id)
        .query(::satirdanIs)
        .optional()
        .orElse(null)

    fun ozet(bolgeId: Int?, id: Long): IsOzeti? = jdbc.sql(
        """
        select i.id, i.bolge_id, b.dosya_kodu, i.yil, i.sp_no, i.durum
          from is_kaydi i
          join bolge b on b.id = i.bolge_id
         where i.id = :id
           and (cast(:bolgeId as int) is null or i.bolge_id = cast(:bolgeId as int))
        """.trimIndent(),
    ).param("bolgeId", bolgeId)
        .param("id", id)
        .query { sonuc, _ ->
            IsOzeti(
                id = sonuc.getLong("id"),
                bolgeId = sonuc.getInt("bolge_id"),
                bolgeDosyaKodu = sonuc.getString("dosya_kodu"),
                yil = sonuc.getInt("yil"),
                spNo = sonuc.getString("sp_no"),
                durum = sonuc.getString("durum")?.let(IsDurumu::valueOf),
            )
        }.optional()
        .orElse(null)

    /**
     * Seçilen kable — **bölge süzgeci yok** (kullanıcı kararı, 3 Ağustos 2026): her bölge sorumlusu
     * her kabloyu seçebilir. İşin bölgesi artık kableden değil, işi açan kullanıcıdan gelir.
     */
    fun kableSec(nosid: String): SecilenKable? = jdbc.sql(
        """
        select k.id, k.bolge_id, b.kod as bolge_kodu, k.nosid, k.adres_a, k.adres_b
          from kable k
          join bolge b on b.id = k.bolge_id
         where k.aktif
           and k.nosid = :nosid
        """.trimIndent(),
    ).param("nosid", nosid)
        .query { sonuc, _ ->
            SecilenKable(
                kableId = sonuc.getLong("id"),
                bolgeId = sonuc.getInt("bolge_id"),
                bolgeKodu = sonuc.getString("bolge_kodu"),
                nosid = sonuc.getString("nosid"),
                adresA = sonuc.getString("adres_a"),
                adresB = sonuc.getString("adres_b"),
            )
        }.optional()
        .orElse(null)

    /**
     * Yeni iş. `yil` veritabanında `now()` üzerinden hesaplanır (NEWPHASES.md D4): `olusturma`
     * varsayılanı da `now()` olduğu için ikisi hiçbir koşulda ayrışamaz. Saat dilimi migration'daki
     * geri doldurmayla aynı biçimde açıkça yazılır — sunucunun JVM varsayılanına bırakılmaz.
     */
    @Suppress("LongParameterList")
    fun olustur(
        bolgeId: Int,
        kablo: ProjeKablosu,
        kapsamId: Int,
        spNo: String,
        route: String,
        ekAraligi: String,
        olusturanId: Long,
    ): Long = jdbc.sql(
        """
        insert into is_kaydi (bolge_id, kable_id, kable_nosid, adres_a, adres_b, sp_no, route, ek_araligi,
                              kapsam_id, olusturan_id, yil)
        values (:bolgeId, :kableId, :nosid, :adresA, :adresB, :spNo, :route, :ekAraligi,
                :kapsamId, :olusturanId,
                extract(year from (now() at time zone 'Europe/Istanbul'))::smallint)
        returning id
        """.trimIndent(),
    ).param("bolgeId", bolgeId)
        .param("kableId", kablo.kableId)
        .param("nosid", kablo.nosid)
        .param("adresA", kablo.adresA)
        .param("adresB", kablo.adresB)
        .param("spNo", spNo)
        .param("route", route)
        .param("ekAraligi", ekAraligi)
        .param("kapsamId", kapsamId)
        .param("olusturanId", olusturanId)
        .query(Long::class.java)
        .single()

    /**
     * Aynı bölgede aynı kabloyu kullanan **diğer aktif** işlerin sayısı — `AYNI_KABLE_VAR` uyarısı için.
     *
     * Bazı hatlar çok uzun olduğu için aynı kabloda birden fazla işlem yapılabiliyor (NEWPHASES.md E7);
     * bu bir kısıt değil, bilgilendirmedir. Hattın hangi parçası olduğunu `ek_araligi` ve `route` ayırt
     * eder. İptal edilmiş iş sayılmaz.
     */
    fun ayniKableSayisi(bolgeId: Int, kableId: Long, bunuHaricTut: Long): Int = jdbc.sql(
        """
        select count(*) from is_kaydi
         where bolge_id = :bolgeId and kable_id = :kableId and id <> :haric
           and (durum is null or durum <> 'IPTAL')
        """.trimIndent(),
    ).param("bolgeId", bolgeId)
        .param("kableId", kableId)
        .param("haric", bunuHaricTut)
        .query(Int::class.java)
        .single()

    /**
     * İşi iptal eder (D5). **Zaten iptal olmayan her durumdan** mümkün — `KAPANDI` dahil (Faz V):
     * yönetici her geçişi yapabiliyor ve buradaki dar liste (~~`ACIK`, `ISLENIYOR`~~) domain kuralıyla
     * ayrışıyordu; `WorkOrderTransition` izin veriyor ama SQL 0 satır güncelleyip 409 üretiyordu.
     *
     * Koşul `where` cümlesinde: iki eşzamanlı istek gelse bile ikincisi 0 satır günceller ve çağıran
     * taraf bunu 409'a çevirir. Kayıt silinmez, yalnızca durumu değişir.
     *
     * @return güncellenen satır sayısı (0 → iş yok ya da zaten iptal edilmiş)
     */
    fun iptalEt(id: Long, gerekce: String, iptalEdenId: Long): Int = jdbc.sql(
        """
        update is_kaydi
           set durum = 'IPTAL', iptal_gerekce = :gerekce, iptal_eden_id = :eden, iptal_tarihi = now()
         where id = :id and durum is distinct from 'IPTAL'
        """.trimIndent(),
    ).param("id", id)
        .param("gerekce", gerekce)
        .param("eden", iptalEdenId)
        .update()

    /**
     * Durumu **beklenen mevcut durumdan** hedefe taşır — koşullu yazım (fail loud).
     *
     * `where durum is not distinct from :mevcut` kalıbı zorunlu: `durum` nullable ve `= null`
     * hiçbir satırla eşleşmez. Fizibilite açılırken beklenen mevcut durum `null`'dır; iki eşzamanlı
     * fizibilite açılışından ikincisi 0 satır günceller ve çağıran 409'a çevirir.
     *
     * ⚠️ **İptal izi temizlenir.** Faz V'de yönetici `IPTAL`'den de çıkabiliyor ve
     * `is_kaydi_iptal_izi_tutarli` kısıtı iptal olmayan bir satırda dolu iz kabul etmiyor —
     * temizlik burada, tek yerde. Zaten boş olan satırlarda etkisiz.
     *
     * @return güncellenen satır sayısı (0 → durum beklenenden farklı)
     */
    fun durumYaz(id: Long, mevcut: IsDurumu?, hedef: IsDurumu): Int = jdbc.sql(
        """
        update is_kaydi
           set durum = :hedef, iptal_gerekce = null, iptal_eden_id = null, iptal_tarihi = null
         where id = :id and durum is not distinct from cast(:mevcut as text)
        """.trimIndent(),
    ).param("id", id)
        .param("mevcut", mevcut?.name)
        .param("hedef", hedef.name)
        .update()

    /**
     * Proje durumunu **koşulsuz** günceller — güncel aşamanın durumunun izdüşümü (yeknesek model).
     *
     * `BelgeServisi` her aşama geçişinden sonra bunu çağırır; proje durumu artık türetilmiştir. Hedef
     * hiçbir zaman `IPTAL` **değildir** (iptal izi gerektirir; iptal ayrı `iptalEt` yolundan geçer),
     * bu yüzden iz güvenle temizlenir. Koşulsuz: aşama geçişi zaten koşullu yazımıyla korunuyor.
     */
    fun durumGuncelle(id: Long, hedef: IsDurumu): Int = jdbc.sql(
        """
        update is_kaydi
           set durum = :hedef, iptal_gerekce = null, iptal_eden_id = null, iptal_tarihi = null
         where id = :id
        """.trimIndent(),
    ).param("id", id)
        .param("hedef", hedef.name)
        .update()

    private fun satirdanIs(sonuc: ResultSet, satirNo: Int) = IsSatiri(
        id = sonuc.getLong("id"),
        bolgeKodu = sonuc.getString("bolge_kodu"),
        bolgeAdi = sonuc.getString("bolge_adi"),
        kapsamKodu = sonuc.getString("kapsam_kodu"),
        kableNosid = sonuc.getString("kable_nosid"),
        adresA = sonuc.getString("adres_a"),
        adresB = sonuc.getString("adres_b"),
        spNo = sonuc.getString("sp_no"),
        route = sonuc.getString("route"),
        ekAraligi = sonuc.getString("ek_araligi"),
        yil = sonuc.getInt("yil"),
        olusturma = sonuc.getTimestamp("olusturma").toInstant(),
        durum = sonuc.getString("durum")?.let(IsDurumu::valueOf),
        iptalGerekce = sonuc.getString("iptal_gerekce"),
    )

    private companion object {
        /** Bu uzunluğun altındaki arama yok sayılır — `KableSorgulari` ile aynı eşik. */
        const val EN_AZ_ARAMA = 3
    }
}

/** Veritabanından okunan iş satırı — yanıt DTO'su bunun üzerine belge listesi ve uyarıları ekler. */
data class IsSatiri(
    val id: Long,
    val bolgeKodu: String,
    val bolgeAdi: String,
    val kapsamKodu: String,
    /**
     * Kablo ve uçlar **boş olabilir** (Faz W): "Yeni Tesis" kapsamında kablo seçimi zorunlu değil.
     *
     * ⚠️ Tipler `String?` olmak zorunda: `ResultSet.getString` null döner ve non-null bir alana
     * yazılırsa Kotlin'in intrinsic null denetimi **NPE** atar — proje listesi hiç açılmazdı.
     */
    val kableNosid: String?,
    val adresA: String?,
    val adresB: String?,
    val spNo: String,
    val route: String,
    val ekAraligi: String,
    val yil: Int,
    val olusturma: Instant,
    val durum: IsDurumu?,
    val iptalGerekce: String?,
)
