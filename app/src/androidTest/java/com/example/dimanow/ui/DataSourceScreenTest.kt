package com.example.dimanow.ui

import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.MealDay
import com.example.dimanow.domain.MealValidationState
import com.example.dimanow.domain.ShuttleDeparture
import com.example.dimanow.meal.MealData
import com.example.dimanow.meal.MealRefreshResult
import com.example.dimanow.meal.MealRefreshTrigger
import com.example.dimanow.meal.MealSource
import com.example.dimanow.meal.DormitoryMealData
import com.example.dimanow.meal.DormitoryMealDay
import com.example.dimanow.meal.DormitoryMealSection
import com.example.dimanow.shuttle.ShuttleData
import com.example.dimanow.shuttle.ShuttleRefreshResult
import com.example.dimanow.shuttle.ShuttleSource
import com.example.dimanow.theme.DIMANowTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test

class DataSourceScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun nearestDeparturesKeepClockTimeAndOnlyCountDownWithinSixtyMinutes() {
        val now = ZonedDateTime.of(2026, 8, 31, 9, 30, 0, 0, ZoneId.of("Asia/Seoul"))
        val departures = listOf(
            departure("A", "main", LocalTime.of(8, 0), CampusZoneId.MAIN, CampusZoneId.YEIN, now.dayOfWeek),
            departure("A", "main", LocalTime.of(10, 0), CampusZoneId.MAIN, CampusZoneId.YEIN, now.dayOfWeek),
            departure("A", "main", LocalTime.of(11, 0), CampusZoneId.MAIN, CampusZoneId.YEIN, now.dayOfWeek),
        )
        composeRule.setContent {
            DIMANowTheme(darkTheme = false) {
                ShuttleScreen(FakeShuttleSource(departures), CampusZoneId.MAIN, now = now)
            }
        }

