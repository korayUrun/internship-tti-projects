package tr.com.tti.hakedis.feature.workorder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tr.com.tti.hakedis.R
import tr.com.tti.hakedis.data.api.CableDto
import tr.com.tti.hakedis.designsystem.AppTextField
import tr.com.tti.hakedis.designsystem.ErrorBanner
import tr.com.tti.hakedis.designsystem.FieldColumns
import tr.com.tti.hakedis.designsystem.FormContainer
import tr.com.tti.hakedis.designsystem.LoadingBox
import tr.com.tti.hakedis.designsystem.OnSurfaceMut
import tr.com.tti.hakedis.designsystem.PrimaryButton
import tr.com.tti.hakedis.designsystem.SectionCard
import tr.com.tti.hakedis.designsystem.Spacing
import tr.com.tti.hakedis.designsystem.appTopBarColors
import tr.com.tti.hakedis.feature.LoadState

/** Ekran 1 — yeni proje. Bölge salt okunur: kullanıcı tek bir bölgeden sorumludur. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewWorkOrderScreen(uiState: NewWorkOrderUiState, onEvent: (NewWorkOrderEvent) -> Unit, onCancel: () -> Unit, onOpenList: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.work_order_new)) },
                colors = appTopBarColors(),
                navigationIcon = {
                    TextButton(onClick = onCancel, modifier = Modifier.defaultMinSize(minHeight = Spacing.minTouchTarget)) {
                        Text(text = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val content = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(Spacing.md)

        when {
            uiState.loadState == LoadState.Loading -> LoadingBox(modifier = content)

            uiState.loadState == LoadState.Failed -> Column(modifier = content) {
                ErrorBanner(
                    message = uiState.error ?: stringResource(R.string.error_generic),
                    onRetry = { onEvent(NewWorkOrderEvent.Retry) },
                )
            }

            else -> Form(uiState = uiState, onEvent = onEvent, onCancel = onCancel, modifier = content)
        }
    }

    /*
     * "Bu proje açıldı" penceresi (kullanıcı isteği, 4 Ağustos 2026). Kapatma yolu tek: proje
     * listesine gitmek. `onDismissRequest` boş bırakılıyor — dışarı tıklayıp pencereyi kapatan
     * kullanıcı, oluşmuş bir projenin formunda kalır ve aynı formu ikinci kez gönderebilirdi.
     */
    if (uiState.createdId != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(text = stringResource(R.string.work_order_created_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(text = stringResource(R.string.work_order_created, uiState.createdSpNo.orEmpty()))
                    uiState.warnings.forEach { uyari ->
                        Text(text = uyari.mesaj, color = MaterialTheme.colorScheme.error)
                    }
                    Text(text = stringResource(R.string.work_order_created_next), color = OnSurfaceMut)
                }
            },
            confirmButton = {
                PrimaryButton(text = stringResource(R.string.work_order_open), onClick = onOpenList)
            },
        )
    }
}

