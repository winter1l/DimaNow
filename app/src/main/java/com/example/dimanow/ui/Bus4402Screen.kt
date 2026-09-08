package com.example.dimanow.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dimanow.transit.Bus4402Schedule
import com.example.dimanow.transit.Bus4402ServiceCalendar
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Composable
internal fun Bus4402ScheduleContent(
    now: ZonedDateTime,
    nearbyStopNumber: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val schedule = Bus4402Schedule.official
    val serviceType = remember(now.toLocalDate()) { Bus4402ServiceCalendar.serviceType(now.toLocalDate()) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
            val departures = remember(serviceType, stop.stopNumber) {
                schedule.departures(serviceType, stop.stopNumber)
            }
            val upcoming = departures.filterNot { it.time.isBefore(now.toLocalTime()) }.take(2)
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (nearbyStopNumber == stop.stopNumber) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(stop.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                if (stop.originOffsetMinutes == 0) "정류장 ${stop.stopNumber}"
                                else "정류장 ${stop.stopNumber} · 공식 기점 +1분 예정",
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            upcoming.forEachIndexed { index, departure ->
                                val target = now.toLocalDate().atTime(departure.time).atZone(now.zone)
                                val millis = Duration.between(now, target).toMillis().coerceAtLeast(0)
                                val minutes = (millis + 59_999L) / 60_000L
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Text(
                                        text = if (minutes == 0L) "강남행 · 곧 출발" else "강남행 · ${minutes}분 후",
                                        color = if (index == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    }

                    Text("전체 시간표", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(departures, key = { _, item -> item.time.toSecondOfDay() }) { index, departure ->
                            val label = buildString {
                                append(departure.time.format(TIME))
                                when (index) {
                                    0 -> append(" (첫차)")
                                    departures.lastIndex -> append(" (막차)")
                                }
                                if (departure.estimated) append(" · 예정")
                            }
                            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Text(
                                    label,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (index == 0 || index == departures.lastIndex) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
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
