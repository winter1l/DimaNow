package com.example.dimanow.ui

import com.example.dimanow.domain.GuidanceKind
import com.example.dimanow.live.LiveChipContent
import com.example.dimanow.live.LiveClassOrder
import com.example.dimanow.live.LiveDisplayOptions
import org.junit.Assert.assertEquals
import org.junit.Test

/** D-140: the settings picture words each example exactly as the notification would. */
class NotificationPreviewTextTest {
    @Test
    fun chipChoiceChangesTheStatusBarChipAndTheNowBarSecondLine() {
        assertEquals("방시전 12분", guidancePreviewText(GuidanceKind.CLASS, LiveDisplayOptions(chipContent = LiveChipContent.COUNTDOWN)).chip)
        assertEquals("덕성관 510-1", guidancePreviewText(GuidanceKind.CLASS, LiveDisplayOptions(chipContent = LiveChipContent.CLASSROOM)).chip)
    }

    @Test
    fun orderChoiceSwapsWhatTheFirstLineAndTheDetailCarry() {
        val courseFirst = guidancePreviewText(GuidanceKind.CLASS, LiveDisplayOptions(classOrder = LiveClassOrder.COURSE_FIRST))
        assertEquals("13:00 · 방송시스템전기", courseFirst.title)
        assertEquals("시작까지 12분 · 덕성관 510-1", courseFirst.detail)

        val roomFirst = guidancePreviewText(GuidanceKind.CLASS, LiveDisplayOptions(classOrder = LiveClassOrder.CLASSROOM_FIRST))
        assertEquals("13:00 · 덕성관 510-1", roomFirst.title)
        assertEquals("시작까지 12분 · 방송시스템전기", roomFirst.detail)
    }

    @Test
    fun shuttleExamplesUseTheirOwnWordsWhateverTheClassChoices() {
        val classroom = LiveDisplayOptions(chipContent = LiveChipContent.CLASSROOM, classOrder = LiveClassOrder.CLASSROOM_FIRST)
        val campus = guidancePreviewText(GuidanceKind.CAMPUS_SHUTTLE, classroom)
        assertEquals("엔터관행 8분", campus.chip)
        assertEquals("본관 → 엔터관", campus.title)
        assertEquals("8분 후 출발 · 12:56", campus.detail)

        val bus = guidancePreviewText(GuidanceKind.BUS_4402, classroom)
        assertEquals("강남행 17분", bus.chip)
        assertEquals("4402 · 13:05 출발", bus.title)
        assertEquals("강남행 17분", bus.detail)
    }
}
