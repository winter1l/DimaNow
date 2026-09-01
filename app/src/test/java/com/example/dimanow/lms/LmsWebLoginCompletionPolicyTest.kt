package com.example.dimanow.lms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LmsWebLoginCompletionPolicyTest {
    @Test
    fun automaticLoginStartsAtTheOfficialPortalWithTheLmsReturnTarget() {
        assertTrue(
            OFFICIAL_LMS_LOGIN_URL ==
                "https://portal.dima.ac.kr/?r=https://lms.dima.ac.kr/sso/index.jsp",
        )
    }

    @Test
    fun authenticatedMainLoadsTheDashboardBeforeTheRenderedCatalogIsAccepted() {
        assertEquals(
            LmsLoginPageAction.WAIT,
            lmsLoginPageAction(
                "https://lms.dima.ac.kr/main/MainView.dunet",
                pageFinished = true,
                authenticatedMain = false,
            ),
        )
        assertEquals(
            LmsLoginPageAction.LOAD_DASHBOARD,
            lmsLoginPageAction(
                "https://lms.dima.ac.kr/main/MainView.dunet",
                pageFinished = true,
                authenticatedMain = true,
            ),
        )
        assertEquals(
            LmsLoginPageAction.EXTRACT_COURSES,
            lmsLoginPageAction(
                "https://lms.dima.ac.kr/lms/myLecture/doListView.dunet?mnid=201008840728",
                pageFinished = true,
                authenticatedMain = false,
            ),
        )
    }

    @Test
    fun submittedPortalLoginStillOnTheLoginPageRequestsCredentialReviewAfterTimeout() {
        val loginUrl = "https://portal.dima.ac.kr/?r=https://lms.dima.ac.kr/sso/index.jsp"

        assertFalse(shouldReviewStoredLmsCredentials(loginUrl, submitted = true, elapsedMillis = 9_999))
        assertTrue(shouldReviewStoredLmsCredentials(loginUrl, submitted = true, elapsedMillis = 10_000))
        assertFalse(
            shouldReviewStoredLmsCredentials(
                "https://lms.dima.ac.kr/main/MainView.dunet",
                submitted = true,
                elapsedMillis = 10_000,
            ),
        )
    }

    @Test
    fun officialPortalSsoPageIsRecognizedAsTheCredentialLoginPage() {
        assertTrue(isOfficialLmsCredentialPage("https://portal.dima.ac.kr/?r=https://lms.dima.ac.kr/sso/index.jsp"))
        assertTrue(isOfficialLmsCredentialPage("https://portal.dima.ac.kr/default.aspx?r=https://lms.dima.ac.kr/sso/index.jsp"))
        assertTrue(isOfficialLmsCredentialPage("https://lms.dima.ac.kr/login/doLoginPage.dunet"))
        assertFalse(isOfficialLmsCredentialPage("https://portal.dima.ac.kr/find/id_find.aspx"))
        assertFalse(isOfficialLmsCredentialPage("https://example.com/?r=https://lms.dima.ac.kr/sso/index.jsp"))
    }
}
