package tr.com.tti.hakedis.feature.workorder

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import tr.com.tti.hakedis.R
import tr.com.tti.hakedis.data.api.DocumentSummaryDto
import tr.com.tti.hakedis.data.api.WorkOrderDto
import tr.com.tti.hakedis.data.api.WorkOrderStatusDto
import tr.com.tti.hakedis.designsystem.ErrorBanner
import tr.com.tti.hakedis.designsystem.LoadingBox
import tr.com.tti.hakedis.designsystem.OnSurfaceMut
import tr.com.tti.hakedis.designsystem.SectionCard
import tr.com.tti.hakedis.designsystem.Spacing
import tr.com.tti.hakedis.designsystem.StatusBadge
import tr.com.tti.hakedis.designsystem.appTopBarColors
import tr.com.tti.hakedis.designsystem.colors
import tr.com.tti.hakedis.designsystem.etiketle
import tr.com.tti.hakedis.domain.document.DocumentStatus
import tr.com.tti.hakedis.domain.document.DocumentType
import tr.com.tti.hakedis.feature.LoadState
import tr.com.tti.hakedis.feature.label
import tr.com.tti.hakedis.feature.tone
import java.io.File

/** Aşama sırası — kartta düğmeler bu sırada dizilir. */
private val ASAMALAR = listOf(DocumentType.FIZIBILITE, DocumentType.KESIF, DocumentType.HAKEDIS)

