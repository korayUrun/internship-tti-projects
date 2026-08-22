package tr.com.tti.hakedis.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Dinamik renk (Material You) bilinçli olarak kapalı: kurumsal kimlik sabit kalmalı.
 * Dark theme kapsam dışı — lacivert bir **yüzeydir**, koyu tema değil (CLAUDE.md §8).
 *
 * ⚠️ **Eşlenmeyen her yuva M3'ün varsayılan mor tonunu basar** ve hata derlemede değil ekranda
 * çıkar. Düzeltme Turu 4'te iki tanesi canlı hataydı:
 * - `outlineVariant` → `HorizontalDivider`'ın varsayılan rengi; her proje kartındaki ayraç
 *   `#CAC4D0` gri-mor basıyordu.
 * - `secondaryContainer` → `NavigationBarItem`'ın seçili hap rengi; alt sekme çubuğu mor hapla
 *   çıkacaktı.
 * Bu yüzden aşağıdaki liste "kullandıklarımız" değil, **M3'ün okuduğu tüm yuvalar**dır.
 */
private val HakedisColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Surface,
    primaryContainer = PrimarySoft,
    onPrimaryContainer = PrimaryDark,
    secondary = PrimaryDark,
    onSecondary = Surface,
    secondaryContainer = PrimarySoft,
    onSecondaryContainer = PrimaryDark,
    tertiary = Success,
    onTertiary = Surface,
    tertiaryContainer = SuccessSoft,
    onTertiaryContainer = SuccessText,
    background = Background,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = DisabledBg,
    onSurfaceVariant = OnSurfaceMut,
    surfaceContainerLowest = Surface,
    surfaceContainerLow = Surface,
    surfaceContainer = Background,
    surfaceContainerHigh = DisabledBg,
    surfaceContainerHighest = DisabledBg,
    outline = Outline,
    outlineVariant = Outline,
    error = ErrorRed,
    onError = Surface,
    errorContainer = ErrorSoft,
    onErrorContainer = ErrorRed,
    inverseSurface = NavSurface,
    inverseOnSurface = NavOnSurface,
    inversePrimary = PrimarySoft,
    scrim = NavSurface,
)

@Composable
fun HakedisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HakedisColorScheme,
        typography = HakedisTypography,
        content = content,
    )
}
