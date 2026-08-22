package tr.com.tti.hakedis.server.config

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import tr.com.tti.hakedis.domain.pricing.MoneyFormatter
import tr.com.tti.hakedis.server.sozlesme.PozYaniti
import java.math.BigDecimal

/**
 * Para değerlerinin JSON'a **string** olarak yazıldığını kilitler.
 *
 * Bu sessizce bozulabilecek bir davranış: `BigDecimal` JSON sayısı olarak yazılırsa istemcide
 * `double`'a düşer, 17 haneli ham birim fiyat kırpılır ve tutar kuruş kaydırır. Hiçbir hata çıkmaz.
 */
class ParaJsonYapilandirmasiTest {
    private val mapper =
        JsonMapper.builder()
            .addModule(ParaJsonYapilandirmasi().paraStringModulu())
            .build()

    private val poz =
        PozYaniti(
            id = "r5",
            pozNo = "1.1",
            sortKey = listOf(1, 1),
            name = "Direk Dikme-montaj",
            unit = "Ad.",
            unitPrice = BigDecimal("648.35852723050107"),
            unitPriceGosterim = MoneyFormatter.money(BigDecimal("648.35852723050107")),
            isGroup = false,
            isPriced = true,
        )

    @Test
    fun `birim fiyat JSON'a string olarak ve tam hassasiyetle yazilir`() {
        val json = mapper.writeValueAsString(poz)

        assertThat(json).contains("\"unitPrice\":\"648.35852723050107\"")
        // Tırnaksız yazılırsa JSON sayısı olur; istemci tarafında double'a düşer.
        assertThat(json).doesNotContain("\"unitPrice\":648")
    }

    @Test
    fun `birim fiyatin gosterim metni sunucuda uretilir`() {
        val json = mapper.writeValueAsString(poz)

        // Web ve mobil para biçimlendirmesi yapmaz; gösterim metni `MoneyFormatter` ile tek yerde
        // üretilip yanıtta taşınır (CLAUDE.md §5). Faz P'den beri 2 haneye yuvarlı: belgeye çivilenen
        // fiyat da yuvarlandığı için gösterilen sayı hesapta kullanılan sayının aynısı.
        assertThat(json).contains("\"unitPriceGosterim\":\"648,36 ₺\"")
    }

    @Test
    fun `isGroup ve isPriced alan adlari kirpilmadan yazilir`() {
        val json = mapper.writeValueAsString(poz)

        // Jackson `isXxx` getter'ını varsayılan olarak `xxx` özelliği sayar; alan adları
        // :core:domain'deki PozItem ile birebir aynı kalmalı, yoksa mobil ve web şemayı kaçırır.
        assertThat(json).contains("\"isGroup\":false")
        assertThat(json).contains("\"isPriced\":true")
        assertThat(json).doesNotContain("\"group\":")
        assertThat(json).doesNotContain("\"priced\":")
    }

    @Test
    fun `string olarak gelen para degeri BigDecimal'e tam cevrilir`() {
        val json = mapper.writeValueAsString(poz)

        val geriOkunan = mapper.readValue(json, PozYaniti::class.java)

        assertThat(geriOkunan.unitPrice).isEqualTo(BigDecimal("648.35852723050107"))
        assertThat(geriOkunan).isEqualTo(poz)
    }
}
