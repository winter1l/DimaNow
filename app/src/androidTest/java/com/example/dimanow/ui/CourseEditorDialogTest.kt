package com.example.dimanow.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.Course
import com.example.dimanow.theme.DIMANowTheme
import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** D-102: the full-screen course form. */
class CourseEditorDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun aNewCourseOnSeveralWeekdaysBecomesOneCoursePerDay() {
        var saved: List<Course>? = null
        compose.setContent { DIMANowTheme { CourseEditorDialog(initial = null, onDismiss = {}, onSave = { saved = it }) } }

        compose.onNodeWithTag("course_editor_save").assertIsNotEnabled()
        compose.onNodeWithText("수업명").performTextInput("음향 실습")
        // A name alone is not enough: at least one weekday must be picked.
        compose.onNodeWithTag("course_editor_save").assertIsNotEnabled()
        compose.onNodeWithContentDescription("월요일").performClick()
        compose.onNodeWithContentDescription("수요일").performClick()
        compose.onNodeWithTag("course_editor_save").assertIsEnabled().performClick()

        compose.runOnIdle {
            val courses = requireNotNull(saved)
            assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), courses.map { it.weekday })
            assertTrue(courses.all { it.name == "음향 실습" && it.start == LocalTime.of(10, 0) && it.end == LocalTime.of(11, 0) && it.id == 0L })
        }
    }

    @Test
    fun editingKeepsOneWeekdayAndTheCourseId() {
        val course = Course(
            weekday = DayOfWeek.MONDAY, start = LocalTime.of(13, 0), end = LocalTime.of(14, 30),
            name = "영상 편집", room = "B101", professor = "", zone = CampusZoneId.MAIN, id = 7,
        )
        var saved: List<Course>? = null
        compose.setContent { DIMANowTheme { CourseEditorDialog(initial = course, onDismiss = {}, onSave = { saved = it }) } }

        compose.onNodeWithText("수업 수정").assertExists()
        compose.onNodeWithContentDescription("화요일").performClick()
        compose.onNodeWithContentDescription("화요일").assertIsSelected()
        compose.onNodeWithContentDescription("월요일").assertIsNotSelected()
        compose.onNodeWithTag("course_editor_save").performClick()

        compose.runOnIdle { assertEquals(listOf(course.copy(weekday = DayOfWeek.TUESDAY)), saved) }
    }

    @Test
    fun closingAfterTypingAsksBeforeDroppingTheInput() {
        var dismissed = false
        compose.setContent { DIMANowTheme { CourseEditorDialog(initial = null, onDismiss = { dismissed = true }, onSave = {}) } }

        compose.onNodeWithText("수업명").performTextInput("드로잉")
        compose.onNodeWithTag("course_editor_close").performClick()
        compose.onNodeWithText("작성한 내용을 버릴까요?").assertExists()
        compose.onNodeWithText("계속 작성").performClick()
        compose.onNodeWithText("작성한 내용을 버릴까요?").assertDoesNotExist()
        compose.runOnIdle { assertFalse(dismissed) }

        compose.onNodeWithTag("course_editor_close").performClick()
        compose.onNodeWithTag("course_editor_discard").performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test
    fun closingAnUntouchedFormDoesNotAsk() {
        var dismissed = false
        compose.setContent { DIMANowTheme { CourseEditorDialog(initial = null, onDismiss = { dismissed = true }, onSave = {}) } }

        compose.onNodeWithTag("course_editor_close").performClick()
        compose.onNodeWithText("작성한 내용을 버릴까요?").assertDoesNotExist()
        compose.runOnIdle { assertTrue(dismissed) }
    }
}
