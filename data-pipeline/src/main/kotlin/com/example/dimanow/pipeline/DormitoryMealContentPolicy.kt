package com.example.dimanow.pipeline

import com.example.dimanow.sync.DormitoryMealPayload
import java.text.Normalizer

/** Thrown when OCR output is shaped like a menu but carries content the public payload must never contain. */
class DormitoryMealContentException : IllegalArgumentException(DormitoryMealContentPolicy.REJECTION_REASON)

/**
 * Content limits for dormitory menus that D-089 publishes without a human reviewer.
 *
 * Gemini OCR output is untrusted text that every app instance renders, so a submission whose transcription exceeds the
 * shape of a real weekly menu, or carries links, contact details or invisible characters, is rejected as a whole
 * (never truncated) and the last-good payload stays live. Limits are calibrated against the published 2026-08/09
 * weeks: 8 sections a day (라면 appears three times), at most 9 lines per section, menu lines up to 43 characters
 * including an origin suffix, and one embedded line break before an origin note (`메뉴\n-돈육:국내산-`).
 */
object DormitoryMealContentPolicy {
    const val MAX_SECTIONS_PER_DAY = 10
    const val MAX_MENU_LINES_PER_SECTION = 15
    const val MAX_SECTION_NAME_LENGTH = 12
    const val MAX_HOURS_LENGTH = 20
    const val MAX_MENU_LINE_LENGTH = 60
    const val MAX_LINE_BREAKS_PER_MENU_LINE = 2
    const val MAX_REJECTION_REASON_LENGTH = 60

    const val REJECTION_REASON = "식단표가 아닌 내용이 포함되어 있어요"
    const val FALLBACK_REASON = "기숙사 식단표 전체가 보이도록 다시 촬영해 주세요"

    private val HOURS_PATTERN = Regex(
        """^\d{1,2}\s*(?::\s*\d{2}|시(?:\s*\d{1,2}\s*분)?)\s*[~\-–—]\s*\d{1,2}\s*(?::\s*\d{2}|시(?:\s*\d{1,2}\s*분)?)$""",
    )
    private val URL_SCHEME = Regex("""[a-z][a-z0-9+.\-]*://""")
    private val WWW = Regex("""(?:^|[^a-z0-9])www\d?\s*[.。．]""")

    // A dot followed by two ASCII letters is a host (bit.ly, naver.me, example.co.kr); menus never write that.
    private val DOMAIN = Regex("""[\p{L}\p{N}\-]\s*(?:[.。．]|\[\.]|\(\.\)|\s+dot\s+)\s*[a-z]{2,}""")
    private val EMAIL = Regex("""[@＠]""")
    private val PHONE = Regex("""(?:^|\D)(?:0\d{1,2}[\s\-.)]*\d{3,4}[\s\-.]*\d{4}|1[5-9]\d{2}[\s\-.]*\d{4})(?:\D|$)""")
    private val LONG_DIGITS = Regex("""\d{5,}""")
    private val DIGIT_GROUPS = Regex("""\d[\d\s\-.()/]*\d""")

    fun requireAllowed(payload: DormitoryMealPayload) {
        if (!isAllowed(payload)) throw DormitoryMealContentException()
    }

    fun isAllowed(payload: DormitoryMealPayload): Boolean = payload.days.all { day ->
        day.sections.size in 1..MAX_SECTIONS_PER_DAY && day.sections.all { section ->
            isAllowedSectionName(section.name) &&
                isAllowedHours(section.hours) &&
                section.menuLines.size in 1..MAX_MENU_LINES_PER_SECTION &&
                section.menuLines.all(::isAllowedMenuLine)
        }
    }

    fun isAllowedSectionName(name: String): Boolean =
        name.isNotBlank() && name.codePointCount(0, name.length) <= MAX_SECTION_NAME_LENGTH &&
            !hasControlCharacters(name, allowLineBreaks = 0) && !hasForbiddenContent(name)

    /** Blank or absent hours are allowed; anything else must be a plain time range such as `08:00~09:30`. */
    fun isAllowedHours(hours: String?): Boolean {
        if (hours.isNullOrBlank()) return true
        if (hours.codePointCount(0, hours.length) > MAX_HOURS_LENGTH) return false
        if (hasControlCharacters(hours, allowLineBreaks = 0)) return false
        return HOURS_PATTERN.matches(Normalizer.normalize(hours.trim(), Normalizer.Form.NFKC))
    }

    fun isAllowedMenuLine(line: String): Boolean =
        line.isNotBlank() && line.codePointCount(0, line.length) <= MAX_MENU_LINE_LENGTH &&
            !hasControlCharacters(line, allowLineBreaks = MAX_LINE_BREAKS_PER_MENU_LINE) && !hasForbiddenContent(line)

    /**
     * Reduces a Gemini rejection reason to one short line that cannot carry links, contact details or control
     * characters. Anything that does not fit is replaced by a fixed reason rather than partially published.
     */
    fun sanitizeRejectionReason(reason: String?): String {
        val firstLine = reason.orEmpty()
            .lineSequence()
            .map { line -> line.withoutInvisibleCharacters().replace(Regex("\\s+"), " ").trim() }
            .firstOrNull(String::isNotEmpty)
            ?: return FALLBACK_REASON
        return firstLine.takeIf {
            it.codePointCount(0, it.length) <= MAX_REJECTION_REASON_LENGTH && !hasForbiddenContent(it)
        } ?: FALLBACK_REASON
    }

    /** Links, hosts, e-mail addresses, phone numbers and long digit runs; checked on NFKC lower-case text. */
    fun hasForbiddenContent(value: String): Boolean {
        val text = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase()
        if (URL_SCHEME.containsMatchIn(text) || WWW.containsMatchIn(text) || DOMAIN.containsMatchIn(text)) return true
        if (EMAIL.containsMatchIn(text) || PHONE.containsMatchIn(text) || LONG_DIGITS.containsMatchIn(text)) return true
        return DIGIT_GROUPS.findAll(text).any { group -> group.value.count(Char::isDigit) >= 7 }
    }

    private fun hasControlCharacters(value: String, allowLineBreaks: Int): Boolean {
        var lineBreaks = 0
        for (codePoint in value.codePoints().toArray()) {
            if (codePoint == '\n'.code) {
                if (++lineBreaks > allowLineBreaks) return true
                continue
            }
            if (Character.getType(codePoint).isInvisibleType()) return true
        }
        return false
    }

    private fun String.withoutInvisibleCharacters(): String {
        val builder = StringBuilder()
        codePoints().filter { !Character.getType(it).isInvisibleType() }.forEach { builder.appendCodePoint(it) }
        return builder.toString()
    }

    private fun Int.isInvisibleType(): Boolean = when (this.toByte()) {
        Character.CONTROL, Character.FORMAT, Character.PRIVATE_USE, Character.SURROGATE,
        Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR, Character.UNASSIGNED,
        -> true
        else -> false
    }
}
