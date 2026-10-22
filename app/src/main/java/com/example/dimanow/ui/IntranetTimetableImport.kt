package com.example.dimanow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.dimanow.data.CampusDataRepository
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.Course
import com.example.dimanow.domain.DisplayVocabulary
import com.example.dimanow.school.INTRANET_TIMETABLE_SCRIPT
import com.example.dimanow.school.INTRANET_TIMETABLE_URL
import com.example.dimanow.school.ImportedCourse
import com.example.dimanow.school.LocalSchoolServiceBridge
import com.example.dimanow.school.SchoolService
import com.example.dimanow.school.SchoolServiceBridge
import com.example.dimanow.school.SchoolServiceResult
import com.example.dimanow.school.parseIntranetTimetable
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Timetable import from the intranet (D-107): read, preview with each course's building, then replace. */
@Stable
internal class IntranetTimetableImport(
    private val bridge: SchoolServiceBridge,
    private val repository: CampusDataRepository,
    private val scope: CoroutineScope,
    private val onMessage: (String) -> Unit,
) {
    var loading by mutableStateOf(false)
        private set
    var preview by mutableStateOf<List<ImportedCourse>?>(null)
        private set
    val zones = mutableStateListOf<CampusZoneId>()

    fun start() {
        if (loading) return
        loading = true
        scope.launch {
            when (val result = bridge.read(SchoolService.INTRANET, INTRANET_TIMETABLE_URL, INTRANET_TIMETABLE_SCRIPT, "시간표")) {
                is SchoolServiceResult.Success -> runCatching { parseIntranetTimetable(result.json) }
                    .onSuccess { courses ->
                        if (courses.isEmpty()) {
                            onMessage("인트라넷 시간표에 수업이 없어요")
                        } else {
                            zones.clear()
                            zones.addAll(courses.map { it.suggestedZone })
                            preview = courses
                        }
                    }
                    .onFailure { onMessage("인트라넷 시간표를 읽지 못했어요") }
                is SchoolServiceResult.Failure -> onMessage(result.message)
                SchoolServiceResult.Cancelled -> Unit
            }
            loading = false
        }
    }

    fun dismiss() {
        preview = null
    }

    /** Replaces [existing] with the previewed courses in the chosen buildings. */
    fun confirm(existing: List<Course>) {
        val courses = preview ?: return
        val chosen = courses.mapIndexed { index, course -> course.toCourse(zones.getOrElse(index) { course.suggestedZone }) }
        preview = null
        scope.launch {
            existing.forEach { repository.deleteCourse(it.id) }
            chosen.forEach { repository.saveCourse(it) }
            onMessage("인트라넷에서 수업 ${chosen.size}개를 가져왔어요")
        }
    }
}

/** Null where the app shell has no school-service bridge (previews, screen tests). */
@Composable
internal fun rememberIntranetTimetableImport(repository: CampusDataRepository, onMessage: (String) -> Unit): IntranetTimetableImport? {
    val bridge = LocalSchoolServiceBridge.current ?: return null
    val scope = rememberCoroutineScope()
    return remember(bridge, repository) { IntranetTimetableImport(bridge, repository, scope, onMessage) }
}

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

@Composable
internal fun IntranetTimetablePreviewDialog(state: IntranetTimetableImport, existing: List<Course>) {
    val courses = state.preview ?: return
    AlertDialog(
        onDismissRequest = state::dismiss,
        title = { Text("인트라넷 시간표") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).testTag("intranet_timetable_preview"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "수업 ${courses.size}개를 찾았어요. 셔틀 안내에 쓸 건물을 확인해 주세요. 1교시는 9시, 한 교시는 60분으로 넣어요.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                courses.forEachIndexed { index, course ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "${koreanWeekdayLabel(course.weekday).take(1)} ${course.start.format(CLOCK)}–${course.end.format(CLOCK)} · ${course.name}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        val details = listOf(course.room, course.professor).filter { it.isNotBlank() }.joinToString(" · ")
                        if (details.isNotEmpty()) {
                            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(CampusZoneId.MAIN, CampusZoneId.YEIN).forEach { zone ->
                                FilterChip(
                                    selected = state.zones.getOrNull(index) == zone,
                                    onClick = { if (index < state.zones.size) state.zones[index] = zone },
                                    label = { Text(DisplayVocabulary.originName(zone)) },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                )
                            }
                        }
                    }
                }
                if (existing.isNotEmpty()) {
                    Text(
                        "지금 시간표의 수업 ${existing.size}개는 지우고 가져온 수업으로 바꿔요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                shapes = ButtonDefaults.shapes(),
                onClick = { state.confirm(existing) },
                modifier = Modifier.testTag("intranet_timetable_confirm"),
            ) { Text(if (existing.isEmpty()) "가져오기" else "바꾸기") }
        },
        dismissButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = state::dismiss) { Text("취소") }
        },
    )
}
