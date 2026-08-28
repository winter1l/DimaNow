package com.example.dimanow.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.Course
import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TimetableDeleteConfirmationTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun courseIsDeletedOnlyAfterTheUserConfirms() {
        var confirmed = false
        composeRule.setContent {
            CourseDeleteConfirmationDialog(
                course = Course(
                    weekday = DayOfWeek.THURSDAY,
                    start = LocalTime.of(13, 0),
                    end = LocalTime.of(14, 50),
                    name = "프리젠테이션영어",
                    room = "덕성관 510-1",
                    professor = "이효정",
                    zone = CampusZoneId.MAIN,
                    id = 7,
                ),
                onDismiss = {},
                onConfirm = { confirmed = true },
            )
        }

        composeRule.onNodeWithText("프리젠테이션영어").assertExists()
        assertFalse(confirmed)
        composeRule.onNodeWithText("삭제").performClick()
        assertTrue(confirmed)
    }
}
