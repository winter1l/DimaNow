package com.example.dimanow.ui

import com.example.dimanow.ui.meal.groupDormitorySections
import com.example.dimanow.ui.motion.AnimatedCountText
import androidx.compose.ui.text.style.TextOverflow
import com.example.dimanow.theme.DimaShapes
import com.example.dimanow.theme.emphasized
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import com.example.dimanow.meal.MealServiceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.DisplayVocabulary
import com.example.dimanow.domain.TermSchedule
import com.example.dimanow.guidance.GuidanceEngine
import com.example.dimanow.guidance.ShuttleBoardPurpose
import com.example.dimanow.meal.MealData
import com.example.dimanow.meal.DormitoryMealData
import com.example.dimanow.meal.mealServiceStatus
import com.example.dimanow.notice.NoticeData
import com.example.dimanow.notice.OFFICIAL_NOTICE_SOURCE_URL
import com.example.dimanow.shuttle.ShuttleData
import com.example.dimanow.lms.LmsSessionState
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first

/**
 * `null` for [shuttle], [meal], [dormitoryMeal] or [notices] means the source has not emitted its
 * first cached value yet; the matching card shows a loading placeholder instead of an empty or
 * error state (D-094).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DashboardScreen(
    schedule: TermSchedule,
    zone: CampusZoneId,
    testMode: Boolean = false,
    shuttle: ShuttleData?,
    meal: MealData?,
    dormitoryMeal: DormitoryMealData? = DormitoryMealData(emptyList(), null, null, null),
    notices: NoticeData? = NoticeData(emptyList(), null, null, null, OFFICIAL_NOTICE_SOURCE_URL),
    onNavigateToPage: (AppPage) -> Unit = {},
    modifier: Modifier = Modifier,
    now: ZonedDateTime = ZonedDateTime.now(),
    lmsSnapshot: com.example.dimanow.lms.LmsSnapshot = com.example.dimanow.lms.LmsSnapshot(),
    lmsSessionState: LmsSessionState = LmsSessionState.SIGNED_OUT,
) {
    val context = LocalContext.current
    val nowTime = now.toLocalTime()
    val guidancePaused = now.toLocalDate() in schedule.noClassDates || schedule.guidancePause?.contains(now.toLocalDate()) == true
    val todayCourses = schedule.coursesOn(now.toLocalDate())
    val remainingCourses = todayCourses.filter { !it.end.isBefore(nowTime) }
    val nextCourse = remainingCourses.firstOrNull()
    val upcomingAfterCourse = remainingCourses.getOrNull(1)

    // 엔진과 무거운 계산은 입력이 바뀔 때만 다시 수행한다 (#20)
    val guidanceEngine = remember { GuidanceEngine() }
    val shuttleDepartures = shuttle?.departures.orEmpty()
    val shuttleBoard = remember(now, zone, shuttleDepartures) {
        guidanceEngine.shuttleBoard(
            now = now,
            originZone = zone,
            departures = shuttleDepartures,
            purpose = ShuttleBoardPurpose.GENERAL,
        )
    }

    val todayMeal = meal?.days?.firstOrNull { it.date == now.toLocalDate() }
    val homeDormitoryMeal = dormitoryMeal?.homeServiceAt(now)
    val useDormitoryMeal = zone == CampusZoneId.YEIN
    // D-094: 선택된 식당의 원본이 아직 첫 값을 내보내지 않았으면 불러오는 중으로 본다
    val mealLoading = if (useDormitoryMeal) dormitoryMeal == null else meal == null
    val mealStatusNow = meal?.serviceStatusAt(now)
    val homeMealOpen = if (useDormitoryMeal) {
        homeDormitoryMeal?.let { day ->
            groupDormitorySections(day.sections).any { mealServiceStatus(day.date, it.hours, now).state == MealServiceState.OPEN }
        } == true
    } else mealStatusNow?.state == MealServiceState.OPEN
    val originName = DisplayVocabulary.originName(zone)

    // D-029: 홈의 상단 바 제목 자리는 현재 위치 배지이고, 설정 톱니는 오른쪽 끝에 둔다 (D-094)
    ScreenScaffold(
        modifier = modifier,
        itemSpacing = DimaLayout.sectionGap,
        listTag = "home_list",
        titleContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = DimaShapes.Badge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "위치: $originName",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
    ) {
        // 카드마다 키가 있는 지연 항목이라, 분 틱은 바뀐 값을 읽는 카드만 다시 그린다 (D-094)
        if (testMode) item(key = "test_mode") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = DimaShapes.Tile,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    text = "테스트 모드 · GPS 미반영",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        // 1. M3 Expressive Hero 수업 브리핑 카드
        item(key = "hero") {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = DimaShapes.Card,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // 브리핑 본문 (수업 일정)
                    if (nextCourse != null) {
                        val isOngoing = !nowTime.isBefore(nextCourse.start)
                        // D-069 style: minutes only within an hour, otherwise the start clock (D-094(14))
                        val startLabel = classStartLabel(now, now.toLocalDate().atTime(nextCourse.start).atZone(now.zone))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Surface(
                                    shape = DimaShapes.Badge,
                                    color = if (isOngoing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                ) {
                                    AnimatedCountText(
                                        text = if (isOngoing) "수업 중" else startLabel,
                                        style = MaterialTheme.typography.labelMedium.emphasized(),
                                        color = if (isOngoing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary,
                                        modifier = Modifier
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .testTag("home_hero_badge"),
                                    )
                                }
                                Text(
                                    text = "${nextCourse.start.format(TIME)} ~ ${nextCourse.end.format(TIME)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                            Text(
                                text = nextCourse.name,
                                style = MaterialTheme.typography.headlineSmall.emphasized(),
                            )
                            Text(
                                text = if (nextCourse.professor.isNotBlank()) "${nextCourse.room} · ${nextCourse.professor}" else nextCourse.room,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            // 셔틀 출발 정보는 바로 아래 셔틀 카드가 캡슐로 보여주므로
                            // 히어로 카드에서 같은 내용을 문장으로 반복하지 않는다 (D-056)
                        }

                        // 오늘의 다음 수업 표시 (있을 경우)
                        if (upcomingAfterCourse != null) {
                            HeroClassPreviewRow(
                                label = "다음 수업 · ${upcomingAfterCourse.start.format(TIME)}",
                                detail = "${upcomingAfterCourse.name} (${upcomingAfterCourse.room})",
                            )
                        }
                    } else {
                        // 다음 수업일(내일부터 최대 7일, 휴강일 제외)의 첫 수업 미리보기
                        val nextClassPreview = remember(schedule, now) {
                            (1L..7L).asSequence()
                                .map { now.toLocalDate().plusDays(it) }
                                .filterNot { date ->
                                    date in schedule.noClassDates || schedule.guidancePause?.contains(date) == true
                                }
                                .filter { !it.isBefore(schedule.termStart) && !it.isAfter(schedule.termEnd) }
                                .mapNotNull { date ->
                                    schedule.coursesOn(date)
                                        .minByOrNull { it.start }
                                        ?.let { date to it }
                                }
                                .firstOrNull()
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = when {
                                    schedule.courses.isEmpty() -> "시간표에 수업을 추가해 주세요"
                                    guidancePaused -> "오늘은 휴강이에요"
                                    schedule.coursesOn(now.toLocalDate()).isEmpty() -> "오늘은 수업이 없어요"
                                    else -> "오늘 수업이 모두 끝났어요"
                                },
                                style = MaterialTheme.typography.titleLarge.emphasized(),
                            )
                            if (nextClassPreview != null) {
                                val (date, course) = nextClassPreview
                                val dayLabel = if (date == now.toLocalDate().plusDays(1)) {
                                    "내일 첫 수업"
                                } else {
                                    "${koreanWeekdayLabel(date.dayOfWeek)} 첫 수업"
                                }
                                HeroClassPreviewRow(
                                    label = "$dayLabel · ${course.start.format(TIME)}",
                                    detail = "${course.name} (${course.room})",
                                )
                            }
                        }
                    }

                }
            }
        }

        // 수업 카드: 카드 전체가 수업 탭을 연다. 로그인 전에는 한 줄 안내로 줄어든다 (D-094(9))
        item(key = "learning") {
            HomeTodaySummary(
                snapshot = lmsSnapshot, now = now,
                onOpenCourses = { onNavigateToPage(AppPage.COURSES) },
                sessionState = lmsSessionState,
            )
        }

        // 2. 실시간 셔틀 카드 — 카드 전체가 셔틀 탭을 연다 (D-094(9))
        item(key = "shuttle") {
            HomeSummaryCard(
                onClick = { onNavigateToPage(AppPage.SHUTTLE) },
                onClickLabel = "셔틀 전체 시간표 보기",
                modifier = Modifier.testTag("dashboard_shuttle_card"),
            ) {
                HomeCardHeader(icon = Icons.Default.DirectionsBus, title = "셔틀 ($originName 출발)")

                val todayZoneDepartures = shuttleDepartures.filter { it.serviceDay == now.dayOfWeek && it.originZone == zone }
                val destinations = todayZoneDepartures.mapNotNull { it.destinationZone }.distinct().sortedBy { it.ordinal }

                when {
                    // D-094: 첫 캐시 값이 오기 전에는 '데이터 없음' 대신 불러오는 중으로 보여준다
                    shuttle == null -> {
                        LoadingLine("셔틀 시간표를 불러오고 있어요", Modifier.testTag("dashboard_shuttle_loading"))
                    }
                    shuttleDepartures.isEmpty() -> {
                        if (shuttle.error != null) {
                            ErrorState(homeShuttleUnavailableMessage(shuttle), contained = false)
                        } else {
                            EmptyState(homeShuttleUnavailableMessage(shuttle), contained = false)
                        }
                    }
                    zone == CampusZoneId.OUTSIDE -> {
                        Text("캠퍼스 외부 — 교내 진입 시 자동 안내", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    destinations.isNotEmpty() -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            destinations.forEach { destZone ->
                                val destName = DisplayVocabulary.originName(destZone)
                                // 목적지 루프 안에서 엔진을 새로 만들지 않고 위에서 remember한 것을 쓴다
                                val annotated = guidanceEngine.annotatedServiceDepartures(
                                    serviceDay = now.dayOfWeek,
                                    originZone = zone,
                                    destinationZone = destZone,
                                    departures = shuttleDepartures,
                                )
                                val upcoming = shuttleBoard.rows.firstOrNull { it.destinationZone == destZone }?.departures.orEmpty()

                                // D-094: 목적지 태그를 캡슐 위 한 줄에 두어 캡슐이 카드 폭을 온전히 쓰게 한다.
                                // 캡슐 안에서는 남은 시간·표식 한 줄과 출발 시각 한 줄을 나눠 글자 단위 줄바꿈을 막는다.
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    // 항상 크게 표시되는 목적지 태그
                                    Surface(
                                        shape = DimaShapes.Badge,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                    ) {
                                        Text(
                                            text = "${destName}행",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    }

                                    if (upcoming.isNotEmpty()) {
                                        DepartureCapsuleRow {
                                            upcoming.forEachIndexed { index, countdown ->
                                                val time = countdown.departure.time
                                                val minutesLeft = countdown.remainingMinutes
                                                val isFirst = index == 0
                                                val serviceDeparture = annotated.firstOrNull { it.departure.time == time }
                                                val isLastService = serviceDeparture?.isLast == true
                                                val serviceLabel = when {
                                                    serviceDeparture?.isFirst == true && isLastService -> "첫차·막차"
                                                    serviceDeparture?.isFirst == true -> "첫차"
                                                    isLastService -> "막차"
                                                    else -> null
                                                }
                                                val boardingStopLabel = when {
                                                    serviceDeparture?.isBoardingStopTransition == true -> "운동장 전환"
                                                    serviceDeparture?.isStadiumStop == true -> "운동장"
                                                    else -> null
                                                }
                                                DepartureCapsule(
                                                    label = buildString {
                                                        append(when {
                                                            minutesLeft > 60 -> "다음 출발"
                                                            minutesLeft <= 0 -> "곧 출발"
                                                            else -> "${minutesLeft}분 후"
                                                        })
                                                        serviceLabel?.let { append(" · $it") }
                                                        boardingStopLabel?.let { append(" · $it") }
                                                    },
                                                    clock = time.format(TIME),
                                                    // D-083: 가장 가까운 출발은 primary, 그 다음은 secondary, 막차는 error 계열
                                                    containerColor = when {
                                                        isLastService -> MaterialTheme.colorScheme.errorContainer
                                                        isFirst -> MaterialTheme.colorScheme.primaryContainer
                                                        else -> MaterialTheme.colorScheme.secondaryContainer
                                                    },
                                                    contentColor = when {
                                                        isLastService -> MaterialTheme.colorScheme.onErrorContainer
                                                        isFirst -> MaterialTheme.colorScheme.onPrimaryContainer
                                                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                                                    },
                                                    modifier = Modifier.testTag("home_departure_${destZone.name}_$index"),
                                                )
                                            }
                                        }
                                    } else {
                                        // 운행 종료 상태 (첫차 및 막차 시각 함께 표시)
                                        val firstDepartureTime = annotated.firstOrNull()?.departure?.time
                                        val lastDepartureTime = annotated.lastOrNull()?.departure?.time
                                        Surface(
                                            shape = DimaShapes.Tile,
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            FlowRow(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                                itemVerticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    text = "운행 종료",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                if (firstDepartureTime != null && lastDepartureTime != null) {
                                                    Text(
                                                        text = "첫차 ${firstDepartureTime.format(TIME)} · 막차 ${lastDepartureTime.format(TIME)}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        Text("오늘은 운행하지 않아요", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // 3. 현재 구역의 오늘 식단 카드 — 카드 전체가 식단 탭을 연다 (D-094(9))
        item(key = "meal") {
            HomeSummaryCard(
                onClick = { onNavigateToPage(AppPage.MEAL) },
                onClickLabel = "식단 보기",
                modifier = Modifier
                    .testTag("dashboard_meal_card")
                    .semantics {
                        stateDescription = when {
                            mealLoading -> "불러오는 중"
                            homeMealOpen -> "운영 중"
                            else -> "운영 시간 아님"
                        }
                    },
            ) {
                HomeCardHeader(icon = Icons.Default.Restaurant, title = if (useDormitoryMeal) "기숙사" else "학생식당") {
                    // 식단이 없는 날은 본문이 한 번만 알린다: 머리의 상태 배지에 같은 말을 되풀이하지 않는다 (D-094(14))
                    if (!useDormitoryMeal && mealStatusNow != null &&
                        mealStatusNow.state != MealServiceState.NO_MENU && mealStatusNow.label.isNotBlank()
                    ) {
                        Surface(
                            shape = DimaShapes.Badge,
                            color = if (homeMealOpen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (homeMealOpen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        ) {
                            Text(
                                text = mealStatusNow.label,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                when {
                    mealLoading -> {
                        LoadingLine("식단을 불러오고 있어요", Modifier.testTag("dashboard_meal_loading"))
                    }
                    useDormitoryMeal && homeDormitoryMeal != null -> {
                        if (homeDormitoryMeal.date != now.toLocalDate()) {
                            Text(
                                text = "${homeDormitoryMeal.date.monthValue}월 ${homeDormitoryMeal.date.dayOfMonth}일 · 다음 식단",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        groupDormitorySections(homeDormitoryMeal.sections).forEachIndexed { index, block ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            val status = mealServiceStatus(homeDormitoryMeal.date, block.hours, now)
                            DormitoryMealPeriodContent(block, status)
                        }
                    }
                    useDormitoryMeal -> {
                        EmptyState("예정된 식단이 없어요", contained = false)
                    }
                    todayMeal != null && todayMeal.menuLines.isNotEmpty() -> {
                        Text(
                            text = todayMeal.menuLines.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    now.dayOfWeek.value >= 6 -> {
                        EmptyState("주말에는 학생식당을 쉬어요", contained = false)
                    }
                    else -> {
                        EmptyState("오늘 등록된 식단이 없어요", contained = false)
                    }
                }
            }
        }

        // 4. 학교 공지 카드 (최근 3건, 행 탭 시 해당 공지로 이동)
        item(key = "notices") {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_notices_card"),
                shape = DimaShapes.Card,
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 공지 카드는 행마다 다른 곳을 열므로 카드 자체는 누를 수 없고, 머리의 전체보기만 버튼이다 (D-094(9))
                    HomeCardHeader(icon = Icons.Default.Campaign, title = "학교 공지", showChevron = false) {
                        TextButton(
                            onClick = { openUrl(context, notices?.sourceUrl ?: OFFICIAL_NOTICE_SOURCE_URL) },
                            modifier = Modifier.semantics { contentDescription = "학교 공지 전체보기" },
                        ) {
                            Text("전체보기")
                        }
                    }

                    val latestNotices = notices?.notices.orEmpty().take(3)
                    if (notices == null) {
                        LoadingLine("공지를 불러오고 있어요", Modifier.testTag("dashboard_notices_loading"))
                    } else if (latestNotices.isEmpty()) {
                        when {
                            // 홈에는 새로고침이 없으므로 다음 행동은 전체보기(학교 누리집)로 안내한다
                            notices.error != null -> ErrorState(
                                message = "공지를 불러오지 못했어요",
                                supporting = "잠시 후 다시 확인하거나 전체보기에서 학교 누리집을 열어 주세요",
                                contained = false,
                                modifier = Modifier.testTag("dashboard_notices_error"),
                            )
                            notices.lastSuccess == null -> LoadingLine("공지를 확인하고 있어요")
                            else -> EmptyState("등록된 공지가 없어요", contained = false)
                        }
                    } else {
                        Column {
                            latestNotices.forEach { notice ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clip(DimaShapes.Small)
                                        .clickable(onClickLabel = "공지 열기", role = Role.Button) { openUrl(context, notice.url) }
                                        .padding(horizontal = 4.dp, vertical = 8.dp)
                                        .testTag("dashboard_notice_${notice.id}"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = notice.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Surface(
                                        shape = DimaShapes.Badge,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ) {
                                        Text(
                                            text = "${notice.date.monthValue}.${notice.date.dayOfMonth}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. 학교 서비스 바로가기 — 수업은 하단 수업 탭과 수업 카드가 이미 열어 주므로 DIMA Portal만 둔다 (D-094(9))
        item(key = "shortcuts") {
            FilledTonalButton(
                onClick = { openUrl(context, "https://portal.dima.ac.kr/") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("DIMA Portal")
            }
        }
    }
}

/**
 * 히어로 수업 카드 안의 보조 수업 줄(오늘의 다음 수업 / 다음 수업일 첫 수업).
 * 두 분기가 같은 문법을 쓰도록 하나로 모은다 (D-056).
 */
@Composable
private fun HeroClassPreviewRow(label: String, detail: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = DimaShapes.Tile,
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = DimaShapes.Badge,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
