package com.example.dimanow.ui

import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.example.dimanow.theme.DimaShapes
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dimanow.meal.MealRefreshResult
import com.example.dimanow.shuttle.ShuttleData
import com.example.dimanow.shuttle.ShuttleRefreshResult
import com.example.dimanow.ui.motion.AnimatedCountText
import com.example.dimanow.ui.motion.DimaMotion
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * D-094(2): collects a data source as `null` until its first real value.
 *
 * Room-backed sources are cold flows, so a fixed empty initial value used to render
 * "no data" states for a moment on every cold start. A [StateFlow] already holds a
 * value, so it is used directly and does not flash a loading placeholder.
 * Inside the app shell, a tab entered again starts from the value it last showed (D-094(12)).
 */
@Composable
internal fun <T : Any> Flow<T>.collectLoadedAsState(): State<T?> {
    val retained = LocalRetainedFlowValues.current
    // Read once per flow instance as the initial value only; collection keeps it current afterwards.
    @Suppress("UNCHECKED_CAST")
    val initial = remember(this) { retained?.get(this) as T? ?: (this as? StateFlow<T>)?.value }
    val state = collectAsStateWithLifecycle(initialValue = initial)
    state.value?.let { retained?.RecordLatest(this, it) }
    return state
}

/**
 * [collectAsStateWithLifecycle] that starts from this flow's last value in the app shell when there is
 * one, and from [initialValue] otherwise.
 */
@Composable
internal fun <T> Flow<T>.collectRetainedAsState(initialValue: T): State<T> {
    val retained = LocalRetainedFlowValues.current
    @Suppress("UNCHECKED_CAST")
    val initial = remember(this) { if (retained?.contains(this) == true) retained[this] as T else initialValue }
    val state = collectAsStateWithLifecycle(initialValue = initial)
    retained?.RecordLatest(this, state.value)
    return state
}

/**
 * The last value each tab-level flow produced, held by the app shell (D-094(12)).
 *
 * Room and DataStore flows are cold, so a tab entered again would render its loading or default
 * layout for a frame. That shorter layout would clamp the list's restored scroll position back to
 * the top; starting from the last value keeps the restored position.
 */
internal class RetainedFlowValues {
    private val values = java.util.IdentityHashMap<Flow<*>, Any?>()

    fun contains(flow: Flow<*>): Boolean = values.containsKey(flow)

    operator fun get(flow: Flow<*>): Any? = values[flow]

    @Composable
    fun RecordLatest(flow: Flow<*>, value: Any?) {
        SideEffect { values[flow] = value }
    }
}

internal val LocalRetainedFlowValues = staticCompositionLocalOf<RetainedFlowValues?> { null }

/** A calm placeholder shown while a source has not emitted yet (Material3 1.4.0 has no LoadingIndicator). */
@Composable
internal fun LoadingLine(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DimaLoadingIndicator(size = 32.dp)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Home has no refresh control, so an empty shuttle cache never asks the user to refresh
 * here; it points to the Shuttle tab's pull-to-refresh instead (D-094).
 */
internal fun homeShuttleUnavailableMessage(data: ShuttleData): String =
    if (data.error != null) {
        "셔틀 시간표를 받지 못했어요. 셔틀 탭에서 아래로 당겨 다시 받아 보세요"
    } else {
        "셔틀 시간표가 아직 없어요. 셔틀 탭에서 아래로 당겨 받아 보세요"
    }

/** Snackbar text for a user-initiated shuttle pull-to-refresh (D-094(3)). */
internal fun shuttleRefreshMessage(result: ShuttleRefreshResult): String = when (result) {
    is ShuttleRefreshResult.Success -> if (result.changed) "셔틀 시간표를 새로 받았어요" else "이미 최신 셔틀 시간표예요"
    is ShuttleRefreshResult.Failure -> if (result.cachedDepartureCount > 0) {
        "셔틀 시간표를 받지 못했어요. 저장된 시간표를 보여 드릴게요"
    } else {
        "셔틀 시간표를 받지 못했어요. 잠시 후 다시 당겨 주세요"
    }
}

/**
 * Snackbar text for a user-initiated meal pull-to-refresh (D-094(3)).
 * `null` means the source decided nothing needed downloading.
 */
internal fun mealRefreshMessage(result: MealRefreshResult?, venue: MealVenue, today: LocalDate): String = when (result) {
    null -> "이미 최신 식단이에요"
    is MealRefreshResult.Success -> when {
        venue == MealVenue.DORMITORY -> "기숙사 식단을 새로 받았어요"
        result.weekStart == today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) -> "이번 주 식단을 새로 받았어요"
        else -> "식단을 새로 받았어요"
    }
    MealRefreshResult.NotPublishedYet -> "아직 새 식단이 올라오지 않았어요"
    is MealRefreshResult.NeedsReview -> "식단을 확인하고 있어요. 잠시 후 다시 확인해 주세요"
    is MealRefreshResult.Failure -> "식단을 받지 못했어요. 잠시 후 다시 당겨 주세요"
}

