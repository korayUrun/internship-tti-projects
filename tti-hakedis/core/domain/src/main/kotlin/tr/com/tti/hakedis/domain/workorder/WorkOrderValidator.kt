package tr.com.tti.hakedis.domain.workorder

/** Ekran 1'deki alanlar. Hata mesajı metinleri `:app` katmanında `strings.xml`'den gelir. */
enum class WorkOrderField {
    REGION,
    SCOPE,
    ROUTE,
    EK_ARALIGI,
    KABLE_ID,
    UC_A,
    UC_B,
}

/** Alan bazlı hata tipi. Şimdilik tek tip var; yeni kural gelirse buraya eklenir. */
enum class WorkOrderError {
    REQUIRED,
}

data class WorkOrderValidation(val errors: Map<WorkOrderField, WorkOrderError>) {
    val isValid: Boolean get() = errors.isEmpty()
}

/**
 * Bölge, kapsam, route ve ek aralığı **her zaman** zorunludur. ~~Sözleşme~~ Faz N'de çıktı (sözleşme
 * artık işin değil, her belgenin kendi seçimi), ~~SP No~~ Faz O'da (sunucu üretiyor). Metin alanları
 * `trim` edilerek boşluk-dolu girişler reddedilir. Hiçbir alan otomatik düzeltilmez — sadece raporlanır.
 *
 * **Kablo kapsama bağlıdır** (Faz W): `Scope.kableZorunluMu`. Kapsam henüz seçilmemişse kablo da
 * zorunlu sayılır — aksi hâlde kapsamsız bir istek kabloyu da atlayarak iki hata yerine bir hata
 * üretirdi ve kullanıcı ikinci eksiği ancak kapsamı seçtikten sonra görürdü.
 *
 * **Uçlar da kapsama bağlıdır** *(12 Ağustos 2026)*: `Scope.uclarZorunluMu` (yalnızca Yeni Tesis)
 * olduğunda Uç A ve Uç B'de **ülke, şehir ve adres** dolu olmalı. Envanterli kapsamlarda uçlar zaten
 * envanterden gelir; orada elle segment aranmaz.
 */
object WorkOrderValidator {
    /**
     * Yeni Tesis'te bir uçta dolu olması gereken segmentler. Anahtarlar `KonumYolu.ALANLAR` ile aynı
     * (`:tools` bu modüle bağımlı değil, o yüzden burada sabit; anlam sözleşmesi tek: ülke/şehir/adres).
     */
    private val ZORUNLU_UC_ALANLARI = listOf("country", "city", "address")

    fun validate(workOrder: WorkOrder): WorkOrderValidation {
        val errors = buildMap {
            if (workOrder.region == null) put(WorkOrderField.REGION, WorkOrderError.REQUIRED)
            if (workOrder.scope == null) put(WorkOrderField.SCOPE, WorkOrderError.REQUIRED)
            if (workOrder.route.isBlank()) put(WorkOrderField.ROUTE, WorkOrderError.REQUIRED)
            if (workOrder.ekAraligi.isBlank()) put(WorkOrderField.EK_ARALIGI, WorkOrderError.REQUIRED)
            if (workOrder.scope?.kableZorunluMu != false && workOrder.kableId.isBlank()) {
                put(WorkOrderField.KABLE_ID, WorkOrderError.REQUIRED)
            }
            if (workOrder.scope?.uclarZorunluMu == true) {
                if (!ucTamMi(workOrder.ucASegmentleri)) put(WorkOrderField.UC_A, WorkOrderError.REQUIRED)
                if (!ucTamMi(workOrder.ucBSegmentleri)) put(WorkOrderField.UC_B, WorkOrderError.REQUIRED)
            }
        }
        return WorkOrderValidation(errors)
    }

    /** Bir uçta zorunlu üç segmentin (ülke/şehir/adres) hepsi dolu mu? */
    private fun ucTamMi(segmentler: Map<String, String>): Boolean = ZORUNLU_UC_ALANLARI.all { !segmentler[it].isNullOrBlank() }
}
