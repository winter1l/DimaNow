package com.example.dimanow.ui

import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.toShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.material3.Typography
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.dimanow.theme.DimaShapes
import com.example.dimanow.theme.DimaTheme
import com.example.dimanow.ui.motion.AnimatedCountText
import com.example.dimanow.ui.motion.DimaMotion
import com.example.dimanow.ui.motion.LocalReducedMotion
import com.example.dimanow.ui.motion.dimaItemChange
import com.example.dimanow.ui.motion.dimaPopIn
import com.example.dimanow.ui.motion.dimaPopOut
import com.example.dimanow.ui.motion.pressScale
import kotlinx.coroutines.launch

/**
 * Visual role of one departure in a route's strip, shared by the campus shuttle and 4402 (D-094(5), (10), D-124).
 * The order is the precedence. A last departure has its warning colour (D-016) until it leaves; once the day's
 * service is over it is a quiet past time like the others, keeping only its 막차 word (D-136).
 */
internal enum class TimetableChipTone { NEXT_LAST, LAST, NEXT, SECOND, FIRST, PAST, UPCOMING }

internal data class TimetableChipSpec(
    val key: Int,
    /** `HH:mm`. */
    val clock: String,
    /** 첫차/막차/운동장/운동장 전환/예정: words that go with the clock; meaning never rests on colour alone. */
    val markers: List<String>,
    /** What a screen reader says (D-094(10)). */
    val description: String,
    val tone: TimetableChipTone,
    val isPast: Boolean,
    /** `5분 후`, `다음 출발`… for today's next two departures (D-069); null for every other time. */
    val countdown: String? = null,
    /**
     * D-127: the next departure is more than an hour off, so its large cell is a quiet tile with the clock large
     * (in [countdown]) and its markers, or `다음 출발`, small above; the departures after it show only their time.
     */
    val farAhead: Boolean = false,
)

/** The small line above a large cell's big one: the clock and markers, or for a far next departure its markers. */
internal fun largeCellTopLine(chip: TimetableChipSpec): String =
    if (chip.farAhead) chip.markers.joinToString(" · ").ifEmpty { "다음 출발" }
    else (listOf(chip.clock) + chip.markers).joinToString(" · ")

/**
 * D-127, narrowed by D-130: how a strip shows today's next two departures. Both count down in minutes, except
 * when the next one is the day's 첫차 more than an hour off: then it alone is large, a quiet tile with its clock,
 * and the one after it is an ordinary time.
 */
internal data class NextDepartureLook(val countdown: String?, val tone: TimetableChipTone, val farAhead: Boolean)

internal fun nextDepartureLook(
    isFirst: Boolean,
    isLast: Boolean,
    isPast: Boolean,
    isNext: Boolean,
    isSecond: Boolean,
    clock: String,
    minutesLeft: Long?,
    nextMinutesLeft: Long?,
    nextIsFirst: Boolean,
): NextDepartureLook {
    val far = nextIsFirst && (nextMinutesLeft ?: 0L) > 60
    return when {
        far && isNext -> NextDepartureLook(clock, timetableChipTone(isFirst, isLast, isPast, isNext = false, isSecond = true), farAhead = true)
        far -> NextDepartureLook(null, timetableChipTone(isFirst, isLast, isPast, isNext = false, isSecond = false), farAhead = false)
        (isNext || isSecond) && minutesLeft != null ->
            NextDepartureLook(departureCountdownLabel(minutesLeft, emptyList()), timetableChipTone(isFirst, isLast, isPast, isNext, isSecond), false)
        else -> NextDepartureLook(null, timetableChipTone(isFirst, isLast, isPast, isNext, isSecond), false)
    }
}

