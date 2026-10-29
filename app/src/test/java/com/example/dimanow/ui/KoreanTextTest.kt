package com.example.dimanow.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** D-134: Korean words stay whole when a line wraps. */
class KoreanTextTest {
    private fun shown(text: String) = keepKoreanWords(text).replace(WORD_JOINER, '+')

    @Test
    fun wordsAreJoinedAndSpacesStayBreakable() {
        assertEquals("확+인+할 수 있+어+요", shown("확인할 수 있어요"))
        // A period stays with its word.
        assertEquals("게+이+트+에 대+요+.", shown("게이트에 대요."))
    }

    @Test
    fun latinAndPunctuationTouchingHangulJoinButMiddleDotsAndSlashesStillBreak() {
        assertEquals("DIMA C+에+서", shown("DIMA C에서"))
        assertEquals("(portal)+에+만", shown("(portal)에만"))
        // The dot stays with the word before it; the line may break after it.
        assertEquals("세+탁+실+·휴+게+실+·편+의+점", shown("세탁실·휴게실·편의점"))
        assertEquals("09:00~02:00 · 받+는 주+소", shown("09:00~02:00 · 받는 주소"))
    }

    @Test
    fun textWithoutHangulIsUnchanged() {
        assertEquals("Apache License 2.0", keepKoreanWords("Apache License 2.0"))
    }
}
