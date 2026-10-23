package com.example.dimanow.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseAbbreviationTest {
    @Test
    fun `the first syllable of each word, up to three`() {
        assertEquals("방시전", CourseAbbreviation.of("방송시스템전기"))
        assertEquals("조기실", CourseAbbreviation.of("조명기초및실습"))
        assertEquals("스조실", CourseAbbreviation.of("스튜디오조명실습"))
        assertEquals("영제실", CourseAbbreviation.of("영상제작실습"))
        assertEquals("뉴콘제", CourseAbbreviation.of("뉴미디어콘텐츠제작"))
        // Spaces, brackets and numbers do not change the words.
        assertEquals("음녹실", CourseAbbreviation.of("음향 녹음 실습(2)"))
        assertEquals("실음이", CourseAbbreviation.of("실용음악이론 II"))
    }

    @Test
    fun `short names stay whole and long single words are cut to two`() {
        assertEquals("연기", CourseAbbreviation.of("연기"))
        assertEquals("화성학", CourseAbbreviation.of("화성학"))
        assertEquals("연기론", CourseAbbreviation.of("연기론"))
        assertEquals("엔터", CourseAbbreviation.of("엔터테인먼트"))
        assertEquals("UX디", CourseAbbreviation.of("UX디자인"))
    }

    @Test
    fun `an unknown stretch counts as two-syllable words`() {
        assertEquals(listOf("한국", "근대", "사회"), CourseAbbreviation.words("한국근대사회"))
        assertEquals("한근사", CourseAbbreviation.of("한국근대사회"))
    }
}
