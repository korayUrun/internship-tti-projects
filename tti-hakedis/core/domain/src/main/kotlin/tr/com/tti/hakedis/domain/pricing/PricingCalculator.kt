package tr.com.tti.hakedis.domain.pricing

import tr.com.tti.hakedis.domain.contract.PozItem
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Tutar hesabı (CLAUDE.md §5):
 * satır tutarı = `unitPrice × quantity`, **satır bazında** `setScale(2, HALF_UP)`;
 * toplam = yuvarlanmış satır tutarlarının toplamı.
 *
 * Hesaplanan değerler hiçbir yerde saklanmaz — ne ekranda, ne veritabanında; her zaman buradan
 * türetilir. Bu sınıf sunucunun, mobilin ve çıktı üretiminin **ortak** kaynağıdır.
 *
 * Miktarlar `poz satır kodu → miktar` eşlemesi olarak verilir. **Anahtarın bulunmaması "girilmemiş"
 * demektir**; `BigDecimal.ZERO` ise "0 girilmiş"tir ve ikisi farklı şeydir: çıktıda yalnızca girilmiş
 * satırlar listelenir. Veritabanında da aynı ayrım satırın varlığıyla korunur.
 */
object PricingCalculator {
    private val SIFIR_PARA: BigDecimal = BigDecimal.ZERO.setScale(2)

    /** Tek satırın tutarı — kuruşa yuvarlanmış. */
    fun rowAmount(unitPrice: BigDecimal, quantity: BigDecimal): BigDecimal = unitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP)

    /**
     * Belgenin toplamı. Grup satırları, fiyatlandırılmamış satırlar ve miktarı girilmemiş satırlar
     * toplama katılmaz.
     */
    fun total(items: List<PozItem>, quantities: Map<String, BigDecimal>): BigDecimal = total(
        items.mapNotNull { item ->
            if (item.isGroup || !item.isPriced) return@mapNotNull null
            val miktar = quantities[item.id] ?: return@mapNotNull null
            PricedRow(item.unitPrice, miktar)
        },
    )

    /**
     * Kaydedilmiş belge satırlarından toplam.
     *
     * Sunucu tarafında toplam bu aşırı yüklemeyle hesaplanır: veritabanındaki `belge_satir` yalnızca
     * miktarı **girilmiş** satırları tutar ve birim fiyatı kendi içinde dondurmuş olduğu için sözleşme
     * poz listesine bakmaya gerek yoktur. Yuvarlama sırası tek yerde kalır (§5): önce satır bazında
     * `setScale(2, HALF_UP)`, sonra toplama.
     */
    fun total(rows: List<PricedRow>): BigDecimal {
        var toplam = SIFIR_PARA
        for (row in rows) {
            toplam = toplam.add(rowAmount(row.unitPrice, row.quantity))
        }
        return toplam
    }

    /**
     * **Zaten hesaplanmış** tutarların toplamı — belge toplamları gibi.
     *
     * [total]'dan farkı girdisi: o, ham `fiyat × miktar` çiftlerinden başlayıp satır bazında yuvarlar;
     * bu ise yuvarlanmış tutarları toplar ve yeniden yuvarlama yapmaz. Liste çıktılarının alt toplamı
     * bunu kullanır. Toplama burada duruyor ki `BigDecimal.add` zinciri çağıran tarafta elle
     * yazılmasın (CLAUDE.md §5: para aritmetiği tek yerde).
     */
    fun sum(amounts: List<BigDecimal>): BigDecimal {
        var toplam = SIFIR_PARA
        for (amount in amounts) {
            toplam = toplam.add(amount)
        }
        return toplam
    }
}

/** Tutarı hesaplanabilir tek satır: dondurulmuş birim fiyat + girilmiş miktar. */
data class PricedRow(val unitPrice: BigDecimal, val quantity: BigDecimal)
