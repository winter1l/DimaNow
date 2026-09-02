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
        val source = HttpShuttleReportSource(
            rootUrl = "https://reports.example",
            reporterToken = "install_token_12345678901234567890",
            transport = ShuttleReportTransport { request ->
                requests += request
                ShuttleReportHttpResponse(
                    200,
                    """{"serviceDate":"2026-09-02","scheduleRevision":9,"reports":[{"runId":"evening-loop-wednesday-1850","stopCallId":"evening-loop-wednesday-1850:3","stopSequence":3,"count":4,"reportedByYou":true}]}""",
                )
            },
        )

        source.refresh(LocalDate.of(2026, 9, 2), 9)

        assertEquals(null, source.state.value.error)
        assertEquals(
            listOf(ShuttleReportAggregate("evening-loop-wednesday-1850", "evening-loop-wednesday-1850:3", 3, 4)),
            source.state.value.reports,
        )
        assertEquals(setOf("evening-loop-wednesday-1850:3"), source.state.value.reportedByThisInstall)
        assertEquals("GET", requests.single().method)
        assertEquals("install_token_12345678901234567890", requests.single().headers["X-Dima-Reporter"])
    }

    @Test
    fun `report and revoke use the exact run and stop call identity`() = runTest {
        val requests = mutableListOf<ShuttleReportHttpRequest>()
        val responses = ArrayDeque(
            listOf(
                ShuttleReportHttpResponse(201, """{"reported":true,"alreadyReported":false}"""),
                ShuttleReportHttpResponse(200, """{"serviceDate":"2026-09-02","scheduleRevision":9,"reports":[]}"""),
                ShuttleReportHttpResponse(204, ""),
                ShuttleReportHttpResponse(200, """{"serviceDate":"2026-09-02","scheduleRevision":9,"reports":[]}"""),
            ),
        )
        val source = HttpShuttleReportSource(
            rootUrl = "https://reports.example",
            reporterToken = "install_token_12345678901234567890",
            transport = ShuttleReportTransport { request -> requests += request; responses.removeFirst() },
        )
        val key = ShuttleReportEventKey(LocalDate.of(2026, 9, 2), 9, "evening-loop-wednesday-1850", "evening-loop-wednesday-1850:3")

        source.report(key)
        source.revoke(key)

        assertEquals(null, source.state.value.error)
        assertEquals(listOf("POST", "GET", "DELETE", "GET"), requests.map { it.method })
        assertEquals(true, requests[0].body?.contains("\"runId\":\"evening-loop-wednesday-1850\""))
        assertEquals(true, requests[2].body?.contains("\"stopCallId\":\"evening-loop-wednesday-1850:3\""))
    }
}
