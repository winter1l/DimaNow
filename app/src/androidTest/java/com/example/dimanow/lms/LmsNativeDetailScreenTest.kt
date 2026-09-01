package com.example.dimanow.lms

import android.os.ParcelFileDescriptor
import android.text.InputType
import android.webkit.WebView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.allOf
import com.example.dimanow.theme.DIMANowTheme
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LmsNativeDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun openingAnItemShowsNativeContentAndDownloadWithoutAWebView() {
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
            item = item.copy(isRead = true, changeState = LmsChangeState.NONE),
            sanitizedHtml = "<h2>준비물</h2><p>카메라와 배터리</p>",
            attachments = listOf(
                LmsAttachment(
                    id = "attachment-1",
                    fileName = "실습 안내.pdf",
                    downloadUrl = "https://lms.dima.ac.kr/files/attachment-1",
                ),
            ),
        )
        val source = DetailLmsSource(item, detail)
        val credentials = EmptyCredentialStore()
        val session = MutableLmsSessionController(LmsSessionState.ACTIVE)
        val loginBridge = LmsLoginBridge()

        composeRule.setContent {
            DIMANowTheme {
                LmsRoute(
                    credentialStore = credentials,
                    sessionController = session,
                    loginBridge = loginBridge,
                    autoLoginCoordinator = LmsAutoLoginCoordinator(credentials, session, loginBridge),
                    source = source,
                    now = Instant.parse("2026-09-01T03:00:00Z"),
                )
            }
        }

        composeRule.onNodeWithText("1주차 안내").performClick()
        composeRule.onNodeWithText("준비물", substring = true).assertExists()
        composeRule.onNodeWithText("카메라와 배터리", substring = true).assertExists()
        composeRule.onNodeWithText("실습 안내.pdf").assertExists()
        onView(isAssignableFrom(WebView::class.java)).check(doesNotExist())
    }

    @Test
    fun automaticLoginShowsOnlyNativeProgressInsteadOfThePortalWebView() {
        val credentials = SavedCredentialStore()
        val session = MutableLmsSessionController(LmsSessionState.EXPIRED)
        val loginBridge = LmsLoginBridge()

        composeRule.setContent {
            DIMANowTheme {
                LmsRoute(
                    credentialStore = credentials,
                    sessionController = session,
                    loginBridge = loginBridge,
                    autoLoginCoordinator = LmsAutoLoginCoordinator(credentials, session, loginBridge),
                    source = ExpiringLmsSource(),
                    now = Instant.parse("2026-09-01T03:00:00Z"),
                )
            }
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("공식 포털에서 로그인 중").fetchSemanticsNodes().isNotEmpty()
        }
        onView(allOf(isAssignableFrom(WebView::class.java), isDisplayed())).check(doesNotExist())
    }

    @Test
    fun passwordFieldReportsPasswordInputTypeToTheKeyboard() {
        val credentials = EmptyCredentialStore()
        val session = MutableLmsSessionController(LmsSessionState.SIGNED_OUT)
        val loginBridge = LmsLoginBridge()

        composeRule.setContent {
            DIMANowTheme {
                LmsRoute(
                    credentialStore = credentials,
                    sessionController = session,
                    loginBridge = loginBridge,
                    autoLoginCoordinator = LmsAutoLoginCoordinator(credentials, session, loginBridge),
                    source = ExpiringLmsSource(),
                    now = Instant.parse("2026-09-01T03:00:00Z"),
                )
            }
        }

        composeRule.onNodeWithTag("lms_password").performClick().performTextInput("dummy-secret")
        composeRule.waitUntil(5_000) {
            val inputType = currentEditorInputType() ?: return@waitUntil false
            inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT
        }
        val inputType = requireNotNull(currentEditorInputType())

        assertEquals(InputType.TYPE_CLASS_TEXT, inputType and InputType.TYPE_MASK_CLASS)
        assertEquals(InputType.TYPE_TEXT_VARIATION_PASSWORD, inputType and InputType.TYPE_MASK_VARIATION)
    }
}

private fun currentEditorInputType(): Int? {
    val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
        .executeShellCommand("dumpsys input_method")
    val dump = ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    return Regex(
        """curEditorInfo:\s+inputType=0x([0-9a-fA-F]+)[\s\S]*?packageName=com\.example\.dimanow""",
    ).find(dump)?.groupValues?.get(1)?.toInt(16)
}

private class EmptyCredentialStore : LmsCredentialStore {
    override val state: StateFlow<CredentialState> = MutableStateFlow(CredentialState.EMPTY)
    override suspend fun save(credentials: SavedLmsCredentials) = Unit
    override suspend fun load(): SavedLmsCredentials? = null
    override suspend fun delete() = Unit
}

private class SavedCredentialStore : LmsCredentialStore {
    override val state: StateFlow<CredentialState> = MutableStateFlow(CredentialState.SAVED)
    override suspend fun save(credentials: SavedLmsCredentials) = Unit
    override suspend fun load(): SavedLmsCredentials = SavedLmsCredentials("student", "password")
    override suspend fun delete() = Unit
}

private class DetailLmsSource(item: LmsItem, private val detail: LmsItemDetail) : LmsSource {
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
    ): LmsRefreshResult = LmsRefreshResult.Success
    override suspend fun clearPrivateData() = Unit
    override suspend fun storeRenderedCourses(courses: List<LmsCourse>) = Unit
}

private class ExpiringLmsSource : LmsSource {
    override val snapshot: Flow<LmsSnapshot> = MutableStateFlow(LmsSnapshot())
    override suspend fun refresh(force: Boolean): LmsRefreshResult = LmsRefreshResult.SessionExpired
    override suspend fun loadDetail(item: LmsItem): LmsDetailLoadResult = LmsDetailLoadResult.SessionExpired
    override suspend fun downloadAttachment(
        attachment: LmsAttachment,
        destination: File,
        onProgress: (Long, Long?) -> Unit,
    ): LmsRefreshResult = LmsRefreshResult.SessionExpired
    override suspend fun clearPrivateData() = Unit
    override suspend fun storeRenderedCourses(courses: List<LmsCourse>) = Unit
}
