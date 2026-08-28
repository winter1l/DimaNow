package com.example.dimanow.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DisplayVocabularyTest {
    @Test
    fun `all user visible origin names use the approved vocabulary`() {
        val names = listOf(CampusZoneId.YEIN, CampusZoneId.MAIN, CampusZoneId.ONE_ROOM).map(DisplayVocabulary::originName)

        assertEquals(listOf("엔터관", "본관", "원룸촌"), names)
        assertFalse(names.any { it.contains("운동" + "장") })
    }
}
