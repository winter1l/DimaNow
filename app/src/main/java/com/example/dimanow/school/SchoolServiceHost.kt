package com.example.dimanow.school

import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.Devices
import com.example.dimanow.lms.LmsCredentialStore
import com.example.dimanow.lms.LmsLoginScreen
import com.example.dimanow.lms.PortalSignInGate
import com.example.dimanow.ui.DialogTone
import com.example.dimanow.ui.DimaAlertDialog
import com.example.dimanow.lms.SavedLmsCredentials
import com.example.dimanow.lms.lmsCredentialSubmissionScript
import com.example.dimanow.ui.DimaLoadingIndicator
import com.example.dimanow.ui.DimaTopAppBar
import com.example.dimanow.ui.openUrl
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import org.json.JSONObject

/**
 * Serves [SchoolServiceBridge] requests from the app shell (D-107). Each read runs in its own WebView: it opens
 * the target page; when the site is not signed in it goes through the school portal and enters the saved
 * portal account, or asks for it on the same sign-in form as the Courses tab (the student may save it there).
 * Then it runs the request's script on the target page. The WebView stays invisible; the official portal page
 * is shown only when the portal needs something the app cannot do (a challenge or the duplicate-login notice).
 * A request without a script (the outing application) opens full screen with a loading state instead and then
 * shows the signed-in page itself, which the student uses as on the school site.
 */
@Composable
fun SchoolServiceHost(bridge: SchoolServiceBridge, credentialStore: LmsCredentialStore) {
    val request by bridge.request.collectAsStateWithLifecycle()
    val current = request ?: return
    key(current) {
        SchoolServiceSession(current, credentialStore, bridge.portalGate) { result -> bridge.complete(current, result) }
    }
}

/**
 * Credentials for one visit, filled from the store or the sign-in form, the WebView's pending submission, and
 * the WebView itself for Back on a shown page.
 */
private class SchoolCredentialSlot {
    var credentials: SavedLmsCredentials? = null
    var submitWhenReady: (() -> Unit)? = null
    var webView: WebView? = null
}

/** What the session shows: nothing (a read), a loading page (a page being opened), the portal, or the page. */
private enum class SchoolSurface { HIDDEN, OPENING, PORTAL, PAGE }

