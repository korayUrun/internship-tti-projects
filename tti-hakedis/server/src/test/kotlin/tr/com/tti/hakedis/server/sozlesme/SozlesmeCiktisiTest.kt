package tr.com.tti.hakedis.server.sozlesme

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Satır menüsündeki "İndir" ile inen sözleşme, "Sözleşme Yükle" ile geri yüklenebilmeli.
 *
 * Bu çıktının tek varlık nedeni "düzelt ve yeni kodla yükle" akışı (§7/4: sözleşme değiştirilemez).
 * Sütun düzeni `SozlesmeSablonu` ile ayrışırsa indirilen dosya geri yüklenemez ve hata yalnızca
 * yönetici dosyayı doldurup yüklemeyi denediğinde ortaya çıkar.
 */
class SozlesmeCiktisiTest {
    private fun poz(pozNo: String, ad: String, birim: String = "", fiyat: String = "0", grup: Boolean = false, fiyatli: Boolean = true) =
        PozYaniti(
            id = pozNo,
            pozNo = pozNo,
            sortKey = listOf(0),
            name = ad,
            unit = birim,
            unitPrice = BigDecimal(fiyat),
            unitPriceGosterim = fiyat,
            isGroup = grup,
            isPriced = fiyatli,
        )

    private fun oku(icerik: ByteArray) = XlsxOkuyucu.sayfada(icerik, sayfaAdi = null) { sayfa ->
        tr.com.tti.hakedis.tools.ContractXlsxParser.parse(sayfa, mutableListOf())
    }

    @Test
    fun `uretilen dosya sozlesme okuyucusundan gecer`() {
        val icerik = SozlesmeCiktisi.uret(
            kod = "2026-sample-rota-1",
            baslik = "Rota-1: Edirne-Kırklareli",
            pozlar = listOf(
                poz("1", "HAVAİ GÜZERGAH İMALATLARI", grup = true, fiyatli = false),
                poz("1.1", "Direk Dikme-montaj", birim = "Ad.", fiyat = "648.3585272"),
            ),
        )

        val contract = oku(icerik)

        assertThat(contract.meta.title).isEqualTo("Rota-1: Edirne-Kırklareli")
        assertThat(contract.items).hasSize(2)
        assertThat(contract.items.first().isGroup).isTrue()
        assertThat(contract.items.last().isGroup).isFalse()
        assertThat(contract.items.last().pozNo).isEqualTo("1.1")
    }

    @Test
    fun `sayfa adi sozlesme kodudur`() {
        // Yeni kod sayfa adından türetiliyor. Sabit bir "Sozlesme" adı kullanılsaydı her indirilen
        // dosya geri yüklendiğinde aynı kodu üretir ve yönetici adı sıfırdan yazmak zorunda kalırdı.
        val icerik = SozlesmeCiktisi.uret(
            kod = "2026-sample-rota-1",
            baslik = "Test",
            pozlar = listOf(poz("1.1", "Kalem", birim = "Ad.", fiyat = "1")),
        )

        val sayfaAdlari = XlsxOkuyucu.sayfaAdlari(icerik)

        assertThat(sayfaAdlari).containsExactly("2026-sample-rota-1")
    }

    @Test
    fun `ham fiyat yuvarlanmadan yazilir`() {
        // Yuvarlama belgeye çivilenirken yapılır (§5). Burada yapılsaydı indirilip geri yüklenen
        // sözleşme her turda biraz daha kuruş kaybederdi.
        val icerik = SozlesmeCiktisi.uret(
            kod = "test-kod",
            baslik = "Test",
            pozlar = listOf(poz("1.1", "Kalem", birim = "Mt.", fiyat = "371.86716779978428")),
        )

        val fiyat = oku(icerik).items.single().unitPrice

        assertThat(fiyat).isEqualTo(BigDecimal("371.86716779978428"))
    }
}
