package tr.com.tti.hakedis.feature.document

import androidx.compose.ui.unit.dp

/**
 * Tablo sütun genişlikleri. `Poz No` + `İş Kalemi` yatay kaydırmada **sabit** kalır,
 * kalan sütunlar ortak bir `ScrollState` ile birlikte kayar.
 */
internal object PozColumn {
    val pozNo = 96.dp
    val name = 260.dp
    val unit = 76.dp

    /**
     * ⚠️ Genişlik, birim fiyatın **yuvarlanmadığı** varsayımıyla 220 dp'ye çıkarılmıştı
     * (`648,35852723050107 ₺`, 20 karakter). O varsayım artık geçersiz: fiyat sunucuda **2 haneye
     * yuvarlanıyor** (`SozlesmeSorgulari`, §5, 3 Ağustos 2026) ve gerçekçi en uzun değer
     * `1.234.567,89 ₺`. Sütun bu yüzden ihtiyaçtan geniş; daraltmak **görünür** bir değişiklik
     * olduğu için ayrı bir turda, cihazda ölçülerek yapılacak.
     *
     * Dar sütunun bedeli değişmedi: fiyat iki satıra sararsa `Br Fiyat × Miktar = Tutar` kontrolü
     * okunamaz hâle geliyor (Düzeltme Turu 1).
     */
    val unitPrice = 220.dp
    val quantity = 140.dp
    val amount = 156.dp

    val minRowHeight = 64.dp

    /**
     * Tablo, dikey kaydırılan bir ekranın içinde yaşıyor. Yüksekliği sınırlanmazsa `LazyColumn`
     * sonsuz yükseklik kısıtıyla ölçülür ve çalışma zamanında hata verir; ayrıca 95 satırlık liste
     * ekranın geri kalanını kullanılamaz hâle getirirdi.
     */
    val tableMaxHeight = 560.dp
}
