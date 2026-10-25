package com.example.dimanow.dorm

import com.example.dimanow.ui.showsWhatsNew
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutingReminderTest {
    private val day = LocalDate.of(2026, 10, 27)
    private fun at(time: String) = ZonedDateTime.parse("2026-10-27T$time:00+09:00[Asia/Seoul]")
    private fun notice(held: Boolean = true, applyBy: LocalTime? = null, receivedAt: String = "2026-10-27T10:00:00Z", date: LocalDate = day) =
        RollCallNotice(date = date, held = held, time = LocalTime.of(23, 0), applyBy = applyBy, receivedAt = Instant.parse(receivedAt))

    @Test
    fun remindsAnHourBeforeTheAnnouncedOrUsualDeadline() {
        assertEquals(at("19:59"), OutingReminder.remindAt(notice(applyBy = LocalTime.of(20, 59)), at("12:00")))
        assertEquals(at("20:00"), OutingReminder.remindAt(notice(), at("12:00")))
    }

    @Test
    fun aNoticeInsideTheLastHourRingsAtOnceAndAfterTheDeadlineNever() {
        assertEquals(at("20:30"), OutingReminder.remindAt(notice(), at("20:30")))
        assertNull(OutingReminder.remindAt(notice(), at("21:00")))
        assertNull(OutingReminder.remindAt(notice(applyBy = LocalTime.of(20, 59)), at("20:59")))
    }

    @Test
    fun aCancelledOrPastDayNeverRings() {
        assertNull(OutingReminder.remindAt(notice(held = false), at("12:00")))
        assertNull(OutingReminder.remindAt(notice(date = day.minusDays(1)), at("12:00")))
    }

    @Test
    fun theNewestNoticeOfEachDayDecidesAndTheEarliestReminderWins() {
        val cancelledLater = listOf(notice(), notice(held = false, receivedAt = "2026-10-27T11:00:00Z"))
        assertNull(OutingReminder.nextReminder(cancelledLater, at("12:00")))
        val twoDays = listOf(notice(date = day.plusDays(7)), notice())
        assertEquals(at("20:00"), OutingReminder.nextReminder(twoDays, at("12:00")))
    }

    @Test
    fun theTextGivesThe점호TimeAndTheDeadline() {
        assertEquals(
            "오늘 23:00 점호가 있어요" to "외출·외박은 20:59까지 신청할 수 있어요",
            OutingReminder.text(notice(applyBy = LocalTime.of(20, 59))),
        )
        assertEquals("외출·외박은 21:00 전에 신청해 주세요", OutingReminder.text(notice()).second)
    }

    @Test
    fun onlyPlainOneToOneChatsAreLeftOut() {
        assertTrue(isOneToOneChat(isGroupConversation = false, conversationTitle = null, subText = null))
        assertFalse(isOneToOneChat(isGroupConversation = true, conversationTitle = null, subText = null))
        assertFalse(isOneToOneChat(isGroupConversation = false, conversationTitle = "예인관 자치회", subText = null))
        assertFalse(isOneToOneChat(isGroupConversation = false, conversationTitle = null, subText = "예인관 자치회"))
    }

    @Test
    fun aQuestionIsNotAnAnnouncement() {
        val received = at("20:00")
        assertNull(RollCallNoticeParser.parse("오늘 점호 없어?", received))
        assertNull(RollCallNoticeParser.parse("금일 점호 취소되었나요？", received))
    }

    @Test
    fun whatsNewShowsAfterAnUpdateOnly() {
        assertTrue(showsWhatsNew(seenVersionCode = null, currentVersionCode = 10, onboardingCompleted = true))
        assertTrue(showsWhatsNew(seenVersionCode = 9, currentVersionCode = 10, onboardingCompleted = true))
        assertFalse(showsWhatsNew(seenVersionCode = 10, currentVersionCode = 10, onboardingCompleted = true))
        assertFalse(showsWhatsNew(seenVersionCode = null, currentVersionCode = 10, onboardingCompleted = false))
    }
}