/**
 * İş listesi — uygulamanın **çalışma ekranı** (Faz S, web ile parite).
 *
 * Her iş bir kart: SP No · Bölge · Kablo ID · Ek Aralığı · Route · Durum, altında üç aşama satırı.
 * Her aşama satırında tarih, son güncelleyen, toplam ve iki düğme: **Güncelle** ve **Gözlemle**.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkOrderListScreen(
    uiState: WorkOrderListUiState,
    onEvent: (WorkOrderListEvent) -> Unit,
    onOpenDocument: (Long, Boolean) -> Unit,
    onNew: () -> Unit,
) {
    // Ekrana geri dönüldüğünde liste tazelenir: yeni açılan iş, süzgeç değiştirmeden görünmeli.
    LifecycleResumeEffect(Unit) {
        onEvent(WorkOrderListEvent.Refresh)
        onPauseOrDispose { }
    }

    // Belge açıldığında (yeni oluşturuldu ya da "Güncelle" tıklandı) gezinme tetiklenir.
    LaunchedEffect(uiState.openDocumentId) {
        uiState.openDocumentId?.let {
            onOpenDocument(it, false)
            onEvent(WorkOrderListEvent.DocumentOpened)
        }
    }

    // İş listesi Excel'i inince paylaşım penceresi açılır: tablette dosyayı görüntülemenin yolu
    // paylaşımdır, uygulamanın kendi indirme klasörü kullanıcıya görünmez.
    val context = LocalContext.current
    LaunchedEffect(uiState.exported) {
        uiState.exported?.let { dosya ->
            context.startActivity(listeyiPaylas(context, dosya))
            onEvent(WorkOrderListEvent.ExportShared)
        }
    }

    Scaffold(
        topBar = {
            // Kullanıcı adı ve "Çıkış" **Hesap** sekmesine taşındı (Düzeltme Turu 4): üst şeritte
            // sıkışık duruyorlardı ve sunucu adresi hiç ulaşılabilir değildi.
            TopAppBar(title = { Text(text = stringResource(R.string.work_orders_title)) }, colors = appTopBarColors())
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Filters(uiState = uiState, onEvent = onEvent, onNew = onNew)

            uiState.error?.let { ErrorBanner(message = it, onRetry = { onEvent(WorkOrderListEvent.Refresh) }) }

            when (uiState.loadState) {
                LoadState.Loading -> LoadingBox()

                LoadState.Failed -> Box(modifier = Modifier.fillMaxSize())

                // Aşağı çekerek yenileme yalnızca burada: bu ekranda iç içe kaydırma yok, hareket
                // listenin kendi kaydırmasıyla çakışmıyor.
                LoadState.Ready -> PullToRefreshBox(
                    isRefreshing = false,
                    onRefresh = { onEvent(WorkOrderListEvent.Refresh) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxSize()) {
                        if (uiState.items.isEmpty()) {
                            // Boş listede de çekilebilmeli; yoksa "yenile" hareketi tam gerektiği anda
                            // (liste boş görünürken) çalışmazdı.
                            item {
                                Text(
                                    text = stringResource(R.string.work_orders_empty),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = OnSurfaceMut,
                                    modifier = Modifier.padding(vertical = Spacing.lg),
                                )
                            }
                        }
                        items(items = uiState.items, key = { it.id }) { item ->
                            WorkOrderCard(
                                item = item,
                                // Ad gelmediyse (uç düşmüş ya da yeni bir kapsam) ham koda düşülür:
                                // eksik ad yüzünden alanı hiç göstermemek bilgiyi kaybettirirdi.
                                scopeName = uiState.scopeNames[item.kapsamKodu] ?: item.kapsamKodu,
                                onEvent = onEvent,
                                onObserve = { onOpenDocument(it, true) },
                            )
                        }
                    }
                }
            }
        }
    }

    uiState.contractPrompt?.let { prompt ->
        ContractDialog(uiState = uiState, type = prompt.type, onEvent = onEvent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Filters(uiState: WorkOrderListUiState, onEvent: (WorkOrderListEvent) -> Unit, onNew: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FilterChip(
            selected = uiState.status == null,
            onClick = { onEvent(WorkOrderListEvent.StatusChanged(null)) },
            label = { Text(text = stringResource(R.string.work_orders_filter_all)) },
            modifier = Modifier.heightIn(min = Spacing.minTouchTarget),
        )
        FilterChip(
            selected = uiState.status == WorkOrderStatusDto.ISLENIYOR,
            onClick = { onEvent(WorkOrderListEvent.StatusChanged(WorkOrderStatusDto.ISLENIYOR)) },
            label = { Text(text = stringResource(R.string.work_orders_filter_processing)) },
            modifier = Modifier.heightIn(min = Spacing.minTouchTarget),
        )

        Box(modifier = Modifier.weight(1f))

        OutlinedButton(
            onClick = { onEvent(WorkOrderListEvent.ExportRequested) },
            enabled = uiState.items.isNotEmpty() && !uiState.exporting,
            modifier = Modifier.defaultMinSize(minHeight = Spacing.minTouchTarget),
        ) {
            Text(
                text = stringResource(if (uiState.exporting) R.string.work_orders_export_busy else R.string.work_orders_export),
                style = MaterialTheme.typography.labelLarge,
            )
        }

        Button(onClick = onNew, modifier = Modifier.defaultMinSize(minHeight = Spacing.minTouchTarget)) {
            Text(text = stringResource(R.string.work_order_new), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** İnen `.xlsx`, `FileProvider` üzerinden paylaşılır — `file://` URI `FileUriExposedException` fırlatır. */
private fun listeyiPaylas(context: Context, dosya: File): Intent = Intent.createChooser(
    Intent(Intent.ACTION_SEND).apply {
        type = XLSX_TIPI
        putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dosya))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    },
    dosya.name,
)

private const val XLSX_TIPI = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/** Ray ve aşama satırlarının paylaştığı aşama bilgisi. */
data class StageInfo(val type: DocumentType, val document: DocumentSummaryDto?, val state: StageState)

/**
 * Üç aşamanın sırayla hâli — webdeki `asamaHalleri` ile aynı kural.
 *
 * `DocumentStageRule` yeniden yazılmıyor: iş durumu kapısı zaten [stageEditable]'da. Buradaki tek ek
 * **sıralama**: bir aşama ancak öncekiler tamamsa etkin olabilir.
 *
 * `IPTAL` bedavaya doğru davranıyor — [stageEditable] iptal/kapandı için `false` döndüğü için iptal
 * edilmiş projede hiçbir aşama etkin görünmez; tamamlanmış belgeler yeşil kalır.
 */
