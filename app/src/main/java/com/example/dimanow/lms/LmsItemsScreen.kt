package com.example.dimanow.lms

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Surface
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.dimanow.theme.DimaTheme
import com.example.dimanow.ui.DimaIconBadge
import com.example.dimanow.ui.DimaSegmentedList
import com.example.dimanow.ui.EmptyState
import com.example.dimanow.ui.ExpandChevron
import com.example.dimanow.ui.expandableSemantics
import com.example.dimanow.ui.ErrorState
import com.example.dimanow.ui.ScreenScaffold
import com.example.dimanow.ui.dimaSegmentedListColors
import com.example.dimanow.ui.motion.DimaMotion
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
        LmsFilterMenuChip(
            label = selectedCourse?.let { id -> courses.firstOrNull { it.id == id }?.name } ?: "전체 과목",
            selected = selectedCourse != null,
            tag = "lms_course_filter",
            options = listOf<Pair<String, String?>>("전체 과목" to null) + courses.map { it.name to it.id },
            optionTag = { null },
            onSelect = onCourseChange,
            modifier = Modifier.weight(1f),
        )
        if (filterActive) {
            IconButton(shapes = IconButtonDefaults.shapes(), 
                onClick = { onCourseChange(null); onKindChange(null) },
                modifier = Modifier.testTag("lms_filter_clear"),
            ) {
                Icon(Icons.Default.Close, contentDescription = "필터 초기화")
            }
        }
    }
}

