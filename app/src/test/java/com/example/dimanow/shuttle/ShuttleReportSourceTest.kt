package com.example.dimanow.shuttle

import com.example.dimanow.guidance.ShuttleReportAggregate
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ShuttleReportSourceTest {
    @Test
    fun `refresh exposes aggregate counts and this install report state`() = runTest {
        val requests = mutableListOf<ShuttleReportHttpRequest>()
        val responses = ArrayDeque(
            listOf(
                ShuttleReportHttpResponse(201, """{"token":"v1.server-issued-token","expiresAt":4102444800}"""),
                ShuttleReportHttpResponse(
                    200,
                    """{"serviceDate":"2026-09-02","scheduleRevision":9,"reports":[{"runId":"evening-loop-wednesday-1850","stopCallId":"evening-loop-wednesday-1850:3","stopSequence":3,"count":4,"reportedByYou":true}]}""",
                ),
            ),
        )
        val source = HttpShuttleReportSource(
            rootUrl = "https://reports.example",
            transport = ShuttleReportTransport { request ->
                requests += request
                responses.removeFirst()
            },
        )

        source.refresh(LocalDate.of(2026, 9, 2), 9)

        assertEquals(null, source.state.value.error)
        assertEquals(
            listOf(ShuttleReportAggregate("evening-loop-wednesday-1850", "evening-loop-wednesday-1850:3", 3, 4)),
            source.state.value.reports,
        )
        assertEquals(setOf("evening-loop-wednesday-1850:3"), source.state.value.reportedByThisInstall)
        assertEquals(listOf("POST", "GET"), requests.map { it.method })
        assertEquals("https://reports.example/v1/shuttle-reporter-token", requests.first().url)
        assertEquals("v1.server-issued-token", requests.last().headers["X-Dima-Reporter"])
    }

    @Test
    fun `report and revoke use the exact run and stop call identity`() = runTest {
        val requests = mutableListOf<ShuttleReportHttpRequest>()
        val responses = ArrayDeque(
            listOf(
                ShuttleReportHttpResponse(201, """{"token":"v1.server-issued-token","expiresAt":4102444800}"""),
                ShuttleReportHttpResponse(201, """{"reported":true,"alreadyReported":false}"""),
                ShuttleReportHttpResponse(200, """{"serviceDate":"2026-09-02","scheduleRevision":9,"reports":[]}"""),
                ShuttleReportHttpResponse(204, ""),
                ShuttleReportHttpResponse(200, """{"serviceDate":"2026-09-02","scheduleRevision":9,"reports":[]}"""),
            ),
        )
        val source = HttpShuttleReportSource(
            rootUrl = "https://reports.example",
            transport = ShuttleReportTransport { request -> requests += request; responses.removeFirst() },
        )
        val key = ShuttleReportEventKey(LocalDate.of(2026, 9, 2), 9, "evening-loop-wednesday-1850", "evening-loop-wednesday-1850:3")

        source.report(key)
        source.revoke(key)

        assertEquals(null, source.state.value.error)
        assertEquals(listOf("POST", "POST", "GET", "DELETE", "GET"), requests.map { it.method })
        assertEquals(true, requests[1].body?.contains("\"runId\":\"evening-loop-wednesday-1850\""))
        assertEquals(true, requests[3].body?.contains("\"stopCallId\":\"evening-loop-wednesday-1850:3\""))
        assertEquals(listOf("v1.server-issued-token"), requests.drop(1).mapNotNull { it.headers["X-Dima-Reporter"] }.distinct())
    }

    @Test
    fun `server rejection invalidates the address-bound token and retries once with a new token`() = runTest {
        val requests = mutableListOf<ShuttleReportHttpRequest>()
        val responses = ArrayDeque(
            listOf(
                ShuttleReportHttpResponse(201, """{"token":"v1.first-server-token","expiresAt":4102444800}"""),
                ShuttleReportHttpResponse(401, """{"message":"신고 신원을 다시 발급해 주세요."}"""),
                ShuttleReportHttpResponse(201, """{"token":"v1.second-server-token","expiresAt":4102444800}"""),
                ShuttleReportHttpResponse(200, """{"serviceDate":"2026-09-02","scheduleRevision":9,"reports":[]}"""),
            ),
        )
        val source = HttpShuttleReportSource(
            rootUrl = "https://reports.example",
            transport = ShuttleReportTransport { request -> requests += request; responses.removeFirst() },
        )

        val result = source.refresh(LocalDate.of(2026, 9, 2), 9)

        assertEquals(ShuttleReportActionResult.Success, result)
        assertEquals(listOf("POST", "GET", "POST", "GET"), requests.map { it.method })
        assertEquals("v1.first-server-token", requests[1].headers["X-Dima-Reporter"])
        assertEquals("v1.second-server-token", requests[3].headers["X-Dima-Reporter"])
    }
}
