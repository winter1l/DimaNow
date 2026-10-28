package com.example.dimanow.lms

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.dimanow.theme.DimaShapes
import com.example.dimanow.theme.DimaTheme
import com.example.dimanow.ui.DimaIconBadge
import com.example.dimanow.ui.DimaLoadingIndicator
import com.example.dimanow.ui.DimaSegmentedList
import com.example.dimanow.ui.EmptyState
import com.example.dimanow.ui.ExpandChevron
import com.example.dimanow.ui.expandableSemantics
import com.example.dimanow.ui.ErrorState
import com.example.dimanow.ui.ScreenScaffold
import com.example.dimanow.ui.dimaSegmentedListColors
import com.example.dimanow.ui.motion.AnimatedCountText
import com.example.dimanow.ui.motion.DimaMotion
import com.example.dimanow.ui.motion.LocalListChangeMotion
import com.example.dimanow.ui.motion.LocalReducedMotion
import com.example.dimanow.ui.motion.dimaCollapseOut
import com.example.dimanow.ui.motion.dimaContentReveal
import com.example.dimanow.ui.motion.dimaExpandIn
import com.example.dimanow.ui.motion.dimaIconSwap
import com.example.dimanow.ui.motion.dimaItemChange
import com.example.dimanow.ui.motion.dimaPopIn
import com.example.dimanow.ui.motion.dimaPopOut
import com.example.dimanow.ui.motion.loadingGrace
import com.example.dimanow.ui.motion.rememberShownAfter
import java.time.Clock
import java.time.Instant

/**
 * 종류·과목을 각각 드롭다운 칩 하나로 접은 고정 필터 바 (D-058).
 *
 * D-057에서 세 줄을 한 줄로 줄였지만, 종류 칩을 전부 펼치니 그 한 줄이 화면 폭을 훌쩍
 * 넘겨 결국 가로로 계속 밀어야 했다. 이제 선택값을 그대로 라벨에 담은 칩 두 개만 남기고
 * 선택지는 메뉴로 내린다. 읽음/안읽음 필터는 제거했다 — 목록은 과목과 완료 여부로 읽는다.
 */
@Composable
private fun LmsFilterRow(
    courses: List<LmsCourse>,
    kinds: List<LmsItemKind>,
    selectedCourse: String?,
    selectedKind: LmsItemKind?,
    filterActive: Boolean,
    onCourseChange: (String?) -> Unit,
    onKindChange: (LmsItemKind?) -> Unit,
) {
    val reduced = LocalReducedMotion.current
    // The gaps sit beside each control rather than in the row's arrangement, so the clear button's gap grows and
    // shrinks with it instead of appearing in one frame (D-137).
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LmsFilterMenuChip(
            label = selectedKind?.let(::kindLabel) ?: "전체 종류",
            selected = selectedKind != null,
            tag = "lms_kind_filter",
            options = listOf<Pair<String, LmsItemKind?>>("전체 종류" to null) + kinds.map { kindLabel(it) to it },
            optionTag = { kind -> kind?.let { "lms_kind_${it.name}" } },
            onSelect = onKindChange,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(LMS_FILTER_GAP))
        LmsFilterMenuChip(
            label = selectedCourse?.let { id -> courses.firstOrNull { it.id == id }?.name } ?: "전체 과목",
            selected = selectedCourse != null,
            tag = "lms_course_filter",
            options = listOf<Pair<String, String?>>("전체 과목" to null) + courses.map { it.name to it.id },
            optionTag = { null },
            onSelect = onCourseChange,
            modifier = Modifier.weight(1f),
        )
        // D-137: the clear button pops in from its own centre while the chips make room for it, and leaves the same
        // way; under reduced motion it only fades (D-121(4)).
        AnimatedVisibility(
            visible = filterActive,
            enter = expandHorizontally(DimaMotion.spatialFast()) + dimaPopIn(reduced),
            exit = shrinkHorizontally(DimaMotion.spatialFast()) + dimaPopOut(reduced),
        ) {
            IconButton(shapes = IconButtonDefaults.shapes(),
                onClick = { onCourseChange(null); onKindChange(null) },
                modifier = Modifier.padding(start = LMS_FILTER_GAP).testTag("lms_filter_clear"),
            ) {
                Icon(Icons.Default.Close, contentDescription = "필터 초기화")
            }
        }
    }
}

/** Between the filter controls (D-058). */
private val LMS_FILTER_GAP = 6.dp

/**
 * 선택값을 라벨로 보여주고 누르면 선택지를 메뉴로 펼치는 필터 칩 (D-058).
 *
 * D-137: a chosen filter rounds into a pill and takes its fill on the fast effects spring, and the arrow turns over
 * while the menu is open (not under reduced motion, D-121(4)); the turn is read in the draw phase.
 */
