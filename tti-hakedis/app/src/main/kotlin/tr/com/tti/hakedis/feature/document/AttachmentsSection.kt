package tr.com.tti.hakedis.feature.document

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import tr.com.tti.hakedis.R
import tr.com.tti.hakedis.data.api.AttachmentDto
import tr.com.tti.hakedis.data.api.AttachmentType
import tr.com.tti.hakedis.designsystem.OnSurfaceMut
import tr.com.tti.hakedis.designsystem.SectionCard
import tr.com.tti.hakedis.designsystem.Spacing

/**
 * EK1 (güzergâh) ve EK2 (saha görselleri) — **üç belge türünde de** var (Faz P): güzergâh dosyası
 * zorunlu, görseller opsiyonel. ~~yalnızca hakediş taslağında~~
 *
 * Yükleme koşulu türe değil **düzenlenebilirliğe** bağlı: sunucu `dosyaKabulEdenBelge` ile karar
 * veriyor (aşama `ONAY_BEKLIYOR`), ekran da `uiState.editable` ile — kural türetilmiyor, sunucudan
 * geliyor. EK3 malzemeleri ise ayrı bir bölüm (`MaterialsSection`) ve yalnızca hakedişte.
 *
 * Dosyalar sunucuya yüklenir, cihazda kalıcı kopya tutulmaz: kaynak doğruluk yeri sunucu.
 */
@Composable
fun AttachmentsSection(uiState: DocumentUiState, cameraUri: Uri?, onEvent: (DocumentEvent) -> Unit) {
    // `.kmz`/`.kml` MIME tipleri cihazlar arasında tutarsız, bu yüzden seçicide geniş liste verilir
    // ve doğrulama **uzantı üzerinden** yapılır (CLAUDE.md §12/3).
    val routeFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onEvent(DocumentEvent.RouteFilePicked(it)) }
    }
    // Photo Picker: `READ_MEDIA_IMAGES` izni gerektirmez (CLAUDE.md §12/5).
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) onEvent(DocumentEvent.ImagesPicked(uris))
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        onEvent(DocumentEvent.CameraFinished(success))
    }

    // Kamera hedefi çekimden ÖNCE belirlenir; URI hazır olunca çekim başlatılır.
    LaunchedEffect(cameraUri) {
        cameraUri?.let { cameraLauncher.launch(it) }
    }

    val routeFile = uiState.attachments.firstOrNull { it.tur == AttachmentType.EK1_ROTA }

    SectionCard(
        title = stringResource(R.string.attachments_title),
        highlighted = uiState.editable && routeFile == null,
    ) {
        Text(
            text = stringResource(R.string.attachment_route_label),
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceMut,
        )
        if (routeFile == null) {
            OutlinedButton(
                onClick = { routeFilePicker.launch(ROUTE_MIME_TYPES) },
                enabled = uiState.editable && !uiState.attachmentBusy,
                modifier = Modifier.defaultMinSize(minHeight = Spacing.minTouchTarget),
            ) {
                Text(text = stringResource(R.string.attachment_route_pick))
            }
        } else {
            AttachmentRow(attachment = routeFile, editable = uiState.editable && !uiState.attachmentBusy, onEvent = onEvent)
        }

        Text(
            text = stringResource(R.string.attachment_images_label),
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceMut,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(
                onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = uiState.editable && !uiState.attachmentBusy,
                modifier = Modifier.defaultMinSize(minHeight = Spacing.minTouchTarget),
            ) {
                Text(text = stringResource(R.string.attachment_images_pick))
            }
            OutlinedButton(
                onClick = { onEvent(DocumentEvent.CameraRequested) },
                enabled = uiState.editable && !uiState.attachmentBusy,
                modifier = Modifier.defaultMinSize(minHeight = Spacing.minTouchTarget),
            ) {
                Text(text = stringResource(R.string.attachment_images_camera))
            }
        }
        uiState.images.forEach { image ->
            AttachmentRow(attachment = image, editable = uiState.editable && !uiState.attachmentBusy, onEvent = onEvent)
        }
    }
}

@Composable
private fun AttachmentRow(attachment: AttachmentDto, editable: Boolean, onEvent: (DocumentEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = attachment.dosyaAdi, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.attachment_size_kb, attachment.boyutBayt / BYTES_PER_KB),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceMut,
            )
        }
        Box {
            if (editable) {
                TextButton(
                    onClick = { onEvent(DocumentEvent.AttachmentRemoved(attachment.id)) },
                    modifier = Modifier.defaultMinSize(minHeight = Spacing.minTouchTarget),
                ) {
                    Text(text = stringResource(R.string.attachment_remove))
                }
            }
        }
    }
}

private const val BYTES_PER_KB = 1024

private val ROUTE_MIME_TYPES = arrayOf(
    "application/vnd.google-earth.kmz",
    "application/vnd.google-earth.kml+xml",
    "application/octet-stream",
    "*/*",
)
