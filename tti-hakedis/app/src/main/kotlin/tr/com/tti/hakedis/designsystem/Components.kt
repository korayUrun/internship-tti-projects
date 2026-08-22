package tr.com.tti.hakedis.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tr.com.tti.hakedis.R

/**
 * Birden fazla ekranda kullanılan küçük parçalar. Web'deki `bilesen.tsx`'in karşılığı; renkler ve
 * ölçüler §8 paletinden gelir, hiçbir ekran kendi rengini yazmaz.
 */

/**
 * Başlık çubuğu (TopAppBar) renkleri — konteyner gövdeyle **aynı** (`Background`), böylece üstte
 * beyaz bir şerit kalmaz; ekran tek renk görünür (kullanıcı kararı, 13 Ağustos 2026). Tüm ekranların
 * TopAppBar'ı bunu kullanır — 48/56dp gibi bu da tek enforcement noktası, hiçbir ekran kendi rengini yazmaz.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun appTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Background,
    scrolledContainerColor = Background,
)

/**
 * Sunucudan gelen hata mesajı **olduğu gibi** basılır — mobil mesajı yeniden yazmaz.
 *
 * Mesaj boşsa genel metne düşülür: hatanın kendisi yutulup kullanıcıya boş bir kutu göstermek,
 * hiç göstermemekten kötüdür. Yedek metin burada, tek yerde duruyor (CLAUDE.md §1/6).
 */
@Composable
fun ErrorBanner(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Banner(background = ErrorSoft, border = ErrorRed, modifier = modifier) {
        Text(
            text = message.ifBlank { stringResource(R.string.error_generic) },
            style = MaterialTheme.typography.bodyLarge,
            color = OnSurface,
        )
        if (onRetry != null) {
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = Spacing.minTouchTarget)) {
                Text(text = stringResource(R.string.action_retry))
            }
        }
    }
}

@Composable
fun InfoBanner(message: String, modifier: Modifier = Modifier) {
    Banner(background = PrimarySoft, border = Primary, modifier = modifier) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge, color = OnSurface)
    }
}

@Composable
private fun Banner(background: Color, border: Color, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(Spacing.sm))
            .border(1.dp, border, RoundedCornerShape(Spacing.sm))
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        content = content,
    )
}

/**
 * Durum rozeti. Metin **sunucudan** gelir (`durumMetni`); mobil durum metni yazmaz (E2).
 *
 * [tone] yalnızca rengi seçer. Eskiden her rozet aynı maviydi ve "Taslak", "Gönderildi", "İptal"
 * birbirinden ayırt edilemiyordu.
 */
@Composable
fun StatusBadge(text: String, tone: StatusTone, modifier: Modifier = Modifier) {
    val renkler = tone.colors()
    Surface(
        color = renkler.bg,
        shape = RoundedCornerShape(Spacing.md),
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = renkler.fg,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
    }
}

/** Etiket → değer satırı; iş ve belge bilgilerinin ortak gösterimi. */
@Composable
fun LabelValue(label: String, value: String?, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            // İki nokta burada ekleniyor, `strings.xml`'de değil: aynı dizeler `OutlinedTextField`
            // etiketi olarak da kullanılıyor (`NewWorkOrderScreen`) ve orada iki nokta yanlış olur.
            text = etiketle(label),
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceMut,
            modifier = Modifier.heightIn(min = Spacing.md),
        )
        Text(
            // Boş değer `—` basar: kablosuz "Yeni Tesis" projesinde kablo ve uçlar boş olabiliyor
            // (Faz W) ve etiketi değersiz bırakmak satırı bozuk gösteriyordu.
            text = value?.takeIf { it.isNotBlank() } ?: stringResource(R.string.bos_deger),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = OnSurface,
        )
    }
}

