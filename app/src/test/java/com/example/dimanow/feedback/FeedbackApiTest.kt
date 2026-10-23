package com.example.dimanow.feedback

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedbackApiTest {
    @Test
    fun `the request carries the text, its kind and the versions only when allowed`() {
        assertEquals(
            """{"category":"bug","message":"시간표가 \"비어\" 보여요","appVersion":"2.1","androidSdk":36}""",
            feedbackRequestJson(FeedbackCategory.BUG, "  시간표가 \"비어\" 보여요\n", "2.1", 36),
        )
        assertEquals(
            """{"category":"idea","message":"위젯 추가"}""",
            feedbackRequestJson(FeedbackCategory.IDEA, "위젯 추가", null, null),
        )
    }

    @Test
    fun `answers map to posted, too often or failed`() {
        assertEquals(
            FeedbackResult.Posted(42, "https://github.com/winter1l/DimaNow/issues/42"),
            feedbackResult(201, """{"message":"제보를 올렸습니다.","number":42,"url":"https://github.com/winter1l/DimaNow/issues/42"}"""),
        )
        // Only this repository's issues are linked from the app.
        assertEquals(FeedbackResult.Failed, feedbackResult(201, """{"number":1,"url":"https://example.com/x"}"""))
        assertEquals(FeedbackResult.TooOften, feedbackResult(429, """{"message":"x"}"""))
        assertEquals(FeedbackResult.Failed, feedbackResult(502, ""))
    }
}
