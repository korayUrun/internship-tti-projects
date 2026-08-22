package tr.com.tti.hakedis.domain.workorder

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorkOrderValidatorTest {
    private val doluKayit = WorkOrder(
        region = Region.IST_AVR,
        scope = Scope.ARIZA_SONRASI_ONARIM,
        contractId = "2026-guvenay-rota-1",
        spNo = "SP-12345",
        route = "Rota-1",
        ekAraligi = "EK-4 / EK-5",
        kableId = "KBL-987",
    )

    /** Bir ucun zorunlu üç segmenti dolu (Yeni Tesis testleri için). */
    private val tamUc = mapOf("country" to "Turkiye", "city" to "Izmir", "address" to "Konak POP")

    @Test
    fun `tum alanlar doluysa gecerli`() {
        val sonuc = WorkOrderValidator.validate(doluKayit)

        assertThat(sonuc.isValid).isTrue()
        assertThat(sonuc.errors).isEmpty()
    }

    /**
     * Boş kayıt: kapsam `null` olduğu için uç alanları (yalnızca Yeni Tesis'e ait) işaretlenmez;
     * uygulanan beş alan tek tek çıkar. UC_A/UC_B'nin kapsama bağlılığı ayrı testlerde.
     */
    @Test
    fun `bos kayitta uygulanan her alan tek tek isaretlenir`() {
        val sonuc = WorkOrderValidator.validate(WorkOrder())

        assertThat(sonuc.isValid).isFalse()
        assertThat(sonuc.errors.keys).containsExactly(
            WorkOrderField.REGION,
            WorkOrderField.SCOPE,
            WorkOrderField.ROUTE,
            WorkOrderField.EK_ARALIGI,
            WorkOrderField.KABLE_ID,
        )
    }

    @Test
    fun `sadece bosluk iceren metin alani bos sayilir`() {
        val sonuc = WorkOrderValidator.validate(doluKayit.copy(route = "   "))

        assertThat(sonuc.errors).containsExactly(WorkOrderField.ROUTE, WorkOrderError.REQUIRED)
    }

    @Test
    fun `secilmemis bolge hata verir`() {
        val sonuc = WorkOrderValidator.validate(doluKayit.copy(region = null))

        assertThat(sonuc.errors).containsExactly(WorkOrderField.REGION, WorkOrderError.REQUIRED)
    }

    /** Sözleşme Faz N'de işten belgeye taşındı: iş açılırken artık sorulmuyor. */
    @Test
    fun `sozlesmesiz kayit gecerlidir`() {
        val sonuc = WorkOrderValidator.validate(doluKayit.copy(contractId = null))

        assertThat(sonuc.isValid).isTrue()
    }

    @Test
    fun `birden fazla eksik alan ayni anda raporlanir`() {
        val sonuc = WorkOrderValidator.validate(doluKayit.copy(route = "", kableId = ""))

        assertThat(sonuc.errors.keys).containsExactly(WorkOrderField.ROUTE, WorkOrderField.KABLE_ID)
    }

    // --- Kablo kapsama bağlı (Faz W) -----------------------------------------------------------

    /**
     * "Yeni Tesis" işi kablo ile ilgilidir ama belirli bir kablo kimliğinin kapsamında değildir;
     * kablo seçimi zorunlu değil. Uçlar (ülke/şehir/adres) dolu olduğunda kayıt geçerlidir.
     */
    @Test
    fun `yeni tesiste kablosuz ama uclari dolu kayit gecerlidir`() {
        val sonuc = WorkOrderValidator.validate(
            doluKayit.copy(scope = Scope.YENI_TESIS, kableId = "", ucASegmentleri = tamUc, ucBSegmentleri = tamUc),
        )

        assertThat(sonuc.isValid).isTrue()
    }

    /** Yeni Tesis'te uçlar **zorunlu** (12 Ağustos 2026): boş bırakılınca iki uç da işaretlenir. */
    @Test
    fun `yeni tesiste bos uclar UC_A ve UC_B isaretlenir`() {
        val sonuc = WorkOrderValidator.validate(doluKayit.copy(scope = Scope.YENI_TESIS, kableId = ""))

        assertThat(sonuc.errors.keys).containsExactly(WorkOrderField.UC_A, WorkOrderField.UC_B)
    }

    /** Bir uçta zorunlu üç segmentten biri (adres) eksikse yalnızca o uç işaretlenir. */
    @Test
    fun `yeni tesiste eksik segmentli uc isaretlenir`() {
        val sonuc = WorkOrderValidator.validate(
            doluKayit.copy(
                scope = Scope.YENI_TESIS,
                kableId = "",
                ucASegmentleri = tamUc,
                ucBSegmentleri = mapOf("country" to "Turkiye", "city" to "Izmir"), // adres yok
            ),
        )

        assertThat(sonuc.errors).containsExactly(WorkOrderField.UC_B, WorkOrderError.REQUIRED)
    }

    /** Envanterli kapsamlarda uç segmentleri aranmaz (uçlar envanterden gelir). */
    @Test
    fun `envanterli kapsamda bos uclar sorun degil`() {
        val sonuc = WorkOrderValidator.validate(doluKayit.copy(scope = Scope.DEPLASE_IYILESTIRME))

        assertThat(sonuc.isValid).isTrue()
    }

    /** Muafiyet **yalnızca** Yeni Tesis'e ait; diğer dört kapsamda kablo hâlâ zorunlu. */
    @Test
    fun `diger butun kapsamlarda kablo zorunlu`() {
        val zorunluKapsamlar = Scope.entries.filter { it != Scope.YENI_TESIS }

        for (kapsam in zorunluKapsamlar) {
            val sonuc = WorkOrderValidator.validate(doluKayit.copy(scope = kapsam, kableId = ""))

            assertThat(sonuc.errors).containsExactly(WorkOrderField.KABLE_ID, WorkOrderError.REQUIRED)
        }
        assertThat(Scope.entries.filterNot { it.kableZorunluMu }).containsExactly(Scope.YENI_TESIS)
    }

    /**
     * Kapsam seçilmemişken kablo **zorunlu sayılır**: aksi hâlde kapsamsız bir istek kabloyu da
     * atlayarak iki hata yerine bir hata üretir ve kullanıcı ikinci eksiği ancak kapsamı seçtikten
     * sonra görürdü. `bos kayitta her alan tek tek isaretlenir` testi bunu zaten kilitliyor.
     */
    @Test
    fun `kapsamsiz kayitta kablo da eksik sayilir`() {
        val sonuc = WorkOrderValidator.validate(doluKayit.copy(scope = null, kableId = ""))

        assertThat(sonuc.errors.keys).containsExactly(WorkOrderField.SCOPE, WorkOrderField.KABLE_ID)
    }
}
