package com.example.dimanow.lms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LmsLearningLaunchPolicyTest {
    @Test
    fun onlyNoticeMaterialAndAssignmentAreNativeDetails() {
        assertTrue(canRenderLmsItemNatively(LmsItemKind.NOTICE))
        assertTrue(canRenderLmsItemNatively(LmsItemKind.MATERIAL))
        assertTrue(canRenderLmsItemNatively(LmsItemKind.ASSIGNMENT))

        listOf(
            LmsItemKind.CONTENT,
            LmsItemKind.QUESTION,
            LmsItemKind.DISCUSSION,
            LmsItemKind.TEAM_PROJECT,
            LmsItemKind.QUIZ,
            LmsItemKind.EXAM,
            LmsItemKind.OTHER,
        ).forEach { kind ->
            assertFalse("$kind must use the official same-session screen", canRenderLmsItemNatively(kind))
        }
    }

    @Test
    fun selectsOnlyTheSingleExactLessonAfterRemovingCourseAndProgressText() {
        val candidates = listOf(
            "방송 프로그램 제작 0분 / 26분",
            "사운드디자인 기초(1) 0분 / 28분",
            "사운드디자인 기초(2) 0분 / 26분",
        )

        val selected = selectOfficialLearningCandidate(
            "[2026-2학기)음향기초실습(D반)] 사운드디자인 기초(1)(0분/28분)",
            candidates,
        )

        assertEquals(1, selected)
    }

    @Test
    fun uniquelyMatchesTheVerifiedCoursePageSubjectWithoutUsingSubstringGuessing() {
        val selected = selectOfficialLearningCandidate(
            "사운드디자인 기초(1)(0분/28분)",
            listOf(
                "사운드디자인 기초(1) (영상콘텐츠(MP4)) | 출석인정시간 : 28분",
                "사운드디자인 기초(2) (영상콘텐츠(MP4)) | 출석인정시간 : 26분",
            ),
        )

        assertEquals(0, selected)
        assertNull(
            selectOfficialLearningCandidate(
                "사운드디자인 기초(1)(0분/28분)",
                listOf(
                    "사운드디자인 기초(1) (영상콘텐츠(MP4)) | 출석인정시간 : 28분",
                    "사운드디자인 기초(1) (영상콘텐츠(MP4)) | 출석인정시간 : 28분",
                ),
            ),
        )
    }

    @Test
    fun ambiguousOrMissingLessonNeverGuesses() {
        assertNull(
            selectOfficialLearningCandidate(
                "사운드디자인 기초",
                listOf("사운드디자인 기초", "사운드디자인 기초"),
            ),
        )
        assertNull(
            selectOfficialLearningCandidate(
                "방송 프로그램 제작",
                listOf("사운드디자인 기초(1)", "사운드디자인 기초(2)"),
            ),
        )
        assertNull(
            selectOfficialLearningCandidate(
                "사운드디자인 기초(1)",
                listOf("1주차 사운드디자인 기초(1) 출석 0분 / 28분"),
            ),
        )
    }

    @Test
    fun acceptsOnlyTheOfficialLearningStartConfirmation() {
        assertTrue(
            shouldConfirmOfficialLearningDialog(
                "https://lms.dima.ac.kr/lms/class/courseSchedule/doListView.dunet",
                "학습 시작을 하시겠습니까?",
            ),
        )
        assertFalse(
            shouldConfirmOfficialLearningDialog(
                "https://portal.dima.ac.kr/",
                "학습 시작을 하시겠습니까?",
            ),
        )
        assertFalse(
            shouldConfirmOfficialLearningDialog(
                "https://lms.dima.ac.kr/lms/class/courseSchedule/doListView.dunet",
                "파일을 삭제하시겠습니까?",
            ),
        )
    }

    @Test
    fun launchWatchdogFallsBackOnlyWhileThePlayerIsStillPending() {
        assertEquals(
            LmsLearningLaunchState.OFFICIAL_FALLBACK,
            reduceOfficialLearningLaunch(
                LmsLearningLaunchState.REQUESTED,
                LmsLearningLaunchEvent.WATCHDOG_EXPIRED,
            ),
        )
        assertEquals(
            LmsLearningLaunchState.PLAYER_OPENED,
            reduceOfficialLearningLaunch(
                LmsLearningLaunchState.PLAYER_OPENED,
                LmsLearningLaunchEvent.WATCHDOG_EXPIRED,
            ),
        )
        assertEquals(
            LmsLearningLaunchState.OFFICIAL_FALLBACK,
            reduceOfficialLearningLaunch(
                LmsLearningLaunchState.LOCATING,
                LmsLearningLaunchEvent.EXACT_CANDIDATE_UNAVAILABLE,
            ),
        )
        assertEquals(
            LmsLearningLaunchState.OFFICIAL_FALLBACK,
            reduceOfficialLearningLaunch(
                LmsLearningLaunchState.REQUESTED,
                LmsLearningLaunchEvent.EXACT_CANDIDATE_UNAVAILABLE,
            ),
        )
    }

    @Test
    fun confirmedFallbackBecomesOpenedOnlyAfterTheOfficialScreenIsDisplayed() {
        val fallback = reduceOfficialLearningLaunch(
            LmsLearningLaunchState.REQUESTED,
            LmsLearningLaunchEvent.WATCHDOG_EXPIRED,
        )
        val displayed = reduceOfficialLearningLaunch(
            fallback,
            LmsLearningLaunchEvent.OFFICIAL_FALLBACK_DISPLAYED,
        )

        assertEquals(LmsLearningLaunchState.OFFICIAL_FALLBACK_OPENED, displayed)
        assertTrue(didConfirmedLearningBecomeOpened(fallback, displayed))

        val repeated = reduceOfficialLearningLaunch(
            displayed,
            LmsLearningLaunchEvent.OFFICIAL_FALLBACK_DISPLAYED,
        )
        assertEquals(LmsLearningLaunchState.OFFICIAL_FALLBACK_OPENED, repeated)
        assertFalse(didConfirmedLearningBecomeOpened(displayed, repeated))

        val failedBeforeOfficialScreen = reduceOfficialLearningLaunch(
            LmsLearningLaunchState.LOCATING,
            LmsLearningLaunchEvent.OFFICIAL_FALLBACK_DISPLAYED,
        )
        assertEquals(LmsLearningLaunchState.LOCATING, failedBeforeOfficialScreen)
        assertFalse(
            didConfirmedLearningBecomeOpened(
                LmsLearningLaunchState.LOCATING,
                failedBeforeOfficialScreen,
            ),
        )
    }
}
