package com.example.dimanow.meal

import com.example.dimanow.sync.DormitoryMealSubmissionStatus
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnonymousDormitoryMealApiTest {
    @Test
    fun `사진은 사용자 인증 헤더 없이 익명 업로드 엔드포인트로 전송된다`() = runTest {
        var receivedPath = ""
        var receivedAuthorization: String? = "not-read"
        var receivedContentType = ""
        var receivedExtension = ""
        var receivedBody = byteArrayOf()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                receivedPath = exchange.requestURI.path
                receivedAuthorization = exchange.requestHeaders.getFirst("Authorization")
                receivedContentType = exchange.requestHeaders.getFirst("Content-Type")
                receivedExtension = exchange.requestHeaders.getFirst("X-Dima-Image-Extension")
                receivedBody = exchange.requestBody.readBytes()
                val response = """{"submissionId":"submission-123","uploadedAt":"2026-08-31T03:04:05Z"}""".toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(202, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            start()
        }

        try {
            val submission = AnonymousDormitoryMealApi(
                uploadRoot = "http://127.0.0.1:${server.address.port}",
                pagesRoot = "http://127.0.0.1:${server.address.port}",
            ).upload(DormitoryMealImage(byteArrayOf(1, 2, 3), "image/jpeg", "jpg"))

            assertEquals("/v1/dormitory-meals", receivedPath)
            assertNull(receivedAuthorization)
            assertEquals("image/jpeg", receivedContentType)
            assertEquals("jpg", receivedExtension)
            assertEquals(listOf(1.toByte(), 2.toByte(), 3.toByte()), receivedBody.toList())
            assertEquals("submission-123", submission.submissionId)
            assertEquals(Instant.parse("2026-08-31T03:04:05Z"), submission.uploadedAt)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `Pages 제출 상태는 캐시를 우회해 공개 json에서 읽는다`() = runTest {
        var receivedPath = ""
        var receivedQuery = ""
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                receivedPath = exchange.requestURI.path
                receivedQuery = exchange.requestURI.query.orEmpty()
                val response = """{"submissionId":"submission-123","state":"PUBLISHED","updatedAt":"2026-08-31T03:04:05Z"}""".toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            start()
        }

        try {
            val status = AnonymousDormitoryMealApi(
                uploadRoot = "http://127.0.0.1:${server.address.port}",
                pagesRoot = "http://127.0.0.1:${server.address.port}",
                now = { Instant.parse("2026-08-31T03:04:05Z") },
            ).submissionStatus("submission-123")

            assertEquals("/dorm-submissions/submission-123.json", receivedPath)
            assertEquals("t=1788145445000", receivedQuery)
            assertEquals(
                DormitoryMealSubmissionStatus("submission-123", "PUBLISHED", null, "2026-08-31T03:04:05Z"),
                status,
            )
        } finally {
            server.stop(0)
        }
    }
}
