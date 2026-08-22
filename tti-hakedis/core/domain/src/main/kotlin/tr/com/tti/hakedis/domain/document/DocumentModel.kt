package tr.com.tti.hakedis.domain.document

import tr.com.tti.hakedis.domain.workorder.Region
import tr.com.tti.hakedis.domain.workorder.Scope
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Önizleme, PDF ve Excel'in **ortak** veri kaynağı. Üç yerde üç farklı sonuç çıkması bu model
 * sayesinde imkânsızdır.
 *
 * Kullanıcıya görünen etiket metinleri burada **yoktur**; `Region`/`Scope` gibi domain sabitleri
 * taşınır, metne çevirme sunum katmanında yapılır.
 *
 * [variant] `TEKLIF` olduğunda para alanları ([DocumentRow.unitPrice], [DocumentRow.amount] ve
 * [total]) `null`'dır: miktarlar görünür, fiyatı teklif veren firma dolduracaktır. Şablonlarda ek bir
 * dallanma gerekmez — `null` alanlar boş basılır.
 */
data class DocumentModel(
    /** `IST-AVR_2026_KESIF_003` — belgenin kalıcı kimliği. */
    val documentNo: String,
    val type: DocumentType,
    val variant: DocumentVariant,
    /** Başlık — sözleşmenin kaynak Excel'indeki 1. satır metni. */
    val title: String,
    val header: DocumentHeader,
    val sections: List<DocumentSection>,
    /** `TEKLIF` varyantında `null`. */
    val total: BigDecimal?,
    /**
     * Aynı işin **keşif belgesinin toplamı** — yalnızca hakedişte dolu (31 Temmuz 2026 kullanıcı
     * isteği). Listelenen satırlardan yeniden toplanmaz: keşif belgesinde yazan sayının aynısı
     * olmalı, yoksa iki belgeyi yan yana koyan kişi iki farklı toplam görür.
     * `TEKLIF` varyantında `null`.
     */
    val kesifTotal: BigDecimal? = null,
    val attachments: DocumentAttachments,
    val createdAt: LocalDateTime,
) {
    val rowCount: Int get() = sections.sumOf { it.rows.size }

    val isEmpty: Boolean get() = rowCount == 0
}

data class DocumentHeader(
    val region: Region?,
    val scope: Scope?,
    val contractName: String,
    val spNo: String,
    val route: String,
    val ekAraligi: String,
    /**
     * Kablo kimliği ve uç adresleri — üçü de **boş olabilir** (Faz W): kapsamı "Yeni Tesis" olan bir
     * projede kablo seçimi zorunlu değil.
     *
     * Adres, kable envanterinden gelmişse işe dondurularak kopyalanmıştır; kablosuz projede yönetici
     * elle girmiştir. Her iki hâlde de donmuştur — envanter yenilendiğinde değişmez.
     *
     * Boş alanın **satırı hiç basılmaz**; kural [PdfTemplate] ve `ExcelUretici`'de, `adresB`'nin
     * baştan beri uyguladığı desenin aynısı.
     */
    val kableId: String?,
    val adresA: String?,
    val adresB: String?,
)

/** Grup başlığı ve altındaki **dolu** kalemler. Altında kalem kalmayan grup modele girmez. */
data class DocumentSection(val groupPozNo: String, val groupName: String, val rows: List<DocumentRow>)

data class DocumentRow(
    val pozNo: String,
    val name: String,
    val unit: String,
    val quantity: BigDecimal,
    /** `TEKLIF` varyantında `null`. */
    val unitPrice: BigDecimal?,
    /** `TEKLIF` varyantında `null`. */
    val amount: BigDecimal?,
    /** Aynı işin keşif belgesindeki miktar — yalnızca hakedişte dolu. Para değil, `TEKLIF`'te de görünür. */
    val kesifQuantity: BigDecimal? = null,
    /** Keşif tutarı, keşifin **kendi** birim fiyatıyla. `TEKLIF` varyantında `null`. */
    val kesifAmount: BigDecimal? = null,
)

/**
 * Belgenin ekleri. Güzergâh dosyası (`.kml`/`.kmz`) **üç belge türünde de zorunludur** (Faz P);
 * görseller opsiyoneldir.
 *
 * [materials] = EK3 "TTI Kullanılan Malzemeler" — **yalnızca hakedişte** dolar, diğer türlerde
 * daima boş liste. Dosya değil yapısal veri olduğu için `imagePaths` gibi bir yol listesi değil.
 */
data class DocumentAttachments(
    val routeFileName: String?,
    val imagePaths: List<String>,
    val materials: List<DocumentMaterial> = emptyList(),
) {
    val imageCount: Int get() = imagePaths.size
}

/**
 * EK3 satırı: sahada kullanılan bir malzeme (kullanıcı isteği, 18 Ağustos 2026).
 *
 * **Üç alan da opsiyonel** — saha bazen yalnızca seri no, bazen yalnızca ad biliyor. Tamamen boş
 * satır modele hiç girmez ([bosMu] ile elenir); aksi hâlde çıktıda boş satırlar birikirdi.
 *
 * [miktar] `BigDecimal`: metre/m² gibi ondalıklı olabilir ve `Double` §5 gereği yasak. Para değil,
 * bu yüzden yuvarlama kuralına girmiyor — girildiği hassasiyetle basılır.
 */
data class DocumentMaterial(val ad: String?, val miktar: java.math.BigDecimal?, val seriNo: String?) {
    val bosMu: Boolean
        get() = ad.isNullOrBlank() && miktar == null && seriNo.isNullOrBlank()
}
