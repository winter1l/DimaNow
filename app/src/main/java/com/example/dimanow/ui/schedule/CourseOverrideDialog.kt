package com.example.dimanow.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.dimanow.domain.Course
import com.example.dimanow.domain.CourseOverride
import com.example.dimanow.domain.CourseOverrideKind
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CourseOverrideDialog(
    course: Course,
    today: LocalDate,
    termStart: LocalDate,
    termEnd: LocalDate,
    onDismiss: () -> Unit,
    onSave: (CourseOverride) -> Unit,
) {
    val initialDate = remember(course, today, termStart, termEnd) {
        maxOf(today, termStart).with(TemporalAdjusters.nextOrSame(course.weekday))
            .takeIf { !it.isAfter(termEnd) }
    }
    var date by remember(course, initialDate) { mutableStateOf(initialDate) }
    var kind by remember(course) { mutableStateOf(CourseOverrideKind.CANCELLED) }
    var startText by remember(course) { mutableStateOf(course.start.format(OVERRIDE_TIME)) }
    var endText by remember(course) { mutableStateOf(course.end.format(OVERRIDE_TIME)) }
    var room by remember(course) { mutableStateOf(course.room) }
    var pickingDate by remember { mutableStateOf(false) }
    val start = runCatching { LocalTime.parse(startText.trim(), OVERRIDE_TIME) }.getOrNull()
    val end = runCatching { LocalTime.parse(endText.trim(), OVERRIDE_TIME) }.getOrNull()
    val dateValid = date?.let {
        !it.isBefore(termStart) && !it.isAfter(termEnd) && it.dayOfWeek == course.weekday
    } == true
    val timeValid = start != null && end != null && end.isAfter(start)
    val changed = kind == CourseOverrideKind.CHANGED
    val valid = dateValid && (!changed || (timeValid && room.isNotBlank()))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("수업 한 번만 변경") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(course.name, style = MaterialTheme.typography.titleSmall)
                Text("선택한 날짜의 수업에만 적용돼요", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(date?.format(OVERRIDE_DATE) ?: "수업 날짜 선택")
                }
                if (!dateValid) {
                    Text("학기 안의 해당 수업 요일을 선택해 주세요", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                CourseOverrideKind.entries.forEach { option ->
                    Row(
                        modifier = Modifier.fillMaxWidth().selectable(
                            selected = kind == option,
                            onClick = { kind = option },
                            role = Role.RadioButton,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = kind == option, onClick = null)
                        Text(overrideKindLabel(option))
                    }
                }
                if (changed) {
                    OutlinedTextField(
                        value = startText,
                        onValueChange = { startText = it },
                        label = { Text("시작 시간 (HH:mm)") },
                        singleLine = true,
                        isError = start == null,
                    )
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { endText = it },
                        label = { Text("종료 시간 (HH:mm)") },
                        singleLine = true,
                        isError = !timeValid,
                    )
                    if (!timeValid) Text("종료 시간은 시작 시간보다 늦어야 해요", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = room,
                        onValueChange = { room = it },
                        label = { Text("강의실") },
                        singleLine = true,
                        isError = room.isBlank(),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    onSave(
                        CourseOverride(
                            courseId = course.id,
                            date = requireNotNull(date),
                            kind = kind,
                            start = if (changed) start else null,
                            end = if (changed) end else null,
                            room = if (changed) room.trim() else null,
                        ),
                    )
                },
            ) { Text("이 날짜에 적용") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )

    if (pickingDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            initialDisplayedMonthMillis = (date ?: termStart).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val day = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return !day.isBefore(termStart) && !day.isAfter(termEnd) && day.dayOfWeek == course.weekday
                }
                override fun isSelectableYear(year: Int): Boolean = year in termStart.year..termEnd.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedDateMillis != null,
                    onClick = {
                        date = pickerState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        pickingDate = false
                    },
                ) { Text("선택") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("취소") } },
        ) { DatePicker(state = pickerState) }
    }
}

internal fun overrideKindLabel(kind: CourseOverrideKind): String = when (kind) {
    CourseOverrideKind.CANCELLED -> "휴강"
    CourseOverrideKind.ONLINE -> "비대면"
    CourseOverrideKind.CHANGED -> "시간·강의실 변경"
}

private val OVERRIDE_TIME = DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(java.time.format.ResolverStyle.STRICT)
private val OVERRIDE_DATE = DateTimeFormatter.ofPattern("M월 d일 (E)", java.util.Locale.KOREAN)
