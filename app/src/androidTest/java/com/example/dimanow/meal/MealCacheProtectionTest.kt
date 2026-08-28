package com.example.dimanow.meal

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.dimanow.data.DimaDatabase
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MealCacheProtectionTest {
    @Test
    fun failedCandidateDoesNotOverwriteLastValidWeek() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, DimaDatabase::class.java).allowMainThreadQueries().build()
        try {
            val source = OfficialMealSource(context, database)
            val weekStart = LocalDate.of(2026, 8, 24)
            val valid = MealValidationResult.Valid(
                weekStart,
                (0L..4L).associate { day -> weekStart.plusDays(day) to listOf("밥", "국") },
            )
            val post = MealPost("[DIMA 학생식당] 8월 4주차", "https://www.instagram.com/p/current/", "11:00 ~ 14:00")
            val firstSuccess = Instant.parse("2026-08-24T01:00:00Z")
            source.acceptValid(valid, post, "https://example.invalid/valid.jpg", firstSuccess)

            source.recordFailure("메뉴 확인 필요: 날짜 헤더 오류", post, "https://example.invalid/candidate.jpg", Instant.parse("2026-08-24T03:00:00Z"))

            val data = source.data.first()
            assertEquals(5, data.days.size)
            assertEquals(listOf("밥", "국"), data.days.first().menuLines)
            assertEquals(firstSuccess, data.lastSuccess)
            assertEquals("메뉴 확인 필요: 날짜 헤더 오류", data.error)
            assertEquals("https://example.invalid/candidate.jpg", data.sourceImageUrl)
        } finally {
            database.close()
        }
    }
}
