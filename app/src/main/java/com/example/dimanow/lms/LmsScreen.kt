package com.example.dimanow.lms

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.SafeBrowsingResponse
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dimanow.ui.ScreenColumn
import com.example.dimanow.ui.motion.expressiveBounceClick
import com.example.dimanow.ui.motion.pulseBreath
import com.example.dimanow.ui.motion.staggeredEntrance
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun LmsRoute(
    credentialStore: LmsCredentialStore,
    sessionController: LmsSessionController,
    loginBridge: LmsLoginBridge,
    autoLoginCoordinator: LmsAutoLoginCoordinator,
    source: LmsSource,
    modifier: Modifier = Modifier,
    onFullScreenChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionState by sessionController.state.collectAsStateWithLifecycle()
    val credentialState by credentialStore.state.collectAsStateWithLifecycle()
    val snapshot by source.snapshot.collectAsStateWithLifecycle(initialValue = LmsSnapshot())
    val loginRequest by loginBridge.request.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var selectedCourse by remember { mutableStateOf<String?>(null) }
    var selectedKind by remember { mutableStateOf<LmsItemKind?>(null) }
    var selectedDetail by remember { mutableStateOf<LmsItemDetail?>(null) }
    BackHandler(enabled = selectedDetail != null) { selectedDetail = null }
    BackHandler(enabled = loginRequest != null) { loginBridge.cancel() }
    DisposableEffect(loginRequest) {
        onDispose {
            if (loginRequest != null) loginBridge.cancel()
        }
    }
    // 로그인 WebView·글 상세가 떠 있는 동안 상위 셸이 하단 내비를 숨기게 알린다 (D-044)
    val fullScreen = loginRequest != null || selectedDetail != null
    LaunchedEffect(fullScreen) { onFullScreenChange(fullScreen) }
    DisposableEffect(Unit) {
        onDispose { onFullScreenChange(false) }
    }

    suspend fun loginAndRefresh(force: Boolean) {
        val state = autoLoginCoordinator.ensureActive(force)
        if (state == LmsSessionState.ACTIVE) {
            when (val result = source.refresh(force = force)) {
                LmsRefreshResult.SessionExpired -> {
                    autoLoginCoordinator.markExpired()
                    if (autoLoginCoordinator.ensureActive(force = true) == LmsSessionState.ACTIVE) source.refresh(force = true)
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
                onCancel = { loginBridge.cancel() },
                modifier = Modifier.fillMaxSize(),
            )
            selectedDetail != null -> LmsDetailScreen(
                detail = requireNotNull(selectedDetail),
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
                onCourseChange = { selectedCourse = it },
                onKindChange = { selectedKind = it },
                onRefresh = { scope.launch { loginAndRefresh(force = true) } },
                onOpenItem = { item ->
                    if (item.kind == LmsItemKind.OTHER) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.detailUrl)))
                    } else {
                        scope.launch {
                            val detail = source.loadDetail(item)
                            if (detail == null) {
                                autoLoginCoordinator.markExpired()
                                if (autoLoginCoordinator.ensureActive(force = true) == LmsSessionState.ACTIVE) {
                                    selectedDetail = source.loadDetail(item, force = true)
                                }
                            } else selectedDetail = detail
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

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
private fun LmsItemsScreen(
    snapshot: LmsSnapshot,
    sessionState: LmsSessionState,
    selectedCourse: String?,
    selectedKind: LmsItemKind?,
    onCourseChange: (String?) -> Unit,
    onKindChange: (LmsItemKind?) -> Unit,
    onRefresh: () -> Unit,
    onOpenItem: (LmsItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = snapshot.items.filter { item ->
        (selectedCourse == null || item.courseId == selectedCourse) &&
            (selectedKind == null || item.kind == selectedKind)
    }
    val filterActive = selectedCourse != null || selectedKind != null
    val syncing = snapshot.syncState == LmsSyncState.SYNCING
    val hasError = sessionState == LmsSessionState.ERROR || snapshot.syncState == LmsSyncState.ERROR

    ScreenColumn(
        title = "수업",
        modifier = modifier,
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
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.staggeredEntrance(0),
        ) {
            item { FilterChip(selected = selectedCourse == null, onClick = { onCourseChange(null) }, label = { Text("전체") }, shape = RoundedCornerShape(10.dp)) }
            items(snapshot.courses, key = { it.id }) { course ->
                FilterChip(selected = selectedCourse == course.id, onClick = { onCourseChange(course.id) }, label = { Text(course.name) }, shape = RoundedCornerShape(10.dp))
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.staggeredEntrance(1),
        ) {
            item { FilterChip(selected = selectedKind == null, onClick = { onKindChange(null) }, label = { Text("전체") }, shape = RoundedCornerShape(10.dp)) }
            items(listOf(LmsItemKind.NOTICE, LmsItemKind.ASSIGNMENT, LmsItemKind.MATERIAL)) { kind ->
                FilterChip(selected = selectedKind == kind, onClick = { onKindChange(kind) }, label = { Text(kindLabel(kind)) }, shape = RoundedCornerShape(10.dp))
            }
        }

        when {
            filtered.isEmpty() && syncing -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp)
                        .staggeredEntrance(2),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CircularProgressIndicator(strokeWidth = 3.dp, strokeCap = StrokeCap.Round)
                    Text("수업 정보를 불러오는 중", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            filtered.isEmpty() && hasError -> {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .staggeredEntrance(2),
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
            filtered.isEmpty() -> {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .staggeredEntrance(2),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = if (filterActive) "필터에 해당하는 항목이 없습니다" else "표시할 항목이 없습니다",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (filterActive) {
                            TextButton(onClick = { onCourseChange(null); onKindChange(null) }) {
                                Text("필터 해제", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            else -> {
                var entranceIndex = 2
                filtered.forEach { item ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggeredEntrance(entranceIndex++)
                            .expressiveBounceClick { onOpenItem(item) },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        text = kindLabel(item.kind),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                Text(
                                    text = item.courseName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
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
            }
        }
    }
}

@Composable
private fun LmsDetailScreen(
    detail: LmsItemDetail,
    source: LmsSource,
    sessionController: LmsSessionController,
    autoLoginCoordinator: LmsAutoLoginCoordinator,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<LmsAttachment?>(null) }
    var downloading by remember { mutableStateOf(false) }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val attachment = pending
        pending = null
        if (uri != null && attachment != null) {
            scope.launch {
                downloading = true
                val cache = File(context.cacheDir, "lms-attachments/${System.nanoTime()}-${attachment.fileName}").apply { parentFile?.mkdirs() }
                var result = source.downloadAttachment(attachment, cache)
                if (result is LmsRefreshResult.Failure && result.message.contains("로그인 세션")) {
                    sessionController.transition(LmsSessionState.EXPIRED)
                    if (autoLoginCoordinator.ensureActive(force = true) == LmsSessionState.ACTIVE) {
                        result = source.downloadAttachment(attachment, cache)
                    }
                }
                if (result is LmsRefreshResult.Success) {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { output -> cache.inputStream().use { it.copyTo(output) } }
                        cache.delete()
                    }
                    onMessage("저장했습니다")
                } else onMessage((result as? LmsRefreshResult.Failure)?.message ?: "저장하지 못했습니다")
                downloading = false
            }
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
        AndroidView(
            factory = { viewContext ->
                WebView(viewContext).apply {
                    settings.javaScriptEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.domStorageEnabled = false
                    loadDataWithBaseURL("https://lms.dima.ac.kr", detail.sanitizedHtml, "text/html", "UTF-8", null)
                }
            },
            update = { it.loadDataWithBaseURL("https://lms.dima.ac.kr", detail.sanitizedHtml, "text/html", "UTF-8", null) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        if (detail.attachments.isNotEmpty()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                detail.attachments.forEach { attachment ->
                    OutlinedButton(
                        onClick = { pending = attachment; createDocument.launch(attachment.fileName) },
                        enabled = !downloading,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Icon(Icons.Default.Download, null); Spacer(Modifier.size(8.dp)); Text(attachment.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@Composable
private fun LmsAuthenticationWebView(
    request: LmsLoginRequest,
    onComplete: (LmsLoginResult) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current
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
                text = "공식 LMS에서 로그인 중",
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
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    if (Build.VERSION.SDK_INT >= 26) WebView.startSafeBrowsing(context, null)
                    var injected = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, webRequest: WebResourceRequest): Boolean {
                            val uri = webRequest.url
                            val allowed = LmsUrlPolicy.isAllowed(uri.toString())
                            if (!allowed) context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            return !allowed
                        }

                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            if (Uri.parse(url).path == SUCCESS_PATH) onComplete(LmsLoginResult.Success)
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            val path = Uri.parse(url).path.orEmpty()
                            if (path == SUCCESS_PATH) {
                                onComplete(LmsLoginResult.Success)
                            } else if (path == LOGIN_PATH && !injected) {
                                injected = true
                                val user = JSONObject.quote(request.credentials.username)
                                val password = JSONObject.quote(request.credentials.password)
                                view.evaluateJavascript(
                                    "(function(){var i=document.querySelector('#id'),p=document.querySelector('#pass');" +
                                        "if(!i||!p||typeof login_proc!=='function')return 'interactive';" +
                                        "i.value=$user;p.value=$password;login_proc();return 'submitted';})()",
                                ) { _ -> Unit }
                            }
                        }

                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) onComplete(LmsLoginResult.NetworkError(error.description.toString()))
                        }

                        override fun onSafeBrowsingHit(view: WebView, request: WebResourceRequest, threatType: Int, callback: SafeBrowsingResponse) {
                            callback.backToSafety(true)
                            onComplete(LmsLoginResult.Failure("안전하지 않은 페이지가 차단되었습니다"))
                        }
                    }
                    loadUrl(LOGIN_URL)
                }
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

private fun kindLabel(kind: LmsItemKind): String = when (kind) {
    LmsItemKind.NOTICE -> "공지"
    LmsItemKind.ASSIGNMENT -> "과제"
    LmsItemKind.MATERIAL -> "자료"
    LmsItemKind.OTHER -> "기타"
}

private val SEOUL = ZoneId.of("Asia/Seoul")
private val LMS_TIME = DateTimeFormatter.ofPattern("M월 d일 HH:mm")
private const val LOGIN_URL = "https://lms.dima.ac.kr/login/doLoginPage.dunet"
private const val LOGIN_PATH = "/login/doLoginPage.dunet"
private const val SUCCESS_PATH = "/lms/myLecture/doListView.dunet"
