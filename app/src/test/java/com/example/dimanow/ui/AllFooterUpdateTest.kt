package com.example.dimanow.ui

import com.example.dimanow.update.AppUpdatePhase
import com.example.dimanow.update.AppUpdateRelease
import com.example.dimanow.update.AppUpdateUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class AllFooterUpdateTest {
    private val release = AppUpdateRelease("2.2", "https://github.com/winter1l/DimaNow/releases/tag/v2.2", "https://example.invalid/a.apk", 1L, "0".repeat(64))

    @Test
    fun `an unchecked version offers a check`() {
        assertEquals(
            AllFooterUpdate("버전 2.1", AllFooterUpdate.Action.CHECK, "업데이트 확인"),
            allFooterUpdate(AppUpdateUiState("2.1")),
        )
    }

    @Test
    fun `a waiting update names its version and offers to get it`() {
        val footer = allFooterUpdate(AppUpdateUiState("2.1", AppUpdatePhase.AVAILABLE, latestRelease = release))
        assertEquals("버전 2.1 · 2.2 버전이 나왔어요", footer.text)
        assertEquals(AllFooterUpdate.Action.DOWNLOAD, footer.action)
    }

    @Test
    fun `an up-to-date app says so without a button`() {
        assertEquals(AllFooterUpdate("버전 2.1 · 최신 버전이에요"), allFooterUpdate(AppUpdateUiState("2.1", AppUpdatePhase.UP_TO_DATE)))
    }

    @Test
    fun `a downloaded update asks to install`() {
        val footer = allFooterUpdate(AppUpdateUiState("2.1", AppUpdatePhase.READY_TO_INSTALL, latestRelease = release))
        assertEquals("버전 2.1 · 2.2 버전을 설치할 수 있어요", footer.text)
        assertEquals(AllFooterUpdate.Action.CONTINUE_INSTALL, footer.action)
    }
}
