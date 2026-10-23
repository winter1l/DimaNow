package com.example.dimanow.lms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.dimanow.theme.DimaTheme
import com.example.dimanow.ui.DimaIconBadge
import com.example.dimanow.ui.DimaSegmentedList
import com.example.dimanow.ui.EmptyState
import com.example.dimanow.ui.ErrorState
import com.example.dimanow.ui.ScreenScaffold
import com.example.dimanow.ui.SettingsExpander
import com.example.dimanow.ui.SettingsSectionHeader
import com.example.dimanow.ui.dimaSegmentedListColors
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

/** A section's heading with how many rows it holds; screen readers can jump between sections (D-117). */
@Composable
private fun LmsSectionHeader(title: String, count: Int) {
    SettingsSectionHeader("$title · ${count}개")
}

/** 완료한 학습, folded under one row that says how many there are (D-117, as D-058 folded it). */
@Composable
private fun LmsCompletedSection(
    items: List<LmsItem>,
    expanded: Boolean,
    onToggle: () -> Unit,
    now: Instant,
    onOpenItem: (LmsItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsExpander(
            title = "완료한 학습",
            supporting = "${items.size}개",
            expanded = expanded,
            onToggle = onToggle,
            modifier = Modifier.testTag("lms_completed_toggle"),
        )
        if (expanded) LmsItemList(items, now, onOpenItem, completed = true)
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
) {
    val filtered = filterLmsItems(snapshot.items, selectedCourse, selectedKind)
    val filterActive = selectedCourse != null || selectedKind != null
    val syncing = snapshot.syncState == LmsSyncState.SYNCING || sessionState == LmsSessionState.AUTHENTICATING
    val hasError = loginErrorMessage != null || sessionState == LmsSessionState.ERROR || snapshot.syncState == LmsSyncState.ERROR
    var todayMode by rememberSaveable { mutableStateOf(true) }
    var completedExpanded by rememberSaveable { mutableStateOf(false) }
    // 할 일에서도 선택한 필터를 그대로 적용한다 (이전에는 칩이 조용히 무시됐다)
    val agenda = remember(filtered, now) {
        LmsAgendaPlanner(Clock.fixed(now, SEOUL)).plan(filtered, now)
    }
    // 과목별은 과목 순서로 묶고 완료한 학습을 아래로 내린다 (D-058)
    val coursePlan = remember(filtered, snapshot.courses) { planLmsByCourse(filtered, snapshot.courses) }
    val visibleEmpty = if (todayMode) {
        agenda.groups.none { it.key != LmsAgendaGroupKey.COMPLETED }
    } else {
        filtered.isEmpty()
    }

    ScreenScaffold(
        title = "수업",
        modifier = modifier,
        listTag = "lms_history",
        // 새로고침은 목록을 당기거나 상단 버튼으로 실행한다 (D-058, D-103)
        onRefresh = onRefresh,
        refreshing = syncing && !visibleEmpty,
        refreshLabel = "수업 새로고침",
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
        // 캐시된 목록 위에서 갱신이 실패하면 스낵바가 사라진 뒤에도 상태를 알 수 있게 배너로 남긴다 (D-056)
        if (hasError && !visibleEmpty) {
            item(key = "lms_refresh_error") {
                LmsRefreshErrorBanner(
                    message = loginErrorMessage ?: "수업 정보를 불러오지 못했어요",
                    lastSuccessAt = snapshot.lastSuccessAt,
                    onRetry = onRefresh,
                )
            }
        } else if (!visibleEmpty) {
            // D-117: 목록이 언제 것인지 한 줄로 알린다
            snapshot.lastSuccessAt?.let { readAt ->
                item(key = "lms_updated") {
                    Text(
                        lmsUpdatedLabel(readAt, now),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp).testTag("lms_updated"),
                    )
                }
            }
        }

        when {
            visibleEmpty && syncing -> {
                item(key = "lms_loading") {
                    Column(
                        modifier = Modifier
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
                item(key = "lms_error") {
                    // 원문 오류(HTTP 코드 등)는 보이지 않고, 무슨 일인지와 다음 행동만 알린다 (D-094(14))
                    ErrorState(
                        message = "수업 정보를 불러오지 못했어요",
                        supporting = loginErrorMessage ?: "인터넷 연결을 확인하고 다시 시도해 주세요",
                        onRetry = onRefresh,
                        modifier = Modifier.testTag("lms_error"),
                    )
                }
            }
            visibleEmpty -> {
                item(key = "lms_empty") {
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
                        modifier = Modifier.testTag("lms_empty"),
                    )
                }
            }
        }
        // Keep cached completed learning available below the status, even while refreshing or retrying.
        if (todayMode) {
            // D-117: 다가오는 마감 → 새 소식 → 기한 지남, 섹션마다 카드 묶음 하나
            agenda.groups.forEach { group ->
                item(key = "agenda-${group.key}") {
                    if (group.key == LmsAgendaGroupKey.COMPLETED) {
                        LmsCompletedSection(group.items, completedExpanded, { completedExpanded = !completedExpanded }, now, onOpenItem)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LmsSectionHeader(group.title, group.items.size)
                            LmsItemList(group.items, now, onOpenItem)
                        }
                    }
                }
            }
        } else {
            // 과목별: 과목 순서대로 묶고, 완료한 학습은 할 일과 똑같이 맨 아래에 접어 둔다 (D-058)
            coursePlan.groups.forEach { group ->
                item(key = "course-${group.courseId}") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LmsSectionHeader(group.courseName, group.items.size)
                        LmsItemList(group.items, now, onOpenItem, showCourseName = false)
                    }
                }
            }
            if (coursePlan.completed.isNotEmpty()) {
                item(key = "all-completed") {
                    LmsCompletedSection(coursePlan.completed, completedExpanded, { completedExpanded = !completedExpanded }, now, onOpenItem)
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

/** One section's rows as an M3 Expressive segmented list on the card tone, as the 전체 and 설정 pages are (D-117). */
@Composable
private fun LmsItemList(
    items: List<LmsItem>,
    now: Instant,
    onOpenItem: (LmsItem) -> Unit,
    showCourseName: Boolean = true,
    completed: Boolean = false,
) {
    DimaSegmentedList {
        items.forEachIndexed { index, item ->
            key(item.kind, item.courseId, item.id) {
                LmsItemRow(item, index, items.size, now, onOpenItem, showCourseName, completed)
            }
        }
    }
}

/**
 * One learning item (D-117): its kind as an icon, the title, `새 항목 · 과제 · 과목` and the exact deadline below,
 * and on the right how much time is left. Only a deadline within about two days takes the warning color, and its
 * words say so too. Completion is the section's job, so rows no longer repeat 미완료.
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
) {
    val due = if (completed) null else item.dueAt?.let { lmsDueLabel(it, now) }
    val changeColor = MaterialTheme.colorScheme.primary
    val meta = buildAnnotatedString {
        when (item.changeState) {
            LmsChangeState.NEW -> "새 항목"
            LmsChangeState.UPDATED -> "변경됨"
            LmsChangeState.NONE -> null
        }?.let { change ->
            withStyle(SpanStyle(color = changeColor, fontWeight = FontWeight.Bold)) { append(change) }
            append(" · ")
        }
        append(kindLabel(item.kind))
        if (showCourseName) append(" · ${item.courseName}")
    }
    SegmentedListItem(
        onClick = { onOpenItem(item) },
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier = Modifier.semantics { onClick(label = "열기") { onOpenItem(item); true } },
        leadingContent = { DimaIconBadge(lmsKindIcon(item.kind)) },
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
                Text(meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                lmsWhenLine(item)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        colors = dimaSegmentedListColors(),
    ) {
        Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

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
