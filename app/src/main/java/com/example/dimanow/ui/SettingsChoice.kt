package com.example.dimanow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/** Stable Material 3 1.4 single-choice control; long labels remain readable as radio rows. */
@Composable
internal fun <T> SettingsChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    itemTag: (T) -> String = { "" },
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelLarge
    val widestLabel = options.maxOfOrNull { (_, label) ->
        measurer.measure(AnnotatedString(label), style = labelStyle, softWrap = false).size.width
    } ?: 0
    // Reserve the standard selected icon and content padding rather than truncating an option.
    val requiredCellWidth = with(density) { widestLabel.toDp() } + 56.dp
    BoxWithConstraints(modifier.widthIn(max = 480.dp).fillMaxWidth()) {
        if (options.size in 2..5 && requiredCellWidth * options.size <= maxWidth) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = selected == value,
                        onClick = { onSelect(value) },
                        enabled = enabled,
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        modifier = Modifier.heightIn(min = 48.dp).testTag(itemTag(value)),
                        label = { Text(label) },
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().selectableGroup()) {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag(itemTag(value))
                            .selectable(
                                selected = selected == value,
                                enabled = enabled,
                                role = Role.RadioButton,
                                onClick = { onSelect(value) },
                            ).padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == value, onClick = null, enabled = enabled)
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (enabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
