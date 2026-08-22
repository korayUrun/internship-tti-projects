package tr.com.tti.hakedis.tools.kable

/**
 * Kable envanterinden okunan tek satır.
 *
 * [sahaAdi] envanterin "Saha" sayfasından, danışman adı üzerinden çözülmüş saha etiketidir
 * ("İstanbul Avp", "Kutahya_Sirnak" gibi). Bölgeye çevirme işi veritabanı tarafında yapılır:
 * eşleşme `bolge.xlsx_saha_adi` sütununda durur, kodda sabitlenmez.
 *
 * [adresA] / [adresB] envanterin **"Location A / Location B" konum yoludur**, tek bir `addressA`
 * hücresi değil (kullanıcı isteği, 4 Ağustos 2026):
 * `Turkiye >> Istanbul >> AND Plaza … >> AND Plaza > Ground Floor >> System Room >> …`
 * Yolu `KableXlsxParser.konumYolu` kurar; alan adları uygulamada `adres` olarak kalıyor çünkü
 * taşıdıkları anlam aynı — işin "Uç A / Uç B" bilgisi.
 */
data class KableSatiri(
    val nosid: String,
    val danismanAd: String,
    val sahaAdi: String,
    val adresA: String,
    val adresB: String?,
    val sehirA: String?,
    val sehirB: String?,
)

/** İçe aktarımın hiç yazmadan durmasına yol açan veri hatası. */
class KableParseException(mesaj: String) : RuntimeException(mesaj)