private fun stageInfos(item: WorkOrderDto): List<StageInfo> {
    val belgeler = item.belgeler.filter { it.durum != DocumentStatus.IPTAL }.associateBy { it.tur }
    val projeAktif = item.durum != WorkOrderStatusDto.IPTAL && item.durum != WorkOrderStatusDto.TAMAMLANDI

    return ASAMALAR.mapIndexed { i, tur ->
        val belge = belgeler[tur]
        val onceki = if (i == 0) null else belgeler[ASAMALAR[i - 1]]
        // Bir aşama, bir önceki aşama yönetici tarafından işleme alındığında (ISLENIYOR) açılabilir;
        // fizibilite ilk aşamadır, önkoşulu yoktur (yeknesek durum makinesi).
        val acilabilir = i == 0 || onceki?.durum == DocumentStatus.ISLENIYOR
        val hal = when {
            // İşleme alınmış (ISLENIYOR) ya da tamamlanmış aşama, bölge sorumlusu için "bitti" görünür.
            belge?.durum == DocumentStatus.ISLENIYOR || belge?.durum == DocumentStatus.TAMAMLANDI -> StageState.TAMAM
            belge?.durum == DocumentStatus.ONAY_BEKLIYOR -> StageState.ETKIN
            belge == null && projeAktif && acilabilir -> StageState.ETKIN
            else -> StageState.BEKLEYEN
        }
        StageInfo(type = tur, document = belge, state = hal)
    }
}

@Composable
private fun WorkOrderCard(item: WorkOrderDto, scopeName: String, onEvent: (WorkOrderListEvent) -> Unit, onObserve: (Long) -> Unit) {
    // `rememberSaveable`: tablet döndürme sık ve §12/6 durum kaybını yasaklıyor; düz `remember`
    // her kartı döndürmede kapatırdı.
    var acik by rememberSaveable(item.id) { mutableStateOf(false) }
    val asamalar = stageInfos(item)
    val vurgu = item.durum.tone().colors().accent

    // Başlık `SP No 2026-BURSA-001` olarak yazılıyor: numaranın kendisi ne olduğunu söylemiyordu.
    SectionCard(title = "${etiketle(stringResource(R.string.field_sp_no))} ${item.spNo}", accent = vurgu) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatusBadge(text = item.durumMetni, tone = item.durum.tone())
            Text(text = item.bolgeAdi, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceMut)
            Box(modifier = Modifier.weight(1f))
            IconButton(
                onClick = { acik = !acik },
                modifier = Modifier.defaultMinSize(minWidth = Spacing.minTouchTarget, minHeight = Spacing.minTouchTarget),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_down),
                    // Açık hâl için ikinci vektör yok; aynı dosya döndürülüyor.
                    contentDescription = stringResource(
                        if (acik) R.string.work_order_collapse else R.string.work_order_expand,
                        item.spNo,
                    ),
                    modifier = Modifier.rotate(if (acik) 180f else 0f),
                )
            }
        }

        // Etiketsiz `mtc079… · EK 123 · R-123` dizisinde hangisinin ne olduğu ancak biçimden
        // tahmin ediliyordu; üçü de etiketiyle basılıyor.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Field(label = stringResource(R.string.field_scope), value = scopeName)
            Field(label = stringResource(R.string.field_kable_id), value = item.kableNosid)
            Field(label = stringResource(R.string.field_ek_araligi), value = item.ekAraligi)
            Field(label = stringResource(R.string.field_route), value = item.route)
        }

        if (item.durum == WorkOrderStatusDto.IPTAL && !item.iptalGerekce.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.work_order_cancelled, item.iptalGerekce),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )
        }

        StageRail(stages = asamalar, modifier = Modifier.padding(vertical = Spacing.sm))

        if (acik) {
            HorizontalDivider()

            asamalar.forEach { asama ->
                StageRow(
                    stage = asama,
                    editable = asama.state == StageState.ETKIN,
                    onUpdate = { onEvent(WorkOrderListEvent.StageClicked(item.id, asama.type, asama.document?.id)) },
                    onObserve = { asama.document?.let { onObserve(it.id) } },
                )
            }
        }
    }
}

