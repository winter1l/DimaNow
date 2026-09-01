package com.example.dimanow.lms

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UrlConnectionLmsTransportTest {
    @Test
    fun postFormFollowsTheOfficialRedirectAndKeepsItsSessionCookie() = runTest {
        val first = FakeConnection(
            url = URL("https://lms.dima.ac.kr/lms/class/classroom/doSetSessionClassRoom.dunet"),
            status = 302,
            headers = mapOf(
                "Location" to listOf("/lms/class/courseSchedule/doListView.dunet"),
                "Set-Cookie" to listOf("CLASS_CONTEXT=ready; Path=/; Secure"),
            ),
        )
        val second = FakeConnection(
            url = URL("https://lms.dima.ac.kr/lms/class/courseSchedule/doListView.dunet"),
            status = 200,
            body = "<main>수업 자료</main>".toByteArray(),
        )
        val connections = ArrayDeque(listOf(first, second))
        val storedCookies = mutableListOf<Pair<String, String>>()
        val transport = UrlConnectionLmsTransport(
            cookieProvider = { null },
            cookieSink = { url, cookie -> storedCookies += url to cookie },
            connectionFactory = { connections.removeFirst() },
        )

        val response = transport.postForm(
            first.url.toString(),
            mapOf("course_id" to "COURSE-A", "class_no" to "D"),
        )

        assertEquals("POST", first.requestMethod)
        assertEquals("GET", second.requestMethod)
        assertEquals("CLASS_CONTEXT=ready", second.getRequestProperty("Cookie"))
        assertEquals(second.url.toString(), response.finalUrl)
        assertEquals("<main>수업 자료</main>", response.body.decodeToString())
        assertEquals(listOf(first.url.toString() to "CLASS_CONTEXT=ready; Path=/; Secure"), storedCookies)
    }
}

private class FakeConnection(
    url: URL,
    private val status: Int,
    private val body: ByteArray = ByteArray(0),
    private val headers: Map<String, List<String>> = emptyMap(),
) : HttpURLConnection(url) {
    private val posted = ByteArrayOutputStream()

    override fun connect() = Unit
    override fun disconnect() = Unit
    override fun usingProxy(): Boolean = false
    override fun getResponseCode(): Int = status
    override fun getInputStream(): InputStream = ByteArrayInputStream(body)
    override fun getOutputStream() = posted
    override fun getHeaderFields(): Map<String, List<String>> = headers
    override fun getHeaderField(name: String?): String? = headers.entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value
        ?.firstOrNull()
    override fun getContentType(): String = "text/html; charset=UTF-8"
    override fun getContentLengthLong(): Long = body.size.toLong()
}
