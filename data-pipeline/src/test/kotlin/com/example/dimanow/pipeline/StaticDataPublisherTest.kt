package com.example.dimanow.pipeline

import com.example.dimanow.sync.CampusDataManifest
import com.example.dimanow.sync.DormitoryMealDayPayload
import com.example.dimanow.sync.DormitoryMealPayload
import com.example.dimanow.sync.DormitoryMealSectionPayload
import com.example.dimanow.sync.DormitoryMealSubmissionStatus
import java.nio.file.Files
import java.time.LocalDate
import java.time.Instant
import org.junit.Assert.assertThrows
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticDataPublisherTest {
    @Test
    fun `scheduled collection stops only after the full current student meal week is published`() {
        val publisher = StaticDataPublisher(Files.createTempDirectory("student-meal-watch"))
        val monday = LocalDate.parse("2026-09-07")
        assertTrue(publisher.shouldCollectStudentMeal(monday))
        publisher.publishMeal(
            com.example.dimanow.sync.MealPayload(
                weekStart = "2026-09-07", weekEnd = "2026-09-13",
                days = (0L..4L).map { offset ->
                    com.example.dimanow.sync.MealDayPayload(
                        monday.plusDays(offset).toString(), listOf("쌀밥", "된장국"),
                        "11:00 ~ 14:00", "https://www.instagram.com/p/example/", "https://example.com/meal.jpg",
                    )
                },
            ), 1, Instant.parse("2026-09-07T03:00:00Z"),
        )
        assertEquals(false, publisher.shouldCollectStudentMeal(monday))
        assertEquals(false, publisher.shouldCollectStudentMeal(LocalDate.parse("2026-09-13")))
        assertTrue(publisher.shouldCollectStudentMeal(LocalDate.parse("2026-09-14")))
        publisher.recordFailure("meal", "NEEDS_REVIEW", "retry", Instant.parse("2026-09-07T04:00:00Z"))
        assertTrue(publisher.shouldCollectStudentMeal(monday))
    }

    @Test
    fun `기숙사 식단 후보는 운영자 승인 전에는 READY로 게시하지 않는다`() {
        val output = Files.createTempDirectory("dima-dorm-meal")
        val publisher = StaticDataPublisher(output)
        val payload = dormitoryPayload()

        val candidateHash = publisher.stageDormitoryMealReviewCandidate(
            payload = payload,
            submissionId = "submission-1",
            sourceImageSha256 = "a".repeat(64),
            createdAt = Instant.parse("2026-08-27T03:00:00Z"),
        )

        assertEquals(false, Files.exists(output.resolve("data/v1/manifest.json")))
        assertTrue(candidateHash.matches(Regex("[0-9a-f]{64}")))
        assertTrue(Files.exists(output.resolve("data/v1/dorm-review-candidates/submission-1.json")))
    }

    @Test
    fun `인증된 운영자가 정확한 후보 해시를 승인해야 READY와 PUBLISHED를 쓴다`() {
        val output = Files.createTempDirectory("dima-dorm-approval")
        val publisher = StaticDataPublisher(output)
        val candidateHash = publisher.stageDormitoryMealReviewCandidate(
            payload = dormitoryPayload(),
            submissionId = "submission-1",
            sourceImageSha256 = "a".repeat(64),
            createdAt = Instant.parse("2026-08-27T03:00:00Z"),
        )

        publisher.approveDormitoryMeal(
            submissionId = "submission-1",
            expectedCandidateSha256 = candidateHash,
            approvedBy = "repository-reviewer",
            approvedAt = Instant.parse("2026-08-27T04:00:00Z"),
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
        val approval = Json.decodeFromString<DormitoryMealApprovalRecord>(
            Files.readString(output.resolve("data/v1/dorm-review-approvals/submission-1.json")),
        )
        assertEquals(candidateHash, approval.candidateSha256)
        assertEquals("repository-reviewer", approval.approvedBy)
    }

    @Test
    fun `후보 파일이나 승인 해시가 바뀌면 게시를 거부한다`() {
        val output = Files.createTempDirectory("dima-dorm-tamper")
        val publisher = StaticDataPublisher(output)
        val candidateHash = publisher.stageDormitoryMealReviewCandidate(
            payload = dormitoryPayload(),
            submissionId = "submission-1",
            sourceImageSha256 = "a".repeat(64),
            createdAt = Instant.parse("2026-08-27T03:00:00Z"),
        )

        assertThrows(IllegalArgumentException::class.java) {
            publisher.approveDormitoryMeal(
                submissionId = "submission-1",
                expectedCandidateSha256 = "b".repeat(64),
                approvedBy = "repository-reviewer",
                approvedAt = Instant.parse("2026-08-27T04:00:00Z"),
            )
        }
        Files.writeString(
            output.resolve("data/v1/dorm-review-candidates/submission-1.json"),
            "{}",
        )
        assertThrows(IllegalArgumentException::class.java) {
            publisher.approveDormitoryMeal(
                submissionId = "submission-1",
                expectedCandidateSha256 = candidateHash,
                approvedBy = "repository-reviewer",
                approvedAt = Instant.parse("2026-08-27T04:00:00Z"),
            )
        }
        assertEquals(false, Files.exists(output.resolve("data/v1/manifest.json")))
    }

    @Test
    fun `같은 제출 ID의 후보 교체와 승인 없는 PUBLISHED 상태를 거부한다`() {
        val output = Files.createTempDirectory("dima-dorm-candidate-replace")
        val publisher = StaticDataPublisher(output)
        publisher.stageDormitoryMealReviewCandidate(
            payload = dormitoryPayload(),
            submissionId = "submission-1",
            sourceImageSha256 = "a".repeat(64),
            createdAt = Instant.parse("2026-08-27T03:00:00Z"),
        )

        assertThrows(IllegalArgumentException::class.java) {
            publisher.stageDormitoryMealReviewCandidate(
                payload = dormitoryPayload().copy(weekEnd = "2026-08-31"),
                submissionId = "submission-1",
                sourceImageSha256 = "a".repeat(64),
                createdAt = Instant.parse("2026-08-27T03:01:00Z"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            publisher.publishDormitorySubmissionStatus(
                DormitoryMealSubmissionStatus("submission-1", "PUBLISHED", null, "2026-08-27T04:00:00Z"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            publisher.stageDormitoryMealReviewCandidate(
                payload = dormitoryPayload().copy(sourceImageUrl = "https://example.invalid/submission-evil.jpg"),
                submissionId = "submission-evil",
                sourceImageSha256 = "b".repeat(64),
                createdAt = Instant.parse("2026-08-27T03:02:00Z"),
            )
        }
        assertEquals(false, Files.exists(output.resolve("data/v1/manifest.json")))
    }

    @Test
    fun `운영자 승인 시점에 대상 주차가 지난 후보는 게시하지 않는다`() {
        val output = Files.createTempDirectory("dima-dorm-stale-approval")
        val publisher = StaticDataPublisher(output)
        val candidateHash = publisher.stageDormitoryMealReviewCandidate(
            payload = dormitoryPayload(),
            submissionId = "submission-1",
            sourceImageSha256 = "a".repeat(64),
            createdAt = Instant.parse("2026-08-27T03:00:00Z"),
        )

        assertThrows(IllegalArgumentException::class.java) {
            publisher.approveDormitoryMeal(
                submissionId = "submission-1",
                expectedCandidateSha256 = candidateHash,
                approvedBy = "repository-reviewer",
                approvedAt = Instant.parse("2026-09-03T04:00:00Z"),
            )
        }
        assertEquals(false, Files.exists(output.resolve("data/v1/manifest.json")))
    }

    @Test
    fun `현재 주 기숙사 식단이 있으면 새 Gemini 처리를 막는다`() {
        val output = Files.createTempDirectory("dima-dorm-dedupe")
        val publisher = StaticDataPublisher(output)
        publishApprovedDormitoryMeal(publisher, dormitoryPayload(), "seed-1", Instant.parse("2026-08-27T04:00:00Z"))

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
        publishApprovedDormitoryMeal(publisher, payload, "seed-2", Instant.parse("2026-08-30T04:00:00Z"))

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
        sourceImageUrl = dormitorySourceUrl("submission-1"),
        days = listOf(
            DormitoryMealDayPayload(
                date = "2026-08-24",
                sections = listOf(DormitoryMealSectionPayload("조식", "08:00~09:30", listOf("떡국"))),
            ),
        ),
    )

    private fun publishApprovedDormitoryMeal(
        publisher: StaticDataPublisher,
        payload: DormitoryMealPayload,
        submissionId: String,
        approvedAt: Instant,
    ) {
        val candidateHash = publisher.stageDormitoryMealReviewCandidate(
            payload = payload.copy(sourceImageUrl = dormitorySourceUrl(submissionId)),
            submissionId = submissionId,
            sourceImageSha256 = "a".repeat(64),
            createdAt = approvedAt.minusSeconds(60),
        )
        publisher.approveDormitoryMeal(submissionId, candidateHash, "test-reviewer", approvedAt)
    }

    private fun dormitorySourceUrl(submissionId: String): String =
        "https://raw.githubusercontent.com/winter1l/DimaNow/${"0".repeat(40)}/dorm-submissions/$submissionId.jpg"
}