@Composable
private fun StageRow(stage: StageInfo, editable: Boolean, onUpdate: () -> Unit, onObserve: () -> Unit) {
    val document = stage.document

    // "Güncelle" yalnızca açık belgede anlamlı: onaylanmış belge bir daha değişmez ve sunucu denemeyi
    // 409 ile reddeder. Onay bekleyen belge **düzenlenebilir** — bölge sorumlusu yönetici onaylayana
    // kadar düzeltme yapabiliyor (Faz V).
    val updatable = editable && (document == null || document.durum.duzenlenebilirMi)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(text = stage.type.label(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            document?.let { StatusBadge(text = it.durumMetni, tone = it.durum.tone()) }
        }

        Text(
            text = when {
                document != null -> stringResource(
                    R.string.stage_summary,
                    document.sonGuncelleyenAdSoyad.orEmpty(),
                    document.toplamGosterim,
                )
                // Sıradaki adım olduğunu söylemek, kapalı bir düğmeye bakmaktan iyidir.
                stage.state == StageState.ETKIN -> stringResource(R.string.stage_next)
                else -> stringResource(R.string.stage_waiting)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceMut,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Button(
                onClick = onUpdate,
                enabled = updatable,
                modifier = Modifier.defaultMinSize(minHeight = Spacing.minTouchTarget),
            ) {
                Text(text = stringResource(if (document == null) R.string.action_start else R.string.action_update))
            }
            OutlinedButton(
                onClick = onObserve,
                enabled = document != null,
                modifier = Modifier.defaultMinSize(minHeight = Spacing.minTouchTarget),
            ) {
                Text(text = stringResource(R.string.action_observe))
            }
        }
    }
}

/**
 * Sözleşme seçici — belge açılırken sözleşme **zorunlu** (Faz N): belge daima bir fiyat sürümüne
 * çivilidir ve o sürüm seçilen sözleşmeden gelir.
 */
@Composable
private fun ContractDialog(uiState: WorkOrderListUiState, type: DocumentType, onEvent: (WorkOrderListEvent) -> Unit) {
    var secili by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { onEvent(WorkOrderListEvent.ContractPromptDismissed) },
        title = { Text(text = stringResource(R.string.contract_select_title, type.label())) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(text = stringResource(R.string.contract_select_hint), color = OnSurfaceMut)
                DropdownSelect(
                    label = stringResource(R.string.field_contract),
                    selectedLabel = uiState.contracts.firstOrNull { it.kod == secili }?.ad,
                    placeholder = stringResource(R.string.placeholder_select),
                    options = uiState.contracts.map { DropdownOption(it.kod, it.ad) },
                    onSelect = { secili = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = secili.isNotBlank() && !uiState.busy,
                onClick = { onEvent(WorkOrderListEvent.ContractChosen(secili)) },
            ) {
                Text(text = stringResource(R.string.action_open))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(WorkOrderListEvent.ContractPromptDismissed) }) {
                Text(text = stringResource(R.string.action_give_up))
            }
        },
    )
}

@Composable
private fun Field(label: String, value: String?) {
    Column {
        Text(text = etiketle(label), style = MaterialTheme.typography.bodySmall, color = OnSurfaceMut, fontWeight = FontWeight.Bold)
        Text(
            text = value?.takeIf { it.isNotBlank() } ?: stringResource(R.string.bos_deger),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
