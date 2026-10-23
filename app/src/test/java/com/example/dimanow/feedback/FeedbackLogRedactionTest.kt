package com.example.dimanow.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackLogRedactionTest {
    @Test
    fun `personal values in the log are masked and the rest stays readable`() {
        val raw = """
            10-24 00:44:49.123 W/DimaNow( 4120): sign-in failed for student 20231234 at https://lms.dima.ac.kr/lms/myLecture/doListView.dunet?to_do_type=all
            10-24 00:44:50.001 E/DimaNow( 4120): contact me@example.com or 010-1234-5678 from 192.168.0.12
            10-24 00:44:51.500 W/WebView( 4120): cookie=JSESSIONID=abcDEF1234567890abcdef1234567890
            10-24 00:44:52.000 E/AndroidRuntime( 4120): java.lang.IllegalStateException: 시간표 표를 찾지 못했어요
        """.trimIndent()
        val masked = redactFeedbackLog(raw)
        for (secret in listOf("20231234", "me@example.com", "010-1234-5678", "192.168.0.12", "doListView", "abcDEF1234567890abcdef1234567890")) {
            assertFalse(secret, secret in masked)
        }
        assertTrue("https://lms.dima.ac.kr/…" in masked)
        assertTrue("<숫자>" in masked)
        assertTrue("java.lang.IllegalStateException: 시간표 표를 찾지 못했어요" in masked)
        // Log timestamps and process ids survive.
        assertTrue("10-24 00:44:52.000 E/AndroidRuntime( 4120)" in masked)
    }

    @Test
    fun `the log keeps its newest part`() {
        val long = (1..3000).joinToString("\n") { "10-24 00:00:00.000 W/X( 1): line $it" }
        val masked = redactFeedbackLog(long)
        assertEquals(15_000, masked.length)
        assertTrue(masked.endsWith("line 3000"))
    }
}
