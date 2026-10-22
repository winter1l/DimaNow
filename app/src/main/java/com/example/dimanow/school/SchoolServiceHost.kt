package com.example.dimanow.school

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.SafeBrowsingResponse
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dimanow.lms.LmsCredentialStore
import com.example.dimanow.lms.LmsLoginScreen
import com.example.dimanow.lms.SavedLmsCredentials
import com.example.dimanow.lms.lmsCredentialSubmissionScript
import com.example.dimanow.ui.DimaTopAppBar
import com.example.dimanow.ui.LocalOpenSettings
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import org.json.JSONObject

/**
 * Serves [SchoolServiceBridge] requests from the app shell (D-107). Each read runs in its own WebView: it opens
 * the target page; when the site is not signed in it goes through the school portal and enters the saved
 * portal account, or asks for it on the same sign-in form as the Courses tab (the student may save it there).
 * Then it runs the request's script on the target page. The WebView stays invisible; the official portal page
 * is shown only when the portal needs something the app cannot do (a challenge or the duplicate-login notice).
 */
@Composable
fun SchoolServiceHost(bridge: SchoolServiceBridge, credentialStore: LmsCredentialStore) {
    val request by bridge.request.collectAsStateWithLifecycle()
    val current = request ?: return
    key(current) {
        SchoolServiceSession(current, credentialStore) { result -> bridge.complete(current, result) }
    }
}

/** Credentials for one read, filled from the store or the sign-in form, and the WebView's pending submission. */
private class SchoolCredentialSlot {
    var credentials: SavedLmsCredentials? = null
    var submitWhenReady: (() -> Unit)? = null
}

