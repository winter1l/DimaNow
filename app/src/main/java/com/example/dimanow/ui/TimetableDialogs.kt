package com.example.dimanow.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.dimanow.theme.DimaShapes
import com.example.dimanow.ui.motion.DimaMotion
import com.example.dimanow.ui.motion.dimaCollapseOut
import com.example.dimanow.ui.motion.dimaContentReveal
import com.example.dimanow.ui.motion.dimaExpandIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.DisplayVocabulary
import com.example.dimanow.domain.Course
import com.example.dimanow.domain.GuidancePause
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
internal fun CourseDeleteConfirmationDialog(
    course: Course,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        icon = { DimaDialogIcon(Icons.Default.DeleteOutline, DialogTone.DANGER) },
        onDismissRequest = onDismiss,
        title = { Text("수업을 삭제할까요?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(course.name, style = MaterialTheme.typography.titleSmall)
                Text("${koreanWeekdayLabel(course.weekday)} · ${course.start.format(TIME)}–${course.end.format(TIME)}")
            }
        },
        confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = onConfirm) { Text("삭제", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
internal fun PauseDurationDialog(
    today: LocalDate,
    termEnd: LocalDate,
    onSelection: (GuidancePause) -> Unit,
    onRangeRequested: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        icon = { DimaDialogIcon(Icons.Default.EventBusy, DialogTone.INFO) },
        onDismissRequest = onDismiss,
        title = { Text("언제까지 휴강할까요?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(shapes = ButtonDefaults.shapes(), 
                    onClick = { onSelection(GuidancePause(today, today)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("오늘만") }
                FilledTonalButton(shapes = ButtonDefaults.shapes(), 
                    onClick = onRangeRequested,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("기간 지정") }
                FilledTonalButton(shapes = ButtonDefaults.shapes(), 
                    onClick = { onSelection(GuidancePause(today, termEnd)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("학기 종료일까지") }
                FilledTonalButton(shapes = ButtonDefaults.shapes(), 
                    onClick = { onSelection(GuidancePause.untilDisabled(today)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("휴강을 해제할 때까지") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
internal fun GuidancePauseSetting(
    pause: GuidancePause?,
    today: LocalDate,
    onConfigure: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("휴강 모드", style = MaterialTheme.typography.titleSmall)
        // D-137: the status cross-fades in place, without a size animation, so a label that wraps differently changes
        // the card's height once and the timetable items below do not chase a growing card.
        Crossfade(
            targetState = pause?.let { pauseLabel(it, today) } ?: "수업 안내 켜짐",
            animationSpec = DimaMotion.effectsDefault(),
            label = "guidance_pause_status",
        ) { status ->
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row {
            FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = onConfigure) {
                Text(if (pause == null) "설정" else "변경")
            }
            // D-137: 해제 widens in from beside the button and narrows away with its gap, instead of popping.
            AnimatedVisibility(
                visible = pause != null,
                enter = fadeIn(DimaMotion.effectsDefault()) + expandHorizontally(DimaMotion.spatialFast(), expandFrom = Alignment.Start),
                exit = fadeOut(DimaMotion.effectsFast()) + shrinkHorizontally(DimaMotion.spatialFast(), shrinkTowards = Alignment.Start),
                label = "guidance_pause_clear",
            ) {
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = onClear,
                    modifier = Modifier.padding(start = 8.dp).testTag("clear_guidance_pause"),
                ) {
                    Text("해제")
                }
            }
        }
    }
}

private fun pauseLabel(pause: GuidancePause, today: LocalDate): String = when {
    pause.isUntilDisabled -> "휴강을 해제할 때까지 휴강"
    pause.startDate == pause.endDateInclusive -> "${pause.endDateInclusive.monthValue}월 ${pause.endDateInclusive.dayOfMonth}일 휴강"
    today.isBefore(pause.startDate) -> "${pause.startDate.monthValue}월 ${pause.startDate.dayOfMonth}일부터 ${pause.endDateInclusive.monthValue}월 ${pause.endDateInclusive.dayOfMonth}일까지 휴강"
    else -> "${pause.endDateInclusive.monthValue}월 ${pause.endDateInclusive.dayOfMonth}일까지 휴강"
}

/**
 * Adding or editing a course is a full-screen form (D-102, M3 full-screen dialog for multi-field tasks):
 * close on the leading edge, 저장 as the one action, and the fields in the order people read a timetable —
 * name, days, time, then room, building and professor. A new course may take several weekdays at once and
 * becomes one course per day; moving the start keeps the class length. Closing with unsaved input asks first.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CourseEditorDialog(initial: Course?, onDismiss: () -> Unit, onSave: (List<Course>) -> Unit) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var room by remember(initial) { mutableStateOf(initial?.room.orEmpty()) }
    var professor by remember(initial) { mutableStateOf(initial?.professor.orEmpty()) }
    var days by remember(initial) { mutableStateOf(setOfNotNull(initial?.weekday)) }
    // D-099: 수업 건물에 따라 셔틀 안내 방향이 달라진다 (본관 / 엔터관)
    var zone by remember(initial) { mutableStateOf(initial?.zone?.takeIf { it == CampusZoneId.YEIN } ?: CampusZoneId.MAIN) }
    var start by remember(initial) { mutableStateOf(initial?.start ?: LocalTime.of(10, 0)) }
    var end by remember(initial) { mutableStateOf(initial?.end ?: LocalTime.of(11, 0)) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var nameTouched by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val adding = initial == null
    val valid = name.isNotBlank() && days.isNotEmpty() && end.isAfter(start)
    val edited = name != initial?.name.orEmpty() || room != initial?.room.orEmpty() ||
        professor != initial?.professor.orEmpty() || days != setOfNotNull(initial?.weekday) ||
        start != (initial?.start ?: LocalTime.of(10, 0)) || end != (initial?.end ?: LocalTime.of(11, 0)) ||
        zone != (initial?.zone?.takeIf { it == CampusZoneId.YEIN } ?: CampusZoneId.MAIN)
    val close = { if (edited) confirmDiscard = true else onDismiss() }
    val save = {
        onSave(
            days.sorted().map { day ->
                Course(
                    weekday = day, start = start, end = end,
                    name = name.trim(), room = room.trim(), professor = professor.trim(), zone = zone, id = initial?.id ?: 0,
                    isOnline = initial?.isOnline ?: false,
                )
            },
        )
    }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // The form draws behind the status and navigation bars like a screen; their icons follow the surface.
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        val lightBars = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        SideEffect {
            dialogWindow?.let { window ->
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = lightBars
                    isAppearanceLightNavigationBars = lightBars
                }
            }
        }
        Scaffold(
            modifier = Modifier.fillMaxSize().imePadding().testTag("course_editor"),
            topBar = {
                TopAppBar(
                    title = { Text(if (adding) "수업 추가" else "수업 수정") },
                    navigationIcon = {
                        IconButton(shapes = IconButtonDefaults.shapes(), onClick = close, modifier = Modifier.testTag("course_editor_close")) {
                            Icon(Icons.Default.Close, contentDescription = "닫기")
                        }
                    },
                    actions = {
                        TextButton(shapes = ButtonDefaults.shapes(), onClick = save, enabled = valid, modifier = Modifier.testTag("course_editor_save")) { Text("저장") }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameTouched = true },
                    label = { Text("수업명") },
                    singleLine = true,
                    isError = nameTouched && name.isBlank(),
                    supportingText = { if (nameTouched && name.isBlank()) Text("수업명을 적어 주세요") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )

                EditorSection(if (adding) "요일 · 여러 요일을 고를 수 있어요" else "요일") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DayOfWeek.entries.forEach { option ->
                            WeekdayChip(
                                label = koreanWeekdayLabel(option).removeSuffix("요일"),
                                description = koreanWeekdayLabel(option),
                                selected = option in days,
                                onClick = {
                                    // 수정은 한 수업이라 요일 하나만, 새 수업은 요일마다 하나씩 만든다
                                    val next = when {
                                        !adding -> setOf(option)
                                        option in days -> days - option
                                        else -> days + option
                                    }
                                    // D-121/D-137: the day cells' segment tick, only when the choice changes.
                                    if (next != days) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    days = next
                                },
                            )
                        }
                    }
                }

                EditorSection("시간") {
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = { showStartPicker = true }, modifier = Modifier.weight(1f)) {
                                Text("시작 ${start.format(TIME)}")
                            }
                            FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = { showEndPicker = true }, modifier = Modifier.weight(1f)) {
                                Text("종료 ${end.format(TIME)}")
                            }
                        }
                        // D-137: the message unfolds under the buttons with its gap, instead of the form jumping.
                        AnimatedVisibility(visible = !end.isAfter(start), enter = dimaExpandIn(), exit = dimaCollapseOut(), label = "course_time_error") {
                            Text(
                                "종료 시간을 시작 시간보다 늦게 설정해 주세요",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("강의실") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )

                EditorSection("수업 건물 · 셔틀 안내 방향을 정해요") {
                    // D-137: one of two buildings is an exclusive choice, so it is the connected button group (D-103):
                    // the chosen button morphs to its checked shape and the change ticks under the finger.
                    DimaConnectedChoice(
                        options = listOf(CampusZoneId.MAIN, CampusZoneId.YEIN).map { it to DisplayVocabulary.originName(it) },
                        selected = zone,
                        onSelect = { zone = it },
                        itemTag = { "course_zone_${it.name}" },
                    )
                }

                OutlinedTextField(
                    value = professor,
                    onValueChange = { professor = it },
                    label = { Text("담당 교수 (선택)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (valid) save() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showStartPicker) {
        CourseTimePickerDialog(
            title = "시작 시간", initial = start, onDismiss = { showStartPicker = false },
            onConfirm = { picked ->
                end = shiftedEnd(start, end, picked)
                start = picked
                showStartPicker = false
            },
        )
    }
    if (showEndPicker) {
        CourseTimePickerDialog(title = "종료 시간", initial = end, onDismiss = { showEndPicker = false }, onConfirm = { end = it; showEndPicker = false })
    }
    if (confirmDiscard) {
        AlertDialog(
            icon = { DimaDialogIcon(Icons.Default.DeleteOutline, DialogTone.DANGER) },
            onDismissRequest = { confirmDiscard = false },
            title = { Text("작성한 내용을 버릴까요?") },
            text = { Text("저장하지 않은 수업 정보는 사라져요.") },
            confirmButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmDiscard = false; onDismiss() }, modifier = Modifier.testTag("course_editor_discard")) {
                    Text("버리기", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmDiscard = false }) { Text("계속 작성") } },
        )
    }
}

/**
 * One weekday in the course form (D-137, the app's chip motion as in the day cells): a chosen day rounds to a pill on
 * the theme's fast spatial spring and tightens under the finger, and its fill, label and outline change on the fast
 * effects spring instead of switching in one frame. The outline is drawn in both states and fades, so it does not
 * vanish while the fill is still arriving.
 */
@Composable
private fun WeekdayChip(label: String, description: String, selected: Boolean, onClick: () -> Unit) {
    val defaults = FilterChipDefaults.filterChipColors()
    val container by animateColorAsState(
        if (selected) defaults.selectedContainerColor else defaults.containerColor,
        DimaMotion.effectsFast(),
        label = "weekday_chip_container",
    )
    val ink by animateColorAsState(
        if (selected) defaults.selectedLabelColor else defaults.labelColor,
        DimaMotion.effectsFast(),
        label = "weekday_chip_label",
    )
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val outline by animateColorAsState(
        if (selected) outlineColor.copy(alpha = 0f) else outlineColor,
        DimaMotion.effectsFast(),
        label = "weekday_chip_outline",
    )
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shapes = FilterChipDefaults.shapes(
            shape = FilterChipDefaults.shape,
            selectedShape = CircleShape,
            pressedShape = DimaShapes.Small,
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = container,
            labelColor = ink,
            selectedContainerColor = container,
            selectedLabelColor = ink,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = outline,
            selectedBorderColor = outline,
            selectedBorderWidth = 1.dp,
        ),
        modifier = Modifier.semantics { contentDescription = description },
    )
}

/** A labelled group of controls inside the course form. */
@Composable
private fun EditorSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

/**
 * Moving the start keeps the class length, as calendar apps do; a length that would cross midnight or a
 * previously invalid range falls back to one hour, clamped to the end of the day.
 */
internal fun shiftedEnd(oldStart: LocalTime, oldEnd: LocalTime, newStart: LocalTime): LocalTime {
    val length = java.time.Duration.between(oldStart, oldEnd).takeIf { !it.isNegative && !it.isZero } ?: java.time.Duration.ofHours(1)
    val lastMinute = LocalTime.of(23, 59)
    val minutesLeft = java.time.Duration.between(newStart, lastMinute)
    return if (length > minutesLeft) lastMinute else newStart.plus(length)
}

/**
 * M3 time picker dialog (D-102): the dial or typed entry, switched by the dialog's own toggle, and the
 * phone's 12/24-hour setting rather than a fixed 24-hour dial.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseTimePickerDialog(title: String, initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
    )
    var displayMode by remember { mutableStateOf(TimePickerDisplayMode.Picker) }
    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("확인") } },
        dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text("취소") } },
        modeToggleButton = {
            TimePickerDialogDefaults.DisplayModeToggle(
                onDisplayModeChange = {
                    displayMode = if (displayMode == TimePickerDisplayMode.Picker) TimePickerDisplayMode.Input else TimePickerDisplayMode.Picker
                },
                displayMode = displayMode,
            )
        },
    ) {
        // D-137: switching between the dial and typed entry cross-fades while the dialog grows or shrinks to the new
        // entry, instead of jumping to its new size. Both share one state, so the chosen time carries over.
        AnimatedContent(
            targetState = displayMode,
            transitionSpec = { dimaContentReveal() },
            contentAlignment = Alignment.TopCenter,
            label = "course_time_entry",
        ) { mode ->
            if (mode == TimePickerDisplayMode.Picker) TimePicker(state = state) else TimeInput(state = state)
        }
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
