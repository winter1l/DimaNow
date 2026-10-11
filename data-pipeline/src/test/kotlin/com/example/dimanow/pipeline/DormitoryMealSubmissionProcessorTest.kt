package com.example.dimanow.pipeline

import com.example.dimanow.sync.CampusDataManifest
import com.example.dimanow.sync.DormitoryMealPayload
import com.example.dimanow.sync.DormitoryMealSubmissionStatus
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DormitoryMealSubmissionProcessorTest {
    @Test
    fun `검증과 OCR을 통과한 기숙사 식단은 운영자 승인 없이 새 revision으로 자동 게시한다`() {
        val output = Files.createTempDirectory("dima-dorm-process-publish")
        val gemini = FakeGemini(ocrResponse = week(8, 24))
        gemini.use {
            val result = process(output, gemini, THURSDAY, "submission-3")

            assertEquals(DormitoryMealSubmissionStatus("submission-3", "PUBLISHED", null, THURSDAY.toString()), result)
            assertEquals(2, gemini.calls)
            assertEquals(result, readStatus(output, "submission-3"))
            val descriptor = readManifest(output).datasets.getValue("dorm_meal")
            assertEquals("READY", descriptor.state)
            assertEquals(1, descriptor.revision)
            assertEquals(THURSDAY.toString(), descriptor.publishedAt)
            val published = Json.decodeFromString<DormitoryMealPayload>(
                Files.readString(output.resolve("data/v1").resolve(descriptor.url)),
            )
            assertEquals("2026-08-24", published.weekStart)
            assertEquals("2026-08-30", published.weekEnd)
            assertEquals(sourceUrl("submission-3"), published.sourceImageUrl)
            assertEquals(listOf("2026-08-24", "2026-08-25", "2026-08-26", "2026-08-27", "2026-08-28"), published.days.map { it.date })
            assertFalse(Files.exists(output.resolve("data/v1/dorm-review-candidates")))
            assertFalse(Files.exists(output.resolve("data/v1/dorm-review-approvals")))
        }
    }

    @Test
    fun `지난 주 식단이 게시된 상태에서 새 주 제출은 다음 revision으로 교체한다`() {
        val output = Files.createTempDirectory("dima-dorm-process-next-revision")
        FakeGemini(ocrResponse = week(8, 24)).use { process(output, it, THURSDAY, "week-1") }
        FakeGemini(ocrResponse = week(8, 31)).use { gemini ->
            val result = process(output, gemini, Instant.parse("2026-09-01T03:00:00Z"), "week-2")

            assertEquals("PUBLISHED", result.state)
            val descriptor = readManifest(output).datasets.getValue("dorm_meal")
            assertEquals(2, descriptor.revision)
            val published = Json.decodeFromString<DormitoryMealPayload>(
                Files.readString(output.resolve("data/v1").resolve(descriptor.url)),
            )
            assertEquals("2026-08-31", published.weekStart)
        }
    }

    @Test
    fun `현재 주가 이미 게시됐으면 Gemini 호출 없이 중복 상태를 게시하고 기존 식단을 유지한다`() {
        val output = Files.createTempDirectory("dima-dorm-process-dedupe")
        FakeGemini(ocrResponse = week(8, 24)).use { process(output, it, THURSDAY, "seed-current-week") }
        val before = readManifest(output).datasets.getValue("dorm_meal")

        FakeGemini(ocrResponse = week(8, 24)).use { gemini ->
            val result = process(output, gemini, THURSDAY.plusSeconds(3_600), "submission-1")

            assertEquals("DUPLICATE", result.state)
            assertEquals("이번 주 기숙사 식단이 이미 등록되어 있어요", result.message)
            assertEquals(0, gemini.calls)
            assertEquals(result, readStatus(output, "submission-1"))
            assertEquals(before, readManifest(output).datasets.getValue("dorm_meal"))
        }
    }

    @Test
    fun `검증이 거절되면 기존 식단을 건드리지 않고 짧은 사유를 게시한다`() {
        val output = Files.createTempDirectory("dima-dorm-process-reject")
        val gemini = FakeGemini(
            validationResponse = """{"동아방송예술대 기숙사 식단이 맞는가?":false,"식단표가 전부 보이는가?":true,"모두 true가 아닌 경우 사용자에게 알려줄 간단 사유":"기숙사 식단표가 아니에요"}""",
            ocrResponse = week(8, 24),
        )
        gemini.use {
            val result = process(output, gemini, THURSDAY, "submission-2")

            assertEquals("REJECTED", result.state)
            assertEquals("기숙사 식단표가 아니에요", result.message)
            assertEquals(1, gemini.calls)
            assertEquals(result, readStatus(output, "submission-2"))
            assertFalse(Files.exists(output.resolve("data/v1/manifest.json")))
        }
    }

    @Test
    fun `OCR 결과가 불완전하면 재촬영 사유로 거절하고 마지막 정상 식단을 보존한다`() {
        val output = Files.createTempDirectory("dima-dorm-process-incomplete")
        FakeGemini(ocrResponse = week(8, 17)).use { process(output, it, Instant.parse("2026-08-18T03:00:00Z"), "last-good") }
        val before = readManifest(output).datasets.getValue("dorm_meal")
        val fourDays = """{"days":[""" + (24..27).joinToString(",") { day ->
            """{"month":8,"day":$day,"sections":[{"name":"조식","menuLines":["떡국"]}]}"""
        } + "]}"

        FakeGemini(ocrResponse = fourDays).use { gemini ->
            val result = process(output, gemini, THURSDAY, "incomplete")

            assertEquals("REJECTED", result.state)
            assertEquals("식단표의 날짜와 메뉴가 잘 보이도록 다시 촬영해 주세요", result.message)
            assertEquals(before, readManifest(output).datasets.getValue("dorm_meal"))
        }
    }

    @Test
    fun `기숙사 표가 맞아도 지난 주 식단이면 사용자 사유와 함께 거절한다`() {
        val output = Files.createTempDirectory("dima-dorm-process-stale")
        FakeGemini(ocrResponse = week(8, 24)).use { gemini ->
            val result = process(output, gemini, Instant.parse("2026-08-31T03:00:00Z"), "submission-stale")

            assertEquals("REJECTED", result.state)
            assertEquals("이번 주 기숙사 식단표가 아니에요", result.message)
            assertFalse(Files.exists(output.resolve("data/v1/manifest.json")))
        }
    }

    @Test
    fun `주말에는 다음 월요일 주차만 게시하고 끝나가는 주 식단은 거절한다`() {
        val saturday = Instant.parse("2026-08-29T03:00:00Z")
        val endingWeekOutput = Files.createTempDirectory("dima-dorm-process-weekend-ending")
        FakeGemini(ocrResponse = week(8, 24)).use { gemini ->
            val result = process(endingWeekOutput, gemini, saturday, "weekend-ending")

            assertEquals("REJECTED", result.state)
            assertEquals("이번 주 기숙사 식단표가 아니에요", result.message)
            assertFalse(Files.exists(endingWeekOutput.resolve("data/v1/manifest.json")))
        }

        val nextWeekOutput = Files.createTempDirectory("dima-dorm-process-weekend-next")
        FakeGemini(ocrResponse = week(8, 31)).use { gemini ->
            val result = process(nextWeekOutput, gemini, saturday, "weekend-next")

            assertEquals("PUBLISHED", result.state)
            val descriptor = readManifest(nextWeekOutput).datasets.getValue("dorm_meal")
            val published = Json.decodeFromString<DormitoryMealPayload>(
                Files.readString(nextWeekOutput.resolve("data/v1").resolve(descriptor.url)),
            )
            assertEquals("2026-08-31", published.weekStart)
        }
        FakeGemini(ocrResponse = week(8, 31)).use { gemini ->
            val result = process(nextWeekOutput, gemini, Instant.parse("2026-08-30T03:00:00Z"), "weekend-duplicate")

            assertEquals("DUPLICATE", result.state)
            assertEquals(0, gemini.calls)
        }
    }

    @Test
    fun `불변 GitHub 원본 주소가 아니면 Gemini 호출과 게시 없이 실패한다`() {
        val output = Files.createTempDirectory("dima-dorm-process-bad-source")
        FakeGemini(ocrResponse = week(8, 24)).use { gemini ->
            for (url in listOf(
                "https://raw.githubusercontent.com/winter1l/DimaNow/example.jpg",
                "https://example.invalid/winter1l/DimaNow/${"0".repeat(40)}/dorm-submissions/bad-source.jpg",
                sourceUrl("other-submission"),
            )) {
                assertThrows(IllegalArgumentException::class.java) {
                    processor(output, gemini, THURSDAY).process(image(), "image/jpeg", url, "bad-source")
                }
            }
            assertEquals(0, gemini.calls)
            assertFalse(Files.exists(output.resolve("data/v1/manifest.json")))
        }
    }

    @Test
    fun `OCR 메뉴에 URL이 있으면 고정 사유로 거절하고 마지막 정상 식단을 유지한다`() {
        val output = Files.createTempDirectory("dima-dorm-process-url")
        FakeGemini(ocrResponse = week(8, 17)).use { process(output, it, Instant.parse("2026-08-18T03:00:00Z"), "last-good") }
        val before = readManifest(output).datasets.getValue("dorm_meal")

        for ((index, line) in listOf("쿠폰 https://bit.ly/x", "문의 010-1234-5678", "메뉴\u0007", "가".repeat(61)).withIndex()) {
            FakeGemini(ocrResponse = week(8, 24, wednesdayLines = listOf(line))).use { gemini ->
                val result = process(output, gemini, THURSDAY, "content-$index")

                assertEquals(line, "REJECTED", result.state)
                assertEquals(DormitoryMealContentPolicy.REJECTION_REASON, result.message)
                assertEquals(before, readManifest(output).datasets.getValue("dorm_meal"))
            }
        }
        FakeGemini(ocrResponse = week(8, 24, wednesdayLines = List(16) { "메뉴$it" })).use { gemini ->
            assertEquals(DormitoryMealContentPolicy.REJECTION_REASON, process(output, gemini, THURSDAY, "too-many-lines").message)
        }
        assertEquals(before, readManifest(output).datasets.getValue("dorm_meal"))
    }

    @Test
    fun `Gemini 거절 사유는 한 줄로 줄이고 링크가 있으면 고정 사유를 게시한다`() {
        val output = Files.createTempDirectory("dima-dorm-process-reason")
        val reasons = mapOf(
            "자세한 안내는 https://evil.example 에서\n두 번째 줄" to DormitoryMealContentPolicy.FALLBACK_REASON,
            "식단표가 잘려 있어요\n추가 설명" to "식단표가 잘려 있어요",
            "가".repeat(200) to DormitoryMealContentPolicy.FALLBACK_REASON,
        )
        for ((index, entry) in reasons.entries.withIndex()) {
            val validation = """{"동아방송예술대 기숙사 식단이 맞는가?":false,"식단표가 전부 보이는가?":true,""" +
                """"모두 true가 아닌 경우 사용자에게 알려줄 간단 사유":${Json.encodeToString(entry.key)}}"""
            FakeGemini(validationResponse = validation, ocrResponse = week(8, 24)).use { gemini ->
                val result = process(output, gemini, THURSDAY, "reason-$index")

                assertEquals("REJECTED", result.state)
                assertEquals(entry.value, result.message)
                assertEquals(result, readStatus(output, "reason-$index"))
            }
        }
    }

    @Test
    fun `이미 최종 상태가 있는 제출은 다시 처리하지 않고 기존 상태를 돌려준다`() {
        val output = Files.createTempDirectory("dima-dorm-process-idempotent")
        val published = FakeGemini(ocrResponse = week(8, 24)).use { process(output, it, THURSDAY, "once") }
        val manifest = readManifest(output)

        FakeGemini(ocrResponse = week(8, 24)).use { gemini ->
            assertEquals(published, process(output, gemini, THURSDAY.plusSeconds(60), "once"))
            assertEquals(0, gemini.calls)
        }
        assertEquals(published, readStatus(output, "once"))
        assertEquals(manifest, readManifest(output))
    }

    @Test
    fun `레거시 6인자 publish-dorm-meal 명령 계약을 유지한다`() {
        val output = Files.createTempDirectory("dima-dorm-cli-legacy")
        val published = FakeGemini(ocrResponse = week(8, 24)).use { process(output, it, THURSDAY, "legacy") }

        // The legacy workflow on the dorm-submissions branch still calls this exact shape with main's pipeline code.
        main(arrayOf("publish-dorm-meal", image().toString(), output.toString(), "legacy", sourceUrl("legacy"), "image/jpeg"))

        assertEquals(published, readStatus(output, "legacy"))
        assertThrows(IllegalArgumentException::class.java) {
            main(arrayOf("publish-dorm-meal", image().toString(), output.toString(), "legacy", sourceUrl("legacy")))
        }
    }

    @Test
    fun `대기 중인 두 제출을 오래된 순서로 모두 처리한다`() {
        val output = Files.createTempDirectory("dima-dorm-batch-two")
        val root = submissionRoot("older", "newer")
        val submissions = listOf(
            pending("newer", THURSDAY.minusSeconds(60), 'b'),
            pending("older", THURSDAY.minusSeconds(3_600), 'a'),
        )

        FakeGemini(ocrResponse = week(8, 24)).use { gemini ->
            val results = processor(output, gemini, THURSDAY).processPending(root, submissions)

            assertEquals(listOf("older" to "PUBLISHED", "newer" to "DUPLICATE"), results.map { it.submissionId to it.state })
            assertEquals(2, gemini.calls)
            assertEquals("PUBLISHED", readStatus(output, "older").state)
            assertEquals("DUPLICATE", readStatus(output, "newer").state)
            val descriptor = readManifest(output).datasets.getValue("dorm_meal")
            val payload = Json.decodeFromString<DormitoryMealPayload>(Files.readString(output.resolve("data/v1").resolve(descriptor.url)))
            assertEquals(
                "https://raw.githubusercontent.com/winter1l/DimaNow/${"a".repeat(40)}/dorm-submissions/older.jpg",
                payload.sourceImageUrl,
            )
        }
    }

    @Test
    fun `이미 상태가 있는 제출은 일괄 처리에서 건너뛴다`() {
        val output = Files.createTempDirectory("dima-dorm-batch-skip")
        val root = submissionRoot("done", "pending")
        val existing = DormitoryMealSubmissionStatus("done", "ERROR", "식단 사진을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.", "2026-08-27T00:00:00Z")
        StaticDataPublisher(output).publishDormitorySubmissionStatus(existing)

        FakeGemini(ocrResponse = week(8, 24)).use { gemini ->
            val results = processor(output, gemini, THURSDAY).processPending(
                root,
                listOf(pending("done", THURSDAY.minusSeconds(7_200), 'c'), pending("pending", THURSDAY.minusSeconds(60), 'd')),
            )

            assertEquals(listOf("pending"), results.map { it.submissionId })
            assertEquals("PUBLISHED", readStatus(output, "pending").state)
            assertEquals(existing, readStatus(output, "done"))
        }
    }

    @Test
    fun `일괄 처리는 실행당 상한을 지키고 남은 제출은 다음 실행에서 처리한다`() {
        val output = Files.createTempDirectory("dima-dorm-batch-cap")
        val ids = (1..12).map { "stale-%02d".format(it) }
        val root = submissionRoot(*ids.toTypedArray())
        // Committed in the previous target week: rejected without any Gemini call.
        val submissions = ids.mapIndexed { index, id -> pending(id, Instant.parse("2026-08-18T03:00:00Z").plusSeconds(index * 60L), 'e') }

        FakeGemini(ocrResponse = week(8, 24)).use { gemini ->
            val first = processor(output, gemini, THURSDAY).processPending(root, submissions)
            assertEquals(DormitoryMealSubmissionProcessor.MAX_SUBMISSIONS_PER_RUN, first.size)
            assertEquals(ids.take(10), first.map { it.submissionId })
            assertTrue(first.all { it.state == "REJECTED" && it.message == DormitoryMealSubmissionProcessor.STALE_SUBMISSION_REASON })
            assertFalse(Files.exists(output.resolve("data/v1/dorm-submissions/stale-11.json")))

            val second = processor(output, gemini, THURSDAY).processPending(root, submissions)
            assertEquals(ids.drop(10), second.map { it.submissionId })
            assertEquals(0, gemini.calls)
            assertFalse(Files.exists(output.resolve("data/v1/manifest.json")))
        }
    }

    @Test
    fun `일괄 처리 중 한 제출이 실패해도 ERROR를 남기고 나머지를 계속 처리한다`() {
        val output = Files.createTempDirectory("dima-dorm-batch-error")
        val root = submissionRoot("present")
        FakeGemini(ocrResponse = week(8, 24)).use { gemini ->
            val results = processor(output, gemini, THURSDAY).processPending(
                root,
                listOf(pending("missing-file", THURSDAY.minusSeconds(600), 'f'), pending("present", THURSDAY.minusSeconds(60), '1')),
            )

            assertEquals(listOf("missing-file" to "ERROR", "present" to "PUBLISHED"), results.map { it.submissionId to it.state })
            assertEquals(DormitoryMealSubmissionProcessor.PROCESSING_ERROR_REASON, readStatus(output, "missing-file").message)
        }
    }

    @Test
    fun `대기 목록은 정확한 커밋과 경로 형식만 받는다`() {
        val sha = "0123456789abcdef0123456789abcdef01234567"
        val invalid = mutableListOf<String>()
        val parsed = PendingDormitorySubmission.parseIndex(
            listOf(
                "$sha 1788134625 dorm-submissions/ok-1.jpg",
                "$sha 1788134625 dorm-submissions/../secret.jpg",
                "$sha 1788134625 dorm-submissions/x.gif",
                "${sha.uppercase()} 1788134625 dorm-submissions/upper.jpg",
                "$sha -1 dorm-submissions/negative.jpg",
                "$sha 1788134625 dorm-submissions/a.png extra",
                "$sha 1788134625 dorm-submissions/ok-2.webp",
            ).joinToString("\n"),
        ) { invalid += it }

        assertEquals(listOf("ok-1", "ok-2"), parsed.map { it.submissionId })
        assertEquals(listOf("image/jpeg", "image/webp"), parsed.map { it.mimeType })
        assertEquals("https://raw.githubusercontent.com/winter1l/DimaNow/$sha/dorm-submissions/ok-1.jpg", parsed.first().sourceImageUrl)
        assertEquals(5, invalid.size)
    }

    private fun submissionRoot(vararg ids: String): Path = Files.createTempDirectory("dima-dorm-submissions").also { root ->
        Files.createDirectories(root.resolve("dorm-submissions"))
        ids.forEach { Files.write(root.resolve("dorm-submissions/$it.jpg"), byteArrayOf(7, 8, 9)) }
    }

    private fun pending(id: String, committedAt: Instant, shaChar: Char) =
        PendingDormitorySubmission(shaChar.toString().repeat(40), committedAt, "dorm-submissions/$id.jpg")

    private fun process(output: Path, gemini: FakeGemini, now: Instant, submissionId: String): DormitoryMealSubmissionStatus =
        processor(output, gemini, now).process(image(), "image/jpeg", sourceUrl(submissionId), submissionId)

    private fun processor(output: Path, gemini: FakeGemini, now: Instant) = DormitoryMealSubmissionProcessor(
        publisher = StaticDataPublisher(output),
        geminiClient = GeminiDormitoryMealClient("test", gemini.baseUrl),
        clock = Clock.fixed(now, ZoneId.of("Asia/Seoul")),
    )

    private fun image(): Path = Files.createTempFile("dorm", ".jpg").also { Files.write(it, byteArrayOf(7, 8, 9)) }

    private fun readStatus(output: Path, submissionId: String): DormitoryMealSubmissionStatus =
        Json.decodeFromString(Files.readString(output.resolve("data/v1/dorm-submissions/$submissionId.json")))

    private fun readManifest(output: Path): CampusDataManifest =
        Json.decodeFromString(Files.readString(output.resolve("data/v1/manifest.json")))

    private fun week(month: Int, mondayDay: Int, wednesdayLines: List<String>? = null): String {
        val monday = java.time.LocalDate.of(2026, month, mondayDay)
        return """{"days":[""" + (0L..4L).joinToString(",") { offset ->
            val date = monday.plusDays(offset)
            val lines = Json.encodeToString(if (offset == 2L && wednesdayLines != null) wednesdayLines else listOf("메뉴$offset"))
            """{"month":${date.monthValue},"day":${date.dayOfMonth},"sections":[{"name":"조식","menuLines":$lines}]}"""
        } + "]}"
    }

    private class FakeGemini(
        private val validationResponse: String = """{"동아방송예술대 기숙사 식단이 맞는가?":true,"식단표가 전부 보이는가?":true}""",
        private val ocrResponse: String,
    ) : AutoCloseable {
        var calls = 0
            private set
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v1beta/models/gemini-3.5-flash-lite:generateContent") { exchange ->
                exchange.requestBody.readBytes()
                val text = if (calls++ == 0) validationResponse else ocrResponse
                val response = """{"candidates":[{"content":{"parts":[{"text":${Json.encodeToString(text)}}]}}]}""".toByteArray()
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            start()
        }
        val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

        override fun close() = server.stop(0)
    }

    private companion object {
        val THURSDAY: Instant = Instant.parse("2026-08-27T03:00:00Z")

        fun sourceUrl(submissionId: String): String =
            "https://raw.githubusercontent.com/winter1l/DimaNow/${"0".repeat(40)}/dorm-submissions/$submissionId.jpg"
    }
}
