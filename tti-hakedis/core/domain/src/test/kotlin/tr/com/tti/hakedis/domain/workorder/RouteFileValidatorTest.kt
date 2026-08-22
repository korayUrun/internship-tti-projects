package tr.com.tti.hakedis.domain.workorder

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

class RouteFileValidatorTest {
    @Test
    fun `kmz ve kml kabul edilir`() {
        assertThat(RouteFileValidator.isAllowed("guzergah.kmz")).isTrue()
        assertThat(RouteFileValidator.isAllowed("guzergah.kml")).isTrue()
    }

    @Test
    fun `buyuk harfli uzanti kabul edilir`() {
        assertThat(RouteFileValidator.isAllowed("GUZERGAH.KMZ")).isTrue()
        assertThat(RouteFileValidator.isAllowed("Rota.Kml")).isTrue()
    }

    @Test
    fun `diger uzantilar reddedilir`() {
        assertThat(RouteFileValidator.isAllowed("guzergah.pdf")).isFalse()
        assertThat(RouteFileValidator.isAllowed("guzergah.kmz.txt")).isFalse()
        assertThat(RouteFileValidator.isAllowed("kmz")).isFalse()
        assertThat(RouteFileValidator.isAllowed("")).isFalse()
    }

    @Test
    fun `EK2 gorsel uzantilari kabul edilir`() {
        assertThat(AttachmentKind.IMAGE.accepts("saha.jpg")).isTrue()
        assertThat(AttachmentKind.IMAGE.accepts("saha.JPEG")).isTrue()
        assertThat(AttachmentKind.IMAGE.accepts("saha.png")).isTrue()
    }

    @Test
    fun `EK2 gorsel olmayan dosyalari reddeder`() {
        // Güzergâh dosyası EK2 kutusuna, görsel EK1 kutusuna gitmemeli: iki tür karışırsa hakediş
        // gönderimindeki "EK1 zorunlu" kontrolü yanlış dosyayı geçerli sayar.
        assertThat(AttachmentKind.IMAGE.accepts("guzergah.kmz")).isFalse()
        assertThat(AttachmentKind.IMAGE.accepts("rapor.pdf")).isFalse()
        assertThat(AttachmentKind.ROUTE_FILE.accepts("saha.jpg")).isFalse()
    }

    @Test
    fun `Turkce locale altinda da dogru calisir`() {
        val original = Locale.getDefault()
        try {
            // "KML".lowercase() Türkçe locale'de "kmı" üretirdi; doğrulama Locale.ROOT kullanmalı.
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertThat(RouteFileValidator.isAllowed("GUZERGAH.KML")).isTrue()
            // Uzantı çıkarımı sunucuda da aynı yerden geçiyor (EkServisi); tuzağı orada da kilitliyoruz.
            assertThat(FileExtension.of("DOSYA.KMZ")).isEqualTo("kmz")
        } finally {
            Locale.setDefault(original)
        }
    }
}
