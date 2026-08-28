package com.example.dimanow.widget

import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.Course
import com.example.dimanow.domain.MealDay
import com.example.dimanow.domain.MealValidationState
import com.example.dimanow.domain.ShuttleDeparture
import com.example.dimanow.guidance.ShuttleBoard
import com.example.dimanow.guidance.ShuttleBoardRow
import com.example.dimanow.guidance.ShuttleCountdown
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusSummaryWidgetPlannerTest {
    private val zoneId = ZoneId.of("Asia/Seoul")

    private val course = Course(
        id = 1,
        name = "미디어음악기초",
        room = "예술관 301호",
        professor = "김교수",
        weekday = DayOfWeek.THURSDAY,
        start = LocalTime.of(10, 0),
        end = LocalTime.of(12, 0),
        zone = CampusZoneId.MAIN,
    )

    @Test
    fun `plans all-in-one widget layout with next course, shuttle, and meal`() {
        val now = ZonedDateTime.of(2026, 8, 27, 9, 30, 0, 0, zoneId)
        val shuttle = ShuttleDeparture(
            sourceRouteId = "r1",
            sourceStopId = "s1",
            direction = "TO_MAIN",
            serviceDay = DayOfWeek.THURSDAY,
            time = LocalTime.of(9, 45),
            originZone = CampusZoneId.YEIN,
            destinationZone = CampusZoneId.MAIN,
        )
        val meal = MealDay(
            date = LocalDate.of(2026, 8, 27),
            menuLines = listOf("배추된장국", "간장돈육불고기", "볼어묵볶음"),
            hours = "11:00 ~ 14:00",
            sourceUrl = "https://www.dima.ac.kr/?p=1",
            sourceImageUrl = "https://instagram.com/sample.jpg",
            validationState = MealValidationState.VALID,
        )

        val plan = CampusSummaryWidgetPlanner().plan(
            now = now,
            currentZone = CampusZoneId.YEIN,
            todayCourses = listOf(course),
            shuttleBoard = ShuttleBoard(listOf(ShuttleBoardRow(CampusZoneId.YEIN, CampusZoneId.MAIN, listOf(ShuttleCountdown(shuttle, 15))))),
            todayMeal = meal,
        )

        assertEquals("현재 위치: 엔터관", plan.headerLocationText)
        assertTrue(plan.headerDateText.contains("8월 27일"))
        assertEquals("미디어음악기초", plan.courseTitle)
        assertEquals("시작까지 30분 · 예술관 301호", plan.courseDetail)
        assertEquals("셔틀 (엔터관 출발)", plan.shuttleTitle)
        assertEquals("본관행 15분", plan.shuttleContent)
        assertEquals("학생식당 (운영 전 · 11:00부터)", plan.mealTitle)
        assertEquals("배추된장국 · 간장돈육불고기 · 볼어묵볶음", plan.mealContent)
        assertTrue(plan.requiresMinuteUpdate)
    }

    @Test
    fun `ongoing class stays visible until its end regardless of the live guidance cutoff`() {
        val now = ZonedDateTime.of(2026, 8, 27, 10, 40, 0, 0, zoneId)

        val plan = CampusSummaryWidgetPlanner().plan(
            now = now,
            currentZone = CampusZoneId.MAIN,
            todayCourses = listOf(course),
            shuttleBoard = ShuttleBoard(emptyList()),
            todayMeal = null,
        )

        assertEquals("▶ 미디어음악기초", plan.courseTitle)
        assertEquals("예술관 301호 · 12:00까지", plan.courseDetail)
        assertFalse(plan.requiresMinuteUpdate)
    }

    @Test
    fun `later course today is previewed with its start time beyond the countdown window`() {
        val now = ZonedDateTime.of(2026, 8, 27, 7, 0, 0, 0, zoneId)

        val plan = CampusSummaryWidgetPlanner().plan(
            now = now,
            currentZone = CampusZoneId.MAIN,
            todayCourses = listOf(course),
            shuttleBoard = ShuttleBoard(emptyList()),
            todayMeal = null,
        )

        assertEquals("미디어음악기초", plan.courseTitle)
        assertEquals("예술관 301호 · 10:00 시작", plan.courseDetail)
    }

    @Test
    fun `plans empty state with short untruncated labels`() {
        val now = ZonedDateTime.of(2026, 8, 27, 22, 0, 0, 0, zoneId)

        val plan = CampusSummaryWidgetPlanner().plan(
            now = now,
            currentZone = CampusZoneId.MAIN,
            todayCourses = emptyList(),
            shuttleBoard = ShuttleBoard(emptyList()),
            todayMeal = null,
        )

        assertEquals("현재 위치: 본관", plan.headerLocationText)
        assertEquals("오늘 수업 없음", plan.courseTitle)
        assertEquals("등록된 일정 없음", plan.courseDetail)
        assertEquals("오늘 운행 종료", plan.shuttleContent)
    }

    @Test
    fun `summary widget distinguishes main location from the evening stadium stop`() {
        val now = ZonedDateTime.of(2026, 8, 27, 19, 30, 0, 0, zoneId)
        val stadiumDeparture = ShuttleDeparture(
            sourceRouteId = "B-evening",
            sourceStopId = "stadium-stop",
            direction = "TO_YEIN",
            serviceDay = DayOfWeek.THURSDAY,
            time = LocalTime.of(19, 35),
            originZone = CampusZoneId.MAIN,
            destinationZone = CampusZoneId.YEIN,
        )
        val plan = CampusSummaryWidgetPlanner().plan(
            now = now,
            currentZone = CampusZoneId.MAIN,
            todayCourses = emptyList(),
            shuttleBoard = ShuttleBoard(
                listOf(ShuttleBoardRow(CampusZoneId.MAIN, CampusZoneId.YEIN, listOf(ShuttleCountdown(stadiumDeparture, 5)))),
            ),
            todayMeal = null,
        )

        assertEquals("현재 위치: 본관", plan.headerLocationText)
        assertEquals("셔틀 (운동장 출발)", plan.shuttleTitle)
    }
}
