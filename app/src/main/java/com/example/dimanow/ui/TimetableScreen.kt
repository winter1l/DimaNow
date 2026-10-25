package com.example.dimanow.ui

import androidx.compose.material3.IconButtonDefaults
import com.example.dimanow.theme.DimaTheme
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.activity.compose.BackHandler
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.example.dimanow.calendar.IcsExport
import com.example.dimanow.calendar.ServiceCalendarStore
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
    addCourseRequest: Long = 0L,
    onAddCourseRequestHandled: () -> Unit = {},
    /** D-110: the timetable opens from 전체 or Home as a page with a Back action. */
    onBack: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<Course?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    // D-102: 홈의 '수업 추가하기'는 탭을 옮긴 뒤 곧바로 추가 화면을 연다
    LaunchedEffect(addCourseRequest) {
        if (addCourseRequest != 0L) {
            editing = null
            showEditor = true
            onAddCourseRequestHandled()
        }
    }
    val timetableImport = rememberTimetableImport { message -> scope.launch { snackbarHostState.showSnackbar(message) } }
    // D-107: 인트라넷의 이번 학기 시간표를 미리 보고 한 번에 바꾼다
    val intranetImport = rememberIntranetTimetableImport(repository) { message -> scope.launch { snackbarHostState.showSnackbar(message) } }
    // D-111: 인트라넷에서 가져오기는 수업 추가 버튼(FAB 메뉴)과 빈 시간표 안내에 함께 둔다
    var addMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var showTermEditor by remember { mutableStateOf(false) }
    var showPauseChoice by remember { mutableStateOf(false) }
    var pausePickerMode by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<Course?>(null) }
    var changingOnce by remember { mutableStateOf<Course?>(null) }
    var changeError by remember { mutableStateOf<String?>(null) }
    // D-128(4): 시간표와 학사 일정을 .ics 파일로 공유한다 (캘린더에 직접 쓰지 않음, D-125(6))
    val context = LocalContext.current
    var showExport by remember { mutableStateOf(false) }
    val serviceCalendar by ServiceCalendarStore.calendar.collectAsState()
    val academicEvents = remember(serviceCalendar, today) { IcsExport.upcomingAcademicEvents(serviceCalendar, today) }
    val exportableCourseCount = remember(schedule) { IcsExport.exportableCourses(schedule).size }
    val canExport = exportableCourseCount > 0 || academicEvents.isNotEmpty()

    BackHandler(
        enabled = addMenuExpanded || changingOnce != null || showEditor || showTermEditor || showPauseChoice ||
            pausePickerMode != null || pendingDelete != null || showExport,
    ) {
        when {
            showExport -> showExport = false
            addMenuExpanded -> addMenuExpanded = false
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
    val termEnded = com.example.dimanow.calendar.AcademicTerms.hasEnded(schedule.termEnd, today)
    val noCourses = schedule.courses.isEmpty()
    val addCourseFab: @Composable () -> Unit = if (intranetImport != null) {
        {
            AddCourseFabMenu(
                expanded = addMenuExpanded,
                onExpandedChange = { addMenuExpanded = it },
                onAddByHand = { addMenuExpanded = false; editing = null; showEditor = true },
                onImportFromIntranet = { addMenuExpanded = false; intranetImport.start() },
                importing = intranetImport.loading,
            )
        }
    } else {
        {
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
        }
    }
    ScreenScaffold(
        title = "시간표",
        modifier = modifier,
        navigationIcon = {
            onBack?.let { back ->
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = back, modifier = Modifier.testTag("close_timetable")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                }
            }
        },
        // D-128(4): 내보낼 수업도 학사 일정도 없으면 버튼을 두지 않는다
        topAction = if (canExport) {
            {
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = { showExport = true }, modifier = Modifier.testTag("export_calendar")) {
                    Icon(Icons.Default.Share, contentDescription = "캘린더로 내보내기")
                }
            }
        } else {
            null
        },
        listState = listState,
        itemSpacing = DimaLayout.sectionGap,
        listTag = "timetable_list",
        snackbarHost = {
            SnackbarHost(snackbarHostState, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        },
        // D-102: 수업이 없을 때는 빈 화면 안내가 추가·불러오기 버튼을 함께 보여 주므로 FAB를 겹쳐 두지 않는다
        floatingActionButton = addCourseFab.takeUnless { noCourses },
    ) {
        item(key = "term") {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = DimaShapes.Card,
                colors = CardDefaults.elevatedCardColors(containerColor = DimaTheme.colors.card),
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
                            color = DimaTheme.colors.cardInner,
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

                    // 휴강 모드는 수업이 있어야 의미가 있으므로, 빈 시간표에서는 학기 기간만 보인다 (D-102)
                    if (!noCourses) {
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
        }
        // D-099: 학기가 끝났으면 새 학기 설정을 바로 안내한다
        if (termEnded) item(key = "term_guide") {
            val suggestion = remember(today) { com.example.dimanow.calendar.AcademicTerms.suggest(today) }
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().testTag("term_ended_card"),
                shape = DimaShapes.Card,
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("학기가 끝났어요", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "새 학기 기간을 정하고 수업을 다시 등록해 주세요. 추천 기간은 " +
                            "${koreanDateLabel(suggestion.start, today)} ~ ${koreanDateLabel(suggestion.end, today)}" +
                            (if (suggestion.published) "이에요." else "이에요 (학교 일정 발표 전 예상)."),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(shapes = ButtonDefaults.shapes(), 
                            onClick = { scope.launch { repository.setTerm(suggestion.start, suggestion.end) } },
                            modifier = Modifier.testTag("apply_suggested_term"),
                        ) { Text("추천 기간으로 바꾸기") }
                        TextButton(shapes = ButtonDefaults.shapes(), onClick = { showTermEditor = true }) { Text("직접 고르기") }
                    }
                }
            }
        }
        // D-102: 빈 시간표는 한 곳에서 채우는 두 방법(직접 추가, 다른 기기에서 저장한 파일)을 모두 보여 준다
        if (intranetImport?.loading == true) item(key = "intranet_import_loading") {
            LoadingLine("인트라넷에서 시간표를 불러오는 중이에요")
        }
        if (noCourses) item(key = "courses_empty") {
            if (intranetImport != null) {
                // D-111: the same two ways as the add button; a saved timetable file loads from Settings.
                EmptyState(
                    message = "아직 등록된 수업이 없어요",
                    supporting = "인트라넷 시간표를 가져오거나 직접 추가하면 수업 시작 전 알림과 셔틀 안내가 시작돼요.",
                    icon = Icons.Default.Schedule,
                    actionLabel = "인트라넷에서 가져오기",
                    actionIcon = Icons.Default.CloudDownload,
                    onAction = intranetImport::start,
                    actionEnabled = !intranetImport.loading,
                    actionTag = "intranet_import",
                    secondaryActionLabel = "직접 추가",
                    onSecondaryAction = { editing = null; showEditor = true },
                    secondaryActionTag = "add_course",
                    modifier = Modifier.testTag("timetable_empty_card"),
                )
            } else {
                EmptyState(
                    message = "아직 등록된 수업이 없어요",
                    supporting = "수업을 추가하면 수업 시작 전 알림과 셔틀 안내가 시작돼요.",
                    icon = Icons.Default.Schedule,
                    actionLabel = "수업 추가",
                    actionIcon = Icons.Default.Add,
                    onAction = { editing = null; showEditor = true },
                    actionTag = "add_course",
                    secondaryActionLabel = "시간표 파일 불러오기",
                    onSecondaryAction = timetableImport::open,
                    secondaryActionTag = "empty_import_timetable",
                    modifier = Modifier.testTag("timetable_empty_card"),
                )
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
                        TextButton(shapes = ButtonDefaults.shapes(), onClick = { scope.launch { repository.removeCourseOverride(change.courseId, change.date) } }) { Text("되돌리기") }
                    }
                }
            }
        }

        val grouped = schedule.courses
            .sortedWith(compareBy<Course> { it.weekday.value }.thenBy { it.start })
            .groupBy { it.weekday }

        if (grouped.isNotEmpty()) {
            grouped.forEach { (weekday, courses) ->
                item(key = "day_${weekday.name}") {
                    // 오늘 요일은 식단 화면과 같은 '오늘' 배지로 찾기 쉽게 한다 (D-102)
                    Row(
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = koreanWeekdayLabel(weekday),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (weekday == today.dayOfWeek) TodayBadge()
                    }
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
                    Surface(modifier = Modifier.fillMaxWidth(), shape = DimaShapes.Tile, color = DimaTheme.colors.card) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = koreanDateLabel(date, today), style = MaterialTheme.typography.bodyMedium)
                            // 바로 지우고 스낵바의 되돌리기로 복구할 수 있게 한다 (D-094(14))
                            TextButton(shapes = ButtonDefaults.shapes(), 
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
        val adding = editing == null
        CourseEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { courses ->
                showEditor = false
                scope.launch {
                    courses.forEach { repository.saveCourse(it) }
                    // 새 수업은 어디에 들어갔는지 짧게 알려 준다 (D-102)
                    if (adding) snackbarHostState.showSnackbar(
                        if (courses.size == 1) "${koreanWeekdayLabel(courses.single().weekday)}에 수업을 추가했어요"
                        else "${courses.joinToString("·") { koreanWeekdayLabel(it.weekday).removeSuffix("요일") }}요일에 수업을 추가했어요",
                    )
                }
            },
        )
    }
    TimetableImportConfirmation(timetableImport)
    intranetImport?.let { IntranetTimetablePreviewDialog(it, schedule.courses) }
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
                TextButton(shapes = ButtonDefaults.shapes(), 
                    enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
                    onClick = {
                        val start = Instant.ofEpochMilli(state.selectedStartDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                        val end = Instant.ofEpochMilli(state.selectedEndDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                        scope.launch { repository.setGuidancePause(GuidancePause(start, end)) }
                        pausePickerMode = null
                    },
                ) { Text("저장") }
            },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { pausePickerMode = null }) { Text("취소") } },
        ) {
            DateRangePicker(
                state = state,
                modifier = Modifier.fillMaxSize(),
                headline = { KoreanDateRangePickerHeadline(state.selectedStartDateMillis, state.selectedEndDateMillis) },
            )
        }
    }
    if (showExport) {
        CalendarExportDialog(
            courseCount = exportableCourseCount,
            academicEventCount = academicEvents.size,
            onDismiss = { showExport = false },
            onExport = { includeTimetable, includeAcademic ->
                showExport = false
                val ics = IcsExport.build(
                    schedule = schedule.takeIf { includeTimetable },
                    academicEvents = if (includeAcademic) academicEvents else emptyList(),
                    now = Instant.now(),
                )
                scope.launch {
                    if (!shareIcsFile(context, ics)) snackbarHostState.showSnackbar("캘린더 파일을 공유하지 못했어요. 잠시 후 다시 시도해 주세요")
                }
            },
        )
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
        colors = CardDefaults.elevatedCardColors(containerColor = DimaTheme.colors.card),
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
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = { menuExpanded = true }, modifier = Modifier.testTag("course_menu_${course.id}")) {
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
            TextButton(shapes = ButtonDefaults.shapes(), 
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

/**
 * 수업 추가 as the M3 Expressive FAB menu (D-111): the toggle button opens 직접 추가 and 인트라넷에서 가져오기 above
 * it and turns into a close button. Shown when the shell can read the intranet; elsewhere the plain extended FAB
 * opens the course form directly.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AddCourseFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAddByHand: () -> Unit,
    onImportFromIntranet: () -> Unit,
    importing: Boolean,
) {
    FloatingActionButtonMenu(
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = onExpandedChange,
                modifier = Modifier
                    .testTag("add_course_menu")
                    .semantics {
                        contentDescription = if (expanded) "수업 추가 메뉴 닫기" else "수업 추가"
                        stateDescription = if (expanded) "펼쳐짐" else "접힘"
                    },
            ) {
                Icon(
                    if (checkedProgress > 0.5f) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = null,
                    modifier = with(ToggleFloatingActionButtonDefaults) { Modifier.animateIcon({ checkedProgress }) },
                )
            }
        },
    ) {
        FloatingActionButtonMenuItem(
            onClick = onAddByHand,
            icon = { Icon(Icons.Default.Edit, contentDescription = null) },
            text = { Text("직접 추가") },
            modifier = Modifier.testTag("add_course"),
        )
        FloatingActionButtonMenuItem(
            onClick = { if (!importing) onImportFromIntranet() },
            icon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
            text = { Text(if (importing) "인트라넷에서 불러오는 중" else "인트라넷에서 가져오기") },
            modifier = Modifier.testTag("intranet_import"),
        )
    }
}
