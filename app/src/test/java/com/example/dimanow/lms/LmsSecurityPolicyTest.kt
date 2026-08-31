package com.example.dimanow.lms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun rfc5987KoreanAttachmentNameIsDecodedAndPathCharactersAreRemoved() {
        val header = "attachment; filename*=UTF-8''%EC%88%98%EC%97%85%2F%EC%95%88%EB%82%B4.pdf"

        assertEquals("수업_안내.pdf", LmsAttachmentNaming.fromContentDisposition(header, "첨부파일"))
    }
}
