package com.example.dimanow.notice

import androidx.test.platform.app.InstrumentationRegistry
import com.example.dimanow.DimaNowApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 실제 공식 공지 페이지를 내려받는 네트워크 프로브. 평상시 러너에서는 건너뛰고
 * `-e runNoticeNetworkProbe true`를 명시한 실행에서만 동작한다 (Samsung 프로브 패턴).
 */
class NoticeSourceLiveProbeTest {
    @Test
    fun officialNoticeBoardYieldsAtLeastThreeCachedNotices() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("runNoticeNetworkProbe") == "true")

        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as DimaNowApplication

        runBlocking {
            val result = app.noticeSource.refresh()
            assertTrue("refresh failed: $result", result is NoticeRefreshResult.Success)
            val data = app.noticeSource.data.first()
            assertTrue("expected >=3 notices, got ${data.notices.size}", data.notices.size >= 3)
        }
    }
}
