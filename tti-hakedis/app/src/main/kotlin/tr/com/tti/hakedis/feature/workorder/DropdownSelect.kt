package tr.com.tti.hakedis.feature.workorder

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import tr.com.tti.hakedis.designsystem.Spacing

/** Açılır listedeki tek seçenek. [key] domain tarafındaki sabit, [label] kullanıcıya görünen metin. */
data class DropdownOption(val key: String, val label: String)

/**
 * Bu ekranda üç kez kullanılan açılır seçim alanı. İkinci bir ekranda da gerekirse
 * `designsystem/` altına taşınacak (PHASE.md Faz 0 notu).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownSelect(
    label: String,
    selectedLabel: String?,
    placeholder: String,
    options: List<DropdownOption>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorText: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedLabel.orEmpty(),
            onValueChange = { },
            readOnly = true,
            enabled = enabled,
            isError = errorText != null,
            label = { Text(text = label) },
            placeholder = { Text(text = placeholder) },
            textStyle = MaterialTheme.typography.bodyLarge,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            supportingText = errorText?.let { message ->
                { Text(text = message, color = MaterialTheme.colorScheme.error) }
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .heightIn(min = Spacing.fieldHeight),
        )

        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = option.label, style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        expanded = false
                        onSelect(option.key)
                    },
                    modifier = Modifier.heightIn(min = Spacing.minTouchTarget),
                )
            }
        }
    }
}
