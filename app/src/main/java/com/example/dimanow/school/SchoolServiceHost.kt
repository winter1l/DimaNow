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
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dimanow.lms.LmsCredentialStore
import com.example.dimanow.lms.SavedLmsCredentials
import com.example.dimanow.lms.lmsCredentialSubmissionScript
import com.example.dimanow.ui.DimaTopAppBar
import java.io.ByteArrayInputStream
import org.json.JSONObject

/**
 * Serves [SchoolServiceBridge] requests from the app shell (D-107). Each read runs in its own WebView: it opens
 * the target page; when the site is not signed in it goes through the school portal, entering the saved
 * portal sign-in when there is one and otherwise showing the official portal page so the student signs in
 * there; then it runs the request's script on the target page. The WebView stays invisible unless the
 * student has to act, and nothing it reads leaves this request.
 */
@Composable
fun SchoolServiceHost(bridge: SchoolServiceBridge, credentialStore: LmsCredentialStore) {
    val request by bridge.request.collectAsStateWithLifecycle()
    val current = request ?: return
    key(current) {
        SchoolServiceSession(current, credentialStore) { result -> bridge.complete(current, result) }
    }
}

@Composable
private fun SchoolServiceSession(
    request: SchoolServiceRequest,
    credentialStore: LmsCredentialStore,
    onResult: (SchoolServiceResult) -> Unit,
) {
    var credentials by remember { mutableStateOf<SavedLmsCredentials?>(null) }
    var credentialsLoaded by remember { mutableStateOf(false) }
    var interactive by remember { mutableStateOf(false) }
    LaunchedEffect(request) {
        credentials = runCatching { credentialStore.load() }.getOrNull()?.takeIf { it.automaticLogin }
        credentialsLoaded = true
    }
    val activity = LocalActivity.current
    DisposableEffect(activity, interactive) {
        // The portal page holds a password field while it is on screen.
        if (interactive) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (interactive) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    if (!credentialsLoaded) return
    Column(
        modifier = if (interactive) {
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .testTag("school_signin")
        } else {
            Modifier.size(1.dp)
        },
    ) {
        if (interactive) {
            DimaTopAppBar(
                title = { Text("학교 포털 로그인", style = com.example.dimanow.theme.dimaPageTitleStyle()) },
                navigationIcon = {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = { onResult(SchoolServiceResult.Cancelled) }) {
                        Icon(Icons.Default.Close, contentDescription = "로그인 취소")
                    }
                },
                showSettings = false,
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            )
            Text(
                "공식 포털에서 로그인하면 ${request.service.label}의 ${request.purpose}을 불러와요. 입력한 비밀번호는 앱이 보거나 저장하지 않아요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        AndroidView(
            factory = { context -> schoolServiceWebView(context, request, credentials, onResult) { interactive = true } },
            update = { webView -> webView.visibility = if (interactive) View.VISIBLE else View.INVISIBLE },
            onRelease = { webView ->
                webView.stopLoading()
                webView.destroy()
            },
            modifier = if (interactive) Modifier.weight(1f).fillMaxWidth() else Modifier.size(1.dp),
        )
    }
}

private enum class SchoolStage { CHECK, SIGN_IN, AFTER_SIGN_IN, DONE }

private fun schoolServiceWebView(
    context: android.content.Context,
    request: SchoolServiceRequest,
    credentials: SavedLmsCredentials?,
    onResult: (SchoolServiceResult) -> Unit,
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
                    val saved = credentials
                    if (saved == null || injected) {
                        // No saved sign-in, or the portal came back after a submission (wrong password, notice):
                        // the student continues on the official page.
                        reveal()
                        return
                    }
                    injected = true
                    view.evaluateJavascript(lmsCredentialSubmissionScript(saved.username, saved.password, portal = true)) { result ->
                        if (result != "\"submitted\"") reveal()
                    }
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
        if (stage != SchoolStage.DONE && !visible) {
            finish(SchoolServiceResult.Failure("${service.label} 응답이 너무 늦어요. 잠시 후 다시 시도해 주세요."))
        }
    }, SILENT_TIMEOUT_MILLIS)
    loadUrl(request.targetUrl)
}

private const val POLL_MILLIS = 300L
private const val SCRIPT_TIMEOUT_MILLIS = 30_000L
private const val SILENT_TIMEOUT_MILLIS = 60_000L
