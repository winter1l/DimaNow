package com.example.dimanow.meal

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.Instant
import com.example.dimanow.sync.DormitoryMealSubmissionStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubDormitoryMealApiTest {
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
            val status = GitHubDormitoryMealApi(
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

    @Test
    fun `GitHub device flow는 공개 client id로 시작하고 토큰을 응답 본문에서만 받는다`() = runTest {
        val paths = mutableListOf<String>()
        val bodies = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                paths += exchange.requestURI.path
                bodies += exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
                val response = if (paths.size == 1) {
                    """{"device_code":"device-1","user_code":"ABCD-EFGH","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}"""
                } else {
                    """{"access_token":"user-token","token_type":"bearer","scope":""}"""
                }.toByteArray()
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            start()
        }

        try {
            val api = GitHubDormitoryMealApi(
                githubApiRoot = "http://127.0.0.1:${server.address.port}",
                githubLoginRoot = "http://127.0.0.1:${server.address.port}",
            )
            val authorization = api.beginAuthorization("Iv1.8a61f9b3a7aba766")
            val token = api.pollAuthorization("Iv1.8a61f9b3a7aba766", authorization.deviceCode)

            assertEquals(listOf("/login/device/code", "/login/oauth/access_token"), paths)
            assertTrue(bodies[0].contains("client_id=Iv1.8a61f9b3a7aba766"))
            assertFalse(bodies[0].contains("client_secret"))
            assertEquals("ABCD-EFGH", authorization.userCode)
            assertEquals("https://github.com/login/device", authorization.verificationUri)
            assertEquals(DormitoryAuthorizationPoll.Authorized("user-token"), token)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `사진은 인증 헤더와 고정 제출 브랜치에 한 파일로 업로드한다`() = runTest {
        var receivedAuthorization: String? = null
        var receivedBody = ""
        var receivedPath = ""
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                receivedPath = exchange.requestURI.path
                receivedAuthorization = exchange.requestHeaders.getFirst("Authorization")
                receivedBody = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
                val response = """{"content":{"sha":"abc"},"commit":{"sha":"def"}}""".toByteArray()
                exchange.sendResponseHeaders(201, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            start()
        }

        try {
            val submission = GitHubDormitoryMealApi(
                githubApiRoot = "http://127.0.0.1:${server.address.port}",
                pagesRoot = "http://127.0.0.1:${server.address.port}",
                now = { Instant.parse("2026-08-31T03:04:05Z") },
                randomId = { "submission-123" },
            ).upload(
                token = "secret-token",
                image = DormitoryMealImage(byteArrayOf(1, 2, 3), "image/jpeg", "jpg"),
            )

            assertEquals("Bearer secret-token", receivedAuthorization)
            assertFalse(receivedBody.contains("secret-token"))
            assertEquals("/repos/winter1l/DimaNow/contents/dorm-submissions/submission-123.jpg", receivedPath)
            assertTrue(receivedBody.contains("\"branch\":\"dorm-submissions\""))
            assertTrue(receivedBody.contains("\"content\":\"AQID\""))
            assertEquals("submission-123", submission.submissionId)
        } finally {
            server.stop(0)
        }
    }
}
