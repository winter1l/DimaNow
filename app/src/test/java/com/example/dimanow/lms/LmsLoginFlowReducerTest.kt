package com.example.dimanow.lms

import org.junit.Assert.assertEquals
import org.junit.Test

class LmsLoginFlowReducerTest {
    private val conflict = "https://portal.dima.ac.kr/sso/error.aspx?errorCode=3045"

    @Test
    fun a3045AnswerSignsInOnceMoreAndSaysTheSignInElsewhereEnded() {
        // D-135: the portal ends the other sign-in as it answers 3045, so the second attempt goes through.
        var transition = reduceLmsLoginFlow(LmsLoginFlowState.initial(), LmsLoginFlowEvent.CredentialsAvailable)
        transition = reduceLmsLoginFlow(transition.state, LmsLoginFlowEvent.MainFrameFinished(conflict))
        assertEquals(LmsLoginFlowStage.CONFLICT_RETRY, transition.state.stage)
        assertEquals(1, transition.state.conflictRetries)
        assertEquals(LmsLoginFlowCommand.RetryCredentials, transition.command)

        transition = reduceLmsLoginFlow(
            transition.state,
            LmsLoginFlowEvent.MainFrameFinished("https://lms.dima.ac.kr/main/MainView.dunet", authenticatedMain = true),
        )
        transition = reduceLmsLoginFlow(
            transition.state,
            LmsLoginFlowEvent.MainFrameFinished("https://lms.dima.ac.kr/lms/myLecture/doListView.dunet?to_do_type=all"),
        )
        transition = reduceLmsLoginFlow(transition.state, LmsLoginFlowEvent.CourseExtractionFinished(hasCourses = true))

        assertEquals(LmsLoginFlowStage.SUCCESS, transition.state.stage)
        assertEquals(LmsLoginFlowCommand.Complete(LmsLoginResult.SuccessEndingElsewhere), transition.command)
    }

    @Test
    fun aSecond3045FailsWithoutAThirdSignIn() {
        val retrying = retryState()

        val transition = reduceLmsLoginFlow(retrying, LmsLoginFlowEvent.MainFrameFinished(conflict))

        val expected = LmsLoginResult.SessionTakeoverFailed()
        assertEquals(LmsLoginFlowStage.TERMINAL_ERROR, transition.state.stage)
        assertEquals(1, transition.state.conflictRetries)
        assertEquals(LmsLoginFlowCommand.Complete(expected), transition.command)
    }

    @Test
    fun aRejectedAccountDuringTheRetryStillAsksForReview() {
        val transition = reduceLmsLoginFlow(retryState(), LmsLoginFlowEvent.CredentialsRejected)

        assertEquals(LmsLoginFlowCommand.Complete(LmsLoginResult.CredentialsRejected), transition.command)
    }

    @Test
    fun aLoginThatDoesNotStickAfterTheRetryIsAnOrdinaryFailure() {
        val transition = reduceLmsLoginFlow(retryState(), LmsLoginFlowEvent.LoginNotEstablished)

        assertEquals(
            LmsLoginFlowCommand.Complete(LmsLoginResult.Failure(LmsUserMessages.LOGIN_NOT_ESTABLISHED)),
            transition.command,
        )
    }

    @Test
    fun wrongHostTerminatesTheLoginFlowAsUnsafe() {
        val submitting = reduceLmsLoginFlow(LmsLoginFlowState.initial(), LmsLoginFlowEvent.CredentialsAvailable).state

        val transition = reduceLmsLoginFlow(
            submitting,
            LmsLoginFlowEvent.MainFrameFinished("https://portal.dima.ac.kr.evil.example/sso/error.aspx?errorCode=3045"),
        )

        val expected = LmsLoginResult.Failure(LmsUserMessages.UNSAFE_PAGE_BLOCKED)
        assertEquals(LmsLoginFlowStage.TERMINAL_ERROR, transition.state.stage)
        assertEquals(expected, transition.state.result)
        assertEquals(LmsLoginFlowCommand.Complete(expected), transition.command)
    }

    @Test
    fun captchaOrOtpChallengeTerminatesWithoutAttemptingABypass() {
        val submitting = reduceLmsLoginFlow(LmsLoginFlowState.initial(), LmsLoginFlowEvent.CredentialsAvailable).state

        val transition = reduceLmsLoginFlow(submitting, LmsLoginFlowEvent.InteractiveChallengeDetected)

        assertEquals(LmsLoginFlowStage.TERMINAL_ERROR, transition.state.stage)
        assertEquals(LmsLoginResult.InteractiveAuthenticationRequired, transition.state.result)
        assertEquals(LmsLoginFlowCommand.Complete(LmsLoginResult.InteractiveAuthenticationRequired), transition.command)
    }