@Composable
private fun SchoolServiceSession(
    request: SchoolServiceRequest,
    credentialStore: LmsCredentialStore,
    portalGate: PortalSignInGate?,
    onResult: (SchoolServiceResult) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val slot = remember { SchoolCredentialSlot() }
    // D-135: the sign-in waiting for the student's answer while another device most likely holds the portal sign-in.
    var confirmSignIn by remember { mutableStateOf<(() -> Unit)?>(null) }
    var credentialsLoaded by remember { mutableStateOf(false) }
    var surface by remember { mutableStateOf(if (request.opensPage) SchoolSurface.OPENING else SchoolSurface.HIDDEN) }
    var askCredentials by remember { mutableStateOf(false) }
    var credentialsRejected by remember { mutableStateOf(false) }
    LaunchedEffect(request) {
        slot.credentials = runCatching { credentialStore.load() }.getOrNull()?.takeIf { it.automaticLogin }
        credentialsLoaded = true
    }
    val activity = LocalActivity.current
    val portalShown = surface == SchoolSurface.PORTAL
    DisposableEffect(activity, portalShown) {
        // The portal page holds a password field while it is on screen.
        if (portalShown) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (portalShown) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    val onScreen = surface != SchoolSurface.HIDDEN
    // Back steps back through the shown page's own history; otherwise it closes, as the close button does.
    BackHandler(enabled = onScreen || askCredentials) {
        val webView = slot.webView
        if (!askCredentials && surface == SchoolSurface.PAGE && webView?.canGoBack() == true) {
            webView.goBack()
        } else {
            onResult(SchoolServiceResult.Cancelled)
        }
    }
    if (!credentialsLoaded) return
    val goal = request.service.label + "의 " +
        if (request.opensPage) "${request.purpose} 화면을 열어요" else "${withObjectParticle(request.purpose)} 불러와요"
    val closeButton: @Composable () -> Unit = {
        IconButton(shapes = IconButtonDefaults.shapes(), onClick = { onResult(SchoolServiceResult.Cancelled) }) {
            Icon(Icons.Default.Close, contentDescription = if (request.opensPage) "닫기" else "로그인 취소")
        }
    }
    Box(modifier = if (onScreen || askCredentials) Modifier.fillMaxSize() else Modifier.size(1.dp)) {
        Column(
            modifier = if (onScreen) {
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .testTag(if (portalShown) "school_signin_web" else "school_page")
            } else {
                Modifier.size(1.dp)
            },
        ) {
            if (onScreen) {
                DimaTopAppBar(
                    title = {
                        Text(
                            if (portalShown) "학교 포털" else request.purpose,
                            style = com.example.dimanow.theme.dimaPageTitleStyle(),
                        )
                    },
                    navigationIcon = closeButton,
                    windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                )
            }
            if (portalShown) {
                Text(
                    "학교 포털이 확인을 요청했어요. 이 화면에서 이어서 진행하면 $goal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Box(modifier = if (onScreen) Modifier.weight(1f).fillMaxWidth() else Modifier.size(1.dp)) {
                AndroidView(
                    factory = { context ->
                        schoolServiceWebView(
                            context = context,
                            request = request,
                            slot = slot,
                            portalGate = portalGate,
                            onResult = onResult,
                            confirmSignIn = { signIn -> confirmSignIn = signIn },
                            askForCredentials = { rejected ->
                                credentialsRejected = rejected
                                askCredentials = true
                            },
                            showToStudent = { if (surface != SchoolSurface.PAGE) surface = SchoolSurface.PORTAL },
                            showPage = { surface = SchoolSurface.PAGE },
                        ).also { slot.webView = it }
                    },
                    update = { webView ->
                        webView.visibility = if (portalShown || surface == SchoolSurface.PAGE) View.VISIBLE else View.INVISIBLE
                    },
                    onRelease = { webView ->
                        slot.webView = null
                        webView.stopLoading()
                        webView.destroy()
                    },
                    modifier = if (onScreen) Modifier.fillMaxSize() else Modifier.size(1.dp),
                )
                if (surface == SchoolSurface.OPENING) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag("school_page_opening"),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        DimaLoadingIndicator()
                        Text(
                            "${request.service.label}에 로그인하고 있어요",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        confirmSignIn?.let { signIn ->
            fun cancel() {
                confirmSignIn = null
                slot.submitWhenReady = null
                onResult(SchoolServiceResult.Cancelled)
            }
            DimaAlertDialog(
                onDismissRequest = ::cancel,
                icon = Icons.Default.Devices,
                tone = DialogTone.WARNING,
                title = "다른 곳에서 학교 계정을 쓰는 것 같아요",
                text = {
                    Text("로그인하면 그곳의 로그인이 끊겨서, 풀고 있던 시험이나 과제가 저장되지 않을 수 있어요.")
                },
                confirmLabel = "로그인",
                onConfirm = {
                    confirmSignIn = null
                    signIn()
                },
                dismissLabel = "취소",
                confirmTag = "school_sign_in_here",
                dismissTag = "school_sign_in_cancel",
            )
        }
        if (askCredentials) {
            // The same form as the Courses tab: the account goes only to the official portal page, and saving it
            // (opt-in) also signs the Courses tab in next time.
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
                intro = "로그인하면 $goal.",
                navigationIcon = closeButton,
                modifier = Modifier.fillMaxSize().testTag("school_signin"),
            )
        }
    }
}

/** SHOWN: a page opened for the student is on screen and the student, not the app, drives it. */
private enum class SchoolStage { CHECK, SIGN_IN, AFTER_SIGN_IN, SHOWN, DONE }

private fun schoolServiceWebView(
    context: android.content.Context,
    request: SchoolServiceRequest,
    slot: SchoolCredentialSlot,
    portalGate: PortalSignInGate?,
    onResult: (SchoolServiceResult) -> Unit,
    confirmSignIn: (signIn: () -> Unit) -> Unit,
    askForCredentials: (rejected: Boolean) -> Unit,
    showToStudent: () -> Unit,
    showPage: () -> Unit,
): WebView = WebView(context).apply {
    visibility = View.INVISIBLE
    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
    if (request.opensPage) {
        // The school's desktop pages are wider than a phone: the student can pinch to zoom.
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
    }
    WebView.startSafeBrowsing(context, null)

    val service = request.service
    val targetPath = Uri.parse(request.targetUrl).path.orEmpty()
    var stage = SchoolStage.CHECK
    var injected = false
    var visible = false
    // Read from the WebView's network thread: while nothing is on screen, page decoration is not fetched.
    val onScreen = java.util.concurrent.atomic.AtomicBoolean(request.opensPage)
    var extracting = false
    var redirectsAfterSignIn = 0
    // D-135: a 3045 answer already signed the other place out, so one more sign-in goes through without asking.
    var conflictRetried = false
    var signInApproved = false
    // The account went to the portal on this visit; reaching the page then means the portal sign-in is the app's.
    var accountSent = false

    fun finish(result: SchoolServiceResult) {
        if (stage == SchoolStage.DONE) return
        stage = SchoolStage.DONE
        if (result is SchoolServiceResult.Success) CookieManager.getInstance().flush()
        onResult(result)
    }

    fun reveal() {
        if (!visible) {
            visible = true
            onScreen.set(true)
            showToStudent()
        }
    }

    fun submit(view: WebView, credentials: SavedLmsCredentials) {
        slot.submitWhenReady = null
        injected = true
        accountSent = true
        view.evaluateJavascript(lmsCredentialSubmissionScript(credentials.username, credentials.password, portal = true)) { result ->
            // A challenge or an unexpected form: the official page takes over.
            if (result != "\"submitted\"") reveal()
        }
    }

    fun submitOrAsk(view: WebView) {
        val credentials = slot.credentials
        val step = schoolSignInStep(
            hasSavedAccount = credentials != null,
            signedInElsewhere = portalGate?.signedInElsewhere?.value == true,
            approved = signInApproved,
            automatic = request.automatic,
        )
        when (step) {
            SchoolSignInStep.SUBMIT -> submit(view, requireNotNull(credentials))
            SchoolSignInStep.ASK_ACCOUNT -> {
                slot.submitWhenReady = { slot.credentials?.let { submit(view, it) } }
                askForCredentials(false)
            }
            SchoolSignInStep.STOP -> finish(SchoolServiceResult.Failure(SCHOOL_SIGNED_IN_ELSEWHERE_MESSAGE))
            SchoolSignInStep.CONFIRM -> {
                val signIn = {
                    signInApproved = true
                    portalGate?.allowSignInHere()
                    submit(view, requireNotNull(credentials))
                }
                // Held while the student answers, so the silent timeout waits as it does for the sign-in form.
                slot.submitWhenReady = signIn
                confirmSignIn(signIn)
            }
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

    fun focusPage(view: WebView) {
        if (!request.fitsPage) return
        service.pageFocusScript?.let { view.evaluateJavascript(it, null) }
    }

    fun showPage(view: WebView) {
        stage = SchoolStage.SHOWN
        // Back from the first page closes it rather than returning to the sign-in hops.
        view.clearHistory()
        focusPage(view)
        showPage()
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
                                // The page's own request failed ("Failed to fetch"): the site did not answer.
                                ?.let { if (isNetworkFailure(it)) "${service.label}에 연결하지 못했어요. 잠시 후 다시 시도해 주세요." else it }
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
            // A shown page's own notices are for the student, in the WebView's usual dialog.
            if (stage == SchoolStage.SHOWN) return super.onJsAlert(view, url, message, result)
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
            } else if (!onScreen.get() && !webRequest.isForMainFrame && webRequest.url.host == service.host &&
                (isPageDecoration(webRequest.url.path) || service.hiddenReadSkipsPageScripts &&
                    isPageScript(webRequest.url.path) && isRequestFromPage(refererOf(webRequest), service.host, targetPath))
            ) {
                // An invisible read needs the site's data, not its images, fonts or styles, nor on DIMA C the target
                // page's own scripts: fewer requests per read (2026-10-23: DIMA C twice refused the phone after
                // repeated reads; Q-031). Portal pages load whole, since a challenge on them may be shown.
                WebResourceResponse("text/plain", "UTF-8", 204, "No Content", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0)))
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
            } else if (stage == SchoolStage.SHOWN) {
                // A link off the school sites on a shown page opens in the browser; the page stays.
                if (webRequest.url.scheme?.lowercase() in setOf("http", "https")) openUrl(view.context, url)
            } else {
                finish(SchoolServiceResult.Failure("학교 사이트가 앱이 열지 않는 주소로 이동했어요 (${SchoolUrlPolicy.describe(url)})."))
            }
            return true
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = Unit

        override fun onPageFinished(view: WebView, url: String) {
            if (stage == SchoolStage.SHOWN) {
                // The student moved on within the site (a postback or a link): fit that page as well.
                if (Uri.parse(url).host == service.host) focusPage(view)
                return
            }
            if (stage == SchoolStage.DONE) return
            if (SchoolUrlPolicy.isSessionConflict(url)) {
                if (!conflictRetried) {
                    // D-135: the portal ended the sign-in held elsewhere as it answered 3045. Its page stays blank
                    // (its script does not run), so the account is sent once more.
                    conflictRetried = true
                    signInApproved = true
                    portalGate?.signedInThroughPortal()
                    android.widget.Toast.makeText(
                        view.context,
                        "로그인하면서 다른 곳의 학교 계정 로그인이 끊겼어요",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                    startSignIn(view)
                } else {
                    finish(SchoolServiceResult.Failure("다른 곳에서 다시 로그인해서 ${service.label}에 로그인하지 못했어요. 잠시 후 다시 시도해 주세요."))
                }
                return
            }
            val uri = Uri.parse(url)
            when (uri.host) {
                service.host -> {
                    if (service.selfForwardingSsoPath?.equals(uri.path.orEmpty(), ignoreCase = true) == true) {
                        // The site's SSO entry finishes the sign-in and moves on by itself.
                        return
                    }
                    if (uri.path.orEmpty().equals(targetPath, ignoreCase = true)) {
                        // D-135: the 수업 tab then signs in as its own, without waiting on a sign-in this one ended.
                        if (accountSent) {
                            accountSent = false
                            portalGate?.signedInThroughPortal()
                        }
                        if (request.opensPage) showPage(view) else extract(view)
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
            // On a shown page the WebView's own error page stays, so the student can go back or close.
            if (webRequest.isForMainFrame && stage != SchoolStage.SHOWN) {
                // The host says which site did not answer (2026-10-23: only DIMA C refused the phone's network).
                val host = webRequest.url.host ?: service.host
                finish(SchoolServiceResult.Failure("${service.label}에 연결하지 못했어요 ($host). 잠시 후 다시 시도하거나 인터넷 연결을 확인해 주세요."))
            }
        }

        override fun onSafeBrowsingHit(view: WebView, webRequest: WebResourceRequest, threatType: Int, callback: SafeBrowsingResponse) {
            callback.backToSafety(true)
            finish(SchoolServiceResult.Failure("안전하지 않은 페이지라 열지 않았어요."))
        }
    }
    postDelayed({
        // Not while the student is entering the account on the form.
        if (stage != SchoolStage.DONE && stage != SchoolStage.SHOWN && !visible && slot.submitWhenReady == null) {
            finish(SchoolServiceResult.Failure("${service.label} 응답이 너무 늦어요. 잠시 후 다시 시도해 주세요."))
        }
    }, SILENT_TIMEOUT_MILLIS)
    loadUrl(request.targetUrl)
}

private const val POLL_MILLIS = 300L
private const val SCRIPT_TIMEOUT_MILLIS = 30_000L
private const val SILENT_TIMEOUT_MILLIS = 60_000L

private val DECORATION = Regex("""\.(png|jpe?g|gif|webp|svg|ico|bmp|woff2?|ttf|otf|eot|css|mp4|webm|mp3)$""", RegexOption.IGNORE_CASE)

/** Images, fonts, styles and media, which a hidden read does not need. */
internal fun isPageDecoration(path: String?): Boolean = path != null && DECORATION.containsMatchIn(path)

private val PAGE_SCRIPT = Regex("""\.js$""", RegexOption.IGNORE_CASE)

/** A script file the page loads. */
internal fun isPageScript(path: String?): Boolean = path != null && PAGE_SCRIPT.containsMatchIn(path)

private fun refererOf(request: WebResourceRequest): String? =
    request.requestHeaders.entries.firstOrNull { it.key.equals("Referer", ignoreCase = true) }?.value

/**
 * Whether a request was made by the page at [path] on [host], going by its Referer. Without a Referer it is not
 * counted as the page's, so a sign-in hop's scripts are never held back.
 */
internal fun isRequestFromPage(referer: String?, host: String, path: String): Boolean {
    val uri = referer?.let { runCatching { java.net.URI(it) }.getOrNull() } ?: return false
    return uri.host.equals(host, ignoreCase = true) && uri.path.orEmpty().equals(path, ignoreCase = true)
}

/** A script error that means the site did not answer, as the WebView words it. */
internal fun isNetworkFailure(message: String): Boolean =
    message.contains("Failed to fetch", ignoreCase = true) || message.contains("NetworkError", ignoreCase = true) ||
        message.contains("Load failed", ignoreCase = true)
