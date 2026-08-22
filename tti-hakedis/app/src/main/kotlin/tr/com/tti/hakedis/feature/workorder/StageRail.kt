package tr.com.tti.hakedis.feature.workorder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tr.com.tti.hakedis.R
import tr.com.tti.hakedis.designsystem.OnSurfaceMut
import tr.com.tti.hakedis.designsystem.Outline
import tr.com.tti.hakedis.designsystem.Spacing
import tr.com.tti.hakedis.designsystem.StatusTone
import tr.com.tti.hakedis.designsystem.Surface
import tr.com.tti.hakedis.designsystem.colors
import tr.com.tti.hakedis.feature.label

/** Bir aşamanın ray üzerindeki hâli — webdeki `AsamaHali` ile aynı bölümleme. */
enum class StageState { TAMAM, ETKIN, BEKLEYEN }

/**
 * Üç aşamalı ray: tamam (dolu daire + tik) · etkin (kalın halka) · bekleyen (boş daire).
 * Bağlayıcı, **önceki aşama tamamsa** yeşil.
 *
 * Eskiden ilerleme iki **Unicode kutucuk karakteriyle** gösteriliyordu (Düzeltme Turu 4'te kalktı):
 * görünüm cihazın fontuna bağlıydı ve üç aşamanın **sırası** hiç okunmuyordu.
 */
@Composable
fun StageRail(stages: List<StageInfo>, modifier: Modifier = Modifier) {
    val railLabel = stringResource(R.string.stage_rail_label)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = railLabel },
    ) {
        stages.forEachIndexed { index, stage ->
            StageNode(stage = stage)
            if (index < stages.size - 1) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = Spacing.sm)
                        .height(2.dp)
                        .background(if (stage.state == StageState.TAMAM) StatusTone.TAMAM.colors().accent else Outline),
                )
            }
        }
    }
}

/** Ray düğümü ve etiketi. */
@Composable
private fun StageNode(stage: StageInfo) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        StageDot(state = stage.state)
        Text(
            text = stage.type.label(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (stage.state == StageState.BEKLEYEN) FontWeight.Normal else FontWeight.Bold,
            color = if (stage.state == StageState.BEKLEYEN) OnSurfaceMut else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun StageDot(state: StageState) {
    when (state) {
        StageState.TAMAM -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(NODE)
                .background(StatusTone.TAMAM.colors().accent, CircleShape),
        ) {
            // Dekoratif: aşamanın durumu hemen altındaki etiketle birlikte zaten okunuyor.
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = Surface,
                modifier = Modifier.size(14.dp),
            )
        }

        StageState.ETKIN -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(NODE)
                .border(3.dp, StatusTone.ISLENIYOR.colors().accent, CircleShape),
        ) {
            Box(modifier = Modifier.size(8.dp).background(StatusTone.ISLENIYOR.colors().accent, CircleShape))
        }

        StageState.BEKLEYEN -> Box(
            modifier = Modifier
                .size(NODE)
                .border(2.dp, Outline, CircleShape),
        )
    }
}

private val NODE = 24.dp
