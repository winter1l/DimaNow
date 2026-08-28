package com.example.dimanow.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.dimanow.domain.ClassContent
import com.example.dimanow.domain.GuidancePhase
import com.example.dimanow.domain.GuidanceSnapshot
import com.example.dimanow.domain.ShuttleLine
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.DefaultSchedule
import com.example.dimanow.domain.ShuttleDeparture
import com.example.dimanow.meal.MealData
import com.example.dimanow.shuttle.ShuttleData
import com.example.dimanow.live.LiveChipContent
import com.example.dimanow.live.LiveClassOrder
import com.example.dimanow.live.LiveDisplayOptions
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class GuidanceCardTest {
    @get:Rule val composeRule = createComposeRule()

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
                hasSavedOrigin = true,
                automatic = true,
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
                hasSavedOrigin = true,
                automatic = true,
                shuttle = ShuttleData(departures, Instant.parse("2026-08-26T12:00:10Z"), null, null, "https://www.dima.ac.kr/?p=97", null),
                meal = MealData(emptyList(), null, null, null, "https://www.dima.ac.kr/?p=1", null, null),
                now = now,
            )
        }

        composeRule.onNodeWithText("운행 종료").assertIsDisplayed()
    }

    @Test
    fun exactGuidanceTextOmitsProfessorAndUnneededShuttlePlaceholder() {
        composeRule.setContent {
            GuidanceCard(
                GuidanceSnapshot(
                    classContent = ClassContent("10:00 · 조명기초및실습", "시작까지 42분 · 덕성관 402"),
                    shuttleLines = listOf(ShuttleLine("엔터관  5분, 30분")),
                    phase = GuidancePhase.BEFORE_CLASS,
                ),
            )
        }

        composeRule.onNodeWithText("10:00 · 조명기초및실습").assertIsDisplayed()
        composeRule.onNodeWithText("시작까지 42분 · 덕성관 402").assertIsDisplayed()
        composeRule.onNodeWithText("엔터관  5분, 30분").assertIsDisplayed()
        composeRule.onNodeWithText("이용창").assertDoesNotExist()
        composeRule.onNodeWithText("셔틀 불필요").assertDoesNotExist()
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
                hasSavedOrigin = true,
                automatic = true,
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
    fun dashboardOmitsSourceStatusBecauseItLivesInSettings() {
        composeRule.setContent {
            DashboardScreen(
                schedule = DefaultSchedule.create(),
                zone = CampusZoneId.OUTSIDE,
                hasSavedOrigin = false,
                automatic = true,
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

        composeRule.onNodeWithText("상단 필").assertIsDisplayed()
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
