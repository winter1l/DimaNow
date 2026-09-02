package com.example.dimanow.lms

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.SafeBrowsingResponse
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dimanow.ui.ScreenColumn
import com.example.dimanow.ui.ScreenLazyColumn
import com.example.dimanow.ui.motion.expressiveBounceClick
import com.example.dimanow.ui.motion.pulseBreath
import com.example.dimanow.ui.motion.staggeredEntrance
import java.io.File
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private data class LmsPresentedDetail(
    val detail: LmsItemDetail,
    val cached: Boolean,
    val attachmentsChanged: Boolean,
)

private data class LmsOfficialCoursePage(
    val item: LmsItem,
    val course: LmsCourse,
)

private data class LmsPendingDocument(
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
) {
    val scope = rememberCoroutineScope()
    val sessionState by sessionController.state.collectAsStateWithLifecycle()
    val credentialState by credentialStore.state.collectAsStateWithLifecycle()
    val snapshot by source.snapshot.collectAsStateWithLifecycle(initialValue = LmsSnapshot())
    val loginRequest by loginBridge.request.collectAsStateWithLifecycle()
    val renderedPageRequest by (
        renderedPageBridge?.request ?: remember { kotlinx.coroutines.flow.flowOf<LmsRenderedPageRequest?>(null) }
        ).collectAsStateWithLifecycle(initialValue = null)
    val snackbar = remember { SnackbarHostState() }
    var selectedCourse by remember { mutableStateOf<String?>(null) }
    var selectedKind by remember { mutableStateOf<LmsItemKind?>(null) }
    var selectedRead by remember { mutableStateOf<Boolean?>(null) }
    var selectedDetail by remember { mutableStateOf<LmsPresentedDetail?>(null) }
    var officialCoursePage by remember { mutableStateOf<LmsOfficialCoursePage?>(null) }
    var pendingOfficialCoursePage by remember { mutableStateOf<LmsOfficialCoursePage?>(null) }
    BackHandler(enabled = selectedDetail != null) { selectedDetail = null }
    BackHandler(enabled = officialCoursePage != null) { officialCoursePage = null }
    BackHandler(enabled = pendingOfficialCoursePage != null) { pendingOfficialCoursePage = null }
    BackHandler(enabled = loginRequest != null) { loginBridge.cancel() }
    BackHandler(enabled = renderedPageRequest != null) { renderedPageBridge?.cancel() }
    // 로그인 WebView·글 상세가 떠 있는 동안 상위 셸이 하단 내비를 숨기게 알린다 (D-044)
    val fullScreen = loginRequest != null || renderedPageRequest != null || selectedDetail != null || officialCoursePage != null
    LaunchedEffect(fullScreen) { onFullScreenChange(fullScreen) }
    DisposableEffect(Unit) {
        onDispose { onFullScreenChange(false) }
    }

    suspend fun loginAndRefresh(force: Boolean) {
        // `force` bypasses only the local data TTL. An already-active LMS session must not be
        // logged in again, because the official LMS allows only one concurrent session.
        val state = autoLoginCoordinator.ensureActive(force = false)
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
                            is LmsRefreshResult.Failure -> snackbar.showSnackbar(retried.message)
                            LmsRefreshResult.CourseCatalogRequired -> snackbar.showSnackbar("수업 목록을 확인하지 못했습니다")
                            else -> Unit
                        }
                    }
                }
                is LmsRefreshResult.Failure -> snackbar.showSnackbar(result.message)
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
            loginRequest != null -> LmsAuthenticationWebView(
                request = requireNotNull(loginRequest),
                onComplete = loginBridge::complete,
                onAuthenticated = { courses ->
                    scope.launch {
                        runCatching { source.storeRenderedCourses(courses) }
                            .onSuccess { loginBridge.complete(LmsLoginResult.Success) }
                            .onFailure { loginBridge.complete(LmsLoginResult.Failure("수업 목록을 저장하지 못했습니다")) }
                    }
                },
                onCancel = loginBridge::cancel,
                modifier = Modifier.fillMaxSize(),
            )
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
                        snackbar.showSnackbar("로그인이 필요합니다")
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
                                loginAndRefresh(force = true)
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
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            else -> LmsItemsScreen(
                snapshot = snapshot,
                sessionState = sessionState,
                selectedCourse = selectedCourse,
                selectedKind = selectedKind,
                selectedRead = selectedRead,
                onCourseChange = { selectedCourse = it },
                onKindChange = { selectedKind = it },
                onReadChange = { selectedRead = it },
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
                                    snackbar.showSnackbar("공식 LMS 수업을 찾지 못했습니다")
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
                                        if (retried == LmsDetailLoadResult.SessionExpired) "로그인이 필요합니다" else "글을 불러오지 못했습니다",
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
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        pendingOfficialCoursePage?.let { pendingPage ->
            AlertDialog(
                onDismissRequest = { pendingOfficialCoursePage = null },
                title = { Text("학습 시작을 하실건가요?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingOfficialCoursePage = null
                            officialCoursePage = pendingPage
                        },
                    ) { Text("시작") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingOfficialCoursePage = null }) { Text("취소") }
                },
            )
        }
    }
}