internal fun timetableChipTone(
    isFirst: Boolean,
    isLast: Boolean,
    isPast: Boolean,
    isNext: Boolean,
    isSecond: Boolean,
): TimetableChipTone = when {
    isNext && isLast -> TimetableChipTone.NEXT_LAST
    isLast && !isPast -> TimetableChipTone.LAST
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
    countdown: String? = null,
): String = buildString {
    when {
        isNext -> append("다음 출발 ")
        isPast -> append("지난 시간 ")
    }
    append(clock)
    countdown?.let { append(", ").append(it) }
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
 * Countdown-then-marker line of a next-departure capsule: the minutes left, however many (D-130, superseding
 * D-069's `다음 출발` beyond an hour); a far 첫차 is shown by [nextDepartureLook] instead.
 */
internal fun departureCountdownLabel(minutesLeft: Long, markers: List<String>): String = buildString {
    append(
        when {
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
 * One route's day as a single strip (D-124): the next departure is a large round pill and the one after a large
 * tile, each with its clock (and markers) small on top and the countdown in large type beneath; every
 * other time is a small tile, past times plain text. Every cell has the strip's one height, so a card never changes
 * height. The strip runs to the card's edges and fades out in the card's side padding, so the soft edge never pops
 * in or out. It opens with the preceding departure at the start so the next one is in view (D-068) and keeps manual
 * scrolling; [resetSignal] (refresh, reselecting the tab), the `지금` button shown while the next departure is off
 * screen, and a screen reader's `다음 차로 이동` bring it back in one continuous glide (`glideToItem`). When a departure leaves, the
 * strip, the cells' widths and their looks move together on one non-bouncy spring (`departureMotion`).
 * [endedNote] non-null ends the strip with `오늘 운행이 끝났어요` and that line beneath (empty for none).
 * [onCardInner] lifts the tiles off a card that itself uses the inner tone (the current-location card, D-095).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DepartureStrip(
    chips: List<TimetableChipSpec>,
    stateKey: Any,
    modifier: Modifier = Modifier,
    resetSignal: Int = 0,
    onCardInner: Boolean = false,
    endedNote: String? = null,
    edgePadding: Dp = 16.dp,
    largeCellTag: (Int) -> String? = { null },
) {
    val reducedMotion = LocalReducedMotion.current
    val density = LocalDensity.current
    val nextIndex = chips.indexOfFirst { it.countdown != null }
    // The item the strip rests on: the next departure, else (today ended) the end note, else the day's first time.
    val anchor = when {
        nextIndex >= 0 -> nextIndex
        endedNote != null -> chips.size
        else -> 0
    }
    val restingItem = (anchor - 1).coerceAtLeast(0)
    val state = remember(stateKey) { LazyListState(firstVisibleItemIndex = restingItem) }
    val scope = rememberCoroutineScope()
    val spacingPx = with(density) { 6.dp.toPx() }
    val typography = MaterialTheme.typography
    // Every cell's width is known before it is laid out (its texts are known), so the strip can always tell the
    // exact distance to any departure, on screen or not, and move there in one animation (D-124).
    val measurer = rememberTextMeasurer()
    val cellWidths = remember(chips, typography, density) { chips.map { measuredCellWidth(it, measurer, typography, density) } }
    val cellWidthsPx = with(density) { cellWidths.map { it.toPx() } }
    suspend fun backToNext() {
        val distance = state.distanceToItem(restingItem, cellWidthsPx, spacingPx)
        when {
            distance == null -> state.scrollToItem(restingItem)
            reducedMotion -> state.scrollBy(distance)
            else -> state.animateScrollBy(distance, departureMotion())
        }
    }
    var seenReset by remember(stateKey) { mutableIntStateOf(resetSignal) }
    LaunchedEffect(resetSignal) {
        if (resetSignal != seenReset) {
            seenReset = resetSignal
            backToNext()
        }
    }
    // A departure leaving: a strip that was resting on it slides by exactly the distance to the new resting item,
    // on the same spring that resizes the cells, instead of a second, competing scroll-to-item animation.
    var lastAnchor by remember(stateKey) { mutableIntStateOf(anchor) }
    LaunchedEffect(anchor) {
        if (anchor == lastAnchor) return@LaunchedEffect
        val previousResting = (lastAnchor - 1).coerceAtLeast(0)
        val wasResting = state.layoutInfo.visibleItemsInfo.any { it.index == previousResting }
        lastAnchor = anchor
        // The student had scrolled away: leave the strip where it is.
        if (wasResting) backToNext()
    }
    // -1: the next departure lies left of the view, 1: right of it, 0: in view or none today.
    val offScreen by remember(state, nextIndex) {
        derivedStateOf {
            val visible = state.layoutInfo.visibleItemsInfo
            when {
                nextIndex < 0 || visible.isEmpty() -> 0
                nextIndex < visible.first().index -> -1
                nextIndex > visible.last().index -> 1
                else -> 0
            }
        }
    }
    var lastSide by remember { mutableIntStateOf(1) }
    if (offScreen != 0) lastSide = offScreen
    val stripHeight = with(density) {
        typography.labelMediumEmphasized.lineHeight.toDp() + typography.titleLargeEmphasized.lineHeight.toDp()
    } + 16.dp
    val fade = with(density) { edgePadding.toPx() }

    Box(
        Modifier
            .fillMaxWidth()
            .semantics {
                if (nextIndex >= 0) {
                    customActions = listOf(CustomAccessibilityAction("다음 차로 이동") { scope.launch { backToNext() }; true })
                }
            },
    ) {
        LazyRow(
            state = state,
            modifier = modifier
                .fillMaxWidth()
                .height(stripHeight)
                // The soft edges sit in the side padding and never change, so scrolling never makes them pop.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black, startX = 0f, endX = fade),
                        size = size.copy(width = fade),
                        blendMode = BlendMode.DstIn,
                    )
                    drawRect(
                        brush = Brush.horizontalGradient(0f to Color.Black, 1f to Color.Transparent, startX = size.width - fade, endX = size.width),
                        topLeft = Offset(size.width - fade, 0f),
                        size = size.copy(width = fade),
                        blendMode = BlendMode.DstIn,
                    )
                },
            contentPadding = PaddingValues(horizontal = edgePadding),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(chips, key = { _, chip -> chip.key }) { index, chip ->
                val tag = if (chip.countdown != null && nextIndex >= 0) largeCellTag(index - nextIndex) else null
                DepartureCell(
                    chip = chip,
                    onCardInner = onCardInner,
                    width = cellWidths[index],
                    modifier = Modifier.fillMaxHeight().then(if (tag != null) Modifier.testTag(tag) else Modifier),
                )
            }
            if (endedNote != null) {
                item(key = "ended") {
                    // D-137: when the day's last departure leaves while the card is shown, the note fades in as the
                    // strip settles on it; opacity only, so no second spring joins the hand-over (D-124(5)).
                    EndedCell(
                        endedNote,
                        onCardInner,
                        Modifier.fillMaxHeight().then(dimaItemChange(fadeIn = true, fadeOut = false, placement = false)),
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = offScreen != 0,
            // D-137: the strip's one primary control grows from its own centre (a fade under reduced motion).
            enter = dimaPopIn(reducedMotion),
            exit = dimaPopOut(reducedMotion),
            modifier = Modifier
                .align(if (lastSide < 0) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = edgePadding),
        ) {
            // D-129: the way back is the M3 Expressive Arrow shape itself, pointing at the next departure — no label;
            // TalkBack still hears `지금 다음 차로 이동`.
            val pointsLeft = lastSide < 0
            // The Arrow polygon points up; the whole button turns to face left or right.
            val arrow = MaterialShapes.Arrow.toShape()
            val pressSource = remember { MutableInteractionSource() }
            Surface(
                onClick = { scope.launch { backToNext() } },
                shape = arrow,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 3.dp,
                interactionSource = pressSource,
                modifier = Modifier
                    .size(48.dp)
                    // D-137: it squeezes under the finger like the app's other controls; 0.97 is invisible at 48dp.
                    .pressScale(pressSource, scaleDown = ARROW_PRESSED_SCALE)
                    .rotate(if (pointsLeft) -90f else 90f)
                    .testTag("departure_strip_back")
                    .semantics { contentDescription = "지금 다음 차로 이동" },
            ) {}
        }
    }
}

private enum class CellKind { LARGE, SMALL, QUIET }

/** How far the back-to-next arrow shrinks under the finger (D-137); the app's 0.97 does not show at 48dp. */
private const val ARROW_PRESSED_SCALE = 0.9f

/**
 * One time of the strip. The next departure fills with primary (warning when it is the last service) and is fully
 * round; the one after is a secondary tile; later times are inner tiles, the first train tertiary, the last amber,
 * stadium boarding outlined in tertiary with its word; past times are quiet text. A change of kind cross-fades while
 * the width follows on `departureMotion`; colours move on the effects spring.
 *
 * D-137: the width, its countdown's size and the round shape share that one spring, and the cross-fade adds no size
 * animation of its own. The hand-over's per-frame values are read where they are used (the width in measure, the
 * shape in the layer, the fill in draw, the ink only by the texts), so a departure leaving no longer recomposes the
 * whole cell or builds a shape on every frame (D-121(4)); what is drawn is unchanged (D-124(5)).
 */
@Composable
private fun DepartureCell(chip: TimetableChipSpec, onCardInner: Boolean, width: Dp, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val tones = DimaTheme.colors
    val kind = cellKind(chip)
    val isNext = isNextCell(chip)
    val stadium = !chip.isPast && chip.markers.any { it.startsWith("운동장") }
    val tile = if (onCardInner) tones.card else tones.cardInner
    val (container, content) = when {
        chip.tone == TimetableChipTone.NEXT_LAST -> tones.warning to tones.onWarning
        isNext -> colors.primary to colors.onPrimary
        chip.tone == TimetableChipTone.LAST -> tones.warningContainer to tones.onWarningContainer
        kind == CellKind.QUIET -> Color.Transparent to colors.onSurfaceVariant
        kind == CellKind.LARGE -> colors.secondaryContainer to colors.onSecondaryContainer
        chip.tone == TimetableChipTone.FIRST -> colors.tertiaryContainer to colors.onTertiaryContainer
        else -> tile to colors.onSurface
    }
    val fill = animateColorAsState(container, DimaMotion.effectsDefault(), label = "strip_fill")
    val inkState = animateColorAsState(content, DimaMotion.effectsDefault(), label = "strip_ink")
    val round = animateFloatAsState(if (isNext) 1f else 0f, departureMotion(), label = "strip_round")
    val shownWidth = animateDpAsState(width, departureMotion(), label = "strip_width")
    // Layered as a Surface is: the border over the fill, which is clipped to the shape with the content.
    Box(
        propagateMinConstraints = true,
        modifier = modifier
            .layout { measurable, constraints ->
                val cellWidth = shownWidth.value.roundToPx().coerceIn(constraints.minWidth, constraints.maxWidth)
                val placeable = measurable.measure(constraints.copy(minWidth = cellWidth, maxWidth = cellWidth))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .semantics(mergeDescendants = true) { contentDescription = chip.description }
            // Only a small or quiet cell is outlined, and those are never round.
            .then(if (stadium && kind != CellKind.LARGE) Modifier.border(1.5.dp, colors.tertiary, DimaShapes.Tile) else Modifier)
            .graphicsLayer {
                val fraction = round.value
                shape = if (fraction == 0f) DimaShapes.Tile else DimaShapes.tileToPill(fraction)
                clip = true
            }
            .drawBehind { drawRect(fill.value) },
    ) {
        AnimatedContent(
            targetState = kind,
            modifier = Modifier.fillMaxSize(),
            // No size transform: the cell's width above is the only width animation.
            transitionSpec = { (fadeIn(DimaMotion.effectsDefault()) togetherWith fadeOut(DimaMotion.effectsFast())).using(null) },
            contentAlignment = Alignment.Center,
            label = "strip_cell",
        ) { shown ->
            val ink = inkState.value
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                when (shown) {
                    // The shuttle tab keeps its stacked cell: clock and markers small on top, countdown large
                    // beneath (D-124). Home's capsules put them side by side instead.
                    CellKind.LARGE -> Column(Modifier.padding(horizontal = if (isNext) 20.dp else 14.dp)) {
                        Text(
                            text = largeCellTopLine(chip),
                            style = MaterialTheme.typography.labelMediumEmphasized.copy(fontFeatureSettings = TABULAR_FIGURES),
                            color = ink,
                            maxLines = 1,
                            softWrap = false,
                        )
                        // D-137: the countdown is part of the cell's measured width, so its size follows the
                        // width's spring; the overshooting default moved the clock line above sideways (D-124(5)).
                        AnimatedCountText(
                            text = chip.countdown.orEmpty(),
                            style = MaterialTheme.typography.titleLargeEmphasized.copy(fontFeatureSettings = TABULAR_FIGURES),
                            color = ink,
                            singleLine = true,
                            sizeSpec = departureMotion(),
                        )
                    }
                    else -> Column(
                        modifier = Modifier.padding(horizontal = if (shown == CellKind.QUIET) 6.dp else 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = chip.clock,
                            style = (if (shown == CellKind.QUIET) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium)
                                .copy(fontFeatureSettings = TABULAR_FIGURES),
                            color = ink,
                            maxLines = 1,
                            softWrap = false,
                        )
                        if (shown == CellKind.SMALL && chip.markers.isNotEmpty() || shown == CellKind.QUIET && chip.leftAsLast) {
                            Text(
                                text = chip.markers.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (stadium && chip.tone != TimetableChipTone.LAST) colors.tertiary else ink,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EndedCell(note: String, onCardInner: Boolean, modifier: Modifier = Modifier) {
    Surface(shape = DimaShapes.Tile, color = if (onCardInner) DimaTheme.colors.card else DimaTheme.colors.cardInner, modifier = modifier) {
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.Center) {
            Text("오늘 운행이 끝났어요", style = MaterialTheme.typography.titleSmall)
            if (note.isNotEmpty()) {
                Text(note, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun cellKind(chip: TimetableChipSpec): CellKind = when {
    chip.countdown != null -> CellKind.LARGE
    chip.isPast && chip.tone != TimetableChipTone.LAST -> CellKind.QUIET
    else -> CellKind.SMALL
}

/** The day's last departure, already gone: quiet, but its 막차 word stays under the time (D-136). */
private val TimetableChipSpec.leftAsLast: Boolean
    get() = isPast && markers.any { "막차" in it }

private fun isNextCell(chip: TimetableChipSpec): Boolean =
    cellKind(chip) == CellKind.LARGE && (chip.tone == TimetableChipTone.NEXT || chip.tone == TimetableChipTone.NEXT_LAST)

/** The width a cell is drawn at: its widest line in the styles [DepartureCell] uses, plus its side padding. */
private fun measuredCellWidth(chip: TimetableChipSpec, measurer: TextMeasurer, typography: Typography, density: Density): Dp {
    fun widthOf(text: String, style: TextStyle) = measurer.measure(text, style.copy(fontFeatureSettings = TABULAR_FIGURES), maxLines = 1, softWrap = false).size.width
    val content = when (cellKind(chip)) {
        CellKind.LARGE -> maxOf(
            widthOf(largeCellTopLine(chip), typography.labelMediumEmphasized),
            widthOf(chip.countdown.orEmpty(), typography.titleLargeEmphasized),
        )
        CellKind.SMALL -> maxOf(
            widthOf(chip.clock, typography.titleMedium),
            if (chip.markers.isEmpty()) 0 else widthOf(chip.markers.joinToString(" · "), typography.labelSmall),
        )
        CellKind.QUIET -> maxOf(
            widthOf(chip.clock, typography.bodyLarge),
            if (chip.leftAsLast) widthOf(chip.markers.joinToString(" · "), typography.labelSmall) else 0,
        )
    }
    val padding = when (cellKind(chip)) {
        CellKind.LARGE -> if (isNextCell(chip)) 40.dp else 28.dp
        CellKind.SMALL -> 24.dp
        CellKind.QUIET -> 12.dp
    }
    // One extra dp so rounding never makes a line wrap.
    return with(density) { content.toDp() } + padding + 1.dp
}

/**
 * The exact scroll distance that brings item [index] to the strip's start, from the cells' known widths
 * ([widthsPx], one per departure) and the first laid-out item; null before the first layout.
 */
internal fun LazyListState.distanceToItem(index: Int, widthsPx: List<Float>, spacingPx: Float): Float? {
    val first = layoutInfo.visibleItemsInfo.firstOrNull() ?: return null
    var distance = first.offset.toFloat()
    if (index >= first.index) {
        for (i in first.index until index) distance += widthsPx.getOrElse(i) { 0f } + spacingPx
    } else {
        for (i in index until first.index) distance -= widthsPx.getOrElse(i) { 0f } + spacingPx
    }
    return distance
}
