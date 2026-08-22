package tr.com.tti.hakedis.feature.archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import tr.com.tti.hakedis.R
import tr.com.tti.hakedis.data.api.DocumentListDto
import tr.com.tti.hakedis.designsystem.ErrorBanner
import tr.com.tti.hakedis.designsystem.LabelValue
import tr.com.tti.hakedis.designsystem.LoadingBox
import tr.com.tti.hakedis.designsystem.OnSurfaceMut
import tr.com.tti.hakedis.designsystem.SectionCard
import tr.com.tti.hakedis.designsystem.Spacing
import tr.com.tti.hakedis.designsystem.StatusBadge
import tr.com.tti.hakedis.designsystem.appTopBarColors
import tr.com.tti.hakedis.domain.document.DocumentStatus
import tr.com.tti.hakedis.domain.document.DocumentType
import tr.com.tti.hakedis.feature.LoadState
import tr.com.tti.hakedis.feature.label
import tr.com.tti.hakedis.feature.tone
import tr.com.tti.hakedis.feature.workorder.DropdownOption
import tr.com.tti.hakedis.feature.workorder.DropdownSelect

/**
 * Belge arşivi — "hangi belgeler üretildi, hangisini açayım".
 *
 * Günlük çalışma **proje listesinde** yürüyor; bu ekran arşivdir. Satır belgeyi **salt okunur**
 * açar: indirme bölümü (`OutputSection`) belge ekranında zaten var, ikinci kez yazılmıyor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentArchiveScreen(uiState: DocumentArchiveUiState, onEvent: (DocumentArchiveEvent) -> Unit, onOpenDocument: (Long) -> Unit) {
    // Sekmeye dönüldüğünde tazelenir: başka bir cihazda gönderilen belge burada görünmeli.
    LifecycleResumeEffect(Unit) {
        onEvent(DocumentArchiveEvent.Refresh)
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(text = stringResource(R.string.documents_title)) }, colors = appTopBarColors()) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Filters(uiState = uiState, onEvent = onEvent)

            uiState.error?.let {
                ErrorBanner(message = it, onRetry = { onEvent(DocumentArchiveEvent.Refresh) })
            }

            when (uiState.loadState) {
                LoadState.Loading -> LoadingBox()

                LoadState.Failed -> Unit

                LoadState.Ready -> PullToRefreshBox(
                    isRefreshing = false,
                    onRefresh = { onEvent(DocumentArchiveEvent.Refresh) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (uiState.items.isEmpty()) {
                            // Boş listede de çekilebilmeli; yoksa "yenile" tam gerektiği anda çalışmaz.
                            item {
                                Text(
                                    text = stringResource(R.string.documents_empty),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = OnSurfaceMut,
                                    modifier = Modifier.padding(vertical = Spacing.lg),
                                )
                            }
                        }
                        items(items = uiState.items, key = { it.id }) { belge ->
                            DocumentRow(belge = belge, onOpen = { onOpenDocument(belge.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Filters(uiState: DocumentArchiveUiState, onEvent: (DocumentArchiveEvent) -> Unit) {
    val hepsi = stringResource(R.string.documents_filter_all)
    val durumAdlari = mapOf(
        DocumentStatus.ONAY_BEKLIYOR to stringResource(R.string.status_pending),
        DocumentStatus.ISLENIYOR to stringResource(R.string.status_processing),
        DocumentStatus.TAMAMLANDI to stringResource(R.string.status_completed),
        DocumentStatus.IPTAL to stringResource(R.string.status_cancelled),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
        DropdownSelect(
            label = stringResource(R.string.documents_filter_type),
            selectedLabel = uiState.type?.label() ?: hepsi,
            placeholder = hepsi,
            options = listOf(DropdownOption("", hepsi)) + DocumentType.entries.map { DropdownOption(it.name, it.label()) },
            onSelect = { secim ->
                onEvent(DocumentArchiveEvent.TypeChanged(secim.takeIf(String::isNotEmpty)?.let(DocumentType::valueOf)))
            },
            modifier = Modifier.weight(1f),
        )
        DropdownSelect(
            label = stringResource(R.string.documents_filter_status),
            selectedLabel = uiState.status?.let(durumAdlari::get) ?: hepsi,
            placeholder = hepsi,
            options = listOf(DropdownOption("", hepsi)) + durumAdlari.map { (durum, ad) -> DropdownOption(durum.name, ad) },
            onSelect = { secim ->
                onEvent(DocumentArchiveEvent.StatusChanged(secim.takeIf(String::isNotEmpty)?.let(DocumentStatus::valueOf)))
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DocumentRow(belge: DocumentListDto, onOpen: () -> Unit) {
    SectionCard(title = belge.belgeNo) {
        StatusBadge(text = belge.durumMetni, tone = belge.durum.tone())

        LabelValue(label = stringResource(R.string.field_sp_no), value = belge.spNo)
        LabelValue(label = stringResource(R.string.field_kable_id), value = belge.kableNosid)
        LabelValue(label = stringResource(R.string.field_responsible), value = belge.sorumluAdSoyad)
        // Tutar sunucudan hazır biçimlenmiş gelir; mobil para biçimlendirmesi yapmaz (§13).
        LabelValue(label = stringResource(R.string.field_total), value = belge.toplamGosterim)

        Button(onClick = onOpen, modifier = Modifier.defaultMinSize(minHeight = Spacing.minTouchTarget)) {
            Text(text = stringResource(R.string.action_observe_document))
        }
    }
}
