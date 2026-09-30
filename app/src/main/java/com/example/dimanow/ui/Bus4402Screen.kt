package com.example.dimanow.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dimanow.transit.Bus4402Schedule
import com.example.dimanow.transit.Bus4402ServiceCalendar
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun Bus4402ScheduleContent(
    now: ZonedDateTime,
    nearbyStopNumber: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val schedule = Bus4402Schedule.official
    val serviceType = remember(now.toLocalDate()) { Bus4402ServiceCalendar.serviceType(now.toLocalDate()) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("4402 강남행", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "오늘 · ${serviceType.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(schedule.sourceUrl)))
                    } catch (_: ActivityNotFoundException) {
                        Unit
                    }
                },
            ) { Text("공식 시간표") }
        }

        schedule.stops.forEach { stop ->
            var detailsExpanded by rememberSaveable(stop.stopNumber) { mutableStateOf(false) }
            var timetableExpanded by rememberSaveable(now.toLocalDate(), serviceType, stop.stopNumber) { mutableStateOf(false) }
            val departures = remember(serviceType, stop.stopNumber) {
                schedule.departures(serviceType, stop.stopNumber)
            }
            val upcoming = departures.filterNot { it.time.isBefore(now.toLocalTime()) }.take(2)
            val firstUpcomingIndex = departures.indexOfFirst { !it.time.isBefore(now.toLocalTime()) }
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (nearbyStopNumber == stop.stopNumber) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(stop.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                departures.firstOrNull()?.let { first ->
                                    "첫차 ${first.time.format(TIME)} · 막차 ${departures.last().time.format(TIME)}" +
                                        if (first.estimated) " · 예정" else ""
                                } ?: "강남행",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (nearbyStopNumber == stop.stopNumber) {
                            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary) {
                                Text(
                                    "현재 정류장",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }

                    if (upcoming.isEmpty()) {
                        Text("오늘 운행 종료", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            upcoming.forEachIndexed { index, departure ->
                                val target = now.toLocalDate().atTime(departure.time).atZone(now.zone)
                                val millis = Duration.between(now, target).toMillis().coerceAtLeast(0)
                                val minutes = (millis + 59_999L) / 60_000L
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Text(
                                        text = buildString {
                                            append(departure.time.format(TIME))
                                            when {
                                                minutes == 0L -> append(" · 곧 출발")
                                                minutes <= 60 -> append(" · ${minutes}분 후")
                                            }
                                            if (departure.estimated) append(" · 예정")
                                        },
                                        color = if (index == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = { timetableExpanded = !timetableExpanded },
                        modifier = Modifier.testTag("bus4402_times_toggle_${stop.stopNumber}")
                            .semantics { stateDescription = if (timetableExpanded) "펼쳐짐" else "접힘" },
                    ) {
                        Text(if (timetableExpanded) "전체 시간표 접기" else "전체 시간표 · ${departures.size}회")
                    }
                    if (timetableExpanded) FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        departures.forEachIndexed { index, departure ->
                            val isPast = departure.time.isBefore(now.toLocalTime())
                            val isNext = index == firstUpcomingIndex
                            val label = buildString {
                                if (isNext) append("다음 · ")
                                append(departure.time.format(TIME))
                                when (index) {
                                    0 -> append(" (첫차)")
                                    departures.lastIndex -> append(" (막차)")
                                }
                                if (departure.estimated) append(" · 예정")
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = when {
                                    isNext -> MaterialTheme.colorScheme.onPrimary
                                    isPast -> MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            ) {
                                Text(
                                    label,
                                    modifier = Modifier.heightIn(min = 32.dp).padding(horizontal = 8.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isNext || index == 0 || index == departures.lastIndex) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                    TextButton(onClick = { detailsExpanded = !detailsExpanded }) {
                        Text(if (detailsExpanded) "정류장 정보 접기" else "정류장 정보")
                    }
                    if (detailsExpanded) {
                        Text(
                            if (stop.originOffsetMinutes == 0) "정류장 ${stop.stopNumber}"
                            else "정류장 ${stop.stopNumber} · 공식 기점 +${stop.originOffsetMinutes}분 예정",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Text(
            "${schedule.effectiveDate.format(DATE)} 기준 · 대원고속",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd")
