package com.example.dimanow.ui.meal

import com.example.dimanow.meal.DormitoryMealSection

/**
 * 화면에 카드 하나로 그려질 기숙사 식사 블록 (D-057).
 *
 * @param extras 같은 식사 시간대에 함께 제공되는, 운영시간이 따로 없는 코너(간편식·라면·도시락 등).
 */
internal data class DormitoryMealBlock(
    val name: String,
    val hours: String?,
    val menuLines: List<String>,
    val extras: List<DormitoryMealSection> = emptyList(),
)

/**
 * 발행된 섹션 목록을 식사 시간대 단위로 묶는다.
 *
 * 기숙사 식단표는 `조식(08:00~09:30) · 간편식 · 라면 · 중식(12:00~14:00) · 라면 · …` 처럼
 * 운영시간이 있는 주 식사 뒤에 시간이 없는 상시 코너가 따라오는 순서로 발행된다. 이 함수는
 * 시간이 있는 섹션을 블록의 머리로 삼고 뒤따르는 시간 없는 섹션을 그 블록에 붙여, 화면이
 * 8개의 평평한 제목 대신 3개의 식사 카드로 읽히게 한다. 첫 섹션부터 시간이 없으면 그대로
 * 독립 블록이 되므로 어떤 입력에서도 항목이 사라지지 않는다.
 */
internal fun groupDormitorySections(sections: List<DormitoryMealSection>): List<DormitoryMealBlock> {
    val blocks = mutableListOf<DormitoryMealBlock>()
    sections.forEach { section ->
        val hours = section.hours?.takeIf { it.isNotBlank() }
        val last = blocks.lastOrNull()
        if (hours == null && last != null && last.hours != null) {
            blocks[blocks.lastIndex] = last.copy(extras = last.extras + section)
        } else {
            blocks += DormitoryMealBlock(
                name = section.name,
                hours = hours,
                menuLines = section.menuLines,
            )
        }
    }
    return blocks
}
