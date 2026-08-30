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
import java.security.MessageDigest

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

    @Test
    fun validDormitoryPayloadKeepsEveryMealSectionForTheWeek() = runTest {
        val payload = """{"schemaVersion":1,"weekStart":"2026-08-24","weekEnd":"2026-08-30","sourceImageUrl":"https://raw.githubusercontent.com/winter1l/DimaNow/example.jpg","days":[{"date":"2026-08-24","sections":[{"name":"조식","hours":"08:00~09:30","menuLines":["떡국","완자전&소스"]},{"name":"중식","hours":"12:00~14:00","menuLines":["유채된장국","계란마파두부"]}]}]}"""
        val hash = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray()).joinToString("") { "%02x".format(it) }
        val manifest = """{"schemaVersion":1,"generatedAt":"2026-08-27T03:00:00Z","datasets":{"dorm_meal":{"revision":1,"state":"READY","publishedAt":"2026-08-27T03:00:00Z","lastAttemptAt":"2026-08-27T03:00:00Z","url":"dorm-meal/$hash.json","sha256":"$hash","sourceUrl":"https://github.com/winter1l/DimaNow/tree/dorm-submissions/dorm-submissions"}}}"""
        val source: MealSource = StaticMealSource(
            database = database,
            transport = StaticDataTransport { url -> if (url.contains("manifest.json")) manifest.toByteArray() else payload.toByteArray() },
            clock = Clock.fixed(Instant.parse("2026-08-27T04:00:00Z"), ZoneOffset.UTC),
        )

        assertEquals(MealRefreshResult.Success(LocalDate.of(2026, 8, 24), Instant.parse("2026-08-27T04:00:00Z")), source.refreshDormitory())
        val day = source.dormitoryData.first().days.single()
        assertEquals(LocalDate.of(2026, 8, 24), day.date)
        assertEquals(listOf("조식", "중식"), day.sections.map { it.name })
        assertEquals(listOf("유채된장국", "계란마파두부"), day.sections[1].menuLines)
    }

    @Test
    fun sameDormitoryRevisionAfterItsWeekExpiresReportsNotPublishedAndKeepsTheLastGoodWeek() = runTest {
        val payload = """{"schemaVersion":1,"weekStart":"2026-08-24","weekEnd":"2026-08-30","sourceImageUrl":"https://raw.githubusercontent.com/winter1l/DimaNow/example.jpg","days":[{"date":"2026-08-24","sections":[{"name":"중식","hours":"12:00~14:00","menuLines":["유채된장국"]}]}]}"""
        val hash = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray()).joinToString("") { "%02x".format(it) }
        val manifest = """{"schemaVersion":1,"generatedAt":"2026-08-27T03:00:00Z","datasets":{"dorm_meal":{"revision":1,"state":"READY","publishedAt":"2026-08-27T03:00:00Z","lastAttemptAt":"2026-08-27T03:00:00Z","url":"dorm-meal/$hash.json","sha256":"$hash","sourceUrl":"https://github.com/winter1l/DimaNow/tree/dorm-submissions/dorm-submissions"}}}"""
        val firstSuccess = Instant.parse("2026-08-27T04:00:00Z")
        StaticMealSource(
            database,
            StaticDataTransport { url -> if (url.endsWith("manifest.json")) manifest.toByteArray() else payload.toByteArray() },
            Clock.fixed(firstSuccess, ZoneOffset.UTC),
        ).refreshDormitory()

        val source = StaticMealSource(
            database,
            StaticDataTransport { manifest.toByteArray() },
            Clock.fixed(Instant.parse("2026-08-31T02:00:00Z"), ZoneOffset.UTC),
        )

        assertEquals(MealRefreshResult.NotPublishedYet, source.refreshDormitory())
        val data = source.dormitoryData.first()
        assertEquals(firstSuccess, data.lastSuccess)
        assertEquals(listOf("유채된장국"), data.days.single().sections.single().menuLines)
    }

    @Test
    fun sameRevisionAfterCachedWeekExpiresReportsNotPublishedAndPreservesSuccess() = runTest {
        val fixture = mealFixture()
        val firstSuccess = Instant.parse("2026-08-28T02:00:00Z")
        StaticMealSource(
            database,
            StaticDataTransport { url -> if (url.endsWith("manifest.json")) fixture.manifest.toByteArray() else fixture.payload.toByteArray() },
            Clock.fixed(firstSuccess, ZoneOffset.UTC),
        ).refresh()

        val source = StaticMealSource(
            database,
            StaticDataTransport { fixture.manifest.toByteArray() },
            Clock.fixed(Instant.parse("2026-08-31T02:00:00Z"), ZoneOffset.UTC),
        )

        assertEquals(MealRefreshResult.NotPublishedYet, source.refresh())
        val data = source.data.first()
        assertEquals(firstSuccess, data.lastSuccess)
        assertEquals(listOf("쌀밥", "된장국"), data.days.single().menuLines)
    }

    @Test
    fun waitingManifestReportsNotPublishedAndPreservesLastGoodMeal() = runTest {
        val fixture = mealFixture()
        val firstSuccess = Instant.parse("2026-08-28T02:00:00Z")
        StaticMealSource(
            database,
            StaticDataTransport { url -> if (url.endsWith("manifest.json")) fixture.manifest.toByteArray() else fixture.payload.toByteArray() },
            Clock.fixed(firstSuccess, ZoneOffset.UTC),
        ).refresh()
        val waiting = fixture.manifest
            .replace("\"state\":\"READY\"", "\"state\":\"WAITING\"")
            .replace("\"sourceUrl\":\"https://www.dima.ac.kr/?p=1\"", "\"sourceUrl\":\"https://www.dima.ac.kr/?p=1\",\"message\":\"아직 새 식단이 올라오지 않았어요\"")
        val source = StaticMealSource(
            database,
            StaticDataTransport { waiting.toByteArray() },
            Clock.fixed(Instant.parse("2026-08-31T02:00:00Z"), ZoneOffset.UTC),
        )

        assertEquals(MealRefreshResult.NotPublishedYet, source.refresh())
        val data = source.data.first()
        assertEquals(firstSuccess, data.lastSuccess)
        assertEquals("WAITING", data.serverState)
        assertEquals(listOf("쌀밥", "된장국"), data.days.single().menuLines)
    }

    private fun mealFixture(): MealFixture {
        val payload = """{"schemaVersion":1,"weekStart":"2026-08-24","weekEnd":"2026-08-30","days":[{"date":"2026-08-24","menuLines":["쌀밥","된장국"],"hours":"11:30 ~ 14:00","sourceUrl":"https://www.instagram.com/p/example/","sourceImageUrl":"https://scontent.example/meal.jpg"}]}"""
        val hash = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray()).joinToString("") { "%02x".format(it) }
        val manifest = """{"schemaVersion":1,"generatedAt":"2026-08-28T01:02:03Z","datasets":{"meal":{"revision":3,"state":"READY","publishedAt":"2026-08-28T01:02:03Z","lastAttemptAt":"2026-08-28T01:02:03Z","url":"meal/$hash.json","sha256":"$hash","sourceUrl":"https://www.dima.ac.kr/?p=1"}}}"""
        return MealFixture(payload, manifest)
    }

    private data class MealFixture(val payload: String, val manifest: String)
}
