package com.example.dimanow.pipeline

import com.example.dimanow.sync.CampusDataManifest
import com.example.dimanow.sync.DormitoryMealDayPayload
import com.example.dimanow.sync.DormitoryMealPayload
import com.example.dimanow.sync.DormitoryMealSectionPayload
import com.example.dimanow.sync.DormitoryMealSubmissionStatus
import java.nio.file.Files
import java.time.LocalDate
import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticDataPublisherTest {
    @Test
    fun `검증된 기숙사 식단과 제출 결과를 Pages에 게시한다`() {
        val output = Files.createTempDirectory("dima-dorm-meal")
        val publisher = StaticDataPublisher(output)
        val payload = dormitoryPayload()

        publisher.publishDormitoryMeal(payload, revision = 2, publishedAt = Instant.parse("2026-08-27T04:00:00Z"))
        publisher.publishDormitorySubmissionStatus(
            DormitoryMealSubmissionStatus("submission-1", "PUBLISHED", null, "2026-08-27T04:00:00Z"),
        )

        val manifest = Json.decodeFromString<CampusDataManifest>(Files.readString(output.resolve("data/v1/manifest.json")))
        val descriptor = manifest.datasets.getValue("dorm_meal")
        assertEquals("READY", descriptor.state)
        assertTrue(descriptor.url.matches(Regex("dorm-meal/[0-9a-f]{64}\\.json")))
        assertTrue(Files.exists(output.resolve("data/v1").resolve(descriptor.url)))
        val status = Json.decodeFromString<DormitoryMealSubmissionStatus>(
            Files.readString(output.resolve("data/v1/dorm-submissions/submission-1.json")),
        )
        assertEquals("PUBLISHED", status.state)
    }

    @Test
    fun `현재 주 기숙사 식단이 있으면 새 Gemini 처리를 막는다`() {
        val output = Files.createTempDirectory("dima-dorm-dedupe")
        val publisher = StaticDataPublisher(output)
        publisher.publishDormitoryMeal(dormitoryPayload(), 1, Instant.parse("2026-08-27T04:00:00Z"))

        assertTrue(publisher.hasCurrentDormitoryMeal(LocalDate.of(2026, 8, 27)))
        assertEquals(false, publisher.hasCurrentDormitoryMeal(LocalDate.of(2026, 8, 31)))
    }

    @Test
    fun `주말에는 다음 월요일 식단도 중복 제출로 판정한다`() {
        val output = Files.createTempDirectory("dima-dorm-weekend-dedupe")
        val publisher = StaticDataPublisher(output)
        val payload = dormitoryPayload().copy(
            weekStart = "2026-08-31",
            weekEnd = "2026-09-06",
            days = listOf(
                DormitoryMealDayPayload("2026-08-31", listOf(DormitoryMealSectionPayload("중식", null, listOf("제육볶음")))),
            ),
        )
        publisher.publishDormitoryMeal(payload, 1, Instant.parse("2026-08-30T04:00:00Z"))

        assertTrue(publisher.hasCurrentDormitoryMeal(LocalDate.of(2026, 8, 30)))
    }

    @Test
    fun `검증된 셔틀을 해시 payload와 manifest로 게시한다`() {
        val output = Files.createTempDirectory("dima-publish")
        val csv = """
            운행요일,노선ID,출발구역,승차정류장,목적구역,출발시각,도착시각
            월,B,본관,본관,엔터관,08:10,08:15
        """.trimIndent()

        StaticDataPublisher(output).publishShuttle(
            csv = csv,
            revision = 7,
            publishedAt = Instant.parse("2026-08-28T01:02:03Z"),
        )

        val manifest = Json.decodeFromString<CampusDataManifest>(Files.readString(output.resolve("data/v1/manifest.json")))
        val shuttle = manifest.datasets.getValue("shuttle")
        assertEquals(1, manifest.schemaVersion)
        assertEquals(7, shuttle.revision)
        assertEquals("READY", shuttle.state)
        assertEquals("https://www.dima.ac.kr/?p=97", shuttle.sourceUrl)
        assertTrue(shuttle.url.matches(Regex("shuttle/[0-9a-f]{64}\\.json")))
        assertEquals(shuttle.sha256, shuttle.url.substringAfter('/').substringBefore('.'))
        assertTrue(Files.exists(output.resolve("data/v1").resolve(shuttle.url)))
    }

    @Test
    fun `식단 후보 검증 실패는 마지막 정상 payload를 보존한다`() {
        val output = Files.createTempDirectory("dima-meal-preserve")
        val publisher = StaticDataPublisher(output)
        publisher.publishMeal(
            payload = com.example.dimanow.sync.MealPayload(
                weekStart = "2026-08-24",
                weekEnd = "2026-08-30",
                days = listOf(
                    com.example.dimanow.sync.MealDayPayload(
                        date = "2026-08-24",
                        menuLines = listOf("쌀밥", "된장국"),
                        hours = "11:30 ~ 14:00",
                        sourceUrl = "https://www.instagram.com/p/example/",
                        sourceImageUrl = "https://scontent.example/meal.jpg",
                    ),
                ),
            ),
            revision = 4,
            publishedAt = Instant.parse("2026-08-28T01:00:00Z"),
        )
        val before = Json.decodeFromString<CampusDataManifest>(Files.readString(output.resolve("data/v1/manifest.json")))
            .datasets.getValue("meal")

        publisher.recordFailure("meal", "NEEDS_REVIEW", "날짜 헤더 부족", Instant.parse("2026-08-28T02:00:00Z"))

        val after = Json.decodeFromString<CampusDataManifest>(Files.readString(output.resolve("data/v1/manifest.json")))
            .datasets.getValue("meal")
        assertEquals(4, after.revision)
        assertEquals(before.url, after.url)
        assertEquals(before.sha256, after.sha256)
        assertEquals("NEEDS_REVIEW", after.state)
        assertEquals("날짜 헤더 부족", after.message)
    }

    @Test
    fun `내용이 같은 재게시에는 revision과 최초 게시 시각을 유지한다`() {
        val output = Files.createTempDirectory("dima-stable-revision")
        val publisher = StaticDataPublisher(output)
        val csv = """
            운행요일,노선ID,출발구역,승차정류장,목적구역,출발시각,도착시각
            월,B,본관,본관,엔터관,08:10,08:15
        """.trimIndent()
        publisher.publishShuttle(csv, 1, Instant.parse("2026-08-28T01:00:00Z"))

        publisher.publishShuttle(csv, publisher.nextRevision("shuttle"), Instant.parse("2026-08-28T07:00:00Z"))

        val descriptor = Json.decodeFromString<CampusDataManifest>(Files.readString(output.resolve("data/v1/manifest.json")))
            .datasets.getValue("shuttle")
        assertEquals(1, descriptor.revision)
        assertEquals("2026-08-28T01:00:00Z", descriptor.publishedAt)
        assertEquals("2026-08-28T07:00:00Z", descriptor.lastAttemptAt)
    }

    private fun dormitoryPayload() = DormitoryMealPayload(
        weekStart = "2026-08-24",
        weekEnd = "2026-08-30",
        sourceImageUrl = "https://raw.githubusercontent.com/winter1l/DimaNow/dorm-submissions/dorm-submissions/example.jpg",
        days = listOf(
            DormitoryMealDayPayload(
                date = "2026-08-24",
                sections = listOf(DormitoryMealSectionPayload("조식", "08:00~09:30", listOf("떡국"))),
            ),
        ),
    )
}
