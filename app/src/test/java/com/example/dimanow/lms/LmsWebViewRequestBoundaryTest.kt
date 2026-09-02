package com.example.dimanow.lms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LmsWebViewRequestBoundaryTest {
    @Test
    fun officialScreensBlockEveryExternalHttpResourceButKeepLocalDocumentSchemes() {
        assertFalse(
            shouldBlockLmsWebResource(
                "https://lms.dima.ac.kr/assets/course.css",
                loginFlow = false,
            ),
        )
        assertFalse(
            shouldBlockLmsWebResource(
                "data:image/png;base64,fixture",
                loginFlow = false,
            ),
        )
        assertTrue(
            shouldBlockLmsWebResource(
                "https://tracker.example/collect.js",
                loginFlow = false,
            ),
        )
        assertTrue(
            shouldBlockLmsWebResource(
                "http://lms.dima.ac.kr/assets/course.css",
                loginFlow = false,
            ),
        )
    }

    @Test
    fun loginScreenAllowsOnlyTheExistingNarrowSsoBridgeException() {
        assertFalse(
            shouldBlockLmsWebResource(
                "https://portal.dima.ac.kr/assets/login.js",
                loginFlow = true,
            ),
        )
        assertFalse(
            shouldBlockLmsWebResource(
                "http://sso.dima.ac.kr:8080/sso/pmi-sso.jsp?ticket=fixture",
                loginFlow = true,
            ),
        )
        assertTrue(
            shouldBlockLmsWebResource(
                "http://sso.dima.ac.kr:8080/assets/unverified.js",
                loginFlow = true,
            ),
        )
        assertTrue(
            shouldBlockLmsWebResource(
                "https://cdn.example/portal.js",
                loginFlow = true,
            ),
        )
    }
}
