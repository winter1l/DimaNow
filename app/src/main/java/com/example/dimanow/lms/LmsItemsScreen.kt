package com.example.dimanow.lms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.example.dimanow.theme.emphasized
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.dimanow.ui.EmptyState
import com.example.dimanow.ui.ErrorState
import com.example.dimanow.ui.ScreenScaffold
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

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
            IconButton(
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

/** 오늘 탭의 날짜 머리글과 전체 탭의 과목 머리글이 함께 쓰는 구분선 (D-058). */
@Composable
private fun LmsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
}

/** 완료한 학습을 접었다 펴는 머리글. 오늘·전체 두 모드가 같은 것을 쓴다 (D-058). */
@Composable
private fun LmsCompletedHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    TextButton(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().testTag("lms_completed_toggle"),
    ) {
        Text(
            "완료한 학습 · ${count}개",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(8.dp))
        Text(if (expanded) "접기" else "보기")
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
    // 오늘 모드에서도 선택한 필터를 그대로 적용한다 (이전에는 칩이 조용히 무시됐다)
    val agenda = remember(filtered, now) {
        LmsAgendaPlanner(Clock.fixed(now, SEOUL)).plan(filtered, now)
    }
    // 전체 모드는 과목 순서로 묶고 완료한 학습을 아래로 내린다 (D-058)
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
        // 새로고침은 목록을 당겨서 실행한다 (D-058)
        onRefresh = onRefresh,
        refreshing = syncing && !visibleEmpty,
        // 모드 전환과 필터는 목록을 아무리 내려도 항상 닿을 수 있어야 한다 (D-057).
        // 이전에는 목록의 첫 항목이라 스크롤과 함께 사라졌고, 필터가 세 줄을 차지했다.
        subHeader = {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(true to "오늘", false to "전체").forEachIndexed { index, (isToday, label) ->
                    SegmentedButton(
                        selected = todayMode == isToday,
                        onClick = { todayMode = isToday },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                        label = { Text(label) },
                        modifier = Modifier.weight(1f).testTag(if (isToday) "lms_mode_today" else "lms_mode_all"),
                    )
                }
            }
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
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp, strokeCap = StrokeCap.Round)
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
                            todayMode -> "오늘 확인할 학습이 없어요"
                            else -> "등록된 학습이 없어요"
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
            agenda.groups.forEach { group ->
                item(key = "agenda-${group.key}-${group.date}") {
                    if (group.key == LmsAgendaGroupKey.COMPLETED) {
                        LmsCompletedHeader(
                            count = group.items.size,
                            expanded = completedExpanded,
                            onToggle = { completedExpanded = !completedExpanded },
                        )
                    } else {
                        LmsSectionHeader(agendaGroupTitle(group))
                    }
                }
                if (group.key != LmsAgendaGroupKey.COMPLETED || completedExpanded) {
                    group.items.forEach { groupItem ->
                        item(key = "today-${groupItem.kind}-${groupItem.courseId}-${groupItem.id}") {
                            LmsItemCard(groupItem, onOpenItem)
                        }
                    }
                }
            }
        } else {
            // 전체 모드: 과목 순서대로 묶고, 완료한 학습은 오늘 탭과 똑같이 맨 아래에 접어 둔다 (D-058)
            coursePlan.groups.forEach { group ->
                item(key = "course-${group.courseId}") {
                    LmsSectionHeader("${group.courseName} · ${group.items.size}")
                }
                group.items.forEach { courseItem ->
                    item(key = "all-${courseItem.kind}-${courseItem.courseId}-${courseItem.id}") {
                        LmsItemCard(courseItem, onOpenItem, showCourseName = false)
                    }
                }
            }
            if (coursePlan.completed.isNotEmpty()) {
                item(key = "all-completed-header") {
                    LmsCompletedHeader(
                        count = coursePlan.completed.size,
                        expanded = completedExpanded,
                        onToggle = { completedExpanded = !completedExpanded },
                    )
                }
                if (completedExpanded) {
                    coursePlan.completed.forEach { completedItem ->
                        item(key = "all-done-${completedItem.kind}-${completedItem.courseId}-${completedItem.id}") {
                            LmsItemCard(completedItem, onOpenItem)
                        }
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

@Composable
private fun LmsItemCard(item: LmsItem, onOpenItem: (LmsItem) -> Unit, showCourseName: Boolean = true) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = { onOpenItem(item) }),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        headlineContent = { Text(item.title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LmsMetadataLabel(kindLabel(item.kind), prominent = false)
                if (showCourseName) Text(
                    item.courseName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                // 읽음/안읽음 배지는 제거했다 — 새 항목·완료 여부만 남긴다 (D-058)
            }
            item.dueAt?.let {
                Text("마감 ${LMS_TIME.format(it.atZone(SEOUL))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } ?: item.registeredAt?.let {
                Text("등록 ${LMS_TIME.format(it.atZone(SEOUL))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val badges = buildList {
                when (item.changeState) {
                    LmsChangeState.NEW -> add("새 항목")
                    LmsChangeState.UPDATED -> add("변경됨")
                    LmsChangeState.NONE -> Unit
                }
                when (item.completionState) {
                    LmsCompletionState.COMPLETE -> add(
                        if (item.kind == LmsItemKind.CONTENT) "수강 완료" else "완료",
                    )
                    LmsCompletionState.INCOMPLETE -> add(
                        if (item.kind == LmsItemKind.CONTENT) "미수강" else "미완료",
                    )
                    LmsCompletionState.NOT_TRACKED, LmsCompletionState.UNKNOWN -> Unit
                }
            }
            if (badges.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    badges.forEach { badge -> LmsMetadataLabel(badge, prominent = badge == "새 항목" || badge == "변경됨") }
                }
            }
        }
        },
    )
}

@Composable
private fun LmsMetadataLabel(label: String, prominent: Boolean) {
    Text(
        label,
        style = if (prominent) MaterialTheme.typography.labelMedium.emphasized() else MaterialTheme.typography.labelMedium,
        color = if (prominent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun agendaGroupTitle(group: LmsAgendaGroup): String = when (group.key) {
    LmsAgendaGroupKey.DATE -> if (group.title == "오늘") {
        "오늘"
    } else {
        group.date?.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)) ?: group.title
    }
    else -> group.title
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