@Composable
private fun SchoolServiceSession(
    request: SchoolServiceRequest,
    credentialStore: LmsCredentialStore,
    onResult: (SchoolServiceResult) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val slot = remember { SchoolCredentialSlot() }
    var credentialsLoaded by remember { mutableStateOf(false) }
    var interactive by remember { mutableStateOf(false) }
    var askCredentials by remember { mutableStateOf(false) }
    var credentialsRejected by remember { mutableStateOf(false) }
    LaunchedEffect(request) {
        slot.credentials = runCatching { credentialStore.load() }.getOrNull()?.takeIf { it.automaticLogin }
        credentialsLoaded = true
    }
    val activity = LocalActivity.current
    DisposableEffect(activity, interactive) {
        // The portal page holds a password field while it is on screen.
        if (interactive) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (interactive) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    // Back on the sign-in form or the official page cancels this read, as the close button does.
    BackHandler(enabled = interactive || askCredentials) { onResult(SchoolServiceResult.Cancelled) }
    if (!credentialsLoaded) return
    val closeButton: @Composable () -> Unit = {
        IconButton(shapes = IconButtonDefaults.shapes(), onClick = { onResult(SchoolServiceResult.Cancelled) }) {
            Icon(Icons.Default.Close, contentDescription = "로그인 취소")
        }
    }
    Box(modifier = if (interactive || askCredentials) Modifier.fillMaxSize() else Modifier.size(1.dp)) {
        Column(
            modifier = if (interactive) {
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .testTag("school_signin_web")
            } else {
                Modifier.size(1.dp)
            },
        ) {
            if (interactive) {
                DimaTopAppBar(
                    title = { Text("학교 포털", style = com.example.dimanow.theme.dimaPageTitleStyle()) },
                    navigationIcon = closeButton,
                    showSettings = false,
                    windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                )
                Text(
                    "학교 포털이 확인을 요청했어요. 이 화면에서 이어서 진행하면 ${request.service.label}의 ${request.purpose}을 불러와요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            AndroidView(
                factory = { context ->
                    schoolServiceWebView(
                        context = context,
                        request = request,
                        slot = slot,
                        onResult = onResult,
                        askForCredentials = { rejected ->
                            credentialsRejected = rejected
                            askCredentials = true
                        },
                        showToStudent = { interactive = true },
                    )
                },
                update = { webView -> webView.visibility = if (interactive) View.VISIBLE else View.INVISIBLE },
                onRelease = { webView ->
                    webView.stopLoading()
                    webView.destroy()
                },
                modifier = if (interactive) Modifier.weight(1f).fillMaxWidth() else Modifier.size(1.dp),
            )
        }
        if (askCredentials) {
            // The same form as the Courses tab: the account goes only to the official portal page, and saving it
            // (opt-in) also signs the Courses tab in next time.
            CompositionLocalProvider(LocalOpenSettings provides null) {
                LmsLoginScreen(
                    needsReview = credentialsRejected,
                    onLogin = { username, password, automatic ->
                        val entered = SavedLmsCredentials(username.trim(), password, automatic)
                        if (automatic) scope.launch { credentialStore.save(entered) }
                        slot.credentials = entered
                        askCredentials = false
                        slot.submitWhenReady?.invoke()
                    },
                    title = "학교 계정 로그인",
                    intro = "로그인하면 ${request.service.label}의 ${request.purpose}을 불러와요.",
                    navigationIcon = closeButton,
                    modifier = Modifier.fillMaxSize().testTag("school_signin"),
                )
            }
        }
    }
}

private enum class SchoolStage { CHECK, SIGN_IN, AFTER_SIGN_IN, DONE }

private fun schoolServiceWebView(
    context: android.content.Context,
    request: SchoolServiceRequest,
    slot: SchoolCredentialSlot,
    onResult: (SchoolServiceResult) -> Unit,
    askForCredentials: (rejected: Boolean) -> Unit,
    showToStudent: () -> Unit,
): WebView = WebView(context).apply {
    visibility = View.INVISIBLE
    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
    if (Build.VERSION.SDK_INT >= 26) WebView.startSafeBrowsing(context, null)

    val service = request.service
    val targetPath = Uri.parse(request.targetUrl).path.orEmpty()
    var stage = SchoolStage.CHECK
    var injected = false
    var visible = false
    var extracting = false
    var redirectsAfterSignIn = 0

    fun finish(result: SchoolServiceResult) {
        if (stage == SchoolStage.DONE) return
        stage = SchoolStage.DONE
        if (result is SchoolServiceResult.Success) CookieManager.getInstance().flush()
        onResult(result)
    }

    fun reveal() {
        if (!visible) {
            visible = true
            showToStudent()
        }
    }

    fun submit(view: WebView, credentials: SavedLmsCredentials) {
        slot.submitWhenReady = null
        injected = true
        view.evaluateJavascript(lmsCredentialSubmissionScript(credentials.username, credentials.password, portal = true)) { result ->
            // A challenge or an unexpected form: the official page takes over.
            if (result != "\"submitted\"") reveal()
        }
    }

    fun submitOrAsk(view: WebView) {
        val credentials = slot.credentials
        if (credentials != null) {
            submit(view, credentials)
        } else {
            slot.submitWhenReady = { slot.credentials?.let { submit(view, it) } }
            askForCredentials(false)
        }
    }

    fun rejectCredentials(view: WebView) {
        injected = false
        slot.credentials = null
        slot.submitWhenReady = { slot.credentials?.let { submit(view, it) } }
        askForCredentials(true)
    }

    fun startSignIn(view: WebView) {
        stage = SchoolStage.SIGN_IN
        injected = false
        view.loadUrl(service.portalEntry)
    }

    fun extract(view: WebView) {
        if (extracting) return
        extracting = true
        view.evaluateJavascript(schoolScriptLauncher(request), null)
        var waited = 0L
        fun poll() {
            if (stage == SchoolStage.DONE) return
            view.evaluateJavascript(schoolScriptPoll(request)) { raw ->
                val text = raw?.takeIf { it != "null" }?.let { runCatching { JSONObject("{\"v\":$it}").getString("v") }.getOrNull() }
                if (text == null) {
                    waited += POLL_MILLIS
                    if (waited >= SCRIPT_TIMEOUT_MILLIS) {
                        finish(SchoolServiceResult.Failure("${service.label}에서 정보를 읽는 데 시간이 너무 오래 걸려요."))
                    } else {
                        view.postDelayed({ poll() }, POLL_MILLIS)
                    }
                    return@evaluateJavascript
                }
                extracting = false
                val answer = runCatching { JSONObject(text) }.getOrNull()
                when (answer?.optString("state")) {
                    "ok" -> finish(SchoolServiceResult.Success(answer.opt("data")?.toString() ?: "null"))
                    "signin" -> if (stage == SchoolStage.CHECK) {
                        startSignIn(view)
                    } else {
                        finish(SchoolServiceResult.Failure("${service.label}에 로그인하지 못했어요. 잠시 후 다시 시도해 주세요."))
                    }
                    else -> finish(
                        SchoolServiceResult.Failure(
                            answer?.optString("message")?.takeIf { it.isNotBlank() && it.length <= 120 }
                                ?: "${service.label}에서 정보를 읽지 못했어요.",
                        ),
                    )
                }
            }
        }
        poll()
    }

    webChromeClient = object : android.webkit.WebChromeClient() {
        override fun onJsAlert(view: WebView, url: String, message: String, result: android.webkit.JsResult): Boolean {
            // The portal answers a wrong account with an alert on its sign-in page.
            result.confirm()
            if (injected && SchoolUrlPolicy.isPortalCredentialPage(url)) rejectCredentials(view)
            return true
        }
    }
    webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, webRequest: WebResourceRequest): WebResourceResponse? {
            val url = webRequest.url.toString()
            val scheme = webRequest.url.scheme?.lowercase()
            return if (scheme in setOf("http", "https") && !SchoolUrlPolicy.isAllowedNavigation(url)) {
                WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0)))
            } else {
                super.shouldInterceptRequest(view, webRequest)
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView, webRequest: WebResourceRequest): Boolean {
            val url = webRequest.url.toString()
            if (SchoolUrlPolicy.isAllowedNavigation(url)) return false
            val upgraded = url.takeIf { it.startsWith("http://") }?.replaceFirst("http://", "https://")
            if (upgraded != null && SchoolUrlPolicy.isAllowedHttps(upgraded)) {
                view.loadUrl(upgraded)
            } else {
                finish(SchoolServiceResult.Failure("학교 사이트가 앱이 열지 않는 주소로 이동했어요 (${SchoolUrlPolicy.describe(url)})."))
            }
            return true
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = Unit

        override fun onPageFinished(view: WebView, url: String) {
            if (stage == SchoolStage.DONE) return
            if (SchoolUrlPolicy.isSessionConflict(url)) {
                // The official page explains the duplicate login and offers its own way on; the student decides.
                reveal()
                return
            }
            val uri = Uri.parse(url)
            when (uri.host) {
                service.host -> {
                    if (uri.path.orEmpty().equals(targetPath, ignoreCase = true)) {
                        extract(view)
                    } else if (stage == SchoolStage.CHECK) {
                        // Not signed in: the site sent us to its error or entry page instead of the target.
                        startSignIn(view)
                    } else if (redirectsAfterSignIn < 2) {
                        // Signed in and landed on the site's front page: go on to the page to read.
                        stage = SchoolStage.AFTER_SIGN_IN
                        redirectsAfterSignIn++
                        view.loadUrl(request.targetUrl)
                    } else {
                        finish(SchoolServiceResult.Failure("${service.label}의 ${request.purpose} 화면을 열지 못했어요 (${SchoolUrlPolicy.describe(url)})."))
                    }
                }
                SchoolUrlPolicy.PORTAL_HOST -> {
                    if (!SchoolUrlPolicy.isPortalCredentialPage(url)) {
                        // A portal page after signing in that did not forward to the service: try the target directly.
                        if (stage == SchoolStage.SIGN_IN && injected && redirectsAfterSignIn < 2) {
                            stage = SchoolStage.AFTER_SIGN_IN
                            redirectsAfterSignIn++
                            view.loadUrl(request.targetUrl)
                        }
                        return
                    }
                    if (stage == SchoolStage.CHECK) stage = SchoolStage.SIGN_IN
                    if (injected) {
                        // Back on the sign-in page after a submission: the account was not accepted.
                        rejectCredentials(view)
                        return
                    }
                    submitOrAsk(view)
                }
                else -> Unit
            }
        }

        override fun onReceivedError(view: WebView, webRequest: WebResourceRequest, error: WebResourceError) {
            if (webRequest.isForMainFrame) {
                finish(SchoolServiceResult.Failure("${service.label}에 연결하지 못했어요. 인터넷 연결을 확인해 주세요."))
            }
        }

        override fun onSafeBrowsingHit(view: WebView, webRequest: WebResourceRequest, threatType: Int, callback: SafeBrowsingResponse) {
            callback.backToSafety(true)
            finish(SchoolServiceResult.Failure("안전하지 않은 페이지라 열지 않았어요."))
        }
    }
    postDelayed({
        // Not while the student is entering the account on the form.
        if (stage != SchoolStage.DONE && !visible && slot.submitWhenReady == null) {
            finish(SchoolServiceResult.Failure("${service.label} 응답이 너무 늦어요. 잠시 후 다시 시도해 주세요."))
        }
    }, SILENT_TIMEOUT_MILLIS)
    loadUrl(request.targetUrl)
}

private const val POLL_MILLIS = 300L
private const val SCRIPT_TIMEOUT_MILLIS = 30_000L
private const val SILENT_TIMEOUT_MILLIS = 60_000L
