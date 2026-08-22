package tr.com.tti.hakedis.feature.document

/**
 * Poz tablosu, projedeki tek zorunlu Compose UI testinin hedefi.
 * Hücreler metinleri tekrar ettiği için test bunları etiketle bulur.
 */
internal fun quantityTestTag(itemId: String): String = "quantity-$itemId"

internal fun amountTestTag(itemId: String): String = "amount-$itemId"

internal const val TOTAL_TAG = "total"
