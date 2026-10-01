package com.example.dimanow.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.dimanow.domain.CampusZoneId
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
        onDismissRequest = onDismiss,
        title = { Text("수업을 삭제할까요?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(course.name, style = MaterialTheme.typography.titleSmall)
                Text("${koreanWeekdayLabel(course.weekday)} · ${course.start.format(TIME)}–${course.end.format(TIME)}")
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("삭제", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
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
        onDismissRequest = onDismiss,
        title = { Text("휴강 기간") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { onSelection(GuidancePause(today, today)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("오늘만") }
                FilledTonalButton(
                    onClick = onRangeRequested,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("기간 지정") }
                FilledTonalButton(
                    onClick = { onSelection(GuidancePause(today, termEnd)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("학기 종료일까지") }
                FilledTonalButton(
                    onClick = { onSelection(GuidancePause.untilDisabled(today)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("휴강을 해제할 때까지") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
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
        Text(
            text = pause?.let { pauseLabel(it, today) } ?: "수업 안내 켜짐",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onConfigure) {
                Text(if (pause == null) "설정" else "변경")
            }
            if (pause != null) {
                TextButton(onClick = onClear, modifier = Modifier.testTag("clear_guidance_pause")) {
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

@Composable
fun CourseEditorDialog(initial: Course?, onDismiss: () -> Unit, onSave: (Course) -> Unit) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var room by remember(initial) { mutableStateOf(initial?.room.orEmpty()) }
    var professor by remember(initial) { mutableStateOf(initial?.professor.orEmpty()) }
    var day by remember(initial) { mutableStateOf(initial?.weekday ?: DayOfWeek.MONDAY) }
    var start by remember(initial) { mutableStateOf(initial?.start ?: LocalTime.of(10, 0)) }
    var end by remember(initial) { mutableStateOf(initial?.end ?: LocalTime.of(11, 0)) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    val valid = name.isNotBlank() && end.isAfter(start)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "수업 추가" else "수업 수정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("수업명") })
                OutlinedTextField(value = room, onValueChange = { room = it }, label = { Text("강의실") })
                OutlinedTextField(value = professor, onValueChange = { professor = it }, label = { Text("담당 교수") })

                Text("요일 선택", style = MaterialTheme.typography.labelMedium)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DayOfWeek.entries.forEach { option ->
                        FilterChip(
                            selected = day == option,
                            onClick = { day = option },
                            label = { Text(koreanWeekdayLabel(option).removeSuffix("요일")) },
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { showStartPicker = true }, modifier = Modifier.weight(1f)) {
                        Text("시작 ${start.format(TIME)}")
                    }
                    FilledTonalButton(onClick = { showEndPicker = true }, modifier = Modifier.weight(1f)) {
                        Text("종료 ${end.format(TIME)}")
                    }
                }
                if (!end.isAfter(start)) {
                    Text("종료 시간을 시작 시간보다 늦게 설정해 주세요", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    onSave(
                        Course(
                            weekday = day, start = start, end = end,
                            name = name, room = room, professor = professor, zone = CampusZoneId.MAIN, id = initial?.id ?: 0,
                        ),
                    )
                },
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )

    if (showStartPicker) {
        CourseTimePickerDialog(title = "시작 시간", initial = start, onDismiss = { showStartPicker = false }, onConfirm = { start = it; showStartPicker = false })
    }
    if (showEndPicker) {
        CourseTimePickerDialog(title = "종료 시간", initial = end, onDismiss = { showEndPicker = false }, onConfirm = { end = it; showEndPicker = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseTimePickerDialog(title: String, initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            Button(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text("확인")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
