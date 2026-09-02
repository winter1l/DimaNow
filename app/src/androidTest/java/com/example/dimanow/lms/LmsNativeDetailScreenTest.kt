package com.example.dimanow.lms

import android.os.ParcelFileDescriptor
import android.text.InputType
import android.webkit.WebView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.assertion.ViewAssertions.matches
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
        loginBridge.cancel()
        composeRule.waitUntil(5_000) { loginBridge.request.value == null }
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
        closeSoftKeyboard()
    }

    @Test
    fun videoContentRequiresConfirmationAndCancelDoesNotOpenThePlayer() {
        val item = LmsItem(
            id = "sound-video-1",
            courseId = "sound",
            courseName = "음향기초실습",
            kind = LmsItemKind.CONTENT,
            title = "2주차 음향 실습 영상",
            detailUrl = "https://lms.dima.ac.kr/lms/class/courseSchedule/doListView.dunet",
        )
        val source = OfficialCoursePageLmsSource(item)
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

        composeRule.onNodeWithText("전체").performClick()
        composeRule.onNodeWithText("2주차 음향 실습 영상").performClick()
        composeRule.onNodeWithText("학습 시작을 하실건가요?").assertExists()
        composeRule.onNodeWithText("취소").performClick()
        composeRule.onNodeWithText("학습 시작을 하실건가요?").assertDoesNotExist()
        onView(isAssignableFrom(WebView::class.java)).check(doesNotExist())
        assertEquals(emptyList<String>(), source.openedItemIds)
    }

    @Test
    fun unsupportedLearningItemOpensTheOfficialScreenWithoutTheVideoConfirmation() {
        val item = LmsItem(
            id = "discussion-1",
            courseId = "sound",
            courseName = "음향기초실습",
            kind = LmsItemKind.DISCUSSION,
            title = "음향 설계 토론",
            detailUrl = "https://lms.dima.ac.kr/lms/class/discuss/stud/doListView.dunet",
        )
        val source = OfficialCoursePageLmsSource(item)
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

        composeRule.onNodeWithText("전체").performClick()
        composeRule.onNodeWithText("음향 설계 토론").performClick()
        composeRule.onNodeWithText("학습 시작을 하실건가요?").assertDoesNotExist()
        composeRule.onNodeWithTag("lms_official_course_screen").assertExists()
        composeRule.onNodeWithContentDescription("뒤로").performClick()
        onView(isAssignableFrom(WebView::class.java)).check(doesNotExist())
    }

    @Test
    fun nativeNoticeSeparatesCourseMetadataAndContent() {
        val item = LmsItem(
            id = "notice-metadata-1",
            courseId = "sound",
            courseName = "음향기초실습",
            kind = LmsItemKind.NOTICE,
            title = "1주차 수업안내",
            detailUrl = "https://lms.dima.ac.kr/lms/class/boardItem/doViewBoardItem.dunet",
        )
        val detail = LmsItemDetail(
            item = item.copy(isRead = true),
            sanitizedHtml = "<p>첫 수업 준비물을 확인하세요.</p>",
            metadata = LmsDetailMetadata(
                author = "담당교수",
                registeredAt = Instant.parse("2026-09-01T00:30:00Z"),
            ),
        )
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
                    source = DetailLmsSource(item, detail),
                    now = Instant.parse("2026-09-01T03:00:00Z"),
                )
            }
        }

        composeRule.onNodeWithText("전체").performClick()
        composeRule.onNodeWithText("1주차 수업안내").performClick()
        composeRule.onNodeWithText("음향기초실습").assertExists()
        composeRule.onNodeWithText("공지").assertExists()
        composeRule.onNodeWithText("작성자").assertExists()
        composeRule.onNodeWithText("담당교수").assertExists()
        composeRule.onNodeWithText("등록일").assertExists()
        composeRule.onNodeWithText("2026년 9월 1일 09:30").assertExists()
        composeRule.onNodeWithText("내용").assertExists()
        composeRule.onNodeWithText("첫 수업 준비물을 확인하세요.", substring = true).assertExists()
    }

    @Test
    fun nativeAssignmentShowsSubmissionPeriodAndScoreWithoutEmptyMetadataOrAWebView() {
        val item = LmsItem(
            id = "assignment-metadata-1",
            courseId = "sound",
            courseName = "음향기초실습",
            kind = LmsItemKind.ASSIGNMENT,
            title = "2주차 과제 · 영상사운드 구성 분석",
            detailUrl = "https://lms.dima.ac.kr/lms/class/report/stud/doFormReport.dunet",
        )
        val detail = LmsItemDetail(
            item = item.copy(isRead = true),
            sanitizedHtml = "<p>작성한 내용을 PDF 파일로 제출하세요.</p>",
            metadata = LmsDetailMetadata(
                submissionStartsAt = Instant.parse("2026-09-01T00:00:00Z"),
                submissionEndsAt = Instant.parse("2026-09-08T06:59:00Z"),
                maxScore = "10점",
            ),
        )
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
                    source = DetailLmsSource(item, detail),
                    now = Instant.parse("2026-09-01T03:00:00Z"),
                )
            }
        }

        composeRule.onNodeWithText("전체").performClick()
        composeRule.onNodeWithText("2주차 과제 · 영상사운드 구성 분석").performClick()
        composeRule.onNodeWithText("음향기초실습").assertExists()
        composeRule.onNodeWithText("과제").assertExists()
        composeRule.onNodeWithText("내용").assertExists()
        composeRule.onNodeWithText("작성한 내용을 PDF 파일로 제출하세요.", substring = true).assertExists()
        composeRule.onNodeWithText("제출기간").assertExists()
        composeRule.onNodeWithText("2026년 9월 1일 09:00 ~ 2026년 9월 8일 15:59").assertExists()
        composeRule.onNodeWithText("만점").assertExists()
        composeRule.onNodeWithText("10점").assertExists()
        composeRule.onNodeWithText("작성자").assertDoesNotExist()
        composeRule.onNodeWithText("등록일").assertDoesNotExist()
        composeRule.onNodeWithText("첨부파일").assertDoesNotExist()
        onView(isAssignableFrom(WebView::class.java)).check(doesNotExist())
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
    ): LmsAttachmentDownloadResult = LmsAttachmentDownloadResult.Failure("unused")
    override suspend fun clearPrivateData() = Unit
    override suspend fun storeRenderedCourses(courses: List<LmsCourse>) = Unit
}

private class OfficialCoursePageLmsSource(private val item: LmsItem) : LmsSource {
    val openedItemIds = mutableListOf<String>()

    override val snapshot: Flow<LmsSnapshot> = MutableStateFlow(
        LmsSnapshot(
            courses = listOf(LmsCourse(item.courseId, item.courseName, classNo = "01")),
            items = listOf(item),
            syncState = LmsSyncState.READY,
        ),
    )

    override suspend fun refresh(force: Boolean): LmsRefreshResult = LmsRefreshResult.Cached
    override suspend fun loadDetail(item: LmsItem): LmsDetailLoadResult = LmsDetailLoadResult.OfficialCoursePage
    override suspend fun markItemOpened(item: LmsItem) {
        openedItemIds += item.id
    }
    override suspend fun downloadAttachment(
        attachment: LmsAttachment,
        destination: File,
        onProgress: (Long, Long?) -> Unit,
    ): LmsAttachmentDownloadResult = LmsAttachmentDownloadResult.Failure("unused")
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
    ): LmsAttachmentDownloadResult = LmsAttachmentDownloadResult.SessionExpired
    override suspend fun clearPrivateData() = Unit
    override suspend fun storeRenderedCourses(courses: List<LmsCourse>) = Unit
}
