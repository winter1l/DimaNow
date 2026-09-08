package com.example.dimanow.ui.meal

import com.example.dimanow.meal.DormitoryMealSection
import org.junit.Assert.assertEquals
import org.junit.Test

class DormitoryMealBlocksTest {
    @Test
    fun `hourless corners attach to the meal they are served with`() {
        val blocks = groupDormitorySections(
            listOf(
                DormitoryMealSection("조식", "08:00~09:30", listOf("떡국")),
                DormitoryMealSection("간편식", null, listOf("시리얼")),
                DormitoryMealSection("중식", "12:00~14:00", listOf("미역국")),
                DormitoryMealSection("라면", null, listOf("신라면")),
                DormitoryMealSection("샐러드도시락", "", listOf("치킨샐러드")),
                DormitoryMealSection("석식", "18:00~19:30", listOf("불고기")),
            ),
        )

        // 평평한 제목 6개가 식사 카드 3장으로 접힌다
        assertEquals(listOf("조식", "중식", "석식"), blocks.map { it.name })
        assertEquals(listOf("간편식"), blocks[0].extras.map { it.name })
        assertEquals(listOf("라면", "샐러드도시락"), blocks[1].extras.map { it.name })
        assertEquals(emptyList<String>(), blocks[2].extras.map { it.name })
    }

    @Test
    fun `no item is ever dropped even when the day starts without hours`() {
        val sections = listOf(
            DormitoryMealSection("공지", null, listOf("오늘은 자율배식")),
            DormitoryMealSection("중식", "12:00~14:00", listOf("미역국")),
        )

        val blocks = groupDormitorySections(sections)

        assertEquals(listOf("공지", "중식"), blocks.map { it.name })
        assertEquals(
            sections.flatMap { it.menuLines },
            blocks.flatMap { block -> block.menuLines + block.extras.flatMap { it.menuLines } },
        )
    }

    @Test
    fun `blank hours are treated as no hours so the chip never renders empty`() {
        val blocks = groupDormitorySections(listOf(DormitoryMealSection("중식", "  ", listOf("미역국"))))

        assertEquals(null, blocks.single().hours)
    }
}
