package com.example.dimanow.ui

import com.example.dimanow.theme.DimaTheme
import com.example.dimanow.theme.dimaScreenTitleStyle
import com.example.dimanow.calendar.ServiceCalendarStore
import com.example.dimanow.calendar.TermHeadline
import com.example.dimanow.calendar.detail
import com.example.dimanow.calendar.label
import com.example.dimanow.calendar.termHeadline
import com.example.dimanow.sync.ServiceCalendar
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.heading
import java.time.LocalDate
import com.example.dimanow.ui.meal.groupDormitorySections
import com.example.dimanow.ui.motion.AnimatedCountText
import com.example.dimanow.ui.motion.dimaContentSwap
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.style.TextOverflow
import com.example.dimanow.theme.DimaShapes
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
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Add
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
 * Which 식당 Home's 식단 card shows (D-134): the 기숙사 in the 예인관 zone, as before (D-067). A 예인관 resident
 * elsewhere sees the 기숙사 too, except on the main campus while the 학생식당 has yet to serve or is serving today;
 * so at 19:00 off campus or with location off, the open 석식 shows instead of `학생식당 · 운영 종료`.
 */
internal fun homeShowsDormitoryMeal(zone: CampusZoneId, livesInDorm: Boolean, cafeteria: MealServiceState?): Boolean =
    zone == CampusZoneId.YEIN || livesInDorm &&
        !(zone == CampusZoneId.MAIN && (cafeteria == MealServiceState.BEFORE_OPEN || cafeteria == MealServiceState.OPEN))

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
    onAddCourse: () -> Unit = { onNavigateToPage(AppPage.TIMETABLE) },
    updateNotice: (@Composable () -> Unit)? = null,
    serviceCalendar: ServiceCalendar = ServiceCalendarStore.current,
    /** Today's 점호 when Home should show it (엔터관 residents on a 점호 day, D-122); null hides the card. */
    rollCall: com.example.dimanow.dorm.RollCallStatus? = null,
    /** D-128(1): saved card order and hidden cards (keys of [HomeCard]); empty means the default order. */
    cardOrder: List<String> = emptyList(),
    hiddenCards: Set<String> = emptySet(),
    favorites: List<HomeShortcut> = emptyList(),
    onOpenShortcut: (HomeShortcut) -> Unit = {},
    /** Opens the Home editor; null hides the `홈 편집` button (previews and tests). */
    onEditHome: (() -> Unit)? = null,
    /** D-128(3): the student opened 학교 공지 from Home (offers keyword alerts once). */
    onNoticeOpened: () -> Unit = {},
    /** D-132: opens 캠퍼스 지도 on a class's room; null hides the location button (previews and tests). */
    onShowRoom: ((String) -> Unit)? = null,
    /** D-134: the student lives in 예인관 (home base 엔터관 방향), so Home's 식단 card leans to the 기숙사. */
    livesInDorm: Boolean = false,
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
    val locationAccess = rememberLocationAccess()
    val shuttleDepartures = shuttle?.departures.orEmpty()
    // The week's schedule index changes only with the timetable; the minute tick reuses it.
    val shuttleIndex = remember(shuttleDepartures) { guidanceEngine.prepareShuttleSchedule(shuttleDepartures) }
    val shuttleBoard = remember(now, zone, shuttleIndex) {
        guidanceEngine.shuttleBoard(
            now = now,
            originZone = zone,
            index = shuttleIndex,
            purpose = ShuttleBoardPurpose.GENERAL,
        )
    }

    val todayMeal = meal?.days?.firstOrNull { it.date == now.toLocalDate() }
    val homeDormitoryMeal = dormitoryMeal?.homeServiceAt(now)
    val mealStatusNow = meal?.serviceStatusAt(now)
    val useDormitoryMeal = homeShowsDormitoryMeal(zone, livesInDorm, mealStatusNow?.state)
    // D-094: 선택된 식당의 원본이 아직 첫 값을 내보내지 않았으면 불러오는 중으로 본다
    val mealLoading = if (useDormitoryMeal) dormitoryMeal == null else meal == null
    val homeMealOpen = if (useDormitoryMeal) {
        homeDormitoryMeal?.let { day ->
            groupDormitorySections(day.sections).any { mealServiceStatus(day.date, it.hours, now).state == MealServiceState.OPEN }
        } == true
    } else mealStatusNow?.state == MealServiceState.OPEN
    val originName = DisplayVocabulary.originName(zone)

    // D-106: 홈의 제목은 학기 주차(시험 주간·방학 포함)이고, 오늘 날짜는 그 옆에 작게 둔다. 현재 위치는
    // 그 위치가 바꾸는 셔틀 카드 머리에서 '○○ 출발'로 보여 준다 (D-029의 위치 배지를 대체). 설정 톱니는 오른쪽 끝 (D-094)
    val headline = termHeadline(now.toLocalDate(), schedule.termStart, schedule.termEnd, serviceCalendar)
    ScreenScaffold(
        titleContent = { HomeTitle(headline, now.toLocalDate()) },
        modifier = modifier,
        itemSpacing = DimaLayout.sectionGap,
        listTag = "home_list",
    ) {
        // 새 버전 안내는 앱을 열자마자 막는 대화상자 대신 홈 맨 위의 카드로 알린다 (D-102)
        updateNotice?.let { notice -> item(key = "update_notice") { notice() } }

        // 카드마다 키가 있는 지연 항목이라, 분 틱은 바뀐 값을 읽는 카드만 다시 그린다 (D-094)
        if (testMode) item(key = "test_mode") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = DimaShapes.Tile,
                // 오류가 아니라 주의 상태이므로 경고색을 쓴다 (D-104)
                color = DimaTheme.colors.warningContainer,
            ) {
                Text(
                    text = "테스트 모드 · GPS 미반영",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = DimaTheme.colors.onWarningContainer,
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
                // D-121: the briefing's shape mark sits at the end of its top block and stays one composable across
                // every branch, so a class starting, ending or the day going free morphs it instead of replacing it.
                val heroMoodNow = heroMood(
                    hasNextCourse = nextCourse != null,
                    ongoing = nextCourse != null && !nowTime.isBefore(nextCourse.start),
                )
                val termEnded = com.example.dimanow.calendar.AcademicTerms.hasEnded(schedule.termEnd, now.toLocalDate())
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(if (nextCourse != null) 16.dp else 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) {
                            // 브리핑 본문 (수업 일정)
                            if (nextCourse != null) {
                                // D-121: the next class rises in as the finished one leaves, once per class change
                                AnimatedContent(
                                    targetState = nextCourse,
                                    contentKey = { it.id to it.start },
                                    transitionSpec = { dimaContentSwap() },
                                    label = "home_hero_course",
                                ) { course ->
                                    val isOngoing = !nowTime.isBefore(course.start)
                                    // D-127: the chip counts down even hours ahead; the clocks beside it already say when
                                    val startLabel = classCountdownLabel(now, now.toLocalDate().atTime(course.start).atZone(now.zone))
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
                                                    style = MaterialTheme.typography.labelMediumEmphasized.copy(fontFeatureSettings = TABULAR_FIGURES),
                                                    color = if (isOngoing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary,
                                                    modifier = Modifier
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                        .testTag("home_hero_badge"),
                                                )
                                            }
                                            Text(
                                                text = "${course.start.format(TIME)} ~ ${course.end.format(TIME)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                        }
                                        Text(
                                            text = course.name,
                                            style = MaterialTheme.typography.headlineSmallEmphasized,
                                        )
                                        // 비워 둔 강의실·교수는 빈 구분자 없이 건너뛴다
                                        listOf(course.room, course.professor).filter(String::isNotBlank).joinToString(" · ")
                                            .takeIf(String::isNotEmpty)?.let { place ->
                                                // D-132: the location button after the room line opens 캠퍼스 지도 on the room
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = place,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.weight(1f, fill = false),
                                                    )
                                                    if (onShowRoom != null && course.room.isNotBlank()) {
                                                        RoomLocationButton(
                                                            onClick = { onShowRoom(course.room) },
                                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            modifier = Modifier.testTag("home_hero_room_map"),
                                                        )
                                                    }
                                                }
                                            }
                                        // 셔틀 출발 정보는 바로 아래 셔틀 카드가 캡슐로 보여주므로
                                        // 히어로 카드에서 같은 내용을 문장으로 반복하지 않는다 (D-056)
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = when {
                                            // D-099: 학기가 끝났으면 새 학기 설정을 먼저 안내한다
                                            termEnded -> "학기가 끝났어요"
                                            schedule.courses.isEmpty() -> "수업을 추가해 주세요"
                                            guidancePaused -> "오늘은 휴강이에요"
                                            schedule.coursesOn(now.toLocalDate()).isEmpty() -> "오늘은 수업이 없어요"
                                            else -> "오늘 수업이 모두 끝났어요"
                                        },
                                        style = MaterialTheme.typography.titleLargeEmphasized,
                                    )
                                    // 무엇을 하면 무엇을 얻는지 한 줄로 알려 준다 (토스 UX 라이팅: 가능하게 하는 조건을 말한다)
                                    when {
                                        termEnded -> "새 학기 기간을 정하면 수업 안내를 다시 받을 수 있어요."
                                        schedule.courses.isEmpty() -> "추가한 수업에 맞춰 수업 전 알림과 셔틀 안내를 보내 드려요."
                                        else -> null
                                    }?.let {
                                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                            }
                        }
                        HeroMoodMark(heroMoodNow)
                    }

                    if (nextCourse != null) {
                        // 오늘의 다음 수업 표시 (있을 경우)
                        if (upcomingAfterCourse != null) {
                            HeroClassPreviewRow(
                                label = "다음 수업 · ${upcomingAfterCourse.start.format(TIME)}",
                                detail = courseWithRoom(upcomingAfterCourse.name, upcomingAfterCourse.room),
                            )
                        }
                    } else {
                        // D-102: 빈 브리핑은 다음 행동을 버튼으로 바로 건넨다
                        when {
                            termEnded -> Button(shapes = ButtonDefaults.shapes(),
                                onClick = { onNavigateToPage(AppPage.TIMETABLE) },
                                modifier = Modifier.testTag("home_set_term"),
                            ) { Text("새 학기 설정하기") }
                            schedule.courses.isEmpty() -> Button(shapes = ButtonDefaults.shapes(),
                                onClick = onAddCourse,
                                modifier = Modifier.testTag("home_add_course"),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text("수업 추가하기")
                            }
                        }
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
                        if (nextClassPreview != null) {
                            val (date, course) = nextClassPreview
                            val dayLabel = if (date == now.toLocalDate().plusDays(1)) {
                                "내일 첫 수업"
                            } else {
                                "${koreanWeekdayLabel(date.dayOfWeek)} 첫 수업"
                            }
                            HeroClassPreviewRow(
                                label = "$dayLabel · ${course.start.format(TIME)}",
                                detail = courseWithRoom(course.name, course.room),
                            )
                        }
                    }
                }
            }
        }

        // D-128(1): the student's favourites, right under the briefing
        if (favorites.isNotEmpty()) item(key = "favorites") { HomeFavoritesRow(favorites, onOpenShortcut) }

        // D-128(1): each movable card is kept by its key and drawn in the student's order; hidden ones are skipped.
        val homeCards = mutableMapOf<String, androidx.compose.foundation.lazy.LazyListScope.() -> Unit>()

        homeCards["learning"] = {
            // 수업 카드: 카드 전체가 수업 탭을 연다. 로그인 전에는 한 줄 안내로 줄어든다 (D-094(9))
            item(key = "learning") {
                HomeTodaySummary(
                    snapshot = lmsSnapshot, now = now,
                    onOpenCourses = { onNavigateToPage(AppPage.COURSES) },
                    sessionState = lmsSessionState,
                )
            }
        }

        homeCards["academic"] = {
            // D-107: 지금 진행 중이거나 2주 안에 시작하는 학사 일정(시험 주간 포함). 없으면 카드도 없다
            val academicAgenda = serviceCalendar.academicAgenda(now.toLocalDate()).take(4)
            if (academicAgenda.isNotEmpty()) item(key = "academic") {
                HomeAcademicAgendaCard(academicAgenda, now.toLocalDate(), onOpenGrades = rememberGradesOpener())
            }
        }

        homeCards["shuttle"] = {
            // 2. 실시간 셔틀 카드 — 카드 전체가 셔틀 탭을 연다 (D-094(9))
            item(key = "shuttle") {
                val shuttleSuspension = remember(now.toLocalDate()) { guidanceEngine.campusShuttleSuspensionOn(now.toLocalDate()) }
                val shuttleHeader: @Composable (Boolean) -> Unit = { openable ->
                    // D-102: 지금 위치는 그 위치가 바꾸는 이 카드에서 '○○ 출발'로 알린다
                    HomeCardHeader(icon = Icons.Default.DirectionsBus, title = "셔틀", showChevron = openable) {
                        if (zone != CampusZoneId.OUTSIDE) {
                            Surface(shape = DimaShapes.Badge, color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text(
                                    text = "$originName 출발",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).testTag("dashboard_shuttle_origin"),
                                )
                            }
                        }
                    }
                }
                // D-102: 위치 권한이 없어 '캠퍼스 밖'으로 보일 때는 설정으로 보내지 않고 여기서 바로 묻는다.
                // M3 카드는 전체가 한 번 누르는 대상이거나 자기 버튼을 가진 그릇 중 하나이므로, 이때 카드는 누를 수 없다.
                if (zone == CampusZoneId.OUTSIDE && !locationAccess.granted && !shuttleDepartures.isEmpty() && shuttleSuspension == null) {
                    HomeContainerCard(modifier = Modifier.testTag("dashboard_shuttle_card")) {
                        shuttleHeader(false)
                        Text(
                            "위치를 허용하면 있는 곳의 셔틀을 바로 보여 줘요. 위치는 기기 안에서만 써요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("dashboard_location_needed"),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(shapes = ButtonDefaults.shapes(), 
                                onClick = locationAccess.request,
                                modifier = Modifier.testTag("dashboard_allow_location"),
                            ) { Text("위치 허용하기") }
                            TextButton(shapes = ButtonDefaults.shapes(), onClick = { onNavigateToPage(AppPage.SHUTTLE) }) { Text("전체 시간표") }
                        }
                    }
                } else HomeSummaryCard(
                    onClick = { onNavigateToPage(AppPage.SHUTTLE) },
                    onClickLabel = "셔틀 전체 시간표 보기",
                    modifier = Modifier.testTag("dashboard_shuttle_card"),
                ) {
                    shuttleHeader(true)

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
                        // D-099: 공휴일·운행 중지일에는 평일 시간표 대신 미운행을 알린다
                        shuttleSuspension != null -> {
                            Text(
                                "오늘은 셔틀이 운행하지 않아요 · $shuttleSuspension",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("dashboard_shuttle_suspended"),
                            )
                        }
                        zone == CampusZoneId.OUTSIDE -> {
                            // 위치를 쓸 수 있는데 캠퍼스 밖이면, 무엇이 언제 나타나는지만 알려 준다 (D-102)
                            Text(
                                "캠퍼스에 오면 바로 보여 줘요",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("dashboard_outside"),
                            )
                        }
                        // D-126(1): 한 목적지 줄의 남은 시간은 모두 한 크기다. 그 크기는 줄 폭에서 미리 세므로 폭을 먼저 받는다.
                        destinations.isNotEmpty() -> androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val rowWidth = maxWidth
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
                                    // D-127/D-130: only a 첫차 more than an hour off becomes one quiet tile with its clock large
                                    // (the ones after it show only the time); any other departure counts down in minutes.
                                    val nextIsFirst = upcoming.firstOrNull()?.let { next ->
                                        annotated.firstOrNull { it.departure.time == next.departure.time }?.isFirst
                                    } == true
                                    val farAhead = nextIsFirst && (upcoming.firstOrNull()?.remainingMinutes ?: 0L) > 60
                                    val capsules = upcoming.mapIndexed { index, countdown ->
                                        val time = countdown.departure.time
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
                                        val markers = listOfNotNull(serviceLabel, boardingStopLabel)
                                        when {
                                            !farAhead -> HomeDeparture(
                                                countdown = departureCountdownLabel(countdown.remainingMinutes, emptyList()),
                                                clock = time.format(TIME),
                                                markers = markers,
                                                isNext = index == 0,
                                                isLastService = isLastService,
                                            )
                                            index == 0 -> HomeDeparture(
                                                countdown = time.format(TIME),
                                                clock = markers.joinToString(" · ").ifEmpty { "다음 출발" },
                                                markers = emptyList(),
                                                isNext = false,
                                                isLastService = isLastService,
                                            )
                                            else -> HomeDeparture(
                                                countdown = "",
                                                clock = time.format(TIME),
                                                markers = emptyList(),
                                                isNext = false,
                                                isLastService = isLastService,
                                                timeOnly = true,
                                            )
                                        }
                                    }
                                    val destinationTag = "${destName}행"
                                    val countdownSize = rememberHomeCountdownSize(rowWidth, destinationTag, capsules)

                                    // D-124: 목적지 태그 오른쪽에 다음 출발 칸을 같은 줄로 붙여 세로 공간을 아낀다. 칸은 줄 폭을
                                    // 똑같이 나누고, 남은 시간 글자는 줄의 모든 칸에 맞는 한 크기로 줄어든다(줄바꿈 없음, D-126(1)).
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // 항상 크게 표시되는 목적지 태그
                                        Surface(
                                            shape = DimaShapes.Badge,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                        ) {
                                            Text(
                                                text = destinationTag,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                        }

                                        if (capsules.isNotEmpty()) {
                                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                capsules.forEachIndexed { index, departure ->
                                                    if (departure.timeOnly) {
                                                        DepartureTimeTile(
                                                            clock = departure.clock,
                                                            modifier = Modifier.weight(1f).testTag("home_departure_${destZone.name}_$index"),
                                                        )
                                                        return@forEachIndexed
                                                    }
                                                    // D-124: the same cell as the shuttle tab's strip — countdown large, clock and markers small
                                                    val (container, content) = shuttleCapsuleColors(isNearest = departure.isNext, isLastService = departure.isLastService)
                                                    DepartureCapsule(
                                                        countdown = departure.countdown,
                                                        clock = departure.clock,
                                                        markers = departure.markers,
                                                        containerColor = container,
                                                        contentColor = content,
                                                        isNext = departure.isNext,
                                                        countdownSize = countdownSize,
                                                        modifier = Modifier.weight(1f).testTag("home_departure_${destZone.name}_$index"),
                                                    )
                                                }
                                            }
                                        } else {
                                            // 운행 종료 상태 (첫차 및 막차 시각 함께 표시)
                                            val firstDepartureTime = annotated.firstOrNull()?.departure?.time
                                            val lastDepartureTime = annotated.lastOrNull()?.departure?.time
                                            Surface(
                                                shape = DimaShapes.Tile,
                                                color = DimaTheme.colors.cardInner,
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                FlowRow(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                                    itemVerticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Text(
                                                        text = "오늘 운행이 끝났어요",
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
        }

        // D-122: 엔터관으로 돌아가는 학생에게는 점호 날 점호 안내와 외출·외박 신청을 홈에서 바로 준다
        homeCards["roll_call"] = {
            rollCall?.let { status ->
                item(key = "roll_call") {
                    HomeRollCallCard(rollCall = status, time = nowTime, onOpenDorm = { onNavigateToPage(AppPage.DORM) })
                }
            }
        }

        homeCards["meal"] = {
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
        }

        homeCards["notices"] = {
            // 4. 학교 공지 카드 (최근 3건, 행 탭 시 해당 공지로 이동)
            item(key = "notices") {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dashboard_notices_card"),
                    shape = DimaShapes.Card,
                    colors = CardDefaults.elevatedCardColors(containerColor = DimaTheme.colors.card),
                ) {
                    // D-122: 머리의 전체보기 버튼(48dp)과 공지 행(48dp)이 이미 위아래 여백을 가지므로, 카드 여백은 위아래를
                    // 줄여 다른 홈 카드와 같은 간격으로 보이게 한다
                    Column(
                        Modifier.padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // 공지 카드는 행마다 다른 곳을 열므로 카드 자체는 누를 수 없고, 머리의 전체보기만 버튼이다 (D-094(9))
                        HomeCardHeader(icon = Icons.Default.Campaign, title = "학교 공지", showChevron = false) {
                            TextButton(shapes = ButtonDefaults.shapes(), 
                                onClick = {
                                    onNoticeOpened()
                                    openUrl(context, notices?.sourceUrl ?: OFFICIAL_NOTICE_SOURCE_URL)
                                },
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
                                notices.lastSuccess == null -> LoadingLine("인터넷에 연결되면 공지를 받아요")
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
                                            .clickable(onClickLabel = "공지 열기", role = Role.Button) {
                                                onNoticeOpened()
                                                openUrl(context, notice.url)
                                            }
                                            .padding(end = 4.dp, top = 8.dp, bottom = 8.dp)
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
                                            color = DimaTheme.colors.cardInner,
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
        }

        homeCardOrder(cardOrder, hiddenCards).forEach { card -> homeCards[card.key]?.invoke(this) }
        onEditHome?.let { edit -> item(key = "home_edit") { HomeEditButton(edit) } }
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
            // D-104: tertiary is the contrasting accent on the primaryContainer hero card
            Surface(
                shape = DimaShapes.Badge,
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
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

/** `수업명 (강의실)`, without empty brackets when the room was left blank. */
internal fun courseWithRoom(name: String, room: String): String = if (room.isBlank()) name else "$name ($room)"

private val TIME = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Home's title (D-106): the semester week, or the vacation countdown, at the screen-title size with the
 * exam week or countdown in primary, then today's date in a quieter, smaller style on the same line.
 */
@Composable
private fun HomeTitle(headline: TermHeadline, today: LocalDate) {
    val lead = when (headline) {
        is TermHeadline.InTerm -> "${headline.week}주차"
        is TermHeadline.Vacation -> "방학"
    }
    val detail = headline.detail
    val title = buildAnnotatedString {
        append(lead)
        if (detail != null) {
            append(" · ")
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append(detail) }
        }
    }
    val spokenDate = "${today.monthValue}월 ${today.dayOfMonth}일 ${koreanWeekdayLabel(today.dayOfWeek)}"
    Row(
        modifier = Modifier
            .testTag("home_title")
            .semantics(mergeDescendants = true) {
                heading()
                contentDescription = "${headline.label}, $spokenDate"
            },
    ) {
        Text(title, style = dimaScreenTitleStyle(), maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline())
        Spacer(Modifier.width(10.dp))
        Text(
            koreanDateLabel(today),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alignByBaseline().weight(1f, fill = false),
        )
    }
}
