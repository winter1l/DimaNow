package com.example.dimanow.lms

import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import com.example.dimanow.theme.DIMANowTheme
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** D-131 (Q-025): on a wide window the 수업 tab shows the list and the opened item side by side. */
class LmsTwoPaneTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aWideWindowOpensAnItemBesideTheListAndBackClearsIt() {
        val item = LmsItem(
            id = "notice-1",
            courseId = "camera",
            courseName = "카메라기초및실습",
            kind = LmsItemKind.NOTICE,
            title = "1주차 안내",
            detailUrl = "https://lms.dima.ac.kr/item/notice-1",
            changeState = LmsChangeState.NEW,
        )
        val detail = LmsItemDetail(
            item = item.copy(isRead = true),
            sanitizedHtml = "<p>카메라와 배터리를 챙겨 오세요.</p>",
        )
        val credentials = TwoPaneCredentialStore()
        val session = MutableLmsSessionController(LmsSessionState.ACTIVE)
        val loginBridge = LmsLoginBridge()
        val fullScreenStates = mutableListOf<Boolean>()

        composeRule.setContent {
            DIMANowTheme {
                // The window's own width decides the layout, as the shell's rail does (D-104), so the test sets it.
                DeviceConfigurationOverride(DeviceConfigurationOverride.WindowSize(DpSize(1000.dp, 720.dp))) {
                    LmsRoute(
                        credentialStore = credentials,
                        sessionController = session,
                        loginBridge = loginBridge,
                        autoLoginCoordinator = LmsAutoLoginCoordinator(credentials, session, loginBridge),
                        source = TwoPaneLmsSource(item, detail),
                        now = Instant.parse("2026-09-01T03:00:00Z"),
                        onFullScreenChange = { fullScreenStates += it },
                    )
                }
            }
        }

        // Before anything is opened: the list and a quiet placeholder beside it.
        composeRule.onNodeWithTag("lms_list_pane").assertExists()
        composeRule.onNodeWithTag("lms_detail_pane").assertExists()
        composeRule.onNodeWithTag("lms_detail_placeholder").assertExists()

        composeRule.onNode(hasText("1주차 안내") and hasClickAction()).performClick()

        // Both panes: the list stays with the opened row marked, and the detail shows beside it.
        composeRule.onNodeWithTag("lms_history").assertExists()
        composeRule.onNode(hasText("1주차 안내") and hasClickAction()).assertIsSelected()
        composeRule.onNodeWithTag("lms_native_detail_body").assertExists()
        composeRule.onNodeWithText("카메라와 배터리를 챙겨 오세요.", substring = true).assertExists()
        composeRule.onNodeWithTag("lms_detail_placeholder").assertDoesNotExist()
        // The detail is a pane, not a full screen, so the shell keeps its navigation rail.
        composeRule.runOnIdle { assertFalse(fullScreenStates.any { it }) }

        // Back clears the selection and leaves the list where it was.
        Espresso.pressBack()
        composeRule.onNodeWithTag("lms_detail_placeholder").assertExists()
        composeRule.onNodeWithTag("lms_native_detail_body").assertDoesNotExist()
        composeRule.onNodeWithTag("lms_history").assertExists()
    }
}

private class TwoPaneCredentialStore : LmsCredentialStore {
    override val state: StateFlow<CredentialState> = MutableStateFlow(CredentialState.EMPTY)
    override suspend fun save(credentials: SavedLmsCredentials) = Unit
    override suspend fun load(): SavedLmsCredentials? = null
    override suspend fun delete() = Unit
}

private class TwoPaneLmsSource(item: LmsItem, private val detail: LmsItemDetail) : LmsSource {
    override val snapshot: Flow<LmsSnapshot> = MutableStateFlow(
        LmsSnapshot(
            courses = listOf(LmsCourse(item.courseId, item.courseName)),
            items = listOf(item),
            syncState = LmsSyncState.READY,
        ),
    )

    override suspend fun refresh(force: Boolean): LmsRefreshResult = LmsRefreshResult.Cached
    override suspend fun loadDetail(item: LmsItem): LmsDetailLoadResult = LmsDetailLoadResult.Fresh(detail, false)
    override suspend fun downloadAttachment(
        attachment: LmsAttachment,
        destination: File,
        onProgress: (Long, Long?) -> Unit,
    ): LmsAttachmentDownloadResult = LmsAttachmentDownloadResult.Failure("unused")
    override suspend fun clearPrivateData() = Unit
    override suspend fun storeRenderedCourses(courses: List<LmsCourse>) = Unit
}
