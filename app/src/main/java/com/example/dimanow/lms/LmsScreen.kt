package com.example.dimanow.lms

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

internal data class LmsPresentedDetail(
    val detail: LmsItemDetail,
    val cached: Boolean,
    val attachmentsChanged: Boolean,
)

internal data class LmsOfficialCoursePage(
    val item: LmsItem,
    val course: LmsCourse,
)

internal data class LmsPendingDocument(
    val cache: File,
    val expectedBytes: Long,
)

@Composable
fun LmsRoute(
    credentialStore: LmsCredentialStore,
    sessionController: LmsSessionController,
    loginBridge: LmsLoginBridge,
    renderedPageBridge: LmsRenderedPageBridge? = null,
    autoLoginCoordinator: LmsAutoLoginCoordinator,
    source: LmsSource,
    now: Instant,
    modifier: Modifier = Modifier,
    onFullScreenChange: (Boolean) -> Unit = {},
    authenticationContent: @Composable (LmsLoginRequest, (List<LmsCourse>) -> Unit) -> Unit = { request, authenticated ->
        LmsAuthenticationWebView(request, { loginBridge.complete(request, it) }, authenticated, loginBridge::cancel, silent = request.credentials.automaticLogin)
    },
) {
    val scope = rememberCoroutineScope()
    val sessionState by sessionController.state.collectAsStateWithLifecycle()
    val credentialState by credentialStore.state.collectAsStateWithLifecycle()
    val loginError by autoLoginCoordinator.errorMessage.collectAsStateWithLifecycle()
    val snapshot by source.snapshot.collectAsStateWithLifecycle(initialValue = LmsSnapshot())
    val loginRequest by loginBridge.request.collectAsStateWithLifecycle()
    val renderedPageRequest by (
        renderedPageBridge?.request ?: remember { kotlinx.coroutines.flow.flowOf<LmsRenderedPageRequest?>(null) }
        ).collectAsStateWithLifecycle(initialValue = null)
    val snackbar = remember { SnackbarHostState() }
    var selectedCourse by remember { mutableStateOf<String?>(null) }
    var selectedKind by remember { mutableStateOf<LmsItemKind?>(null) }
    var selectedDetail by remember { mutableStateOf<LmsPresentedDetail?>(null) }
    var officialCoursePage by remember { mutableStateOf<LmsOfficialCoursePage?>(null) }
    var pendingOfficialCoursePage by remember { mutableStateOf<LmsOfficialCoursePage?>(null) }
    BackHandler(enabled = selectedDetail != null) { selectedDetail = null }
    BackHandler(enabled = officialCoursePage != null) { officialCoursePage = null }
    BackHandler(enabled = pendingOfficialCoursePage != null) { pendingOfficialCoursePage = null }
    val silentLogin = loginRequest?.credentials?.automaticLogin == true
    BackHandler(enabled = loginRequest != null && !silentLogin) { loginBridge.cancel() }
    BackHandler(enabled = renderedPageRequest != null) { renderedPageBridge?.cancel() }
    // 로그인 WebView·글 상세가 떠 있는 동안 상위 셸이 하단 내비를 숨기게 알린다 (D-044)
    val fullScreen = (loginRequest != null && !silentLogin) || renderedPageRequest != null || selectedDetail != null || officialCoursePage != null
    LaunchedEffect(fullScreen) { onFullScreenChange(fullScreen) }
    DisposableEffect(Unit) {
        onDispose { onFullScreenChange(false) }
    }

    suspend fun loginAndRefresh(force: Boolean, credentialsChanged: Boolean = false) {
        // Explicit retries bypass login cooldowns; the coordinator retains an active session
        // unless the user has just supplied new credentials.
        val state = autoLoginCoordinator.ensureActive(force = force, credentialsChanged = credentialsChanged)
        if (state == LmsSessionState.ACTIVE) {
            when (val result = source.refresh(force = force)) {
                LmsRefreshResult.SessionExpired -> {
                    autoLoginCoordinator.markExpired()
                    if (autoLoginCoordinator.ensureActive(force = true) == LmsSessionState.ACTIVE) source.refresh(force = true)
                }
                LmsRefreshResult.CourseCatalogRequired -> {
                    sessionController.transition(LmsSessionState.EXPIRED)
                    if (autoLoginCoordinator.ensureActive(force = true) == LmsSessionState.ACTIVE) {
                        when (val retried = source.refresh(force = true)) {
                            is LmsRefreshResult.Failure -> snackbar.showSnackbar(LmsUserMessages.REFRESH_FAILED)
                            LmsRefreshResult.CourseCatalogRequired -> snackbar.showSnackbar(LmsUserMessages.COURSE_CATALOG_FAILED)
                            else -> Unit
                        }
                    }
                }
                is LmsRefreshResult.Failure -> snackbar.showSnackbar(LmsUserMessages.REFRESH_FAILED)
                else -> Unit
            }
        }
    }

    // force 없이 시작하면 5분 캐시 TTL 내 재방문은 네트워크를 건드리지 않는다 (#26)
    LaunchedEffect(Unit) {
        if (credentialState == CredentialState.SAVED) {
            when (source.refresh(force = false)) {
                LmsRefreshResult.SessionExpired -> loginAndRefresh(force = false)
                LmsRefreshResult.CourseCatalogRequired -> loginAndRefresh(force = false)
                is LmsRefreshResult.Failure -> Unit
                else -> sessionController.transition(LmsSessionState.ACTIVE)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            loginRequest != null && !silentLogin -> Unit // The authentication host below owns manual progress.
            renderedPageRequest != null && renderedPageBridge != null -> LmsRenderedPageWebView(
                request = requireNotNull(renderedPageRequest),
                onComplete = renderedPageBridge::complete,
                onCancel = renderedPageBridge::cancel,
                modifier = Modifier.fillMaxSize(),
            )
            officialCoursePage != null -> LmsOfficialCourseWebView(
                page = requireNotNull(officialCoursePage),
                onBack = { officialCoursePage = null },
                onLaunched = {
                    officialCoursePage?.item?.let { item ->
                        scope.launch { source.markItemOpened(item) }
                    }
                },
                onSessionExpired = {
                    officialCoursePage = null
                    scope.launch {
                        autoLoginCoordinator.markExpired()
                        snackbar.showSnackbar(LmsUserMessages.SIGN_IN_REQUIRED)
                    }
                },
                onMessage = { scope.launch { snackbar.showSnackbar(it) } },
                modifier = Modifier.fillMaxSize(),
            )
            selectedDetail != null -> LmsDetailScreen(
                presented = requireNotNull(selectedDetail),
                source = source,
                sessionController = sessionController,
                autoLoginCoordinator = autoLoginCoordinator,
                onBack = { selectedDetail = null },
                onMessage = { scope.launch { snackbar.showSnackbar(it) } },
                modifier = Modifier.fillMaxSize(),
            )
            sessionState != LmsSessionState.ACTIVE && (
                credentialState == CredentialState.EMPTY || credentialState == CredentialState.INVALIDATED ||
                    sessionState == LmsSessionState.CREDENTIALS_NEED_REVIEW
                ) -> LmsLoginScreen(
                    needsReview = sessionState == LmsSessionState.CREDENTIALS_NEED_REVIEW || credentialState == CredentialState.INVALIDATED,
                    onLogin = { username, password, automatic ->
                        scope.launch {
                            val credentials = SavedLmsCredentials(username.trim(), password, automatic)
                            if (automatic) {
                                credentialStore.save(credentials)
                                loginAndRefresh(force = true, credentialsChanged = true)
                            } else {
                                credentialStore.delete()
                                sessionController.transition(LmsSessionState.AUTHENTICATING)
                                val result = loginBridge.authenticate(credentials)
                                val next = when (result) {
                                    LmsLoginResult.Success -> LmsSessionState.ACTIVE
                                    LmsLoginResult.CredentialsRejected -> LmsSessionState.CREDENTIALS_NEED_REVIEW
                                    LmsLoginResult.InteractiveAuthenticationRequired -> LmsSessionState.INTERACTIVE_AUTH_REQUIRED
                                    else -> LmsSessionState.ERROR
                                }
                                sessionController.transition(next)
                                if (next == LmsSessionState.ACTIVE) source.refresh(force = true)
                                else result.failureMessage()?.let { snackbar.showSnackbar(it) }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            else -> LmsItemsScreen(
                snapshot = if (silentLogin) snapshot.copy(syncState = LmsSyncState.SYNCING, errorMessage = null) else snapshot,
                sessionState = sessionState,
                loginErrorMessage = loginError.takeUnless { sessionState == LmsSessionState.ACTIVE || silentLogin },
                selectedCourse = selectedCourse,
                selectedKind = selectedKind,
                onCourseChange = { selectedCourse = it },
                onKindChange = { selectedKind = it },
                onRefresh = { scope.launch { loginAndRefresh(force = true) } },
                onOpenItem = { item ->
                    scope.launch {
                        suspend fun present(result: LmsDetailLoadResult): Boolean = when (result) {
                            is LmsDetailLoadResult.Fresh -> {
                                selectedDetail = LmsPresentedDetail(
                                    result.detail,
                                    cached = false,
                                    attachmentsChanged = result.attachmentsChanged,
                                )
                                true
                            }
                            is LmsDetailLoadResult.Cached -> {
                                selectedDetail = LmsPresentedDetail(result.detail, cached = true, attachmentsChanged = false)
                                true
                            }
                            LmsDetailLoadResult.OfficialCoursePage -> {
                                val course = snapshot.courses.firstOrNull { it.id == item.courseId }
                                if (course == null) {
                                    snackbar.showSnackbar("공식 LMS에서 이 수업을 찾지 못했어요")
                                    false
                                } else if (item.kind == LmsItemKind.CONTENT) {
                                    pendingOfficialCoursePage = LmsOfficialCoursePage(item, course)
                                    true
                                } else {
                                    officialCoursePage = LmsOfficialCoursePage(item, course)
                                    true
                                }
                            }
                            is LmsDetailLoadResult.Failure -> {
                                snackbar.showSnackbar(result.message)
                                false
                            }
                            LmsDetailLoadResult.SessionExpired -> false
                        }
                        val first = source.loadDetail(item)
                        if (!present(first) && first == LmsDetailLoadResult.SessionExpired) {
                            autoLoginCoordinator.markExpired()
                            if (autoLoginCoordinator.ensureActive(force = true) == LmsSessionState.ACTIVE) {
                                val retried = source.loadDetail(item)
                                if (!present(retried)) {
                                    snackbar.showSnackbar(
                                        if (retried == LmsDetailLoadResult.SessionExpired) LmsUserMessages.SIGN_IN_REQUIRED else LmsUserMessages.DETAIL_FAILED,
                                    )
                                }
                            }
                        }
                    }
                },
                now = now,
                modifier = Modifier.fillMaxSize(),
            )
        }
        loginRequest?.let { request ->
            key(request) {
                authenticationContent(request) { courses ->
                    scope.launch {
                        if (!request.result.isCompleted) {
                            runCatching { source.storeRenderedCourses(courses) }
                                .onSuccess { loginBridge.complete(request, LmsLoginResult.Success) }
                                .onFailure { loginBridge.complete(request, LmsLoginResult.Failure("수업 목록을 저장하지 못했어요. 다시 시도해 주세요")) }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        pendingOfficialCoursePage?.let { pendingPage ->
            AlertDialog(
                onDismissRequest = { pendingOfficialCoursePage = null },
                title = { Text("학습을 시작할까요?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingOfficialCoursePage = null
                            officialCoursePage = pendingPage
                        },
                    ) { Text("학습 시작") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingOfficialCoursePage = null }) { Text("취소") }
                },
            )
        }
    }
}

internal val SEOUL = ZoneId.of("Asia/Seoul")
internal val LMS_TIME = DateTimeFormatter.ofPattern("M월 d일 HH:mm")
internal val LMS_DETAIL_TIME = DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH:mm")
internal const val OFFICIAL_LMS_LOGIN_URL =
    "https://portal.dima.ac.kr/?r=https://lms.dima.ac.kr/sso/index.jsp"
internal const val LOGIN_URL = OFFICIAL_LMS_LOGIN_URL
internal const val LMS_DASHBOARD_URL =
    "https://lms.dima.ac.kr/lms/myLecture/doListView.dunet?to_do_type=all"
internal const val LMS_NATIVE_DETAIL_DASHBOARD_URL =
    "https://lms.dima.ac.kr/lms/myLecture/doListView.dunet?mnid=201008840728"
internal const val LMS_COURSE_SCHEDULE_PATH = "/lms/class/courseSchedule/doListView.dunet"
internal const val LMS_COURSE_SCHEDULE_URL =
    "https://lms.dima.ac.kr/lms/class/courseSchedule/doListView.dunet"
internal const val LMS_LEARNING_WINDOW_PATH = "/lms/class/courseSchedule/doLearningWindow2.dunet"
internal const val LMS_LEARNING_SELECTION_ATTEMPTS = 20
internal const val LMS_LEARNING_SELECTION_RETRY_MILLIS = 250L
internal const val LMS_LEARNING_LAUNCH_TIMEOUT_MILLIS = 8_000L