@Composable
private fun <T> LmsFilterMenuChip(
    label: String,
    selected: Boolean,
    tag: String,
    options: List<Pair<String, T>>,
    optionTag: (T) -> String?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val reduced = LocalReducedMotion.current
    val arrowTurn = animateFloatAsState(
        targetValue = if (expanded && !reduced) 180f else 0f,
        animationSpec = DimaMotion.spatialFast(),
        label = "lms_filter_arrow",
    )
    val selectedFill = MaterialTheme.colorScheme.secondaryContainer
    val fill by animateColorAsState(
        targetValue = if (selected) selectedFill else selectedFill.copy(alpha = 0f),
        animationSpec = DimaMotion.effectsFast(),
        label = "lms_filter_fill",
    )
    Box(modifier = modifier) {
        FilterChip(
            selected = selected,
            onClick = { expanded = true },
            label = {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            shapes = FilterChipDefaults.shapes(
                shape = FilterChipDefaults.shape,
                selectedShape = CircleShape,
                pressedShape = DimaShapes.Small,
            ),
            trailingIcon = {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = arrowTurn.value },
                )
            },
            colors = FilterChipDefaults.filterChipColors(containerColor = fill, selectedContainerColor = fill),
            modifier = Modifier.fillMaxWidth().testTag(tag),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (optionLabel, value) ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                    modifier = optionTag(value)?.let { Modifier.testTag(it) } ?: Modifier,
                )
            }
        }
    }
}

/**
 * A card group's heading (D-118): the name in title-large on the page's text color with the count beside it in the
 * primary color, so each group is easy to find while scrolling. Screen readers hear one heading (`다가오는 마감 2개`)
 * and can jump between groups.
 *
 * D-137: the count rolls when items arrive or leave ([AnimatedCountText]). Both texts share title-large, so aligning
 * their tops keeps the baselines of D-118 together; a baseline read through the rolling count would move with it.
 */
