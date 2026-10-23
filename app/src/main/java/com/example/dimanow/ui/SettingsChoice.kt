package com.example.dimanow.ui

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.DisplayVocabulary
import com.example.dimanow.theme.DimaShapes
import com.example.dimanow.theme.DimaTheme
import com.example.dimanow.ui.motion.DimaMotion

/**
 * M3 connected button group for one exclusive choice (D-103), replacing segmented buttons, which Material 3
 * no longer recommends: equal-width toggle buttons in one color style, the selected one morphing to the
 * checked shape, at least 48dp tall. Each button reads as a radio item with its selected state, so screen
 * readers and tests see the same semantics the segmented row exposed.
 */
@Composable
internal fun <T> DimaConnectedChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    itemTag: (T) -> String? = { null },
) {
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, (value, label) ->
            val checked = value == selected
            ToggleButton(
                checked = checked,
                // A connected single-choice group never ends with nothing selected.
                onCheckedChange = { onSelect(value) },
                enabled = enabled,
                // D-114: unselected buttons sit one tone above the card or page instead of sinking below it.
                colors = ToggleButtonDefaults.colors(containerColor = DimaTheme.colors.cardInner),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .then(itemTag(value)?.takeIf(String::isNotEmpty)?.let { Modifier.testTag(it) } ?: Modifier)
                    .clearAndSetSemantics {
                        role = Role.RadioButton
                        this.selected = checked
                        text = AnnotatedString(label)
                        onClick { onSelect(value); true }
                        if (!enabled) disabled()
                    },
            ) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Single-choice settings control: a connected button group while labels fit, otherwise radio rows. */
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
            DimaConnectedChoice(options, selected, onSelect, enabled = enabled, itemTag = itemTag)
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

/** A group title in Settings; screen readers can jump between groups by heading (D-102). */
@Composable
internal fun SettingsSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 4.dp, top = 8.dp).semantics { heading() },
    )
}

/**
 * A settings section that opens in place (D-094(6)): the whole row toggles it, the chevron turns,
 * and screen readers hear its expanded state and get expand/collapse actions.
 */
@Composable
internal fun SettingsExpander(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    supportingColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = DimaMotion.spatialDefault(),
        label = "settings_expander_chevron",
    )
    Surface(
        onClick = onToggle,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                stateDescription = if (expanded) "펼쳐짐" else "접힘"
                if (expanded) collapse { onToggle(); true } else expand { onToggle(); true }
            },
        shape = DimaShapes.Card,
        color = DimaTheme.colors.card,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                supporting?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = supportingColor)
                }
            }
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(rotation),
            )
        }
    }
}

/**
 * GPS-free test location (D-061): a switch plus every campus zone as one visible single choice,
 * never a horizontally scrolling row that hides options (D-094(6)).
 */
@Composable
internal fun LocationTestCard(
    testMode: Boolean,
    testZone: CampusZoneId,
    onTestModeChange: (Boolean) -> Unit,
    onTestZone: (CampusZoneId) -> Unit,
    testTransitStopNumber: String?,
    onTestTransitStop: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = DimaShapes.Card,
        colors = CardDefaults.elevatedCardColors(containerColor = DimaTheme.colors.card),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("위치 테스트", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(value = testMode, role = Role.Switch, onValueChange = onTestModeChange),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("테스트 모드", style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (testMode) "GPS 반영 안 함" else "현재 위치 자동 판정",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = testMode, onCheckedChange = null)
            }
            Text("테스트 위치", style = MaterialTheme.typography.labelLarge)
            SettingsChoice(
                options = CampusZoneId.entries.map { zone -> zone to DisplayVocabulary.zoneName(zone) },
                selected = testZone.takeIf { testMode },
                onSelect = { zone -> zone?.let(onTestZone) },
                enabled = testMode,
                itemTag = { zone -> "test_zone_${zone?.name}" },
            )
            if (testMode) {
                TransitStopTestControls(
                    testStopNumber = testTransitStopNumber,
                    onChange = onTestTransitStop,
                )
            }
        }
    }
}
