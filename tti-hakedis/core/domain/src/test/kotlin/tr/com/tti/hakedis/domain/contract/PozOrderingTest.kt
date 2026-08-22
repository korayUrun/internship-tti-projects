package tr.com.tti.hakedis.domain.contract

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal

class PozOrderingTest {
    @Test
    fun `4_10 poz numarasi 4_9 dan sonra gelir`() {
        val items = listOf(pozItem("4.10"), pozItem("4.9"), pozItem("4.2"))

        val sirali = items.sortedWith(pozItemComparator).map { it.pozNo }

        assertThat(sirali).containsExactly("4.2", "4.9", "4.10").inOrder()
    }

    @Test
    fun `duz string siralamasi yanlis sonuc verirdi`() {
        val pozNolar = listOf("4.10", "4.9")

        // Referans: string sıralaması "4.10" < "4.9" der — bu yüzden sortKey gerekiyor.
        assertThat(pozNolar.sorted()).containsExactly("4.10", "4.9").inOrder()
    }

    @Test
    fun `grup satiri alt kalemlerinden once gelir`() {
        val items = listOf(pozItem("4.1"), pozItem("4"), pozItem("3.2"))

        val sirali = items.sortedWith(pozItemComparator).map { it.pozNo }

        assertThat(sirali).containsExactly("3.2", "4", "4.1").inOrder()
    }

    @Test
    fun `sortKey virgullu ve sondaki noktali yazimlari tolere eder`() {
        assertThat(parseSortKey("4.10")).containsExactly(4, 10).inOrder()
        assertThat(parseSortKey("1,5")).containsExactly(1, 5).inOrder()
        assertThat(parseSortKey("4.4.")).containsExactly(4, 4).inOrder()
        assertThat(parseSortKey("6,23")).containsExactly(6, 23).inOrder()
        assertThat(parseSortKey("9")).containsExactly(9)
    }

    @Test
    fun `sayisal parca icermeyen poz numarasi bos anahtar uretir`() {
        assertThat(parseSortKey("EK")).isEmpty()
    }

    private fun pozItem(pozNo: String) = PozItem(
        id = "id-$pozNo",
        pozNo = pozNo,
        sortKey = parseSortKey(pozNo),
        name = "Kalem $pozNo",
        unit = "Ad.",
        unitPrice = BigDecimal.ONE,
        isGroup = false,
        isPriced = true,
    )
}
