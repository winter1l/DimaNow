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
        assertFalse(LmsUrlPolicy.isAllowed("http://lms.dima.ac.kr/login"))
        assertFalse(LmsUrlPolicy.isAllowed("https://lms.dima.ac.kr.evil.example/login"))
        assertFalse(LmsUrlPolicy.isAllowed("https://user@lms.dima.ac.kr/login"))
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
    fun onlyTheObservedPortalBridgeMayUseCleartextDuringInteractiveLogin() {
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
    }

    @Test
    fun rfc5987KoreanAttachmentNameIsDecodedAndPathCharactersAreRemoved() {
        val header = "attachment; filename*=UTF-8''%EC%88%98%EC%97%85%2F%EC%95%88%EB%82%B4.pdf"

        assertEquals("수업_안내.pdf", LmsAttachmentNaming.fromContentDisposition(header, "첨부파일"))
    }
}