@Composable
private fun Form(uiState: NewWorkOrderUiState, onEvent: (NewWorkOrderEvent) -> Unit, onCancel: () -> Unit, modifier: Modifier) {
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        FormContainer {
            uiState.error?.let { ErrorBanner(message = it) }

            SectionCard(title = stringResource(R.string.work_order_section_definition)) {
                OutlinedTextField(
                    value = uiState.regionName,
                    onValueChange = { },
                    readOnly = true,
                    enabled = false,
                    label = { Text(text = stringResource(R.string.field_region)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Spacing.fieldHeight),
                )
                DropdownSelect(
                    label = stringResource(R.string.field_scope),
                    selectedLabel = uiState.scopes.firstOrNull { it.kod == uiState.scopeCode }?.ad,
                    placeholder = stringResource(R.string.dropdown_placeholder),
                    options = uiState.scopes.map { DropdownOption(it.kod, it.ad) },
                    onSelect = { onEvent(NewWorkOrderEvent.ScopeChanged(it)) },
                )
                Text(
                    text = stringResource(R.string.work_order_generated_fields_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceMut,
                )
            }

            CableSection(uiState = uiState, onEvent = onEvent)

            SectionCard(title = stringResource(R.string.work_order_section_identifiers)) {
                // Route + Ek Aralığı kısa alanlar — tabletde yan yana, dar ekranda alt alta akar.
                FieldColumns(
                    left = {
                        AppTextField(
                            label = stringResource(R.string.field_route),
                            value = uiState.route,
                            onValueChange = { onEvent(NewWorkOrderEvent.RouteChanged(it)) },
                        )
                    },
                    right = {
                        AppTextField(
                            label = stringResource(R.string.field_ek_araligi),
                            value = uiState.ekAraligi,
                            onValueChange = { onEvent(NewWorkOrderEvent.EkAraligiChanged(it)) },
                        )
                    },
                )

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    PrimaryButton(
                        text = if (uiState.saving) {
                            stringResource(R.string.work_order_saving)
                        } else {
                            stringResource(R.string.work_order_save)
                        },
                        onClick = { onEvent(NewWorkOrderEvent.Submitted) },
                        enabled = !uiState.missing && !uiState.saving,
                    )
                    TextButton(onClick = onCancel, modifier = Modifier.defaultMinSize(minHeight = Spacing.minTouchTarget)) {
                        Text(text = stringResource(R.string.action_cancel))
                    }
                }

                if (uiState.missing) {
                    Text(
                        // Metin kapsama bağlı: "kable listeden seçilmelidir" cümlesi kablonun zorunlu
                        // olmadığı kapsamda yanlış olurdu (Faz W).
                        text = if (uiState.cableRequired) {
                            stringResource(R.string.work_order_missing)
                        } else {
                            stringResource(R.string.work_order_missing_no_cable)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun CableSection(uiState: NewWorkOrderUiState, onEvent: (NewWorkOrderEvent) -> Unit) {
    SectionCard(title = stringResource(R.string.work_order_section_cable)) {
        // Kablo kartı kapsama göre **iki ayrı moddur**: zorunlu kapsamlarda envanter araması; Yeni
        // Tesis'te Kablo ID'yi sistem üretir (mtc…) ve uçlar elle, zorunlu girilir (12 Ağustos 2026).
        if (!uiState.cableRequired) {
            Text(
                text = stringResource(R.string.work_order_cable_auto_note),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceMut,
            )
            OutlinedTextField(
                value = stringResource(R.string.work_order_cable_auto_value),
                onValueChange = { },
                readOnly = true,
                enabled = false,
                label = { Text(text = stringResource(R.string.field_kable_id)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.fieldHeight),
            )
            EndpointsSection(uiState = uiState, onEvent = onEvent)
            return@SectionCard
        }

        val selected = uiState.selectedCable
        if (selected != null) {
            Text(text = selected.nosid, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.cable_end_a, selected.adresA ?: stringResource(R.string.bos_deger)),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceMut,
            )
            Text(
                text = stringResource(R.string.cable_end_b, selected.adresB ?: stringResource(R.string.bos_deger)),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceMut,
            )
            TextButton(
                onClick = { onEvent(NewWorkOrderEvent.CableSelected(null)) },
                modifier = Modifier.defaultMinSize(minHeight = Spacing.minTouchTarget),
            ) {
                Text(text = stringResource(R.string.action_change))
            }
            return@SectionCard
        }

        AppTextField(
            label = stringResource(R.string.field_kable_search),
            value = uiState.cableQuery,
            onValueChange = { onEvent(NewWorkOrderEvent.CableQueryChanged(it)) },
        )
        // Arama sunucuda (Faz O): envanterin tamamı çekilmiyor, kullanıcı `mtc` sonrasına numarayı
        // yazdıkça sonuç geliyor.
        when {
            uiState.queryTooShort -> Text(
                text = stringResource(R.string.cable_query_too_short, NewWorkOrderUiState.EN_AZ_ARAMA),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceMut,
            )

            uiState.searching -> Text(
                text = stringResource(R.string.cable_searching),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceMut,
            )

            uiState.cables.isEmpty() -> Text(
                text = stringResource(R.string.cable_none),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceMut,
            )

            else -> {
                if (uiState.cablesTruncated) {
                    Text(
                        text = stringResource(R.string.cable_truncated, NewWorkOrderUiState.ARAMA_SINIRI),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceMut,
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = CABLE_LIST_HEIGHT)) {
                    items(items = uiState.cables, key = { it.nosid }) { cable ->
                        CableRow(cable = cable, onSelect = { onEvent(NewWorkOrderEvent.CableSelected(cable)) })
                    }
                }
            }
        }
    }
}

/**
 * Yeni Tesis projesinde uç konumlarının elle girilmesi — **zorunlu** (12 Ağustos 2026).
 *
 * Her zaman görünür (kablo envanterden gelmediği için adresi kullanıcı girmeli). Ülke/şehir/adres
 * zorunlu, diğer segmentler opsiyonel. Alan adları sunucudaki `KonumYolu.ALANLAR` ile birebir aynı;
 * yolu (`>>`, bina `>` kat) **sunucu** kurar.
 */
@Composable
private fun EndpointsSection(uiState: NewWorkOrderUiState, onEvent: (NewWorkOrderEvent) -> Unit) {
    Text(
        text = stringResource(R.string.work_order_ends_manual_note),
        style = MaterialTheme.typography.bodyMedium,
        color = OnSurfaceMut,
    )
    // Uç A ve Uç B tabletde yan yana iki sütun; dar ekranda alt alta akar (FieldColumns, §8).
    FieldColumns(
        left = { EndpointFields(baslik = R.string.cable_end_a_label, yon = "A", degerler = uiState.endpointA, onEvent = onEvent) },
        right = { EndpointFields(baslik = R.string.cable_end_b_label, yon = "B", degerler = uiState.endpointB, onEvent = onEvent) },
    )
}

@Composable
private fun EndpointFields(baslik: Int, yon: String, degerler: Map<String, String>, onEvent: (NewWorkOrderEvent) -> Unit) {
    Text(
        text = stringResource(baslik),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = Spacing.sm),
    )
    KONUM_ALANLARI.forEach { (alan, etiket) ->
        val zorunlu = alan in KONUM_ZORUNLU
        val deger = degerler[alan].orEmpty()
        AppTextField(
            // Zorunlu alanlar (ülke/şehir/adres) `*` ile; kural sunucudaki WorkOrderValidator'da.
            label = stringResource(etiket) + if (zorunlu) " *" else "",
            value = deger,
            onValueChange = { onEvent(NewWorkOrderEvent.EndpointChanged(yon, alan, it)) },
        )
        if (zorunlu && deger.isBlank()) {
            Text(
                text = stringResource(R.string.field_required, stringResource(etiket)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun CableRow(cable: CableDto, onSelect: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .heightIn(min = Spacing.minTouchTarget)
            .padding(vertical = Spacing.sm),
    ) {
        Text(text = cable.nosid, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        // Konum yolu iki uç için 400 karakteri aşabiliyor; listede kırpılır, seçilince yukarıda
        // tam hâliyle görünür.
        Text(
            text = listOfNotNull(cable.adresA, cable.adresB).joinToString(" → "),
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceMut,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

private val CABLE_LIST_HEIGHT = 320.dp

/**
 * Konum yolu segmentleri — alan adları **sunucudaki `KonumYolu.ALANLAR` ile birebir aynı** olmak
 * zorunda; anahtarlar sunucuya böyle gidiyor ve yolu sunucu kuruyor (web'deki `SEGMENTLER`'in ikizi).
 */
private val KONUM_ALANLARI = listOf(
    "country" to R.string.segment_country,
    "city" to R.string.segment_city,
    "address" to R.string.segment_address,
    "building" to R.string.segment_building,
    "floor" to R.string.segment_floor,
    "room" to R.string.segment_room,
    "cage" to R.string.segment_cage,
    "footprint" to R.string.segment_footprint,
    "rackspace" to R.string.segment_rackspace,
)

/**
 * Yeni Tesis'te dolu olması zorunlu segmentler (ülke/şehir/adres) — sunucudaki
 * `WorkOrderValidator.ZORUNLU_UC_ALANLARI` ve web ile aynı.
 */
private val KONUM_ZORUNLU = setOf("country", "city", "address")
