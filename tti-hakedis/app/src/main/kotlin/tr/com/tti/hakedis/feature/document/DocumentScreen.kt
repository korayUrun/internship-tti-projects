package tr.com.tti.hakedis.feature.document

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import tr.com.tti.hakedis.R
import tr.com.tti.hakedis.designsystem.ErrorBanner
import tr.com.tti.hakedis.designsystem.FieldColumns
import tr.com.tti.hakedis.designsystem.FormContainer
import tr.com.tti.hakedis.designsystem.InfoBanner
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
 * Belge ekranı — üç belge türü için **tek** ekran. Sıra: sözleşme → miktar → dosyalar (Faz S).
 *
 * Toplam çubuğu ekranın altına sabitlenir (CLAUDE.md §8); gönderme ön koşulları eksikse düğme kapalı
 * ve eksikler madde madde yazılı.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(
    durum: DocumentUiState,
    readOnly: Boolean = false,
    cameraUri: Uri?,
    onEvent: (DocumentEvent) -> Unit,
    onBack: () -> Unit,
    onFinished: () -> Unit,
) {
    // "Gözlemle" modu tek yerde uygulanır: aşağıdaki her `uiState.editable` kontrolü otomatik kapanır.
    val uiState = if (readOnly) durum.copy(readOnly = true) else durum

    // Ekrana geri dönüldüğünde veri tazelenir: yönetici bu arada fizibiliteyi onaylamış olabilir.
    // Kaydedilmemiş miktar varsa `DocumentViewModel` yüklemeyi atlar (girişi ezmemek için).
    LifecycleResumeEffect(Unit) {
        onEvent(DocumentEvent.Refresh)
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = uiState.document?.belgeNo ?: stringResource(R.string.document_title)) },
                colors = appTopBarColors(),
                navigationIcon = {
                    TextButton(onClick = onBack, modifier = Modifier.defaultMinSize(minHeight = Spacing.minTouchTarget)) {
                        Text(text = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    // Ekran dikey kaydırma + iç LazyColumn içerdiği için "aşağı çekerek yenile"
                    // hareketi tabloyla çakışıyor; yenileme açık bir düğme olarak duruyor.
                    TextButton(
                        onClick = { onEvent(DocumentEvent.Refresh) },
                        modifier = Modifier.defaultMinSize(minHeight = Spacing.minTouchTarget),
                    ) {
                        Text(text = stringResource(R.string.action_refresh))
                    }
                },
            )
        },
        bottomBar = { if (uiState.loadState == LoadState.Ready) TotalBar(uiState = uiState, onEvent = onEvent) },
    ) { padding ->
        val content = Modifier
            .fillMaxSize()
            .padding(padding)

        when (uiState.loadState) {
            LoadState.Loading -> LoadingBox(modifier = content)

            LoadState.Failed -> Column(modifier = content.padding(Spacing.md)) {
                ErrorBanner(
                    message = uiState.error ?: stringResource(R.string.error_generic),
                    onRetry = { onEvent(DocumentEvent.Retry) },
                )
            }

            LoadState.Ready -> Body(uiState = uiState, cameraUri = cameraUri, onEvent = onEvent, modifier = content)
        }
    }

    if (uiState.submitted) {
        SubmittedDialog(uiState = uiState, onFinished = onFinished)
    }
}

