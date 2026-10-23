package com.example.dimanow.dorm

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The three announcement shapes the user shared on 2026-10-23 (no personal data in them). */
class RollCallTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private val tuesday = ZonedDateTime.of(2026, 10, 20, 18, 2, 0, 0, seoul)

    private val examCancelled = """
        [시험 기간 점호 미실시 안내]

        금일 점호는 중간고사 기간으로 인해 실시되지 않습니다.

        관생분들께서는 참고 부탁드리며, 시험에서 좋은 결과 있으시길 바랍니다.🍀
    """.trimIndent()

    private val plainCancelled = """
        [예인관 점호 일정 안내]

        금일 점호는 진행되지 않습니다.

        관생분들께서는 참고 부탁드립니다.
    """.trimIndent()

    private val held = """
        [금일 점호 안내]

        금일 23시에 점호가 진행될 예정입니다.
        관생 여러분께서는 반드시 방문을 열고 점호를 대기해 주시기 바랍니다.

        ❗ 외출 및 외박 신청은 20시 59분까지 가능하며, 외출 및 외박 예정인 경우 시간에 유의하여 신청해 주시기 바랍니다.
        ❗ 원활한 점호 진행을 위해 문이 닫혀 있을 경우 추가로 노크하지 않습니다.
        ❗ 방 안에 있음에도 문을 열지 않아 발생하는 벌점은 본인 책임이니 유의해 주시기 바랍니다.
        ❗ 1층과 2층은 한 명이 점호를 진행할 예정이므로, 1층 관생분들께서는 잠시 대기해 주시기 바랍니다.

        관생 여러분의 협조 부탁드립니다.
    """.trimIndent()

    @Test
    fun `a cancelled 점호 keeps the day and the reason`() {
        val notice = requireNotNull(RollCallNoticeParser.parse(examCancelled, tuesday))
        assertEquals(LocalDate.of(2026, 10, 20), notice.date)
        assertFalse(notice.held)
        assertEquals("중간고사 기간", notice.reason)
        val plain = requireNotNull(RollCallNoticeParser.parse(plainCancelled, tuesday))
        assertFalse(plain.held)
        assertNull(plain.reason)
    }

    @Test
    fun `a held 점호 gives its time and the outing deadline, not a cancellation`() {
        val notice = requireNotNull(RollCallNoticeParser.parse(held, tuesday))
        assertTrue(notice.held)
        assertEquals(LocalTime.of(23, 0), notice.time)
        assertEquals(LocalTime.of(20, 59), notice.applyBy)
    }

    @Test
    fun `tomorrow and dated notices land on their day, other messages are ignored`() {
        assertEquals(LocalDate.of(2026, 10, 21), RollCallNoticeParser.parse("내일 점호는 실시되지 않습니다.", tuesday)?.date)
        assertEquals(LocalDate.of(2026, 10, 27), RollCallNoticeParser.parse("10월 27일 점호는 진행되지 않습니다.", tuesday)?.date)
        assertNull(RollCallNoticeParser.parse("세탁실 점검 안내입니다.", tuesday))
        assertNull(RollCallNoticeParser.parse("점호 관련 건의사항은 층장에게 말씀해 주세요.", tuesday))
    }

    @Test
    fun `today's status prefers the newest announcement, then the Tuesday rule`() {
        val day = LocalDate.of(2026, 10, 20)
        val first = requireNotNull(RollCallNoticeParser.parse(held, tuesday))
        val later = requireNotNull(RollCallNoticeParser.parse(plainCancelled, tuesday.plusHours(2)))
        assertEquals(RollCallStatus.Announced(later), RollCallRules.status(day, listOf(first, later), null, null))
        assertEquals(RollCallStatus.Scheduled(LocalTime.of(23, 0), "중간고사"), RollCallRules.status(day, emptyList(), null, "중간고사"))
        assertEquals(RollCallStatus.Holiday("개천절"), RollCallRules.status(day, emptyList(), "개천절", null))
        assertEquals(RollCallStatus.NotToday(LocalDate.of(2026, 10, 27)), RollCallRules.status(day.plusDays(1), emptyList(), null, null))
    }

    @Test
    fun `the door is open from 06 to 02 the next morning`() {
        assertTrue(DormHours.doorOpen(LocalTime.of(6, 0)))
        assertTrue(DormHours.doorOpen(LocalTime.of(1, 59)))
        assertFalse(DormHours.doorOpen(LocalTime.of(2, 0)))
        assertFalse(DormHours.doorOpen(LocalTime.of(5, 59)))
        assertTrue(DormHours.isOpen(LocalTime.of(23, 30), LocalTime.of(6, 0), LocalTime.of(0, 0)))
        assertFalse(DormHours.isOpen(LocalTime.of(0, 30), LocalTime.of(6, 0), LocalTime.of(0, 0)))
    }

    @Test
    fun `saved notices come back as they were`() {
        val notices = listOf(
            requireNotNull(RollCallNoticeParser.parse(held, tuesday)),
            requireNotNull(RollCallNoticeParser.parse(examCancelled, tuesday.plusMinutes(5))),
        )
        assertEquals(notices, decodeRollCallNotices(encodeRollCallNotices(notices)))
        assertEquals(emptyList<RollCallNotice>(), decodeRollCallNotices("[]"))
    }

    @Test
    fun `an announcement read again on reconnect is kept once`() {
        RollCallStore.clear()
        val notice = requireNotNull(RollCallNoticeParser.parse(held, tuesday))
        RollCallStore.add(notice, tuesday.toLocalDate())
        // D-115: the listener reads the shade again each time it connects.
        RollCallStore.add(requireNotNull(RollCallNoticeParser.parse(held, tuesday)), tuesday.toLocalDate())
        assertEquals(listOf(notice), RollCallStore.notices.value)
        RollCallStore.clear()
    }

    @Test
    fun `the 오늘 card names the source and the deadline`() {
        val held = requireNotNull(RollCallNoticeParser.parse(held, tuesday))
        assertEquals(
            "오늘 23:00 점호 · 문을 열고 기다려요" to "외출·외박 신청은 20:59까지 · 카톡 공지 18:02",
            com.example.dimanow.ui.rollCallText(RollCallStatus.Announced(held)),
        )
        val cancelled = requireNotNull(RollCallNoticeParser.parse(examCancelled, tuesday))
        assertEquals(
            "오늘은 점호가 없어요" to "중간고사 기간 · 카톡 공지 18:02",
            com.example.dimanow.ui.rollCallText(RollCallStatus.Announced(cancelled)),
        )
        assertEquals(
            "오늘 23:00 점호 예정" to "외출·외박 신청은 21:00 전까지 · 중간고사 기간이라 없을 수 있어요(카톡 공지 확인)",
            com.example.dimanow.ui.rollCallText(RollCallStatus.Scheduled(LocalTime.of(23, 0), "중간고사")),
        )
    }
}
