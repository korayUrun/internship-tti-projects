package tr.com.tti.hakedis.data.api

import kotlinx.serialization.Serializable
import tr.com.tti.hakedis.domain.document.DocumentStatus
import tr.com.tti.hakedis.domain.document.DocumentType

/**
 * Sunucu API'sinin mobil karşılıkları — `web/src/tipler.ts` ile aynı sözleşmenin Kotlin hâli.
 *
 * **Alan adları sunucudaki gibi Türkçedir** (`kullaniciAdi`, `durumMetni`, `spNo`): bunlar sınıf adı
 * değil, JSON anahtarlarının kendisidir. Her alan için `@SerialName` yazmak, iki şemayı yan yana
 * karşılaştırmayı zorlaştırır ve bir harf hatasını sessiz bir "alan gelmedi"ye çevirirdi.
 *
 * ⚠️ **Para ve miktar alanları `String`'dir**, sayı değil (sunucudaki `ParaJsonYapilandirmasi`).
 * Ham birim fiyat 17 haneye kadar çıkıyor; `Double`'a çevrilse kuruş kayardı. Hesaba girecekleri
 * yerde `BigDecimal(String)` ile okunur — `BigDecimal(double)` yasak (CLAUDE.md §5).
 *
 * Belge türü/durumu `:core:domain` enum'larıyla çözülür — aynı isimler veritabanında, sunucuda ve
 * mobilde geçerli (`EnumSenkronDogrulayici` bunu açılışta zorluyor). Kullanıcıya gösterilen durum
 * metni **sunucudan** gelir (`durumMetni`); mobil durum metni yazmaz (NEWPHASES.md E2).
 */
@Serializable
data class UserDto(
    val kullaniciAdi: String,
    val adSoyad: String,
    val rol: UserRole,
    /** Yöneticide `null` — "tüm bölgeler" demektir, "bölgesiz" değil. */
    val bolgeKodu: String? = null,
    /** Kodun okunabilir hâli; ekranda bu yazılır (web `App.tsx` ile aynı). Yöneticide `null`. */
    val bolgeAdi: String? = null,
)

enum class UserRole {
    BOLGE_SORUMLUSU,
    YONETICI,
}

@Serializable
data class RegionDto(val kod: String, val ad: String)

/**
 * [kableZorunlu] **sunucudan gelir** (`Scope.kableZorunluMu`, Faz W). Mobil `kod == "YENI_TESIS"`
 * diye kendi listesini tutmaz: kural üç yerde yaşasaydı yeni bir kapsam muaf olduğunda biri
 * unutulur ve ekran zorunlu göstermeye devam ederdi (CLAUDE.md §7/1).
 *
 * Varsayılan `true`: alan gelmezse kablo zorunlu sayılır — eksik bilgiyle zorunluluğu gevşetmek,
 * sunucunun reddedeceği bir formu açık göstermek olurdu.
 */
@Serializable
data class ScopeDto(val kod: String, val ad: String, val kableZorunlu: Boolean = true)

@Serializable
data class ContractDto(val kod: String, val ad: String)

@Serializable
data class CableDto(
    val nosid: String,
    val bolgeKodu: String = "",
    val adresA: String,
    val adresB: String? = null,
    val sehirA: String? = null,
    val sehirB: String? = null,
)

/** Sözleşme poz satırı. Alan adları `:core:domain`'deki `PozItem` ile birebir aynı. */
@Serializable
data class PozDto(
    val id: String,
    val pozNo: String,
    val name: String,
    val unit: String,
    /**
     * Birim fiyat — sunucuda **2 haneye yuvarlanmış** (`SozlesmeSorgulari`: `round(birim_fiyat, 2)`),
     * belgeye çivilenen değerin aynısı (§5, 3 Ağustos 2026). `String` gelmesinin nedeni yuvarlanmamış
     * olması değil, yolda `double`'a düşmemesi; hesaba `BigDecimal(unitPrice)` olarak girer.
     */
    val unitPrice: String,
    /** Sunucunun ürettiği gösterim — **2 haneye yuvarlı** (Faz P); belgeye çivilenen fiyatla aynı. */
    val unitPriceGosterim: String,
    val isGroup: Boolean,
    val isPriced: Boolean,
)

/** Bilgilendirme amaçlı uyarı; **hiçbir koşulda işlemi durdurmaz** (NEWPHASES.md D1/4). */
@Serializable
data class WarningDto(val kod: String, val mesaj: String)

/**
 * İş listesi satır ekranında bir aşamanın özeti: aşama düğmesinin hizasında gösterilen tarih, son
 * güncelleyen ve toplam.
 */
