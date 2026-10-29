package com.example.dimanow.ui

import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.dimanow.sync.ServiceCalendar
import com.example.dimanow.theme.DimaShapes
import com.example.dimanow.theme.DimaTheme
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Where [event] stands on [today]: running (`진행 중 · 12월 11일 (금)까지`, `오늘까지`) or how soon it starts. */
internal fun academicEventStatus(event: ServiceCalendar.AcademicEvent, today: LocalDate): String = when {
    today.isBefore(event.start) -> when (val days = ChronoUnit.DAYS.between(today, event.start)) {
        1L -> "내일부터"
        else -> "D-$days"
    }
    event.start == event.end -> "오늘"
    today == event.end -> "오늘까지"
    else -> "진행 중 · ${koreanDateLabel(event.end)}까지"
}

/** `12월 7일 (월) ~ 12월 11일 (금)`, or one date for a one-day event. */
internal fun academicEventDates(event: ServiceCalendar.AcademicEvent): String =
    if (event.start == event.end) koreanDateLabel(event.start) else "${koreanDateLabel(event.start)} ~ ${koreanDateLabel(event.end)}"

/**
 * Home's 학사 일정 card (D-107): the 학사일정 periods running today or starting within two weeks, each with its
 * dates and a status badge — tertiary while it runs, quieter while it is still ahead. It holds no action.
 */
@Composable
internal fun HomeAcademicAgendaCard(
    events: List<ServiceCalendar.AcademicEvent>,
    today: LocalDate,
    modifier: Modifier = Modifier,
    tag: String = "dashboard_academic",
    /** D-131(3): opens the signed-in intranet while a 성적 열람 period runs; null hides the button. */
    onOpenGrades: (() -> Unit)? = null,
) {
    HomeContainerCard(modifier = modifier.testTag(tag)) {
        HomeCardHeader(icon = Icons.Default.Event, title = "학사 일정", showChevron = false)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            events.forEach { event ->
                val running = !today.isBefore(event.start)
                val status = academicEventStatus(event, today)
                val dates = academicEventDates(event)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics(mergeDescendants = true) { contentDescription = "${event.name}, $dates, $status" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(event.name, style = MaterialTheme.typography.bodyLarge)
                        Text(dates, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (onOpenGrades != null && running && isGradeViewing(event)) {
                        TextButton(
                            shapes = ButtonDefaults.shapes(),
                            onClick = onOpenGrades,
                            modifier = Modifier.testTag("academic_open_grades"),
                        ) { Text("성적 보기") }
                    }
                    Surface(
                        shape = DimaShapes.Badge,
                        color = if (running) MaterialTheme.colorScheme.tertiaryContainer else DimaTheme.colors.cardInner,
                    ) {
                        Text(
                            status,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (running) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** A 학사 일정 row for viewing grades (`성적 열람`, `계절학기 성적 열람 및 성적 정정`…), D-131(3). */
internal fun isGradeViewing(event: ServiceCalendar.AcademicEvent): Boolean = "성적" in event.name && "열람" in event.name

/** The intranet's own front page, where its menu leads to the grades (the grade page's URL is not yet known). */
internal const val INTRANET_HOME_URL = "https://intranet.dima.ac.kr/default.aspx"

/**
 * D-131(3): opens the signed-in intranet with its menu kept, for the 성적 열람 period; without the school bridge
 * (previews) it falls back to the browser.
 */
@Composable
internal fun rememberGradesOpener(): () -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bridge = com.example.dimanow.school.LocalSchoolServiceBridge.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return remember(bridge, context) {
        {
            if (bridge == null) {
                openUrl(context, INTRANET_HOME_URL)
            } else {
                scope.launch {
                    val result = bridge.open(com.example.dimanow.school.SchoolService.INTRANET, INTRANET_HOME_URL, "성적 보기", fitsPage = false)
                    if (result is com.example.dimanow.school.SchoolServiceResult.Failure) {
                        android.widget.Toast.makeText(context, result.message, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

