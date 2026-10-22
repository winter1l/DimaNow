package com.example.dimanow.ui

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
internal fun HomeAcademicAgendaCard(events: List<ServiceCalendar.AcademicEvent>, today: LocalDate, modifier: Modifier = Modifier) {
    HomeContainerCard(modifier = modifier.testTag("dashboard_academic")) {
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
                    Surface(
                        shape = DimaShapes.Badge,
                        color = if (running) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
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