/**
 * The M3 Expressive loading indicator for waits of roughly 200ms to 5s (D-104). Inline lines use a
 * smaller size than the 48dp default. Still `@ExperimentalMaterial3ExpressiveApi` in Material3
 * 1.5.0-beta02, so the opt-in lives here only.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DimaLoadingIndicator(modifier: Modifier = Modifier, size: Dp = 48.dp) {
    LoadingIndicator(modifier = modifier.size(size))
}

/** OpenType tabular figures: every digit takes the same width, so a ticking countdown keeps its size. */
internal const val TABULAR_FIGURES = "tnum"

/**
 * The non-bouncy spring of departure hand-overs (D-124): when a departure leaves, the strip's scroll, the cells'
 * widths and the round shape move together on it. The expressive spatial spring's overshoot made them jump.
 */
internal fun <T> departureMotion(): FiniteAnimationSpec<T> =
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

/** The height of [DepartureLargeContent] at the current font scale, so strips and capsules keep one height. */
internal fun Density.departureLargeHeight(typography: Typography): Dp = maxOf(
    typography.titleLargeEmphasized.lineHeight.toDp(),
    typography.labelLarge.lineHeight.toDp() + typography.labelSmall.lineHeight.toDp(),
)

/**
 * The large departure content shared by Home's capsules and the shuttle and 4402 strips (D-124): the countdown in
 * large type on the left; the clock, with any markers (첫차, 막차, 운동장, 예정) beneath it, small on the right.
 * Tabular figures keep ticking digits the same width (D-102); the countdown rolls (D-121).
 */
@Composable
internal fun DepartureLargeContent(
    countdown: String,
    clock: String,
    markers: List<String>,
    ink: Color,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = false,
) {
    val large = MaterialTheme.typography.titleLargeEmphasized
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AnimatedCountText(
            text = countdown,
            style = large.copy(fontFeatureSettings = TABULAR_FIGURES),
            color = ink,
            singleLine = true,
            // In a fixed-width cell (Home, D-124) the countdown takes the room the clock leaves and steps down in size
            // instead of overflowing on a narrow phone or at a large font scale.
            autoSize = if (fillWidth) {
                androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = large.fontSize, stepSize = 1.sp)
            } else null,
            modifier = if (fillWidth) Modifier.weight(1f) else Modifier,
        )
        Column {
            Text(
                text = clock,
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TABULAR_FIGURES),
                color = ink,
                maxLines = 1,
                softWrap = false,
            )
            if (markers.isNotEmpty()) {
                Text(
                    text = markers.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = ink,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/**
 * Home's next-departure capsule (D-124, matching the shuttle tab's strip): [DepartureLargeContent] on the strip's
 * colours — the nearest departure in primary and fully round, the others secondary tiles, last service in the
 * warning roles. Supersedes D-121's imminent-only pill: the nearest one is always round and nothing changes at five
 * minutes. The fill changes on the effects spring.
 */
@Composable
internal fun DepartureCapsule(
    countdown: String,
    clock: String,
    markers: List<String>,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    isNext: Boolean = false,
) {
    val fill by animateColorAsState(containerColor, DimaMotion.effectsDefault(), label = "departure_capsule_fill")
    val ink by animateColorAsState(contentColor, DimaMotion.effectsDefault(), label = "departure_capsule_ink")
    val height = with(LocalDensity.current) { departureLargeHeight(MaterialTheme.typography) } + 16.dp
    Surface(shape = if (isNext) CircleShape else DimaShapes.Tile, color = fill, modifier = modifier) {
        Box(Modifier.heightIn(min = height).padding(horizontal = if (isNext) 16.dp else 12.dp), contentAlignment = Alignment.CenterStart) {
            DepartureLargeContent(countdown = countdown, clock = clock, markers = markers, ink = ink, modifier = Modifier.fillMaxWidth(), fillWidth = true)
        }
    }
}

/**
 * Lays capsules out side by side with equal widths only when the widest capsule still fits its
 * label on one line; otherwise it uses fewer columns and wraps onto further rows (a lone capsule
 * on a row takes the full width). This keeps labels from breaking per character at any font scale.
 */
@Composable
internal fun DepartureCapsuleRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 6.dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier.fillMaxWidth()) { measurables, constraints ->
        if (measurables.isEmpty()) return@Layout layout(constraints.minWidth, constraints.minHeight) {}
        val gap = spacing.roundToPx()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else {
            measurables.sumOf { it.maxIntrinsicWidth(Constraints.Infinity) } + gap * (measurables.size - 1)
        }
        val widest = measurables.maxOf { it.maxIntrinsicWidth(Constraints.Infinity) }
        val columns = (measurables.size downTo 1).firstOrNull { count ->
            widest * count + gap * (count - 1) <= width
        } ?: 1
        val rows = measurables.chunked(columns).map { row ->
            val cellWidth = ((width - gap * (row.size - 1)) / row.size).coerceAtLeast(0)
            val rowHeight = row.maxOf { it.minIntrinsicHeight(cellWidth) }
            row.map { it.measure(Constraints.fixed(cellWidth, rowHeight)) } to rowHeight
        }
        val height = rows.sumOf { it.second } + gap * (rows.size - 1)
        layout(width, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            var y = 0
            rows.forEach { (placeables, rowHeight) ->
                var x = 0
                placeables.forEach { placeable ->
                    placeable.placeRelative(x, y)
                    x += placeable.width + gap
                }
                y += rowHeight + gap
            }
        }
    }
}
