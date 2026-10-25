package tr.com.tti.hakedis.domain.pricing

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tr.com.tti.hakedis.domain.contract.PozItem
import tr.com.tti.hakedis.domain.contract.parseSortKey
import java.math.BigDecimal

class PricingCalculatorTest {
    // Gerçek sözleşme verisi: 2026 Sample Rota-1, poz 1.1 "Direk Dikme-montaj"
    private val directDikme = pozItem(id = "r5", pozNo = "1.1", unitPrice = "648.35852723050107")

    // Poz 2.1 "Direkli güzergahta F/O Havai Kablo çekimi - montaj"
    private val kabloCekimi = pozItem(id = "r11", pozNo = "2.1", unitPrice = "35.330091343077179", unit = "Mt")

    private val grupSatiri = pozItem(id = "r4", pozNo = "1", unitPrice = "0", unit = "", isGroup = true)

    private val fiyatsizSatir = pozItem(id = "r24", pozNo = "4.2", unitPrice = "0")

    @Test
    fun `satir tutari kurusa yuvarlanir - elle hesap ile birebir`() {
        // 648.35852723050107 × 3 = 1945.07558169150321 → 1945,08
        val amount = PricingCalculator.rowAmount(directDikme.unitPrice, BigDecimal("3"))
        assertThat(amount).isEqualTo(BigDecimal("1945.08"))
    }

    @Test
    fun `ondalikli miktarda da kurus dogru`() {
        // 35.330091343077179 × 12.5 = 441.6261417884647375 → 441,63
        val amount = PricingCalculator.rowAmount(kabloCekimi.unitPrice, BigDecimal("12.5"))
        assertThat(amount).isEqualTo(BigDecimal("441.63"))
    }

    @Test
    fun `yuvarlama HALF_UP - tam yarim yukari gider`() {
        val amount = PricingCalculator.rowAmount(BigDecimal("0.005"), BigDecimal("1"))
        assertThat(amount).isEqualTo(BigDecimal("0.01"))
    }

    @Test
    fun `toplam yuvarlanmis satir tutarlarinin toplamidir`() {
        // Her satır ayrı ayrı 0,01'e yuvarlanır → toplam 0,02
        // (önce toplanıp sonra yuvarlansaydı 0,01 çıkardı)
        val items = listOf(
            pozItem(id = "a", pozNo = "1.1", unitPrice = "0.005"),
            pozItem(id = "b", pozNo = "1.2", unitPrice = "0.005"),
        )
        val miktarlar = mapOf("a" to BigDecimal.ONE, "b" to BigDecimal.ONE)

        assertThat(PricingCalculator.total(items, miktarlar)).isEqualTo(BigDecimal("0.02"))
    }

    @Test
    fun `grup satiri toplama katilmaz`() {
        val miktarlar = mapOf(grupSatiri.id to BigDecimal("10"), directDikme.id to BigDecimal("2"))

        val toplam = PricingCalculator.total(listOf(grupSatiri, directDikme), miktarlar)

        // Sadece 648.35852723050107 × 2 = 1296.71705446100214 → 1296,72
        assertThat(toplam).isEqualTo(BigDecimal("1296.72"))
    }

    @Test
    fun `fiyatlandirilmamis satir toplama katilmaz`() {
        val toplam = PricingCalculator.total(listOf(fiyatsizSatir), mapOf(fiyatsizSatir.id to BigDecimal("100")))

        assertThat(toplam.compareTo(BigDecimal.ZERO)).isEqualTo(0)
    }

    @Test
    fun `girilmemis miktar ile sifir girilmis miktar ayni toplami verir ama ayri durumlardir`() {
        // Anahtarın yokluğu "girilmemiş", ZERO ise "0 girilmiş" demektir: toplamları aynı,
        // ama çıktıda yalnızca ikincisi satır olarak listelenir (bkz. DocumentModelBuilder).
        val girilmemis = PricingCalculator.total(listOf(directDikme), emptyMap())
        val sifirGirilmis = PricingCalculator.total(listOf(directDikme), mapOf(directDikme.id to BigDecimal.ZERO))

        assertThat(girilmemis.compareTo(sifirGirilmis)).isEqualTo(0)
    }

    @Test
    fun `birden fazla satirin tutarlari toplanir`() {
        val miktarlar = mapOf(directDikme.id to BigDecimal("3"), kabloCekimi.id to BigDecimal("12.5"))

        val toplam = PricingCalculator.total(listOf(directDikme, kabloCekimi), miktarlar)

        // 1945,08 + 441,63
        assertThat(toplam).isEqualTo(BigDecimal("2386.71"))
    }

    @Test
    fun `hic giris yoksa toplam sifirdir`() {
        val toplam = PricingCalculator.total(listOf(directDikme, kabloCekimi), emptyMap())

        assertThat(toplam.compareTo(BigDecimal.ZERO)).isEqualTo(0)
    }

    // --- Kaydedilmiş belge satırlarından toplam (sunucu yolu) -----------------------------------

    @Test
    fun `belge satirlarindan toplam poz listesi olmadan ayni sonucu verir`() {
        val miktarlar = mapOf(directDikme.id to BigDecimal("3"), kabloCekimi.id to BigDecimal("12.5"))
        val pozListesiyle = PricingCalculator.total(listOf(directDikme, kabloCekimi), miktarlar)

        val satirlardan = PricingCalculator.total(
            listOf(
                PricedRow(directDikme.unitPrice, BigDecimal("3")),
                PricedRow(kabloCekimi.unitPrice, BigDecimal("12.5")),
            ),
        )

        assertThat(satirlardan).isEqualTo(pozListesiyle)
        assertThat(satirlardan).isEqualTo(BigDecimal("2386.71"))
    }

    @Test
    fun `belge satirlarinda da yuvarlama satir bazindadir`() {
        val toplam = PricingCalculator.total(
            listOf(
                PricedRow(BigDecimal("0.005"), BigDecimal.ONE),
                PricedRow(BigDecimal("0.005"), BigDecimal.ONE),
            ),
        )

        assertThat(toplam).isEqualTo(BigDecimal("0.02"))
    }

    @Test
    fun `satir yoksa toplam iki kurus olcegiyle sifirdir`() {
        assertThat(PricingCalculator.total(emptyList())).isEqualTo(BigDecimal("0.00"))
    }

    private fun pozItem(id: String, pozNo: String, unitPrice: String, unit: String = "Ad.", isGroup: Boolean = false) = PozItem(
        id = id,
        pozNo = pozNo,
        sortKey = parseSortKey(pozNo),
        name = "Test kalemi $pozNo",
        unit = unit,
        unitPrice = BigDecimal(unitPrice),
        isGroup = isGroup,
        isPriced = !isGroup && BigDecimal(unitPrice).signum() > 0,
    )
}
