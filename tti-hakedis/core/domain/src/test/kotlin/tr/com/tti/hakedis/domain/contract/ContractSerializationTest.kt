package tr.com.tti.hakedis.domain.contract

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test
import java.math.BigDecimal

/**
 * `:tools:contract-generator` bu modelle JSON üretir, `:app` aynı modelle okur.
 * Kritik nokta: fiyat **string** olarak taşınır; sayı olsaydı `Double` üzerinden hassasiyet kaybederdi.
 */
class ContractSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val contract = Contract(
        meta = ContractMeta(
            id = "2026-guvenay-rota-1",
            name = "2026 Güvenay Rota-1",
            title = "Rota-1: Edirne-Kırklareli-Tekirdağ",
            sheetName = "2026_Güvenay_Rota-1 Br Fiyatlar",
        ),
        items = listOf(
            PozItem(
                id = "r5",
                pozNo = "1.1",
                sortKey = listOf(1, 1),
                name = "Direk Dikme-montaj",
                unit = "Ad.",
                unitPrice = BigDecimal("648.35852723050107"),
                isGroup = false,
                isPriced = true,
            ),
        ),
    )

    @Test
    fun `gidis donus fiyat hassasiyetini korur`() {
        val geriOkunan = json.decodeFromString<Contract>(json.encodeToString(contract))

        assertThat(geriOkunan).isEqualTo(contract)
        assertThat(geriOkunan.items.first().unitPrice.toPlainString()).isEqualTo("648.35852723050107")
    }

    @Test
    fun `fiyat JSON icinde string olarak yazilir`() {
        val encoded = json.encodeToString(contract)

        assertThat(encoded).contains("\"unitPrice\":\"648.35852723050107\"")
    }

    @Test
    fun `Turkce karakterler korunur`() {
        val geriOkunan = json.decodeFromString<Contract>(json.encodeToString(contract))

        assertThat(geriOkunan.meta.name).isEqualTo("2026 Güvenay Rota-1")
        assertThat(geriOkunan.meta.sheetName).contains("Güvenay")
    }
}
