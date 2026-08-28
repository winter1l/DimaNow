package com.example.dimanow.pipeline

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MealPayloadBuilderTest {
    @Test
    fun `검증된 평일 OCR을 한 주 식단 payload로 만든다`() {
        val lines = buildList {
            (0..4).forEach { day ->
                val left = 100 + day * 200
                add(OcrLine("8.${24 + day}", left, 10, left + 80, 30))
                add(OcrLine("메뉴${day}A", left, 60, left + 80, 80))
                add(OcrLine("메뉴${day}B", left, 90, left + 80, 110))
            }
        }

        val payload = MealPayloadBuilder().build(
            lines = lines,
            referenceDate = LocalDate.of(2026, 8, 26),
            hours = "11:30 ~ 14:00",
            sourceUrl = "https://www.instagram.com/p/example/",
            sourceImageUrl = "https://scontent.example/meal.jpg",
        )

        assertEquals("2026-08-24", payload.weekStart)
        assertEquals("2026-08-30", payload.weekEnd)
        assertEquals(listOf("메뉴0A", "메뉴0B"), payload.days.first().menuLines)
        assertEquals("11:30 ~ 14:00", payload.days.first().hours)
        assertEquals(5, payload.days.size)
    }

    @Test
    fun `주말에는 바로 다음 주에 게시된 식단을 허용한다`() {
        val lines = buildList {
            val dates = listOf("8.31", "9.1", "9.2", "9.3", "9.4")
            dates.forEachIndexed { day, date ->
                val left = 100 + day * 200
                add(OcrLine(date, left, 10, left + 80, 30))
                add(OcrLine("밥${day}", left, 60, left + 80, 80))
                add(OcrLine("국${day}", left, 90, left + 80, 110))
            }
        }

        val payload = MealPayloadBuilder().build(
            lines,
            LocalDate.of(2026, 8, 30),
            "11:30 ~ 14:00",
            "https://www.instagram.com/p/next/",
            "https://scontent.example/next.jpg",
        )

        assertEquals("2026-08-31", payload.weekStart)
    }
}
