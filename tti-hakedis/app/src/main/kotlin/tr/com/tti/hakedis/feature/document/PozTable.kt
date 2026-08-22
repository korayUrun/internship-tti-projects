package tr.com.tti.hakedis.feature.document

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import tr.com.tti.hakedis.R
import tr.com.tti.hakedis.designsystem.OnSurfaceMut
import tr.com.tti.hakedis.designsystem.PrimarySoft
import tr.com.tti.hakedis.designsystem.Spacing

/**
 * Poz tablosu: arama/süzme kutusu, sabit başlık ve kaydırılabilir satırlar.
 *
 * Sol taraf (Poz No + İş Kalemi) yatay kaydırmada sabit kalır; başlık satırı ve tüm veri satırları
 * **aynı** `ScrollState`'i paylaşır, böylece birlikte kayarlar.
 */
@Composable
fun PozTable(uiState: DocumentUiState, onEvent: (DocumentEvent) -> Unit, modifier: Modifier = Modifier) {
    val horizontalScrollState = androidx.compose.foundation.rememberScrollState()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        TableFilters(uiState = uiState, onEvent = onEvent)
        TableHeader(horizontalScrollState = horizontalScrollState)

        if (uiState.rows.isEmpty()) {
            Text(
                text = stringResource(R.string.poz_table_no_match),
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurfaceMut,
                modifier = Modifier.padding(Spacing.md),
            )
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = PozColumn.tableMaxHeight)) {
                items(items = uiState.rows, key = { it.id }) { row ->
                    PozTableRow(
                        row = row,
                        horizontalScrollState = horizontalScrollState,
                        enabled = uiState.editable,
                        onQuantityChanged = { text -> onEvent(DocumentEvent.QuantityChanged(row.id, text)) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TableFilters(uiState: DocumentUiState, onEvent: (DocumentEvent) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = uiState.query,
            onValueChange = { onEvent(DocumentEvent.QueryChanged(it)) },
            placeholder = { Text(text = stringResource(R.string.poz_table_search_placeholder)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = Spacing.fieldHeight),
        )
        FilterChip(
            selected = uiState.onlyFilled,
            onClick = { onEvent(DocumentEvent.OnlyFilledChanged(!uiState.onlyFilled)) },
            label = { Text(text = stringResource(R.string.poz_table_only_filled)) },
            modifier = Modifier.heightIn(min = Spacing.minTouchTarget),
        )
    }
}

@Composable
private fun TableHeader(horizontalScrollState: ScrollState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PrimarySoft)
            .heightIn(min = Spacing.minTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCell(text = stringResource(R.string.column_poz_no), width = PozColumn.pozNo)
        HeaderCell(text = stringResource(R.string.column_item_name), width = PozColumn.name)
        Row(
            modifier = Modifier.horizontalScroll(horizontalScrollState),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderCell(text = stringResource(R.string.column_unit), width = PozColumn.unit)
            HeaderCell(text = stringResource(R.string.column_unit_price), width = PozColumn.unitPrice, align = TextAlign.End)
            HeaderCell(text = stringResource(R.string.column_quantity), width = PozColumn.quantity, align = TextAlign.End)
            HeaderCell(text = stringResource(R.string.column_amount), width = PozColumn.amount, align = TextAlign.End)
        }
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp, align: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        textAlign = align,
        modifier = Modifier
            .width(width)
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
    )
}
