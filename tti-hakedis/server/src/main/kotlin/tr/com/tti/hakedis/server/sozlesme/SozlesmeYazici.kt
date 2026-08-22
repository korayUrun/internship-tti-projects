package tr.com.tti.hakedis.server.sozlesme

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import tr.com.tti.hakedis.domain.contract.PozItem

/**
 * Sözleşme ve poz satırlarını yazar.
 *
 * **Var olan poz satırları değişmez ve silinmez.** `poz.satir_kodu` (`"r5"` — kaynak Excel satır
 * numarası) hem belgelerin hem miktar şablonu eşleştirmesinin kimliğidir; kaydırmak ya da silmek
 * geçmiş bağları koparır. Değişebilen alanlar **ad/başlık/aktiflik**tir; birim fiyat için sözleşme
 * yeni bir kodla yüklenir.
 *
 * Faz V'de açılan tek istisna [pozEkle]: listenin **sonuna** yeni bir kalem eklemek hiçbir mevcut
 * `satir_kodu`'na dokunmaz, dolayısıyla yukarıdaki gerekçe onu kapsamaz.
 *
 * [sil] tek tek poz satırlarını değil **sözleşmenin tamamını** kaldırır ve yalnızca hiç belge
 * açılmamışsa çağrılır; yukarıdaki "geçmiş bağları koparma" gerekçesinin konusu olan bağ orada
 * tanım gereği yoktur.
 */
@Repository
class SozlesmeYazici(private val jdbc: JdbcClient) {
    @Suppress("LongParameterList")
    fun sozlesmeOlustur(
        kod: String,
        ad: String,
        baslik: String,
        sayfaAdi: String?,
        olusturanId: Long,
        kaynakDosyaAdi: String?,
        kaynakOzet: String,
        /** Revizyonsa düzenlenen (önceki) sözleşmenin id'si; sıfırdan yüklemede `null`. */
        oncekiId: Long? = null,
        /** Revizyon **pasif** doğar (yönetici sonra aktife alır); sıfırdan yükleme aktif. */
        aktif: Boolean = true,
    ): Long = jdbc.sql(
        """
        insert into sozlesme (kod, ad, baslik, sayfa_adi, aktif, olusturan_id, kaynak_dosya_adi, kaynak_ozet, onceki_id)
        values (:kod, :ad, :baslik, :sayfaAdi, :aktif, :olusturan, :dosyaAdi, :ozet, :oncekiId)
        returning id
        """.trimIndent(),
    ).param("kod", kod)
        .param("ad", ad)
        .param("baslik", baslik)
        .param("sayfaAdi", sayfaAdi)
        .param("aktif", aktif)
        .param("olusturan", olusturanId)
        .param("dosyaAdi", kaynakDosyaAdi)
        .param("ozet", kaynakOzet)
        .param("oncekiId", oncekiId)
        .query(Long::class.java)
        .single()

    /**
     * Poz satırlarını kaynak sırasıyla yazar ve `satir_kodu → poz.id` eşlemesini döner; fiyat
     * satırları bu kimliklerle yazılacak.
     *
     * `sort_key` `integer[]` sütununa **metin literali + cast** ile bağlanıyor (`'{4,10}'::integer[]`):
     * JDBC dizi bağlaması ham `Connection` erişimi ister, `JdbcClient` bunu vermiyor. Literal biçim
     * V3 seed'iyle de aynı.
     */
    fun pozlariYaz(sozlesmeId: Long, pozlar: List<PozItem>): Map<String, Long> = pozlar.mapIndexed { indeks, poz ->
        val id = jdbc.sql(
            """
            insert into poz (sozlesme_id, satir_kodu, poz_no, sort_key, ad, birim, grup_mu, fiyatli_mi, sira)
            values (:sozlesmeId, :satirKodu, :pozNo, cast(:sortKey as integer[]), :ad, :birim, :grupMu, :fiyatliMi, :sira)
            returning id
            """.trimIndent(),
        ).param("sozlesmeId", sozlesmeId)
            .param("satirKodu", poz.id)
            .param("pozNo", poz.pozNo)
            .param("sortKey", poz.sortKey.joinToString(",", prefix = "{", postfix = "}"))
            .param("ad", poz.name)
            .param("birim", poz.unit)
            .param("grupMu", poz.isGroup)
            .param("fiyatliMi", poz.isPriced)
            .param("sira", indeks + 1)
            .query(Long::class.java)
            .single()

        poz.id to id
    }.toMap()