@Composable
private fun LmsSectionHeader(title: String, count: Int?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(start = 4.dp, top = 12.dp).semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f, fill = false),
        )
        count?.let {
            AnimatedCountText(
                text = "${it}개",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * A folded part of a list (완료한 학습; under 과목별 also 기한 지남): one row says how many, and the rows open below
 * it with the M3 Expressive spatial spring and fade (D-118), as the equipment notice does.
 */
@Composable
private fun LmsFoldedSection(
    title: String,
    items: List<LmsItem>,
    expanded: Boolean,
    onToggle: () -> Unit,
    now: Instant,
    onOpenItem: (LmsItem) -> Unit,
    tag: String,
    completed: Boolean,
    modifier: Modifier = Modifier,
    showCourseName: Boolean = true,
    selected: (LmsItem) -> Boolean? = { null },
    opening: (LmsItem) -> LmsRowOpening = { LmsRowOpening.NONE },
) {
    Column(modifier) {
        LmsFoldHeader(title = title, count = items.size, completed = completed, expanded = expanded, onToggle = onToggle, tag = tag)
        // The shared fold motion of D-118 (D-137: the tokens themselves, so the fold never drifts from them).
        AnimatedVisibility(
            visible = expanded,
            enter = dimaExpandIn(),
            exit = dimaCollapseOut(),
        ) {
            LmsItemList(
                items,
                now,
                onOpenItem,
                Modifier.padding(top = 8.dp),
                showCourseName = showCourseName,
                completed = completed,
                muted = true,
                selected = selected,
                opening = opening,
            )
        }
    }
}

/**
 * The row that opens a fold (D-120): one line on the muted tone with a thin outline and smaller corners, so it reads
 * as "more below" rather than as another card to act on; the icon says which fold (missed or done), the count sits
 * beside the name, and the chevron turns with the expressive spring. Screen readers hear its expanded state.
 */
@Composable
private fun LmsFoldHeader(title: String, count: Int, completed: Boolean, expanded: Boolean, onToggle: () -> Unit, tag: String) {
    Surface(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(tag)
            .expandableSemantics(expanded, onToggle),
        shape = RoundedCornerShape(14.dp),
        color = DimaTheme.colors.cardMuted,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (completed) Icons.Default.TaskAlt else Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            // D-137: an item finished or missed rolls the count, as the section headings do.
            AnimatedCountText("${count}개", style = MaterialTheme.typography.labelLarge, color = LocalContentColor.current)
            ExpandChevron(expanded, Modifier.size(20.dp), label = "lms_fold_chevron")
        }
    }
}

@Composable
internal fun LmsItemsScreen(
    snapshot: LmsSnapshot,
    sessionState: LmsSessionState,
    selectedCourse: String?,
    selectedKind: LmsItemKind?,
    onCourseChange: (String?) -> Unit,
    onKindChange: (LmsItemKind?) -> Unit,
    onRefresh: () -> Unit,
    onOpenItem: (LmsItem) -> Unit,
    now: Instant,
    modifier: Modifier = Modifier,
    loginErrorMessage: String? = null,
    /**
     * D-135: the LMS ended this device's session within two hours of its last use, so another device most likely signed
     * in. The list then shows a card whose 이 기기로 로그인 ([onSignInHere]) is the only thing that signs in, since it
     * signs that device out; the card stays until then.
     */
    signedInElsewhere: Boolean = false,
    onSignInHere: () -> Unit = {},
    /**
     * D-131 (Q-025): true while the list sits beside the detail pane on a wide window. Every row then says whether it
     * is the one open there ([selection]); on phones the detail takes the whole screen and rows carry no such state.
     */
    selectable: Boolean = false,
    /** D-131: the item open in the detail pane beside the list, marked as the selected row. */
    selection: LmsItem? = null,
    /**
     * D-137: the [entryKey] of the item whose detail is being read after a tap. Once that takes longer than the
     * loading grace, its row shows the loading indicator in place of its kind mark.
     */
    openingKey: String? = null,
) {
    val filtered = filterLmsItems(snapshot.items, selectedCourse, selectedKind)
    val filterActive = selectedCourse != null || selectedKind != null
    val syncing = snapshot.syncState == LmsSyncState.SYNCING || sessionState == LmsSessionState.AUTHENTICATING
    val hasError = loginErrorMessage != null || sessionState == LmsSessionState.ERROR || snapshot.syncState == LmsSyncState.ERROR
    var todayMode by rememberSaveable { mutableStateOf(true) }
    // Folded parts the student opened: 할 일's completed learning and each course's missed and completed items.
    var openSections by rememberSaveable { mutableStateOf(setOf<String>()) }
    fun toggleSection(key: String) {
        openSections = if (key in openSections) openSections - key else openSections + key
    }
    // 할 일에서도 선택한 필터를 그대로 적용한다 (이전에는 칩이 조용히 무시됐다)
    val agenda = remember(filtered, now) {
        LmsAgendaPlanner(Clock.fixed(now, SEOUL)).plan(filtered, now)
    }
    // 과목별은 과목 순서로 묶고, 과목마다 남은 항목·기한 지남·완료한 학습으로 나눈다 (D-058, D-118)
    val coursePlan = remember(filtered, snapshot.courses, now) { planLmsByCourse(filtered, snapshot.courses, now) }
    // D-138: 할 일 and 과목별 are pages drawn side by side while they slide, so what each shows is worked out per page.
    fun visibleEmptyIn(today: Boolean) = if (today) {
        agenda.groups.none { it.key != LmsAgendaGroupKey.COMPLETED }
    } else {
        filtered.isEmpty()
    }
    val visibleEmpty = visibleEmptyIn(todayMode)
    // D-137: the line or card above the list and the block standing in for an empty list, each one item whose content
    // changes in place (R2), so a refresh failure, a new read time or the list arriving never pops.
    fun statusIn(today: Boolean): LmsListStatus {
        val empty = visibleEmptyIn(today)
        return lmsListStatus(
            signedInElsewhere = signedInElsewhere,
            showsError = hasError && !empty,
            updatedLabel = snapshot.lastSuccessAt?.takeUnless { empty }?.let { lmsUpdatedLabel(it, now) },
            errorMessage = loginErrorMessage ?: "수업 정보를 불러오지 못했어요",
            lastSuccessAt = snapshot.lastSuccessAt,
        )
    }
    fun placeholderIn(today: Boolean): LmsListPlaceholder? = when {
        !visibleEmptyIn(today) -> null
        syncing -> LmsListPlaceholder.Loading
        // D-135: the card above says why nothing new is read and offers 이 기기로 로그인.
        signedInElsewhere -> null
        // 원문 오류(HTTP 코드 등)는 보이지 않고, 무슨 일인지와 다음 행동만 알린다 (D-094(14))
        hasError -> LmsListPlaceholder.Failed(loginErrorMessage ?: "인터넷 연결을 확인하고 다시 시도해 주세요")
        else -> lmsEmptyPlaceholder(
            interactiveAuth = sessionState == LmsSessionState.INTERACTIVE_AUTH_REQUIRED,
            needsSignIn = sessionState != LmsSessionState.ACTIVE,
            neverLoaded = snapshot.syncState == LmsSyncState.IDLE && snapshot.lastSuccessAt == null,
            filterActive = filterActive,
            todayMode = today,
        )
    }
    // Quick opens (a cached detail) show nothing at the row; only a wait past the grace does (D-137). Only the row
    // tapped last carries the swap, so the other rows keep their plain mark (D-131 keeps rows light).
    val openingShown = rememberShownAfter(openingKey != null)
    val lastOpened = remember { LmsLastOpened() }
    if (openingKey != null) lastOpened.key = openingKey
    val opening: (LmsItem) -> LmsRowOpening = { item ->
        val key = item.entryKey()
        when {
            openingShown && key == openingKey -> LmsRowOpening.SHOWN
            key == lastOpened.key -> LmsRowOpening.SLOT
            else -> LmsRowOpening.NONE
        }
    }
    val reduced = LocalReducedMotion.current

    ScreenScaffold(
        title = "수업",
        modifier = modifier,
        listTag = "lms_history",
        // D-129, D-138: 할 일(0) and 과목별(1) are pages that slide together
        pageIndex = if (todayMode) 0 else 1,
        // 새로고침은 목록을 당기거나 상단 버튼으로 실행한다 (D-058, D-103)
        onRefresh = onRefresh,
        refreshing = syncing && !visibleEmpty,
        refreshLabel = "수업 새로고침",
        // D-131: rows are items of their own, so each item sets the gap above it (LmsLazyRows).
        itemSpacing = 0.dp,
        // 모드 전환과 필터는 목록을 아무리 내려도 항상 닿을 수 있어야 한다 (D-057).
        subHeader = {
            // D-137: the filter row folds open under the switch on the non-bouncy spring the list's shift uses, as the
            // shuttle weekday row does, so the pinned header never bobs; its gap folds with it.
            Column {
                // D-103: 모드 전환은 M3 연결형 버튼 그룹이다. D-117: 하단 탭 '전체'와 겹치지 않게 '할 일'·'과목별'로 부른다
                com.example.dimanow.ui.DimaConnectedChoice(
                    options = listOf(true to "할 일", false to "과목별"),
                    selected = todayMode,
                    onSelect = { todayMode = it },
                    itemTag = { if (it) "lms_mode_today" else "lms_mode_all" },
                )
                AnimatedVisibility(
                    visible = !todayMode || filterActive,
                    enter = if (reduced) EnterTransition.None else dimaExpandIn(DimaMotion.settle()),
                    exit = if (reduced) ExitTransition.None else dimaCollapseOut(DimaMotion.settle()),
                ) {
                    Box(Modifier.padding(top = 8.dp)) {
                        LmsFilterRow(
                            courses = snapshot.courses,
                            kinds = LmsItemKind.entries.filter { kind -> snapshot.items.any { it.kind == kind } },
                            selectedCourse = selectedCourse,
                            selectedKind = selectedKind,
                            filterActive = filterActive,
                            onCourseChange = onCourseChange,
                            onKindChange = onKindChange,
                        )
                    }
                }
            }
        },
    ) { page ->
        val pageToday = page == 0
        val status = statusIn(pageToday)
        val placeholder = placeholderIn(pageToday)
        // D-131 (Q-032 (g)): every section row is its own lazy item, so only the rows on screen compose (a course with
        // 80 notices used to compose all of them in one item). The list therefore has no item spacing of its own; each
        // item carries the gap above it, as the group columns had: 12dp between blocks, 8dp under a heading and before
        // a fold, the segmented gap between rows. Folded rows stay inside their fold, which opens as one block (D-118).
        val rows = LmsLazyRows()
        fun selectedOf(item: LmsItem): Boolean? = if (selectable) selection?.entryKey() == item.entryKey() else null

        // D-137: always the first item. Its content carries the gap below it, so with nothing to say it takes no
        // space and the next block starts at the top, as before; a change fades in place while the height follows.
        item(key = "lms_status") {
            val changes = LocalListChangeMotion.current
            AnimatedContent(
                targetState = status,
                contentKey = { it.kind },
                transitionSpec = { lmsBlockChange(changes) },
                label = "lms_status",
            ) { shown ->
                val below = Modifier.padding(bottom = LMS_BLOCK_GAP)
                when (shown) {
                    is LmsListStatus.Elsewhere -> LmsSignedInElsewhereCard(
                        shown.lastSuccessAt,
                        onSignInHere,
                        // A sign-in already under way is not started twice.
                        enabled = sessionState != LmsSessionState.AUTHENTICATING,
                        modifier = below,
                    )
                    // 캐시된 목록 위에서 갱신이 실패하면 스낵바가 사라진 뒤에도 상태를 알 수 있게 배너로 남긴다 (D-056)
                    is LmsListStatus.Failed -> LmsRefreshErrorBanner(
                        message = shown.message,
                        lastSuccessAt = shown.lastSuccessAt,
                        onRetry = onRefresh,
                        modifier = below,
                    )
                    // D-117: 목록이 언제 것인지 한 줄로 알린다. D-137: a refresh rolls the time to the new one.
                    is LmsListStatus.Updated -> AnimatedCountText(
                        text = shown.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = below.padding(horizontal = 4.dp).testTag("lms_updated"),
                    )
                    LmsListStatus.None -> Spacer(Modifier.fillMaxWidth())
                }
            }
        }

        placeholder?.let { shownPlaceholder ->
            val top = rows.gap(LMS_BLOCK_GAP)
            // D-137: loading, failure and the empty messages change in place; when the list arrives this item fades
            // out as the groups fade in together (D-122(6)), as 장비 대여's catalog does. When the other mode has
            // learning to show, switching to it would remove this item too, so it then leaves without a fade of its
            // own (the whole list fades in that switch).
            val emptyInBothModes = filtered.isEmpty() && agenda.groups.none { it.key != LmsAgendaGroupKey.COMPLETED }
            item(key = rows.key("lms_state")) {
                val changes = LocalListChangeMotion.current
                AnimatedContent(
                    targetState = shownPlaceholder,
                    contentKey = { it.key },
                    transitionSpec = { lmsBlockChange(changes) },
                    label = "lms_state",
                    modifier = dimaItemChange(fadeIn = true, fadeOut = emptyInBothModes).padding(top = top),
                ) { state ->
                    when (state) {
                        // A read that ends within the grace shows nothing (D-137).
                        LmsListPlaceholder.Loading -> Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp)
                                .loadingGrace(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            DimaLoadingIndicator()
                            Text("수업 정보를 불러오고 있어요", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        is LmsListPlaceholder.Failed -> ErrorState(
                            message = "수업 정보를 불러오지 못했어요",
                            supporting = state.supporting,
                            onRetry = onRefresh,
                            modifier = Modifier.testTag("lms_error"),
                        )
                        is LmsListPlaceholder.Empty -> EmptyState(
                            message = state.message,
                            supporting = state.supporting,
                            actionLabel = state.action?.label,
                            onAction = when (state.action) {
                                LmsEmptyAction.REFRESH, LmsEmptyAction.LOAD -> onRefresh
                                LmsEmptyAction.CLEAR_FILTERS -> ({ onCourseChange(null); onKindChange(null) })
                                null -> null
                            },
                            modifier = Modifier.testTag("lms_empty"),
                        )
                    }
                }
            }
        }
        // Keep cached completed learning available below the status, even while refreshing or retrying.
        // D-137: headings, rows and folds added by a data change fade in on their own; a whole-list switch (할 일 ↔
        // 과목별) turns that off through ScreenScaffold. They never fade out, since a removed item keeps the specs of its
        // last composition and would also fade during the switch (D-094(12)), and nothing slides into the place of a
        // fading row (see lmsRowItems).
        if (pageToday) {
            // D-117: 다가오는 마감 → 새 소식 → 기한 지남, 섹션마다 머리글 아래 카드 묶음 하나
            agenda.groups.forEach { group ->
                val top = rows.gap(LMS_BLOCK_GAP)
                if (group.key == LmsAgendaGroupKey.COMPLETED) {
                    item(key = rows.key("agenda-${group.key}"), contentType = LMS_FOLD) {
                        LmsFoldedSection(
                            title = group.title,
                            items = group.items,
                            expanded = TODO_COMPLETED in openSections,
                            onToggle = { toggleSection(TODO_COMPLETED) },
                            now = now,
                            onOpenItem = onOpenItem,
                            tag = "lms_completed_toggle",
                            completed = true,
                            selected = ::selectedOf,
                            opening = opening,
                            modifier = dimaItemChange(fadeIn = true, fadeOut = false).padding(top = top),
                        )
                    }
                } else {
                    item(key = rows.key("agenda-${group.key}"), contentType = LMS_HEADER) {
                        LmsSectionHeader(group.title, group.items.size, dimaItemChange(fadeIn = true, fadeOut = false).padding(top = top))
                    }
                    lmsRowItems(rows, "agenda-${group.key}", group.items, now, onOpenItem, showCourseName = true, selected = ::selectedOf, opening = opening)
                }
            }
        } else {
            // 과목별 (D-118): 과목마다 남은 항목, 접힌 기한 지남, 접힌 완료한 학습 순서
            coursePlan.groups.forEach { group ->
                val headerTop = rows.gap(LMS_BLOCK_GAP)
                item(key = rows.key("course-${group.courseId}"), contentType = LMS_HEADER) {
                    LmsSectionHeader(
                        group.courseName,
                        group.items.size.takeIf { it > 0 },
                        dimaItemChange(fadeIn = true, fadeOut = false).padding(top = headerTop),
                    )
                }
                if (group.items.isNotEmpty()) {
                    lmsRowItems(rows, "course-${group.courseId}", group.items, now, onOpenItem, showCourseName = false, selected = ::selectedOf, opening = opening)
                } else {
                    val top = rows.gap(LMS_GROUP_GAP)
                    item(key = rows.key("course-${group.courseId}-empty")) {
                        // Only folds left: say so plainly, as text that does not look like something to tap.
                        Text(
                            "남은 학습이 없어요",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = dimaItemChange(fadeIn = true, fadeOut = false)
                                .padding(top = top)
                                .padding(horizontal = 4.dp)
                                .testTag("lms_course_empty_${group.courseId}"),
                        )
                    }
                }
                if (group.overdue.isNotEmpty()) {
                    val key = "course-${group.courseId}-overdue"
                    val top = rows.gap(LMS_GROUP_GAP)
                    item(key = rows.key(key), contentType = LMS_FOLD) {
                        LmsFoldedSection(
                            title = "기한 지남",
                            items = group.overdue,
                            expanded = key in openSections,
                            onToggle = { toggleSection(key) },
                            now = now,
                            onOpenItem = onOpenItem,
                            tag = "lms_overdue_toggle_${group.courseId}",
                            completed = false,
                            showCourseName = false,
                            selected = ::selectedOf,
                            opening = opening,
                            modifier = dimaItemChange(fadeIn = true, fadeOut = false).padding(top = top),
                        )
                    }
                }
                if (group.completed.isNotEmpty()) {
                    val key = "course-${group.courseId}-completed"
                    val top = rows.gap(LMS_GROUP_GAP)
                    item(key = rows.key(key), contentType = LMS_FOLD) {
                        LmsFoldedSection(
                            title = "완료한 학습",
                            items = group.completed,
                            expanded = key in openSections,
                            onToggle = { toggleSection(key) },
                            now = now,
                            onOpenItem = onOpenItem,
                            tag = "lms_completed_toggle_${group.courseId}",
                            completed = true,
                            showCourseName = false,
                            selected = ::selectedOf,
                            opening = opening,
                            modifier = dimaItemChange(fadeIn = true, fadeOut = false).padding(top = top),
                        )
                    }
                }
            }
        }
    }
}

/**
 * How a block of the 수업 list changes in place (D-137): [dimaContentReveal], or at once while ScreenScaffold fades
 * the whole list in a 할 일 ↔ 과목별 switch ([LocalListChangeMotion] false), so the block does not also animate the
 * same change (D-094(12)).
 */
private fun lmsBlockChange(changes: Boolean): ContentTransform =
    if (changes) dimaContentReveal() else ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)

/** The line or card above the 수업 list (D-137); [kind] keys its change, so only another kind cross-fades. */
internal sealed interface LmsListStatus {
    val kind: String

    /** D-135: another device most likely holds the LMS session. */
    data class Elsewhere(val lastSuccessAt: Instant?) : LmsListStatus {
        override val kind: String get() = "elsewhere"
    }

    /** A refresh failed over a list in hand (D-056). */
    data class Failed(val message: String, val lastSuccessAt: Instant?) : LmsListStatus {
        override val kind: String get() = "failed"
    }

    /** When the list was read (D-117). */
    data class Updated(val label: String) : LmsListStatus {
        override val kind: String get() = "updated"
    }

    data object None : LmsListStatus {
        override val kind: String get() = "none"
    }
}

/**
 * Pure choice of [LmsListStatus], in the order the list always used: the sign-in elsewhere first, then a failed
 * refresh over a list in hand ([showsError]), then the read time ([updatedLabel], null for an empty list).
 */
internal fun lmsListStatus(
    signedInElsewhere: Boolean,
    showsError: Boolean,
    updatedLabel: String?,
    errorMessage: String,
    lastSuccessAt: Instant?,
): LmsListStatus = when {
    signedInElsewhere -> LmsListStatus.Elsewhere(lastSuccessAt)
    showsError -> LmsListStatus.Failed(errorMessage, lastSuccessAt)
    updatedLabel != null -> LmsListStatus.Updated(updatedLabel)
    else -> LmsListStatus.None
}

/** What stands in for an empty 수업 list (D-137); [key] keys its change, so each message cross-fades into the next. */
internal sealed interface LmsListPlaceholder {
    val key: String

    data object Loading : LmsListPlaceholder {
        override val key: String get() = "loading"
    }

    data class Failed(val supporting: String) : LmsListPlaceholder {
        override val key: String get() = "failed"
    }

    data class Empty(val message: String, val supporting: String?, val action: LmsEmptyAction?) : LmsListPlaceholder {
        override val key: String get() = "empty:$message"
    }
}

/** The one follow-up an empty 수업 list offers. */
internal enum class LmsEmptyAction(val label: String) {
    REFRESH("다시 시도"),
    LOAD("수업 정보 불러오기"),
    CLEAR_FILTERS("필터 초기화"),
}

/** Pure wording of an empty 수업 list (D-094(14), D-117), unchanged from the list it replaced. */
internal fun lmsEmptyPlaceholder(
    interactiveAuth: Boolean,
    needsSignIn: Boolean,
    neverLoaded: Boolean,
    filterActive: Boolean,
    todayMode: Boolean,
): LmsListPlaceholder.Empty = LmsListPlaceholder.Empty(
    message = when {
        interactiveAuth -> "추가 인증이 필요해요"
        needsSignIn -> "수업 정보를 확인하려면 로그인해 주세요"
        neverLoaded -> "아직 불러온 수업 정보가 없어요"
        filterActive -> "선택한 조건에 맞는 학습이 없어요"
        todayMode -> "남은 할 일이 없어요"
        else -> "등록된 학습이 없어요"
    },
    supporting = if (todayMode && !needsSignIn && !neverLoaded && !filterActive) "새 과제나 공지가 올라오면 여기에 보여요." else null,
    action = when {
        needsSignIn -> LmsEmptyAction.REFRESH
        neverLoaded -> LmsEmptyAction.LOAD
        filterActive -> LmsEmptyAction.CLEAR_FILTERS
        else -> null
    },
)

/**
 * D-135: the session ended within the LMS's two hours of this device's last use, so another device most likely holds
 * it. Not an error: the saved list stays, and signing in here is the student's choice. The app cannot tell when the
 * other place is done (closing a site does not sign it out), so the card asks the student to tap once it is.
 */
@Composable
private fun LmsSignedInElsewhereCard(
    lastSuccessAt: Instant?,
    onSignInHere: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        message = "다른 곳에서 LMS에 로그인한 것 같아요",
        supporting = "그곳에서 다 썼다면 이 기기로 로그인을 눌러 주세요. 쓰는 중에 누르면 그곳의 로그인이 끊겨서, " +
            "풀고 있던 시험이나 과제가 저장되지 않을 수 있어요. " +
            (lastSuccessAt?.let { "저장된 목록은 ${LMS_DETAIL_TIME.format(it.atZone(SEOUL))}에 받은 거예요." } ?: "저장된 목록을 보여 드려요."),
        actionLabel = "이 기기로 로그인",
        onAction = onSignInHere,
        actionEnabled = enabled,
        actionTag = "lms_sign_in_here",
        modifier = modifier.testTag("lms_signed_in_elsewhere"),
    )
}

