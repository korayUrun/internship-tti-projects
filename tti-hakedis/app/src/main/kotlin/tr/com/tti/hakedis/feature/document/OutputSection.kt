package tr.com.tti.hakedis.feature.document

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import tr.com.tti.hakedis.R
import tr.com.tti.hakedis.data.api.OutputFormat
import tr.com.tti.hakedis.designsystem.OnSurfaceMut
import tr.com.tti.hakedis.designsystem.Outline
import tr.com.tti.hakedis.designsystem.SectionCard
import tr.com.tti.hakedis.designsystem.Spacing
import tr.com.tti.hakedis.designsystem.Success
import tr.com.tti.hakedis.domain.document.DocumentVariant
import java.io.File

/**
 * Çıktı indirme ve paylaşma.
 *
 * PDF ve Excel **sunucuda** üretilir (CLAUDE.md §13); cihaz yalnızca indirir. İnen PDF'in ilk sayfası
 * `PdfPreviewRenderer` ile gösterilir, paylaşım `FileProvider` üzerinden yapılır — `file://` URI
 * paylaşmak `FileUriExposedException` fırlatır (§12/2).
 */
@Composable
fun OutputSection(uiState: DocumentUiState, onEvent: (DocumentEvent) -> Unit) {
    val context = LocalContext.current
    val downloaded = uiState.downloaded

    // Sistem kaydetme ekranı: kullanıcı hedefi (İndirilenler vb.) kendisi seçer. İzin gerektirmez ve
    // minSdk 26'da çalışır — doğrudan İndirilenler'e yazmak API 29+ ister (bkz. `FileStorage`).
    // Vazgeçildiğinde `uri` null gelir; bu bir hata değil, hiçbir şey yapılmaz.
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(downloaded?.format?.mimeType() ?: "application/pdf"),
    ) { uri -> uri?.let { onEvent(DocumentEvent.SaveRequested(it)) } }

    SectionCard(title = stringResource(R.string.output_title)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            uiState.variants.forEach { variant ->
                OutlinedButton(
                    onClick = { onEvent(DocumentEvent.OutputRequested(OutputFormat.PDF, variant)) },
                    enabled = !uiState.downloading,
                    modifier = Modifier.defaultMinSize(minHeight = Spacing.minTouchTarget),
                ) {
                    Text(text = stringResource(R.string.output_pdf, variant.label()))
                }
                OutlinedButton(
                    onClick = { onEvent(DocumentEvent.OutputRequested(OutputFormat.EXCEL, variant)) },
                    enabled = !uiState.downloading,
                    modifier = Modifier.defaultMinSize(minHeight = Spacing.minTouchTarget),
                ) {
                    Text(text = stringResource(R.string.output_excel, variant.label()))
                }
            }
        }

        if (uiState.downloading) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                CircularProgressIndicator(modifier = Modifier.heightIn(max = PROGRESS_SIZE), strokeWidth = 2.dp)
                Text(text = stringResource(R.string.output_downloading), style = MaterialTheme.typography.bodyLarge)
            }
        }

        downloaded?.let { output ->
            Text(text = output.file.name, style = MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(
                    onClick = { saveLauncher.launch(output.file.name) },
                    modifier = Modifier.defaultMinSize(minHeight = Spacing.minTouchTarget),
                ) {
                    Text(text = stringResource(R.string.action_download), style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = { context.startActivity(shareIntent(context, output.file, output.format)) },
                    modifier = Modifier.defaultMinSize(minHeight = Spacing.minTouchTarget),
                ) {
                    Text(text = stringResource(R.string.output_share))
                }
                OutlinedButton(
                    onClick = { context.startActivity(openIntent(context, output.file, output.format)) },
                    modifier = Modifier.defaultMinSize(minHeight = Spacing.minTouchTarget),
                ) {
                    Text(text = stringResource(R.string.output_open))
                }
            }

            uiState.savedFileName?.let { ad ->
                Text(
                    text = stringResource(R.string.output_saved, ad),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Success,
                )
            }

            output.preview?.let { preview ->
                Text(
                    text = stringResource(R.string.output_page_count, output.pageCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceMut,
                )
                // Önizleme **düğmelerin altında** ve yüksekliği sınırlı: A4 oranındaki tam boy görsel
                // ekranın üç katı yer kaplıyor ve "İndir"i görünmez hâle getiriyordu. Belgenin
                // doğru belge olduğunu anlamak için ilk ekranı görmek yetiyor.
                Image(
                    bitmap = preview,
                    contentDescription = stringResource(R.string.output_preview_description),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = ONIZLEME_YUKSEKLIGI)
                        .border(1.dp, Outline),
                )
            }
        }
    }
}

@Composable
private fun DocumentVariant.label(): String = when (this) {
    DocumentVariant.FIYATLI -> stringResource(R.string.variant_fiyatli)
    DocumentVariant.TEKLIF -> stringResource(R.string.variant_teklif)
}

private fun uriFor(context: android.content.Context, file: File) =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

private fun shareIntent(context: android.content.Context, file: File, format: OutputFormat): Intent = Intent.createChooser(
    Intent(Intent.ACTION_SEND).apply {
        type = format.mimeType()
        putExtra(Intent.EXTRA_STREAM, uriFor(context, file))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    },
    file.name,
)

private fun openIntent(context: android.content.Context, file: File, format: OutputFormat): Intent = Intent(Intent.ACTION_VIEW).apply {
    setDataAndType(uriFor(context, file), format.mimeType())
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
}

private fun OutputFormat.mimeType(): String = when (this) {
    OutputFormat.PDF -> "application/pdf"
    OutputFormat.EXCEL -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
}

private val PROGRESS_SIZE = 24.dp

/** Önizleme yalnızca "doğru belge mi" sorusunu cevaplar; tam boy göstermek gerekmiyor. */
private val ONIZLEME_YUKSEKLIGI = 420.dp