    /** Sözleşmenin düzenlenebilir alanları. Kod ve poz listesi değişmez. */
    fun bilgiGuncelle(kod: String, ad: String, baslik: String, aktif: Boolean): Int = jdbc.sql(
        "update sozlesme set ad = :ad, baslik = :baslik, aktif = :aktif where kod = :kod",
    ).param("ad", ad)
        .param("baslik", baslik)
        .param("aktif", aktif)
        .param("kod", kod)
        .update()

    /**
     * Sözleşmeyi, poz listesini ve fiyat sürümlerini birlikte siler.
     *
     * ⚠️ Çağırmadan önce [SozlesmeSorgulari.belgeSayisi] **sıfır olmalıdır**; bu metot kontrolü
     * kendisi yapmaz. Kontrolü denetleyici yapıyor ki kullanıcı sayıyı içeren anlaşılır bir hata
     * görsün — buraya kadar gelinmişse karar verilmiştir.
     *
     * Silme sırası pazarlıksız ve `cascade`e bırakılamaz:
     * 1. `onceki_id` bağı **koparılır** — revizyon zincirinde bu sözleşmeyi işaret eden bir sonraki
     *    sürüm varsa, satır silinemez (FK `restrict`) ve kullanıcı sebebini anlamaz. Bağ yalnızca
     *    tarihsel bir işaretçi; hedefi kalkınca `null` olması doğru sonuçtur.
     * 2. `fiyat_versiyonu` **elle** silinir: `sozlesme_id` üzerinde cascade YOK (V2). `poz_fiyat`
     *    ona cascade ile bağlı, o kendiliğinden gider.
     * 3. `sozlesme` silinir; `poz` cascade ile gider.
     *
     * Geriye kalan güvenlik ağı veritabanındadır: `belge.fiyat_versiyonu_id` ve `belge_satir.poz_id`
     * `restrict`tir, yani kontrol atlansa bile para verisi taşıyan bir satır sessizce silinemez —
     * işlem FK hatasıyla düşer.
     */
    fun sil(sozlesmeId: Long): Int {
        jdbc.sql("update sozlesme set onceki_id = null where onceki_id = :id").param("id", sozlesmeId).update()
        jdbc.sql("delete from fiyat_versiyonu where sozlesme_id = :id").param("id", sozlesmeId).update()
        return jdbc.sql("delete from sozlesme where id = :id").param("id", sozlesmeId).update()
    }

    /**
     * Yeni bir iş kalemini **verilen sıraya** ekler ve `poz.id` döner (Faz V).
     *
     * `sira` bir aralık değil ardışık bir numara olduğu için önce yer açılır: hedef sıradan itibaren
     * her şey bir kayar. Bu güvenlidir — `sira` yalnızca sıralamadır, kimlik `satir_kodu`'dur ve ona
     * dokunulmuyor.
     *
     * `satir_kodu` **`m{n}`** kalıbıyla üretilir; parser'ın ürettiği `r{satır}` uzayıyla hiçbir
     * koşulda çakışmaz. Çakışsaydı `unique (sozlesme_id, satir_kodu)` kısıtı yükleme anında değil
     * ekleme anında patlardı ve yönetici sebebini anlamazdı.
     */
    fun pozEkle(sozlesmeId: Long, satirKodu: String, poz: PozItem, sira: Int): Long {
        jdbc.sql("update poz set sira = sira + 1 where sozlesme_id = :sozlesmeId and sira >= :sira")
            .param("sozlesmeId", sozlesmeId)
            .param("sira", sira)
            .update()

        return jdbc.sql(
            """
            insert into poz (sozlesme_id, satir_kodu, poz_no, sort_key, ad, birim, grup_mu, fiyatli_mi, sira)
            values (:sozlesmeId, :satirKodu, :pozNo, cast(:sortKey as integer[]), :ad, :birim, false, true, :sira)
            returning id
            """.trimIndent(),
        ).param("sozlesmeId", sozlesmeId)
            .param("satirKodu", satirKodu)
            .param("pozNo", poz.pozNo)
            .param("sortKey", poz.sortKey.joinToString(",", prefix = "{", postfix = "}"))
            .param("ad", poz.name)
            .param("birim", poz.unit)
            .param("sira", sira)
            .query(Long::class.java)
            .single()
    }
}
