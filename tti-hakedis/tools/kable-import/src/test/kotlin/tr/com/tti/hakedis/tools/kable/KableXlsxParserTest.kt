package tr.com.tti.hakedis.tools.kable

import com.google.common.truth.Truth.assertThat
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Envanter okuyucusunun davranışı — bir HTTP yükleme ucuna bağlandığı için (Faz U) artık testli
 * olmak zorunda. Excel bellekte kurulur; dosya sistemine yalnızca [ayni_dosya_akistan_ve_dosyadan_ayni_okunur]
 * dokunur.
 */
class KableXlsxParserTest {
    /** `cable` sayfasının okunan sütunları; sıra serbest olduğu için başlıklar da yazılır. */
    private val basliklar = listOf(
        "nosid", "Technical Consultant", "addressA", "addressB", "cityA", "cityB",
        "countryA", "cityA", "addressA", "buildingA", "floorA", "roomA", "cageA", "footprintA", "rackspaceA",
        "countryB", "cityB", "addressB", "buildingB", "floorB", "roomB", "cageB", "footprintB", "rackspaceB",
    ).distinct()

    /**
     * İki sayfalı bir envanter kurar.
     *
     * [kableSatirlari] her biri sütun adı → değer eşlemesi; verilmeyen sütun boş bırakılır.
     * [sahaEslesmesi] `Saha` sayfası: danışman → saha adı.
     */
    private fun envanter(
        kableSatirlari: List<Map<String, String>>,
        sahaEslesmesi: Map<String, String> = mapOf("Ali Veli" to "İstanbul Avp"),
    ): ByteArray {
        val kitap = XSSFWorkbook()

        val saha = kitap.createSheet("Saha")
        saha.createRow(0).also {
            it.createCell(0).setCellValue("Technical Consultant")
            it.createCell(1).setCellValue("Saha")
        }
        sahaEslesmesi.entries.forEachIndexed { indeks, (danisman, sahaAdi) ->
            saha.createRow(indeks + 1).also {
                it.createCell(0).setCellValue(danisman)
                it.createCell(1).setCellValue(sahaAdi)
            }
        }

        val cable = kitap.createSheet("cable")
        cable.createRow(0).also { satir ->
            basliklar.forEachIndexed { sutun, ad -> satir.createCell(sutun).setCellValue(ad) }
        }
        kableSatirlari.forEachIndexed { indeks, degerler ->
            val satir = cable.createRow(indeks + 1)
            degerler.forEach { (ad, deger) ->
                val sutun = basliklar.indexOf(ad)
                check(sutun >= 0) { "Test başlıkları arasında yok: $ad" }
                satir.createCell(sutun).setCellValue(deger)
            }
        }

        return ByteArrayOutputStream().use { cikti ->
            kitap.use { it.write(cikti) }
            cikti.toByteArray()
        }
    }

    private val tamSatir = mapOf(
        "nosid" to "mtc-0001",
        "Technical Consultant" to "Ali Veli",
        "addressA" to "AND Plaza - Umut Sokak 10-12",
        "countryA" to "Turkiye",
        "cityA" to "Istanbul",
        "buildingA" to "AND Plaza",
        "floorA" to "Ground Floor",
        "roomA" to "System Room",
        "addressB" to "Kocnet POP",
        "countryB" to "Turkiye",
        "cityB" to "Izmir",
    )

    @Test
    fun `konum yolu hiyerarsik olarak kurulur ve bina kat tek segmentte birlesir`() {
        val satirlar = KableXlsxParser.parse(envanter(listOf(tamSatir)).inputStream())

        assertThat(satirlar).hasSize(1)
        assertThat(satirlar.single().adresA).isEqualTo(
            "Turkiye >> Istanbul >> AND Plaza - Umut Sokak 10-12 >> AND Plaza > Ground Floor >> System Room",
        )
        assertThat(satirlar.single().sahaAdi).isEqualTo("İstanbul Avp")
    }

    /** Kayıtların çoğunda bina/kat/raf boş; kısa yol normaldir, eksiklik değil. */
    @Test
    fun `bos segmentler atlanir`() {
        val satirlar = KableXlsxParser.parse(
            envanter(
                listOf(
                    mapOf(
                        "nosid" to "mtc-0002",
                        "Technical Consultant" to "Ali Veli",
                        "addressA" to "Sadece adres",
                        "countryA" to "Turkiye",
                    ),
                ),
            ).inputStream(),
        )

        assertThat(satirlar.single().adresA).isEqualTo("Turkiye >> Sadece adres")
        assertThat(satirlar.single().adresB).isNull()
    }

    /**
     * Sunucu (`MultipartFile`) akıştan, CLI dosyadan okur. İkisi ayrışırsa web'den yüklenen dosya
     * ile araçtan yüklenen dosya farklı envanter üretirdi ve hiçbir hata çıkmazdı.
     */
    @Test
    fun `ayni dosya akistan ve dosyadan ayni okunur`() {
        val icerik = envanter(listOf(tamSatir))
        val gecici = File.createTempFile("kable-test", ".xlsx").apply { deleteOnExit() }
        gecici.writeBytes(icerik)

        assertThat(KableXlsxParser.parse(gecici)).isEqualTo(KableXlsxParser.parse(icerik.inputStream()))
    }

    @Test
    fun `eslesme tablosunda olmayan danisman hata verir`() {
        val hata = runCatching {
            KableXlsxParser.parse(
                envanter(listOf(tamSatir + ("Technical Consultant" to "Tanımsız Kişi"))).inputStream(),
            )
        }.exceptionOrNull()

        assertThat(hata).isInstanceOf(KableParseException::class.java)
        assertThat(hata).hasMessageThat().contains("Tanımsız Kişi")
    }

    @Test
    fun `tekrar eden nosid hic yazmadan durdurur`() {
        val hata = runCatching {
            KableXlsxParser.parse(envanter(listOf(tamSatir, tamSatir)).inputStream())
        }.exceptionOrNull()

        assertThat(hata).isInstanceOf(KableParseException::class.java)
        assertThat(hata).hasMessageThat().contains("mtc-0001")
    }

    @Test
    fun `adressiz satir eksik veridir ve sessizce gecmez`() {
        val hata = runCatching {
            KableXlsxParser.parse(envanter(listOf(tamSatir - "addressA")).inputStream())
        }.exceptionOrNull()

        assertThat(hata).isInstanceOf(KableParseException::class.java)
        assertThat(hata).hasMessageThat().contains("addressA")
    }
}
