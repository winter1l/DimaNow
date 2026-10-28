package com.example.dimanow.ui

import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.example.dimanow.ui.motion.DimaMotion
import com.example.dimanow.ui.motion.LocalReducedMotion
import com.example.dimanow.ui.motion.dimaPopIn
import com.example.dimanow.ui.motion.dimaPopOut
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
import com.example.dimanow.theme.DimaTheme
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
    resetSignal: Int = 0,
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
                resetSignal = resetSignal,
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
            TextButton(shapes = ButtonDefaults.shapes(), 
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
    resetSignal: Int = 0,
) {
    val nowTime = now.toLocalTime()
    val reducedMotion = LocalReducedMotion.current
    val firstUpcomingIndex = departures.indexOfFirst { !it.time.isBefore(nowTime) }
    val upcoming = if (firstUpcomingIndex < 0) emptyList() else departures.drop(firstUpcomingIndex).take(2)
    val estimated = departures.firstOrNull()?.estimated == true
    // D-095: 가까운 정류장은 연한 컨테이너 + primary 테두리로 강조해 안쪽 칸과 칩이 묻히지 않게 한다
    val emphasis = rememberPlaceEmphasis(isNearby)

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, emphasis.border, DimaShapes.Card)
            .testTag("bus4402_stop_${stop.stopNumber}"),
        shape = DimaShapes.Card,
        colors = CardDefaults.elevatedCardColors(containerColor = emphasis.container),
    ) {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 교내 셔틀 노선 카드와 같은 머리: 이름 + 현재 정류장 배지, 그 아래 횟수·시간대와 정류장 정보
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stop.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = emphasis.title,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // D-137: the badge pops in and out with the place (a fade under reduced motion).
                    AnimatedVisibility(visible = isNearby, enter = dimaPopIn(reducedMotion), exit = dimaPopOut(reducedMotion)) {
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
                    // D-124: the count and the day's span, as on the campus shuttle cards.
                    Text(
                        text = "${departures.size}회 · ${departures.first().time.format(TIME)}–${departures.last().time.format(TIME)}" +
                            if (estimated) " · 예정" else "",
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // D-124: one strip carries the next two departures (large, with their countdown) and the whole day.
            fun minutesUntil(time: java.time.LocalTime): Long {
                val target = now.toLocalDate().atTime(time).atZone(now.zone)
                return (Duration.between(now, target).toMillis().coerceAtLeast(0) + 59_999L) / 60_000L
            }
            val nextMinutesLeft = departures.getOrNull(firstUpcomingIndex)?.let { minutesUntil(it.time) }
            val chips = departures.mapIndexed { index, departure ->
                val isPast = departure.time.isBefore(nowTime)
                val isNext = index == firstUpcomingIndex
                val isSecond = firstUpcomingIndex >= 0 && index == firstUpcomingIndex + 1
                val isFirst = index == 0
                val isLast = index == departures.lastIndex
                val markers = serviceMarkers(isFirst, isLast) + listOfNotNull("예정".takeIf { departure.estimated })
                val look = nextDepartureLook(
                    isFirst = isFirst, isLast = isLast, isPast = isPast, isNext = isNext, isSecond = isSecond,
                    clock = departure.time.format(TIME),
                    minutesLeft = if (isNext || isSecond) minutesUntil(departure.time) else null,
                    nextMinutesLeft = nextMinutesLeft,
                    nextIsFirst = firstUpcomingIndex == 0,
                )
                TimetableChipSpec(
                    key = departure.time.toSecondOfDay(),
                    clock = departure.time.format(TIME),
                    markers = markers,
                    description = timetableChipDescription(departure.time.format(TIME), isPast, isNext, markers, look.countdown.takeUnless { look.farAhead }),
                    tone = look.tone,
                    isPast = isPast,
                    countdown = look.countdown,
                    farAhead = look.farAhead,
                )
            }
            DepartureStrip(
                chips = chips,
                stateKey = timetableKey,
                resetSignal = resetSignal,
                onCardInner = isNearby,
                endedNote = if (departures.isNotEmpty() && upcoming.isEmpty()) "" else null,
                largeCellTag = { "bus4402_next_${stop.stopNumber}_$it" },
                modifier = Modifier.testTag("bus4402_times_${stop.stopNumber}"),
            )
        }
    }
}

/** The two looks of a card for the place the student is at ([rememberPlaceEmphasis]). */
internal data class PlaceEmphasis(val container: Color, val border: Color, val title: Color)

/**
 * The look of a card for where the student is (D-029, D-095): the inner-tone container, a primary border and a
 * primary title, against the plain card. D-137: when the place changes while the card is shown (the 현재 위치 origin
 * or the 현재 정류장), the change between the two looks moves on the default effects spring, as the strip's tiles inside
 * already do, so for a moment the card no longer disagrees with its own tiles. Shapes and tones are unchanged (D-105);
 * the border only fades, so it is always drawn, transparent when [active] is false. Used by the shuttle route card,
 * its origin heading and the 4402 stop card.
 */
@Composable
internal fun rememberPlaceEmphasis(active: Boolean): PlaceEmphasis {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (active) DimaTheme.colors.cardInner else DimaTheme.colors.card,
        DimaMotion.effectsDefault(),
        label = "place_container",
    )
    val border by animateColorAsState(
        if (active) colors.primary else colors.primary.copy(alpha = 0f),
        DimaMotion.effectsDefault(),
        label = "place_border",
    )
    val title by animateColorAsState(
        if (active) colors.primary else colors.onSurface,
        DimaMotion.effectsDefault(),
        label = "place_title",
    )
    return PlaceEmphasis(container, border, title)
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd")
