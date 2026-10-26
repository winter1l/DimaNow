package com.example.dimanow.lms

import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.ButtonDefaults
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.dimanow.ui.EmptyState
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
import kotlinx.coroutines.flow.first
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
    /**
     * D-131 (Q-025): list and detail side by side on a wide window ([lmsTwoPaneWindow]); an opened item shows in the
     * detail pane beside the list instead of taking the whole screen, and Back clears the selection.
     */
    twoPane: Boolean = lmsTwoPaneWindow(),
    authenticationContent: @Composable (LmsLoginRequest, (List<LmsCourse>, LmsLoginResult) -> Unit) -> Unit = { request, authenticated ->
        LmsAuthenticationWebView(request, { loginBridge.complete(request, it) }, authenticated, loginBridge::cancel, silent = request.credentials.automaticLogin)
    },
) {
    val scope = rememberCoroutineScope()
    val sessionState by sessionController.state.collectAsStateWithLifecycle()
    val credentialState by credentialStore.state.collectAsStateWithLifecycle()
    val loginError by autoLoginCoordinator.errorMessage.collectAsStateWithLifecycle()
    // D-135: the LMS ended this device's session within two hours of its last use, so another device most likely
    // signed in; the list waits for the student's 이 기기로 로그인 instead of signing that device out.
    val signedInElsewhere by autoLoginCoordinator.signedInElsewhere.collectAsStateWithLifecycle()
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
    // 로그인 WebView·글 상세가 떠 있는 동안 상위 셸이 하단 내비를 숨기게 알린다 (D-044).
    // D-131: on a wide window the detail is a pane beside the list, so the rail stays.
    val fullScreen = (loginRequest != null && !silentLogin) || renderedPageRequest != null ||
        (selectedDetail != null && !twoPane) || officialCoursePage != null
    LaunchedEffect(fullScreen) { onFullScreenChange(fullScreen) }
    DisposableEffect(Unit) {
        onDispose { onFullScreenChange(false) }
    }

    suspend fun manualLogin(credentials: SavedLmsCredentials) {
        sessionController.transition(LmsSessionState.AUTHENTICATING)
        val result = loginBridge.authenticate(credentials)
        val next = when (result) {
            LmsLoginResult.Success, LmsLoginResult.SuccessEndingElsewhere -> LmsSessionState.ACTIVE
            LmsLoginResult.CredentialsRejected -> LmsSessionState.CREDENTIALS_NEED_REVIEW
            LmsLoginResult.InteractiveAuthenticationRequired -> LmsSessionState.INTERACTIVE_AUTH_REQUIRED
            else -> LmsSessionState.ERROR
        }
        sessionController.transition(next)
        if (next == LmsSessionState.ACTIVE) {
            autoLoginCoordinator.allowSignInHere()
            source.refresh(force = true)
            if (result == LmsLoginResult.SuccessEndingElsewhere) snackbar.showSnackbar(LmsUserMessages.SIGNED_OUT_ELSEWHERE)
        } else {
            result.failureMessage()?.let { snackbar.showSnackbar(it) }
        }
    }

    /** Returns true when the list was read from the LMS (or its fresh cache) in the end. */
    suspend fun loginAndRefresh(force: Boolean, credentialsChanged: Boolean = false, signInHere: Boolean = false): Boolean {
        // Explicit retries bypass login cooldowns; the coordinator retains an active session
        // unless the user has just supplied new credentials.
        val state = autoLoginCoordinator.ensureActive(force = force, credentialsChanged = credentialsChanged, signInHere = signInHere)
        if (state != LmsSessionState.ACTIVE) return false
        val final = when (val result = source.refresh(force = force)) {
            LmsRefreshResult.SessionExpired -> {
                autoLoginCoordinator.markExpired()
                // The student's 이 기기로 로그인 still signs in when the read finds the session ended (D-135).
                if (autoLoginCoordinator.ensureActive(force = true, signInHere = signInHere) == LmsSessionState.ACTIVE) {
                    source.refresh(force = true)
                } else {
                    result
                }
            }
            LmsRefreshResult.CourseCatalogRequired -> {
                sessionController.transition(LmsSessionState.EXPIRED)
                if (autoLoginCoordinator.ensureActive(force = true) == LmsSessionState.ACTIVE) {
                    source.refresh(force = true).also { retried ->
                        if (retried == LmsRefreshResult.CourseCatalogRequired) snackbar.showSnackbar(LmsUserMessages.COURSE_CATALOG_FAILED)
                    }
                } else {
                    result
                }
            }
            else -> result
        }
        if (final is LmsRefreshResult.Failure) snackbar.showSnackbar(LmsUserMessages.REFRESH_FAILED)
        if (final == LmsRefreshResult.Success) autoLoginCoordinator.noteActivity()
        return final == LmsRefreshResult.Success || final == LmsRefreshResult.Cached
    }

    // force 없이 시작하면 5분 캐시 TTL 내 재방문은 네트워크를 건드리지 않는다 (#26)
    LaunchedEffect(Unit) {
        if (credentialState == CredentialState.SAVED) {
            when (source.refresh(force = false)) {
                LmsRefreshResult.SessionExpired -> {
                    autoLoginCoordinator.markExpired()
                    loginAndRefresh(force = false)
                }
                LmsRefreshResult.CourseCatalogRequired -> loginAndRefresh(force = false)
                is LmsRefreshResult.Failure -> Unit
                // A fresh cache says nothing about the session; while it was most likely taken elsewhere (D-135) the
                // card keeps waiting for 이 기기로 로그인.
                LmsRefreshResult.Cached -> if (!autoLoginCoordinator.signedInElsewhere.value) {
                    sessionController.transition(LmsSessionState.ACTIVE)
                }
                else -> {
                    autoLoginCoordinator.noteActivity()
                    sessionController.transition(LmsSessionState.ACTIVE)
                }
            }
        }
    }

    // D-135: a sign-in that ended the one elsewhere says so.
    LaunchedEffect(autoLoginCoordinator) {
        autoLoginCoordinator.notices.collect { snackbar.showSnackbar(it) }
    }

    fun openItem(item: LmsItem) {
        scope.launch {
            suspend fun present(result: LmsDetailLoadResult): Boolean = when (result) {
                is LmsDetailLoadResult.Fresh -> {
                    autoLoginCoordinator.noteActivity()
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
                } else if (autoLoginCoordinator.signedInElsewhere.value) {
                    snackbar.showSnackbar(LmsUserMessages.SIGNED_IN_ELSEWHERE_HINT)
                }
            }
        }
    }

    val needsSignIn = sessionState != LmsSessionState.ACTIVE && (
        credentialState == CredentialState.EMPTY || credentialState == CredentialState.INVALIDATED ||
            sessionState == LmsSessionState.CREDENTIALS_NEED_REVIEW
        )

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
                        snackbar.showSnackbar(
                            if (autoLoginCoordinator.signedInElsewhere.value) {
                                LmsUserMessages.SIGNED_IN_ELSEWHERE_HINT
                            } else {
                                LmsUserMessages.SIGN_IN_REQUIRED
                            },
                        )
                    }
                },
                onMessage = { scope.launch { snackbar.showSnackbar(it) } },
                modifier = Modifier.fillMaxSize(),
            )
            selectedDetail != null && !twoPane -> LmsDetailScreen(
                presented = requireNotNull(selectedDetail),
                source = source,
                sessionController = sessionController,
                autoLoginCoordinator = autoLoginCoordinator,
                onBack = { selectedDetail = null },
                onMessage = { scope.launch { snackbar.showSnackbar(it) } },
                modifier = Modifier.fillMaxSize(),
            )
            // An item already open beside the list stays there; otherwise signing in comes first, as on phones.
            needsSignIn && !(twoPane && selectedDetail != null) -> LmsLoginScreen(
                    needsReview = sessionState == LmsSessionState.CREDENTIALS_NEED_REVIEW || credentialState == CredentialState.INVALIDATED,
                    onLogin = { username, password, automatic ->
                        scope.launch {
                            val credentials = SavedLmsCredentials(username.trim(), password, automatic)
                            if (automatic) {
                                credentialStore.save(credentials)
                                loginAndRefresh(force = true, credentialsChanged = true)
                            } else {
                                credentialStore.delete()
                                manualLogin(credentials)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            // D-131 (Q-025): the list keeps one place in the composition whether or not a detail pane sits beside it,
            // so opening an item on a wide window keeps the list's mode, folds and scroll position.
            else -> Row(Modifier.fillMaxSize()) {
                LmsItemsScreen(
                    snapshot = if (silentLogin) snapshot.copy(syncState = LmsSyncState.SYNCING, errorMessage = null) else snapshot,
                    sessionState = sessionState,
                    loginErrorMessage = loginError.takeUnless { sessionState == LmsSessionState.ACTIVE || silentLogin },
                    signedInElsewhere = signedInElsewhere && sessionState != LmsSessionState.ACTIVE,
                    onSignInHere = { scope.launch { loginAndRefresh(force = true, signInHere = true) } },
                    selectedCourse = selectedCourse,
                    selectedKind = selectedKind,
                    onCourseChange = { selectedCourse = it },
                    onKindChange = { selectedKind = it },
                    onRefresh = {
                        scope.launch {
                            // 셔틀·식단처럼 직접 당긴 새로고침은 바뀐 것이 없을 때도 알린다
                            val before = source.snapshot.first().items
                            if (loginAndRefresh(force = true)) {
                                snackbar.showSnackbar(lmsRefreshMessage(before, source.snapshot.first().items))
                            } else if (autoLoginCoordinator.signedInElsewhere.value) {
                                snackbar.showSnackbar(LmsUserMessages.SIGNED_IN_ELSEWHERE_HINT)
                            }
                        }
                    },
                    onOpenItem = ::openItem,
                    now = now,
                    selectable = twoPane,
                    selection = selectedDetail?.detail?.item?.takeIf { twoPane },
                    modifier = if (twoPane) {
                        Modifier
                            .width(LMS_LIST_PANE_WIDTH)
                            .fillMaxHeight()
                            // The list pane does not reach the window's end edge; the detail pane takes that inset.
                            .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.End))
                            .testTag("lms_list_pane")
                    } else {
                        Modifier.fillMaxSize()
                    },
                )
                if (twoPane) {
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LmsDetailPane(
                        presented = selectedDetail,
                        source = source,
                        sessionController = sessionController,
                        autoLoginCoordinator = autoLoginCoordinator,
                        onClose = { selectedDetail = null },
                        onMessage = { scope.launch { snackbar.showSnackbar(it) } },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)),
                    )
                }
            }
        }
        loginRequest?.let { request ->
            key(request) {
                authenticationContent(request) { courses, result ->
                    scope.launch {
                        if (!request.result.isCompleted) {
                            runCatching { source.storeRenderedCourses(courses) }
                                .onSuccess { loginBridge.complete(request, result) }
                                .onFailure { loginBridge.complete(request, LmsLoginResult.Failure("수업 목록을 저장하지 못했어요. 다시 시도해 주세요")) }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        pendingOfficialCoursePage?.let { pendingPage ->
            AlertDialog(
                icon = { com.example.dimanow.ui.DimaDialogIcon(Icons.Default.PlayCircle, com.example.dimanow.ui.DialogTone.INFO) },
                onDismissRequest = { pendingOfficialCoursePage = null },
                title = { Text("학습을 시작할까요?") },
                text = { Text("공식 LMS 학습 화면이 열리고, 열면 학습 기록이 남을 수 있어요.") },
                confirmButton = {
                    TextButton(shapes = ButtonDefaults.shapes(), 
                        onClick = {
                            pendingOfficialCoursePage = null
                            officialCoursePage = pendingPage
                        },
                    ) { Text("학습 시작") }
                },
                dismissButton = {
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = { pendingOfficialCoursePage = null }) { Text("취소") }
                },
            )
        }
    }
}

/**
 * The detail pane beside the 수업 list on a wide window (D-131, Q-025): the opened item's native detail, or a quiet
 * line saying where it will show while nothing is open. Each item gets its own detail state, so moving to another
 * item starts at its top with no download of the previous one still shown.
 */
@Composable
private fun LmsDetailPane(
    presented: LmsPresentedDetail?,
    source: LmsSource,
    sessionController: LmsSessionController,
    autoLoginCoordinator: LmsAutoLoginCoordinator,
    onClose: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.testTag("lms_detail_pane")) {
        if (presented == null) {
            EmptyState(
                message = "학습을 고르면 여기에 내용이 보여요",
                icon = Icons.AutoMirrored.Filled.Article,
                contained = false,
                modifier = Modifier
                    .align(Alignment.Center)
                    .statusBarsPadding()
                    .padding(24.dp)
                    .testTag("lms_detail_placeholder"),
            )
        } else {
            val item = presented.detail.item
            key(item.kind, item.courseId, item.id) {
                LmsDetailScreen(
                    presented = presented,
                    source = source,
                    sessionController = sessionController,
                    autoLoginCoordinator = autoLoginCoordinator,
                    onBack = onClose,
                    onMessage = onMessage,
                    inPane = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * D-131 (Q-025): whether the window is wide enough for the 수업 list and an item's detail side by side. It reads the
 * window width as the shell's rail choice does (D-104), and uses the M3 expanded width class ([lmsUsesTwoPane]).
 */
@Composable
internal fun lmsTwoPaneWindow(): Boolean {
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    return lmsUsesTwoPane(windowWidth)
}

/**
 * Pure choice of the list-detail layout (D-131): from the M3 expanded width, 840dp. Between the rail's 600dp
 * (D-104) and 840dp the rail already takes part of the width, and the detail beside a phone-wide list would be
 * narrower than a phone.
 */
internal fun lmsUsesTwoPane(windowWidth: Dp): Boolean = windowWidth >= LMS_TWO_PANE_MIN_WIDTH

/** The M3 expanded window width class, where list-detail layouts show both panes (D-131). */
internal val LMS_TWO_PANE_MIN_WIDTH = 840.dp

/** The list pane's width beside the detail (D-131): the M3 list-detail default, about a phone's width. */
private val LMS_LIST_PANE_WIDTH = 360.dp

internal val SEOUL = ZoneId.of("Asia/Seoul")
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
