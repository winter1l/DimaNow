package com.example.dimanow.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.key
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalLayoutDirection
import com.example.dimanow.ui.motion.dimaLateral
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import com.example.dimanow.ui.motion.LocalListChangeMotion
import com.example.dimanow.ui.motion.LocalReducedMotion
import com.example.dimanow.ui.motion.dimaPopIn
import com.example.dimanow.ui.motion.dimaPopOut
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.Alignment
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.dimanow.theme.DimaTheme
import com.example.dimanow.theme.dimaScreenTitleStyle
import com.example.dimanow.ui.motion.DimaMotion
import com.example.dimanow.ui.motion.entrance

/**
 * The single top app bar of every primary screen, Settings and the Classes tab (D-094 item 4).
 *
 * A Material3 small [TopAppBar] (D-105: one pinned header, no large collapsing title): it stays pinned,
 * draws behind the status bar through [windowInsets], and tints `surface` -> `surfaceContainer` once
 * content scrolls under it. Settings opens from 전체 (D-110), so the bar carries no settings gear.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DimaTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    colors: TopAppBarColors = dimaTopAppBarColors(),
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        windowInsets = windowInsets,
        colors = colors,
        scrollBehavior = scrollBehavior,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun dimaTopAppBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = DimaTheme.colors.page,
    scrolledContainerColor = DimaTheme.colors.scrolledHeader,
)

/** Screen title text for [DimaTopAppBar]. */
@Composable
internal fun DimaTopAppBarTitle(text: String) {
    Text(text = text, style = dimaScreenTitleStyle(), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/**
 * Pinned-header screen scaffold shared by every primary tab, Settings and the Classes tab
 * (D-057, D-094 items 4 and 13).
 *
 * - [DimaTopAppBar] is pinned with [TopAppBarDefaults.pinnedScrollBehavior]; an optional
 *   [subHeader] (mode switches, day selectors) is pinned beneath it and shares its container color.
 * - [content] is real lazy-list content: callers emit keyed `item(key = …)` blocks so only visible
 *   cards compose and a minute tick recomposes only the items that read the changed state.
 * - The list ends above the navigation bar: the gesture-bar inset is added to the bottom content
 *   padding when no bottom NavigationBar consumed it (Settings), plus clearance for [floatingActionButton].
 * - [onRefresh] enables Material pull-to-refresh (D-058) and a [refreshLabel] action in the bar
 *   (D-103); without it no refresh node or action is added.
 * - [snackbarHost] is laid out by the Scaffold, so undo snackbars sit above the FAB.
 * - D-137: a [floatingActionButton] that comes or goes (식단's PAYCO 식권 with 기숙사, 시간표's add button with the
 *   first course) grows in from its centre and shrinks away ([dimaPopIn]/[dimaPopOut]; a fade under reduced motion).
 * - On wide windows the list keeps [DimaLayout.readingWidth] and centers, and the bar and [subHeader]
 *   line up with it (D-104); the list itself still spans the window, so it scrolls and refreshes from
 *   anywhere.
 * - D-138: with [pageIndex] each of the screen's own tabs is a page with its own list and scroll position, and a tab
 *   change slides the two pages together by the full width ([dimaLateral], M3's lateral pattern). [content] is called
 *   with the page it builds, and while the pages move it builds both, so it must build the page it is given, not the
 *   tab chosen now.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScreenScaffold(
    title: String? = null,
    modifier: Modifier = Modifier,
    titleContent: (@Composable () -> Unit)? = null,
    navigationIcon: @Composable () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    topAction: (@Composable RowScope.() -> Unit)? = null,
    subHeader: (@Composable ColumnScope.() -> Unit)? = null,
    floatingActionButton: (@Composable () -> Unit)? = null,
    snackbarHost: @Composable () -> Unit = {},
    listTag: String? = null,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
    refreshLabel: String = "새로고침",
    itemSpacing: Dp = 12.dp,
    /**
     * D-129, D-138: the page of the tab chosen in [subHeader]'s switch (셔틀, 식단, 수업), from 0. Each page has its own
     * list; when it changes, the pages slide together (M3 lateral). Null: one list, [listState], page 0.
     */
    pageIndex: Int? = null,
    /** D-138: the pages' own list states, one per page; by default each page remembers its own. */
    pageListStates: List<LazyListState>? = null,
    /**
     * D-137: what the list shows within one page, such as the chosen day or a load phase (never a minute-tick text).
     * When it changes, the list fades in again on the slow effects spring without moving; a page change does not fade.
     */
    contentKey: Any? = null,
    content: LazyListScope.(page: Int) -> Unit,
) {
    // D-138: the shown page's list drives the bar's tint and a reselected tab's scroll to the top.
    val pageStates = if (pageIndex == null) null else pageListStates ?: rememberPageListStates(SCREEN_PAGES)
    val activeState = pageStates?.getOrNull(pageIndex ?: 0) ?: listState
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    // The pinned behavior only sees user drags; programmatic jumps (the meal list's scrollToItem,
    // tests' scrollToIndex) must tint the bar too, so the list position is the source of truth.
    LaunchedEffect(activeState, scrollBehavior) {
        snapshotFlow { activeState.canScrollBackward }.collect { scrolledAway ->
            scrollBehavior.state.contentOffset = if (scrolledAway) {
                minOf(scrollBehavior.state.contentOffset, SCROLLED_CONTENT_OFFSET)
            } else {
                0f
            }
        }
    }
    // D-102: selecting the visible tab again scrolls its list to the top. The last handled signal is saved
    // with the tab, so returning to a tab keeps its scroll position (D-094(12)) instead of replaying it.
    val scrollToTopSignal = LocalScrollToTopSignal.current
    var handledScrollToTop by rememberSaveable { mutableIntStateOf(scrollToTopSignal) }
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal != handledScrollToTop) {
            handledScrollToTop = scrollToTopSignal
            activeState.animateScrollToItem(0)
        }
    }
    // D-137: another day or phase within one page fades the list in again where it is. The content key and page the
    // list was last shown for lag the parameters for one composition, and that composition, which already holds the
    // new list, hides it, so it never shows in place for a frame before the fade starts (a blink). A page change does
    // not fade: its pages slide (D-138).
    val keyAlpha = remember { Animatable(1f) }
    var shownKey by remember { mutableStateOf(contentKey) }
    var shownKeyPage by remember { mutableStateOf(pageIndex) }
    val reducedMotion = LocalReducedMotion.current
    val keySwitch = !reducedMotion && pageIndex == shownKeyPage && contentKey != shownKey
    LaunchedEffect(pageIndex, contentKey) {
        val fade = !reducedMotion && pageIndex == shownKeyPage && contentKey != shownKey
        keyAlpha.snapTo(if (fade) 0f else 1f)
        shownKey = contentKey
        shownKeyPage = pageIndex
        if (fade) keyAlpha.animateTo(1f, DimaMotion.effectsSlow())
    }
    val shownAlpha: () -> Float = { if (keySwitch) 0f else keyAlpha.value }
    // D-137: the FAB that was last passed, so one the caller stops passing can still draw while it shrinks away.
    val shownFab = rememberLastNonNull(floatingActionButton)
    val titleSlot: @Composable () -> Unit = titleContent ?: { title?.let { DimaTopAppBarTitle(it) } }
    val actions: @Composable RowScope.() -> Unit = {
        // D-103: pull-to-refresh gets a one-tap equivalent in the bar (M3 pull-to-refresh guidance),
        // named after what it refreshes; it rests while a refresh is already running.
        if (onRefresh != null) {
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = onRefresh, enabled = !refreshing, modifier = Modifier.testTag("refresh_action")) {
                Icon(Icons.Default.Refresh, contentDescription = refreshLabel)
            }
        }
        topAction?.invoke(this)
    }

    Scaffold(
        containerColor = DimaTheme.colors.page,
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // The header surface spans the window and tints as one; the bar and the pinned controls below
            // it change color together and, on wide windows, line up with the centered reading column.
            val defaults = dimaTopAppBarColors()
            // Read through derivedStateOf: the pinned behavior writes contentOffset on every scroll frame, and a
            // direct read would recompose the whole header (and the pinned sub-header) each frame.
            val scrolledUnder by remember(scrollBehavior) {
                derivedStateOf { scrollBehavior.state.overlappedFraction > 0.01f }
            }
            val headerColor = animateColorAsState(
                targetValue = if (scrolledUnder) {
                    defaults.scrolledContainerColor
                } else {
                    defaults.containerColor
                },
                animationSpec = DimaMotion.effectsDefault(),
                label = "screen_header_color",
            )
            // D-137: the tint is drawn behind a transparent surface and bar, read in the draw phase, so the bar, its
            // title and actions and the pinned controls do not recompose on every frame of the colour change.
            Surface(
                color = Color.Transparent,
                contentColor = contentColorFor(defaults.containerColor),
                modifier = Modifier.drawBehind { drawRect(headerColor.value) },
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    DimaTopAppBar(
                        title = titleSlot,
                        modifier = Modifier.widthIn(max = HEADER_MAX_WIDTH).fillMaxWidth(),
                        navigationIcon = navigationIcon,
                        actions = actions,
                        scrollBehavior = scrollBehavior,
                        colors = defaults.copy(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                    )
                    if (subHeader != null) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = HEADER_MAX_WIDTH)
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            content = subHeader,
                        )
                    }
                }
            }
        },
        // D-137: AnimatedVisibility, not Modifier.animateFloatingActionButton, which keeps a hidden FAB composed and
        // reachable by TalkBack. A FAB present at the first composition shows without an entrance.
        floatingActionButton = {
            AnimatedVisibility(
                visible = floatingActionButton != null,
                enter = dimaPopIn(reducedMotion),
                exit = dimaPopOut(reducedMotion),
                label = "screen_fab",
            ) {
                shownFab?.invoke()
            }
        },
        // Placed by the Scaffold above the FAB and the gesture bar (D-094(14) undo snackbars).
        snackbarHost = snackbarHost,
        // A bottom NavigationBar in the app shell consumes this inset; Settings has none, so its
        // list pads for the gesture bar here.
        contentWindowInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom),
    ) { innerPadding ->
        val bottomClearance = if (floatingActionButton != null) FAB_CLEARANCE else 0.dp
        val bottomPadding = innerPadding.calculateBottomPadding() + 24.dp + bottomClearance
        PullToRefresh(
            onRefresh = onRefresh,
            refreshing = refreshing,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            // D-137: items composed by a whole-list change do not also fade on their own (D-094(12)); the list fades.
            CompositionLocalProvider(LocalListChangeMotion provides !keySwitch) {
                // One entrance for the whole list, on first launch only: per-item entrances re-fire whenever scrolling
                // composes an item again (D-058), and after a tab change the tab transition is the only motion
                // (D-094(12)).
                if (pageStates == null) {
                    ScaffoldList(listState, bottomPadding, itemSpacing, listTag, shownAlpha, Modifier.entrance()) { content(0) }
                } else {
                    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
                    AnimatedContent(
                        targetState = pageIndex ?: 0,
                        transitionSpec = { dimaLateral(forward = (targetState > initialState) == ltr, reducedMotion) },
                        modifier = Modifier.fillMaxSize().clipToBounds().entrance(),
                        label = "screen_pages",
                    ) { page ->
                        ScaffoldList(
                            state = pageStates.getOrElse(page) { listState },
                            bottomPadding = bottomPadding,
                            itemSpacing = itemSpacing,
                            listTag = listTag,
                            alpha = if (page == pageIndex) shownAlpha else FullAlpha,
                        ) { content(page) }
                    }
                }
            }
        }
    }
}

