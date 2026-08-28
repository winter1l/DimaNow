package com.example.dimanow.meal

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.dimanow.data.DimaDatabase
import com.example.dimanow.sync.StaticDataTransport
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class StaticMealSourceTest {
    private lateinit var database: DimaDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DimaDatabase::class.java).build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun validServerMealReplacesOnlyItsValidatedWeek() = runTest {
        val payload = """{"schemaVersion":1,"weekStart":"2026-08-24","weekEnd":"2026-08-30","days":[{"date":"2026-08-24","menuLines":["쌀밥","된장국"],"hours":"11:30 ~ 14:00","sourceUrl":"https://www.instagram.com/p/example/","sourceImageUrl":"https://scontent.example/meal.jpg"}]}"""
        val manifest = """{"schemaVersion":1,"generatedAt":"2026-08-28T01:02:03Z","datasets":{"meal":{"revision":3,"state":"READY","publishedAt":"2026-08-28T01:02:03Z","lastAttemptAt":"2026-08-28T01:02:03Z","url":"meal/973d6574aee62757ea06d7c092584a59b5aede7452ab5a5a4cfdc5cbe667467f.json","sha256":"973d6574aee62757ea06d7c092584a59b5aede7452ab5a5a4cfdc5cbe667467f","sourceUrl":"https://www.dima.ac.kr/?p=1"}}}"""
        val source: MealSource = StaticMealSource(
            database = database,
            transport = StaticDataTransport { url -> if (url.endsWith("manifest.json")) manifest.toByteArray() else payload.toByteArray() },
            clock = Clock.fixed(Instant.parse("2026-08-28T02:00:00Z"), ZoneOffset.UTC),
        )

        assertEquals(MealRefreshResult.Success(LocalDate.of(2026, 8, 24), Instant.parse("2026-08-28T02:00:00Z")), source.refresh())
        val meal = source.data.first().days.single()
        assertEquals(LocalDate.of(2026, 8, 24), meal.date)
        assertEquals(listOf("쌀밥", "된장국"), meal.menuLines)
        assertEquals("11:30 ~ 14:00", meal.hours)
    }
}
