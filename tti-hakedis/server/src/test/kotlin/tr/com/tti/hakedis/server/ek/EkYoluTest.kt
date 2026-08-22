package tr.com.tti.hakedis.server.ek

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * Yüklenen dosyaların depo yolu.
 *
 * İki şey kilitleniyor: (a) aynı adla yüklenen ikinci görselin birincinin üzerine yazmaması,
 * (b) kullanıcının verdiği adın dizin dışına çıkamaması.
 */
class EkYoluTest {
    @Test
    fun `ek yolu bolge yil belge agacinda`() {
        val yol = EkYolu.ek("IST-AVR", 2026, "IST-AVR_2026_HAKEDIS_001", EkTuru.EK1_ROTA, ekId = 42, dosyaAdi = "guzergah.kmz")

        assertThat(yol).isEqualTo("IST-AVR/2026/IST-AVR_2026_HAKEDIS_001/EK1_42_guzergah.kmz")
    }

    @Test
    fun `ayni adla yuklenen ikinci gorsel birincinin uzerine yazmaz`() {
        // Tabletten gelen fotoğrafların adı sıklıkla aynı olur (IMG_0001.jpg); ek kimliği ayırır.
        val ilk = EkYolu.ek("BURSA", 2026, "BURSA_2026_HAKEDIS_003", EkTuru.EK2_GORSEL, ekId = 7, dosyaAdi = "IMG_0001.jpg")
        val ikinci = EkYolu.ek("BURSA", 2026, "BURSA_2026_HAKEDIS_003", EkTuru.EK2_GORSEL, ekId = 8, dosyaAdi = "IMG_0001.jpg")

        assertThat(ilk).isNotEqualTo(ikinci)
    }

    @Test
    fun `dizin disina cikmaya calisan dosya adi etkisiz kalir`() {
        val yol = EkYolu.ek("IZMIR", 2026, "IZMIR_2026_HAKEDIS_001", EkTuru.EK2_GORSEL, ekId = 5, dosyaAdi = "../../../etc/passwd")

        // `/` ve `..` sadeleştirmede düşüyor; geriye kalan nokta yığını da gövdeyi boşalttığı için
        // yedek ada dönüyor. Sonuç: yol belgenin klasöründen çıkamıyor.
        assertThat(yol).isEqualTo("IZMIR/2026/IZMIR_2026_HAKEDIS_001/EK2_5_dosya.etcpasswd")
        assertThat(yol).doesNotContain("../")
    }

    @Test
    fun `Turkce karakterli ad ASCII'ye cevrilir`() {
        val yol = EkYolu.ek("KUTAHYA", 2026, "KUTAHYA_2026_HAKEDIS_002", EkTuru.EK1_ROTA, ekId = 9, dosyaAdi = "güzergâh şeması.kml")

        assertThat(yol).endsWith("EK1_9_guzergah_semasi.kml")
    }

    @Test
    fun `sadelestirme adi tamamen yerse uzantili yedek ad uretilir`() {
        val yol = EkYolu.ek("ANKARA", 2026, "ANKARA_2026_HAKEDIS_001", EkTuru.EK2_GORSEL, ekId = 3, dosyaAdi = "图片.png")

        assertThat(yol).endsWith("EK2_3_dosya.png")
    }

    @Test
    fun `Turkce locale altinda da ayni yol uretilir`() {
        val onceki = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))

            // `I` → `ı` katlaması varsayılan locale ile yola sızarsa dosya başka bir klasöre yazılır
            // ve eski ekler kaybolur (CLAUDE.md §5 locale tuzağı).
            assertThat(EkYolu.ek("IZMIR", 2026, "IZMIR_2026_HAKEDIS_001", EkTuru.EK1_ROTA, ekId = 1, dosyaAdi = "IMG.kml"))
                .isEqualTo("IZMIR/2026/IZMIR_2026_HAKEDIS_001/EK1_1_IMG.kml")
        } finally {
            Locale.setDefault(onceki)
        }
    }
}
