package com.example.dimanow.ui

import com.example.dimanow.theme.DimaTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.dimanow.theme.DimaShapes

/**
 * Visual role of one full-timetable chip, shared by the campus shuttle and 4402 (D-094(5), (10)).
 * The order is the precedence: a last departure keeps its warning colour (D-016) even when past.
 */
internal enum class TimetableChipTone { NEXT_LAST, LAST, NEXT, SECOND, FIRST, PAST, UPCOMING }

internal data class TimetableChipSpec(
    val key: Int,
    val text: String,
    /** What a screen reader says; the meaning is never carried by colour alone (D-094(10)). */
    val description: String,
    val tone: TimetableChipTone,
)

internal fun timetableChipTone(
    isFirst: Boolean,
    isLast: Boolean,
    isPast: Boolean,
    isNext: Boolean,
    isSecond: Boolean,
): TimetableChipTone = when {
    isNext && isLast -> TimetableChipTone.NEXT_LAST
    isLast -> TimetableChipTone.LAST
    isNext -> TimetableChipTone.NEXT
    isSecond -> TimetableChipTone.SECOND
    isFirst -> TimetableChipTone.FIRST
    isPast -> TimetableChipTone.PAST
    else -> TimetableChipTone.UPCOMING
}

/** e.g. `다음 출발 21:30`, `지난 시간 17:30`, `18:55, 운동장 전환`, `21:50, 막차`. */
internal fun timetableChipDescription(
    clock: String,
    isPast: Boolean,
    isNext: Boolean,
    markers: List<String>,
): String = buildString {
    when {
        isNext -> append("다음 출발 ")
        isPast -> append("지난 시간 ")
    }
    append(clock)
    markers.forEach { append(", ").append(it) }
}

/** First/last marker; a sole departure is `첫차·막차` as elsewhere in the app. */
internal fun serviceMarkers(isFirst: Boolean, isLast: Boolean): List<String> = listOfNotNull(
    when {
        isFirst && isLast -> "첫차·막차"
        isFirst -> "첫차"
        isLast -> "막차"
        else -> null
    },
)

/**
 * Countdown-then-marker line of a next-departure capsule (D-069): minutes only within one hour,
 * otherwise `다음 출발` and the clock line beneath carries the time.
 */
internal fun departureCountdownLabel(minutesLeft: Long, markers: List<String>): String = buildString {
    append(
        when {
            minutesLeft > 60 -> "다음 출발"
            minutesLeft <= 0 -> "곧 출발"
            else -> "${minutesLeft}분 후"
        },
    )
    markers.forEach { append(" · ").append(it) }
}

/** Campus shuttle and 4402 next-departure fills (D-083). */
@Composable
internal fun shuttleCapsuleColors(isNearest: Boolean, isLastService: Boolean): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    val warning = DimaTheme.colors
    // D-104: last service uses the defined warning roles instead of the error roles (D-016/D-083)
    return when {
        isNearest && isLastService -> warning.warning to warning.onWarning
        isLastService -> warning.warningContainer to warning.onWarningContainer
        isNearest -> colors.primary to colors.onPrimary
        else -> colors.secondaryContainer to colors.onSecondaryContainer
    }
}

/**
 * Always-visible horizontal full timetable. It opens with the preceding departure at the start so
 * the next one is in view (D-068), and keeps later manual scrolling until [stateKey] changes.
 */
@Composable
internal fun TimetableChipRow(
    chips: List<TimetableChipSpec>,
    initialIndex: Int,
    stateKey: Any,
    modifier: Modifier = Modifier,
) {
    // Built at the right index from the start so a day switch never needs a scrollToItem relayout.
    val state = remember(stateKey) {
        LazyListState(firstVisibleItemIndex = (initialIndex - 1).coerceAtLeast(0))
    }
    LazyRow(
        state = state,
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(chips, key = { it.key }) { chip -> TimetableChip(chip) }
    }
}

/**
 * One departure of the full timetable as a plain time label (D-104): the times cannot be tapped, so
 * they no longer look like M3 chips. Colour and weight keep the meaning that the description also
 * states: next in primary, the one after in the default text colour, last service in the warning
 * colour, the first train in tertiary and past times in the quieter variant colour.
 */
@Composable
private fun TimetableChip(chip: TimetableChipSpec) {
    val colors = MaterialTheme.colorScheme
    val warning = DimaTheme.colors
    val color = when (chip.tone) {
        TimetableChipTone.NEXT_LAST, TimetableChipTone.LAST -> warning.warning
        TimetableChipTone.NEXT -> colors.primary
        TimetableChipTone.SECOND, TimetableChipTone.UPCOMING -> colors.onSurface
        TimetableChipTone.FIRST -> colors.tertiary
        TimetableChipTone.PAST -> colors.onSurfaceVariant
    }
    val emphasized = chip.tone == TimetableChipTone.NEXT || chip.tone == TimetableChipTone.NEXT_LAST
    Box(
        contentAlignment = Alignment.Center,
        // Grows with the font scale instead of clipping at a fixed height; the spoken node spans the
        // whole 32dp label, padding included.
        modifier = Modifier
            .semantics(mergeDescendants = true) { contentDescription = chip.description }
            .heightIn(min = 32.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Text(
            text = chip.text,
            style = (if (emphasized) MaterialTheme.typography.bodyMediumEmphasized else MaterialTheme.typography.bodyMedium)
                .copy(fontFeatureSettings = TABULAR_FIGURES),
            color = color,
            maxLines = 1,
            softWrap = false,
        )
    }
}