@Serializable
data class DocumentSummaryDto(
    val id: Long,
    val belgeNo: String,
    val tur: DocumentType,
    val durum: DocumentStatus,
    val durumMetni: String,
    val olusturma: String,
    val sonGuncelleme: String? = null,
    val sonGuncelleyenAdSoyad: String? = null,
    val sonGuncelleyenRol: UserRole? = null,
    val toplamGosterim: String = "",
)

/** Projenin durumu — `:core:domain`'deki `WorkOrderStatus` ile aynı yeknesek küme. `null` = henüz aşama yok. */
enum class WorkOrderStatusDto {
    ONAY_BEKLIYOR,
    ISLENIYOR,
    TAMAMLANDI,
    IPTAL,
}

@Serializable
data class WorkOrderDto(
    val id: Long,
    val bolgeKodu: String,
    val bolgeAdi: String,
    /** İşin niteliği (`DEPLASE_IYILESTIRME`, `YENI_TESIS`…); kartta kimlik alanlarıyla birlikte yazılır. */
    val kapsamKodu: String = "",
    /** Kablo ve uçlar **boş olabilir**: "Yeni Tesis" kapsamında kablo seçimi zorunlu değil (Faz W). */
    val kableNosid: String? = null,
    val adresA: String? = null,
    val adresB: String? = null,
    val spNo: String,
    val route: String,
    val ekAraligi: String,
    val yil: Int,
    /** `null` = fizibilite bekleniyor (iş açıldı, henüz belgesi yok). */
    val durum: WorkOrderStatusDto? = null,
    /** Kullanıcıya gösterilecek durum; metin sunucudan gelir (CLAUDE.md §7). */
    val durumMetni: String = "",
    val iptalGerekce: String? = null,
    val belgeler: List<DocumentSummaryDto> = emptyList(),
    /** Yalnızca `POST /api/isler` yanıtında dolu olabilir. */
    val uyarilar: List<WarningDto> = emptyList(),
)

@Serializable
data class DocumentRowDto(
    /** Poz satır kodu — `PozItem.id` ile aynı ("r5"). Poz numarası tekrar edebildiği için kimlik bu. */
    val pozId: String,
    val miktar: String,
)

@Serializable
data class DocumentDto(
    val id: Long,
    val isId: Long,
    val belgeNo: String,
    val tur: DocumentType,
    val durum: DocumentStatus,
    val durumMetni: String,
    /** Belgenin **kendi** sözleşmesi; üç aşama farklı sözleşme seçebilir (Faz N). */
    val sozlesmeKodu: String,
    val sozlesmeAdi: String,
    val satirlar: List<DocumentRowDto> = emptyList(),
    /** Toplam **sunucuda** hesaplanır; mobil de aynı `PricingCalculator`'ı kullanır (CLAUDE.md §7/1). */
    val toplamGosterim: String,
    /**
     * Miktar ve dosya girilebilir mi — **sunucu söyler**, mobil türetmez (Faz V).
     *
     * Eskiden burada `durum.duzenlenebilirMi` + işin durumu diye yerel bir kural vardı ve aynı kural
     * sunucuda, webde ve burada üç kez yazılıydı. `ONAY_BEKLIYOR` eklenirken biri unutulsaydı ekran
     * kilitli görünür ama sunucu yazmayı kabul ederdi — hiçbir hata çıkmadan (CLAUDE.md §7/1).
     */
    val duzenlenebilir: Boolean = false,
    /** "Gönder" düğmesi anlamlı mı; onay bekleyen belge yeniden gönderilmez, düzeltilir. */
    val gonderilebilir: Boolean = false,
    /** Yalnızca `durum == IPTAL` iken dolu; yöneticinin gerekçesi aynen gösterilir. */
    val iptalGerekce: String? = null,
)

enum class AttachmentType {
    EK1_ROTA,
    EK2_GORSEL,
}

@Serializable
data class AttachmentDto(val id: Long, val tur: AttachmentType, val dosyaAdi: String, val boyutBayt: Long)

/**
 * EK3 "TTI Kullanılan Malzemeler" satırı — **yalnızca hakedişte** dolar (Faz Y).
 *
 * Dosya değil **yapısal veri** olduğu için [AttachmentType]'a bir değer eklenmedi; kendi ucu var
 * (`/api/belgeler/{id}/malzemeler`) ve kendi tablosunda duruyor (`belge_malzeme`, `V19`).
 *
 * **Üç alan da opsiyonel**: saha bazen yalnızca seri no, bazen yalnızca ad biliyor. [miktar]
 * **metin**: ayrıştırma sunucuda (`QuantityParser`), sayıya çevrilse `double`'a düşerdi
 * (CLAUDE.md §5). Varsayılanlar duruyor — bir alanı hiç göndermeyen sunucuda çözümleme patlamasın.
 */
@Serializable
data class MaterialDto(val ad: String? = null, val miktar: String? = null, val seriNo: String? = null)