/** 선택값을 라벨로 보여주고 누르면 선택지를 메뉴로 펼치는 필터 칩 (D-058). */
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
    Box(modifier = modifier) {
        FilterChip(
            selected = selected,
            onClick = { expanded = true },
            label = {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            trailingIcon = {
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
            },
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
 */
@Composable
private fun LmsSectionHeader(title: String, count: Int?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(start = 4.dp, top = 12.dp).semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.alignByBaseline().weight(1f, fill = false),
        )
        count?.let {
            Text(
                "${it}개",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.alignByBaseline(),
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
) {
    Column(modifier) {
        LmsFoldHeader(title = title, count = items.size, completed = completed, expanded = expanded, onToggle = onToggle, tag = tag)
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(DimaMotion.spatialDefault()) + fadeIn(DimaMotion.effectsDefault()),
            exit = shrinkVertically(DimaMotion.spatialDefault()) + fadeOut(DimaMotion.effectsFast()),
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
            Text("${count}개", style = MaterialTheme.typography.labelLarge)
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
     * D-131 (Q-025): true while the list sits beside the detail pane on a wide window. Every row then says whether it
     * is the one open there ([selection]); on phones the detail takes the whole screen and rows carry no such state.
     */
    selectable: Boolean = false,
    /** D-131: the item open in the detail pane beside the list, marked as the selected row. */
    selection: LmsItem? = null,
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
    val visibleEmpty = if (todayMode) {
        agenda.groups.none { it.key != LmsAgendaGroupKey.COMPLETED }
    } else {
        filtered.isEmpty()
    }

    ScreenScaffold(
        title = "수업",
        modifier = modifier,
        listTag = "lms_history",
        // D-129: 할 일(0) and 과목별(1) slide in from their side
        pageIndex = if (todayMode) 0 else 1,
        // 새로고침은 목록을 당기거나 상단 버튼으로 실행한다 (D-058, D-103)
        onRefresh = onRefresh,
        refreshing = syncing && !visibleEmpty,
        refreshLabel = "수업 새로고침",
        // D-131: rows are items of their own, so each item sets the gap above it (LmsLazyRows).
        itemSpacing = 0.dp,
        // 모드 전환과 필터는 목록을 아무리 내려도 항상 닿을 수 있어야 한다 (D-057).
        subHeader = {
            // D-103: 모드 전환은 M3 연결형 버튼 그룹이다. D-117: 하단 탭 '전체'와 겹치지 않게 '할 일'·'과목별'로 부른다
            com.example.dimanow.ui.DimaConnectedChoice(
                options = listOf(true to "할 일", false to "과목별"),
                selected = todayMode,
                onSelect = { todayMode = it },
                itemTag = { if (it) "lms_mode_today" else "lms_mode_all" },
            )
            if (!todayMode || filterActive) {
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
        },
    ) {
        // D-131 (Q-032 (g)): every section row is its own lazy item, so only the rows on screen compose (a course with
        // 80 notices used to compose all of them in one item). The list therefore has no item spacing of its own; each
        // item carries the gap above it, as the group columns had: 12dp between blocks, 8dp under a heading and before
        // a fold, the segmented gap between rows. Folded rows stay inside their fold, which opens as one block (D-118).
        val rows = LmsLazyRows()
        fun selectedOf(item: LmsItem): Boolean? = if (selectable) selection?.entryKey() == item.entryKey() else null

        // 캐시된 목록 위에서 갱신이 실패하면 스낵바가 사라진 뒤에도 상태를 알 수 있게 배너로 남긴다 (D-056)
        if (hasError && !visibleEmpty) {
            val top = rows.gap(LMS_BLOCK_GAP)
            item(key = rows.key("lms_refresh_error")) {
                LmsRefreshErrorBanner(
                    message = loginErrorMessage ?: "수업 정보를 불러오지 못했어요",
                    lastSuccessAt = snapshot.lastSuccessAt,
                    onRetry = onRefresh,
                    modifier = Modifier.padding(top = top),
                )
            }
        } else if (!visibleEmpty) {
            // D-117: 목록이 언제 것인지 한 줄로 알린다
            snapshot.lastSuccessAt?.let { readAt ->
                val top = rows.gap(LMS_BLOCK_GAP)
                item(key = rows.key("lms_updated")) {
                    Text(
                        lmsUpdatedLabel(readAt, now),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = top).padding(horizontal = 4.dp).testTag("lms_updated"),
                    )
                }
            }
        }

        when {
            visibleEmpty && syncing -> {
                val top = rows.gap(LMS_BLOCK_GAP)
                item(key = rows.key("lms_loading")) {
                    Column(
                        modifier = Modifier
                            .padding(top = top)
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        com.example.dimanow.ui.DimaLoadingIndicator()
                        Text("수업 정보를 불러오고 있어요", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            visibleEmpty && hasError -> {
                val top = rows.gap(LMS_BLOCK_GAP)
                item(key = rows.key("lms_error")) {
                    // 원문 오류(HTTP 코드 등)는 보이지 않고, 무슨 일인지와 다음 행동만 알린다 (D-094(14))
                    ErrorState(
                        message = "수업 정보를 불러오지 못했어요",
                        supporting = loginErrorMessage ?: "인터넷 연결을 확인하고 다시 시도해 주세요",
                        onRetry = onRefresh,
                        modifier = Modifier.padding(top = top).testTag("lms_error"),
                    )
                }
            }
            visibleEmpty -> {
                val top = rows.gap(LMS_BLOCK_GAP)
                item(key = rows.key("lms_empty")) {
                    val needsSignIn = sessionState != LmsSessionState.ACTIVE
                    val neverLoaded = snapshot.syncState == LmsSyncState.IDLE && snapshot.lastSuccessAt == null
                    EmptyState(
                        message = when {
                            sessionState == LmsSessionState.INTERACTIVE_AUTH_REQUIRED -> "추가 인증이 필요해요"
                            needsSignIn -> "수업 정보를 확인하려면 로그인해 주세요"
                            neverLoaded -> "아직 불러온 수업 정보가 없어요"
                            filterActive -> "선택한 조건에 맞는 학습이 없어요"
                            todayMode -> "남은 할 일이 없어요"
                            else -> "등록된 학습이 없어요"
                        },
                        supporting = if (todayMode && !needsSignIn && !neverLoaded && !filterActive) {
                            "새 과제나 공지가 올라오면 여기에 보여요."
                        } else {
                            null
                        },
                        actionLabel = when {
                            needsSignIn -> "다시 시도"
                            neverLoaded -> "수업 정보 불러오기"
                            filterActive -> "필터 초기화"
                            else -> null
                        },
                        onAction = when {
                            needsSignIn || neverLoaded -> onRefresh
                            filterActive -> ({ onCourseChange(null); onKindChange(null) })
                            else -> null
                        },
                        modifier = Modifier.padding(top = top).testTag("lms_empty"),
                    )
                }
            }
        }
        // Keep cached completed learning available below the status, even while refreshing or retrying.
        if (todayMode) {
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
                            modifier = Modifier.padding(top = top),
                        )
                    }
                } else {
                    item(key = rows.key("agenda-${group.key}"), contentType = LMS_HEADER) {
                        LmsSectionHeader(group.title, group.items.size, Modifier.padding(top = top))
                    }
                    lmsRowItems(rows, "agenda-${group.key}", group.items, now, onOpenItem, showCourseName = true, selected = ::selectedOf)
                }
            }
        } else {
            // 과목별 (D-118): 과목마다 남은 항목, 접힌 기한 지남, 접힌 완료한 학습 순서
            coursePlan.groups.forEach { group ->
                val headerTop = rows.gap(LMS_BLOCK_GAP)
                item(key = rows.key("course-${group.courseId}"), contentType = LMS_HEADER) {
                    LmsSectionHeader(group.courseName, group.items.size.takeIf { it > 0 }, Modifier.padding(top = headerTop))
                }
                if (group.items.isNotEmpty()) {
                    lmsRowItems(rows, "course-${group.courseId}", group.items, now, onOpenItem, showCourseName = false, selected = ::selectedOf)
                } else {
                    val top = rows.gap(LMS_GROUP_GAP)
                    item(key = rows.key("course-${group.courseId}-empty")) {
                        // Only folds left: say so plainly, as text that does not look like something to tap.
                        Text(
                            "남은 학습이 없어요",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = top).padding(horizontal = 4.dp).testTag("lms_course_empty_${group.courseId}"),
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
                            modifier = Modifier.padding(top = top),
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
                            modifier = Modifier.padding(top = top),
                        )
                    }
                }
            }
        }
    }
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
) {
    DimaSegmentedList(modifier) {
        items.forEachIndexed { index, item ->
            key(item.kind, item.courseId, item.id) {
                LmsItemRow(item, index, items.size, now, onOpenItem, showCourseName, completed, muted = muted, selected = selected(item))
            }
        }
    }
}

/**
 * One open section's rows as lazy items of their own (D-131, Q-032 (g)), keyed by the LMS entry and shaped as one
 * segmented list: the first row sits [LMS_GROUP_GAP] under its heading, the others [ListItemDefaults.SegmentedGap]
 * apart, with the group's outer corners large and the inner ones small, as [DimaSegmentedList] lays them out.
 */
private fun LazyListScope.lmsRowItems(
    rows: LmsLazyRows,
    section: String,
    items: List<LmsItem>,
    now: Instant,
    onOpenItem: (LmsItem) -> Unit,
    showCourseName: Boolean,
    selected: (LmsItem) -> Boolean?,
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
                modifier = Modifier.padding(top = top),
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
private fun LmsItem.entryKey(): String = "$kind:$courseId:$id"

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
) {
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
    SegmentedListItem(
        onClick = { onOpenItem(item) },
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier = modifier.semantics {
            onClick(label = "열기") { onOpenItem(item); true }
            selected?.let { this.selected = it }
        },
        leadingContent = { DimaIconBadge(lmsKindIcon(item.kind), muted = muted) },
        trailingContent = due?.let { label ->
            {
                Text(
                    label.text,
                    style = if (label.urgent) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
                    color = if (label.urgent) DimaTheme.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        supportingContent = {
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
        },
        colors = when {
            selected == true -> ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            muted -> ListItemDefaults.segmentedColors(containerColor = DimaTheme.colors.cardMuted)
            else -> dimaSegmentedListColors()
        },
    ) {
        Text(
            item.title,
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
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
