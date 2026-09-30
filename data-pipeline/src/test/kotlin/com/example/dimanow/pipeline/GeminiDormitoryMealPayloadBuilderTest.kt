package com.example.dimanow.pipeline

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GeminiDormitoryMealPayloadBuilderTest {
    @Test
    fun `기숙사 표의 요일별 모든 식사 구분을 순서대로 보존한다`() {
        val response = """
            {"days":[
              {"month":8,"day":24,"sections":[
                {"name":"조식","hours":"08:00~09:30","menuLines":["떡국","완자전&소스"]},
                {"name":"간편식","menuLines":["단백질세트","컵과일"]},
                {"name":"중식","hours":"12:00~14:00","menuLines":["유채된장국","계란마파두부"]},
                {"name":"석식","hours":"18:00~19:30","menuLines":["미역국","풍나물불고기"]}
              ]},
              {"month":8,"day":25,"sections":[{"name":"조식","menuLines":["소고기해장국"]}]},
              {"month":8,"day":26,"sections":[{"name":"조식","menuLines":["아욱된장국"]}]},
              {"month":8,"day":27,"sections":[{"name":"조식","menuLines":["버섯들깨국"]}]},
              {"month":8,"day":28,"sections":[{"name":"조식","menuLines":["돈육김치찌개"]}]}
            ]}
        """.trimIndent()

        val payload = GeminiDormitoryMealPayloadBuilder().build(
            responseJson = response,
            referenceDate = LocalDate.of(2026, 8, 27),
            sourceImageUrl = "https://raw.githubusercontent.com/winter1l/DimaNow/dorm-submissions/dorm-submissions/example.jpg",
        )

        assertEquals("2026-08-24", payload.weekStart)
        assertEquals("2026-08-30", payload.weekEnd)
        assertEquals(listOf("조식", "간편식", "중식", "석식"), payload.days.first().sections.map { it.name })
        assertEquals(listOf("유채된장국", "계란마파두부"), payload.days.first().sections[2].menuLines)
        assertEquals("12:00~14:00", payload.days.first().sections[2].hours)
    }

    @Test
    fun `주말 기준일에는 다음 월요일 주차만 허용하고 끝나가는 주는 거절한다`() {
        val builder = GeminiDormitoryMealPayloadBuilder()
        val saturday = LocalDate.of(2026, 8, 29)
        val sunday = LocalDate.of(2026, 8, 30)

        for (referenceDate in listOf(saturday, sunday)) {
            assertEquals("2026-08-31", builder.build(week(LocalDate.of(2026, 8, 31)), referenceDate, SOURCE).weekStart)
            assertThrows(DormitoryMealWeekMismatchException::class.java) {
                builder.build(week(LocalDate.of(2026, 8, 24)), referenceDate, SOURCE)
            }
        }
        assertEquals("2026-08-24", builder.build(week(LocalDate.of(2026, 8, 24)), LocalDate.of(2026, 8, 28), SOURCE).weekStart)
        assertThrows(DormitoryMealWeekMismatchException::class.java) {
            builder.build(week(LocalDate.of(2026, 8, 31)), LocalDate.of(2026, 8, 28), SOURCE)
        }
    }

    private fun week(monday: LocalDate): String = """{"days":[""" + (0L..4L).joinToString(",") { offset ->
        val date = monday.plusDays(offset)
        """{"month":${date.monthValue},"day":${date.dayOfMonth},"sections":[{"name":"조식","menuLines":["메뉴"]}]}"""
    } + "]}"

    private companion object {
        const val SOURCE = "https://raw.githubusercontent.com/winter1l/DimaNow/dorm-submissions/dorm-submissions/example.jpg"
    }
}
