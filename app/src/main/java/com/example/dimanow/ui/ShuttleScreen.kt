package com.example.dimanow.ui

import com.example.dimanow.ui.motion.DimaMotion
import com.example.dimanow.theme.DimaShapes
import com.example.dimanow.theme.emphasized
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.DisplayVocabulary
import com.example.dimanow.domain.ShuttleDeparture
import com.example.dimanow.guidance.GuidanceEngine
import com.example.dimanow.guidance.AnnotatedServiceDeparture
import com.example.dimanow.guidance.ShuttleBoardPurpose
import com.example.dimanow.shuttle.ShuttleSource
import com.example.dimanow.time.MinuteTicker
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

private enum class ShuttleView { CAMPUS, BUS_4402 }

@Composable
fun ShuttleScreen(
    shuttleSource: ShuttleSource,
    currentZone: CampusZoneId,
    nearbyTransitStopNumber: String? = null,
    modifier: Modifier = Modifier,
    now: ZonedDateTime = ZonedDateTime.now(MinuteTicker.CAMPUS_ZONE),
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    // D-094: null until the cache emits, so the day list shows loading instead of "운행하지 않아요"
    val shuttle by shuttleSource.data.collectLoadedAsState()
    val shuttleDepartures = shuttle?.departures.orEmpty()
    val guidanceEngine = remember { GuidanceEngine() }
    var refreshing by remember { mutableStateOf(false) }
    var refreshMessage by remember { mutableStateOf<String?>(null) }
    // 날짜가 바뀌면(자정) 선택 요일도 새 오늘로 재설정된다 (#14).
    // 선택은 고른 날짜와 함께 저장해, 탭을 오가도 유지되지만 다른 날 돌아오면 오늘로 돌아간다 (D-094(12)).
    val todayEpochDay = now.toLocalDate().toEpochDay()
    var daySelection by rememberSaveable { mutableStateOf(todayEpochDay to now.dayOfWeek) }
    val selectedDay = if (daySelection.first == todayEpochDay) daySelection.second else now.dayOfWeek
    var shuttleView by rememberSaveable { mutableStateOf(ShuttleView.CAMPUS) }

    LaunchedEffect(refreshMessage) {
        refreshMessage?.let {
            snackbarHostState.showSnackbar(it)
            refreshMessage = null
        }
    }

    fun refreshShuttle() {
        if (refreshing) return
        refreshing = true
        refreshMessage = null
        scope.launch {
            try {
                // D-094: 스낵바는 사용자가 직접 당긴 새로고침의 결과만 쉬운 말로 알린다
                refreshMessage = shuttleRefreshMessage(shuttleSource.refresh())
            } finally {
                refreshing = false
            }
        }
    }

    // 요일별 출발지 묶음은 컴포저블 범위에서 한 번만 계산하고, 목록은 카드마다 키가 있는 지연 항목으로 낸다 (D-094)
    val activeDay = selectedDay
    val isToday = activeDay == now.dayOfWeek
    val dayDepartures = remember(shuttleDepartures, activeDay) {
        shuttleDepartures.filter { it.serviceDay == activeDay }
    }
    val originGroups = remember(dayDepartures, currentZone) {
        dayDepartures
            .groupBy { it.originZone }
            .toList()
            .sortedWith(
                compareByDescending<Pair<CampusZoneId, List<ShuttleDeparture>>> {
                    it.first == currentZone && currentZone != CampusZoneId.OUTSIDE
                }.thenBy { it.first.ordinal }
            )
            .map { (originZone, originDepartures) ->
                originZone to originDepartures
                    .groupBy { it.destinationZone }
                    .toList()
                    .sortedBy { it.first?.ordinal ?: Int.MAX_VALUE }
            }
    }

    Box(modifier = modifier.fillMaxSize()) {
    ScreenScaffold(
        title = "셔틀버스",
        modifier = Modifier.fillMaxSize(),
        itemSpacing = DimaLayout.sectionGap,
        // 새로고침은 목록을 당겨서 실행한다 (D-058)
        onRefresh = ::refreshShuttle,
        refreshing = refreshing,
        listTag = "shuttle_list",
    ) {
        item(key = "shuttle_view") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = shuttleView == ShuttleView.CAMPUS,
                    onClick = { shuttleView = ShuttleView.CAMPUS },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("교내 셔틀") }
                SegmentedButton(
                    selected = shuttleView == ShuttleView.BUS_4402,
                    onClick = { shuttleView = ShuttleView.BUS_4402 },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("4402") }
            }
        }
        if (shuttleView == ShuttleView.BUS_4402) {
            item(key = "bus_4402") {
                Bus4402ScheduleContent(now = now, nearbyStopNumber = nearbyTransitStopNumber)
            }
        } else {
            // M3 Expressive 요일 선택 (DimaDaySelector)
            item(key = "shuttle_days") {
                DimaDaySelector(
                    days = DayOfWeek.entries,
                    selected = selectedDay,
                    onSelect = { daySelection = todayEpochDay to it },
                    today = now.dayOfWeek,
                    itemTag = { "shuttle_day_${it.name}" },
                )
            }
            when {
                shuttle == null -> item(key = "shuttle_loading") {
                    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = DimaShapes.Card) {
                        LoadingLine(
                            text = "셔틀 시간표를 불러오고 있어요",
                            modifier = Modifier.padding(24.dp).testTag("shuttle_loading"),
                        )
                    }
                }
                // 저장된 시간표가 통째로 없으면 요일 탓이 아니므로, 받지 못한 이유와 다시 받기를 안내한다 (D-094(14))
                shuttleDepartures.isEmpty() && shuttle?.error != null -> item(key = "shuttle_error") {
                    ErrorState(
                        message = "셔틀 시간표를 받지 못했어요",
                        supporting = "인터넷 연결을 확인하고 다시 시도해 주세요",
                        onRetry = ::refreshShuttle,
                        modifier = Modifier.testTag("shuttle_error"),
                    )
                }
                shuttleDepartures.isEmpty() -> item(key = "shuttle_no_data") {
                    EmptyState(
                        message = "셔틀 시간표가 아직 없어요",
                        supporting = "아래로 당기거나 버튼을 눌러 받아 보세요",
                        actionLabel = "시간표 받기",
                        onAction = ::refreshShuttle,
                        modifier = Modifier.testTag("shuttle_no_data"),
                    )
                }
                dayDepartures.isEmpty() -> item(key = "shuttle_empty_${activeDay.name}") {
                    EmptyState(
                        message = "${koreanWeekdayLabel(activeDay)}은 운행하지 않아요",
                        modifier = Modifier.animateItem(fadeInSpec = dayFadeIn, placementSpec = null, fadeOutSpec = null),
                    )
                }
                // 선택된 요일의 노선별 셔틀 목록 (현재 위치 출발 우선 정렬)
                else -> originGroups.forEach { (originZone, routes) ->
                    val isCurrentLocation = originZone == currentZone && currentZone != CampusZoneId.OUTSIDE
                    item(key = "origin_${activeDay.name}_${originZone.name}") {
                        ShuttleOriginHeader(
                            origin = DisplayVocabulary.originName(originZone),
                            isCurrentLocation = isCurrentLocation,
                            modifier = Modifier.animateItem(fadeInSpec = dayFadeIn, placementSpec = null, fadeOutSpec = null),
                        )
                    }
                    routes.forEach { (destinationZone, routeDepartures) ->
                        item(key = "route_${activeDay.name}_${originZone.name}_${destinationZone?.name}") {
                            Box(Modifier.animateItem(fadeInSpec = dayFadeIn, placementSpec = null, fadeOutSpec = null)) {
                                ShuttleRouteCard(
                                    originZone = originZone,
                                    destinationZone = destinationZone,
                                    routeDepartures = routeDepartures,
                                    shuttleDepartures = shuttleDepartures,
                                    activeDay = activeDay,
                                    isToday = isToday,
                                    isCurrentLocation = isCurrentLocation,
                                    now = now,
                                    guidanceEngine = guidanceEngine,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    SnackbarHost(
        snackbarHostState,
        Modifier
            .align(Alignment.BottomCenter)
            .padding(16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
    }
}

@Composable
private fun ShuttleOriginHeader(origin: String, isCurrentLocation: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = origin,
            style = if (isCurrentLocation) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            color = if (isCurrentLocation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (isCurrentLocation) {
            Surface(
                shape = DimaShapes.Badge,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    text = "현재 위치",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** A newly selected day's cards fade in; the previous day's cards leave at once so two days never overlap. */
private val dayFadeIn = DimaMotion.effectsSlow<Float>()

/**
 * One origin-to-destination card of the campus shuttle list (a keyed lazy item, D-094).
 * Its board/annotation work is remembered per card, so a minute tick only redoes the countdowns.
 */
@Composable
private fun ShuttleRouteCard(
    originZone: CampusZoneId,
    destinationZone: CampusZoneId?,
    routeDepartures: List<ShuttleDeparture>,
    shuttleDepartures: List<ShuttleDeparture>,
    activeDay: DayOfWeek,
    isToday: Boolean,
    isCurrentLocation: Boolean,
    now: ZonedDateTime,
    guidanceEngine: GuidanceEngine,
) {
    val nowTime = now.toLocalTime()
    val destination = destinationZone?.let(DisplayVocabulary::originName)
    val annotatedDepartures = remember(shuttleDepartures, activeDay, originZone, destinationZone) {
        if (destinationZone != null) {
            guidanceEngine.annotatedServiceDepartures(
                serviceDay = activeDay,
                originZone = originZone,
                destinationZone = destinationZone,
                departures = shuttleDepartures,
            )
        } else {
            val slots = routeDepartures.sortedBy { it.time }.distinctBy { it.time }
            slots.mapIndexed { index, departure ->
                AnnotatedServiceDeparture(
                    departure = departure,
                    isFirst = index == 0,
                    isLast = index == slots.lastIndex,
                )
            }
        }
    }
    val sortedTimes = annotatedDepartures.map { it.departure.time }

    val upcomingCountdowns = remember(shuttleDepartures, now, activeDay, isToday, originZone, destinationZone) {
        if (isToday && destinationZone != null) {
            guidanceEngine.shuttleBoard(
                now = now,
                originZone = originZone,
                departures = shuttleDepartures,
                purpose = ShuttleBoardPurpose.GENERAL,
            ).rows.firstOrNull { it.destinationZone == destinationZone }?.departures.orEmpty()
        } else emptyList()
    }
    val upcomingTimes = upcomingCountdowns.map { it.departure.time }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = DimaShapes.Card,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isCurrentLocation) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = destination?.let { "${it}행" } ?: "기타 목적지",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrentLocation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                if (sortedTimes.isNotEmpty()) {
                    Surface(shape = DimaShapes.Badge, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Text(
                            text = "첫차 ${sortedTimes.first().format(TIME)} · 막차 ${sortedTimes.last().format(TIME)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // 실시간 다가오는 셔틀 2개 남은 시간 뱃지 (오늘 요일일 때만)
            if (isToday) {
                if (upcomingTimes.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "출발 시각",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        // 4402와 같은 캡슐: 남은 시간·표식 한 줄, 그 아래 출발 시각 한 줄 (D-094(5))
                        DepartureCapsuleRow(spacing = 8.dp) {
                            upcomingCountdowns.forEachIndexed { index, countdown ->
                                val time = countdown.departure.time
                                val serviceDeparture = annotatedDepartures.firstOrNull { it.departure.time == time }
                                val isLastService = serviceDeparture?.isLast == true
                                val boardingStopLabel = when {
                                    serviceDeparture?.isBoardingStopTransition == true -> "운동장 전환"
                                    serviceDeparture?.isStadiumStop == true -> "운동장"
                                    else -> null
                                }
                                val (container, content) = shuttleCapsuleColors(isNearest = index == 0, isLastService = isLastService)
                                DepartureCapsule(
                                    label = departureCountdownLabel(
                                        countdown.remainingMinutes,
                                        serviceMarkers(serviceDeparture?.isFirst == true, isLastService) + listOfNotNull(boardingStopLabel),
                                    ),
                                    clock = time.format(TIME),
                                    containerColor = container,
                                    contentColor = content,
                                    labelStyle = MaterialTheme.typography.titleSmall.emphasized(),
                                    modifier = Modifier.testTag("next_departure_$index"),
                                )
                            }
                        }
                    }
                } else {
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
                }
            }

            // 전체 시간표 칩 (지나간 시간은 딤 처리)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "전체 시간표 · ${sortedTimes.size}회",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowTimeChips(
                    departures = annotatedDepartures,
                    isToday = isToday,
                    nowTime = nowTime,
                    upcomingTimes = upcomingTimes,
                    contextKey = "$activeDay|$originZone|$destinationZone|${sortedTimes.hashCode()}",
                )
            }
        }
    }
}

@Composable
private fun FlowTimeChips(
    departures: List<AnnotatedServiceDeparture>,
    isToday: Boolean,
    nowTime: LocalTime,
    upcomingTimes: List<LocalTime>,
    contextKey: String,
) {
    val times = departures.map { it.departure.time }
    val initialIndex = when {
        times.isEmpty() || !isToday -> 0
        else -> times.indexOfFirst { !it.isBefore(nowTime) }.takeIf { it >= 0 } ?: times.lastIndex
    }
    val chips = departures.map { item ->
        val time = item.departure.time
        val isPast = isToday && time.isBefore(nowTime)
        val isNext = isToday && upcomingTimes.getOrNull(0) == time
        val isSecond = isToday && upcomingTimes.getOrNull(1) == time
        val boardingStopLabel = when {
            item.isBoardingStopTransition -> "운동장 전환"
            item.isStadiumStop -> "운동장"
            else -> null
        }
        TimetableChipSpec(
            key = time.toSecondOfDay(),
            text = item.displayText,
            description = timetableChipDescription(
                clock = time.format(TIME),
                isPast = isPast,
                isNext = isNext,
                markers = serviceMarkers(item.isFirst, item.isLast) + listOfNotNull(boardingStopLabel),
            ),
            tone = timetableChipTone(item.isFirst, item.isLast, isPast, isNext, isSecond),
        )
    }
    TimetableChipRow(chips = chips, initialIndex = initialIndex, stateKey = contextKey)
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
