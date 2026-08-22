package tr.com.tti.hakedis.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Dört anlamsal durum ailesi (CLAUDE.md §8) — webdeki `durumAilesi` ile birebir aynı bölümleme.
 *
 * Aile yalnızca **rengi** seçer. ⚠️ Durum metni her zaman sunucudan gelir (`durumMetni`); uygulama
 * hiçbir yerde durum metni yazmaz.
 *
 * Bu dosya DTO tanımaz — `designsystem` yaprak modül olarak kalıyor. Enum eşlemesi
 * `feature/StatusTones.kt`'de.
 */
enum class StatusTone { ACIK, ISLENIYOR, TAMAM, IPTAL }

/** Rozet zemini · rozet metni · vurgu (kart şeridi, stepper dairesi). */
data class ToneColors(val bg: Color, val fg: Color, val accent: Color)

fun StatusTone.colors(): ToneColors = when (this) {
    StatusTone.ACIK -> ToneColors(bg = PrimarySoft, fg = PrimaryDark, accent = Primary)
    StatusTone.ISLENIYOR -> ToneColors(bg = WarningSoft, fg = WarningText, accent = WarningBorder)
    StatusTone.TAMAM -> ToneColors(bg = SuccessSoft, fg = SuccessText, accent = Success)
    StatusTone.IPTAL -> ToneColors(bg = ErrorSoft, fg = ErrorRed, accent = ErrorRed)
}
