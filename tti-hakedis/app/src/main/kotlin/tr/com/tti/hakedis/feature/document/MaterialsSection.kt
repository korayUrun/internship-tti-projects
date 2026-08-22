package tr.com.tti.hakedis.feature.document

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import tr.com.tti.hakedis.R
import tr.com.tti.hakedis.designsystem.AppTextField
import tr.com.tti.hakedis.designsystem.ErrorBanner
import tr.com.tti.hakedis.designsystem.FieldColumns
import tr.com.tti.hakedis.designsystem.InfoBanner
import tr.com.tti.hakedis.designsystem.OnSurfaceMut
import tr.com.tti.hakedis.designsystem.PrimaryButton
import tr.com.tti.hakedis.designsystem.SecondaryButton
import tr.com.tti.hakedis.designsystem.SectionCard
import tr.com.tti.hakedis.designsystem.Spacing

/**
 * EK3 "TTI Kullanılan Malzemeler" — **yalnızca hakedişte** (kullanıcı isteği, 18 Ağustos 2026).
 * Görünürlük kararı çağıranda değil, `DocumentUiState.showsMaterials`'ta: kural tek yerde.
 *
 * Üç alanın da (**Malzeme Adı · Malzeme Miktarı · Seri No**) boş bırakılabilmesi bilinçlidir: saha
 * bazen yalnızca seri no, bazen yalnızca ad biliyor ve zorunluluk koymak eldeki eksik bilgiyi hiç
 * kaydettirmemek demekti. Bu yüzden bölüm **gönderim kilidine girmiyor** — `SubmitBlocker`'a hiçbir
 * madde eklenmiyor; kaydedilmemiş satır uyarısı `DocumentViewModel.submit()` içinde veriliyor.
 *
 * Kaydetme **tam değiştirmedir** ve **açık düğmeyle** yapılır (miktar tablosundaki debounce'lu
 * kayıttan ayrıldığı tek nokta): yarısı yazılmış bir satırın kendiliğinden gidip listeyi ezmesi
 * istenmiyor.
 *
 * Gözlem modunda ve kapanmış belgede kart **çizilmeye devam eder**, alanlar pasif olur — tamamlanmış
 * bir hakedişin malzemelerini görmek gözlemin amacının kendisi. Ayrıca `readOnly` kontrolü burada
 * yazılmaz: `uiState.editable` zaten `!readOnly && duzenlenebilir`.
 */
@Composable
fun MaterialsSection(uiState: DocumentUiState, onEvent: (DocumentEvent) -> Unit) {
    SectionCard(
        title = stringResource(R.string.materials_title),
        highlighted = uiState.materialsUnsaved,
    ) {
        Text(
            text = stringResource(R.string.materials_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceMut,
        )

        // Girilen satır kaydedilmeden gönderilirse belge malzemesiz kilitlenirdi; uyarı burada da
        // duruyor ki kullanıcı düğmeyi görürken sebebi okusun. Tasarım sisteminde sarı "uyarı"
        // şeridi yok (yalnızca bilgi/hata); tek çağrı noktası için üçüncü varyant açılmadı.
        if (uiState.materialsUnsaved) {
            ErrorBanner(message = stringResource(R.string.materials_unsaved_warning))
        }
        if (uiState.materialsSaved) {
            InfoBanner(message = stringResource(R.string.materials_result_saved))
        }

        if (uiState.materials.isEmpty() && !uiState.editable) {
            Text(
                text = stringResource(R.string.materials_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurfaceMut,
            )
        } else {
            uiState.materials.forEachIndexed { index, row ->
                MaterialRow(
                    index = index,
                    row = row,
                    enabled = uiState.editable && !uiState.materialsBusy,
                    onEvent = onEvent,
                )
            }
        }

        if (uiState.editable) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                SecondaryButton(
                    text = stringResource(R.string.materials_add),
                    enabled = !uiState.materialsBusy,
                    onClick = { onEvent(DocumentEvent.MaterialRowAdded) },
                )
                PrimaryButton(
                    text = stringResource(
                        if (uiState.materialsBusy) R.string.document_saving else R.string.materials_save,
                    ),
                    enabled = !uiState.materialsBusy,
                    onClick = { onEvent(DocumentEvent.MaterialsSaveRequested) },
                )
            }
        }
    }
}

/**
 * Satır kimliği **indekstir**: satırların sunucu tarafında kimliği yok (liste tam değiştiriliyor) ve
 * sıra kullanıcının kendi eklediği sıradır — web de `key={indeks}` kullanıyor.
 *
 * ⚠️ Bu yalnızca **tüm alan state'i ViewModel'de** olduğu için güvenli. Buraya bir `remember`
 * konursa `index` numaralı satırı silmek bir sonraki satırın içeriğini gözle görünür biçimde bozar.
 */
@Composable
private fun MaterialRow(index: Int, row: MaterialRowUi, enabled: Boolean, onEvent: (DocumentEvent) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.materials_row_number, index + 1),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Box(modifier = Modifier.weight(1f))
        if (enabled) {
            TextButton(
                onClick = { onEvent(DocumentEvent.MaterialRowRemoved(index)) },
                modifier = Modifier.defaultMinSize(minHeight = Spacing.minTouchTarget),
            ) {
                Text(text = stringResource(R.string.materials_remove), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    // Üç alan yan yana ~840 dp ister; `formMaxWidth` 920 dp olduğu için yatayda sığar ama portrede
    // okunmaz. `FieldColumns` bunu tek mantıkla çözüyor (CLAUDE.md §8) — elle genişlik dallanması yok.
    FieldColumns(
        left = {
            AppTextField(
                label = stringResource(R.string.materials_column_name),
                value = row.ad,
                enabled = enabled,
                onValueChange = { onEvent(DocumentEvent.MaterialChanged(index, MaterialField.AD, it)) },
            )
            AppTextField(
                label = stringResource(R.string.materials_column_quantity),
                value = row.miktar,
                placeholder = stringResource(R.string.materials_quantity_placeholder),
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                // Karakter süzgeci `PozTableRow` ile ortak. Yapı denetimi **sunucuda**: geçersiz
                // miktarda 400 dönüyor ve mesajı zaten kullanıcıya uygun ("… ör. 12,5. Verilen: X").
                onValueChange = { yeni ->
                    if (yeni.all(::miktarKarakteriMi)) {
                        onEvent(DocumentEvent.MaterialChanged(index, MaterialField.MIKTAR, yeni))
                    }
                },
            )
        },
        right = {
            AppTextField(
                label = stringResource(R.string.materials_column_serial),
                value = row.seriNo,
                enabled = enabled,
                onValueChange = { onEvent(DocumentEvent.MaterialChanged(index, MaterialField.SERI_NO, it)) },
            )
        },
    )

    HorizontalDivider(modifier = Modifier.fillMaxWidth())
}
