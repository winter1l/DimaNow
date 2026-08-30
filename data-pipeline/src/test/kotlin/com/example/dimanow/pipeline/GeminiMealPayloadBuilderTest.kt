package com.example.dimanow.pipeline

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GeminiMealPayloadBuilderTest {
    @Test
    fun `Gemini JSON 월일을 기준 연도의 주간 식단으로 만든다`() {
        val response = """
            {
              "days": [
                {"month": 8, "day": 24, "menuLines": ["유부장국", "함박스테이크조림"]},
                {"month": 8, "day": 25, "menuLines": ["미역국", "닭볶음탕"]},
                {"month": 8, "day": 26, "menuLines": ["참치김치찌개", "치킨까스"]},
                {"month": 8, "day": 27, "menuLines": ["배추된장국", "간장돈육불고기"]},
                {"month": 8, "day": 28, "menuLines": ["계란볶음밥", "짬뽕순두부찌개"]}
              ]
            }
        """.trimIndent()

        val payload = GeminiMealPayloadBuilder().build(
            responseJson = response,
            referenceDate = LocalDate.of(2026, 8, 30),
            hours = "11:00 ~ 14:00",
            sourceUrl = "https://www.instagram.com/p/example/",
            sourceImageUrl = "https://scontent.example/meal.jpg",
        )

        assertEquals("2026-08-24", payload.weekStart)
        assertEquals("2026-08-30", payload.weekEnd)
        assertEquals(listOf("유부장국", "함박스테이크조림"), payload.days.first().menuLines)
        assertEquals("11:00 ~ 14:00", payload.days.first().hours)
        assertEquals(5, payload.days.size)
    }

    @Test
    fun `연말에 게시된 다음 주 식단의 1월 날짜는 다음 연도로 해석한다`() {
        val response = """
            {
              "days": [
                {"month": 12, "day": 28, "menuLines": ["월요일 메뉴"]},
                {"month": 12, "day": 29, "menuLines": ["화요일 메뉴"]},
                {"month": 12, "day": 30, "menuLines": ["수요일 메뉴"]},
                {"month": 12, "day": 31, "menuLines": ["목요일 메뉴"]},
                {"month": 1, "day": 1, "menuLines": ["금요일 메뉴"]}
              ]
            }
        """.trimIndent()

        val payload = GeminiMealPayloadBuilder().build(
            response,
            LocalDate.of(2026, 12, 27),
            "11:00 ~ 14:00",
            "https://www.instagram.com/p/new-year/",
            "https://scontent.example/new-year.jpg",
        )

        assertEquals("2026-12-28", payload.weekStart)
        assertEquals("2027-01-01", payload.days.last().date)
        assertEquals("2027-01-03", payload.weekEnd)
    }

    @Test
    fun `현재 주나 주말의 다음 주가 아닌 식단은 게시하지 않는다`() {
        val response = """
            {"days": [
              {"month": 8, "day": 17, "menuLines": ["월"]},
              {"month": 8, "day": 18, "menuLines": ["화"]},
              {"month": 8, "day": 19, "menuLines": ["수"]},
              {"month": 8, "day": 20, "menuLines": ["목"]},
              {"month": 8, "day": 21, "menuLines": ["금"]}
            ]}
        """.trimIndent()

        val error = assertThrows(IllegalArgumentException::class.java) {
            GeminiMealPayloadBuilder().build(
                response,
                LocalDate.of(2026, 8, 30),
                "11:00 ~ 14:00",
                "https://www.instagram.com/p/old/",
                "https://scontent.example/old.jpg",
            )
        }

        assertEquals("현재 기준 주와 식단 날짜가 일치하지 않습니다.", error.message)
    }

    @Test
    fun `월요일부터 금요일까지 날짜가 연속되지 않으면 게시하지 않는다`() {
        val response = """
            {"days": [
              {"month": 8, "day": 24, "menuLines": ["월"]},
              {"month": 8, "day": 24, "menuLines": ["중복"]},
              {"month": 8, "day": 26, "menuLines": ["수"]},
              {"month": 8, "day": 27, "menuLines": ["목"]},
              {"month": 8, "day": 28, "menuLines": ["금"]}
            ]}
        """.trimIndent()

        val error = assertThrows(IllegalArgumentException::class.java) {
            GeminiMealPayloadBuilder().build(
                response,
                LocalDate.of(2026, 8, 26),
                "11:00 ~ 14:00",
                "https://www.instagram.com/p/duplicate/",
                "https://scontent.example/duplicate.jpg",
            )
        }

        assertEquals("월요일부터 금요일까지 연속된 날짜가 아닙니다.", error.message)
    }

    @Test
    fun `빈 줄만 제거하고 Gemini 메뉴명과 순서를 보존한다`() {
        val response = """
            {"days": [
              {"month": 8, "day": 24, "menuLines": ["  유부장국  ", "", "함박스테이크조림"]},
              {"month": 8, "day": 25, "menuLines": ["미역국"]},
              {"month": 8, "day": 26, "menuLines": ["참치김치찌개"]},
              {"month": 8, "day": 27, "menuLines": ["배추된장국"]},
              {"month": 8, "day": 28, "menuLines": ["계란볶음밥"]}
            ]}
        """.trimIndent()

        val payload = GeminiMealPayloadBuilder().build(
            response,
            LocalDate.of(2026, 8, 26),
            "11:00 ~ 14:00",
            "https://www.instagram.com/p/preserve/",
            "https://scontent.example/preserve.jpg",
        )

        assertEquals(listOf("유부장국", "함박스테이크조림"), payload.days.first().menuLines)
    }
}
