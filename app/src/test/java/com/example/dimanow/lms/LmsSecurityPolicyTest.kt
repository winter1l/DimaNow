package com.example.dimanow.lms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LmsSecurityPolicyTest {
    @Test
    fun onlyExactOfficialHttpsHostsAreAllowed() {
        assertTrue(LmsUrlPolicy.isAllowed("https://lms.dima.ac.kr/lms/myLecture/doListView.dunet"))
        assertTrue(LmsUrlPolicy.isAllowed("https://portal.dima.ac.kr/"))
        assertTrue(LmsUrlPolicy.isAllowed("https://lms.dima.ac.kr:443/lms/myLecture/doListView.dunet"))
        assertTrue(LmsUrlPolicy.isAllowed("https://portal.dima.ac.kr:443/"))
        assertFalse(LmsUrlPolicy.isAllowed("http://lms.dima.ac.kr/login"))
        assertFalse(LmsUrlPolicy.isAllowed("https://lms.dima.ac.kr.evil.example/login"))
        assertFalse(LmsUrlPolicy.isAllowed("https://user@lms.dima.ac.kr/login"))
        assertFalse(LmsUrlPolicy.isAllowed("https://lms.dima.ac.kr:8443/login"))
        assertFalse(LmsUrlPolicy.isAllowed("https://portal.dima.ac.kr:9443/"))
    }

    @Test
    fun officialPortalSsoHttpNavigationIsUpgradedWithoutChangingItsTarget() {
        assertEquals(
            "https://lms.dima.ac.kr/sso/index.jsp?ticket=one-time",
            LmsUrlPolicy.upgradeOfficialHttp("http://lms.dima.ac.kr/sso/index.jsp?ticket=one-time"),
        )
        assertEquals(
            "https://portal.dima.ac.kr/?r=https://lms.dima.ac.kr/sso/index.jsp",
            LmsUrlPolicy.upgradeOfficialHttp("http://portal.dima.ac.kr/?r=https://lms.dima.ac.kr/sso/index.jsp"),
        )
        assertNull(LmsUrlPolicy.upgradeOfficialHttp("http://lms.dima.ac.kr.evil.example/sso/index.jsp"))
        assertNull(LmsUrlPolicy.upgradeOfficialHttp("https://lms.dima.ac.kr/sso/index.jsp"))
    }

    @Test
    fun approvedSsoBridgesAreAllowedOnlyDuringLoginAtTheirExactPortAndPaths() {
        assertTrue(
            LmsUrlPolicy.isAllowedLoginNavigation(
                "http://sso.dima.ac.kr:8080/sso/pmi-sso.jsp?ticket=one-time",
            ),
        )
        assertTrue(
            LmsUrlPolicy.isAllowedLoginNavigation(
                "http://sso.dima.ac.kr:8080/sso/pmi-sso2.jsp?ticket=one-time",
            ),
        )
        assertTrue(LmsUrlPolicy.isAllowedLoginNavigation("https://portal.dima.ac.kr/"))
        assertFalse(LmsUrlPolicy.isAllowed("http://sso.dima.ac.kr:8080/sso/pmi-sso.jsp?ticket=one-time"))
        assertFalse(LmsUrlPolicy.isAllowedLoginNavigation("http://sso.dima.ac.kr/sso/pmi-sso.jsp"))
        assertFalse(LmsUrlPolicy.isAllowedLoginNavigation("http://sso.dima.ac.kr:8080/other.jsp"))
        assertFalse(LmsUrlPolicy.isAllowedLoginNavigation("http://sso.dima.ac.kr:8080/sso/pmi-sso3.jsp"))
        assertFalse(LmsUrlPolicy.isAllowedLoginNavigation("http://sso.dima.ac.kr.evil.example:8080/sso/pmi-sso.jsp"))
        assertFalse(LmsUrlPolicy.isAllowedLoginNavigation("http://user@sso.dima.ac.kr:8080/sso/pmi-sso.jsp"))
        assertFalse(LmsUrlPolicy.isAllowedLoginNavigation("http://sso.dima.ac.kr:8080/sso/%70mi-sso.jsp"))
        assertFalse(LmsUrlPolicy.isAllowedLoginNavigation("http://sso.dima.ac.kr:8080/sso/pmi-sso.jsp#other"))
    }

    @Test
    fun credentialScriptChecksTheLiveDocumentLocationBeforeTouchingAnyField() {
        val portalScript = lmsCredentialSubmissionScript("fixture-user", "fixture-password", portal = true)
        val portalGuard = "var page=location.protocol+'//'+location.host+location.pathname;" +
            "if(!(page===\"https://portal.dima.ac.kr/\"||page===\"https://portal.dima.ac.kr/default.aspx\"))" +
            "return 'origin-mismatch';"
        assertEquals(portalGuard, lmsCredentialPageGuard(portal = true))
        assertTrue(portalScript.contains(portalGuard))
        val guardIndex = portalScript.indexOf(portalGuard)
        val firstStatement = portalScript.substringAfter("(function(){").trimStart()
        assertTrue(firstStatement.startsWith(portalGuard))
        for (laterStep in listOf("document.querySelector", "\"fixture-user\"", "\"fixture-password\"", "Login('N')")) {
            assertTrue(laterStep, portalScript.indexOf(laterStep) > guardIndex + portalGuard.length)
        }
        assertFalse(portalScript.contains("lms.dima.ac.kr"))
        assertFalse(portalScript.contains("sso.dima.ac.kr"))

        val lmsScript = lmsCredentialSubmissionScript("fixture-user", "fixture-password", portal = false)
        val lmsGuard = "var page=location.protocol+'//'+location.host+location.pathname;" +
            "if(!(page===\"https://lms.dima.ac.kr/login/doLoginPage.dunet\"))return 'origin-mismatch';"
        assertTrue(lmsScript.substringAfter("(function(){").trimStart().startsWith(lmsGuard))
        assertTrue(lmsScript.indexOf("login_proc()") > lmsScript.indexOf(lmsGuard))
        assertFalse(lmsScript.contains("portal.dima.ac.kr"))
        // The guard must not rely on page-overridable prototype methods.
        assertFalse(portalGuard.contains("indexOf") || portalGuard.contains("includes") || portalGuard.contains("some("))
    }

    @Test
    fun credentialPageGuardListsMatchTheKotlinCredentialPageCheck() {
        (PORTAL_CREDENTIAL_PAGES + LMS_CREDENTIAL_PAGES).forEach { page ->
            assertTrue(page, isOfficialLmsCredentialPage(page))
        }
        assertFalse(isOfficialLmsCredentialPage("http://sso.dima.ac.kr:8080/sso/pmi-sso.jsp"))
        assertFalse(isOfficialLmsCredentialPage("http://portal.dima.ac.kr/"))
        assertFalse(isOfficialLmsCredentialPage("https://portal.dima.ac.kr:8443/"))
        assertFalse(isOfficialLmsCredentialPage("https://user@lms.dima.ac.kr/login/doLoginPage.dunet"))
        assertTrue(isOfficialLmsCredentialPage("https://lms.dima.ac.kr:443/login/doLoginPage.dunet"))
    }

    @Test
    fun credentialsAreEncodedAsInertJavaScriptStringLiterals() {
        assertEquals("\"plain\"", jsStringLiteral("plain"))
        assertEquals("\"a\\\"b\\\\c\"", jsStringLiteral("a\"b\\c"))
        assertEquals("\"\\n\\r\\t\\u0000\"", jsStringLiteral("\n\r\t\u0000"))
        assertEquals("\"\\u003c/script\\u003e\\u2028\\u2029\"", jsStringLiteral("</script>\u2028\u2029"))
        assertEquals("\"비밀'번호\"", jsStringLiteral("비밀'번호"))
        val script = lmsCredentialSubmissionScript("id\"');alert(1);//", "p\\w", portal = false)
        assertTrue(script.contains("i.value=\"id\\\"');alert(1);//\";p.value=\"p\\\\w\";"))
    }

    @Test
    fun rfc5987KoreanAttachmentNameIsDecodedAndPathCharactersAreRemoved() {
        val header = "attachment; filename*=UTF-8''%EC%88%98%EC%97%85%2F%EC%95%88%EB%82%B4.pdf"

        assertEquals("수업_안내.pdf", LmsAttachmentNaming.fromContentDisposition(header, "첨부파일"))
    }

    @Test
    fun percentAndPlusEncodedBasicFilenameIsDecodedForTheSaveDialog() {
        val header = "attachment; filename=\"%EC%88%98%EC%97%85+%EC%95%88%EB%82%B4.pdf\""

        assertEquals("수업 안내.pdf", LmsAttachmentNaming.fromContentDisposition(header, "fallback.pdf"))
    }

    @Test
    fun encodedFilenameIsDecodedExactlyOnceAndUnsafeCharactersAreRemoved() {
        assertEquals(
            "%2E%2E%2Fnotes.pdf",
            LmsAttachmentNaming.fromContentDisposition(
                "attachment; filename=\"%252E%252E%252Fnotes.pdf\"",
                "fallback.pdf",
            ),
        )
        assertEquals(
            "_report.pdf",
            LmsAttachmentNaming.fromContentDisposition(
                "attachment; filename=\"%2E%2E%2F%00report.pdf\"",
                "fallback.pdf",
            ),
        )
        assertEquals(
            "lecture-notes.pdf",
            LmsAttachmentNaming.fromContentDisposition(
                "attachment; filename=\"lecture-notes.pdf\"",
                "fallback.pdf",
            ),
        )
        assertEquals(
            "C++-notes.pdf",
            LmsAttachmentNaming.fromContentDisposition(
                "attachment; filename=\"C++-notes.pdf\"",
                "fallback.pdf",
            ),
        )
        assertEquals(
            "A+B.pdf",
            LmsAttachmentNaming.fromContentDisposition(
                "attachment; filename=\"A+B.pdf\"",
                "fallback.pdf",
            ),
        )
    }
}
