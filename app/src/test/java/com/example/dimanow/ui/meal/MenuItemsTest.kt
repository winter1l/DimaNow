package com.example.dimanow.ui.meal

import org.junit.Assert.assertEquals
import org.junit.Test

class MenuItemsTest {
    @Test
    fun `dash-wrapped origins attach to the dish above, even when wrapped over two lines`() {
        val items = parseMenuItems(
            listOf(
                "부대찌개", "-계육:국내산,돈육:국내산", "계육:국내산-",
                "난자완스", "-계육:국내산-",
                "계란짜장조림", "쌀밥",
            ),
        )

        assertEquals(
            listOf(
                MenuItem("부대찌개", "계육:국내산, 돈육:국내산"),
                MenuItem("난자완스", "계육:국내산"),
                MenuItem("계란짜장조림"),
                MenuItem("쌀밥"),
            ),
            items,
        )
    }

    @Test
    fun `bare origin notes attach and an origin without a dish is kept`() {
        assertEquals(
            listOf(MenuItem("오징어:중국산"), MenuItem("오징어까스 / 소스", "오징어:중국산")),
            parseMenuItems(listOf("-오징어:중국산-", "오징어까스 / 소스", "오징어:중국산")),
        )
    }

    @Test
    fun `the quick meal reads as one choice among three options`() {
        assertEquals(
            listOf("단백질세트 (바·쉐이크·우유 중 1)", "컵과일", "샌드위치 + 음료 (각 택1)"),
            quickMealOptions(listOf("단백질세트(바,쉐이크,우유 택1)", "컵과일", "샌드위치 택1", "음료 택1")),
        )
    }

    @Test
    fun `a single pick-one line keeps its wording`() {
        assertEquals(listOf("샌드위치 택1", "컵과일"), quickMealOptions(listOf("샌드위치 택1", "컵과일")))
    }
}