/**
 * The content padding of [ScreenScaffold]'s list (D-104 centred reading column; D-131, Q-032 (g)).
 *
 * The side gutters keep the list's content in the [DimaLayout.readingWidth] column, centred on wider windows, with
 * at least [MIN_SIDE_GUTTER] on narrow ones. They depend on the list's own width, which a `BoxWithConstraints` used to
 * supply by subcomposing the whole list during layout. Now [readingColumnGutters] sits on the list and writes [gutter]
 * from the list's constraints just before passing them on, and the list reads its padding only while it measures
 * (after that write, in the same pass), so the layout is the one the `BoxWithConstraints` gave, with no subcomposition.
 * [gutter] is a plain field on purpose: it is derived from the constraints of the very pass that reads it, so no
 * state read or extra remeasure is needed. The top and bottom padding are fixed for one instance.
 */
private class ReadingColumnPadding(private val top: Dp, private val bottom: Dp) : PaddingValues {
    var gutter: Dp = MIN_SIDE_GUTTER

    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = gutter
    override fun calculateTopPadding(): Dp = top
    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = gutter
    override fun calculateBottomPadding(): Dp = bottom
}

/**
 * Sets [padding]'s gutters from the list's width and measures the list unchanged (D-131). It must be on the list
 * itself, after its size modifier, so the width it sees is the width the list is measured with.
 */
