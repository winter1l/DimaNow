package com.example.dimanow.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.dimanow.theme.DimaShapes
import com.example.dimanow.theme.emphasized
import com.example.dimanow.transit.Bus4402Departure
import com.example.dimanow.transit.Bus4402Schedule
import com.example.dimanow.transit.Bus4402ServiceCalendar
import com.example.dimanow.transit.Bus4402Stop
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 4402 departures in the campus shuttle's grammar (D-094(5)): countdown-then-clock capsules for the
 * next two departures (minutes only within an hour, D-069) and the always-visible horizontal full
 * timetable (D-086) that opens near the next departure (D-068). Downstream-stop times are the
 * official origin +1 minute and are marked `예정` wherever a time is shown (D-061).
 */
@Composable
internal fun Bus4402ScheduleContent(
    now: ZonedDateTime,
    nearbyStopNumber: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val schedule = Bus4402Schedule.official
    val serviceType = remember(now.toLocalDate()) { Bus4402ServiceCalendar.serviceType(now.toLocalDate()) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("4402 강남행", style = MaterialTheme.typography.titleLarge)
            Text(
                "오늘 · ${serviceType.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        schedule.stops.forEach { stop ->
            val departures = remember(serviceType, stop.stopNumber) {
                schedule.departures(serviceType, stop.stopNumber)
            }
            Bus4402StopCard(
                stop = stop,
                departures = departures,
                now = now,
                isNearby = nearbyStopNumber == stop.stopNumber,
                timetableKey = "${now.toLocalDate()}|$serviceType|${stop.stopNumber}",
            )
        }

        // D-016: 원문 링크는 시간표 뒤, 기준 정보와 함께 둔다
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${schedule.effectiveDate.format(DATE)} 기준 · 대원고속",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, schedule.sourceUrl.toUri()))
                    } catch (_: ActivityNotFoundException) {
                        Unit
                    }
                },
                modifier = Modifier.testTag("bus4402_source"),
            ) {
                Text("공식 시간표")
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing).size(ButtonDefaults.IconSize),
                )
            }
        }
    }
}

@Composable
private fun Bus4402StopCard(
    stop: Bus4402Stop,
    departures: List<Bus4402Departure>,
    now: ZonedDateTime,
    isNearby: Boolean,
    timetableKey: String,
) {
    val nowTime = now.toLocalTime()
    val firstUpcomingIndex = departures.indexOfFirst { !it.time.isBefore(nowTime) }
    val upcoming = if (firstUpcomingIndex < 0) emptyList() else departures.drop(firstUpcomingIndex).take(2)
    val estimated = departures.firstOrNull()?.estimated == true

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().testTag("bus4402_stop_${stop.stopNumber}"),
        shape = DimaShapes.Card,
        colors = CardDefaults.elevatedCardColors(
            // D-083: 가까운 정류장은 primaryContainer로 강조한다
            containerColor = if (isNearby) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 교내 셔틀 노선 카드와 같은 머리: 이름 + 현재 정류장 배지, 그 아래 첫차·막차와 정류장 정보
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stop.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isNearby) {
                        Surface(shape = DimaShapes.Badge, color = MaterialTheme.colorScheme.primary) {
                            Text(
                                "현재 정류장",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                Text(
                    if (stop.originOffsetMinutes == 0) "정류장 ${stop.stopNumber}"
                    else "정류장 ${stop.stopNumber} · 공식 기점 +${stop.originOffsetMinutes}분 예정",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (departures.isNotEmpty()) {
                    Surface(shape = DimaShapes.Badge, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Text(
                            text = "첫차 ${departures.first().time.format(TIME)} · 막차 ${departures.last().time.format(TIME)}" +
                                if (estimated) " · 예정" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            if (upcoming.isEmpty()) {
                Surface(
                    shape = DimaShapes.Tile,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "오늘 운행이 끝났어요",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "출발 시각",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    DepartureCapsuleRow(spacing = 8.dp) {
                        upcoming.forEachIndexed { index, departure ->
                            val departureIndex = firstUpcomingIndex + index
                            val target = now.toLocalDate().atTime(departure.time).atZone(now.zone)
                            val minutesLeft = (Duration.between(now, target).toMillis().coerceAtLeast(0) + 59_999L) / 60_000L
                            val isLastService = departureIndex == departures.lastIndex
                            val (container, content) = shuttleCapsuleColors(isNearest = index == 0, isLastService = isLastService)
                            DepartureCapsule(
                                label = departureCountdownLabel(
                                    minutesLeft,
                                    serviceMarkers(departureIndex == 0, isLastService),
                                ),
                                clock = departure.time.format(TIME) + if (departure.estimated) " · 예정" else "",
                                containerColor = container,
                                contentColor = content,
                                labelStyle = MaterialTheme.typography.titleSmall.emphasized(),
                                modifier = Modifier.testTag("bus4402_next_${stop.stopNumber}_$index"),
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "전체 시간표 · ${departures.size}회",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val chips = departures.mapIndexed { index, departure ->
                    val isPast = departure.time.isBefore(nowTime)
                    val isNext = index == firstUpcomingIndex
                    val isFirst = index == 0
                    val isLast = index == departures.lastIndex
                    TimetableChipSpec(
                        key = departure.time.toSecondOfDay(),
                        text = buildString {
                            append(departure.time.format(TIME))
                            when {
                                isFirst && isLast -> append(" (첫차·막차)")
                                isFirst -> append(" (첫차)")
                                isLast -> append(" (막차)")
                            }
                            if (departure.estimated) append(" · 예정")
                        },
                        description = timetableChipDescription(
                            clock = departure.time.format(TIME),
                            isPast = isPast,
                            isNext = isNext,
                            markers = serviceMarkers(isFirst, isLast) + listOfNotNull("예정".takeIf { departure.estimated }),
                        ),
                        tone = timetableChipTone(
                            isFirst = isFirst,
                            isLast = isLast,
                            isPast = isPast,
                            isNext = isNext,
                            isSecond = firstUpcomingIndex >= 0 && index == firstUpcomingIndex + 1,
                        ),
                    )
                }
                TimetableChipRow(
                    chips = chips,
                    initialIndex = if (firstUpcomingIndex >= 0) firstUpcomingIndex else departures.lastIndex,
                    stateKey = timetableKey,
                    modifier = Modifier.testTag("bus4402_times_${stop.stopNumber}"),
                )
            }
        }
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd")
