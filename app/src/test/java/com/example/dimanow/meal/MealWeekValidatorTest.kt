package com.example.dimanow.meal

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MealWeekValidatorTest {
    @Test
    fun `weekday columns become a validated monday through friday week`() {
        val lines = listOf(
            OcrLine("8/24 월", 0, 0, 90, 20), OcrLine("8/25 화", 100, 0, 190, 20),
            OcrLine("8/26 수", 200, 0, 290, 20), OcrLine("8/27 목", 300, 0, 390, 20),
            OcrLine("8/28 금", 400, 0, 490, 20),
            OcrLine("제육볶음", 5, 40, 85, 60), OcrLine("미역국", 5, 70, 85, 90),
            OcrLine("닭갈비", 105, 40, 185, 60), OcrLine("된장국", 105, 70, 185, 90),
            OcrLine("돈가스", 205, 40, 285, 60), OcrLine("우동", 205, 70, 285, 90),
            OcrLine("불고기", 305, 40, 385, 60), OcrLine("김치찌개", 305, 70, 385, 90),
            OcrLine("비빔밥", 405, 40, 485, 60), OcrLine("계란국", 405, 70, 485, 90),
        )

        val result = MealWeekValidator().validate(lines, LocalDate.of(2026, 8, 26))

        val valid = result as MealValidationResult.Valid
        assertEquals(LocalDate.of(2026, 8, 24), valid.weekStart)
        assertEquals(listOf("제육볶음", "미역국"), valid.days.getValue(LocalDate.of(2026, 8, 24)))
        assertEquals(listOf("비빔밥", "계란국"), valid.days.getValue(LocalDate.of(2026, 8, 28)))
    }

    @Test
    fun `accepts weekday and parenthesized date split into separate OCR lines`() {
        val lines = buildList {
            listOf("월", "화", "수", "목", "금").forEachIndexed { index, day ->
                add(OcrLine(day, index * 100, 0, index * 100 + 90, 20))
                add(OcrLine("(8/${24 + index})", index * 100, 22, index * 100 + 90, 42))
                add(OcrLine("메뉴${index + 1}A", index * 100, 60, index * 100 + 90, 80))
                add(OcrLine("메뉴${index + 1}B", index * 100, 90, index * 100 + 90, 110))
            }
            add(OcrLine("Jima 동아방송예술대학교", 150, 140, 350, 160))
            add(OcrLine("DONG-AH INSTITUTE OF MEDIA AND ARTS", 100, 165, 400, 185))
            add(OcrLine("11:00 ~ 14:00", 180, 190, 320, 210))
        }

        val result = MealWeekValidator().validate(lines, LocalDate.of(2026, 8, 26))

        val valid = result as MealValidationResult.Valid
        assertEquals(LocalDate.of(2026, 8, 24), valid.weekStart)
        assertEquals(listOf("메뉴3A", "메뉴3B"), valid.days.getValue(LocalDate.of(2026, 8, 26)))
    }
}
