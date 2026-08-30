package com.example.dimanow.pipeline

import java.time.LocalDate
import org.junit.Assert.assertEquals
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
}
