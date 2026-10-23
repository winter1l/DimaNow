package com.example.dimanow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import com.example.dimanow.theme.DimaShapes
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.dimanow.theme.DimaTheme
import java.time.DayOfWeek
import java.time.LocalDate

/** App layout decisions, not prescribed Material component dimensions. */
internal object DimaLayout {
    /** Widest a screen's column grows; wider windows center it (D-104 large screens). */
    val readingWidth = 840.dp
    /** From this window width (the M3 medium class) the shell shows a navigation rail, not the bottom bar (D-104). */
    val railMinWidth = 600.dp
    /** Space between cards and sections (D-079; D-105 restores it after D-104's 8dp). */
    val sectionGap = 16.dp
}

/**
 * M3 Expressive segmented list (D-104): rows sit [ListItemDefaults.SegmentedGap] apart, the group's outer
 * corners are large and the inner ones small ([ListItemDefaults.segmentedShapes]), and each row takes the
 * card tone (`DimaTheme.colors.card`, D-114) so a group reads like the cards around it on the page.
 */
@Composable
internal fun DimaSegmentedList(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap), content = content)
}

@Composable
internal fun dimaSegmentedListColors(): ListItemColors =
    ListItemDefaults.segmentedColors(containerColor = DimaTheme.colors.card)

/**
 * An icon in a tonal circle (D-114): the leading mark of card headers and list rows, so each card and row reads as
 * its own item at a glance, as list rows in the system Settings app do. Decorative: the row's text names it.
 */
@Composable
internal fun DimaIconBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    muted: Boolean = false,
) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        // D-119: a muted row's mark drops the accent tone, as the row itself does.
        color = if (muted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(size * 0.55f))
        }
    }
}

/**
 * The filled `오늘` pill shared by the meal day heading and the timetable weekday heading (D-102),
 * in tertiary: M3 gives badge-like contrasting accents to the tertiary role (D-104).
 */
@Composable
internal fun TodayBadge(modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = DimaShapes.Badge, color = MaterialTheme.colorScheme.tertiary) {
        Text(
            text = "오늘",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Stable Material 1.4 selection control; never imitates unavailable connected-toggle APIs. */
@Composable
internal fun DimaDaySelector(
    days: List<DayOfWeek>,
    selected: DayOfWeek,
    onSelect: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
    itemTag: (DayOfWeek) -> String? = { null },
    today: DayOfWeek? = null,
    supportingLabel: (DayOfWeek) -> String? = { null },
) = DimaDayCellSelector(
    items = days,
    selected = selected,
    onSelect = onSelect,
    weekday = { it },
    modifier = modifier,
    itemTag = itemTag,
    isToday = { it == today },
    supportingLabel = supportingLabel,
)

/**
 * The same selector for concrete dates (the meal week, D-094(11)): each cell shows the weekday
 * initial over the day of month, exposes its selected state and announces `오늘` as its state.
 */
@Composable
internal fun DimaDateSelector(
    dates: List<LocalDate>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    today: LocalDate,
    modifier: Modifier = Modifier,
    itemTag: (LocalDate) -> String? = { null },
) = DimaDayCellSelector(
    items = dates,
    selected = selected,
    onSelect = onSelect,
    weekday = { it.dayOfWeek },
    modifier = modifier,
    itemTag = itemTag,
    isToday = { it == today },
    supportingLabel = { it.dayOfMonth.toString() },
)

@Composable
private fun <T> DimaDayCellSelector(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    weekday: (T) -> DayOfWeek,
    modifier: Modifier,
    itemTag: (T) -> String?,
    isToday: (T) -> Boolean,
    supportingLabel: (T) -> String?,
) {
    if (items.isEmpty()) return
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLargeEmphasized
    val dateStyle = MaterialTheme.typography.labelSmall
    val dateLabels = items.associateWith(supportingLabel)
    val widestTextPx = remember(items, dateLabels, labelStyle, dateStyle, textMeasurer) {
        items.maxOf { item ->
            val labelWidth = textMeasurer.measure(koreanWeekdayLabel(weekday(item)).take(1), labelStyle).size.width
            val dateWidth = dateLabels[item]?.let { textMeasurer.measure(it, dateStyle).size.width } ?: 0
            maxOf(labelWidth, dateWidth)
        }
    }
    // Material3 1.4 FilterChip has 8dp outer + 8dp label padding on each side.
    // Measure scaled text separately from the 48dp minimum touch target.
    val minimumCellWidth = (with(density) { widestTextPx.toDp() } + 32.dp).coerceAtLeast(48.dp)
    // App layout decisions: a 4dp column gap and balanced extra rows keep every day visible.
    // 360dp fits seven 48dp targets; narrow dialogs and large text wrap without hiding weekends.
    val columnGap = 4.dp
    BoxWithConstraints(modifier.fillMaxWidth().selectableGroup()) {
        val maximumColumns = ((maxWidth + columnGap) / (minimumCellWidth + columnGap))
            .toInt().coerceIn(1, items.size)
        val rowCount = (items.size + maximumColumns - 1) / maximumColumns
        val columns = (items.size + rowCount - 1) / rowCount
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items.chunked(columns).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(columnGap)) {
                    rowItems.forEach { item ->
                        val day = weekday(item)
                        val date = dateLabels[item]
                        val isSelected = item == selected
                        val today = isToday(item)
                        val description = listOfNotNull(koreanWeekdayLabel(day), date).joinToString(" ")
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelect(item) },
                            // Grows with the font scale; 52dp is only the resting minimum.
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                                .then(itemTag(item)?.let { Modifier.testTag(it) } ?: Modifier)
                                .semantics {
                                    contentDescription = description
                                    // One of several exclusive cells, announced like a tab with its selection.
                                    role = Role.Tab
                                    if (today) stateDescription = if (isSelected) "오늘, 선택됨" else "오늘"
                                },
                            shape = DimaShapes.Tile,
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (today && !isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                selectedBorderColor = Color.Transparent,
                                borderWidth = 1.dp,
                            ),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = DimaTheme.colors.card,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                            elevation = FilterChipDefaults.filterChipElevation(
                                elevation = 0.dp,
                                pressedElevation = 1.dp,
                            ),
                            label = {
                                Column(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        text = koreanWeekdayLabel(day).take(1),
                                        style = if (isSelected) labelStyle else MaterialTheme.typography.labelLarge,
                                    )
                                    date?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier.size(5.dp).background(
                                            color = if (today) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = CircleShape,
                                        ),
                                    )
                                }
                            },
                        )
                    }
                    repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