/** 캐시된 목록 위에 남는 갱신 실패 배너. 마지막 성공 시각을 함께 알려준다 (D-056), 공용 ErrorState (D-094(14)). */
@Composable
private fun LmsRefreshErrorBanner(
    message: String,
    lastSuccessAt: Instant?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ErrorState(
        message = message,
        supporting = lastSuccessAt
            ?.let { "저장된 목록을 보여 드려요 · 마지막 갱신 ${LMS_DETAIL_TIME.format(it.atZone(SEOUL))}" }
            ?: "저장된 목록을 보여 드려요",
        onRetry = onRetry,
        modifier = modifier.testTag("lms_refresh_error_banner"),
    )
}

/**
 * A fold's rows as an M3 Expressive segmented list on the card tone, as the 전체 and 설정 pages are (D-117). The open
 * sections' rows are lazy items of their own ([lmsRowItems], D-131); a fold keeps its rows together so it opens as one
 * block on the expressive spring (D-118).
 */
@Composable
private fun LmsItemList(
    items: List<LmsItem>,
    now: Instant,
    onOpenItem: (LmsItem) -> Unit,
    modifier: Modifier = Modifier,
    showCourseName: Boolean = true,
    completed: Boolean = false,
    muted: Boolean = false,
    selected: (LmsItem) -> Boolean? = { null },
    opening: (LmsItem) -> LmsRowOpening = { LmsRowOpening.NONE },
) {
    DimaSegmentedList(modifier) {
        items.forEachIndexed { index, item ->
            key(item.kind, item.courseId, item.id) {
                LmsItemRow(
                    item, index, items.size, now, onOpenItem, showCourseName, completed,
                    muted = muted, selected = selected(item), opening = opening(item),
                )
            }
        }
    }
}