/** Hata gövdesi her uçta aynı biçimde (`HataYakalayici`). */
@Serializable
data class ErrorDto(val hata: String = "", val mesaj: String = "")

// --- İstek gövdeleri --------------------------------------------------------

@Serializable
data class LoginRequest(val kullaniciAdi: String, val sifre: String)

/**
 * "Yeni İş" ekranının gönderdiği alanlar. Hepsi zorunlu; **kararı sunucu verir**
 * (`WorkOrderValidator`).
 *
 * ~~`bolgeKodu`~~ ve ~~`spNo`~~ Faz O'da çıktı (bölge oturumdan, SP No sayaçtan gelir),
 * ~~`sozlesmeKodu`~~ Faz N'de (sözleşme artık her belgede ayrı seçiliyor).
 */
@Serializable
data class WorkOrderRequest(
    val kapsamKodu: String,
    /** "Yeni Tesis" kapsamında `null` olabilir; diğerlerinde sunucu 400 döner. */
    val kableNosid: String? = null,
    val route: String,
    val ekAraligi: String,
    /**
     * Yalnızca **kablo seçilmediğinde** okunur: kablo varsa uçlar envanterden gelir ve buradaki
     * segmentler yok sayılır — adres, kablonun projeye dondurulmuş kopyasıdır (CLAUDE.md §7/5).
     *
     * Konum yolunu (`>>`, bina `>` kat) **sunucu** kurar; mobil ayraçları birleştirmez.
     */
    val ucA: EndpointRequest? = null,
    val ucB: EndpointRequest? = null,
)

/** Bir ucun konum segmentleri — anahtarlar sunucudaki `KonumYolu.ALANLAR` ile birebir aynı. */
@Serializable
data class EndpointRequest(val segmentler: Map<String, String>)

/** Belge açılırken sözleşme **zorunlu**: belge daima bir fiyat sürümüne çivilidir (Faz N). */
@Serializable
data class DocumentRequest(val tur: DocumentType, val sozlesmeKodu: String)

/** Belge ekranının üstündeki sözleşme seçimi. */
@Serializable
data class ContractRequest(val sozlesmeKodu: String)

/**
 * Miktar **string** olarak gider: JSON sayısına çevrilse `double`'a düşer ve ondalık kayardı
 * (CLAUDE.md §5). Sunucuda Jackson `BigDecimal`'e çözer — web de aynısını yapıyor.
 */
@Serializable
data class RowEntry(val pozId: String, val miktar: String)

@Serializable
data class RowsRequest(val satirlar: List<RowEntry>)

/**
 * EK3 satırının **istek** hâli. [MaterialDto]'dan ayrı bir tip olması şart:
 *
 * ⚠️ `kotlinx.serialization` varsayılan olarak `encodeDefaults = false` çalışır. Alanlara `= null`
 * verilseydi `MaterialEntry(null, "5", null)` telde `{"miktar":"5"}` olur, `ad` ve `seriNo`
 * anahtarları **sessizce düşerdi**. Sunucu bugün bunu tolere ediyor (`String?` → null), ama tel
 * biçimi belgesiz ve test edilemez hâle gelirdi. Varsayılan yok → üç anahtar daima yazılır.
 *
 * Miktar **metin** gider (bkz. [RowEntry]); kırpma ve tamamen boş satırı eleme **sunucudadır**
 * (`MalzemeIstegi.coz`) — kuralı burada tekrarlamak ikinci bir kural üretirdi (CLAUDE.md §7/1).
 */
@Serializable
data class MaterialEntry(val ad: String?, val miktar: String?, val seriNo: String?)

/** Yazma **tam değiştirmedir** — [RowsRequest] ile aynı semantik. */
@Serializable
data class MaterialsRequest(val malzemeler: List<MaterialEntry>)

/**
 * Belge arşivi satırı (`GET /api/belgeler`) — webdeki `BelgeListeSatiri` ile aynı tel biçimi.
 *
 * Her nullable alan varsayılan taşıyor: sunucuya yeni bir alan eklendiğinde `decode()` patlamasın.
 * Para alanları **string**; sayıya çevrilseydi kuruş kayardı (CLAUDE.md §5).
 */
@Serializable
data class DocumentListDto(
    val id: Long,
    val isId: Long,
    val belgeNo: String,
    val tur: DocumentType,
    val durum: DocumentStatus,
    val durumMetni: String,
    val bolgeKodu: String = "",
    val bolgeAdi: String = "",
    val sorumluAdSoyad: String = "",
    val spNo: String = "",
    val kableNosid: String = "",
    val ekAraligi: String = "",
    val route: String = "",
    val olusturma: String = "",
    val sonGuncelleme: String? = null,
    val sonGuncelleyenAdSoyad: String? = null,
    val toplamGosterim: String = "",
)
