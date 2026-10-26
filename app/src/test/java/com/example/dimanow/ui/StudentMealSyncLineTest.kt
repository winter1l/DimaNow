package com.example.dimanow.ui

import com.example.dimanow.meal.MealData
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StudentMealSyncLineTest {
    private val today = LocalDate.of(2026, 10, 26)
    private fun meal(error: String?, serverState: String?) = MealData(emptyList(), null, null, error, "https://example.invalid", null, null, serverState = serverState)

    @Test
    fun aWeekTheServerHasNotGotYetReadsAsNotPostedNotAsAFailure() {
        // D-129: the cafeteria has not posted (WAITING) or the post could not be read yet (NEEDS_REVIEW).
        assertEquals(StudentMealSyncLine.NOT_POSTED, studentMealSyncLine(meal("아직 새 식단이 올라오지 않았어요", "WAITING"), today))
        assertEquals(StudentMealSyncLine.NOT_POSTED, studentMealSyncLine(meal("식단 원격 응답 401", "NEEDS_REVIEW"), today))
        assertEquals(StudentMealSyncLine.NOT_POSTED, studentMealSyncLine(meal(null, null), today))
    }

    @Test
    fun onlyTheAppsOwnFailedDownloadIsAFailure() {
        assertEquals(StudentMealSyncLine.FAILED, studentMealSyncLine(meal("Unable to resolve host", "READY"), today))
        assertEquals(StudentMealSyncLine.FAILED, studentMealSyncLine(meal("timeout", null), today))
    }
}
