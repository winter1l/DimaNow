package com.example.dimanow.ui

import com.example.dimanow.domain.CampusNotice
import com.example.dimanow.guidance.HomeBase
import com.example.dimanow.notice.NoticeKeywordAlerts
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeLayoutTest {
    @Test
    fun savedOrderComesFirstThenUnnamedCardsInTheirDefaultPlaceAndHiddenOnesDrop() {
        assertEquals(HomeCard.entries, homeCardOrder(emptyList(), emptySet()))
        assertEquals(
            listOf(HomeCard.SHUTTLE, HomeCard.LEARNING, HomeCard.ACADEMIC, HomeCard.ROLL_CALL, HomeCard.NOTICES),
            homeCardOrder(listOf("shuttle", "learning", "unknown", "shuttle"), setOf("meal")),
        )
    }

    @Test
    fun favouritesDefaultUntilPickedAndAddOutingFor엔터관() {
        assertEquals(listOf(HomeShortcut.TIMETABLE, HomeShortcut.LIBRARY_CARD), homeFavorites(null, HomeBase.COMMUTE))
        assertEquals(listOf(HomeShortcut.TIMETABLE, HomeShortcut.OUTING, HomeShortcut.LIBRARY_CARD), homeFavorites(null, HomeBase.YEIN))
        assertEquals(emptyList<HomeShortcut>(), homeFavorites(emptyList(), HomeBase.YEIN))
        assertEquals(listOf(HomeShortcut.HEYOUNG), homeFavorites(listOf("heyoung", "gone"), HomeBase.YEIN))
    }

    @Test
    fun keywordsAreTrimmedCappedAndNotRepeated() {
        assertEquals("장학", cleanNoticeKeyword("  장학 ", emptyList()))
        assertNull(cleanNoticeKeyword("장학", listOf("장학")))
        assertNull(cleanNoticeKeyword("   ", emptyList()))
        assertEquals(MAX_NOTICE_KEYWORD_LENGTH, cleanNoticeKeyword("가".repeat(40), emptyList())?.length)
        assertNull(cleanNoticeKeyword("새 단어", List(MAX_NOTICE_KEYWORDS) { "k$it" }))
    }

    @Test
    fun onlyUnseenNoticesWithAKeywordAlertAndTheFirstSyncAlertsNothing() {
        val notices = listOf(
            notice("1", "2026학년도 2학기 수강 신청 안내"),
            notice("2", "기숙사 소방 점검"),
            notice("3", "교내 장학금 신청"),
        )
        val keywords = listOf("수강신청", "장학")
        assertEquals(emptyList<Pair<CampusNotice, String>>(), NoticeKeywordAlerts.matches(notices, null, keywords))
        assertEquals(
            listOf("1" to "수강신청", "3" to "장학"),
            NoticeKeywordAlerts.matches(notices, emptySet(), keywords).map { (notice, keyword) -> notice.id to keyword },
        )
        assertEquals(listOf("3"), NoticeKeywordAlerts.matches(notices, setOf("1", "2"), keywords).map { it.first.id })
    }

    private fun notice(id: String, title: String) = CampusNotice(id, title, "https://www.dima.ac.kr/?p=111&idx=$id", LocalDate.of(2026, 10, 26), false)
}
