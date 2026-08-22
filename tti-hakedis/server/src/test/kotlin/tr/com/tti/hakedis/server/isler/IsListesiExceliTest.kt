package tr.com.tti.hakedis.server.isler

import com.google.common.truth.Truth.assertThat
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Test
import tr.com.tti.hakedis.domain.document.DocumentStatus
import tr.com.tti.hakedis.domain.document.DocumentType
import tr.com.tti.hakedis.server.belge.BelgeOzetYaniti
import tr.com.tti.hakedis.server.cikti.CiktiEtiketleri
import java.math.BigDecimal
import java.time.Instant

/**
 * İş listesi Excel'inin kilitleri.
 *
 * Hepsi sessiz kırılabilecek şeyler: tutar metin hücresine düşerse Excel'de toplanamaz, tarih metin
 * olursa sıralama alfabetik olur, iptal edilmiş belge sayılırsa fark uydurma bir sapma gösterir.
 * Hiçbiri derleme hatası vermez ve dosya yine "çalışır" görünür.
 */
class IsListesiExceliTest {
    private val etiketler = CiktiEtiketleri()

    @Test
    fun `tutar ve tarih gercek hucre tipiyle yazilir`() {
        val sayfa = uret(listOf(is1()))

        val toplamHucresi = sayfa.getRow(1).getCell(SUTUN_FIZIBILITE_TOPLAM)
        assertThat(toplamHucresi.cellType).isEqualTo(CellType.NUMERIC)
        assertThat(BigDecimal(toplamHucresi.rawValue)).isEqualTo(BigDecimal("4413.72"))

        // Tarih de sayısal (Excel seri numarası); metin olsaydı tarihe göre sıralama bozulurdu.
        assertThat(sayfa.getRow(1).getCell(SUTUN_FIZIBILITE_ACILMA).cellType).isEqualTo(CellType.NUMERIC)
    }

    @Test
    fun `asama sutunlari tur adiyla oneklenir`() {
        val basliklar = uret(listOf(is1())).getRow(0).map { it.stringCellValue }

        assertThat(basliklar).contains("${etiketler.turFizibilite} ${etiketler.toplam}")
        assertThat(basliklar).contains("${etiketler.turKesif} ${etiketler.toplam}")
        assertThat(basliklar).contains("${etiketler.turHakedis} ${etiketler.toplam}")
        // Öneksiz "Durum" yalnızca işin kendi durumu için var; aşamalarınki öneklidir.
        assertThat(basliklar.count { it == etiketler.sutunDurum }).isEqualTo(0)
    }

    @Test
    fun `iptal edilmis belge ne satira ne farka girer`() {
        val kayit = is1(
            belgeler = listOf(
                ozet(DocumentType.FIZIBILITE, DocumentStatus.IPTAL, "1000.00"),
                ozet(DocumentType.KESIF, DocumentStatus.TAMAMLANDI, "2595.49"),
            ),
        )
        val sayfa = uret(listOf(kayit))
        val satir = sayfa.getRow(1)

        assertThat(satir.getCell(SUTUN_FIZIBILITE_TOPLAM)?.rawValue).isNull()
        assertThat(BigDecimal(satir.getCell(SUTUN_KESIF_TOPLAM).rawValue)).isEquivalentAccordingToCompareTo(BigDecimal("2595.49"))

        // Fark hücresi de BOŞ kalmalı. İptal edilmiş fizibilite "0" sayılsaydı fark 2595,49 çıkar ve
        // hiç yapılmamış bir karşılaştırma, süzgeçte gerçek bir sapmadan ayırt edilemezdi.
        assertThat(satir.getCell(SUTUN_FARK_FIZ_KESIF)?.rawValue).isNull()

        // Alt toplam satırı yoktur (müdür isteği, Ağustos 2026): verinin altına satır açılmaz.
        assertThat(sayfa.getRow(2)).isNull()
    }

    /**
     * Fark sütunları **sonraki − önceki**. Yön bir kez ters yazılsa dosya yine üretilir ve sayı yine
     * makul görünür; hangi aşamanın pahalıya kapandığı sessizce tersine döner.
     */
    @Test
    fun `asama farklari sonraki eksi onceki yazilir`() {
        val kayit = is1(
            belgeler = listOf(
                ozet(DocumentType.FIZIBILITE, DocumentStatus.TAMAMLANDI, "1000.00"),
                ozet(DocumentType.KESIF, DocumentStatus.TAMAMLANDI, "1250.55"),
                ozet(DocumentType.HAKEDIS, DocumentStatus.TAMAMLANDI, "1100.30"),
            ),
        )
        val satir = uret(listOf(kayit)).getRow(1)

        assertThat(BigDecimal(satir.getCell(SUTUN_FARK_FIZ_KESIF).rawValue))
            .isEquivalentAccordingToCompareTo(BigDecimal("250.55"))
        // Negatif fark: hakediş keşiften ucuza kapandı — işaret korunmalı.
        assertThat(BigDecimal(satir.getCell(SUTUN_FARK_KESIF_HAKEDIS).rawValue))
            .isEquivalentAccordingToCompareTo(BigDecimal("-150.25"))
    }

