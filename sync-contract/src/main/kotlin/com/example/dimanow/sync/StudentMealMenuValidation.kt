package com.example.dimanow.sync

/** A closure printed in a weekday column is complete information, not an incomplete menu. */
fun isValidStudentMealMenu(menuLines: List<String>): Boolean {
    if (menuLines.isEmpty() || menuLines.any(String::isBlank)) return false
    if (menuLines.size >= 2) return true
    return menuLines.single().replace(Regex("[\\s\\p{Z}\\uFEFF]+"), "") in STUDENT_MEAL_CLOSURE_LABELS
}

// Exact labels only: a dish such as "추석 특식" must not pass as a closure.
private val STUDENT_MEAL_CLOSURE_LABELS = setOf(
    "휴무", "휴일", "공휴일", "대체공휴일", "미운영", "운영안함", "휴관",
    "추석", "추석공휴일", "추석연휴", "설날", "설연휴",
)