@Composable
private fun Body(uiState: DocumentUiState, cameraUri: Uri?, onEvent: (DocumentEvent) -> Unit, modifier: Modifier) {
    val document = uiState.document ?: return

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
    ) {
        FormContainer {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(text = document.durumMetni, tone = document.durum.tone())
                Text(
                    text = document.tur.label(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnSurfaceMut,
                )
            }

            // Sunucudan gelen mesaj olduğu gibi basılır; cihazda verilen ret kararının metni burada.
            uiState.error?.let { ErrorBanner(message = it) }
            uiState.localError?.let { ErrorBanner(message = stringResource(it.messageRes())) }

            if (uiState.invalidPozNos.isNotEmpty()) {
                ErrorBanner(
                    message = stringResource(R.string.document_invalid_quantities, uiState.invalidPozNos.joinToString(", ")),
                )
            }

            // İptal gerekçesi yöneticinin yazdığı metindir ve yalnızca iptal edilmiş belgede dolu
            // gelir; belgenin neden kapandığını söyleyen tek yer burasıdır.
            if (document.durum == DocumentStatus.IPTAL && !document.iptalGerekce.isNullOrBlank()) {
                ErrorBanner(message = stringResource(R.string.document_cancelled, document.iptalGerekce))
            }

            if (!uiState.editable) {
                InfoBanner(message = stringResource(R.string.document_read_only, document.durumMetni))
            } else if (document.tur == DocumentType.FIZIBILITE) {
                InfoBanner(message = stringResource(R.string.document_reference_prices))
            }

            uiState.workOrder?.let { workOrder ->
                SectionCard(title = stringResource(R.string.work_order_title)) {
                    // İş özeti tabletde iki sütun etiket→değer; dar ekranda alt alta akar (§8).
                    FieldColumns(
                        left = {
                            LabelValue(label = stringResource(R.string.field_sp_no), value = workOrder.spNo)
                            LabelValue(label = stringResource(R.string.field_ek_araligi), value = workOrder.ekAraligi)
                        },
                        right = {
                            LabelValue(label = stringResource(R.string.field_kable_id), value = workOrder.kableNosid)
                            LabelValue(label = stringResource(R.string.field_route), value = workOrder.route)
                        },
                    )
                }
            }

            // Ekran sırası web ile aynı (kullanıcı kararı): sözleşme → miktar → dosyalar.
            ContractSection(uiState = uiState, onEvent = onEvent)

            SectionCard(
                title = stringResource(R.string.poz_table_title),
                highlighted = uiState.editable && uiState.filledRowCount == 0,
            ) {
                if (uiState.editable) QuantityFileRow(uiState = uiState, onEvent = onEvent)
                PozTable(uiState = uiState, onEvent = onEvent)
            }

            AttachmentsSection(uiState = uiState, cameraUri = cameraUri, onEvent = onEvent)

            // EK3 — dosyalardan sonra, çıktıdan önce (CLAUDE.md §9 adım 4; web'de de aynı sıra).
            // Yalnızca hakedişte: kural `showsMaterials`'ta, tek yerde.
            if (uiState.showsMaterials) {
                MaterialsSection(uiState = uiState, onEvent = onEvent)
            }

            OutputSection(uiState = uiState, onEvent = onEvent)
        }
    }
}

/**
 * Belgenin sözleşmesi — ekranın **ilk** kartı (Faz N). Değiştirmek yalnızca taslakta mümkün ve
 * **girilmiş miktarları siler**: poz kimliği sözleşme içinde anlamlıdır, başka bir sözleşmede aynı
 * kod bambaşka bir kaleme denk gelir.
 */
@Composable
private fun ContractSection(uiState: DocumentUiState, onEvent: (DocumentEvent) -> Unit) {
    val document = uiState.document ?: return
    var acik by remember { mutableStateOf(false) }
    var secili by remember(document.sozlesmeKodu) { mutableStateOf(document.sozlesmeKodu) }

    SectionCard(title = stringResource(R.string.contract_section_title)) {
        LabelValue(label = stringResource(R.string.field_contract), value = document.sozlesmeAdi)

        if (uiState.editable && !acik) {
            OutlinedButton(
                onClick = {
                    acik = true
                    onEvent(DocumentEvent.ContractEditRequested)
                },
                modifier = Modifier.defaultMinSize(minHeight = Spacing.minTouchTarget),
            ) {
                Text(text = stringResource(R.string.contract_change))
            }
        }

        if (acik) {
            Text(text = stringResource(R.string.contract_change_warning), color = MaterialTheme.colorScheme.error)
            DropdownSelect(
                label = stringResource(R.string.field_contract),
                selectedLabel = uiState.contracts.firstOrNull { it.kod == secili }?.ad ?: document.sozlesmeAdi,
                placeholder = stringResource(R.string.placeholder_select),
                options = uiState.contracts.map { DropdownOption(it.kod, it.ad) },
                onSelect = { secili = it },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(
                    enabled = !uiState.contractChanging && secili != document.sozlesmeKodu,
                    onClick = {
                        onEvent(DocumentEvent.ContractChanged(secili))
                        acik = false
                    },
                    modifier = Modifier.defaultMinSize(minHeight = Spacing.minTouchTarget),
                ) {
                    Text(text = stringResource(R.string.contract_change))
                }
                OutlinedButton(
                    onClick = {
                        secili = document.sozlesmeKodu
                        acik = false
                    },
                    modifier = Modifier.defaultMinSize(minHeight = Spacing.minTouchTarget),
                ) {
                    Text(text = stringResource(R.string.action_give_up))
                }
            }
        }
    }
}

/**
 * Miktarların Excel ile girilmesi: **şablon indir → doldur → yükle** (Faz P). Tabloya elle giriş
 * alternatif olarak duruyor; ikisi aynı sonucu veriyor.
 */
