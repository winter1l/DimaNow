package com.example.dimanow.ui

import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import com.example.dimanow.ui.motion.DimaMotion
import com.example.dimanow.ui.motion.LocalReducedMotion
import com.example.dimanow.ui.motion.dimaCollapseOut
import com.example.dimanow.ui.motion.dimaExpandIn
import com.example.dimanow.ui.motion.dimaItemChange
import com.example.dimanow.ui.motion.dimaPopIn
import com.example.dimanow.ui.motion.dimaPopOut
import com.example.dimanow.theme.DimaShapes
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.dimanow.guidance.ShuttleScheduleIndex
import com.example.dimanow.shuttle.ShuttleSource
import com.example.dimanow.time.MinuteTicker
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

private enum class ShuttleView { CAMPUS, BUS_4402 }

/**
 * What the 교내 셔틀 list shows for the chosen day (D-137): with the day it is the list's content key, so a change of
 * either replaces the whole list with one fade instead of the cards fading or popping one by one.
 */
private enum class ShuttlePhase { LOADING, ERROR, NO_DATA, SUSPENDED, CONTENT }

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
    // D-124: a refresh or reselecting the 셔틀 tab brings every route strip back to its next departure.
    var stripReset by remember { mutableIntStateOf(0) }
    val reselection = LocalScrollToTopSignal.current
    var seenReselection by remember { mutableIntStateOf(reselection) }
    LaunchedEffect(reselection) {
        if (reselection != seenReselection) {
            seenReselection = reselection
            stripReset++
        }
    }

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
        stripReset++
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
    // One schedule index per timetable, shared by every card's countdown instead of rebuilt per card each minute.
    val shuttleIndex = remember(shuttleDepartures) { guidanceEngine.prepareShuttleSchedule(shuttleDepartures) }
    // D-099: 공휴일·운행 중지일에는 오늘 시간표 대신 미운행을, 학기 밖(방학)에는 시간 변동 가능성을 알린다
    val todaySuspension = remember(now.toLocalDate()) { guidanceEngine.campusShuttleSuspensionOn(now.toLocalDate()) }
    val outsideTerm = remember(now.toLocalDate()) {
        val calendar = com.example.dimanow.calendar.ServiceCalendarStore.current
        calendar.terms.isNotEmpty() && calendar.termContaining(now.toLocalDate()) == null
    }
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
    val phase = when {
        shuttle == null -> ShuttlePhase.LOADING
        shuttleDepartures.isEmpty() && shuttle?.error != null -> ShuttlePhase.ERROR
        shuttleDepartures.isEmpty() -> ShuttlePhase.NO_DATA
        isToday && todaySuspension != null -> ShuttlePhase.SUSPENDED
        else -> ShuttlePhase.CONTENT
    }
    val reducedMotion = LocalReducedMotion.current

    Box(modifier = modifier.fillMaxSize()) {
    ScreenScaffold(
        title = "셔틀버스",
        modifier = Modifier.fillMaxSize(),
        itemSpacing = DimaLayout.sectionGap,
        // 새로고침은 목록을 당기거나 상단 버튼으로 실행한다 (D-058, D-103)
        onRefresh = ::refreshShuttle,
        refreshing = refreshing,
        refreshLabel = "셔틀 새로고침",
        listTag = "shuttle_list",
        pageIndex = shuttleView.ordinal,
        // D-137: within 교내 셔틀 a day change, the first cache read or a retry that replaces an error fades the whole
        // list in again; 4402 has no days, and switching modes slides instead (pageIndex).
        contentKey = if (shuttleView == ShuttleView.CAMPUS) activeDay to phase else null,
        // D-102: 식단 화면처럼 노선 전환과 요일 선택을 머리에 고정해, 시간표를 내려 읽다가도 바로 바꿀 수 있다 (D-057)
        subHeader = {
            // D-137: one column without the header's spacing, so the gap above the weekday row folds with it.
            Column {
                // D-103: 노선 전환은 M3 연결형 버튼 그룹이다
                DimaConnectedChoice(
                    options = listOf(ShuttleView.CAMPUS to "교내 셔틀", ShuttleView.BUS_4402 to "4402"),
                    selected = shuttleView,
                    onSelect = { shuttleView = it },
                )
                // D-137: the weekday row folds away for 4402 and back on the non-bouncy spring the list slides on
                // (D-129), its 8dp gap inside, so the pinned header neither snaps nor bobs; at once under reduced motion.
                AnimatedVisibility(
                    visible = shuttleView == ShuttleView.CAMPUS,
                    enter = if (reducedMotion) EnterTransition.None else dimaExpandIn(DimaMotion.settle()),
                    exit = if (reducedMotion) ExitTransition.None else dimaCollapseOut(DimaMotion.settle()),
                    label = "shuttle_day_selector",
                ) {
                    // M3 Expressive 요일 선택 (DimaDaySelector)
                    DimaDaySelector(
                        days = DayOfWeek.entries,
                        selected = selectedDay,
                        onSelect = { daySelection = todayEpochDay to it },
                        modifier = Modifier.padding(top = 8.dp),
                        today = now.dayOfWeek,
                        itemTag = { "shuttle_day_${it.name}" },
                    )
                }
            }
        },
    ) {
        if (shuttleView == ShuttleView.BUS_4402) {
            item(key = "bus_4402") {
                Bus4402ScheduleContent(now = now, nearbyStopNumber = nearbyTransitStopNumber, resetSignal = stripReset)
            }
        } else {
            when (phase) {
                ShuttlePhase.LOADING -> item(key = "shuttle_loading") {
                    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = DimaShapes.Card) {
                        LoadingLine(
                            text = "셔틀 시간표를 불러오고 있어요",
                            modifier = Modifier.padding(24.dp).testTag("shuttle_loading"),
                        )
                    }
                }
                // 저장된 시간표가 통째로 없으면 요일 탓이 아니므로, 받지 못한 이유와 다시 받기를 안내한다 (D-094(14))
                ShuttlePhase.ERROR -> item(key = "shuttle_error") {
                    ErrorState(
                        message = "셔틀 시간표를 받지 못했어요",
                        supporting = "인터넷 연결을 확인하고 다시 시도해 주세요",
                        onRetry = ::refreshShuttle,
                        modifier = Modifier.testTag("shuttle_error"),
                    )
                }
                ShuttlePhase.NO_DATA -> item(key = "shuttle_no_data") {
                    EmptyState(
                        message = "셔틀 시간표가 아직 없어요",
                        supporting = "아래로 당기거나 버튼을 눌러 받아 보세요",
                        actionLabel = "시간표 받기",
                        onAction = ::refreshShuttle,
                        modifier = Modifier.testTag("shuttle_no_data"),
                    )
                }
                ShuttlePhase.SUSPENDED -> item(key = "shuttle_suspended") {
                    EmptyState(
                        message = "오늘은 셔틀이 운행하지 않아요 · $todaySuspension",
                        supporting = "다른 요일을 고르면 평소 시간표를 볼 수 있어요",
                        modifier = Modifier.testTag("shuttle_suspended"),
                    )
                }
                // D-137: the day's items fade in only when a refresh adds them (a day change fades the whole list, so
                // they do not fade again) and leave at once, so two days never overlap; items that move glide.
                ShuttlePhase.CONTENT -> if (dayDepartures.isEmpty()) {
                    item(key = "shuttle_empty_${activeDay.name}") {
                        EmptyState(
                            message = "${koreanWeekdayLabel(activeDay)}은 운행하지 않아요",
                            modifier = dimaItemChange(fadeIn = true, fadeOut = false, placement = true),
                        )
                    }
                } else {
                    // 선택된 요일의 노선별 셔틀 목록 (현재 위치 출발 우선 정렬)
                    if (outsideTerm) item(key = "shuttle_vacation_note") {
                        Text(
                            "지금은 학기 중이 아니라 운행 시간이 다를 수 있어요. 학교 공지를 함께 확인해 주세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("shuttle_vacation_note"),
                        )
                    }
                    originGroups.forEach { (originZone, routes) ->
                        val isCurrentLocation = originZone == currentZone && currentZone != CampusZoneId.OUTSIDE
                        item(key = "origin_${activeDay.name}_${originZone.name}") {
                            ShuttleOriginHeader(
                                origin = DisplayVocabulary.originName(originZone),
                                isCurrentLocation = isCurrentLocation,
                                modifier = dimaItemChange(fadeIn = true, fadeOut = false, placement = true),
                            )
                        }
                        routes.forEach { (destinationZone, routeDepartures) ->
                            item(key = "route_${activeDay.name}_${originZone.name}_${destinationZone?.name}") {
                                // A new 현재 위치 re-sorts the groups under the same keys: they glide to their places.
                                Box(dimaItemChange(fadeIn = true, fadeOut = false, placement = true)) {
                                    ShuttleRouteCard(
                                        originZone = originZone,
                                        destinationZone = destinationZone,
                                        routeDepartures = routeDepartures,
                                        shuttleDepartures = shuttleDepartures,
                                        shuttleIndex = shuttleIndex,
                                        activeDay = activeDay,
                                        isToday = isToday,
                                        isCurrentLocation = isCurrentLocation,
                                        now = now,
                                        guidanceEngine = guidanceEngine,
                                        resetSignal = stripReset,
                                    )
                                }
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
    val emphasis = rememberPlaceEmphasis(isCurrentLocation)
    val reducedMotion = LocalReducedMotion.current
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = origin,
            style = if (isCurrentLocation) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            color = emphasis.title,
        )
        // D-137: the badge pops in and out with the place (a fade under reduced motion).
        AnimatedVisibility(visible = isCurrentLocation, enter = dimaPopIn(reducedMotion), exit = dimaPopOut(reducedMotion)) {
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
    shuttleIndex: ShuttleScheduleIndex,
    activeDay: DayOfWeek,
    isToday: Boolean,
    isCurrentLocation: Boolean,
    now: ZonedDateTime,
    guidanceEngine: GuidanceEngine,
    resetSignal: Int = 0,
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

    val upcomingCountdowns = remember(shuttleIndex, now, activeDay, isToday, originZone, destinationZone) {
        if (isToday && destinationZone != null) {
            guidanceEngine.shuttleBoard(
                now = now,
                originZone = originZone,
                index = shuttleIndex,
                purpose = ShuttleBoardPurpose.GENERAL,
            ).rows.firstOrNull { it.destinationZone == destinationZone }?.departures.orEmpty()
        } else emptyList()
    }
    val upcomingTimes = upcomingCountdowns.map { it.departure.time }

    val nextTime = upcomingTimes.getOrNull(0)
    val secondTime = upcomingTimes.getOrNull(1)
    val chips = annotatedDepartures.map { item ->
        val time = item.departure.time
        val isPast = isToday && time.isBefore(nowTime)
        val isNext = isToday && nextTime == time
        val isSecond = isToday && secondTime == time
        val markers = serviceMarkers(item.isFirst, item.isLast) + listOfNotNull(
            when {
                item.isBoardingStopTransition -> "운동장 전환"
                item.isStadiumStop -> "운동장"
                else -> null
            },
        )
        val look = nextDepartureLook(
            isFirst = item.isFirst, isLast = item.isLast, isPast = isPast, isNext = isNext, isSecond = isSecond,
            clock = time.format(TIME),
            minutesLeft = upcomingCountdowns.firstOrNull { it.departure.time == time }?.remainingMinutes,
            nextMinutesLeft = upcomingCountdowns.firstOrNull()?.remainingMinutes,
            nextIsFirst = annotatedDepartures.firstOrNull { it.departure.time == nextTime }?.isFirst == true,
        )
        TimetableChipSpec(
            key = time.toSecondOfDay(),
            clock = time.format(TIME),
            markers = markers,
            description = timetableChipDescription(time.format(TIME), isPast, isNext, markers, look.countdown.takeUnless { look.farAhead }),
            tone = look.tone,
            isPast = isPast,
            countdown = look.countdown,
            farAhead = look.farAhead,
        )
    }
    // D-124: once today's last departure has left, the strip ends with tomorrow's first one for this route.
    val endedNote = if (isToday && chips.isNotEmpty() && chips.all { it.isPast }) {
        val tomorrow = now.toLocalDate().plusDays(1)
        when {
            guidanceEngine.campusShuttleSuspensionOn(tomorrow) != null -> "내일은 운행하지 않아요"
            else -> shuttleDepartures
                .filter { it.serviceDay == tomorrow.dayOfWeek && it.originZone == originZone && it.destinationZone == destinationZone }
                .minOfOrNull { it.time }
                ?.let { "내일 첫차 ${it.format(TIME)}" }
                ?: ""
        }
    } else null

    // D-029/D-095: 현재 위치 출발 카드는 연한 컨테이너 + primary 테두리로 강조한다
    val emphasis = rememberPlaceEmphasis(isCurrentLocation)
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, emphasis.border, DimaShapes.Card),
        shape = DimaShapes.Card,
        colors = CardDefaults.elevatedCardColors(containerColor = emphasis.container),
    ) {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = destination?.let { "${it}행" } ?: "기타 목적지",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = emphasis.title,
                )
                if (sortedTimes.isNotEmpty()) {
                    // D-124: the count and the day's span; first and last are named in the strip itself.
                    Text(
                        text = "${sortedTimes.size}회 · ${sortedTimes.first().format(TIME)}–${sortedTimes.last().format(TIME)}",
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("shuttle_route_span"),
                    )
                }
            }
            // D-124: one strip carries the next two departures (large, with their countdown) and the whole day.
            DepartureStrip(
                chips = chips,
                stateKey = "$activeDay|$originZone|$destinationZone|${sortedTimes.hashCode()}",
                resetSignal = resetSignal,
                onCardInner = isCurrentLocation,
                endedNote = endedNote,
                largeCellTag = { "next_departure_$it" },
            )
        }
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
