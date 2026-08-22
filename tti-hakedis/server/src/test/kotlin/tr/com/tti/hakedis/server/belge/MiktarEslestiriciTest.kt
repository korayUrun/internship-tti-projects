package tr.com.tti.hakedis.server.belge

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import tr.com.tti.hakedis.server.sozlesme.ReferansPoz
import java.math.BigDecimal

/**
 * Eşleştirmenin dört katmanlı savunması — miktar sürümü (NEWPHASES.md E10, Faz P'de uyarlandı).
 *
 * Yanlış satıra yazılan miktar, yanlış satıra yazılan fiyat kadar pahalıdır: ikisi de doğrudan tutarı
 * bozar ve hiçbir hata çıkmaz. Bu yüzden kimlik sütunu + poz/ad/birim teyidi aynen korundu.
 */
class MiktarEslestiriciTest {
    private val grup = ReferansPoz(101, "r3", "1", "HAVAİ GÜZERGAH İMALATLARI", "", grupMu = true, referansFiyat = null)
    private val direk = ReferansPoz(102, "r4", "1.1", "Direk Dikme-montaj", "Ad.", false, BigDecimal("648.36"))
    private val lente = ReferansPoz(103, "r5", "1.3", "Lente yapımı", "Ad.", false, BigDecimal("991.61"))
    private val fiyatsiz = ReferansPoz(104, "r6", "4.2", "Fiyatlandırılmamış kalem", "Ad.", false, null)
    private val referans = listOf(grup, direk, lente, fiyatsiz)

    private fun satir(
        poz: ReferansPoz,
        miktar: OkunanMiktar,
        pozId: Long? = poz.pozId,
        pozNo: String = poz.pozNo,
        ad: String = poz.ad,
        birim: String = poz.birim,
    ) = YuklenenSatir(pozId, poz.satirKodu, pozNo, ad, birim, miktar)

    private fun deger(v: String) = OkunanMiktar.Deger(BigDecimal(v))

    // --- Katman 1: kimlik sütunu ---------------------------------------------------------------

    @Test
    fun `kimlik sutunu varsa satir sirasindan bagimsiz eslesir`() {
        // Satırlar ters sırada; kimlik sütunu olduğu için eşleşme bozulmuyor.
        val sonuc = MiktarEslestirici.eslestir(
            referans,
            listOf(satir(lente, deger("3")), satir(direk, deger("2")), satir(grup, OkunanMiktar.Bos)),
        )

        assertThat(sonuc.gecerliMi).isTrue()
        assertThat(sonuc.kimlikSutunuKullanildi).isTrue()
        assertThat(sonuc.eslesenler.associate { it.pozId to it.miktar })
            .containsExactly(103L, BigDecimal("3"), 102L, BigDecimal("2"))
    }

    @Test
    fun `baska sozlesmenin kimligi reddedilir`() {
        val sonuc = MiktarEslestirici.eslestir(referans, listOf(satir(direk, deger("2"), pozId = 9999)))

        assertThat(sonuc.gecerliMi).isFalse()
        assertThat(sonuc.sorunlar.single().aciklama).contains("bu sözleşmede yok")
    }

    // --- Katman 2: satır kodu + üçlü teyit -----------------------------------------------------

    @Test
    fun `kimlik sutunu yoksa satir kaymasi poz no teyidiyle yakalanir`() {
        // Kimlik sütunu silinmiş (pozId null) ve satır kaymış: r4'te lentenin bilgileri var.
        val kaymis = YuklenenSatir(null, "r4", lente.pozNo, lente.ad, lente.birim, deger("2"))

        val sonuc = MiktarEslestirici.eslestir(referans, listOf(kaymis))

        assertThat(sonuc.gecerliMi).isFalse()
        assertThat(sonuc.kimlikSutunuKullanildi).isFalse()
        assertThat(sonuc.sorunlar.single().aciklama).contains("uyuşmuyor")
    }

    @Test
    fun `kimlik sutunu yoksa dogru hizali dosya kabul edilir`() {
        val sonuc = MiktarEslestirici.eslestir(
            referans,
            listOf(satir(direk, deger("2"), pozId = null), satir(lente, deger("5"), pozId = null)),
        )

        assertThat(sonuc.gecerliMi).isTrue()
        assertThat(sonuc.eslesenler.map { it.satirKodu }).containsExactly("r4", "r5").inOrder()
    }

    // --- Miktara özgü kurallar -----------------------------------------------------------------

    /** Fiyat listesinden en önemli fark: boş hücre hata değil, "girilmemiş" demektir. */
    @Test
    fun `bos hucre hata degildir - kalem sadece yazilmaz`() {
        val sonuc = MiktarEslestirici.eslestir(
            referans,
            listOf(satir(direk, deger("2")), satir(lente, OkunanMiktar.Bos)),
        )

        assertThat(sonuc.gecerliMi).isTrue()
        assertThat(sonuc.eslesenler.map { it.pozId }).containsExactly(102L)
    }

    /** "Girilmemiş" ile "0 girilmiş" ayrı: sıfır meşru bir miktardır ve yazılır (CLAUDE.md §5). */
    @Test
    fun `sifir gecerli bir miktardir ve yazilir`() {
        val sonuc = MiktarEslestirici.eslestir(referans, listOf(satir(direk, deger("0"))))

        assertThat(sonuc.gecerliMi).isTrue()
        assertThat(sonuc.eslesenler.single().miktar).isEqualTo(BigDecimal("0"))
    }

    @Test
    fun `negatif miktar reddedilir`() {
        val sonuc = MiktarEslestirici.eslestir(referans, listOf(satir(direk, deger("-1"))))

        assertThat(sonuc.gecerliMi).isFalse()
        assertThat(sonuc.sorunlar.single().aciklama).contains("negatif")
    }

    @Test
    fun `sayiya cevrilemeyen miktar sessizce sifirlanmaz`() {
        val sonuc = MiktarEslestirici.eslestir(referans, listOf(satir(direk, OkunanMiktar.Gecersiz("iki adet"))))

        assertThat(sonuc.gecerliMi).isFalse()
        assertThat(sonuc.sorunlar.single().aciklama).contains("iki adet")
    }

    @Test
    fun `grup basligina miktar girilemez`() {
        val sonuc = MiktarEslestirici.eslestir(referans, listOf(satir(grup, deger("5"))))

        assertThat(sonuc.gecerliMi).isFalse()
        assertThat(sonuc.sorunlar.single().aciklama).contains("Grup başlığına")
    }

    @Test
    fun `fiyatlandirilmamis kaleme miktar girilemez`() {
        val sonuc = MiktarEslestirici.eslestir(referans, listOf(satir(fiyatsiz, deger("3"))))

        assertThat(sonuc.gecerliMi).isFalse()
        assertThat(sonuc.sorunlar.single().aciklama).contains("fiyatlandırılmamış")
    }

    @Test
    fun `ayni kalem iki kez gelirse reddedilir`() {
        val sonuc = MiktarEslestirici.eslestir(referans, listOf(satir(direk, deger("2")), satir(direk, deger("3"))))

        assertThat(sonuc.gecerliMi).isFalse()
        assertThat(sonuc.sorunlar.single().aciklama).contains("birden fazla kez")
    }

    /** Tek sorun bile tüm yüklemeyi düşürür: kısmi yükleme yok. */
    @Test
    fun `tek sorun tum yuklemeyi dusurur`() {
        val sonuc = MiktarEslestirici.eslestir(
            referans,
            listOf(satir(direk, deger("2")), satir(grup, deger("1"))),
        )

        assertThat(sonuc.gecerliMi).isFalse()
    }
}
