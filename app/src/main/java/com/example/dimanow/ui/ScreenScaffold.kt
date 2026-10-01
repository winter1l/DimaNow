package com.example.dimanow.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.dimanow.theme.dimaScreenTitleStyle
import com.example.dimanow.ui.motion.DimaMotion
import com.example.dimanow.ui.motion.entrance

/**
 * The single top app bar of every primary screen, Settings and the Classes tab (D-094 item 4).
 *
 * A Material3 small [TopAppBar]: it stays pinned, draws behind the status bar through
 * [windowInsets], and tints `surface` -> `surfaceContainer` once content scrolls under it.
 * The shared settings gear is appended to [actions] whenever [LocalOpenSettings] provides one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DimaTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    showSettings: Boolean = true,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    colors: TopAppBarColors = dimaTopAppBarColors(),
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    val openSettings = if (showSettings) LocalOpenSettings.current else null
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = {
            actions()
            openSettings?.let { open ->
                IconButton(onClick = open, modifier = Modifier.testTag("open_settings")) {
                    Icon(Icons.Default.Settings, contentDescription = "설정")
                }
            }
        },
        windowInsets = windowInsets,
        colors = colors,
        scrollBehavior = scrollBehavior,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun dimaTopAppBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.surface,
    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
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
 * - [onRefresh] enables Material pull-to-refresh (D-058); without it no refresh node is added.
 * - [snackbarHost] is laid out by the Scaffold, so undo snackbars sit above the FAB.
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
    itemSpacing: Dp = 12.dp,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    // The pinned behavior only sees user drags; programmatic jumps (the meal list's scrollToItem,
    // tests' scrollToIndex) must tint the bar too, so the list position is the source of truth.
    LaunchedEffect(listState, scrollBehavior) {
        snapshotFlow { listState.canScrollBackward }.collect { scrolledAway ->
            scrollBehavior.state.contentOffset = if (scrolledAway) {
                minOf(scrollBehavior.state.contentOffset, SCROLLED_CONTENT_OFFSET)
            } else {
                0f
            }
        }
    }
    val titleSlot: @Composable () -> Unit = titleContent ?: { title?.let { DimaTopAppBarTitle(it) } }
    val actions: @Composable RowScope.() -> Unit = { topAction?.invoke(this) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (subHeader == null) {
                DimaTopAppBar(
                    title = titleSlot,
                    navigationIcon = navigationIcon,
                    actions = actions,
                    scrollBehavior = scrollBehavior,
                )
            } else {
                // The pinned controls below the bar must change color together with it.
                val defaults = dimaTopAppBarColors()
                val headerColor by animateColorAsState(
                    targetValue = if (scrollBehavior.state.overlappedFraction > 0.01f) {
                        defaults.scrolledContainerColor
                    } else {
                        defaults.containerColor
                    },
                    animationSpec = DimaMotion.effectsDefault(),
                    label = "screen_header_color",
                )
                Surface(color = headerColor) {
                    Column(Modifier.fillMaxWidth()) {
                        DimaTopAppBar(
                            title = titleSlot,
                            navigationIcon = navigationIcon,
                            actions = actions,
                            scrollBehavior = scrollBehavior,
                            colors = defaults.copy(containerColor = headerColor, scrolledContainerColor = headerColor),
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            content = subHeader,
                        )
                    }
                }
            }
        },
        floatingActionButton = { floatingActionButton?.invoke() },
        // Placed by the Scaffold above the FAB and the gesture bar (D-094(14) undo snackbars).
        snackbarHost = snackbarHost,
        // A bottom NavigationBar in the app shell consumes this inset; Settings has none, so its
        // list pads for the gesture bar here.
        contentWindowInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom),
    ) { innerPadding ->
        val bottomClearance = if (floatingActionButton != null) FAB_CLEARANCE else 0.dp
        PullToRefresh(
            onRefresh = onRefresh,
            refreshing = refreshing,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    // One entrance for the whole list, on first launch only: per-item entrances
                    // re-fire whenever scrolling composes an item again (D-058), and after a tab
                    // change the tab transition is the only motion (D-094(12)).
                    .entrance()
                    .then(if (listTag != null) Modifier.testTag(listTag) else Modifier),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp + bottomClearance,
                ),
                verticalArrangement = Arrangement.spacedBy(itemSpacing),
                content = content,
            )
        }
    }
}

/** Any offset well past the bar height makes the pinned bar report full overlap. */
private const val SCROLLED_CONTENT_OFFSET = -100_000f

/** Room for a 56dp FAB and its 16dp margin, so the last list item can scroll clear of it. */
private val FAB_CLEARANCE = 72.dp

/**
 * M3 pull-to-refresh wrapper (D-058).
 *
 * Without [onRefresh] it draws only [content], so screens without refresh get no nested-scroll node.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        content()
    }
}
