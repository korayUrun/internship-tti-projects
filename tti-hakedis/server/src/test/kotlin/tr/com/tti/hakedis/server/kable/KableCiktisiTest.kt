package tr.com.tti.hakedis.server.kable

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import tr.com.tti.hakedis.tools.kable.KableXlsxParser

/**
 * "Envanteri İndir" ile inen dosya, "Envanter Yükle" ile geri yüklenebilmeli ve **hiçbir değişiklik
 * üretmemeli**.
 *
 * Çıktıyı `fastexcel` yazıyor, yüklemeyi Apache POI + `KableXlsxParser` okuyor. Sütun adlarından
 * biri değişirse derleme hatası çıkmaz; yönetici dosyayı indirip geri yüklediğinde envanterin
 * eridiğini görene kadar da fark edilmez (dosyada olmayan her kablo pasifleşir). Bu test o sessiz
 * kırılmayı kapatır — `SozlesmeSablonuTest` ile aynı gerekçe.
 */
class KableCiktisiTest {
    private val satirlar = listOf(
        KableCiktiSatiri(
            nosid = "mtc-00001",
            danismanAd = "Ahmet Yilmaz",
            sahaAdi = "İzmir",
            adresA = "Turkiye >> Izmir >> Konak POP >> AND Plaza > Ground Floor >> System Room",
            adresB = "Turkiye >> Izmir >> Bornova ODF",
            sehirA = "Izmir",
            sehirB = "Izmir",
        ),
        // B ucu ve şehirleri boş: envanterin kendisinde de yaygın, parser bunu hata saymamalı.
        KableCiktiSatiri(
            nosid = "mtc-00002",
            danismanAd = "Ayse Kaya",
            sahaAdi = "Bursa",
            adresA = "Turkiye >> Bursa >> Merkez POP",
            adresB = null,
            sehirA = null,
            sehirB = null,
        ),
    )

    @Test
    fun `uretilen envanter kable okuyucusundan gecer`() {
        val okunan = KableXlsxParser.parse(KableCiktisi.uret(satirlar).inputStream())

        assertThat(okunan.map { it.nosid }).containsExactly("mtc-00001", "mtc-00002").inOrder()
    }

    /**
     * Asıl iddia: gidiş-dönüş **birebir aynı** veriyi üretiyor. Konum yolu tek hücreye yazılıp
     * segment sütunları boş bırakıldığı için parser aynı yolu yeniden kuruyor — boş segmentleri
     * atlama kuralı (`KonumYolu`) bunu mümkün kılan şey.
     */
    @Test
    fun `gidis donus adresleri degistirmez`() {
        val okunan = KableXlsxParser.parse(KableCiktisi.uret(satirlar).inputStream()).associateBy { it.nosid }

        val ilk = okunan.getValue("mtc-00001")
        assertThat(ilk.adresA).isEqualTo(satirlar[0].adresA)
        assertThat(ilk.adresB).isEqualTo(satirlar[0].adresB)
        assertThat(ilk.sehirA).isEqualTo("Izmir")
        assertThat(ilk.danismanAd).isEqualTo("Ahmet Yilmaz")
        assertThat(ilk.sahaAdi).isEqualTo("İzmir")

        val ikinci = okunan.getValue("mtc-00002")
        assertThat(ikinci.adresB).isNull()
        assertThat(ikinci.sehirA).isNull()
    }
}
