package tr.com.tti.hakedis.server.sozlesme

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * "Sözleşme Formatını İndir" ile inen dosya, "Sözleşme Yükle" ile geri yüklenebilmeli.
 *
 * Şablonu `fastexcel` yazıyor, yüklemeyi Apache POI + `ContractXlsxParser` okuyor. Sütun adlarından
 * biri değişirse hiçbir derleme hatası çıkmaz; yönetici dosyayı doldurup yükleyene kadar da fark
 * edilmez. Bu test o sessiz kırılmayı kapatır.
 */
class SozlesmeSablonuTest {
    @Test
    fun `uretilen format sozlesme okuyucusundan gecer`() {
        val uyarilar = mutableListOf<String>()

        val contract = XlsxOkuyucu.sayfada(SozlesmeSablonu.uret(), sayfaAdi = null) { sayfa ->
            tr.com.tti.hakedis.tools.ContractXlsxParser.parse(sayfa, uyarilar)
        }

        // İki örnek satır: bir grup başlığı, bir kalem.
        assertThat(contract.items).hasSize(2)
        assertThat(contract.items.first().isGroup).isTrue()
        assertThat(contract.items.last().isGroup).isFalse()
    }

    @Test
    fun `basligi sablonun ilk satirindan alir`() {
        val contract = XlsxOkuyucu.sayfada(SozlesmeSablonu.uret(), sayfaAdi = null) { sayfa ->
            tr.com.tti.hakedis.tools.ContractXlsxParser.parse(sayfa, mutableListOf())
        }

        // 1. satır PDF başlığı olarak basılıyor (CLAUDE.md §4); yer tutucu metin bunu anlatıyor.
        assertThat(contract.meta.title).contains("Sözleşme başlığını buraya yazın")
    }

    /**
     * Başlıkların **okunur** olması kullanıcı isteğidir (18 Ağustos 2026): `ISC_POZNO` gibi teknik
     * adlar şablonu elle dolduran kişiye hiçbir şey anlatmıyordu. Ad değişirse burası düşer.
     */
    @Test
    fun `sablon okunur sutun adlari tasir`() {
        assertThat(SozlesmeSablonu.BASLIKLAR).containsExactly("Poz No", "İş Kalemi", "Birim", "Br Fiyat").inOrder()
    }
}
