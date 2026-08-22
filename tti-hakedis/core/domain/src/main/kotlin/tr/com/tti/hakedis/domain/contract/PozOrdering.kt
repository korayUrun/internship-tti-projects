package tr.com.tti.hakedis.domain.contract

/**
 * Poz numarası sıralaması. `"4.10"` düz string olarak `"4.9"`dan **önce** gelirdi;
 * bu yüzden sayısal parçalardan oluşan [PozItem.sortKey] karşılaştırılır.
 */
val pozItemComparator: Comparator<PozItem> = Comparator { left, right ->
    compareSortKeys(left.sortKey, right.sortKey)
}

/** Parça parça karşılaştırır; ön ek eşitse kısa olan önce gelir (`4` < `4.1`). */
fun compareSortKeys(left: List<Int>, right: List<Int>): Int {
    val shared = minOf(left.size, right.size)
    for (index in 0 until shared) {
        val diff = left[index].compareTo(right[index])
        if (diff != 0) return diff
    }
    return left.size.compareTo(right.size)
}

/**
 * `"4.10"` → `[4, 10]`, `"1,5"` → `[1, 5]`, `"4.4."` → `[4, 4]`.
 * Sayıya çevrilemeyen parçalar atlanır; hiç parça yoksa boş liste döner (sıralamada en başa gider).
 */
fun parseSortKey(pozNo: String): List<Int> = pozNo.split('.', ',')
    .mapNotNull { part -> part.trim().takeIf { it.isNotEmpty() }?.toIntOrNull() }
