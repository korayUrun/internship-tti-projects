package tr.com.tti.hakedis.server.belge

import com.fasterxml.jackson.annotation.JsonProperty
import tr.com.tti.hakedis.domain.document.DocumentStatus
import tr.com.tti.hakedis.domain.document.DocumentType
import tr.com.tti.hakedis.domain.pricing.MoneyFormatter
import java.math.BigDecimal
import java.time.Instant

/**
 * `isId` alanının **açıkça adlandırılması zorunludur.**
 *
 * Kotlin, adı `is` ile başlayan bir özellik için getter'ı `getIsId()` değil `isId()` üretir; Jackson
 * bunu boolean kalıbı sanıp öneki kırpar ve özelliği `"id"` adıyla yazmaya çalışır. Aynı sınıfta
 * zaten bir `id` olduğu için alan JSON'a **hiç çıkmaz** — hata da vermez. Sonuç: istemci belgenin
 * hangi işe ait olduğunu okuyamaz ve `/api/isler/undefined` çağırır.
 *
 * `PozYaniti.isGroup` / `isPriced` aynı tuzağa düşmüştü (Faz B); orada fark edilen kural burada
 * gözden kaçtı. `BelgeYanitlariTest` artık ikisini de kilitliyor.
 */
private const val IS_ID = "isId"

/**
 * Belgenin tam hâli: satırlar, satır tutarları ve toplam.
 *
 * Para ve miktar alanları JSON'a **string** olarak yazılır (`ParaJsonYapilandirmasi`): 17 haneli ham
 * birim fiyat JSON sayısına çevrilirse istemcide `double`'a düşer ve kuruş kayar.
 *
 * [toplamGosterim], [durumMetni] ve [BelgeSatirYaniti.tutarGosterim] **sunucuda** üretilir. İstemci ne
 * aritmetik ne formatlama ne durum metni yazar — üçü de tek yerde (CLAUDE.md §5, §7/1).
 */
data class BelgeYaniti(
    val id: Long,
    @get:JsonProperty(IS_ID) val isId: Long,
    /** `IST-AVR_2026_KESIF_003` */
    val belgeNo: String,
    val tur: DocumentType,
    val durum: DocumentStatus,
    /** Kullanıcıya gösterilecek durum: "Fizibilite Hazırlanıyor", "1. Keşif Tamamlandı" … */
    val durumMetni: String,
    val bolgeKodu: String,
    val yil: Int,
    val siraNo: Int,
    /** Belgenin **kendi** sözleşmesi; üç aşama farklı sözleşme seçebilir (Faz N). */
    val sozlesmeKodu: String,
    val sozlesmeAdi: String,
    /** Belgenin çivilendiği fiyat versiyonu — fiyat dondurmanın izlenebilir hâli. */
    val fiyatVersiyonuId: Long,
    val olusturma: Instant,
    /** Bölge sorumlusunun gönderdiği an; taslakta `null`. */
    val gonderme: Instant?,
    /** Belgenin kapandığı an. */
    val tamamlanma: Instant?,
    /** Belgeye en son dokunulan an — satır ekranındaki "değiştirilme tarihi". */
    val sonGuncelleme: Instant,
    val sonGuncelleyenAdSoyad: String,
    val sonGuncelleyenRol: String,
    /** `IPTAL` belgede doludur; iptalin nedeni kayıtta kalır (D5). */
    val iptalGerekce: String?,
    /**
     * Miktar ve dosya girilebilir mi — `DocumentStageRule.duzenlenebilirMi` sonucu.
     *
     * İstemci bunu **kendi türetmez**. Türetseydi kural üç yerde (sunucu, web, mobil) yaşardı ve
     * `ONAY_BEKLIYOR` gibi bir değer eklenirken biri unutulduğunda ekran kilitli görünüp sunucu
     * yazmayı kabul ederdi — hiçbir hata çıkmadan (CLAUDE.md §7/1).
     */
    val duzenlenebilir: Boolean,
    /** "Gönder" düğmesi anlamlı mı — onay bekleyen (düzenlenebilir) aşamada gönderilebilir. */
    val gonderilebilir: Boolean,
    val satirlar: List<BelgeSatirYaniti>,
    val toplam: BigDecimal,
    val toplamGosterim: String,
)

