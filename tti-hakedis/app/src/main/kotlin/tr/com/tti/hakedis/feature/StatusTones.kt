package tr.com.tti.hakedis.feature

import tr.com.tti.hakedis.data.api.WorkOrderStatusDto
import tr.com.tti.hakedis.designsystem.StatusTone
import tr.com.tti.hakedis.domain.document.DocumentStatus

/**
 * DTO durum kodu → renk ailesi. Bölümleme webdeki `durumAilesi` ile **birebir aynı** olmak zorunda;
 * ayrışırsa aynı kayıt iki arayüzde iki renkte görünür.
 *
 * `designsystem` yerine burada: o modül DTO tanımıyor, yaprak kalıyor.
 */
fun DocumentStatus.tone(): StatusTone = when (this) {
    // Yeknesek dört durum (Ağustos 2026). Bölümleme webdeki `durumAilesi` ile birebir aynı.
    DocumentStatus.ONAY_BEKLIYOR -> StatusTone.ACIK
    DocumentStatus.ISLENIYOR -> StatusTone.ISLENIYOR
    DocumentStatus.TAMAMLANDI -> StatusTone.TAMAM
    DocumentStatus.IPTAL -> StatusTone.IPTAL
}

/** `null` = henüz aşama açılmamış talep — onay bekleyenle aynı aile. */
fun WorkOrderStatusDto?.tone(): StatusTone = when (this) {
    null, WorkOrderStatusDto.ONAY_BEKLIYOR -> StatusTone.ACIK
    WorkOrderStatusDto.ISLENIYOR -> StatusTone.ISLENIYOR
    WorkOrderStatusDto.TAMAMLANDI -> StatusTone.TAMAM
    WorkOrderStatusDto.IPTAL -> StatusTone.IPTAL
}
