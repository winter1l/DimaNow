package com.example.dimanow.meal

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.dimanow.data.DimaDatabase
import com.example.dimanow.sync.StaticDataTransport
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DormitoryHolidayMealSourceTest {
    private lateinit var database: DimaDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), DimaDatabase::class.java).build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun closedDaysAndSingleDishSectionsImportWithoutInventingExtraLines() = runTest {
        val source = source(payload())
        assertEquals(MealRefreshResult.Success(LocalDate.parse("2026-09-21"), NOW), source.refreshDormitory())
        val days = source.dormitoryData.first().days
        assertEquals(listOf("떡국"), days[0].sections[0].menuLines)
        assertEquals(listOf("휴무"), days[0].sections[1].menuLines)
        assertEquals(listOf("추석 공휴일"), days[1].sections.single().menuLines)
        assertEquals(null, days[1].sections.single().hours)
        assertEquals(listOf("추석"), days[2].sections.single().menuLines)
    }

    @Test
    fun missingHolidayContentDoesNotErasePreviouslyImportedMeals() = runTest {
        source(payload()).refreshDormitory()
        val source = source(payload().replace("\"추석 공휴일\"", "\" \""), revision = 2)
        assertTrue(source.refreshDormitory() is MealRefreshResult.Failure)
        val stored = source.dormitoryData.first()
        assertEquals(listOf("추석 공휴일"), stored.days[1].sections.single().menuLines)
        assertEquals(NOW, stored.lastSuccess)
    }

    private fun source(payload: String, revision: Int = 1): MealSource {
        val hash = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray()).joinToString("") { "%02x".format(it) }
        val manifest = """{"schemaVersion":1,"generatedAt":"$NOW","datasets":{"dorm_meal":{"revision":$revision,"state":"READY","publishedAt":"$NOW","lastAttemptAt":"$NOW","url":"dorm-meal/$hash.json","sha256":"$hash","sourceUrl":"https://github.com/winter1l/DimaNow/tree/dorm-submissions/dorm-submissions"}}}"""
        return StaticMealSource(database, StaticDataTransport { url ->
            if (url.endsWith("manifest.json")) manifest.toByteArray() else payload.toByteArray()
        }, Clock.fixed(NOW, ZoneOffset.UTC))
    }

    private fun payload() = """{"schemaVersion":1,"weekStart":"2026-09-21","weekEnd":"2026-09-27","sourceImageUrl":"https://raw.githubusercontent.com/winter1l/DimaNow/example.jpg","days":[
      {"date":"2026-09-21","sections":[{"name":"조식","menuLines":["떡국"]},{"name":"석식","menuLines":["휴무"]}]},
      {"date":"2026-09-24","sections":[{"name":"안내","menuLines":["추석 공휴일"]}]},
      {"date":"2026-09-25","sections":[{"name":"안내","menuLines":["추석"]}]}
    ]}"""

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-21T04:00:00Z")
    }
}