data class BelgeSatirYaniti(
    /** Poz satır kodu — `PozItem.id` ile aynı ("r5"). Poz numarası tekrar edebildiği için kimlik bu. */
    val pozId: String,
    val pozNo: String,
    val ad: String,
    val birim: String,
    val miktar: BigDecimal,
    val miktarGosterim: String,
    /**
     * Belgeye dondurulmuş birim fiyat — **2 haneye yuvarlanmış** olarak çivilenir (Faz P).
     * Sözleşme sonradan güncellenirse bu değer değişmez.
     */
    val birimFiyat: BigDecimal,
    /** Gösterim hâli. Hesapta kullanılan değerin aynısı: `fiyat × miktar = tutar` tam tutar. */
    val birimFiyatGosterim: String,
    val tutar: BigDecimal,
    val tutarGosterim: String,
)

/**
 * İş listesi satır ekranında bir aşamanın özeti: açılma tarihi, değiştirilme tarihi, kim güncelledi
 * ve belgenin toplamı. Üçü de aynı satırda, aşama düğmesinin hizasında gösterilir.
 */
data class BelgeOzetYaniti(
    val id: Long,
    val belgeNo: String,
    val tur: DocumentType,
    val durum: DocumentStatus,
    val durumMetni: String,
    val olusturma: Instant,
    /** Bölge sorumlusunun "Gönder" dediği an; dolu ise yönetici işleme alabilir (kart düğmesi). */
    val gonderme: Instant?,
    val tamamlanma: Instant?,
    val sonGuncelleme: Instant?,
    val sonGuncelleyenAdSoyad: String?,
    val sonGuncelleyenRol: String?,
    val toplam: BigDecimal,
    val toplamGosterim: String,
)

/**
 * Web'in ana liste satırı: hangi belge, hangi durumda, hangi bölgede, kimin.
 *
 * [kableNosid] ve [ekAraligi] birlikte gösterilir: aynı kablo üzerinde birden fazla iş olabildiği için
 * (NEWPHASES.md E7) satırlar bunlarla ayırt edilir.
 *
 * [toplam] SQL'de `sum()` ile değil, `PricingCalculator` ile hesaplanır — yuvarlama kuralı domain'de
 * kalır (CLAUDE.md §5).
 */
data class BelgeListeYaniti(
    val id: Long,
    @get:JsonProperty(IS_ID) val isId: Long,
    val belgeNo: String,
    val tur: DocumentType,
    val durum: DocumentStatus,
    val durumMetni: String,
    val bolgeKodu: String,
    val bolgeAdi: String,
    /** Belgenin bölgesinin aktif bölge sorumlusu (madde 16). */
    val sorumluAdSoyad: String,
    /** Belgeyi fiilen açan kişi — sorumludan farklı olabilir. */
    val olusturanAdSoyad: String,
    val spNo: String,
    /** Kablosuz proje olabilir: "Yeni Tesis" kapsamında kablo seçimi zorunlu değil (Faz W). */
    val kableNosid: String?,
    val ekAraligi: String,
    val route: String,
    val yil: Int,
    val siraNo: Int,
    val olusturma: Instant,
    val gonderme: Instant?,
    val tamamlanma: Instant?,
    val sonGuncelleme: Instant?,
    val sonGuncelleyenAdSoyad: String?,
    val sonGuncelleyenRol: String?,
    val toplam: BigDecimal,
    val toplamGosterim: String,
)

fun BelgeListeSatiri.ozet(toplam: BigDecimal) = BelgeOzetYaniti(
    id = id,
    belgeNo = belgeNo,
    tur = tur,
    durum = durum,
    durumMetni = BelgeMetinleri.durumMetni(durum),
    olusturma = olusturma,
    gonderme = gonderme,
    tamamlanma = tamamlanma,
    sonGuncelleme = sonGuncelleme?.an,
    sonGuncelleyenAdSoyad = sonGuncelleme?.adSoyad,
    sonGuncelleyenRol = sonGuncelleme?.rol,
    toplam = toplam,
    toplamGosterim = MoneyFormatter.money(toplam),
)
