package com.example.dimanow.lms

import org.junit.Assert.assertEquals
import org.junit.Test

class LmsLoginFlowReducerTest {
    @Test
    fun repeated3045AfterOneTakeoverIsATerminalFailure() {
        val takeover = takeoverSubmissionState()

        val transition = reduceLmsLoginFlow(
            takeover,
            LmsLoginFlowEvent.MainFrameFinished(
                "https://portal.dima.ac.kr/sso/error.aspx?errorCode=3045",
            ),
        )

        val expected = LmsLoginResult.SessionTakeoverFailed(
            "다른 로그인 세션을 전환하지 못했습니다",
        )
        assertEquals(LmsLoginFlowStage.TERMINAL_ERROR, transition.state.stage)
        assertEquals(1, transition.state.sessionTakeoverSubmissions)
        assertEquals(expected, transition.state.result)
        assertEquals(LmsLoginFlowCommand.Complete(expected), transition.command)
    }

    @Test
    fun verifiedTakeoverCommandCanNeverBeSubmittedTwice() {
        val takeover = takeoverSubmissionState()

        val transition = reduceLmsLoginFlow(
            takeover,
            LmsLoginFlowEvent.SessionTakeoverActionVerified,
        )

        val expected = LmsLoginResult.SessionTakeoverFailed()
        assertEquals(LmsLoginFlowStage.TERMINAL_ERROR, transition.state.stage)
        assertEquals(1, transition.state.sessionTakeoverSubmissions)
        assertEquals(LmsLoginFlowCommand.Complete(expected), transition.command)
    }

    @Test
    fun missingVerifiedOfficialTakeoverActionFailsWithoutSubmittingAnything() {
        var transition = reduceLmsLoginFlow(
            LmsLoginFlowState.initial(),
            LmsLoginFlowEvent.CredentialsAvailable,
        )
        transition = reduceLmsLoginFlow(
            transition.state,
            LmsLoginFlowEvent.MainFrameFinished(
                "https://portal.dima.ac.kr/sso/error.aspx?errorCode=3045",
            ),
        )

        transition = reduceLmsLoginFlow(
            transition.state,
            LmsLoginFlowEvent.SessionTakeoverActionUnavailable,
        )

        val expected = LmsLoginResult.SessionTakeoverFailed()
        assertEquals(LmsLoginFlowStage.TERMINAL_ERROR, transition.state.stage)
        assertEquals(0, transition.state.sessionTakeoverSubmissions)
        assertEquals(LmsLoginFlowCommand.Complete(expected), transition.command)
    }

    @Test
    fun wrongHostTerminatesTheLoginFlowAsUnsafe() {
        val submitting = reduceLmsLoginFlow(
            LmsLoginFlowState.initial(),
            LmsLoginFlowEvent.CredentialsAvailable,
        ).state

        val transition = reduceLmsLoginFlow(
            submitting,
            LmsLoginFlowEvent.MainFrameFinished(
                "https://portal.dima.ac.kr.evil.example/sso/error.aspx?errorCode=3045",
            ),
        )

        val expected = LmsLoginResult.Failure("안전하지 않은 페이지가 차단되었습니다")
        assertEquals(LmsLoginFlowStage.TERMINAL_ERROR, transition.state.stage)
        assertEquals(expected, transition.state.result)
        assertEquals(LmsLoginFlowCommand.Complete(expected), transition.command)
    }

    @Test
    fun captchaOrOtpChallengeTerminatesWithoutAttemptingABypass() {
        val submitting = reduceLmsLoginFlow(
            LmsLoginFlowState.initial(),
            LmsLoginFlowEvent.CredentialsAvailable,
        ).state

        val transition = reduceLmsLoginFlow(
            submitting,
            LmsLoginFlowEvent.InteractiveChallengeDetected,
        )

        assertEquals(LmsLoginFlowStage.TERMINAL_ERROR, transition.state.stage)
        assertEquals(LmsLoginResult.InteractiveAuthenticationRequired, transition.state.result)
        assertEquals(
            LmsLoginFlowCommand.Complete(LmsLoginResult.InteractiveAuthenticationRequired),
            transition.command,
        )
    }

    @Test
    fun sessionConflictIsNeverReclassifiedAsRejectedCredentials() {
        val submitting = reduceLmsLoginFlow(
            LmsLoginFlowState.initial(),
            LmsLoginFlowEvent.CredentialsAvailable,
        ).state
        val rejected = reduceLmsLoginFlow(
            submitting,
            LmsLoginFlowEvent.CredentialsRejected,
        )
        assertEquals(LmsLoginResult.CredentialsRejected, rejected.state.result)

        val conflict = reduceLmsLoginFlow(
            submitting,
            LmsLoginFlowEvent.MainFrameFinished(
                "https://portal.dima.ac.kr/sso/error.aspx?errorCode=3045",
            ),
        ).state
        val staleRejection = reduceLmsLoginFlow(
            conflict,
            LmsLoginFlowEvent.CredentialsRejected,
        )

        assertEquals(LmsLoginFlowStage.SESSION_CONFLICT, staleRejection.state.stage)
        assertEquals(LmsLoginResult.SessionConflict, staleRejection.state.result)
        assertEquals(LmsLoginFlowCommand.None, staleRejection.command)
    }

    @Test
    fun terminalFailureCannotRestartOrSubmitAConflictTakeover() {
        val submitting = reduceLmsLoginFlow(
            LmsLoginFlowState.initial(),
            LmsLoginFlowEvent.CredentialsAvailable,
        ).state
        val terminal = reduceLmsLoginFlow(
            submitting,
            LmsLoginFlowEvent.InteractiveChallengeDetected,
        ).state

        val transition = reduceLmsLoginFlow(
            terminal,
            LmsLoginFlowEvent.MainFrameFinished(
                "https://portal.dima.ac.kr/sso/error.aspx?errorCode=3045",
            ),
        )

        assertEquals(terminal, transition.state)
        assertEquals(LmsLoginFlowCommand.None, transition.command)
    }

