package com.example.dimanow.pipeline

import com.example.dimanow.sync.DormitoryMealDayPayload
import com.example.dimanow.sync.DormitoryMealPayload
import com.example.dimanow.sync.DormitoryMealSectionPayload
import com.example.dimanow.sync.DormitoryMealSubmissionStatus
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class DormitoryMealSubmissionProcessorTest {
    @Test
    fun `현재 주가 이미 게시됐으면 Gemini 호출 없이 중복 상태를 게시한다`() {
        val output = Files.createTempDirectory("dima-dorm-process-dedupe")
        val publisher = StaticDataPublisher(output)
        publisher.publishDormitoryMeal(
            DormitoryMealPayload(
                weekStart = "2026-08-24",
                weekEnd = "2026-08-30",
                sourceImageUrl = "https://raw.githubusercontent.com/winter1l/DimaNow/example.jpg",
                days = listOf(
                    DormitoryMealDayPayload("2026-08-24", listOf(DormitoryMealSectionPayload("조식", null, listOf("떡국")))),
                ),
            ),
            1,
            Instant.parse("2026-08-24T00:00:00Z"),
        )
        val image = Files.createTempFile("dorm", ".jpg").also { Files.write(it, byteArrayOf(1, 2, 3)) }
        var calls = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                calls++
                exchange.sendResponseHeaders(500, 0)
                exchange.close()
            }
            start()
        }

        try {
            val result = DormitoryMealSubmissionProcessor(
                publisher = publisher,
                geminiClient = GeminiDormitoryMealClient("test", "http://127.0.0.1:${server.address.port}"),
                clock = Clock.fixed(Instant.parse("2026-08-27T03:00:00Z"), ZoneId.of("Asia/Seoul")),
            ).process(image, "image/jpeg", "https://raw.githubusercontent.com/winter1l/DimaNow/example.jpg", "submission-1")

            assertEquals("DUPLICATE", result.state)
            assertEquals(0, calls)
            val status = Json.decodeFromString<DormitoryMealSubmissionStatus>(
                Files.readString(output.resolve("data/v1/dorm-submissions/submission-1.json")),
            )
            assertEquals("DUPLICATE", status.state)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `검증이 거절되면 기존 식단을 건드리지 않고 짧은 사유를 게시한다`() {
        val output = Files.createTempDirectory("dima-dorm-process-reject")
        val image = Files.createTempFile("dorm", ".jpg").also { Files.write(it, byteArrayOf(3, 2, 1)) }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v1beta/models/gemini-3.5-flash-lite:generateContent") { exchange ->
                exchange.requestBody.readBytes()
                val text = """{\"동아방송예술대 기숙사 식단이 맞는가?\":false,\"식단표가 전부 보이는가?\":true,\"모두 true가 아닌 경우 사용자에게 알려줄 간단 사유\":\"기숙사 식단표가 아니에요\"}"""
                val response = """{"candidates":[{"content":{"parts":[{"text":"$text"}]}}]}""".toByteArray()
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            start()
        }

        try {
            val result = DormitoryMealSubmissionProcessor(
                publisher = StaticDataPublisher(output),
                geminiClient = GeminiDormitoryMealClient("test", "http://127.0.0.1:${server.address.port}"),
                clock = Clock.fixed(Instant.parse("2026-08-27T03:00:00Z"), ZoneId.of("Asia/Seoul")),
            ).process(image, "image/jpeg", "https://raw.githubusercontent.com/winter1l/DimaNow/example.jpg", "submission-2")

            assertEquals("REJECTED", result.state)
            assertEquals("기숙사 식단표가 아니에요", result.message)
            assertEquals(false, Files.exists(output.resolve("data/v1/manifest.json")))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `검증과 OCR을 통과한 기숙사 식단을 현재 주 payload로 게시한다`() {
        val output = Files.createTempDirectory("dima-dorm-process-publish")
        val image = Files.createTempFile("dorm", ".jpg").also { Files.write(it, byteArrayOf(7, 8, 9)) }
        var call = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v1beta/models/gemini-3.5-flash-lite:generateContent") { exchange ->
                exchange.requestBody.readBytes()
                val text = if (call++ == 0) {
                    """{"동아방송예술대 기숙사 식단이 맞는가?":true,"식단표가 전부 보이는가?":true}"""
                } else {
                    """{"days":[
                      {"month":8,"day":24,"sections":[{"name":"조식","menuLines":["떡국"]}]},
                      {"month":8,"day":25,"sections":[{"name":"조식","menuLines":["소고기해장국"]}]},
                      {"month":8,"day":26,"sections":[{"name":"조식","menuLines":["아욱된장국"]}]},
                      {"month":8,"day":27,"sections":[{"name":"조식","menuLines":["버섯들깨국"]}]},
                      {"month":8,"day":28,"sections":[{"name":"조식","menuLines":["돈육김치찌개"]}]}
                    ]}"""
                }
                val response = """{"candidates":[{"content":{"parts":[{"text":${jsonString(text)}}]}}]}""".toByteArray()
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            start()
        }

        try {
            val result = DormitoryMealSubmissionProcessor(
                publisher = StaticDataPublisher(output),
                geminiClient = GeminiDormitoryMealClient("test", "http://127.0.0.1:${server.address.port}"),
                clock = Clock.fixed(Instant.parse("2026-08-27T03:00:00Z"), ZoneId.of("Asia/Seoul")),
            ).process(image, "image/jpeg", "https://raw.githubusercontent.com/winter1l/DimaNow/example.jpg", "submission-3")

            assertEquals("PUBLISHED", result.state)
            assertEquals(2, call)
            val descriptor = Json.decodeFromString<com.example.dimanow.sync.CampusDataManifest>(
                Files.readString(output.resolve("data/v1/manifest.json")),
            ).datasets.getValue("dorm_meal")
            assertEquals("READY", descriptor.state)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `기숙사 표가 맞아도 지난 주 식단이면 사용자 사유와 함께 거절한다`() {
        val output = Files.createTempDirectory("dima-dorm-process-stale")
        val image = Files.createTempFile("dorm", ".jpg").also { Files.write(it, byteArrayOf(4, 5, 6)) }
        var call = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v1beta/models/gemini-3.5-flash-lite:generateContent") { exchange ->
                exchange.requestBody.readBytes()
                val text = if (call++ == 0) {
                    """{"동아방송예술대 기숙사 식단이 맞는가?":true,"식단표가 전부 보이는가?":true}"""
                } else {
                    """{"days":[
                      {"month":8,"day":24,"sections":[{"name":"조식","menuLines":["떡국"]}]},
                      {"month":8,"day":25,"sections":[{"name":"조식","menuLines":["소고기해장국"]}]},
                      {"month":8,"day":26,"sections":[{"name":"조식","menuLines":["아욱된장국"]}]},
                      {"month":8,"day":27,"sections":[{"name":"조식","menuLines":["버섯들깨국"]}]},
                      {"month":8,"day":28,"sections":[{"name":"조식","menuLines":["돈육김치찌개"]}]}
                    ]}"""
                }
                val response = """{"candidates":[{"content":{"parts":[{"text":${jsonString(text)}}]}}]}""".toByteArray()
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            start()
        }

        try {
            val result = DormitoryMealSubmissionProcessor(
                publisher = StaticDataPublisher(output),
                geminiClient = GeminiDormitoryMealClient("test", "http://127.0.0.1:${server.address.port}"),
                clock = Clock.fixed(Instant.parse("2026-08-31T03:00:00Z"), ZoneId.of("Asia/Seoul")),
            ).process(image, "image/jpeg", "https://raw.githubusercontent.com/winter1l/DimaNow/example.jpg", "submission-stale")

            assertEquals("REJECTED", result.state)
            assertEquals("이번 주 기숙사 식단표가 아니에요", result.message)
            assertEquals(false, Files.exists(output.resolve("data/v1/manifest.json")))
        } finally {
            server.stop(0)
        }
    }

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(char)
            }
        }
        append('"')
    }
}
