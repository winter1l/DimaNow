package com.example.dimanow.pipeline

import com.example.dimanow.sync.MealDayPayload
import com.example.dimanow.sync.MealPayload
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MealPublicationPolicyTest {
    @Test
    fun `학생식당 게시물이 없으면 새 식단 대기 상태다`() {
        assertEquals(MealPublicationDecision.WAITING, MealPublicationPolicy().decide(postFound = false, payload = null, today = LocalDate.parse("2026-08-31")))
    }

    @Test
    fun `검증 결과의 마지막 날짜가 지났으면 새 식단 대기 상태다`() {
        assertEquals(MealPublicationDecision.WAITING, MealPublicationPolicy().decide(postFound = true, payload = payload("2026-08-28"), today = LocalDate.parse("2026-08-31")))
    }

    @Test
    fun `오늘 이후 식단은 게시한다`() {
        assertEquals(MealPublicationDecision.PUBLISH, MealPublicationPolicy().decide(postFound = true, payload = payload("2026-09-04"), today = LocalDate.parse("2026-08-31")))
    }

    private fun payload(lastDate: String) = MealPayload(
        weekStart = "2026-08-31",
        weekEnd = "2026-09-06",
        days = listOf(MealDayPayload(lastDate, listOf("밥"), "11:30 ~ 14:00", "https://www.instagram.com/p/example/", "https://example.invalid/meal.jpg")),
    )
}