    @Test
    fun aRejectionAfterTheFlowMovedOnIsIgnored() {
        val main = reduceLmsLoginFlow(
            reduceLmsLoginFlow(LmsLoginFlowState.initial(), LmsLoginFlowEvent.CredentialsAvailable).state,
            LmsLoginFlowEvent.MainFrameFinished("https://lms.dima.ac.kr/main/MainView.dunet", authenticatedMain = true),
        ).state

        val transition = reduceLmsLoginFlow(main, LmsLoginFlowEvent.CredentialsRejected)

        assertEquals(main, transition.state)
        assertEquals(LmsLoginFlowCommand.None, transition.command)
    }

    @Test
    fun terminalFailureCannotRestartOrSignInAgain() {
        val terminal = reduceLmsLoginFlow(
            reduceLmsLoginFlow(LmsLoginFlowState.initial(), LmsLoginFlowEvent.CredentialsAvailable).state,
            LmsLoginFlowEvent.InteractiveChallengeDetected,
        ).state

        val transition = reduceLmsLoginFlow(terminal, LmsLoginFlowEvent.MainFrameFinished(conflict))

        assertEquals(terminal, transition.state)
        assertEquals(LmsLoginFlowCommand.None, transition.command)
    }

    @Test
    fun normalLoginConfirmsTheLmsMainThenExtractsCourses() {
        var transition = reduceLmsLoginFlow(LmsLoginFlowState.initial(), LmsLoginFlowEvent.CredentialsAvailable)
        assertEquals(LmsLoginFlowStage.LOGIN_SUBMISSION, transition.state.stage)
        assertEquals(LmsLoginFlowCommand.SubmitCredentials, transition.command)

        transition = reduceLmsLoginFlow(
            transition.state,
            LmsLoginFlowEvent.MainFrameFinished(url = "https://lms.dima.ac.kr/main/MainView.dunet", authenticatedMain = true),
        )
        assertEquals(LmsLoginFlowStage.LMS_MAIN_CONFIRMATION, transition.state.stage)
        assertEquals(LmsLoginFlowCommand.LoadDashboard, transition.command)

        transition = reduceLmsLoginFlow(
            transition.state,
            LmsLoginFlowEvent.MainFrameFinished(url = "https://lms.dima.ac.kr/lms/myLecture/doListView.dunet?to_do_type=all"),
        )
        assertEquals(LmsLoginFlowStage.COURSE_LIST_EXTRACTION, transition.state.stage)
        assertEquals(LmsLoginFlowCommand.ExtractCourses, transition.command)

        transition = reduceLmsLoginFlow(transition.state, LmsLoginFlowEvent.CourseExtractionFinished(hasCourses = true))
        assertEquals(LmsLoginFlowStage.SUCCESS, transition.state.stage)
        assertEquals(LmsLoginFlowCommand.Complete(LmsLoginResult.Success), transition.command)
    }

    @Test
    fun anOrdinaryLoginThatDoesNotStickIsNotReportedAsASignInElsewhere() {
        // The 2026-10-24 report: nobody else was signed in, LMS showed its signed-in main page, then the app was sent
        // back without a session.
        var transition = reduceLmsLoginFlow(LmsLoginFlowState.initial(), LmsLoginFlowEvent.CredentialsAvailable)
        transition = reduceLmsLoginFlow(
            transition.state,
            LmsLoginFlowEvent.MainFrameFinished("https://lms.dima.ac.kr/main/MainView.dunet", authenticatedMain = true),
        )
        assertEquals(LmsLoginFlowStage.LMS_MAIN_CONFIRMATION, transition.state.stage)

        transition = reduceLmsLoginFlow(transition.state, LmsLoginFlowEvent.LoginNotEstablished)

        val expected = LmsLoginResult.Failure(LmsUserMessages.LOGIN_NOT_ESTABLISHED)
        assertEquals(LmsLoginFlowStage.TERMINAL_ERROR, transition.state.stage)
        assertEquals(LmsLoginFlowCommand.Complete(expected), transition.command)
    }

    private fun retryState(): LmsLoginFlowState = reduceLmsLoginFlow(
        reduceLmsLoginFlow(LmsLoginFlowState.initial(), LmsLoginFlowEvent.CredentialsAvailable).state,
        LmsLoginFlowEvent.MainFrameFinished(conflict),
    ).state
}