private fun Modifier.readingColumnGutters(padding: ReadingColumnPadding): Modifier = layout { measurable, constraints ->
    padding.gutter = if (constraints.hasBoundedWidth) {
        maxOf(MIN_SIDE_GUTTER, (constraints.maxWidth.toDp() - DimaLayout.readingWidth) / 2)
    } else {
        MIN_SIDE_GUTTER
    }
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/** The list's side margin on windows narrower than the reading column plus two margins (D-104). */
private val MIN_SIDE_GUTTER = 16.dp

/** The screen tabs every paged screen has today (셔틀, 식단, 수업: two each). */
private const val SCREEN_PAGES = 2

private val FullAlpha: () -> Float = { 1f }

/** One list state per page, each saved with the screen (D-138). */
@Composable
private fun rememberPageListStates(count: Int): List<LazyListState> = List(count) { page -> key(page) { rememberLazyListState() } }

/**
 * One page's list (D-138): the reading column's gutters ([readingColumnGutters]) and the fade of a content change
 * ([alpha], read in the draw phase).
 */
@Composable
private fun ScaffoldList(
    state: LazyListState,
    bottomPadding: Dp,
    itemSpacing: Dp,
    listTag: String?,
    alpha: () -> Float,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val contentPadding = remember(bottomPadding) { ReadingColumnPadding(top = 12.dp, bottom = bottomPadding) }
    LazyColumn(
        state = state,
        modifier = modifier
            .fillMaxSize()
            // D-131: the side gutters follow the list's own width, set in its measure pass.
            .readingColumnGutters(contentPadding)
            .graphicsLayer { this.alpha = alpha() }
            .then(if (listTag != null) Modifier.testTag(listTag) else Modifier),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
        content = content,
    )
}

/** Any offset well past the bar height marks content as scrolled under the pinned bar. */
private const val SCROLLED_CONTENT_OFFSET = -100_000f

/** The reading column plus its 16dp gutters: the bar's title and the column's cards share one start edge. */
private val HEADER_MAX_WIDTH = DimaLayout.readingWidth + 32.dp

/** Room for a 56dp FAB and its 16dp margin, so the last list item can scroll clear of it. */
private val FAB_CLEARANCE = 72.dp

/**
 * The last non-null [value] (D-137), so content an `AnimatedVisibility` is taking away can still draw what it showed
 * while it leaves. A plain field rather than state: it only changes together with [value], whose change already
 * recomposes the caller.
 */
@Composable
internal fun <T : Any> rememberLastNonNull(value: T?): T? {
    val last = remember { LastNonNull<T>() }
    if (value != null) last.value = value
    return last.value
}

private class LastNonNull<T : Any> {
    var value: T? = null
}

/**
 * M3 pull-to-refresh wrapper (D-058).
 *
 * Without [onRefresh] it draws only [content], so screens without refresh get no nested-scroll node.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PullToRefresh(
    onRefresh: (() -> Unit)?,
    refreshing: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (onRefresh == null) {
        Box(modifier = modifier) { content() }
        return
    }
    val state = rememberPullToRefreshState()
    // D-121: one tick as the pull passes the refresh threshold, so the student feels "let go now" without looking.
    // A refresh started from the bar's button also moves the indicator to the threshold; it stays silent.
    val haptics = LocalHapticFeedback.current
    val currentlyRefreshing by rememberUpdatedState(refreshing)
    LaunchedEffect(state) {
        snapshotFlow { state.distanceFraction >= 1f }.collect { armed ->
            if (armed && !currentlyRefreshing) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }
    }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        state = state,
        // D-104: M3 Expressive pull-to-refresh shows the contained loading indicator
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = state,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
        // D-099: 당기기 동작을 쓰기 어려운 화면 낭독기 사용자를 위해 같은 새로고침을 접근성 동작으로도 제공한다
        modifier = modifier.semantics {
            customActions = listOf(
                androidx.compose.ui.semantics.CustomAccessibilityAction("새로고침") { onRefresh(); true },
            )
        },
    ) {
        content()
    }
}