/**
 * One open section's rows as lazy items of their own (D-131, Q-032 (g)), keyed by the LMS entry and shaped as one
 * segmented list: the first row sits [LMS_GROUP_GAP] under its heading, the others [ListItemDefaults.SegmentedGap]
 * apart, with the group's outer corners large and the inner ones small, as [DimaSegmentedList] lays them out.
 *
 * D-137: a row added by a data change fades in. Rows do not glide to new places: the folds and the status above them
 * animate their own height, and placed rows would chase every frame of it; without the glide a fading row would have
 * the next row jump under it, so a removed row simply goes, as before.
 */
private fun LazyListScope.lmsRowItems(
    rows: LmsLazyRows,
    section: String,
    items: List<LmsItem>,
    now: Instant,
    onOpenItem: (LmsItem) -> Unit,
    showCourseName: Boolean,
    selected: (LmsItem) -> Boolean?,
    opening: (LmsItem) -> LmsRowOpening,
) {
    items.forEachIndexed { index, item ->
        val top = rows.gap(if (index == 0) LMS_GROUP_GAP else ListItemDefaults.SegmentedGap)
        item(key = rows.key("$section-row-${item.entryKey()}"), contentType = LMS_ROW) {
            LmsItemRow(
                item = item,
                index = index,
                count = items.size,
                now = now,
                onOpenItem = onOpenItem,
                showCourseName = showCourseName,
                completed = false,
                selected = selected(item),
                opening = opening(item),
                modifier = dimaItemChange(fadeIn = true, fadeOut = false).padding(top = top),
            )
        }
    }
}

