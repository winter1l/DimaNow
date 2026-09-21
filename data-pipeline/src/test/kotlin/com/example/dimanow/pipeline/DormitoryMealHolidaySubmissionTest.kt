package com.example.dimanow.pipeline

import com.example.dimanow.sync.CampusDataManifest
import com.example.dimanow.sync.DormitoryMealPayload
import com.example.dimanow.sync.DormitoryMealSubmissionStatus
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DormitoryMealHolidaySubmissionTest {
    @Test
    fun `휴일 한 줄과 부분 휴무를 게시할 때 원문 그대로 보존한다`() = withSubmission(
        thursdaySections = """[{"name":"안내","menuLines":["추석 공휴일"]}]""",
    ) { result, _, output ->
        assertEquals("PUBLISHED", result.state)
        val manifest = Json.decodeFromString<CampusDataManifest>(Files.readString(output.resolve("data/v1/manifest.json")))
        val descriptor = manifest.datasets.getValue("dorm_meal")
        assertEquals("READY", descriptor.state)
        val published = Json.decodeFromString<DormitoryMealPayload>(Files.readString(output.resolve("data/v1/${descriptor.url}")))
        assertEquals(listOf("추석 공휴일"), published.days[3].sections.single().menuLines)
        assertEquals(null, published.days[3].sections.single().hours)
        assertEquals(listOf("쌀밥", "된장국"), published.days[0].sections[0].menuLines)
        assertEquals(listOf("휴무"), published.days[0].sections[1].menuLines)
        val status = Json.decodeFromString<DormitoryMealSubmissionStatus>(
            Files.readString(output.resolve("data/v1/dorm-submissions/holiday-test.json")),
        )
        assertEquals(result, status)
    }
    @Test
    fun `휴일을 빈 식단으로 인식하면 내용을 만들지 않고 재촬영 상태를 남긴다`() = withSubmission(
        thursdaySections = "[]",
    ) { result, _, output ->
        assertEquals("REJECTED", result.state)
        assertEquals("식단표의 날짜와 메뉴가 잘 보이도록 다시 촬영해 주세요", result.message)
        val status = Json.decodeFromString<DormitoryMealSubmissionStatus>(
            Files.readString(output.resolve("data/v1/dorm-submissions/holiday-test.json")),
        )
        assertEquals(result, status)
        assertFalse(Files.exists(output.resolve("data/v1/dorm-review-candidates/holiday-test.json")))
        assertFalse(Files.exists(output.resolve("data/v1/manifest.json")))
    }

    private fun withSubmission(
        thursdaySections: String,
        check: (DormitoryMealSubmissionStatus, StaticDataPublisher, java.nio.file.Path) -> Unit,
    ) {
        val output = Files.createTempDirectory("dima-dorm-holiday")
        val image = output.resolve("synthetic.jpg").also { Files.write(it, byteArrayOf(1, 2, 3)) }
        var calls = 0
        val response = """{"days":[
          {"month":9,"day":21,"sections":[{"name":"조식","menuLines":["쌀밥","된장국"]},{"name":"석식","menuLines":["휴무"]}]},
          {"month":9,"day":22,"sections":[{"name":"조식","menuLines":["떡국"]}]},
          {"month":9,"day":23,"sections":[{"name":"조식","menuLines":["미역국"]}]},
          {"month":9,"day":24,"sections":$thursdaySections},
          {"month":9,"day":25,"sections":[{"name":"안내","menuLines":["추석"]}]}
        ]}"""
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                exchange.requestBody.readBytes()
                val text = if (calls++ == 0) {
                    """{"동아방송예술대 기숙사 식단이 맞는가?":true,"식단표가 전부 보이는가?":true}"""
                } else response
                val envelope = """{"candidates":[{"content":{"parts":[{"text":${Json.encodeToString(text)}}]}}]}""".toByteArray()
                exchange.sendResponseHeaders(200, envelope.size.toLong())
                exchange.responseBody.use { it.write(envelope) }
            }
            start()
        }
        try {
            val publisher = StaticDataPublisher(output)
            val result = DormitoryMealSubmissionProcessor(
                publisher,
                GeminiDormitoryMealClient("test", "http://127.0.0.1:${server.address.port}"),
                Clock.fixed(NOW, ZoneId.of("Asia/Seoul")),
            ).process(image, "image/jpeg", "https://raw.githubusercontent.com/winter1l/DimaNow/${"0".repeat(40)}/dorm-submissions/holiday-test.jpg", "holiday-test")
            assertEquals(2, calls)
            check(result, publisher, output)
        } finally {
            server.stop(0)
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-21T04:00:00Z")
    }
}
