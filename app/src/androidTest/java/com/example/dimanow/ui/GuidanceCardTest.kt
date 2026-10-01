package com.example.dimanow.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.DefaultSchedule
import com.example.dimanow.domain.ShuttleDeparture
import com.example.dimanow.meal.MealData
import com.example.dimanow.meal.DormitoryMealData
import com.example.dimanow.meal.DormitoryMealDay
import com.example.dimanow.meal.DormitoryMealSection
import com.example.dimanow.shuttle.ShuttleData
import com.example.dimanow.live.LiveChipContent
import com.example.dimanow.live.LiveClassOrder
import com.example.dimanow.live.LiveDisplayOptions
import com.example.dimanow.live.GuidanceKind
import com.example.dimanow.live.NotificationGuidanceMode
import com.example.dimanow.live.NotificationGuidancePolicy
import com.example.dimanow.theme.DIMANowTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class GuidanceCardTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun dashboardUsesDormitoryMealAtYeinAndMainCafeteriaAtMain() {
        val now = ZonedDateTime.of(2026, 8, 31, 12, 0, 0, 0, ZoneId.of("Asia/Seoul"))
        val dormitory = DormitoryMealData(
            days = listOf(
                DormitoryMealDay(
                    LocalDate.parse("2026-08-31"),
                    listOf(DormitoryMealSection("중식", "12:00~14:00", listOf("기숙사 제육볶음"))),
                    "https://example.invalid/dorm.jpg",
                ),
            ),
            lastSuccess = null,
            lastAttempt = null,
            error = null,
        )
        composeRule.setContent {
            DashboardScreen(
                schedule = DefaultSchedule.create(),
                zone = CampusZoneId.YEIN,
                shuttle = ShuttleData(emptyList(), null, null, null, "https://www.dima.ac.kr/?p=97", null),
                meal = MealData(emptyList(), null, null, null, "https://www.dima.ac.kr/?p=1", null, null),
                dormitoryMeal = dormitory,
                now = now,
            )
        }

        composeRule.onNodeWithText("기숙사").assertIsDisplayed()
        composeRule.onNodeWithText("중식").assertIsDisplayed()
        composeRule.onNodeWithText("기숙사 제육볶음").assertIsDisplayed()
        composeRule.onNodeWithText("중식 · 기숙사 제육볶음").assertDoesNotExist()
        composeRule.onNodeWithText("학생식당").assertDoesNotExist()
    }

    @Test
    fun dashboardShowsBothMainDestinationsFromTheSameCurrentInstant() {
        val now = ZonedDateTime.of(2026, 9, 1, 18, 50, 1, 0, ZoneId.of("Asia/Seoul"))
        val departures = listOf(
            ShuttleDeparture("A", "main-a", "TO_ONE_ROOM", DayOfWeek.TUESDAY, LocalTime.of(18, 55), CampusZoneId.MAIN, CampusZoneId.ONE_ROOM),
            ShuttleDeparture("B", "main-b", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(18, 55), CampusZoneId.MAIN, CampusZoneId.YEIN),
            ShuttleDeparture("B", "main-b", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(19, 20), CampusZoneId.MAIN, CampusZoneId.YEIN),
        )
        composeRule.setContent {
            DashboardScreen(
                schedule = DefaultSchedule.create(),
                zone = CampusZoneId.MAIN,
                shuttle = ShuttleData(departures, Instant.parse("2026-08-26T12:00:10Z"), null, null, "https://www.dima.ac.kr/?p=97", null),
                meal = MealData(emptyList(), null, null, null, "https://www.dima.ac.kr/?p=1", null, null),
                now = now,
            )
        }

        composeRule.onNodeWithText("엔터관행").assertIsDisplayed()
        composeRule.onNodeWithText("30분 후 · 막차").assertIsDisplayed()
        composeRule.onNodeWithText("19:20").assertIsDisplayed()
        composeRule.onNodeWithText("원룸촌행").assertIsDisplayed()
        composeRule.onNodeWithText("5분 후 · 첫차·막차").assertIsDisplayed()
    }

    @Test
    fun dashboardShowsACompactEndOfServiceStateWhenCachedServiceHasEnded() {
        val now = ZonedDateTime.of(2026, 8, 30, 23, 0, 0, 0, ZoneId.of("Asia/Seoul"))
        val departures = listOf(
            ShuttleDeparture("S", "yein", "TO_MAIN", DayOfWeek.SUNDAY, LocalTime.of(8, 0), CampusZoneId.YEIN, CampusZoneId.MAIN),
        )
        composeRule.setContent {
            DashboardScreen(
                schedule = DefaultSchedule.create(),
                zone = CampusZoneId.YEIN,
                shuttle = ShuttleData(departures, Instant.parse("2026-08-26T12:00:10Z"), null, null, "https://www.dima.ac.kr/?p=97", null),
                meal = MealData(emptyList(), null, null, null, "https://www.dima.ac.kr/?p=1", null, null),
                now = now,
            )
        }

        composeRule.onNodeWithText("운행 종료").assertIsDisplayed()
    }

    @Test
    fun timetableCourseUsesKoreanWeekdayAndReadableInformationRows() {
        composeRule.setContent {
            CourseSummaryCard(
                course = com.example.dimanow.domain.Course(
                    weekday = DayOfWeek.TUESDAY,
                    start = LocalTime.of(9, 0),
                    end = LocalTime.of(11, 50),
                    name = "스튜디오기초실습",
                    room = "기예관 122",
                    professor = "이상운",
                    zone = CampusZoneId.MAIN,
                ),
                onEdit = {},
                onDelete = {},
            )
        }

        composeRule.onNodeWithText("09:00 – 11:50").assertIsDisplayed()
        composeRule.onNodeWithText("스튜디오기초실습").assertIsDisplayed()
        // D-093: 강의실과 교수는 각각 한 줄로 읽힌다
        composeRule.onNodeWithText("기예관 122").assertIsDisplayed()
        composeRule.onNodeWithText("이상운").assertIsDisplayed()
        composeRule.onNodeWithText("담당 이상운").assertDoesNotExist()
        composeRule.onNodeWithText("TUESDAY").assertDoesNotExist()
    }

    @Test
    fun dashboardShowsValidShuttleCacheInsteadOfAskingForRefresh() {
        composeRule.setContent {
            DashboardScreen(
                schedule = DefaultSchedule.create(),
                zone = CampusZoneId.YEIN,
                shuttle = ShuttleData(
                    departures = listOf(
                        ShuttleDeparture(
                            "B",
                            "yein",
                            "TO_MAIN",
                            DayOfWeek.MONDAY,
                            LocalTime.of(8, 30),
                            CampusZoneId.YEIN,
                            CampusZoneId.MAIN,
                            LocalTime.of(8, 35),
                        ),
                    ),
                    lastSuccess = Instant.parse("2026-08-26T12:00:10Z"),
                    lastAttempt = Instant.parse("2026-08-26T12:00:10Z"),
                    error = null,
                    sourceUrl = "https://www.dima.ac.kr/?p=97",
                    noticeUrl = null,
                ),
                meal = MealData(emptyList(), null, null, null, "https://www.dima.ac.kr/?p=1", null, null),
                now = ZonedDateTime.of(2026, 8, 31, 8, 0, 0, 0, ZoneId.of("Asia/Seoul")),
            )
        }

        composeRule.onNodeWithText("본관행").assertIsDisplayed()
        composeRule.onNodeWithText("30분 후 · 첫차·막차").assertIsDisplayed()
        composeRule.onNodeWithText("08:30").assertIsDisplayed()
        composeRule.onNodeWithText("셔틀 데이터를 새로고침해 주세요").assertDoesNotExist()
    }

    @Test
    fun dashboardCapsulesUseStadiumLabelsForOfficialEveningService() {
        val departures = listOf(
            ShuttleDeparture("B", "university-headquarters", "TO_YEIN", DayOfWeek.MONDAY, LocalTime.of(19, 20), CampusZoneId.MAIN, CampusZoneId.YEIN),
            ShuttleDeparture("B-evening", "stadium-stop", "TO_YEIN", DayOfWeek.MONDAY, LocalTime.of(19, 35), CampusZoneId.MAIN, CampusZoneId.YEIN),
            ShuttleDeparture("B-evening", "stadium-stop", "TO_YEIN", DayOfWeek.MONDAY, LocalTime.of(20, 0), CampusZoneId.MAIN, CampusZoneId.YEIN),
        )
        composeRule.setContent {
            DashboardScreen(
                schedule = DefaultSchedule.create(),
                zone = CampusZoneId.MAIN,
                shuttle = ShuttleData(
                    departures = departures,
                    lastSuccess = Instant.parse("2026-08-26T12:00:10Z"),
                    lastAttempt = Instant.parse("2026-08-26T12:00:10Z"),
                    error = null,
                    sourceUrl = "https://www.dima.ac.kr/?p=97",
                    noticeUrl = null,
                ),
                meal = MealData(emptyList(), null, null, null, "https://www.dima.ac.kr/?p=1", null, null),
                now = ZonedDateTime.of(2026, 8, 31, 19, 30, 0, 0, ZoneId.of("Asia/Seoul")),
            )
        }

        // D-019: the first stadium departure is the stop transition; later stadium slots name the stadium.
        composeRule.onNodeWithText("5분 후 · 운동장 전환").assertExists()
        composeRule.onNodeWithText("30분 후 · 막차 · 운동장").assertExists()
    }

    @Test
    fun dashboardCapsuleKeepsLabelAndClockOnSeparateSingleLinesAtDefaultFontScale() {
        assertCapsuleLinesAtFontScale(1.0f, labelMustFitOneLine = true)
    }

    @Test
    fun dashboardCapsuleKeepsLabelAndClockOnSeparateSingleLinesAtFontScale1_3() {
        assertCapsuleLinesAtFontScale(1.3f, labelMustFitOneLine = true)
    }

    @Test
    fun dashboardCapsuleClockNeverSplitsAtFontScale2() {
        assertCapsuleLinesAtFontScale(2.0f, labelMustFitOneLine = false)
    }

    @Test
    fun dashboardShowsLoadingInsteadOfEmptyOrErrorStatesBeforeSourcesEmit() {
        composeRule.setContent {
            DIMANowTheme(darkTheme = false) {
                DashboardScreen(
                    schedule = DefaultSchedule.create(),
                    zone = CampusZoneId.MAIN,
                    shuttle = null,
                    meal = null,
                    dormitoryMeal = null,
                    notices = null,
                    now = ZonedDateTime.of(2026, 8, 31, 12, 0, 0, 0, ZoneId.of("Asia/Seoul")),
                )
            }
        }

        // D-094: 첫 캐시 값이 오기 전에는 빨간 새로고침 요청이나 '식단 없음'을 보이지 않는다
        composeRule.onNodeWithTag("dashboard_shuttle_loading", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("dashboard_meal_loading", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("dashboard_notices_loading", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithText("새로고침", substring = true, useUnmergedTree = true).fetchSemanticsNodes()
            .let { assertEquals(0, it.size) }
        composeRule.onNodeWithText("오늘은 제공 식단이 없어요", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("오늘 등록된 식단이 없어요", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("공지를 확인하고 있어요", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun dashboardEmptyShuttleCachePointsToTheShuttleTabInsteadOfAskingToRefreshHere() {
        composeRule.setContent {
            DashboardScreen(
                schedule = DefaultSchedule.create(),
                zone = CampusZoneId.MAIN,
                shuttle = ShuttleData(emptyList(), null, null, null, "https://www.dima.ac.kr/?p=97", null),
                meal = MealData(emptyList(), null, null, null, "https://www.dima.ac.kr/?p=1", null, null),
                now = ZonedDateTime.of(2026, 8, 31, 12, 0, 0, 0, ZoneId.of("Asia/Seoul")),
            )
        }

        composeRule.onNodeWithText("셔틀 시간표가 아직 없어요. 셔틀 탭에서 아래로 당겨 받아 보세요", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("셔틀 데이터를 새로고침해 주세요", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("dashboard_shuttle_loading", useUnmergedTree = true).assertDoesNotExist()
    }

    private fun assertCapsuleLinesAtFontScale(fontScale: Float, labelMustFitOneLine: Boolean) {
        val departures = listOf(
            ShuttleDeparture("B", "university-headquarters", "TO_YEIN", DayOfWeek.MONDAY, LocalTime.of(19, 0), CampusZoneId.MAIN, CampusZoneId.YEIN),
            ShuttleDeparture("B-evening", "stadium-stop", "TO_YEIN", DayOfWeek.MONDAY, LocalTime.of(19, 35), CampusZoneId.MAIN, CampusZoneId.YEIN),
            ShuttleDeparture("B-evening", "stadium-stop", "TO_YEIN", DayOfWeek.MONDAY, LocalTime.of(19, 50), CampusZoneId.MAIN, CampusZoneId.YEIN),
        )
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                DIMANowTheme(darkTheme = false) {
                    DashboardScreen(
                        schedule = DefaultSchedule.create(),
                        zone = CampusZoneId.MAIN,
                        shuttle = ShuttleData(departures, Instant.parse("2026-08-26T12:00:10Z"), null, null, "https://www.dima.ac.kr/?p=97", null),
                        meal = MealData(emptyList(), null, null, null, "https://www.dima.ac.kr/?p=1", null, null),
                        now = ZonedDateTime.of(2026, 8, 31, 19, 19, 0, 0, ZoneId.of("Asia/Seoul")),
                    )
                }
            }
        }

        // D-094: the clock is its own single node on one line; the countdown label sits above it.
        val clock = composeRule.onNode(
            hasText("19:50") and hasAnyAncestor(hasTestTag("home_departure_YEIN_1")),
            useUnmergedTree = true,
        )
        clock.assertExists()
        assertEquals(1, clock.lineCount())
        val label = composeRule.onNode(
            hasText("31분 후 · 막차 · 운동장") and hasAnyAncestor(hasTestTag("home_departure_YEIN_1")),
            useUnmergedTree = true,
        )
        label.assertExists()
        composeRule.onNode(
            hasText("19:35") and hasAnyAncestor(hasTestTag("home_departure_YEIN_0")),
            useUnmergedTree = true,
        ).let { assertEquals(1, it.lineCount()) }
        composeRule.onNode(
            hasText("16분 후 · 운동장 전환") and hasAnyAncestor(hasTestTag("home_departure_YEIN_0")),
            useUnmergedTree = true,
        ).assertExists()
        if (labelMustFitOneLine) assertEquals(1, label.lineCount())
    }

    private fun SemanticsNodeInteraction.lineCount(): Int {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single().lineCount
    }

    @Test
    fun dashboardOmitsSourceStatusBecauseItLivesInSettings() {
        composeRule.setContent {
            DashboardScreen(
                schedule = DefaultSchedule.create(),
                zone = CampusZoneId.OUTSIDE,
                shuttle = ShuttleData(
                    departures = emptyList(),
                    lastSuccess = Instant.parse("2026-08-26T12:00:10Z"),
                    lastAttempt = Instant.parse("2026-08-26T12:00:10Z"),
                    error = null,
                    sourceUrl = "https://www.dima.ac.kr/?p=97",
                    noticeUrl = null,
                ),
                meal = MealData(
                    days = emptyList(),
                    lastSuccess = Instant.parse("2026-08-26T12:01:24Z"),
                    lastAttempt = Instant.parse("2026-08-26T12:01:24Z"),
                    error = null,
                    sourceUrl = "https://www.dima.ac.kr/?p=1",
                    sourceImageUrl = null,
                    hours = null,
                ),
            )
        }

        composeRule.onNodeWithText("셔틀 2026년 8월 26일 21:00 KST").assertDoesNotExist()
        composeRule.onNodeWithText("식단 2026년 8월 26일 21:01 KST").assertDoesNotExist()
        composeRule.onNodeWithText("셔틀·식단 마지막 성공 기록 없음").assertDoesNotExist()
    }

    @Test
    fun liveDisplaySettingsExposeBothUserChoices() {
        var selectedChip = LiveChipContent.COUNTDOWN
        var selectedOrder = LiveClassOrder.COURSE_FIRST
        composeRule.setContent {
            LiveDisplaySettings(
                options = LiveDisplayOptions(),
                onChipContentChange = { selectedChip = it },
                onClassOrderChange = { selectedOrder = it },
            )
        }

        composeRule.onNodeWithText("상단 알림 표시").assertIsDisplayed()
        composeRule.onNodeWithText("남은 시간").assertIsDisplayed()
        composeRule.onNodeWithText("강의실").performClick()
        composeRule.onNodeWithText("잠금화면 첫 줄").assertIsDisplayed()
        composeRule.onNodeWithText("강의실 먼저").performClick()

        composeRule.runOnIdle {
            assertEquals(LiveChipContent.CLASSROOM, selectedChip)
            assertEquals(LiveClassOrder.CLASSROOM_FIRST, selectedOrder)
        }
    }

    @Test
    fun notificationSettingsExposeIndependentClassCampusAnd4402Modes() {
        var changed: Pair<GuidanceKind, NotificationGuidanceMode>? = null
        composeRule.setContent {
            NotificationGuidanceSettings(
                policy = NotificationGuidancePolicy(),
                onModeChange = { kind, mode -> changed = kind to mode },
            )
        }

        composeRule.onNodeWithText("수업 안내").assertIsDisplayed()
        composeRule.onNodeWithText("교내 셔틀").assertIsDisplayed()
        composeRule.onNodeWithText("4402 강남행").assertIsDisplayed()
        composeRule.onNodeWithTag("notification_mode_BUS_4402_STANDARD").performClick()

        composeRule.runOnIdle {
            assertEquals(GuidanceKind.BUS_4402 to NotificationGuidanceMode.STANDARD, changed)
        }
    }

    @Test
    fun testLocationControlsCanSelectEither4402Stop() {
        var selected: String? = null
        composeRule.setContent {
            TransitStopTestControls(testStopNumber = null, onChange = { selected = it })
        }

        composeRule.onNodeWithText("4402 정류장").assertIsDisplayed()
        composeRule.onNodeWithText("대학 셔틀 정류장").assertIsDisplayed()
        composeRule.onNodeWithText("원룸촌 앞").performClick()
        composeRule.runOnIdle { assertEquals("33243", selected) }
    }

    @Test
    fun bus4402ScheduleShowsBothStopsAndMarksTheEstimatedOneRoomTimes() {
        composeRule.setContent {
            Bus4402ScheduleContent(
                now = ZonedDateTime.of(2026, 9, 4, 8, 42, 0, 0, ZoneId.of("Asia/Seoul")),
                nearbyStopNumber = "34710",
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        }

        composeRule.onNodeWithText("4402 강남행").assertExists()
        composeRule.onNodeWithText("대학 셔틀 정류장").assertExists()
        composeRule.onNodeWithText("원룸촌 앞").assertExists()
        // D-094(5): countdown-then-clock capsules like the campus shuttle; the downstream stop's
        // time stays marked 예정 (D-061/D-068).
        assertCountdownAboveClock("bus4402_next_34710_0", "8분 후", "08:50")
        assertCountdownAboveClock("bus4402_next_34710_1", "48분 후", "09:30")
        assertCountdownAboveClock("bus4402_next_33243_0", "9분 후", "08:51 · 예정")
        composeRule.onNodeWithText("08:50 · 8분 후").assertDoesNotExist()
        composeRule.onNodeWithText("08:51 · 9분 후 · 예정").assertDoesNotExist()
        // The stop note is always visible instead of behind a "정류장 정보" button.
        composeRule.onNodeWithText("정류장 33243 · 공식 기점 +1분 예정").assertExists()
        composeRule.onNodeWithText("정류장 정보").assertDoesNotExist()
        composeRule.onNodeWithText("첫차 05:01 · 막차 22:01 · 예정").assertExists()
        // Both stops keep all 32 weekday departures in the horizontal chip list (D-086).
        composeRule.onAllNodesWithText("전체 시간표 · 32회").assertCountEquals(2)
        composeRule.onNodeWithContentDescription("다음 출발 08:50").assertHeightIsAtLeast(32.dp)
        composeRule.onNodeWithContentDescription("지난 시간 08:10").assertExists()
        composeRule.onNodeWithContentDescription("다음 출발 08:51, 예정").assertExists()
        composeRule.onNodeWithTag("bus4402_times_34710").performScrollToNode(hasContentDescription("22:00, 막차"))
        composeRule.onNodeWithText("22:00 (막차)").assertExists()
        composeRule.onNodeWithTag("bus4402_times_33243").performScrollToNode(hasContentDescription("22:01, 막차, 예정"))
        composeRule.onNodeWithText("22:01 (막차) · 예정").assertExists()
    }

    @Test
    fun bus4402ShowsAClockInsteadOfLargeMinuteCountsAndAnEndOfServiceState() {
        val now = mutableStateOf(ZonedDateTime.of(2026, 9, 4, 3, 50, 0, 0, ZoneId.of("Asia/Seoul")))
        composeRule.setContent {
            Bus4402ScheduleContent(
                now = now.value,
                nearbyStopNumber = null,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        }
        // D-069: beyond one hour the capsule names the next departure and shows its clock.
        assertCountdownAboveClock("bus4402_next_34710_0", "다음 출발 · 첫차", "05:00")
        composeRule.runOnIdle { now.value = now.value.withHour(4).withMinute(0) }
        assertCountdownAboveClock("bus4402_next_34710_0", "60분 후 · 첫차", "05:00")
        composeRule.runOnIdle { now.value = now.value.withHour(21).withMinute(55) }
        assertCountdownAboveClock("bus4402_next_34710_0", "5분 후 · 막차", "22:00")
        composeRule.runOnIdle { now.value = now.value.withHour(22).withMinute(30) }
        composeRule.onAllNodesWithText("오늘 운행이 끝났어요").assertCountEquals(2)
    }

    private fun assertCountdownAboveClock(tag: String, countdown: String, clock: String) {
        val label = composeRule.onNode(hasText(countdown) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
        val time = composeRule.onNode(hasText(clock) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
        label.assertExists()
        time.assertExists()
        assertTrue(
            "$tag: the countdown line sits above the clock line",
            label.fetchSemanticsNode().boundsInRoot.bottom <= time.fetchSemanticsNode().boundsInRoot.top,
        )
        assertEquals(1, time.lineCount())
    }

    @Test
    fun courseEditorUsesAndroidTimePickerInsteadOfFreeFormTimeText() {
        composeRule.setContent {
            CourseEditorDialog(initial = null, onDismiss = {}, onSave = {})
        }

        composeRule.onNodeWithText("시작 10:00").assertIsDisplayed()
        composeRule.onNodeWithText("종료 11:00").assertIsDisplayed()
        composeRule.onNodeWithText("시작 HH:mm").assertDoesNotExist()
        composeRule.onNodeWithText("시작 10:00").performClick()
        composeRule.onNodeWithText("시작 시간").assertIsDisplayed()
    }
}