/**
 * Builds the 수업 list's lazy items (D-131): the gap above each item (none above the first, as the list's spacing had)
 * and keys that stay unique even if the LMS ever listed one entry twice, since a repeated lazy key would crash the list.
 */
private class LmsLazyRows {
    private var first = true
    private val used = HashMap<String, Int>()

    fun gap(space: Dp): Dp = if (first) {
        first = false
        0.dp
    } else {
        space
    }

    fun key(base: String): String {
        val seen = (used[base] ?: 0) + 1
        used[base] = seen
        return if (seen == 1) base else "$base#$seen"
    }
}

/** One LMS entry, as the cache keys it: an item keeps this through refreshes and detail loads. */
internal fun LmsItem.entryKey(): String = "$kind:$courseId:$id"

/** Between blocks of the 수업 list (the list's item spacing before its rows became items, D-131). */
private val LMS_BLOCK_GAP = 12.dp

/** Under a section heading and before a fold, as the course group's column spaced them (D-118). */
private val LMS_GROUP_GAP = 8.dp

private const val LMS_HEADER = "lms_header"
private const val LMS_ROW = "lms_row"
private const val LMS_FOLD = "lms_fold"

/**
 * One learning item (D-117): its kind as an icon, the title, `새 항목 · 과제 · 과목` and the exact deadline below,
 * and on the right how much time is left. Only a deadline within about two days takes the warning color, and its
 * words say so too. Completion is the section's job, so rows no longer repeat 미완료. D-118: the title is title-medium
 * on the text color and the course name Bold on the text color, while the kind stays quieter.
 *
 * D-131: beside the detail pane on a wide window, [selected] says whether this row is the one open there; the open
 * row takes the secondary container tone and is announced as selected. Null on phones (no selection state).
 * D-137: there the row is the selectable segmented item, so the selection eases its tone and morphs its shape with
 * the theme's motion scheme.
 *
 * D-137: while [opening] is [LmsRowOpening.SHOWN] (the tap is being answered past the loading grace) the kind mark
 * turns into the loading indicator in the same fixed slot, so the title never moves, and screen readers hear `여는 중`.
 */