    @Test
    fun oneOfficial3045ConflictIsTakenOverThenLoginSucceeds() {
        var transition = reduceLmsLoginFlow(
            LmsLoginFlowState.initial(),
            LmsLoginFlowEvent.CredentialsAvailable,
        )

        transition = reduceLmsLoginFlow(
            transition.state,
            LmsLoginFlowEvent.MainFrameFinished(
                "https://portal.dima.ac.kr/sso/error.aspx?errorCode=3045",
            ),
        )
        assertEquals(LmsLoginFlowStage.SESSION_CONFLICT, transition.state.stage)
        assertEquals(LmsLoginResult.SessionConflict, transition.state.result)
        assertEquals(LmsLoginFlowCommand.InspectSessionTakeoverAction, transition.command)

        transition = reduceLmsLoginFlow(
            transition.state,
            LmsLoginFlowEvent.SessionTakeoverActionVerified,
        )
        assertEquals(LmsLoginFlowStage.SESSION_TAKEOVER_SUBMISSION, transition.state.stage)
        assertEquals(1, transition.state.sessionTakeoverSubmissions)
        assertEquals(LmsLoginFlowCommand.SubmitVerifiedSessionTakeover, transition.command)

        transition = reduceLmsLoginFlow(
            transition.state,
            LmsLoginFlowEvent.MainFrameFinished(
                url = "https://lms.dima.ac.kr/main/MainView.dunet",
                authenticatedMain = true,
            ),
        )
        transition = reduceLmsLoginFlow(
            transition.state,
            LmsLoginFlowEvent.MainFrameFinished(
                "https://lms.dima.ac.kr/lms/myLecture/doListView.dunet?to_do_type=all",
            ),
        )
        transition = reduceLmsLoginFlow(
            transition.state,
            LmsLoginFlowEvent.CourseExtractionFinished(hasCourses = true),
        )

        assertEquals(LmsLoginFlowStage.SUCCESS, transition.state.stage)
        assertEquals(1, transition.state.sessionTakeoverSubmissions)
        assertEquals(LmsLoginFlowCommand.Complete(LmsLoginResult.Success), transition.command)
    }

    @Test
    fun normalLoginConfirmsTheLmsMainThenExtractsCourses() {
        var transition = reduceLmsLoginFlow(
            LmsLoginFlowState.initial(),
            LmsLoginFlowEvent.CredentialsAvailable,
        )
        assertEquals(LmsLoginFlowStage.LOGIN_SUBMISSION, transition.state.stage)
        assertEquals(LmsLoginFlowCommand.SubmitCredentials, transition.command)

        transition = reduceLmsLoginFlow(
            transition.state,
            LmsLoginFlowEvent.MainFrameFinished(
                url = "https://lms.dima.ac.kr/main/MainView.dunet",
                authenticatedMain = true,
            ),
        )
        assertEquals(LmsLoginFlowStage.LMS_MAIN_CONFIRMATION, transition.state.stage)
        assertEquals(LmsLoginFlowCommand.LoadDashboard, transition.command)

        transition = reduceLmsLoginFlow(
            transition.state,
            LmsLoginFlowEvent.MainFrameFinished(
                url = "https://lms.dima.ac.kr/lms/myLecture/doListView.dunet?to_do_type=all",
            ),
        )
        assertEquals(LmsLoginFlowStage.COURSE_LIST_EXTRACTION, transition.state.stage)
        assertEquals(LmsLoginFlowCommand.ExtractCourses, transition.command)

        transition = reduceLmsLoginFlow(
            transition.state,
            LmsLoginFlowEvent.CourseExtractionFinished(hasCourses = true),
        )
        assertEquals(LmsLoginFlowStage.SUCCESS, transition.state.stage)
        assertEquals(LmsLoginFlowCommand.Complete(LmsLoginResult.Success), transition.command)
    }

    @Test
    fun takeoverScriptResultAcceptsOnlyTheSmallVerifiedProtocol() {
        assertEquals(
            LmsSessionTakeoverScriptResult.VERIFIED,
            parseLmsSessionTakeoverScriptResult("\"verified\""),
        )
        assertEquals(
            LmsSessionTakeoverScriptResult.SUBMITTED,
            parseLmsSessionTakeoverScriptResult("\"submitted\""),
        )
        assertEquals(
            LmsSessionTakeoverScriptResult.INTERACTIVE,
            parseLmsSessionTakeoverScriptResult("\"interactive\""),
        )
        assertEquals(
            LmsSessionTakeoverScriptResult.UNAVAILABLE,
            parseLmsSessionTakeoverScriptResult("{\"action\":\"Login\",\"value\":\"secret\"}"),
        )
        assertEquals(
            LmsSessionTakeoverScriptResult.UNAVAILABLE,
            parseLmsSessionTakeoverScriptResult(null),
        )
    }

    private fun takeoverSubmissionState(): LmsLoginFlowState {
        var transition = reduceLmsLoginFlow(
            LmsLoginFlowState.initial(),
            LmsLoginFlowEvent.CredentialsAvailable,
        )
        transition = reduceLmsLoginFlow(
            transition.state,
            LmsLoginFlowEvent.MainFrameFinished(
                "https://portal.dima.ac.kr/sso/error.aspx?errorCode=3045",
            ),
        )
        return reduceLmsLoginFlow(
            transition.state,
            LmsLoginFlowEvent.SessionTakeoverActionVerified,
        ).state
    }
}
