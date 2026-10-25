package tr.com.tti.hakedis.server.sozlesme

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import tr.com.tti.hakedis.server.belge.MiktarEslestirici
import tr.com.tti.hakedis.server.belge.MiktarOkuyucu
import tr.com.tti.hakedis.server.belge.OkunanMiktar
import java.math.BigDecimal

/**
 * Şablon ↔ okuyucu sözleşmesi: **sistemin ürettiği dosya, sistemin okuyucusundan geçebilmeli.**
 *
 * Bu iki parça ayrı sınıflarda ve ayrı kütüphanelerle çalışıyor (yazma `fastexcel`, okuma Apache POI +
 * `ContractXlsxParser`). Sütun adlarından biri değişirse ya da satır düzeni kayarsa hiçbir derleme
 * hatası çıkmaz — kullanıcı dosyayı yükleyene kadar da fark edilmez. Buradaki testler o sessiz
 * kırılmayı kapatır.
 */
class PozSablonuTest {
    private val grup = ReferansPoz(101, "r3", "1", "HAVAİ GÜZERGAH İMALATLARI", "", grupMu = true, referansFiyat = null)
    private val direk = ReferansPoz(102, "r4", "1.1", "Direk Dikme-montaj", "Ad.", false, BigDecimal("648.36"))
    private val lente = ReferansPoz(103, "r5", "1.3", "Lente yapımı", "Ad.", false, BigDecimal("991.61"))
    private val referans = listOf(grup, direk, lente)

    private fun sablon(miktarlar: Map<Long, BigDecimal> = emptyMap()) =
        PozSablonu.uret("Rota-1: Edirne-Kırklareli", "2026 Sample Rota-1", referans, miktarlar)

    @Test
    fun `sablon okuyucudan gecer ve kimlik sutunu okunur`() {
        val okunan = MiktarOkuyucu.oku(sablon(), sayfaAdi = null)

        assertThat(okunan.satirlar).hasSize(3)
        assertThat(okunan.satirlar.map { it.pozId }).containsExactly(101L, 102L, 103L).inOrder()
        assertThat(okunan.satirlar.map { it.pozNo }).containsExactly("1", "1.1", "1.3").inOrder()
    }

    @Test
    fun `miktar girilmemis sablonda tum hucreler bos gelir`() {
        val okunan = MiktarOkuyucu.oku(sablon(), sayfaAdi = null)

        assertThat(okunan.satirlar.map { it.miktar }).containsExactly(OkunanMiktar.Bos, OkunanMiktar.Bos, OkunanMiktar.Bos)
    }

    /**
     * Şablon **mevcut miktarlarla dolu** iner. Boş inseydi, ekranda birkaç satır girmiş bir kullanıcı
     * şablonu yükleyerek girdiklerini silerdi — yükleme tam değiştirmedir.
     */
    @Test
    fun `mevcut miktarlar sablona yazilir ve geri okunur`() {
        val okunan = MiktarOkuyucu.oku(sablon(mapOf(102L to BigDecimal("12.5"))), sayfaAdi = null)

        val direkSatiri = okunan.satirlar.single { it.pozId == 102L }
        assertThat((direkSatiri.miktar as OkunanMiktar.Deger).value).isEqualTo(BigDecimal("12.5"))
    }

    /** Tam tur: üret → oku → eşleştir. Zincirin herhangi bir halkası kırılırsa burada görünür. */
    @Test
    fun `doldurulmus sablon eslestiriciden gecer`() {
        val okunan = MiktarOkuyucu.oku(sablon(mapOf(102L to BigDecimal("2"), 103L to BigDecimal("7"))), sayfaAdi = null)

        val sonuc = MiktarEslestirici.eslestir(referans, okunan.satirlar)

        assertThat(sonuc.gecerliMi).isTrue()
        assertThat(sonuc.kimlikSutunuKullanildi).isTrue()
        // Excel sayısal hücresi ölçek taşır (`2` → `2.0`); karşılaştırma **değer** üzerinden yapılır,
        // `BigDecimal.equals` ölçeği de kıyaslar ve burada yanıltıcı olur.
        assertThat(sonuc.eslesenler.associate { it.pozId to it.miktar.stripTrailingZeros().toPlainString() })
            .containsExactly(102L, "2", 103L, "7")
    }

    /** Grup satırı fiyatsız yazılmalı: parser birimsiz satırın fiyatlı olmasını hata sayıyor. */
    @Test
    fun `grup satiri fiyatsiz ve miktarsiz yazilir`() {
        val okunan = MiktarOkuyucu.oku(sablon(mapOf(101L to BigDecimal("9"))), sayfaAdi = null)

        assertThat(okunan.satirlar.single { it.pozId == 101L }.miktar).isEqualTo(OkunanMiktar.Bos)
    }
}