@Composable
private fun LmsOfficialCourseWebView(
    page: LmsOfficialCoursePage,
    onBack: () -> Unit,
    onLaunched: () -> Unit,
    onSessionExpired: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var ready by remember(page) { mutableStateOf(false) }
    Column(
        modifier
            .testTag("lms_official_course_screen")
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
            Text(
                text = page.item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = {
                    WebView(context).apply {
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.setSupportMultipleWindows(false)
                        var submitted = false
                        var learningLaunchState = LmsLearningLaunchState.LOCATING
                        var launchReported = false
                        var launchWatchdogScheduled = false
                        var officialFallbackReported = false
                        var selectionAttempts = 0
                        var learningActionResolutionInFlight = false
                        webChromeClient = object : WebChromeClient() {
                            override fun onJsConfirm(
                                view: WebView,
                                url: String,
                                message: String,
                                result: JsResult,
                            ): Boolean {
                                if (!shouldConfirmOfficialLearningDialog(url, message)) {
                                    return super.onJsConfirm(view, url, message, result)
                                }
                                result.confirm()
                                return true
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                webRequest: WebResourceRequest,
                            ): WebResourceResponse? = if (
                                shouldBlockLmsWebResource(webRequest.url.toString(), loginFlow = false)
                            ) {
                                blockedLmsWebResourceResponse()
                            } else {
                                super.shouldInterceptRequest(view, webRequest)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                webRequest: WebResourceRequest,
                            ): Boolean {
                                if (LmsUrlPolicy.isAllowed(webRequest.url.toString())) return false
                                onMessage("안전하지 않은 페이지가 차단되었습니다")
                                return true
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                val path = Uri.parse(url).path.orEmpty()
                                val isVideo = page.item.kind == LmsItemKind.CONTENT
                                fun reportOpened() {
                                    if (!launchReported) {
                                        launchReported = true
                                        onLaunched()
                                    }
                                }
                                fun reportOfficialFallbackDisplayed() {
                                    val previous = learningLaunchState
                                    learningLaunchState = reduceOfficialLearningLaunch(
                                        previous,
                                        LmsLearningLaunchEvent.OFFICIAL_FALLBACK_DISPLAYED,
                                    )
                                    ready = true
                                    if (didConfirmedLearningBecomeOpened(previous, learningLaunchState)) {
                                        reportOpened()
                                    }
                                }
                                fun showOfficialFallback(message: String) {
                                    val currentPath = Uri.parse(view.url.orEmpty()).path.orEmpty()
                                    if (currentPath == LMS_COURSE_SCHEDULE_PATH) {
                                        reportOfficialFallbackDisplayed()
                                        if (!officialFallbackReported) {
                                            officialFallbackReported = true
                                            onMessage(message)
                                        }
                                    } else {
                                        view.loadUrl(LMS_COURSE_SCHEDULE_URL)
                                    }
                                }
                                if (isOfficialLmsCredentialPage(url) || path == MAIN_PATH) {
                                    onSessionExpired()
                                    return
                                }
                                if (path == "/lms/myLecture/doListView.dunet" && !submitted) {
                                    submitted = true
                                    val contentType = page.item.kind.officialContentType()
                                    if (contentType == null) {
                                        if (LmsUrlPolicy.isAllowed(page.item.detailUrl)) {
                                            view.loadUrl(page.item.detailUrl)
                                        } else {
                                            ready = true
                                            onMessage("공식 LMS 항목을 열 수 없습니다")
                                        }
                                        return
                                    }
                                    val officialItemId = if (isVideo) page.course.id + "_V" else page.item.id
                                    val script = "(function(){if(typeof fnGoContent!=='function')return 'missing';" +
                                        "fnGoContent(${JSONObject.quote(contentType)},${JSONObject.quote(page.course.id)}," +
                                        "${JSONObject.quote(page.course.classNo)},${JSONObject.quote(officialItemId)},'S');" +
                                        "return 'submitted';})()"
                                    view.evaluateJavascript(script) { result ->
                                        if (result == "\"missing\"") {
                                            if (LmsUrlPolicy.isAllowed(page.item.detailUrl)) {
                                                view.loadUrl(page.item.detailUrl)
                                            } else {
                                                ready = true
                                                onMessage("공식 LMS 항목을 열 수 없습니다")
                                            }
                                        }
                                    }
                                    return
                                }
                                if (path == LMS_LEARNING_WINDOW_PATH) {
                                    val previous = learningLaunchState
                                    learningLaunchState = reduceOfficialLearningLaunch(
                                        previous,
                                        LmsLearningLaunchEvent.PLAYER_PAGE_REACHED,
                                    )
                                    ready = true
                                    if (didConfirmedLearningBecomeOpened(previous, learningLaunchState)) {
                                        reportOpened()
                                    }
                                    return
                                }
                                if (
                                    learningLaunchState in setOf(
                                        LmsLearningLaunchState.OFFICIAL_FALLBACK,
                                        LmsLearningLaunchState.OFFICIAL_FALLBACK_OPENED,
                                    ) &&
                                    path == LMS_COURSE_SCHEDULE_PATH
                                ) {
                                    reportOfficialFallbackDisplayed()
                                    if (!officialFallbackReported) {
                                        officialFallbackReported = true
                                        onMessage("영상 플레이어를 열지 못해 공식 수업 화면을 열었습니다")
                                    }
                                    return
                                }
                                if (!isVideo && submitted && path.startsWith("/lms/class/")) {
                                    ready = true
                                    reportOpened()
                                    return
                                }
                                if (
                                    isVideo && submitted && path == LMS_COURSE_SCHEDULE_PATH &&
                                    learningLaunchState == LmsLearningLaunchState.LOCATING
                                ) {
                                    val target = normalizeOfficialLearningTitle(page.item.title)
                                    fun markUnavailable(message: String) {
                                        learningLaunchState = reduceOfficialLearningLaunch(
                                            learningLaunchState,
                                            LmsLearningLaunchEvent.EXACT_CANDIDATE_UNAVAILABLE,
                                        )
                                        reportOfficialFallbackDisplayed()
                                        onMessage(message)
                                    }
                                    fun scheduleLaunchWatchdog() {
                                        if (launchWatchdogScheduled) return
                                        launchWatchdogScheduled = true
                                        view.postDelayed(
                                            {
                                                val next = reduceOfficialLearningLaunch(
                                                    learningLaunchState,
                                                    LmsLearningLaunchEvent.WATCHDOG_EXPIRED,
                                                )
                                                if (next != learningLaunchState) {
                                                    learningLaunchState = next
                                                    showOfficialFallback(
                                                        "영상 플레이어를 열지 못해 공식 수업 화면을 열었습니다",
                                                    )
                                                }
                                            },
                                            LMS_LEARNING_LAUNCH_TIMEOUT_MILLIS,
                                        )
                                    }
                                    fun executeExactAction() {
                                        learningLaunchState = reduceOfficialLearningLaunch(
                                            learningLaunchState,
                                            LmsLearningLaunchEvent.EXACT_CANDIDATE_STARTED,
                                        )
                                        if (learningLaunchState != LmsLearningLaunchState.REQUESTED) return
                                        scheduleLaunchWatchdog()
                                        view.evaluateJavascript(officialLearningActionExecutionScript(target)) { rawResult ->
                                            if (rawResult != "\"started\"" && rawResult != "\"already_started\"") {
                                                val next = reduceOfficialLearningLaunch(
                                                    learningLaunchState,
                                                    LmsLearningLaunchEvent.EXACT_CANDIDATE_UNAVAILABLE,
                                                )
                                                if (next != learningLaunchState) {
                                                    learningLaunchState = next
                                                    showOfficialFallback(
                                                        if (rawResult == "\"ambiguous\"") {
                                                            "같은 이름의 영상이 있어 공식 화면을 열었습니다"
                                                        } else {
                                                            "해당 영상 차시를 찾지 못해 공식 화면을 열었습니다"
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    fun locateAndStart() {
                                        if (
                                            learningActionResolutionInFlight ||
                                            learningLaunchState != LmsLearningLaunchState.LOCATING
                                        ) {
                                            return
                                        }
                                        learningActionResolutionInFlight = true
                                        selectionAttempts += 1
                                        view.evaluateJavascript(officialLearningActionResolutionScript(target)) { rawResult ->
                                            learningActionResolutionInFlight = false
                                            when (rawResult) {
                                                "\"ready\"" -> executeExactAction()
                                                "\"waiting\"" -> if (selectionAttempts < LMS_LEARNING_SELECTION_ATTEMPTS) {
                                                    view.postDelayed(::locateAndStart, LMS_LEARNING_SELECTION_RETRY_MILLIS)
                                                } else {
                                                    markUnavailable("해당 영상 차시를 찾지 못해 공식 화면을 열었습니다")
                                                }
                                                "\"ambiguous\"" -> markUnavailable("같은 이름의 영상이 있어 공식 화면을 열었습니다")
                                                else -> markUnavailable("해당 영상 차시를 찾지 못해 공식 화면을 열었습니다")
                                            }
                                        }
                                    }
                                    locateAndStart()
                                    return
                                }
                                if (
                                    isVideo && submitted && path.startsWith("/lms/class/") &&
                                    learningLaunchState == LmsLearningLaunchState.LOCATING
                                ) {
                                    learningLaunchState = reduceOfficialLearningLaunch(
                                        learningLaunchState,
                                        LmsLearningLaunchEvent.EXACT_CANDIDATE_UNAVAILABLE,
                                    )
                                    reportOfficialFallbackDisplayed()
                                }
                            }

                            override fun onReceivedError(
                                view: WebView,
                                webRequest: WebResourceRequest,
                                error: WebResourceError,
                            ) {
                                if (webRequest.isForMainFrame) onMessage(error.description.toString())
                            }
                        }
                        loadUrl(LMS_NATIVE_DETAIL_DASHBOARD_URL)
                    }
                },
                modifier = Modifier.fillMaxSize().then(if (ready) Modifier else Modifier.alpha(0f)),
                onRelease = { webView ->
                    webView.stopLoading()
                    webView.destroy()
                },
            )
            if (!ready) {
                CircularProgressIndicator(
                    Modifier.align(Alignment.Center).size(36.dp).pulseBreath(),
                    strokeWidth = 3.dp,
                    strokeCap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun officialLearningActionLookupScript(target: String): String {
    val quotedTarget = JSONObject.quote(target)
    return """
          var target=$quotedTarget;
          function normalize(value){
            return (value||'')
              .replace(/^\[[^\]]+\]\s*/, '')
              .replace(/\s*\(?\s*\d+\s*분\s*\/\s*\d+\s*분\s*\)?\s*$/, '')
              .replace(/\s*\(영상콘텐츠\([^)]+\)\)\s*\|\s*출석인정시간\s*:\s*\d+\s*분\s*$/, '')
              .replace(/\s+/g, ' ')
              .trim();
          }
          function findExactAction(){
            var rows=Array.from(document.querySelectorAll('.view_act_cont'));
            if(rows.length===0)return {status:'waiting'};
            var matchingRows=rows.filter(function(row){
              var subject=row.querySelector('strong');
              return subject && normalize(subject.innerText||subject.textContent||'')===target;
            });
            if(matchingRows.length===0)return {status:'missing'};
            if(matchingRows.length!==1)return {status:'ambiguous'};
            var actions=Array.from(matchingRows[0].querySelectorAll(
              "button.btn_learn[onclick*='fncLearningWindow']"
            ));
            if(actions.length===0)return {status:'waiting'};
            if(actions.length!==1)return {status:'ambiguous'};
            return {status:'ready',action:actions[0]};
          }
    """.trimIndent()
}

internal fun officialLearningActionResolutionScript(target: String): String = """
        (function(){
          ${officialLearningActionLookupScript(target)}
          return findExactAction().status;
        })()
    """.trimIndent()

internal fun officialLearningActionExecutionScript(target: String): String = """
        (function(){
          ${officialLearningActionLookupScript(target)}
          var found=findExactAction();
          if(found.status!=='ready')return found.status;
          if(window.__dimaNowLearningActionStarted===true)return 'already_started';
          window.__dimaNowLearningActionStarted=true;
          window.open=function(url){
            if(typeof url==='string'&&url.trim()){
              try{
                var next=new URL(url,window.location.href);
                if(next.protocol==='https:'&&next.hostname==='lms.dima.ac.kr'&&(!next.port||next.port==='443')){
                  window.location.assign(next.href);
                }
              }catch(ignored){}
            }
            return window;
          };
          var stripTargets=function(){
            Array.from(document.querySelectorAll('form[target]')).forEach(function(form){form.removeAttribute('target');});
          };
          var nativeSubmit=HTMLFormElement.prototype.submit;
          HTMLFormElement.prototype.submit=function(){
            this.removeAttribute('target');
            return nativeSubmit.call(this);
          };
          if(HTMLFormElement.prototype.requestSubmit){
            var nativeRequestSubmit=HTMLFormElement.prototype.requestSubmit;
            HTMLFormElement.prototype.requestSubmit=function(submitter){
              this.removeAttribute('target');
              return arguments.length ? nativeRequestSubmit.call(this,submitter) : nativeRequestSubmit.call(this);
            };
          }
          stripTargets();
          new MutationObserver(stripTargets).observe(document.documentElement,{childList:true,subtree:true});
          found.action.click();
          return 'started';
        })()
    """.trimIndent()

@Composable
private fun LmsLoginScreen(
    needsReview: Boolean,
    onLogin: (String, String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var automatic by remember { mutableStateOf(true) }
    ScreenColumn(title = "수업", modifier = modifier) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .staggeredEntrance(0),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("LMS 계정으로 로그인", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (needsReview) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            text = "계정 정보를 다시 확인해 주세요.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("학번") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("lms_username"),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("비밀번호") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("lms_password"),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("자동 로그인", fontWeight = FontWeight.Bold)
                        Text("이 기기에 암호화해 저장합니다", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = automatic, onCheckedChange = { automatic = it })
                }
                Button(
                    onClick = { onLogin(username, password, automatic) },
                    enabled = username.isNotBlank() && password.isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("lms_login"),
                ) { Text("로그인", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
internal fun LmsItemsScreen(
    snapshot: LmsSnapshot,
    sessionState: LmsSessionState,
    selectedCourse: String?,
    selectedKind: LmsItemKind?,
    selectedRead: Boolean?,
    onCourseChange: (String?) -> Unit,
    onKindChange: (LmsItemKind?) -> Unit,
    onReadChange: (Boolean?) -> Unit,
    onRefresh: () -> Unit,
    onOpenItem: (LmsItem) -> Unit,
    now: Instant,
    modifier: Modifier = Modifier,
) {
    val filtered = filterLmsItems(snapshot.items, selectedCourse, selectedKind, selectedRead)
    val filterActive = selectedCourse != null || selectedKind != null || selectedRead != null
    val syncing = snapshot.syncState == LmsSyncState.SYNCING
    val hasError = sessionState == LmsSessionState.ERROR || snapshot.syncState == LmsSyncState.ERROR
    var todayMode by rememberSaveable { mutableStateOf(true) }
    var completedExpanded by rememberSaveable { mutableStateOf(false) }
    val agenda = remember(snapshot.items, now) {
        LmsAgendaPlanner(Clock.fixed(now, SEOUL)).plan(snapshot.items, now)
    }
    val visibleEmpty = if (todayMode) agenda.groups.isEmpty() else filtered.isEmpty()

    ScreenLazyColumn(
        title = "수업",
        modifier = modifier,
        listTag = "lms_history",
        topAction = {
            IconButton(onClick = onRefresh, enabled = !syncing) {
                if (syncing) {
                    CircularProgressIndicator(
                        Modifier.size(22.dp).pulseBreath(),
                        strokeWidth = 2.5.dp,
                        strokeCap = StrokeCap.Round,
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                }
            }
        },
    ) {
        item {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().staggeredEntrance(0),
            ) {
                listOf(true to "오늘", false to "전체").forEachIndexed { index, (isToday, label) ->
                    SegmentedButton(
                        selected = todayMode == isToday,
                        onClick = { todayMode = isToday },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                        label = { Text(label, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f).testTag(if (isToday) "lms_mode_today" else "lms_mode_all"),
                    )
                }
            }
        }

        if (!todayMode) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.staggeredEntrance(1)) {
                    item { FilterChip(selected = selectedCourse == null, onClick = { onCourseChange(null) }, label = { Text("전체") }, shape = RoundedCornerShape(10.dp)) }
                    items(snapshot.courses, key = { it.id }) { course ->
                        FilterChip(selected = selectedCourse == course.id, onClick = { onCourseChange(course.id) }, label = { Text(course.name) }, shape = RoundedCornerShape(10.dp))
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.staggeredEntrance(2)) {
                    item { FilterChip(selected = selectedKind == null, onClick = { onKindChange(null) }, label = { Text("전체") }, shape = RoundedCornerShape(10.dp)) }
                    items(LmsItemKind.entries.filter { kind -> snapshot.items.any { it.kind == kind } }) { kind ->
                        FilterChip(
                            selected = selectedKind == kind,
                            onClick = { onKindChange(kind) },
                            label = { Text(kindLabel(kind)) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("lms_kind_${kind.name}"),
                        )
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.staggeredEntrance(3)) {
                    item { FilterChip(selected = selectedRead == null, onClick = { onReadChange(null) }, label = { Text("모두") }, shape = RoundedCornerShape(10.dp), modifier = Modifier.testTag("lms_read_all")) }
                    item { FilterChip(selected = selectedRead == false, onClick = { onReadChange(false) }, label = { Text("안읽음") }, shape = RoundedCornerShape(10.dp), modifier = Modifier.testTag("lms_read_unread")) }
                    item { FilterChip(selected = selectedRead == true, onClick = { onReadChange(true) }, label = { Text("읽음") }, shape = RoundedCornerShape(10.dp), modifier = Modifier.testTag("lms_read_read")) }
                }
            }
        }

        when {
            visibleEmpty && syncing -> {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp)
                            .staggeredEntrance(3),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CircularProgressIndicator(strokeWidth = 3.dp, strokeCap = StrokeCap.Round)
                        Text("수업 정보를 불러오는 중", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            visibleEmpty && hasError -> {
                item {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggeredEntrance(3),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = snapshot.errorMessage ?: "수업 정보를 불러오지 못했습니다",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            FilledTonalButton(onClick = onRefresh, shape = RoundedCornerShape(12.dp)) {
                                Text("다시 시도", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            visibleEmpty -> {
                item {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggeredEntrance(3),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = if (todayMode) "오늘 확인할 학습이 없습니다" else if (filterActive) "필터에 해당하는 항목이 없습니다" else "표시할 항목이 없습니다",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!todayMode && filterActive) {
                                TextButton(onClick = { onCourseChange(null); onKindChange(null); onReadChange(null) }) {
                                    Text("필터 해제", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                if (todayMode) {
                    agenda.groups.forEach { group ->
                        item(key = "agenda-${group.key}-${group.date}") {
                            if (group.key == LmsAgendaGroupKey.COMPLETED) {
                                TextButton(
                                    onClick = { completedExpanded = !completedExpanded },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        group.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text("${group.items.size}개")
                                    Spacer(Modifier.size(8.dp))
                                    Text(if (completedExpanded) "접기" else "보기")
                                }
                            } else {
                                Text(
                                    text = agendaGroupTitle(group),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                        if (group.key != LmsAgendaGroupKey.COMPLETED || completedExpanded) {
                            items(group.items, key = { item -> "today-${item.kind}-${item.courseId}-${item.id}" }) { item ->
                                LmsItemCard(item, onOpenItem)
                            }
                        }
                    }
                } else {
                    items(filtered, key = { item -> "${item.kind}:${item.courseId}:${item.id}" }) { item ->
                        LmsItemCard(item, onOpenItem)
                    }
                }
            }
        }
    }
}

@Composable
private fun LmsItemCard(item: LmsItem, onOpenItem: (LmsItem) -> Unit) {
    ElevatedCard(
        onClick = { onOpenItem(item) },
        modifier = Modifier.fillMaxWidth().staggeredEntrance(4).expressiveBounceClick(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LmsStatusBadge(kindLabel(item.kind), prominent = false)
                Text(
                    item.courseName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                LmsStatusBadge(if (item.isRead) "읽음" else "안읽음", prominent = !item.isRead)
            }
            val badges = buildList {
                when (item.changeState) {
                    LmsChangeState.NEW -> add("새 항목")
                    LmsChangeState.UPDATED -> add("변경됨")
                    LmsChangeState.NONE -> Unit
                }
                when (item.completionState) {
                    LmsCompletionState.COMPLETE -> add(
                        if (item.kind == LmsItemKind.CONTENT) "수강 완료" else "완료",
                    )
                    LmsCompletionState.INCOMPLETE -> add(
                        if (item.kind == LmsItemKind.CONTENT) "미수강" else "미완료",
                    )
                    LmsCompletionState.NOT_TRACKED, LmsCompletionState.UNKNOWN -> Unit
                }
            }
            if (badges.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    badges.forEach { badge -> LmsStatusBadge(badge, prominent = true) }
                }
            }
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            item.dueAt?.let {
                Text("마감 ${LMS_TIME.format(it.atZone(SEOUL))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } ?: item.registeredAt?.let {
                Text("등록 ${LMS_TIME.format(it.atZone(SEOUL))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LmsStatusBadge(label: String, prominent: Boolean) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (prominent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (prominent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

private fun agendaGroupTitle(group: LmsAgendaGroup): String = when (group.key) {
    LmsAgendaGroupKey.DATE -> if (group.title == "오늘") {
        "오늘"
    } else {
        group.date?.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)) ?: group.title
    }
    else -> group.title
}

internal fun filterLmsItems(
    items: List<LmsItem>,
    courseId: String?,
    kind: LmsItemKind?,
    isRead: Boolean?,
): List<LmsItem> = items.filter { item ->
    (courseId == null || item.courseId == courseId) &&
        (kind == null || item.kind == kind) &&
        (isRead == null || item.isRead == isRead)
}

@Composable
private fun LmsDetailScreen(
    presented: LmsPresentedDetail,
    source: LmsSource,
    sessionController: LmsSessionController,
    autoLoginCoordinator: LmsAutoLoginCoordinator,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = presented.detail
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingDocument by remember { mutableStateOf<LmsPendingDocument?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var activeDownloadCache by remember { mutableStateOf<File?>(null) }
    val activeDownloadCacheOnDispose by rememberUpdatedState(activeDownloadCache)
    DisposableEffect(Unit) {
        onDispose {
            activeDownloadCacheOnDispose?.delete()
        }
    }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val verified = pendingDocument
        pendingDocument = null
        if (uri == null || verified == null) {
            verified?.cache?.delete()
            activeDownloadCache = null
            downloading = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                LmsDocumentWriter(
                    openOutputStream = { destination: Uri ->
                        context.contentResolver.openOutputStream(destination, "w")
                    },
                    deleteDocument = { destination: Uri ->
                        runCatching {
                            DocumentsContract.deleteDocument(context.contentResolver, destination)
                        }.getOrDefault(false)
                    },
                ).write(verified.cache, uri, verified.expectedBytes)
            }
            when (result) {
                is LmsDocumentWriteResult.Success -> onMessage("저장했습니다")
                is LmsDocumentWriteResult.Failure -> onMessage(result.message)
            }
            activeDownloadCache = null
            downloading = false
        }
    }
    Column(modifier.statusBarsPadding().navigationBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
            Text(
                text = detail.item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (presented.attachmentsChanged) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("첨부파일이 변경됐어요", fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
            }
        } else if (presented.cached) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("저장된 내용", fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("lms_native_detail_body"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    detail.item.courseName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                LmsStatusBadge(kindLabel(detail.item.kind), prominent = false)
            }
            Text(detail.item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            val registeredAt = detail.metadata.registeredAt ?: detail.item.registeredAt
            if (!detail.metadata.author.isNullOrBlank() || registeredAt != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    detail.metadata.author?.takeIf { it.isNotBlank() }?.let { author ->
                        LmsDetailMetadataRow("작성자", author)
                    }
                    registeredAt?.let { value ->
                        LmsDetailMetadataRow("등록일", LMS_DETAIL_TIME.format(value.atZone(SEOUL)))
                    }
                }
            }
            Text("내용", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            SelectionContainer {
                Text(
                    text = AnnotatedString.fromHtml(
                        htmlString = detail.sanitizedHtml,
                        linkInteractionListener = {},
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (detail.item.kind == LmsItemKind.ASSIGNMENT) {
                assignmentPeriod(detail)?.let { period ->
                    LmsDetailMetadataRow("제출기간", period)
                }
                detail.metadata.maxScore?.takeIf { it.isNotBlank() }?.let { score ->
                    LmsDetailMetadataRow("만점", score)
                }
            }
            if (detail.attachments.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("첨부파일", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    detail.attachments.forEach { attachment ->
                        OutlinedButton(
                            onClick = {
                                if (downloading) return@OutlinedButton
                                downloading = true
                                scope.launch {
                                    val cache = runCatching {
                                        createLmsAttachmentCacheFile(context.cacheDir)
                                    }.getOrElse { error ->
                                        downloading = false
                                        onMessage(error.message ?: "첨부파일 임시 경로를 만들지 못했습니다")
                                        return@launch
                                    }
                                    activeDownloadCache = cache
                                    var result = source.downloadAttachment(attachment, cache)
                                    if (result == LmsAttachmentDownloadResult.SessionExpired) {
                                        sessionController.transition(LmsSessionState.EXPIRED)
                                        if (autoLoginCoordinator.ensureActive(force = true) == LmsSessionState.ACTIVE) {
                                            result = source.downloadAttachment(attachment, cache)
                                        }
                                    }
                                    when (result) {
                                        is LmsAttachmentDownloadResult.Success -> {
                                            if (result.bytesWritten <= 0L || cache.length() != result.bytesWritten) {
                                                cache.delete()
                                                activeDownloadCache = null
                                                downloading = false
                                                onMessage("첨부파일 크기를 확인하지 못했습니다")
                                            } else {
                                                pendingDocument = LmsPendingDocument(cache, result.bytesWritten)
                                                createDocument.launch(result.fileName)
                                            }
                                        }
                                        LmsAttachmentDownloadResult.SessionExpired -> {
                                            cache.delete()
                                            activeDownloadCache = null
                                            downloading = false
                                            onMessage("로그인이 필요합니다")
                                        }
                                        is LmsAttachmentDownloadResult.Failure -> {
                                            cache.delete()
                                            activeDownloadCache = null
                                            downloading = false
                                            onMessage(result.message)
                                        }
                                    }
                                }
                            },
                            enabled = !downloading,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (downloading) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Download, null)
                            }
                            Spacer(Modifier.size(8.dp))
                            Text(attachment.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LmsDetailMetadataRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun assignmentPeriod(detail: LmsItemDetail): String? {
    val start = detail.metadata.submissionStartsAt
    val end = detail.metadata.submissionEndsAt ?: detail.item.dueAt
    return when {
        start != null && end != null ->
            "${LMS_DETAIL_TIME.format(start.atZone(SEOUL))} ~ ${LMS_DETAIL_TIME.format(end.atZone(SEOUL))}"
        end != null -> "마감 · ${LMS_DETAIL_TIME.format(end.atZone(SEOUL))}"
        start != null -> "시작 · ${LMS_DETAIL_TIME.format(start.atZone(SEOUL))}"
        else -> null
    }
}

@Composable
private fun LmsAuthenticationWebView(
    request: LmsLoginRequest,
    onComplete: (LmsLoginResult) -> Unit,
    onAuthenticated: (List<LmsCourse>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current
    val parser = remember { LmsHtmlParser() }
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    Column(modifier.statusBarsPadding().navigationBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "로그인 취소")
            }
            Text(
                text = "공식 포털에서 로그인 중",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            CircularProgressIndicator(
                Modifier.size(20.dp).pulseBreath(),
                strokeWidth = 2.5.dp,
                strokeCap = StrokeCap.Round,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    Modifier.size(36.dp).pulseBreath(),
                    strokeWidth = 3.dp,
                    strokeCap = StrokeCap.Round,
                )
                Text("공식 포털에서 로그인 중", fontWeight = FontWeight.SemiBold)
            }
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        visibility = View.INVISIBLE
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        if (Build.VERSION.SDK_INT >= 26) WebView.startSafeBrowsing(context, null)
                        var injected = false
                        var catalogRequested = false
                        var flowState = LmsLoginFlowState.initial()
                        var finished = false

                        fun finish(result: LmsLoginResult) {
                            if (finished || request.result.isCompleted) return
                            finished = true
                            onComplete(result)
                        }

                        fun advance(
                            webView: WebView,
                            event: LmsLoginFlowEvent,
                            extractedCourses: List<LmsCourse>? = null,
                        ) {
                            if (finished || request.result.isCompleted) return
                            val transition = reduceLmsLoginFlow(flowState, event)
                            flowState = transition.state
                            when (val command = transition.command) {
                                LmsLoginFlowCommand.None -> Unit
                                LmsLoginFlowCommand.SubmitCredentials -> webView.loadUrl(LOGIN_URL)
                                LmsLoginFlowCommand.InspectSessionTakeoverAction -> {
                                    webView.evaluateJavascript(VERIFY_LMS_SESSION_TAKEOVER_ACTION_SCRIPT) { raw ->
                                        when (parseLmsSessionTakeoverScriptResult(raw)) {
                                            LmsSessionTakeoverScriptResult.VERIFIED ->
                                                advance(webView, LmsLoginFlowEvent.SessionTakeoverActionVerified)
                                            LmsSessionTakeoverScriptResult.INTERACTIVE ->
                                                advance(webView, LmsLoginFlowEvent.InteractiveChallengeDetected)
                                            else -> advance(
                                                webView,
                                                LmsLoginFlowEvent.SessionTakeoverActionUnavailable,
                                            )
                                        }
                                    }
                                }
                                LmsLoginFlowCommand.SubmitVerifiedSessionTakeover -> {
                                    // The official 3045 recovery returns to the portal login form. Allow exactly
                                    // one credential resubmission after the live page action is re-verified.
                                    injected = false
                                    webView.evaluateJavascript(SUBMIT_LMS_SESSION_TAKEOVER_ACTION_SCRIPT) { raw ->
                                        when (parseLmsSessionTakeoverScriptResult(raw)) {
                                            LmsSessionTakeoverScriptResult.SUBMITTED -> Unit
                                            LmsSessionTakeoverScriptResult.INTERACTIVE ->
                                                advance(webView, LmsLoginFlowEvent.InteractiveChallengeDetected)
                                            else -> advance(
                                                webView,
                                                LmsLoginFlowEvent.SessionTakeoverActionUnavailable,
                                            )
                                        }
                                    }
                                }
                                LmsLoginFlowCommand.LoadDashboard -> webView.loadUrl(LMS_DASHBOARD_URL)
                                LmsLoginFlowCommand.ExtractCourses -> {
                                    if (catalogRequested) return
                                    catalogRequested = true
                                    webView.evaluateJavascript(EXTRACT_RENDERED_COURSES_SCRIPT) { value ->
                                        val courses = parser.parseRenderedCourses(value)
                                        advance(
                                            webView,
                                            LmsLoginFlowEvent.CourseExtractionFinished(courses.isNotEmpty()),
                                            courses,
                                        )
                                    }
                                }
                                is LmsLoginFlowCommand.Complete -> {
                                    if (command.result == LmsLoginResult.Success && extractedCourses != null) {
                                        finished = true
                                        CookieManager.getInstance().flush()
                                        onAuthenticated(extractedCourses)
                                    } else {
                                        finish(command.result)
                                    }
                                }
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onJsAlert(
                                view: WebView,
                                url: String,
                                message: String,
                                result: JsResult,
                            ): Boolean {
                                if (isOfficialLmsCredentialPage(url)) {
                                    result.confirm()
                                    advance(view, LmsLoginFlowEvent.CredentialsRejected)
                                    return true
                                }
                                if (isExactOfficialLmsSessionConflictUrl(url)) {
                                    result.cancel()
                                    advance(view, LmsLoginFlowEvent.InteractiveChallengeDetected)
                                    return true
                                }
                                return super.onJsAlert(view, url, message, result)
                            }

                            override fun onJsConfirm(
                                view: WebView,
                                url: String,
                                message: String,
                                result: JsResult,
                            ): Boolean {
                                if (LmsUrlPolicy.isAllowedLoginNavigation(url)) {
                                    result.cancel()
                                    advance(view, LmsLoginFlowEvent.InteractiveChallengeDetected)
                                    return true
                                }
                                return super.onJsConfirm(view, url, message, result)
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                webRequest: WebResourceRequest,
                            ): WebResourceResponse? = if (
                                shouldBlockLmsWebResource(webRequest.url.toString(), loginFlow = true)
                            ) {
                                blockedLmsWebResourceResponse()
                            } else {
                                super.shouldInterceptRequest(view, webRequest)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                webRequest: WebResourceRequest,
                            ): Boolean {
                                val uri = webRequest.url
                                val allowed = LmsUrlPolicy.isAllowedLoginNavigation(uri.toString())
                                if (allowed) return false
                                val upgraded = LmsUrlPolicy.upgradeOfficialHttp(uri.toString())
                                if (upgraded != null) view.loadUrl(upgraded)
                                else advance(view, LmsLoginFlowEvent.MainFrameFinished(uri.toString()))
                                return true
                            }

                            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = Unit

                            override fun onPageFinished(view: WebView, url: String) {
                                if (isExactOfficialLmsSessionConflictUrl(url)) {
                                    advance(view, LmsLoginFlowEvent.MainFrameFinished(url))
                                    return
                                }
                                val path = Uri.parse(url).path.orEmpty()
                                if (path == MAIN_PATH) {
                                    view.evaluateJavascript(
                                        "Boolean(document.querySelector(\"a[href*='/lms/myLecture/doListView']\"))",
                                    ) { authenticated ->
                                        if (authenticated == "true") {
                                            advance(
                                                view,
                                                LmsLoginFlowEvent.MainFrameFinished(
                                                    url = url,
                                                    authenticatedMain = true,
                                                ),
                                            )
                                        } else if (flowState.stage == LmsLoginFlowStage.LOGIN_SUBMISSION) {
                                            advance(view, LmsLoginFlowEvent.CredentialsRejected)
                                        } else {
                                            advance(view, LmsLoginFlowEvent.SessionTakeoverActionUnavailable)
                                        }
                                    }
                                    return
                                } else if (path == "/lms/myLecture/doListView.dunet") {
                                    advance(view, LmsLoginFlowEvent.MainFrameFinished(url))
                                    return
                                } else if (isOfficialLmsCredentialPage(url) && !injected) {
                                    injected = true
                                    val portal = Uri.parse(url).host == PORTAL_HOST
                                    view.evaluateJavascript(
                                        lmsCredentialSubmissionScript(
                                            username = request.credentials.username,
                                            password = request.credentials.password,
                                            portal = portal,
                                        ),
                                    ) { result ->
                                        if (result == "\"submitted\"") {
                                            view.postDelayed(
                                                {
                                                    if (
                                                        !request.result.isCompleted &&
                                                        shouldReviewStoredLmsCredentials(
                                                            view.url.orEmpty(),
                                                            submitted = true,
                                                            elapsedMillis = LOGIN_RESULT_TIMEOUT_MILLIS,
                                                        )
                                                    ) {
                                                        if (flowState.stage == LmsLoginFlowStage.LOGIN_SUBMISSION) {
                                                            advance(view, LmsLoginFlowEvent.CredentialsRejected)
                                                        } else {
                                                            advance(
                                                                view,
                                                                LmsLoginFlowEvent.SessionTakeoverActionUnavailable,
                                                            )
                                                        }
                                                    }
                                                },
                                                LOGIN_RESULT_TIMEOUT_MILLIS,
                                            )
                                        } else {
                                            advance(view, LmsLoginFlowEvent.InteractiveChallengeDetected)
                                        }
                                    }
                                    return
                                }
                                advance(view, LmsLoginFlowEvent.MainFrameFinished(url))
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError,
                            ) {
                                if (request.isForMainFrame) {
                                    finish(LmsLoginResult.NetworkError(error.description.toString()))
                                }
                            }

                            override fun onSafeBrowsingHit(
                                view: WebView,
                                request: WebResourceRequest,
                                threatType: Int,
                                callback: SafeBrowsingResponse,
                            ) {
                                callback.backToSafety(true)
                                finish(LmsLoginResult.Failure("안전하지 않은 페이지가 차단되었습니다"))
                            }
                        }
                        advance(this, LmsLoginFlowEvent.CredentialsAvailable)
                    }
                },
                modifier = Modifier.size(1.dp),
            )
        }
    }
}

@Composable
private fun LmsRenderedPageWebView(
    request: LmsRenderedPageRequest,
    onComplete: (LmsRenderedPageResult) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val parser = remember { LmsHtmlParser() }
    Column(modifier.statusBarsPadding().navigationBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "불러오기 취소")
            }
            Text(
                text = "글 불러오는 중",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            CircularProgressIndicator(
                Modifier.size(20.dp).pulseBreath(),
                strokeWidth = 2.5.dp,
                strokeCap = StrokeCap.Round,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                Modifier.size(36.dp).pulseBreath(),
                strokeWidth = 3.dp,
                strokeCap = StrokeCap.Round,
            )
            AndroidView(
                factory = {
                    WebView(context).apply {
                        visibility = View.INVISIBLE
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        var submitted = false
                        var assignmentActionSubmitted = false
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                webRequest: WebResourceRequest,
                            ): WebResourceResponse? = if (
                                shouldBlockLmsWebResource(webRequest.url.toString(), loginFlow = false)
                            ) {
                                blockedLmsWebResourceResponse()
                            } else {
                                super.shouldInterceptRequest(view, webRequest)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                webRequest: WebResourceRequest,
                            ): Boolean {
                                if (LmsUrlPolicy.isAllowed(webRequest.url.toString())) return false
                                onComplete(LmsRenderedPageResult.Failure("안전하지 않은 페이지가 차단되었습니다"))
                                return true
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                val uri = Uri.parse(url)
                                val path = uri.path.orEmpty()
                                if (isOfficialLmsCredentialPage(url) || path == MAIN_PATH) {
                                    onComplete(LmsRenderedPageResult.SessionExpired)
                                    return
                                }
                                if (path == "/lms/myLecture/doListView.dunet" && !submitted) {
                                    val type = request.item.kind.officialContentType()
                                    if (type == null) {
                                        onComplete(LmsRenderedPageResult.Failure("이 글 형식은 아직 지원하지 않습니다"))
                                        return
                                    }
                                    submitted = true
                                    val script =
                                        "(function(){if(typeof fnGoContent!=='function')return 'missing';" +
                                            "fnGoContent(${JSONObject.quote(type)}," +
                                            "${JSONObject.quote(request.course.id)}," +
                                            "${JSONObject.quote(request.course.classNo)}," +
                                            "${JSONObject.quote(request.item.id)},'S');return 'submitted';})()"
                                    view.evaluateJavascript(script) { result ->
                                        if (result == "\"missing\"") {
                                            onComplete(LmsRenderedPageResult.Failure("공식 LMS 글 열기 기능을 찾지 못했습니다"))
                                        }
                                    }
                                    return
                                }
                                if (
                                    submitted &&
                                    request.item.kind == LmsItemKind.ASSIGNMENT &&
                                    path == "/lms/class/report/stud/doListView.dunet" &&
                                    !assignmentActionSubmitted
                                ) {
                                    assignmentActionSubmitted = true
                                    view.evaluateJavascript("document.documentElement.outerHTML") { value ->
                                        val html = runCatching {
                                            JSONObject("{\"value\":$value}").getString("value")
                                        }.getOrNull()
                                        val action = html?.let {
                                            parser.resolveAssignmentListOnClick(request.item, it)
                                        }
                                        if (action == null) {
                                            onComplete(LmsRenderedPageResult.OfficialCoursePage)
                                            return@evaluateJavascript
                                        }
                                        val script =
                                            "(function(){if(typeof fncModifyReport!=='function')return 'missing';" +
                                                "$action;return 'submitted';})()"
                                        view.evaluateJavascript(script) { result ->
                                            if (result == "\"missing\"") {
                                                onComplete(LmsRenderedPageResult.OfficialCoursePage)
                                            }
                                        }
                                    }
                                    return
                                }
                                if (
                                    submitted &&
                                    request.item.kind == LmsItemKind.ASSIGNMENT &&
                                    path != "/lms/class/report/stud/doFormReport.dunet"
                                ) {
                                    return
                                }
                                if (submitted && path.startsWith("/lms/class/")) {
                                    view.evaluateJavascript("document.documentElement.outerHTML") { value ->
                                        val html = runCatching {
                                            JSONObject("{\"value\":$value}").getString("value")
                                        }.getOrNull()
                                        if (html.isNullOrBlank()) {
                                            onComplete(LmsRenderedPageResult.Failure("글 내용을 확인하지 못했습니다"))
                                        } else {
                                            CookieManager.getInstance().flush()
                                            onComplete(LmsRenderedPageResult.Success(url, html))
                                        }
                                    }
                                }
                            }

                            override fun onReceivedError(
                                view: WebView,
                                webRequest: WebResourceRequest,
                                error: WebResourceError,
                            ) {
                                if (webRequest.isForMainFrame) {
                                    onComplete(LmsRenderedPageResult.Failure(error.description.toString()))
                                }
                            }
                        }
                        loadUrl(LMS_NATIVE_DETAIL_DASHBOARD_URL)
                    }
                },
                modifier = Modifier.size(1.dp),
                onRelease = { webView ->
                    webView.stopLoading()
                    webView.destroy()
                },
            )
        }
    }
}

private fun LmsItemKind.officialContentType(): String? = when (this) {
    LmsItemKind.NOTICE -> "1"
    LmsItemKind.QUESTION -> "2"
    LmsItemKind.ASSIGNMENT -> "3"
    LmsItemKind.DISCUSSION -> "4"
    LmsItemKind.TEAM_PROJECT -> "5"
    LmsItemKind.QUIZ -> "6"
    LmsItemKind.EXAM -> "7"
    LmsItemKind.CONTENT -> "8"
    LmsItemKind.MATERIAL -> "9"
    LmsItemKind.OTHER -> null
}

private fun kindLabel(kind: LmsItemKind): String = when (kind) {
    LmsItemKind.NOTICE -> "공지"
    LmsItemKind.ASSIGNMENT -> "과제"
    LmsItemKind.CONTENT -> "콘텐츠"
    LmsItemKind.MATERIAL -> "자료"
    LmsItemKind.QUESTION -> "질문"
    LmsItemKind.DISCUSSION -> "토론"
    LmsItemKind.TEAM_PROJECT -> "팀프로젝트"
    LmsItemKind.QUIZ -> "퀴즈"
    LmsItemKind.EXAM -> "시험"
    LmsItemKind.OTHER -> "기타"
}

private val SEOUL = ZoneId.of("Asia/Seoul")
private val LMS_TIME = DateTimeFormatter.ofPattern("M월 d일 HH:mm")
private val LMS_DETAIL_TIME = DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH:mm")
internal const val OFFICIAL_LMS_LOGIN_URL =
    "https://portal.dima.ac.kr/?r=https://lms.dima.ac.kr/sso/index.jsp"
private const val LOGIN_URL = OFFICIAL_LMS_LOGIN_URL
private const val LMS_DASHBOARD_URL =
    "https://lms.dima.ac.kr/lms/myLecture/doListView.dunet?to_do_type=all"
private const val LMS_NATIVE_DETAIL_DASHBOARD_URL =
    "https://lms.dima.ac.kr/lms/myLecture/doListView.dunet?mnid=201008840728"
private const val LMS_COURSE_SCHEDULE_PATH = "/lms/class/courseSchedule/doListView.dunet"
private const val LMS_COURSE_SCHEDULE_URL =
    "https://lms.dima.ac.kr/lms/class/courseSchedule/doListView.dunet"
private const val LMS_LEARNING_WINDOW_PATH = "/lms/class/courseSchedule/doLearningWindow2.dunet"
private const val LMS_LEARNING_SELECTION_ATTEMPTS = 20
private const val LMS_LEARNING_SELECTION_RETRY_MILLIS = 250L
private const val LMS_LEARNING_LAUNCH_TIMEOUT_MILLIS = 8_000L

internal fun shouldBlockLmsWebResource(url: String, loginFlow: Boolean): Boolean {
    val scheme = runCatching { java.net.URI.create(url).scheme?.lowercase() }.getOrNull()
    if (scheme !in setOf("http", "https")) return false
    return if (loginFlow) {
        !LmsUrlPolicy.isAllowedLoginNavigation(url)
    } else {
        !LmsUrlPolicy.isAllowed(url)
    }
}

private fun blockedLmsWebResourceResponse(): WebResourceResponse = WebResourceResponse(
    "text/plain",
    "UTF-8",
    403,
    "Blocked",
    mapOf("Cache-Control" to "no-store"),
    ByteArrayInputStream(ByteArray(0)),
)

internal fun lmsCredentialSubmissionScript(
    username: String,
    password: String,
    portal: Boolean,
): String {
    val user = JSONObject.quote(username)
    val secret = JSONObject.quote(password)
    val userSelector = if (portal) "#txtID" else "#id"
    val passwordSelector = if (portal) "#txtPwd" else "#pass"
    val submitCheck = if (portal) "typeof Login==='function'" else "typeof login_proc==='function'"
    val submit = if (portal) "Login('N')" else "login_proc()"
    return """
        (function(){
          var i=document.querySelector('$userSelector'),p=document.querySelector('$passwordSelector');
          if(!i||!p||!($submitCheck))return 'interactive';
          function isHidden(control){
            if(control.hidden)return true;
            if(control.tagName==='INPUT'&&control.type==='hidden')return true;
            var style=window.getComputedStyle(control);
            return style.display==='none'||style.visibility==='hidden';
          }
          var challengeMarker=document.querySelector(
            "[data-sitekey],.g-recaptcha,img[src*='captcha' i]"
          );
          var unexpectedControl=Array.from(document.querySelectorAll(
            "input,select,textarea,iframe"
          )).some(function(control){
            if(control===i||control===p||control.disabled||isHidden(control))return false;
            if(control.tagName!=='INPUT')return true;
            return !['button','submit','reset','image','checkbox','radio'].includes(control.type);
          });
          if(challengeMarker||unexpectedControl)return 'interactive';
          i.value=$user;p.value=$secret;$submit;return 'submitted';
        })()
    """.trimIndent()
}

private val EXTRACT_RENDERED_COURSES_SCRIPT = """
    (function(){
      return Array.from(document.querySelectorAll("[href*='fncGoClassroom'],[onclick*='fncGoClassroom']"))
        .map(function(link){
          var action=(link.getAttribute('href')||'')+' '+(link.getAttribute('onclick')||'');
          var match=action.match(/fncGoClassroom\(\s*['"]([^'"]+)['"]\s*,\s*['"]([^'"]*)['"]/);
          if(!match)return null;
          var root=link.closest('li.box,.lecture_info,.lecture-card,.course-card')||link.parentElement||link;
          var nameNode=link.querySelector('.title,.lecture_title')||root.querySelector('.title,.lecture_title')||link;
          var text=root.innerText||'';
          var professor=text.match(/교수(?:명)?\s*[:：]\s*([^\n·|]+)/);
          return {id:match[1],classNo:match[2],name:(nameNode.textContent||'').trim(),professor:professor?professor[1].trim():null};
        }).filter(Boolean);
    })()
""".trimIndent()
private val LMS_SESSION_TAKEOVER_CANDIDATE_FUNCTION = """
    function findVerifiedTakeoverAction(){
      var query=new URLSearchParams(location.search);
      var queryNames=Array.from(query.keys());
      var exactQuery=query.getAll('errorCode').length===1&&query.get('errorCode')==='3045'&&
        query.getAll('errorMsg').length<=1&&queryNames.every(function(name){
          return name==='errorCode'||name==='errorMsg';
        });
      if(location.protocol!=='https:'||location.hostname!=='portal.dima.ac.kr'||
         location.pathname!=='/sso/error.aspx'||!exactQuery){
        return {state:'unavailable'};
      }
      if(document.querySelector("input[type='password'],input[name*='otp' i],input[id*='otp' i],"+
          "input[name*='captcha' i],input[id*='captcha' i],iframe[src*='captcha' i]")){
        return {state:'interactive'};
      }
      function parseExactConflictUrl(raw){
        try{
          var url=new URL(raw,location.href);
          var names=Array.from(url.searchParams.keys());
          var validQuery=url.searchParams.getAll('errorCode').length===1&&
            url.searchParams.get('errorCode')==='3045'&&
            url.searchParams.getAll('errorMsg').length<=1&&
            names.every(function(name){return name==='errorCode'||name==='errorMsg';});
          return url.protocol==='https:'&&url.hostname==='portal.dima.ac.kr'&&
            (url.port===''||url.port==='443')&&url.pathname==='/sso/error.aspx'&&
            !url.hash&&validQuery?url:null;
        }catch(e){return null;}
      }
      var forms=Array.from(document.forms);
      var form=forms.length===1?forms[0]:null;
      var controls=form?Array.from(form.elements):[];
      var actionUrl=form?parseExactConflictUrl(form.getAttribute('action')||location.href):null;
      var hiddenOnly=Boolean(form)&&form.method.toLowerCase()==='post'&&controls.length>0&&
        controls.every(function(control){return control.tagName==='INPUT'&&control.type==='hidden';})&&
        controls.some(function(control){return control.name==='__VIEWSTATE';})&&
        actionUrl!==null&&actionUrl.search===location.search;
      var hasInteractiveControl=Boolean(document.querySelector(
        "button,input:not([type='hidden']),select,textarea,a[href],a[onclick],[onclick],"+
        "[role='button'],[tabindex]:not([tabindex='-1']),"+
        "[contenteditable]:not([contenteditable='false'])"
      ));
      var redirectUrls=Array.from(document.scripts).map(function(script){
        var match=(script.textContent||'').match(
          /top\.location\.href\s*=\s*['"](https:\/\/portal\.dima\.ac\.kr\/?)['"]/
        );
        if(!match)return null;
        try{
          var target=new URL(match[1]);
          return target.protocol==='https:'&&target.hostname==='portal.dima.ac.kr'&&
            target.pathname==='/'&&!target.search&&!target.hash?target.href:null;
        }catch(e){return null;}
      }).filter(Boolean);
      if(hiddenOnly&&!hasInteractiveControl&&redirectUrls.length===1){
        return {state:'verified',redirectUrl:redirectUrls[0]};
      }
      return {state:'unavailable'};
    }
""".trimIndent()

internal val VERIFY_LMS_SESSION_TAKEOVER_ACTION_SCRIPT = """
    (function(){
      $LMS_SESSION_TAKEOVER_CANDIDATE_FUNCTION
      return findVerifiedTakeoverAction().state;
    })()
""".trimIndent()

internal val SUBMIT_LMS_SESSION_TAKEOVER_ACTION_SCRIPT = """
    (function(){
      $LMS_SESSION_TAKEOVER_CANDIDATE_FUNCTION
      var result=findVerifiedTakeoverAction();
      if(result.state!=='verified')return result.state;
      if(!result.redirectUrl)return 'unavailable';
      setTimeout(function(){
        location.replace(result.redirectUrl);
      },0);
      return 'submitted';
    })()
""".trimIndent()
private const val PORTAL_HOST = "portal.dima.ac.kr"
private const val MAIN_PATH = "/main/MainView.dunet"
private const val LOGIN_RESULT_TIMEOUT_MILLIS = 10_000L
