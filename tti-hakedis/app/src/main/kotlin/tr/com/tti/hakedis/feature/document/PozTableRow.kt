package tr.com.tti.hakedis.feature.document

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import tr.com.tti.hakedis.R
import tr.com.tti.hakedis.designsystem.DisabledBg
import tr.com.tti.hakedis.designsystem.OnSurfaceMut
import tr.com.tti.hakedis.designsystem.PrimarySoft
import tr.com.tti.hakedis.designsystem.Spacing
import tr.com.tti.hakedis.domain.pricing.QuantityError

/**
 * Tek tablo satırı. Sol taraf (Poz No + İş Kalemi) sabit, sağ taraf [horizontalScrollState] ile kayar.
 *
 * Her belge türü ayrı bir belge olduğu için tek miktar ve tek tutar sütunu vardır.
 *
 * [enabled] `false` olduğunda belge artık düzenlenemez (taslak değil, D2): miktar kutuları kapalı
 * ama satırlar okunabilir kalır.
 */
@Composable
fun PozTableRow(row: PozRowUi, horizontalScrollState: ScrollState, enabled: Boolean, onQuantityChanged: (String) -> Unit) {
    val background = if (row.isGroup) PrimarySoft else MaterialTheme.colorScheme.surface

    Column(modifier = Modifier.background(background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PozColumn.minRowHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextCell(
                text = row.pozNo,
                width = PozColumn.pozNo,
                bold = row.isGroup,
            )
            Column(
                modifier = Modifier
                    .width(PozColumn.name)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            ) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (row.isGroup) FontWeight.Bold else FontWeight.Normal,
                )
                if (!row.isGroup && !row.isPriced) {
                    Text(
                        text = stringResource(R.string.poz_table_not_priced),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceMut,
                    )
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(horizontalScrollState),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextCell(text = row.unit, width = PozColumn.unit, bold = row.isGroup)
                TextCell(text = row.unitPriceText, width = PozColumn.unitPrice, align = TextAlign.End)

                QuantityCell(
                    value = row.quantityText,
                    enabled = enabled,
                    inputAllowed = !row.isGroup && row.isPriced,
                    error = row.quantityError,
                    testTag = quantityTestTag(row.id),
                    onValueChange = onQuantityChanged,
                )
                AmountCell(
                    text = row.amountText,
                    isGroup = row.isGroup,
                    testTag = amountTestTag(row.id),
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun TextCell(text: String, width: androidx.compose.ui.unit.Dp, bold: Boolean = false, align: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = align,
        modifier = Modifier
            .width(width)
            .padding(horizontal = Spacing.sm),
    )
}

/**
 * Miktar hücresi.
 *
 * Grup başlığı ve fiyatlandırılmamış satırda kutu **hiç çizilmez** ([inputAllowed] `false`): pasif de
 * olsa bir giriş kutusu, oraya bir şey yazılabileceği izlenimi veriyor. Kapanmış belgede ise kutu
 * kalır ama pasiftir — girilmiş miktar okunabilmeli.
 */
@Composable
private fun QuantityCell(
    value: String,
    enabled: Boolean,
    inputAllowed: Boolean,
    error: QuantityError?,
    testTag: String,
    onValueChange: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .width(PozColumn.quantity)
            .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
    ) {
        if (!inputAllowed) return@Box
        OutlinedTextField(
            value = value,
            // `KeyboardType.Decimal` yalnızca klavyeyi seçer; kullanıcı klavyeyi harfe çevirip
            // `qwdqwd` yazabiliyordu (elle testte çıktı). Kabul edilmeyen karakter kutuya hiç girmez.
            onValueChange = { yeni -> if (yeni.all(::miktarKarakteriMi)) onValueChange(yeni) },
            enabled = enabled,
            singleLine = true,
            isError = error != null,
            textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.End),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            supportingText = error?.let { quantityError ->
                { Text(text = stringResource(quantityError.messageRes()), style = MaterialTheme.typography.bodyMedium) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
        )
    }
}

/**
 * Miktar kutusuna girmesine izin verilen karakterler: rakamlar ve iki ayırıcı.
 *
 * Bu bir **karakter süzgeci**, yapı denetimi değil. Web'in maskesi (`^\d*([.,]\d*)?$`) buraya
 * **kopyalanmaz**: web, domain'deki `QuantityParser`'ın dar bir alt kümesini kabul ediyor çünkü
 * binlik ayırıcı kuralını JS'e taşımak iş mantığını ikinci kez yazmak olurdu. Mobil aynı domain
 * sınıfını doğrudan çağırdığı için `1.234,5` gibi yazımları da kabul edebiliyor; burada yapıyı
 * kısıtlamak o yeteneği gereksiz yere geri alırdı.
 *
 * Yapı denetimi yerinde duruyor: `QuantityParser` geçersiz yazımı yakalar, satır kırmızı kenarlık
 * alır ve gönderme kilitlenir.
 *
 * `internal`: EK3 malzeme miktarı da aynı süzgeci kullanıyor (`MaterialsSection`). Kopyalamak aynı
 * kuralı iki dosyaya yazmak olurdu ve `KeyboardType.Decimal` tek başına harf girişini durdurmuyor —
 * kullanıcı klavyeyi değiştirebiliyor (Düzeltme Turu 2).
 */
internal fun miktarKarakteriMi(karakter: Char): Boolean = karakter.isDigit() || karakter == '.' || karakter == ','

/** Hesaplanan tutar: dokunulamadığı anlaşılsın diye `DisabledBg` zeminli. */
@Composable
private fun AmountCell(text: String, isGroup: Boolean, testTag: String) {
    Box(
        modifier = Modifier
            .width(PozColumn.amount)
            .heightIn(min = PozColumn.minRowHeight)
            .background(if (isGroup) Color.Transparent else DisabledBg)
            .padding(horizontal = Spacing.sm),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(testTag),
        )
    }
}

private fun QuantityError.messageRes(): Int = when (this) {
    QuantityError.NOT_A_NUMBER -> R.string.error_quantity_invalid
    QuantityError.NEGATIVE -> R.string.error_quantity_negative
}
