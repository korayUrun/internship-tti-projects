package tr.com.tti.hakedis.server.belge

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import tr.com.tti.hakedis.domain.document.DocumentStatus
import tr.com.tti.hakedis.domain.document.DocumentType
import java.math.BigDecimal
import java.time.Instant

/**
 * `isId` alanının JSON'a **bu adla** çıktığını kilitler.
 *
 * Sessizce bozulabilecek bir davranış: Kotlin `is` ile başlayan özellik için getter'ı `isId()` üretir,
 * Jackson bunu boolean kalıbı sanıp öneki kırpar ve alanı `"id"` adıyla yazmaya çalışır. Aynı sınıfta
 * `id` zaten olduğu için alan JSON'dan **tamamen düşer** ve hiçbir hata çıkmaz. Web belgenin hangi işe
 * ait olduğunu okuyamaz; hata ancak `/api/isler/undefined` çağrısında 500 olarak görünür.
 */
class BelgeYanitlariTest {
    private val mapper = JsonMapper.builder().build()

    private val belge =
        BelgeYaniti(
            id = 316,
            isId = 42,
            belgeNo = "BURSA_2026_FIZIBILITE_003",
            tur = DocumentType.FIZIBILITE,
            durum = DocumentStatus.ONAY_BEKLIYOR,
            durumMetni = "Onay Bekliyor",
            bolgeKodu = "BURSA",
            yil = 2026,
            siraNo = 3,
            sozlesmeKodu = "2026-guvenay-rota-1",
            sozlesmeAdi = "2026 Güvenay Rota-1",
            fiyatVersiyonuId = 4,
            olusturma = Instant.EPOCH,
            gonderme = null,
            tamamlanma = null,
            sonGuncelleme = Instant.EPOCH,
            sonGuncelleyenAdSoyad = "Bursa Bölge Sorumlusu",
            sonGuncelleyenRol = "BOLGE_SORUMLUSU",
            iptalGerekce = null,
            duzenlenebilir = true,
            gonderilebilir = true,
            satirlar = emptyList(),
            toplam = BigDecimal.ZERO,
            toplamGosterim = "0,00 ₺",
        )

    private val listeSatiri =
        BelgeListeYaniti(
            id = 316,
            isId = 42,
            belgeNo = "BURSA_2026_FIZIBILITE_003",
            tur = DocumentType.FIZIBILITE,
            durum = DocumentStatus.ONAY_BEKLIYOR,
            durumMetni = "Onay Bekliyor",
            bolgeKodu = "BURSA",
            bolgeAdi = "Bursa",
            sorumluAdSoyad = "Bursa Bölge Sorumlusu",
            olusturanAdSoyad = "Servet Özkan",
            spNo = "SP-1",
            kableNosid = "mtc07962a.cable*mns-usa",
            ekAraligi = "EK1-EK4",
            route = "R-1",
            yil = 2026,
            siraNo = 3,
            olusturma = Instant.EPOCH,
            gonderme = null,
            tamamlanma = null,
            sonGuncelleme = Instant.EPOCH,
            sonGuncelleyenAdSoyad = "Bursa Bölge Sorumlusu",
            sonGuncelleyenRol = "BOLGE_SORUMLUSU",
            toplam = BigDecimal.ZERO,
            toplamGosterim = "0,00 ₺",
        )

    @Test
    fun `belge detayinda isId kendi adiyla yazilir ve id ile karismaz`() {
        val json = mapper.writeValueAsString(belge)

        assertThat(json).contains("\"isId\":42")
        assertThat(json).contains("\"id\":316")
    }

    @Test
    fun `belge listesinde isId kendi adiyla yazilir`() {
        val json = mapper.writeValueAsString(listOf(listeSatiri))

        assertThat(json).contains("\"isId\":42")
        assertThat(json).contains("\"id\":316")
    }

    @Test
    fun `isId geri okunabilir`() {
        val geriOkunan = mapper.readValue(mapper.writeValueAsString(belge), BelgeYaniti::class.java)

        assertThat(geriOkunan.isId).isEqualTo(42)
        assertThat(geriOkunan).isEqualTo(belge)
    }
}
