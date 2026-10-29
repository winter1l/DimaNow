package com.example.dimanow.lms

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** D-137: the 수업 list's status line and empty-list block are chosen as the list always chose them. */
class LmsListBlocksTest {
    private val readAt = Instant.parse("2026-10-23T13:05:00Z")

    @Test
    fun `the sign-in elsewhere comes first, then a failed refresh, then the read time`() {
        assertEquals(
            LmsListStatus.Elsewhere(readAt),
            lmsListStatus(signedInElsewhere = true, showsError = true, updatedLabel = "오늘 22:05에 불러왔어요", errorMessage = "실패", lastSuccessAt = readAt),
        )
        assertEquals(
            LmsListStatus.Failed("실패", readAt),
            lmsListStatus(signedInElsewhere = false, showsError = true, updatedLabel = "오늘 22:05에 불러왔어요", errorMessage = "실패", lastSuccessAt = readAt),
        )
        assertEquals(
            LmsListStatus.Updated("오늘 22:05에 불러왔어요"),
            lmsListStatus(signedInElsewhere = false, showsError = false, updatedLabel = "오늘 22:05에 불러왔어요", errorMessage = "실패", lastSuccessAt = readAt),
        )
        assertEquals(
            LmsListStatus.None,
            lmsListStatus(signedInElsewhere = false, showsError = false, updatedLabel = null, errorMessage = "실패", lastSuccessAt = null),
        )
    }

    @Test
    fun `only another kind of status changes the block, a new read time rolls in place`() {
        val morning = lmsListStatus(false, false, "오늘 09:10에 불러왔어요", "실패", readAt)
        val afternoon = lmsListStatus(false, false, "오늘 14:32에 불러왔어요", "실패", readAt)
        assertEquals(morning.kind, afternoon.kind)
    }

    @Test
    fun `an empty list keeps its wording and its one follow-up`() {
        fun empty(
            interactiveAuth: Boolean = false,
            needsSignIn: Boolean = false,
            neverLoaded: Boolean = false,
            filterActive: Boolean = false,
            todayMode: Boolean = true,
        ) = lmsEmptyPlaceholder(interactiveAuth, needsSignIn, neverLoaded, filterActive, todayMode)

        assertEquals(LmsListPlaceholder.Empty("추가 인증이 필요해요", null, LmsEmptyAction.REFRESH), empty(interactiveAuth = true, needsSignIn = true))
        assertEquals(
            LmsListPlaceholder.Empty("수업 정보를 확인하려면 로그인해 주세요", null, LmsEmptyAction.REFRESH),
            empty(needsSignIn = true, neverLoaded = true),
        )
        assertEquals(LmsListPlaceholder.Empty("아직 불러온 수업 정보가 없어요", null, LmsEmptyAction.LOAD), empty(neverLoaded = true))
        assertEquals(LmsListPlaceholder.Empty("선택한 조건에 맞는 학습이 없어요", null, LmsEmptyAction.CLEAR_FILTERS), empty(filterActive = true))
        assertEquals(LmsListPlaceholder.Empty("남은 할 일이 없어요", "새 과제나 공지가 올라오면 여기에 보여요.", null), empty())
        assertEquals(LmsListPlaceholder.Empty("등록된 학습이 없어요", null, null), empty(todayMode = false))
        assertEquals("다시 시도", LmsEmptyAction.REFRESH.label)
        assertEquals("수업 정보 불러오기", LmsEmptyAction.LOAD.label)
        assertEquals("필터 초기화", LmsEmptyAction.CLEAR_FILTERS.label)
    }
}
