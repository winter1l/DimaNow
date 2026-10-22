package com.example.dimanow.ui.meal

/** One dish with the origin note the menu board prints under it (`-계육:국내산-`), shown on the dish's row. */
internal data class MenuItem(val name: String, val origin: String? = null)

/**
 * Turns published menu lines into dishes with their origin notes.
 *
 * Board OCR emits an origin as its own line wrapped in dashes, and a long origin can wrap over several lines
 * (`-계육:국내산,돈육:국내산` then `계육:국내산-`). Those lines are joined and attached to the dish before
 * them, so the card keeps one row per dish. An origin with no dish before it stays a row of its own, and
 * nothing is ever dropped.
 */
internal fun parseMenuItems(lines: List<String>): List<MenuItem> {
    val items = mutableListOf<MenuItem>()
    val pending = mutableListOf<String>()
    var inOrigin = false

    fun attach(text: String) {
        val origin = text.trim().trim('-').trim().replace(Regex("\\s*,\\s*"), ", ")
        if (origin.isEmpty()) return
        val last = items.lastOrNull()
        if (last != null) {
            // The board can repeat a part when an origin wraps; show each part once.
            val parts = listOfNotNull(last.origin, origin).flatMap { it.split(", ") }.map(String::trim).filter(String::isNotEmpty).distinct()
            items[items.lastIndex] = last.copy(origin = parts.joinToString(", "))
        } else {
            items += MenuItem(origin)
        }
    }

    lines.flatMap { it.split('\n') }.map(String::trim).filter(String::isNotEmpty).forEach { line ->
        when {
            inOrigin -> {
                pending += line
                if (line.endsWith("-")) {
                    attach(pending.joinToString(","))
                    pending.clear()
                    inOrigin = false
                }
            }
            line.startsWith("-") -> {
                if (line.length > 1 && line.endsWith("-")) {
                    attach(line)
                } else {
                    pending += line
                    inOrigin = true
                }
            }
            isBareOrigin(line) -> attach(line)
            else -> items += MenuItem(line)
        }
    }
    if (pending.isNotEmpty()) attach(pending.joinToString(","))
    return items
}

/** `쌀:국내산` style notes without dashes (student cafeteria posts). */
private fun isBareOrigin(line: String): Boolean =
    line.contains(':') && ORIGIN_WORDS.any { line.contains(it) } && !line.contains("kcal", ignoreCase = true)

private val ORIGIN_WORDS = listOf("국내산", "수입산", "외국산", "원산지") +
    listOf("호주", "미국", "중국", "칠레", "브라질", "베트남", "러시아", "캐나다", "뉴질랜드", "스페인", "덴마크", "네덜란드", "멕시코")
        .map { "${it}산" }

/**
 * The dormitory's 간편식 is one choice among options: e.g. `단백질세트(바,쉐이크,우유 택1)` or `컵과일` or a
 * sandwich and a drink (`샌드위치 택1` + `음료 택1`, printed in one cell). Consecutive "X 택1" lines are one
 * option; a "(a,b,c 택1)" suffix reads as "a·b·c 중 1".
 */
internal fun quickMealOptions(lines: List<String>): List<String> {
    val options = mutableListOf<String>()
    val pickEach = mutableListOf<String>()

    fun flushPickEach() {
        when (pickEach.size) {
            0 -> Unit
            1 -> options += "${pickEach.single()} 택1"
            else -> options += pickEach.joinToString(" + ") + " (각 택1)"
        }
        pickEach.clear()
    }

    parseMenuItems(lines).map { it.name }.forEach { line ->
        val each = Regex("^(.+?)\\s*택\\s*1$").find(line)
        val inner = Regex("^(.+?)\\s*\\((.+?)\\s*택\\s*1\\)$").find(line)
        when {
            each != null && !line.contains('(') -> pickEach += each.groupValues[1].trim()
            inner != null -> {
                flushPickEach()
                val choices = inner.groupValues[2].split(',', '·', '/').map(String::trim).filter(String::isNotEmpty)
                options += "${inner.groupValues[1].trim()} (${choices.joinToString("·")} 중 1)"
            }
            else -> {
                flushPickEach()
                options += line
            }
        }
    }
    flushPickEach()
    return options
}

internal fun isQuickMealSection(name: String): Boolean = name.replace(" ", "").contains("간편식")