/** Başlıklı kart — web'deki `.kart` karşılığı. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    /** Sol vurgu şeridi rengi — verilirse kartın durumu kenardan, metne bakmadan okunur. */
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = Surface,
        shape = RoundedCornerShape(Spacing.sm),
        border = BorderStroke(1.dp, if (highlighted) ErrorRed else Outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        // Sol vurgu şeridi `drawBehind` ile çizilir; eskiden `Row(IntrinsicSize.Min)` + `fillMaxHeight`
        // kutu kullanılıyordu ama intrinsic ölçüm, kart içindeki `BoxWithConstraints`/`FieldColumns`
        // gibi SubcomposeLayout tabanlı bileşenlerde çöküyordu ("intrinsic measurements not supported").
        Column(
            modifier = Modifier
                .then(
                    if (accent != null) {
                        Modifier.drawBehind {
                            drawRect(color = accent, size = Size(ACCENT_WIDTH.toPx(), size.height))
                        }
                    } else {
                        Modifier
                    },
                )
                .padding(
                    start = Spacing.md + if (accent != null) ACCENT_WIDTH else 0.dp,
                    top = Spacing.md,
                    end = Spacing.md,
                    bottom = Spacing.md,
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

private val ACCENT_WIDTH = 4.dp

/**
 * Etiket → değer gösterimlerinde etiketin sonuna iki nokta koyar.
 *
 * `strings.xml`'e yazılmıyor: aynı dizeler form alanı etiketi olarak da kullanılıyor
 * (`OutlinedTextField(label = …)`) ve orada iki nokta yanlış. Zaten iki nokta ile bitenlere ikinci
 * kez eklenmez.
 */
fun etiketle(label: String): String = if (label.endsWith(':')) label else "$label:"

@Composable
fun LoadingBox(text: String = stringResource(R.string.loading), modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            CircularProgressIndicator(modifier = Modifier.heightIn(max = PROGRESS_SIZE), strokeWidth = 2.dp)
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private val PROGRESS_SIZE = 24.dp

// ---- Form yapı taşları — 48/56dp ve maks. genişlik kuralları TEK YERDE (CLAUDE.md §8) ----------

/**
 * Standart metin girişi. 56dp yükseklik + `bodyLarge` + tam genişlik; kural burada tanımlı, çağrı
 * yerlerinde bir daha tekrarlanmaz. Hata [isError]/[supportingText] ile gösterilir.
 */
@Composable
fun AppTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        placeholder = placeholder?.let { { Text(text = it) } },
        enabled = enabled,
        isError = isError,
        supportingText = supportingText?.let { { Text(text = it, color = MaterialTheme.colorScheme.error) } },
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        textStyle = MaterialTheme.typography.bodyLarge,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.fieldHeight),
    )
}

/** Birincil eylem butonu — 48dp dokunma, `labelLarge`. Genişlik [modifier] ile verilir (ör. fillMaxWidth). */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = Spacing.minTouchTarget),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** İkincil (çerçeveli) eylem butonu — aynı 48dp kuralı. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = Spacing.minTouchTarget),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Form içeriğini yatayda ortalar ve [Spacing.formMaxWidth] ile sınırlar — tablet yatayda içerik
 * ekranı baştan sona doldurup okunmaz olmasın. Dikey kaydırma/padding çağıran ekrandadır.
 */
@Composable
fun FormContainer(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(Spacing.md),
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = Spacing.formMaxWidth),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

/**
 * Eşli alanları yan yana iki sütuna koyar; ekran dar olduğunda (portrait / küçük cihaz) alt alta
 * **akar** — tek mantık, çalışma-zamanı ikinci ağaç yok (CLAUDE.md §8). Geniş tabletde iki sütun.
 */
@Composable
fun FieldColumns(
    modifier: Modifier = Modifier,
    columnMinWidth: Dp = COLUMN_MIN_WIDTH,
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth >= columnMinWidth * 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    content = left,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    content = right,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                left()
                right()
            }
        }
    }
}

private val COLUMN_MIN_WIDTH = 320.dp