        // D-093/D-124: the next two cells show the clock (with markers) on top and the countdown (60분 이내) beneath
        assertInDeparture(0, "30분 후")
        assertInDeparture(0, "10:00")
        assertInDeparture(1, "다음 출발")
        assertInDeparture(1, "11:00 · 막차")
        composeRule.onNodeWithText("90분 후", substring = true).assertDoesNotExist()
    }

    @Test
    fun nearestLastDepartureRetainsItsClockCountdownAndLastServiceWarning() {
        val now = ZonedDateTime.of(2026, 8, 31, 8, 30, 0, 0, ZoneId.of("Asia/Seoul"))
        val departures = listOf(
            departure("A", "main", LocalTime.of(8, 0), CampusZoneId.MAIN, CampusZoneId.YEIN, now.dayOfWeek),
            departure("A", "main", LocalTime.of(9, 0), CampusZoneId.MAIN, CampusZoneId.YEIN, now.dayOfWeek),
        )
        composeRule.setContent {
            DIMANowTheme(darkTheme = false) {
                ShuttleScreen(FakeShuttleSource(departures), CampusZoneId.MAIN, now = now)
            }
        }

        assertInDeparture(0, "30분 후")
        assertInDeparture(0, "09:00 · 막차")
        composeRule.onNodeWithContentDescription("다음 출발 09:00, 30분 후, 막차").assertExists()
    }

    @Test
    fun shuttleCacheDescribesRawWeeklyRowsAndUniqueUserDepartureSlots() {
        val serviceDay = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).dayOfWeek
        val departures = listOf(
            departure("A", "headquarters-a", LocalTime.of(19, 0), CampusZoneId.MAIN, CampusZoneId.YEIN, serviceDay),
            departure("B", "headquarters-b", LocalTime.of(19, 0), CampusZoneId.MAIN, CampusZoneId.YEIN, serviceDay),
            departure("B", "headquarters", LocalTime.of(19, 30), CampusZoneId.MAIN, CampusZoneId.YEIN, serviceDay),
            departure("C", "one-room", LocalTime.of(19, 0), CampusZoneId.ONE_ROOM, CampusZoneId.MAIN, serviceDay),
        )
        // Snapshot the fake sources outside composition (Compose 1.12 lint: no StateFlow.value in composition).
        val shuttleData = FakeShuttleSource(departures).data.value
        val mealData = FakeMealSource().data.value
        composeRule.setContent {
            DataAndSourcesCard(shuttleData = shuttleData, mealData = mealData)
        }

        val expected = "기기 동기화: 2026년 8월 26일 21:00 KST"
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(expected).assertExists()
        composeRule.onNodeWithText("서버 게시: 2026년 8월 26일 20:30 KST").assertExists()
        composeRule.onNodeWithText("공식 주간 시간표 4행 · 사용자 출발 슬롯 3개").assertExists()
        composeRule.onNodeWithText("캠퍼스 구역 CAMPUS_ZONES_V2_USER_2026_08_27 · © OpenStreetMap contributors").assertExists()
    }

    @Test
    fun diagnosticsCardShowsLoadingBeforeSourcesEmitAndLabelsRawErrorsAsDiagnostic() {
        var shuttle by mutableStateOf<ShuttleData?>(null)
        composeRule.setContent { DataAndSourcesCard(shuttleData = shuttle, mealData = null) }

        // D-094(14): no "기록 없음" before the cache has answered
        composeRule.onNodeWithTag("data_sources_shuttle_loading", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("data_sources_meal_loading", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("기록 없음", substring = true).assertDoesNotExist()

        composeRule.runOnIdle {
            shuttle = ShuttleData(emptyList(), null, null, "HTTP 503", "https://www.dima.ac.kr/?p=97", null)
        }
        composeRule.onNodeWithText("진단 · 마지막 동기화 오류: HTTP 503").assertExists()
        composeRule.onNodeWithTag("data_sources_shuttle_loading", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun pullingTheShuttleListRefreshesOnceAndReportsTheResult() {
        val source = BlockingShuttleSource()
        composeRule.setContent {
            ShuttleScreen(shuttleSource = source, currentZone = CampusZoneId.OUTSIDE)
        }

        // D-058/D-103: 당겨서 새로고침에 더해 상단 바에도 같은 새로고침 버튼이 있다
        composeRule.onNodeWithContentDescription("셔틀 새로고침").assertIsEnabled()
        composeRule.onNodeWithTag("shuttle_list").performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 600f)
        }

        composeRule.waitUntil(5_000) { source.started.isCompleted }
        // 새로고침이 도는 동안 두 번째 당김은 또 다른 요청을 만들지 않는다
        composeRule.onNodeWithTag("shuttle_list").performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 600f)
        }

        source.release.complete(Unit)
        // D-094: 사용자가 당긴 새로고침만 쉬운 말의 스낵바로 알린다
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("셔틀 시간표를 새로 받았어요").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("2건 저장 완료").assertDoesNotExist()
        assertEquals(1, source.refreshCount)
    }

    @Test
    fun theBarRefreshActionDoesWhatPullingDoesAndRestsWhileItRuns() {
        val source = BlockingShuttleSource()
        composeRule.setContent {
            ShuttleScreen(shuttleSource = source, currentZone = CampusZoneId.OUTSIDE)
        }

        // D-103: M3 asks for a one-tap alternative to pull-to-refresh.
        composeRule.onNodeWithContentDescription("셔틀 새로고침").performClick()
        composeRule.waitUntil(5_000) { source.started.isCompleted }
        composeRule.onNodeWithContentDescription("셔틀 새로고침").assertIsNotEnabled()

        source.release.complete(Unit)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("셔틀 시간표를 새로 받았어요").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("셔틀 새로고침").assertIsEnabled()
        assertEquals(1, source.refreshCount)
    }

    @Test
    fun shuttleShowsLoadingUntilTheCacheEmitsInsteadOfNoServiceOrRefreshPrompts() {
        val source = object : ShuttleSource {
            override val data = flow<ShuttleData> { awaitCancellation() }
            override suspend fun refresh() = ShuttleRefreshResult.Failure("unused", 0)
        }
        composeRule.setContent {
            ShuttleScreen(shuttleSource = source, currentZone = CampusZoneId.MAIN)
        }

        composeRule.onNodeWithTag("shuttle_loading", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("운행하지 않아요", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("새로고침", substring = true).assertDoesNotExist()
    }

    @Test
    fun mealShowsLoadingUntilTheCacheEmitsInsteadOfAnEmptyWeek() {
        val source = object : MealSource {
            override val data = flow<MealData> { awaitCancellation() }
            override val dormitoryData = flow<DormitoryMealData> { awaitCancellation() }
            override suspend fun refresh() = MealRefreshResult.Failure("unused")
            override suspend fun refreshIfDue(trigger: MealRefreshTrigger, now: Instant): MealRefreshResult? = null
        }
        composeRule.setContent {
            MealScreen(mealSource = source, today = LocalDate.parse("2026-08-27"))
        }

        composeRule.onNodeWithTag("meal_loading", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("등록된 식단이 없어요").assertDoesNotExist()
        composeRule.onNodeWithText("이번 주 식단을 아직 불러오지 못했어요").assertDoesNotExist()
        composeRule.onNodeWithText("기숙사").performClick()
        composeRule.onNodeWithTag("meal_loading", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("이번 주 기숙사 식단이 아직 없어요").assertDoesNotExist()
    }

    @Test
    fun automaticMealRefreshOnTabVisitDoesNotShowASnackbar() {
        var automaticRefreshes = 0
        val source = object : MealSource {
            override val data = MutableStateFlow(MealData(emptyList(), null, null, null, "https://www.dima.ac.kr/?p=1", null, null))
            override suspend fun refresh() = MealRefreshResult.Success(LocalDate.parse("2026-08-24"), Instant.parse("2026-08-27T01:00:00Z"))
            override suspend fun refreshIfDue(trigger: MealRefreshTrigger, now: Instant): MealRefreshResult? {
                automaticRefreshes++
                return refresh()
            }
        }
        composeRule.setContent {
            MealScreen(mealSource = source, today = LocalDate.parse("2026-08-27"))
        }

        composeRule.waitUntil(5_000) { automaticRefreshes > 0 }
        composeRule.waitForIdle()
        // D-094: 자동 새로고침 결과는 스낵바로 알리지 않는다
        composeRule.onNodeWithText("식단을 새로 받았어요", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("저장 완료", substring = true).assertDoesNotExist()
    }

    @Test
    fun shuttleKeepsDataDetailsOutOfTheScreenAndHasNoTopRefreshButton() {
        val serviceDay = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).dayOfWeek
        composeRule.setContent {
            ShuttleScreen(
                shuttleSource = FakeShuttleSource(
                    listOf(
                        departure("A", "headquarters", LocalTime.of(8, 10), CampusZoneId.MAIN, CampusZoneId.YEIN, serviceDay),
                    ),
                ),
                currentZone = CampusZoneId.MAIN,
            )
        }

        composeRule.onNodeWithText("셔틀 데이터 상태").assertDoesNotExist()
        composeRule.onNodeWithText("셔틀 데이터 관리").assertDoesNotExist()
        composeRule.onNodeWithText("요일 선택").assertDoesNotExist()
    }

    @Test
    fun userSeesEveryShuttleDayAndOriginWithoutChoosingFilters() {
        val serviceDay = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).dayOfWeek
        val departures = listOf(
            departure("A", "headquarters-a", LocalTime.of(8, 10), CampusZoneId.MAIN, CampusZoneId.YEIN, serviceDay),
            departure("B", "headquarters-b", LocalTime.of(8, 10), CampusZoneId.MAIN, CampusZoneId.YEIN, serviceDay),
            departure("B", "headquarters", LocalTime.of(8, 40), CampusZoneId.MAIN, CampusZoneId.YEIN, serviceDay),
            departure("B", "headquarters", LocalTime.of(9, 10), CampusZoneId.MAIN, CampusZoneId.YEIN, serviceDay),
            departure("A", "headquarters", LocalTime.of(8, 20), CampusZoneId.MAIN, CampusZoneId.ONE_ROOM, serviceDay),
            departure("B", "headquarters", LocalTime.NOON, CampusZoneId.MAIN, CampusZoneId.YEIN, serviceDay.plus(1)),
        )
        composeRule.setContent {
            ShuttleScreen(
                shuttleSource = FakeShuttleSource(departures),
                currentZone = CampusZoneId.MAIN,
                // 공식 저녁 본관 운행으로 전환되기 전이면서 모든 fixture 운행이 끝난
                // 시각을 사용해, 본관 그룹 헤더와 전체 시간표를 안정적으로 검증한다.
                now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"))
                    .withHour(18).withMinute(0).withSecond(0).withNano(0),
            )
        }

        composeRule.onNodeWithText("셔틀버스").assertExists()
        composeRule.onNodeWithText("본관").assertExists()
        composeRule.onNodeWithText("현재 위치").assertExists()
        composeRule.onNodeWithText("엔터관행").assertExists()
        composeRule.onNodeWithText("원룸촌행").assertExists()
        composeRule.onNodeWithText("본관 → 엔터관").assertDoesNotExist()
        composeRule.onNodeWithText("본관 → 원룸촌").assertDoesNotExist()
        composeRule.onNodeWithText("08:10–09:10", substring = true).assertExists()
        // D-093/D-124: 하루 전체가 펼치기 없이 한 줄 띠로 항상 보이고, 운행이 끝나면 띠 끝에 그 사실을 둔다
        composeRule.onAllNodesWithText("오늘 운행이 끝났어요").onFirst().assertExists()
        composeRule.onNodeWithContentDescription("지난 시간 09:10, 막차").assertExists()
        composeRule.onNode(
            hasScrollToNodeAction() and !hasTestTag("shuttle_list") and hasAnyDescendant(hasContentDescription("지난 시간 09:10, 막차")),
        ).performScrollToNode(hasContentDescription("지난 시간 08:10, 첫차"))
        composeRule.onNodeWithContentDescription("지난 시간 08:10, 첫차").assertExists()
    }

    @Test
    fun mainGroupAndDepartureLabelsSwitchToStadiumForOfficialEveningService() {
        val now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"))
            .withHour(19).withMinute(30).withSecond(0).withNano(0)
        val departures = listOf(
            departure("B", "university-headquarters", LocalTime.of(19, 20), CampusZoneId.MAIN, CampusZoneId.YEIN, now.dayOfWeek),
            departure("B-evening", "stadium-stop", LocalTime.of(19, 35), CampusZoneId.MAIN, CampusZoneId.YEIN, now.dayOfWeek),
            departure("B-evening", "stadium-stop", LocalTime.of(20, 0), CampusZoneId.MAIN, CampusZoneId.YEIN, now.dayOfWeek),
        )
        composeRule.setContent {
            ShuttleScreen(
                shuttleSource = FakeShuttleSource(departures),
                currentZone = CampusZoneId.MAIN,
                now = now,
            )
        }

        // D-020/D-090: 그룹 제목은 본관, 운동장 탑승은 캡슐과 시간표 칩에 표시한다
        composeRule.onNodeWithText("본관").assertExists()
        assertInDeparture(0, "5분 후")
        assertInDeparture(0, "19:35 · 운동장 전환")
        assertInDeparture(1, "30분 후")
        assertInDeparture(1, "20:00 · 막차 · 운동장")
    }

    @Test
    fun timetableChipsAreTallEnoughAndStateTheirMeaningNotJustColour() {
        val now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"))
            .withHour(19).withMinute(30).withSecond(0).withNano(0)
        val departures = listOf(
            departure("B", "university-headquarters", LocalTime.of(19, 20), CampusZoneId.MAIN, CampusZoneId.YEIN, now.dayOfWeek),
            departure("B-evening", "stadium-stop", LocalTime.of(19, 35), CampusZoneId.MAIN, CampusZoneId.YEIN, now.dayOfWeek),
            departure("B-evening", "stadium-stop", LocalTime.of(20, 0), CampusZoneId.MAIN, CampusZoneId.YEIN, now.dayOfWeek),
        )
        composeRule.setContent {
            DIMANowTheme {
                ShuttleScreen(
                    shuttleSource = FakeShuttleSource(departures),
                    currentZone = CampusZoneId.MAIN,
                    now = now,
                )
            }
        }

        // D-094(10): past, next, stadium-switch and last-departure meaning is spoken, and every chip is at least 32dp.
        composeRule.onNodeWithContentDescription("지난 시간 19:20", substring = true)
            .assertExists().assertHeightIsAtLeast(32.dp)
        composeRule.onNodeWithContentDescription("다음 출발 19:35, 5분 후, 운동장 전환")
            .assertExists().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("20:00, 30분 후, 막차, 운동장")
            .assertExists().assertHeightIsAtLeast(48.dp)
        assertInDeparture(0, "19:35 · 운동장 전환")
    }

    @Test
    fun mealDaySelectorExposesSelectionAndToday() {
        composeRule.setContent {
            MealScreen(
                mealSource = FakeMealSource(listOf(mealDay("2026-08-24", "제육볶음"))),
                today = LocalDate.parse("2026-08-27"),
            )
        }

        // D-094(11): the shared selector reports the selected cell and "오늘" as state.
        composeRule.onNodeWithTag("meal_day_THURSDAY")
            .assertIsSelected()
            .assert(hasStateDescription("오늘, 선택됨"))
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("meal_day_MONDAY").assertIsNotSelected().performClick().assertIsSelected()
        composeRule.onNodeWithTag("meal_day_THURSDAY")
            .assertIsNotSelected()
            .assert(hasStateDescription("오늘"))
        composeRule.onNodeWithText("8월 24일 월요일").assertExists()
    }

    @Test
    fun mealOpensOnTodayAndTheHeaderDaySelectorSwitchesTheDayWithoutScrolling() {
        val meals = listOf(
            mealDay("2026-08-21", "이전 식단"),
            mealDay("2026-08-24", "제육볶음", "미역국"),
            mealDay("2026-08-25", "돈가스"),
            mealDay("2026-08-28", "김치볶음밥"),
            mealDay("2026-08-31", "다음 식단"),
        )
        composeRule.setContent {
            MealScreen(
                mealSource = FakeMealSource(meals),
                today = LocalDate.parse("2026-08-27"),
            )
        }

        // D-057: 주간 5일을 한 화면에 밀어 넣는 대신 오늘 하루부터 보여준다
        composeRule.onNodeWithText("8월 27일 목요일").assertExists()
        composeRule.onNodeWithText("등록된 식단이 없어요").assertExists()

        // 요일 선택기는 헤더에 고정돼 스크롤 없이 닿는다
        composeRule.onNodeWithTag("meal_day_MONDAY").performClick()
        composeRule.onNodeWithText("8월 24일 월요일").assertExists()
        // 메뉴는 " · "로 이어붙인 한 줄이 아니라 항목별 줄로 읽힌다
        composeRule.onNodeWithText("제육볶음").assertExists()
        composeRule.onNodeWithText("미역국").assertExists()

        // 이번 주 바깥 날짜는 요일 선택기로 닿을 수 없다
        composeRule.onAllNodesWithText("이전 식단").assertCountEquals(0)
        composeRule.onAllNodesWithText("다음 식단").assertCountEquals(0)
    }

    @Test
    fun dormitoryTabShowsUploadOnlyWhenTheCurrentWeekIsMissing() {
        composeRule.setContent {
            MealScreen(
                mealSource = FakeMealSource(),
                today = LocalDate.parse("2026-08-27"),
            )
        }

        composeRule.onNodeWithText("본관 학생식당").assertExists()
        composeRule.onNodeWithText("기숙사").performClick()
        composeRule.onNodeWithText("식단표 사진 올리기").assertExists()
        // D-056: 주간 전체가 비면 같은 빈 카드 5장 대신 이유와 본문 CTA가 있는 안내 카드 하나만 남는다
        composeRule.onNodeWithTag("dormitory_week_empty").assertExists()
        composeRule.onNodeWithText("이번 주 기숙사 식단이 아직 없어요").assertExists()
        composeRule.onNodeWithTag("dormitory_week_empty_upload").assertExists()
        composeRule.onAllNodesWithText("등록된 식단 없음").assertCountEquals(0)
    }

    @Test
    fun currentDormitoryWeekKeepsEveryMealSectionAndHidesUpload() {
        val dormitoryDays = listOf(
            DormitoryMealDay(
                date = LocalDate.parse("2026-08-24"),
                sections = listOf(
                    DormitoryMealSection("조식", "08:00~09:30", listOf("떡국", "쌀밥")),
                    DormitoryMealSection("간편식", null, listOf("시리얼")),
                    DormitoryMealSection("중식", "12:00~14:00", listOf("미역국", "오징어무침")),
                    DormitoryMealSection("석식", "18:00~19:30", listOf("콩나물불고기")),
                ),
                sourceImageUrl = "https://example.invalid/dorm.jpg",
            ),
        )
        composeRule.setContent {
            MealScreen(
                mealSource = FakeMealSource(dormitoryDays = dormitoryDays),
                today = LocalDate.parse("2026-08-27"),
            )
        }

        composeRule.onNodeWithText("기숙사").performClick()
        composeRule.onNodeWithTag("meal_day_MONDAY").performClick()

        // D-057: 식사 시간대별 카드 — 이름과 운영시간이 한 문자열로 붙어 있지 않다
        composeRule.onNodeWithText("조식").assertExists()
        composeRule.onNodeWithText("08:00~09:30 · 운영 종료").assertExists()
        composeRule.onNodeWithText("떡국").assertExists()
        composeRule.onNodeWithText("쌀밥").assertExists()
        composeRule.onNodeWithText("중식").assertExists()
        composeRule.onNodeWithText("석식").assertExists()
        // 운영시간이 없는 코너는 앞선 식사 카드 안에 남아 사라지지 않는다
        // 간편식은 "하나 선택" 묶음으로 보이고, 라면 같은 코너는 제목 없이 메뉴만 보인다
        composeRule.onNodeWithText("간편식 · 하나 선택").assertExists()
        composeRule.onNodeWithText("시리얼").assertExists()
        composeRule.onNodeWithText("식단표 사진 올리기").assertDoesNotExist()
    }

    @Test
    fun dormitoryUploadRefreshesFirstAndStopsWhenTheServerAlreadyHasThisWeek() {
        val source = DuplicateDormitoryMealSource()
        composeRule.setContent {
            MealScreen(mealSource = source, today = LocalDate.parse("2026-08-27"))
        }

        composeRule.onNodeWithText("기숙사").performClick()
        composeRule.onNodeWithText("식단표 사진 올리기").performClick()

        composeRule.waitUntil(5_000) { source.refreshCount == 1 }
        composeRule.onNodeWithText("사진 선택").assertDoesNotExist()
        composeRule.onNodeWithText("이번 주 기숙사 식단을 불러왔어요").assertExists()
        composeRule.onNodeWithText("식단표 사진 올리기").assertDoesNotExist()
    }

    @Test
    fun missingDormitoryWeekOffersOnlyPhotoOrCameraAfterRefresh() {
        val source = FakeMealSource()
        composeRule.setContent {
            MealScreen(mealSource = source, today = LocalDate.parse("2026-08-27"))
        }

        composeRule.onNodeWithText("기숙사").performClick()
        composeRule.onNodeWithText("식단표 사진 올리기").performClick()

        composeRule.onNodeWithText("사진 선택").assertExists()
        composeRule.onNodeWithText("카메라 촬영").assertExists()
    }

    @Test
    fun todaysMealExplainsThatServiceHasNotOpenedYet() {
        composeRule.setContent {
            MealScreen(
                mealSource = FakeMealSource(listOf(mealDay("2026-08-27", "제육볶음"))),
                today = LocalDate.parse("2026-08-27"),
                nowTime = LocalTime.of(11, 29),
            )
        }

        composeRule.onNodeWithText("11:30~14:00").assertExists()
        composeRule.onNodeWithText("운영 전 · 11:30부터").assertExists()
        composeRule.onNodeWithText("제육볶음").assertExists()
    }

    @Test
    fun mealKeepsDataDetailsOutOfTheScreenAndOffersATapRefresh() {
        composeRule.setContent {
            MealScreen(
                mealSource = FakeMealSource(listOf(mealDay("2026-08-24", "제육볶음"))),
                today = LocalDate.parse("2026-08-27"),
            )
        }

        // D-103: the bar carries the one-tap refresh; data details still stay in Settings
        composeRule.onNodeWithContentDescription("식단 새로고침").assertExists()
        composeRule.onNodeWithText("식단 데이터 상태").assertDoesNotExist()
        composeRule.onNodeWithText("식단 데이터 관리").assertDoesNotExist()
    }

    @Test
    fun mealRefreshExplainsThatTheNewWeekHasNotBeenPublishedYet() {
        composeRule.setContent {
            MealScreen(
                mealSource = object : MealSource {
                    override val data = MutableStateFlow(MealData(emptyList(), null, null, null, "https://www.dima.ac.kr/?p=1", null, null))
                    override suspend fun refresh() = MealRefreshResult.NotPublishedYet
                },
                today = LocalDate.parse("2026-08-31"),
            )
        }

        composeRule.onNodeWithTag("meal_list").performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 600f)
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("아직 새 식단이 올라오지 않았어요").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** The large cells merge their lines for screen readers (D-124), so the lines are read from the unmerged tree. */
    private fun assertInDeparture(index: Int, text: String) {
        composeRule.onNode(hasText(text) and hasAnyAncestor(hasTestTag("next_departure_$index")), useUnmergedTree = true).assertExists()
    }

    private fun timetableRowContaining(text: String) =
        composeRule.onNode(hasScrollToNodeAction() and !hasTestTag("shuttle_list") and hasAnyDescendant(hasText(text)))

    private fun departure(
        route: String,
        stop: String,
        time: LocalTime,
        origin: CampusZoneId,
        destination: CampusZoneId,
        serviceDay: DayOfWeek = DayOfWeek.MONDAY,
    ) = ShuttleDeparture(route, stop, "DIRECTION", serviceDay, time, origin, destination)

    private fun mealDay(date: String, vararg lines: String) = MealDay(
        date = LocalDate.parse(date),
        menuLines = lines.toList(),
        hours = "11:30~14:00",
        sourceUrl = "https://www.dima.ac.kr/?p=1",
        sourceImageUrl = "https://example.invalid/menu.jpg",
        validationState = MealValidationState.VALID,
    )

    private class FakeShuttleSource(departures: List<ShuttleDeparture>) : ShuttleSource {
        override val data = MutableStateFlow(
            ShuttleData(
                departures,
                Instant.parse("2026-08-26T12:00:10Z"),
                Instant.parse("2026-08-26T12:00:10Z"),
                null,
                "https://www.dima.ac.kr/?p=97",
                null,
                Instant.parse("2026-08-26T11:30:00Z"),
            ),
        )
        override suspend fun refresh() = ShuttleRefreshResult.Success(data.value.departures.size, Instant.parse("2026-08-26T12:00:10Z"))
    }

    private class FakeMealSource(
        days: List<MealDay> = emptyList(),
        dormitoryDays: List<DormitoryMealDay> = emptyList(),
    ) : MealSource {
        override val data = MutableStateFlow(MealData(days, null, null, null, "https://www.dima.ac.kr/?p=1", null, "11:30~14:00"))
        override val dormitoryData = MutableStateFlow(DormitoryMealData(dormitoryDays, null, null, null))
        override suspend fun refresh() = MealRefreshResult.Failure("unused")
    }

    private class DuplicateDormitoryMealSource : MealSource {
        override val data = MutableStateFlow(MealData(emptyList(), null, null, null, "https://www.dima.ac.kr/?p=1", null, null))
        override val dormitoryData = MutableStateFlow(DormitoryMealData(emptyList(), null, null, null))
        var refreshCount = 0
        override suspend fun refresh() = MealRefreshResult.Failure("unused")
        override suspend fun refreshDormitory(): MealRefreshResult {
            refreshCount++
            dormitoryData.value = DormitoryMealData(
                listOf(DormitoryMealDay(LocalDate.parse("2026-08-24"), listOf(DormitoryMealSection("중식", null, listOf("제육볶음"))), "https://example.invalid/dorm.jpg")),
                Instant.parse("2026-08-27T01:00:00Z"),
                Instant.parse("2026-08-27T01:00:00Z"),
                null,
            )
            return MealRefreshResult.Success(LocalDate.parse("2026-08-24"), Instant.parse("2026-08-27T01:00:00Z"))
        }
    }

    private class BlockingShuttleSource : ShuttleSource {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var refreshCount = 0
        override val data = MutableStateFlow(
            ShuttleData(emptyList(), null, null, null, "https://www.dima.ac.kr/?p=97", null),
        )
        override suspend fun refresh(): ShuttleRefreshResult {
            refreshCount++
            started.complete(Unit)
            release.await()
            return ShuttleRefreshResult.Success(2, Instant.parse("2026-08-26T12:00:10Z"))
        }
    }

}
