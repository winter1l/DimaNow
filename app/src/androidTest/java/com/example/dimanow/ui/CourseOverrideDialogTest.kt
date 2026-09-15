package com.example.dimanow.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.Course
import com.example.dimanow.domain.CourseOverride
import com.example.dimanow.domain.CourseOverrideKind
import com.example.dimanow.ui.schedule.CourseOverrideDialog
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class CourseOverrideDialogTest {
    @get:Rule val composeRule = createComposeRule()
    private val course = Course(
        weekday = DayOfWeek.MONDAY,
        start = LocalTime.of(10, 0),
        end = LocalTime.of(12, 0),
        name = "영상제작",
        room = "101호",
        professor = "",
        zone = CampusZoneId.MAIN,
        id = 42,
    )

    @Test
    fun onlineAppliesToTodaysSingleOccurrenceWithoutChangingWeeklyTimeOrRoom() {
        var saved: CourseOverride? = null
        show(LocalDate.parse("2026-09-14")) { saved = it }
        composeRule.onNodeWithText("비대면").performClick()
        composeRule.onNodeWithText("이 날짜에 적용").performClick()
        assertEquals(42L, saved?.courseId)
        assertEquals(LocalDate.parse("2026-09-14"), saved?.date)
        assertEquals(CourseOverrideKind.ONLINE, saved?.kind)
        assertNull(saved?.start)
        assertNull(saved?.end)
        assertNull(saved?.room)
    }

    @Test
    fun cancellationFromAnotherWeekdayTargetsNextOccurrence() {
        var saved: CourseOverride? = null
        show(LocalDate.parse("2026-09-15")) { saved = it }
        composeRule.onNodeWithText("이 날짜에 적용").performClick()
        assertEquals(LocalDate.parse("2026-09-21"), saved?.date)
        assertEquals(CourseOverrideKind.CANCELLED, saved?.kind)
    }

    @Test
    fun invalidChangedTimeCannotBeSaved() {
        show(LocalDate.parse("2026-09-14")) {}
        composeRule.onNodeWithText("시간·강의실 변경").performClick()
        composeRule.onNodeWithText("종료 시간 (HH:mm)").performTextReplacement("09:00")
        composeRule.onNodeWithText("이 날짜에 적용").assertIsNotEnabled()
        composeRule.onNodeWithText("종료 시간 (HH:mm)").performTextReplacement("24:00")
        composeRule.onNodeWithText("이 날짜에 적용").assertIsNotEnabled()
    }

    private fun show(today: LocalDate, onSave: (CourseOverride) -> Unit) {
        composeRule.setContent {
            CourseOverrideDialog(
                course = course,
                today = today,
                termStart = LocalDate.parse("2026-09-01"),
                termEnd = LocalDate.parse("2026-12-18"),
                onDismiss = {},
                onSave = onSave,
            )
        }
    }
}
