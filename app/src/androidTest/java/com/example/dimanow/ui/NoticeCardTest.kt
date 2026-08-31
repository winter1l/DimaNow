package com.example.dimanow.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.dimanow.domain.CampusNotice
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.DefaultSchedule
import com.example.dimanow.meal.MealData
import com.example.dimanow.notice.NoticeData
import com.example.dimanow.shuttle.ShuttleData
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Rule
import org.junit.Test

class NoticeCardTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun dashboardShowsLatestThreeNoticesAndSchoolServiceShortcuts() {
        val notices = NoticeData(
            notices = listOf(
                CampusNotice("1", "2026-2학기 수강신청 정정 기간 안내", "https://www.dima.ac.kr/?p=111&viewMode=view&reqIdx=1", LocalDate.of(2026, 8, 27), false),
                CampusNotice("2", "학내 Wi-Fi 구축 관련 사용 안내", "https://www.dima.ac.kr/?p=111&viewMode=view&reqIdx=2", LocalDate.of(2026, 8, 26), true),
                CampusNotice("3", "2026-2학기 국가장학금 2차 신청 안내", "https://www.dima.ac.kr/?p=111&viewMode=view&reqIdx=3", LocalDate.of(2026, 8, 18), false),
                CampusNotice("4", "네 번째 공지는 카드에 나오지 않습니다", "https://www.dima.ac.kr/?p=111&viewMode=view&reqIdx=4", LocalDate.of(2026, 8, 17), false),
            ),
            lastSuccess = Instant.parse("2026-08-28T00:30:00Z"),
            lastAttempt = Instant.parse("2026-08-28T00:30:00Z"),
            error = null,
            sourceUrl = "https://www.dima.ac.kr/?p=111",
        )
        composeRule.setContent {
            DashboardScreen(
                schedule = DefaultSchedule.create(),
                zone = CampusZoneId.OUTSIDE,
                automatic = true,
                shuttle = ShuttleData(emptyList(), null, null, null, "https://www.dima.ac.kr/?p=97", null),
                meal = MealData(emptyList(), null, null, null, "https://www.dima.ac.kr/?p=1", null, null),
                notices = notices,
                now = ZonedDateTime.of(2026, 8, 28, 21, 0, 0, 0, ZoneId.of("Asia/Seoul")),
            )
        }

        composeRule.onNodeWithText("학교 공지").assertExists()
        composeRule.onNodeWithText("2026-2학기 수강신청 정정 기간 안내").assertExists()
        composeRule.onNodeWithText("학내 Wi-Fi 구축 관련 사용 안내").assertExists()
        composeRule.onNodeWithText("2026-2학기 국가장학금 2차 신청 안내").assertExists()
        composeRule.onNodeWithText("네 번째 공지는 카드에 나오지 않습니다").assertDoesNotExist()
        composeRule.onNodeWithText("DIMA Portal").assertExists()
        composeRule.onNodeWithText("수업 (LMS)").assertExists()
    }

    @Test
    fun emptyNoticeCacheShowsAQuietPlaceholderInsteadOfAnError() {
        composeRule.setContent {
            DashboardScreen(
                schedule = DefaultSchedule.create(),
                zone = CampusZoneId.OUTSIDE,
                automatic = true,
                shuttle = ShuttleData(emptyList(), null, null, null, "https://www.dima.ac.kr/?p=97", null),
                meal = MealData(emptyList(), null, null, null, "https://www.dima.ac.kr/?p=1", null, null),
                now = ZonedDateTime.of(2026, 8, 28, 21, 0, 0, 0, ZoneId.of("Asia/Seoul")),
            )
        }

        composeRule.onNodeWithText("학교 공지").assertExists()
        composeRule.onNodeWithText("공지를 불러오는 중이거나 없습니다").assertExists()
    }
}
