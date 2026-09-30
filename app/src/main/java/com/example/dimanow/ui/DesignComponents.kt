package com.example.dimanow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek

/** App layout decisions, not prescribed Material component dimensions. */
internal object DimaLayout {
    val readingWidth = 840.dp
    val pageMargin = 16.dp
    val sectionGap = 16.dp
}

/** Navigation changes placement at the Material medium breakpoint; its role and actions stay stable. */
@Composable
internal fun DimaNavigationShell(
    page: AppPage,
    pages: List<AppPage>,
    showNavigation: Boolean,
    onSelect: (AppPage) -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
        val useRail = maxWidth >= 600.dp
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            bottomBar = {
                if (showNavigation && !useRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
                        pages.forEach { item ->
                            NavigationBarItem(
                                modifier = Modifier.testTag("nav_${item.name}"),
                                selected = page == item, onClick = { onSelect(item) },
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = { Text(item.title, fontWeight = if (page == item) FontWeight.Bold else FontWeight.Medium) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize()) {
                if (showNavigation && useRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).testTag("navigation_rail"),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        pages.forEach { item ->
                            NavigationRailItem(
                                modifier = Modifier.testTag("nav_${item.name}"),
                                selected = page == item, onClick = { onSelect(item) },
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = { Text(item.title) },
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight().consumeWindowInsets(padding)) { content(padding) }
            }
        }
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
) {
    if (days.isEmpty()) return
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
    val dateStyle = MaterialTheme.typography.labelSmall
    val dateLabels = days.associateWith(supportingLabel)
    val widestTextPx = remember(days, dateLabels, labelStyle, dateStyle, textMeasurer) {
        days.maxOf { day ->
            val labelWidth = textMeasurer.measure(koreanWeekdayLabel(day).take(1), labelStyle).size.width
            val dateWidth = dateLabels[day]?.let { textMeasurer.measure(it, dateStyle).size.width } ?: 0
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
            .toInt().coerceIn(1, days.size)
        val rowCount = (days.size + maximumColumns - 1) / maximumColumns
        val columns = (days.size + rowCount - 1) / rowCount
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            days.chunked(columns).forEach { rowDays ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(columnGap)) {
                    rowDays.forEach { day ->
                        val date = dateLabels[day]
                        val description = listOfNotNull(koreanWeekdayLabel(day), date, "오늘".takeIf { day == today }).joinToString(" ")
                        val isSelected = day == selected
                        val isToday = day == today
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelect(day) },
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                                .then(itemTag(day)?.let { Modifier.testTag(it) } ?: Modifier)
                                .semantics { contentDescription = description },
                            shape = RoundedCornerShape(16.dp),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isToday && !isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent,
                                selectedBorderColor = Color.Transparent,
                                borderWidth = 1.dp,
                            ),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    date?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        )
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier.size(5.dp).background(
                                            color = if (isToday) {
                                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
                                            } else Color.Transparent,
                                            shape = CircleShape,
                                        ),
                                    )
                                }
                            },
                        )
                    }
                    repeat(columns - rowDays.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