@Composable
private fun LmsItemRow(
    item: LmsItem,
    index: Int,
    count: Int,
    now: Instant,
    onOpenItem: (LmsItem) -> Unit,
    showCourseName: Boolean,
    completed: Boolean,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    selected: Boolean? = null,
    opening: LmsRowOpening = LmsRowOpening.NONE,
) {
    val reduced = LocalReducedMotion.current
    val due = if (completed) null else item.dueAt?.let { lmsDueLabel(it, now) }
    val changeColor = MaterialTheme.colorScheme.primary
    // D-119: folded rows (기한 지남, 완료한 학습) sit on a quieter tone with quieter text than what is still to do.
    val textColor = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val courseColor = textColor
    val meta = buildAnnotatedString {
        when (item.changeState) {
            LmsChangeState.NEW -> "새 항목"
            LmsChangeState.UPDATED -> "변경됨"
            LmsChangeState.NONE -> null
        }?.let { change ->
            withStyle(SpanStyle(color = changeColor, fontWeight = FontWeight.ExtraBold)) { append(change) }
            append(" · ")
        }
        append(kindLabel(item.kind))
        if (showCourseName) {
            append(" · ")
            withStyle(SpanStyle(color = courseColor, fontWeight = FontWeight.Bold)) { append(item.courseName) }
        }
    }
    val rowModifier = modifier.semantics {
        onClick(label = "열기") { onOpenItem(item); true }
        if (opening == LmsRowOpening.SHOWN) stateDescription = "여는 중"
    }
    val leading: @Composable () -> Unit = {
        if (opening == LmsRowOpening.NONE) {
            DimaIconBadge(lmsKindIcon(item.kind), size = LMS_ROW_MARK_SIZE, muted = muted)
        } else {
            Box(Modifier.size(LMS_ROW_MARK_SIZE), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = opening == LmsRowOpening.SHOWN,
                    transitionSpec = { dimaIconSwap(reduced) },
                    label = "lms_row_opening",
                ) { busy ->
                    if (busy) DimaLoadingIndicator(size = 32.dp) else DimaIconBadge(lmsKindIcon(item.kind), size = LMS_ROW_MARK_SIZE, muted = muted)
                }
            }
        }
    }
    val trailing: (@Composable () -> Unit)? = due?.let { label ->
        {
            Text(
                label.text,
                style = if (label.urgent) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
                color = if (label.urgent) DimaTheme.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val supporting: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                meta,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            lmsWhenLine(item)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    val title: @Composable () -> Unit = {
        Text(
            item.title,
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    val shapes = ListItemDefaults.segmentedShapes(index = index, count = count)
    if (selected != null) {
        // The selectable overload announces the selection itself, so the row does not set it again.
        SegmentedListItem(
            selected = selected,
            onClick = { onOpenItem(item) },
            shapes = shapes,
            modifier = rowModifier,
            leadingContent = leading,
            trailingContent = trailing,
            supportingContent = supporting,
            colors = ListItemDefaults.segmentedColors(
                containerColor = if (muted) DimaTheme.colors.cardMuted else DimaTheme.colors.card,
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
            content = title,
        )
    } else {
        SegmentedListItem(
            onClick = { onOpenItem(item) },
            shapes = shapes,
            modifier = rowModifier,
            leadingContent = leading,
            trailingContent = trailing,
            supportingContent = supporting,
            colors = if (muted) ListItemDefaults.segmentedColors(containerColor = DimaTheme.colors.cardMuted) else dimaSegmentedListColors(),
            content = title,
        )
    }
}

/** The kind mark's slot at a row's start; the opening indicator takes the same slot (D-137). */
private val LMS_ROW_MARK_SIZE = 40.dp

/**
 * A row's part in opening an item (D-137): [NONE] for most rows, [SLOT] for the row tapped last, whose mark sits in the
 * swap slot from the tap on (so the swap after the grace animates), and [SHOWN] while its loading indicator shows.
 */
private enum class LmsRowOpening { NONE, SLOT, SHOWN }

/** The entry tapped last; a plain holder written while composing, since it only follows the opening key. */
private class LmsLastOpened {
    var key: String? = null
}

private const val TODO_COMPLETED = "todo-completed"

/** Each kind's mark in the row's tonal circle; decorative, the row's text names the kind. */
internal fun lmsKindIcon(kind: LmsItemKind): ImageVector = when (kind) {
    LmsItemKind.NOTICE -> Icons.Default.Campaign
    LmsItemKind.ASSIGNMENT -> Icons.AutoMirrored.Filled.Assignment
    LmsItemKind.CONTENT -> Icons.Default.PlayCircle
    LmsItemKind.MATERIAL -> Icons.Default.Description
    LmsItemKind.QUESTION -> Icons.Default.QuestionAnswer
    LmsItemKind.DISCUSSION -> Icons.Default.Forum
    LmsItemKind.TEAM_PROJECT -> Icons.Default.Groups
    LmsItemKind.QUIZ -> Icons.Default.Quiz
    LmsItemKind.EXAM -> Icons.Default.HistoryEdu
    LmsItemKind.OTHER -> Icons.AutoMirrored.Filled.Article
}

/** 과목과 종류로만 거른다. 읽음/안읽음 필터는 제거했다 (D-058). */
internal fun filterLmsItems(
    items: List<LmsItem>,
    courseId: String?,
    kind: LmsItemKind?,
): List<LmsItem> = items.filter { item ->
    (courseId == null || item.courseId == courseId) && (kind == null || item.kind == kind)
}

internal fun kindLabel(kind: LmsItemKind): String = when (kind) {
    LmsItemKind.NOTICE -> "공지"
    LmsItemKind.ASSIGNMENT -> "과제"
    LmsItemKind.CONTENT -> "콘텐츠"
    LmsItemKind.MATERIAL -> "자료"
    LmsItemKind.QUESTION -> "질문"
    LmsItemKind.DISCUSSION -> "토론"
    LmsItemKind.TEAM_PROJECT -> "팀프로젝트"
    LmsItemKind.QUIZ -> "퀴즈"
    LmsItemKind.EXAM -> "시험"
    LmsItemKind.OTHER -> "기타"
}