    /** Tek aşaması olan projede karşılaştıracak ikinci aşama yoktur; iki fark hücresi de boş kalır. */
    @Test
    fun `eksik asamada fark hucresi bos kalir`() {
        val satir = uret(listOf(is1())).getRow(1)

        assertThat(satir.getCell(SUTUN_FARK_FIZ_KESIF)?.rawValue).isNull()
        assertThat(satir.getCell(SUTUN_FARK_KESIF_HAKEDIS)?.rawValue).isNull()
    }

    /**
     * Kablosuz "Yeni Tesis" projesi (Faz W): kablo hücresi **boş** kalır ama sütun düzeni kaymaz —
     * sonraki sütunlar yerinde olmalı, yoksa dosya sessizce bir sütun sola kayardı.
     */
    @Test
    fun `kablosuz projede kablo hucresi bos kalir`() {
        val sayfa = uret(listOf(is1().copy(kableNosid = null, adresA = null, adresB = null)))
        val satir = sayfa.getRow(1)

        assertThat(satir.getCell(SUTUN_KABLE)).isNull()
        // Düzenin kaymadığının kanıtı: toplam hâlâ kendi sütununda ve sayısal.
        assertThat(satir.getCell(SUTUN_FIZIBILITE_TOPLAM).cellType).isEqualTo(CellType.NUMERIC)
    }

    @Test
    fun `bos listede yalnizca baslik satiri yazilir`() {
        val sayfa = uret(emptyList())

        assertThat(sayfa.getRow(0)).isNotNull()
        assertThat(sayfa.getRow(1)).isNull()
    }

    private fun uret(isler: List<IsYaniti>): XSSFSheet {
        val icerik = IsListesiExceli.uret(isler, mapOf("DEPLASE_IYILESTIRME" to "Deplase / İyileştirme"), etiketler)
        return XSSFWorkbook(icerik.inputStream()).getSheetAt(0)
    }

    private fun is1(belgeler: List<BelgeOzetYaniti> = listOf(ozet(DocumentType.FIZIBILITE, DocumentStatus.TAMAMLANDI, "4413.72"))) =
        IsYaniti(
            id = 1,
            bolgeKodu = "BURSA",
            bolgeAdi = "Bursa",
            kapsamKodu = "DEPLASE_IYILESTIRME",
            kableNosid = "mtc29627a.cable*ank-ank",
            adresA = "Ankara",
            adresB = "Bursa",
            spNo = "2026-BURSA-001",
            route = "Ankara - Bursa",
            ekAraligi = "12-42",
            yil = 2026,
            olusturma = AN,
            durum = IsDurumu.ISLENIYOR,
            durumMetni = "İşleniyor",
            iptalGerekce = null,
            belgeler = belgeler,
        )

    private fun ozet(tur: DocumentType, durum: DocumentStatus, toplam: String) = BelgeOzetYaniti(
        id = 1,
        belgeNo = "BURSA_2026_${tur.name}_001",
        tur = tur,
        durum = durum,
        durumMetni = "durum",
        olusturma = AN,
        gonderme = AN,
        tamamlanma = AN,
        sonGuncelleme = AN,
        sonGuncelleyenAdSoyad = "Bursa Bölge Sorumlusu",
        sonGuncelleyenRol = "BOLGE_SORUMLUSU",
        toplam = BigDecimal(toplam),
        toplamGosterim = toplam,
    )

    private companion object {
        val AN: Instant = Instant.parse("2026-08-04T04:35:00Z")

        // Sütun düzeni: 8 kimlik sütunu, ardından aşama başına 5 sütun (durum, açılma, değiştirilme,
        // güncelleyen, toplam). Test bu düzeni bilerek sabitliyor — kayarsa dosya sessizce bozulur.
        /** Kablo sütunu — `IsListesiExceli.SUTUN_KABLE` ile aynı olmak zorunda. */
        const val SUTUN_KABLE = 3
        const val SUTUN_FIZIBILITE_ACILMA = 9
        const val SUTUN_FIZIBILITE_TOPLAM = 12
        const val SUTUN_KESIF_TOPLAM = 17

        // Tablonun sonundaki iki fark sütunu: 8 kimlik + 3×5 aşama = 23 (iptal gerekçesi), sonra 24-25.
        const val SUTUN_FARK_FIZ_KESIF = 24
        const val SUTUN_FARK_KESIF_HAKEDIS = 25
    }
}
