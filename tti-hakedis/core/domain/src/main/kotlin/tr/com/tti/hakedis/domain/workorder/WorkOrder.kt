package tr.com.tti.hakedis.domain.workorder

import kotlinx.serialization.Serializable

/** Operasyonun yapıldığı coğrafi bölge. Kullanıcıya gösterilen metin `:app` katmanındadır. */
enum class Region {
    IST_AVR,
    IST_AND,
    EDIRNE,
    ANKARA,
    KUTAHYA,
    IZMIR,
    BURSA,
}

/** İşin niteliği. */
enum class Scope {
    DEPLASE_IYILESTIRME,
    ARIZA_SONRASI_ONARIM,
    ZORUNLU_DEPLASE,
    YENI_TESIS,
    YATAY_SONDAJ,
    ;

    /**
     * Proje açılırken kablo seçimi zorunlu mu? *(Faz W, 10 Ağustos 2026)*
     *
     * `YENI_TESIS` **hariç** hepsinde zorunlu. Yeni tesis işi kablo ile ilgilidir ama belirli bir
     * kablo kimliğinin kapsamında değildir: tesis henüz kuruluyor ve envanterde karşılığı yok.
     * Zorunlu kalsaydı bu projeler ya hiç açılamaz ya alakasız bir kablo seçilerek açılır ve
     * hakediş belgesinde yanlış bir kablo kimliğiyle adres basılırdı.
     *
     * Karar **tek yerde**: sunucu doğrulaması, web ve mobil formları bu bayrağı okur; hiçbiri
     * `kod == "YENI_TESIS"` diye kendi listesini tutmaz (CLAUDE.md §7/1).
     */
    val kableZorunluMu: Boolean get() = this != YENI_TESIS

    /**
     * Uç A / Uç B **elle** girilmesi zorunlu mu? *(12 Ağustos 2026, kullanıcı isteği)*
     *
     * Yalnızca `YENI_TESIS`'te: kablo envanterden gelmediği için adresi kullanıcı girer ve boş
     * geçilemez. Diğer kapsamlarda uçlar zaten envanterden dondurularak gelir; istekteki elle
     * segmentler yok sayılır (`IsServisi.projeKablosu`), dolayısıyla orada bu kural işletilmez.
     *
     * ~~Faz W: Yeni Tesis'te uçların üçü de opsiyoneldi~~ — kullanıcı, hakediş belgesinde uç
     * adreslerinin daima dolu olmasını istedi. `kableZorunluMu`'nun aynası; bu da tek yerde.
     */
    val uclarZorunluMu: Boolean get() = this == YENI_TESIS
}

/**
 * İşin tanımı. Miktarlar burada değil, belge tarafında tutulur.
 *
 * [kableId], [adresA] ve [adresB] kable envanterinden gelir ve işe **dondurularak** kopyalanır:
 * envanter dışarıdan periyodik olarak yenilendiği için kopyalanmasaydı, tamamlanmış bir hakedişin
 * adresi sonradan kendiliğinden değişirdi.
 */
@Serializable
data class WorkOrder(
    val region: Region? = null,
    val scope: Scope? = null,
    val contractId: String? = null,
    val spNo: String = "",
    val route: String = "",
    val ekAraligi: String = "",
    val kableId: String = "",
    val adresA: String = "",
    val adresB: String? = null,
    /**
     * Elle girilen uç segmentleri (yalnızca "Yeni Tesis"). Anahtarlar `KonumYolu.ALANLAR` ile aynı
     * (`country`, `city`, `address` …); doğrulayıcı [Scope.uclarZorunluMu] olduğunda üçünün dolu
     * olmasını arar. Envanterden gelen projelerde boş kalır (uçlar [adresA]/[adresB]'de dondurulur).
     */
    val ucASegmentleri: Map<String, String> = emptyMap(),
    val ucBSegmentleri: Map<String, String> = emptyMap(),
)