@Composable
private fun QuantityFileRow(uiState: DocumentUiState, onEvent: (DocumentEvent) -> Unit) {
    val secici = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onEvent(DocumentEvent.QuantityFilePicked(it)) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(
                enabled = !uiState.quantityFileBusy,
                onClick = { onEvent(DocumentEvent.QuantityTemplateRequested) },
                modifier = Modifier.defaultMinSize(minHeight = Spacing.minTouchTarget),
            ) {
                Text(text = stringResource(R.string.quantity_template_download))
            }
            OutlinedButton(
                enabled = !uiState.quantityFileBusy,
                // MIME tipi cihazdan cihaza değişiyor (CLAUDE.md §12/3); `*/*` verilip doğrulama
                // sunucuya bırakılıyor.
                onClick = { secici.launch(arrayOf(XLSX_MIME, "*/*")) },
                modifier = Modifier.defaultMinSize(minHeight = Spacing.minTouchTarget),
            ) {
                Text(text = stringResource(R.string.quantity_template_upload))
            }
        }
        Text(
            text = stringResource(R.string.quantity_template_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceMut,
        )
    }
}

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/** Toplam ve gönderme — ekranın altına sabit. */
@Composable
private fun TotalBar(uiState: DocumentUiState, onEvent: (DocumentEvent) -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.poz_table_total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceMut,
                    )
                    Text(
                        text = uiState.totalText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag(TOTAL_TAG),
                    )
                }

                // Kaydetme durumu yalnızca taslakta anlamlı: kapanmış belgede "kaydedilmemiş
                // değişiklik var" yazması, bir şeyin kaybolduğu izlenimi verir (Düzeltme Turu 1/3).
                if (uiState.editable) {
                    Text(
                        text = when {
                            uiState.saving -> stringResource(R.string.document_saving)
                            uiState.unsaved -> stringResource(R.string.document_unsaved)
                            else -> stringResource(R.string.document_saved)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceMut,
                    )
                }

                Column(modifier = Modifier.weight(1f)) {}

                // Düğmenin **görünürlüğü** `gonderilebilir`e bağlı (sunucu söyler, web de aynı alanı
                // okuyor); soldaki kaydetme metni ve alttaki eksik listesi ise girişi anlatıyor ve
                // `editable`da kalıyor.
                if (uiState.submitVisible) {
                    Button(
                        onClick = { onEvent(DocumentEvent.Submitted) },
                        enabled = uiState.canSubmit,
                        modifier = Modifier.defaultMinSize(minHeight = Spacing.minTouchTarget),
                    ) {
                        Text(
                            text = when {
                                uiState.submitting -> stringResource(R.string.document_submitting)
                                uiState.document?.tur == DocumentType.FIZIBILITE ->
                                    stringResource(R.string.document_submit_for_approval)

                                else -> stringResource(R.string.document_submit)
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            // Eksikler madde madde yazılır: kullanıcı 409 duvarına toslamadan neyin eksik olduğunu
            // görmeli. Güvence yine sunucuda.
            if (uiState.editable) {
                // Kaydedilmemiş malzeme, gönderimi engelleyen eksiklerden **ayrı** yazılıyor ve
                // `SubmitBlocker` DEĞİL: blocker listesi düğmeyi kapatıyor, bu ise düğmeyi açık
                // bırakıp uyarıyı gönderme anında veriyor (19 Ağustos 2026). Not burada duruyor
                // çünkü düğme yapışkan alt çubukta, hata şeridi ise kaydırılan sütunun tepesinde —
                // 95 satırlık bir pozda ikisi ekranlarca uzakta kalıyor.
                if (uiState.materialsUnsaved) {
                    Text(
                        text = stringResource(R.string.materials_unsaved_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                uiState.blockers.forEach { blocker ->
                    Text(
                        text = stringResource(blocker.messageRes()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun SubmittedDialog(uiState: DocumentUiState, onFinished: () -> Unit) {
    AlertDialog(
        onDismissRequest = onFinished,
        title = { Text(text = stringResource(R.string.document_submitted_title)) },
        text = { Text(text = uiState.document?.durumMetni.orEmpty()) },
        confirmButton = {
            TextButton(onClick = onFinished, modifier = Modifier.defaultMinSize(minHeight = Spacing.minTouchTarget)) {
                Text(text = stringResource(R.string.document_submitted_go_to_work_order))
            }
        },
    )
}

private fun LocalError.messageRes(): Int = when (this) {
    LocalError.INVALID_ROUTE_EXTENSION -> R.string.attachment_error_invalid_extension
    LocalError.UNSAVED_MATERIALS -> R.string.materials_unsaved_submit_error
}

private fun SubmitBlocker.messageRes(): Int = when (this) {
    SubmitBlocker.NO_QUANTITY -> R.string.blocker_no_quantity
    SubmitBlocker.INVALID_QUANTITY -> R.string.blocker_invalid_quantity
    SubmitBlocker.MISSING_EK1 -> R.string.blocker_missing_ek1
}
