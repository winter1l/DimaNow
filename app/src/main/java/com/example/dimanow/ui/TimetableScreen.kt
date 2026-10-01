package com.example.dimanow.ui

import com.example.dimanow.ui.motion.expressiveBounceClick
import com.example.dimanow.ui.schedule.KoreanDateRangePickerHeadline
import com.example.dimanow.ui.schedule.KoreanLocale
import com.example.dimanow.ui.schedule.TermEditorDialog
import com.example.dimanow.ui.schedule.nextCourseOccurrence
import com.example.dimanow.theme.DimaShapes
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.activity.compose.BackHandler
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.dimanow.data.CampusDataRepository
import com.example.dimanow.domain.Course
import com.example.dimanow.domain.GuidancePause
import com.example.dimanow.domain.TermSchedule
import com.example.dimanow.time.MinuteTicker
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimetableScreen(
    repository: CampusDataRepository,
    schedule: TermSchedule,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(MinuteTicker.CAMPUS_ZONE),
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<Course?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showTermEditor by remember { mutableStateOf(false) }
    var showPauseChoice by remember { mutableStateOf(false) }
    var pausePickerMode by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<Course?>(null) }
    var changingOnce by remember { mutableStateOf<Course?>(null) }
    var changeError by remember { mutableStateOf<String?>(null) }

    BackHandler(
        enabled = changingOnce != null || showEditor || showTermEditor || showPauseChoice || pausePickerMode != null || pendingDelete != null,
    ) {
        when {
            changingOnce != null -> changingOnce = null
            pendingDelete != null -> pendingDelete = null
            pausePickerMode != null -> pausePickerMode = null
            showPauseChoice -> showPauseChoice = false
            showTermEditor -> showTermEditor = false
            showEditor -> showEditor = false
        }
    }

    val listState = rememberLazyListState()
    // 목록 맨 위에서는 "수업 추가"를 글자와 함께 펼치고, 내려 읽는 동안에는 아이콘 버튼으로 접는다 (D-094)
    val addExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    ScreenScaffold(
        title = "시간표",
        modifier = modifier,
        listState = listState,
        itemSpacing = DimaLayout.sectionGap,
        listTag = "timetable_list",
        snackbarHost = {
            SnackbarHost(snackbarHostState, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("수업 추가") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = { editing = null; showEditor = true },
                expanded = addExpanded,
                // 접힌 상태에서도 같은 이름으로 읽히도록 버튼 자체에 설명을 둔다
                modifier = Modifier
                    .testTag("add_course")
                    .semantics { contentDescription = "수업 추가" },
            )
        },
    ) {
        item(key = "term") {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = DimaShapes.Card,
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            onClick = { showTermEditor = true },
                            modifier = Modifier.testTag("edit_term"),
                            shape = DimaShapes.Badge,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Text(
                                    "학기 ${koreanDateLabel(schedule.termStart, today)} ~ ${koreanDateLabel(schedule.termEnd, today)}",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    GuidancePauseSetting(
                        pause = schedule.guidancePause,
                        today = today,
                        onConfigure = { showPauseChoice = true },
                        onClear = { scope.launch { repository.clearGuidancePause() } },
                    )
                }
            }
        }

        changeError?.let { error -> item(key = "change_error") { Text(error, color = MaterialTheme.colorScheme.error) } }
        val upcomingChanges = schedule.courseOverrides.filter { it.date >= today }
        if (upcomingChanges.isNotEmpty()) {
            item(key = "changes_header") { Text("한 번만 바꾼 수업", style = MaterialTheme.typography.titleMedium) }
            itemsIndexed(upcomingChanges, key = { index, change -> "change_${change.courseId}_${change.date}_$index" }) { _, change ->
                val course = schedule.courses.firstOrNull { it.id == change.courseId }
                OutlinedCard(Modifier.fillMaxWidth(), shape = DimaShapes.Card) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${koreanDateLabel(change.date, today)} · ${com.example.dimanow.ui.schedule.overrideKindLabel(change.kind)}")
                            Text(course?.name.orEmpty(), style = MaterialTheme.typography.titleSmall)
                            if (change.kind == com.example.dimanow.domain.CourseOverrideKind.CHANGED) {
                                Text("${(change.start ?: course?.start)?.format(TIME)}–${(change.end ?: course?.end)?.format(TIME)} · ${change.room ?: course?.room}")
                            }
                        }
                        TextButton(onClick = { scope.launch { repository.removeCourseOverride(change.courseId, change.date) } }) { Text("되돌리기") }
                    }
                }
            }
        }

        val grouped = schedule.courses
            .sortedWith(compareBy<Course> { it.weekday.value }.thenBy { it.start })
            .groupBy { it.weekday }

        if (grouped.isEmpty()) {
            item(key = "courses_empty") {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = DimaShapes.Card,
                ) {
                    Text(text = "등록된 수업이 없어요", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
                }
            }
        } else {
            grouped.forEach { (weekday, courses) ->
                item(key = "day_${weekday.name}") {
                    Text(
                        text = koreanWeekdayLabel(weekday),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )
                }
                // 저장 전 수업(과 기본 시간표)은 id가 0이라, id가 없으면 요일 안 순서로 키를 만든다
                itemsIndexed(courses, key = { index, course ->
                    if (course.id != 0L) "course_${course.id}" else "course_${weekday.name}_$index"
                }) { _, course ->
                    CourseSummaryCard(
                        course = course,
                        onEdit = { editing = course; showEditor = true },
                        onDelete = { pendingDelete = course },
                        onChangeOnce = { changingOnce = course },
                        nextOccurrence = nextCourseOccurrence(course, today, schedule.termStart, schedule.termEnd, schedule.noClassDates),
                    )
                }
            }
        }

        if (schedule.noClassDates.isNotEmpty()) item(key = "no_class_dates") {
            Text(
                "지정한 휴강일",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (schedule.noClassDates.isNotEmpty()) item(key = "no_class_date_list") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                schedule.noClassDates.sorted().forEach { date ->
                    Surface(modifier = Modifier.fillMaxWidth(), shape = DimaShapes.Tile, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = koreanDateLabel(date, today), style = MaterialTheme.typography.bodyMedium)
                            // 바로 지우고 스낵바의 되돌리기로 복구할 수 있게 한다 (D-094(14))
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        repository.removeNoClassDate(date)
                                        val result = snackbarHostState.showSnackbar(
                                            message = "휴강일을 삭제했어요",
                                            actionLabel = "되돌리기",
                                            duration = SnackbarDuration.Long,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) repository.addNoClassDate(date)
                                    }
                                },
                                modifier = Modifier
                                    .testTag("delete_no_class_${date}")
                                    .semantics { contentDescription = "${koreanDateLabel(date, today)} 휴강일 삭제" },
                            ) {
                                Text("삭제", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    changingOnce?.let { course ->
        com.example.dimanow.ui.schedule.CourseOverrideDialog(
            course, today, schedule.termStart, schedule.termEnd,
            skipDates = schedule.noClassDates,
            onDismiss = { changingOnce = null },
            onSave = { change -> scope.launch {
                try { repository.setCourseOverride(change); changingOnce = null; changeError = null }
                catch (error: IllegalArgumentException) {
                    // 저장소의 검증 문장은 무엇을 고치면 되는지 알려 주는 해요체 안내다
                    changeError = error.message ?: "수업 변경을 저장하지 못했어요. 날짜와 시간을 확인해 주세요"
                    changingOnce = null
                }
                catch (error: IllegalStateException) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    changeError = "수업 변경을 저장하지 못했어요. 시간표를 다시 열어 확인해 주세요"
                    changingOnce = null
                }
            } },
        )
    }
    if (showEditor) {
        CourseEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { course -> scope.launch { repository.saveCourse(course) }; showEditor = false },
        )
    }
    if (showTermEditor) {
        TermEditorDialog(
            start = schedule.termStart,
            end = schedule.termEnd,
            onDismiss = { showTermEditor = false },
            onSave = { start, end ->
                scope.launch { repository.setTerm(start, end) }
                showTermEditor = false
            },
        )
    }
    if (showPauseChoice) {
        PauseDurationDialog(
            today = today,
            termEnd = schedule.termEnd,
            onSelection = { pause ->
                scope.launch { repository.setGuidancePause(pause) }
                showPauseChoice = false
            },
            onRangeRequested = {
                showPauseChoice = false; pausePickerMode = "RANGE"
            },
            onDismiss = { showPauseChoice = false },
        )
    }
    if (pausePickerMode == "RANGE") KoreanLocale {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { pausePickerMode = null },
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
                    onClick = {
                        val start = Instant.ofEpochMilli(state.selectedStartDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                        val end = Instant.ofEpochMilli(state.selectedEndDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                        scope.launch { repository.setGuidancePause(GuidancePause(start, end)) }
                        pausePickerMode = null
                    },
                ) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { pausePickerMode = null }) { Text("취소") } },
        ) {
            DateRangePicker(
                state = state,
                modifier = Modifier.fillMaxSize(),
                headline = { KoreanDateRangePickerHeadline(state.selectedStartDateMillis, state.selectedEndDateMillis) },
            )
        }
    }
    pendingDelete?.let { course ->
        CourseDeleteConfirmationDialog(
            course = course,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                scope.launch { repository.deleteCourse(course.id) }
                pendingDelete = null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CourseSummaryCard(
    course: Course,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onChangeOnce: (() -> Unit)? = null,
    nextOccurrence: LocalDate? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .expressiveBounceClick { onEdit() },
        shape = DimaShapes.Card,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (onChangeOnce != null && nextOccurrence != null) 4.dp else 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FlowRow(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = DimaShapes.Badge,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "${course.start.format(TIME)} – ${course.end.format(TIME)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    Text(
                        text = course.room,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (course.professor.isNotBlank()) {
                    Text(
                        text = course.professor,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.testTag("course_menu_${course.id}")) {
                    Icon(Icons.Default.MoreVert, contentDescription = "${course.name} 더보기")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    onChangeOnce?.let { action ->
                        DropdownMenuItem(text = { Text("이번 수업만 변경") }, onClick = { menuExpanded = false; action() })
                    }
                    DropdownMenuItem(text = { Text("수정") }, onClick = { menuExpanded = false; onEdit() })
                    DropdownMenuItem(text = { Text("삭제") }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
        // 한 번만 바꾸기(휴강·비대면·시간 변경)는 더보기 메뉴에 숨기지 않고 다음 수업 날짜와 함께 보인다 (D-094(14))
        if (onChangeOnce != null && nextOccurrence != null) {
            TextButton(
                onClick = onChangeOnce,
                modifier = Modifier
                    .padding(start = 4.dp, bottom = 4.dp)
                    .testTag("change_once_${course.id}"),
            ) {
                Icon(Icons.Default.EditCalendar, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("이번 수업만 변경 · ${koreanDateLabel(nextOccurrence)}")
            }
        }
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
