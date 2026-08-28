package com.example.dimanow.ui

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceTimeFormatterTest {
    @Test
    fun `source success instant is displayed in human readable Korea Standard Time`() {
        assertEquals(
            "2026년 8월 26일 21:00 KST",
            formatSourceSuccessTime(Instant.parse("2026-08-26T12:00:10Z")),
        )
    }
}
