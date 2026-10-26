package com.example.dimanow.lms

import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ButtonDefaults
import android.graphics.Bitmap
import android.net.Uri
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
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.example.dimanow.theme.DimaShapes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.dimanow.theme.DimaTheme
import com.example.dimanow.ui.DimaLayout
import com.example.dimanow.ui.ScreenScaffold

@Composable
internal fun LmsLoginScreen(
    needsReview: Boolean,
    onLogin: (String, String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "수업",
    intro: String = "과제·강의 마감과 수업 공지를 이 탭에서 모아 볼 수 있어요.",
    navigationIcon: @Composable () -> Unit = {},
) {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    // D-099: 계정 저장은 사용자가 직접 켜야 한다
    var automatic by remember { mutableStateOf(false) }
    val canLogin = username.isNotBlank() && password.isNotEmpty()
    ScreenScaffold(
        title = title,
        modifier = modifier.imePadding(),
        navigationIcon = navigationIcon,
        itemSpacing = DimaLayout.sectionGap,
        listTag = "lms_login_list",
    ) {
        item(key = "lms_login_form") {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // D-102: 로그인을 묻기 전에 무엇을 얻는지 한 줄로 먼저 말한다 (HIG 계정, 토스 로그인 안내)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("학교 계정으로 로그인", style = MaterialTheme.typography.titleMedium)
                        Text(
                            intro,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (needsReview) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = DimaShapes.Tile,
                            color = MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Text(
                                text = "계정 정보를 다시 확인해 주세요.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                    // D-102: 키보드의 다음·완료로 이어서 입력하고, 비밀번호는 눌러서 확인할 수 있다 (M3 텍스트 필드)
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("학번") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth().testTag("lms_username").semantics { contentType = ContentType.Username },
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("비밀번호") },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (canLogin) onLogin(username, password, automatic) }),
                        trailingIcon = {
                            IconButton(shapes = IconButtonDefaults.shapes(), onClick = { passwordVisible = !passwordVisible }, modifier = Modifier.testTag("lms_password_visibility")) {
                                Icon(
                                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "비밀번호 숨기기" else "비밀번호 보기",
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("lms_password").semantics { contentType = ContentType.Password },
                    )
                    Row(
                        Modifier.fillMaxWidth().toggleable(value = automatic, role = Role.Switch, onValueChange = { automatic = it }).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("자동 로그인 (선택)")
                            // D-107: 저장한 계정이 인트라넷·DIMA C·도서관 읽기에도 쓰인다는 것을 켜기 전에 알린다
                            // (D-134: 로그인 전 안내에서 이 스위치 설명으로 옮겼다)
                            Text(
                                "켜면 학번과 비밀번호를 이 기기에만 암호화해 저장하고, 수업 탭과 장비 대여·인트라넷 시간표·예인관 상벌점·" +
                                    "외출·외박 신청·도서관 화면을 열 때 같은 포털 로그인으로 인트라넷·DIMA C·도서관" +
                                    "(intranet·udimac·lib.dima.ac.kr)에 자동으로 로그인해요. 설정의 수업 계정에서 언제든 지울 수 있어요.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = automatic, onCheckedChange = null)
                    }
                    // D-099: 비공식 앱, 전송 경로와 중복 로그인을 로그인 전에 알린다. D-102: 항목별로 나눠 읽기 쉽게 한다.
                    // D-134: 입력 칸이 폰 첫 화면에 보이도록 칸과 자동 로그인 뒤, 로그인 버튼 바로 앞에 둔다.
                    Surface(shape = DimaShapes.Tile, color = DimaTheme.colors.card) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp).testTag("lms_consent"),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("로그인 전에 확인해 주세요", style = MaterialTheme.typography.labelLarge)
                            LmsNoticeLine("DIMA Now는 학교가 만든 앱이 아니에요.")
                            LmsNoticeLine("학번과 비밀번호는 학교 공식 로그인 화면(portal·lms.dima.ac.kr)에만 입력되고, 개발자나 다른 서버로 보내지 않아요.")
                            LmsNoticeLine("학교 통합 로그인 중 한 구간(sso.dima.ac.kr:8080)은 학교가 암호화하지 않은 연결을 써서, 공용 Wi-Fi에서는 로그인 정보가 노출될 수 있어요.")
                            // D-135: the portal ends the other sign-in as soon as this one is sent, so the app
                            // asks only when another device has just taken the sign-in over.
                            LmsNoticeLine("학교 계정은 한 곳에서만 로그인돼요. 앱이 로그인하면 PC 등 다른 곳의 로그인이 끊겨요. 다른 곳에서 방금 로그인한 것 같으면 먼저 물어봐요.")
                        }
                    }
                    Button(shapes = ButtonDefaults.shapes(), 
                        onClick = { onLogin(username, password, automatic) },
                        enabled = canLogin,
                        modifier = Modifier.fillMaxWidth().testTag("lms_login"),
                    ) { Text("로그인") }
                }
            }
        }
    }
}

/** One bullet of the pre-login notice. */
@Composable
private fun LmsNoticeLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // The bullet is decoration; screen readers read only the sentence.
        Text(
            "•",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics { },
        )
        com.example.dimanow.ui.KoreanText(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LmsAuthenticationFrame(
    silent: Boolean,
    onCancel: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    if (silent) {
        Box(Modifier.size(1.dp)) { content() }
        return
    }
    LmsFullScreenPane(
        title = "로그인",
        onBack = onCancel,
        modifier = modifier,
        backDescription = "로그인 취소",
    ) {
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
                com.example.dimanow.ui.DimaLoadingIndicator()
                Text("공식 포털에서 로그인 중", style = MaterialTheme.typography.titleSmall)
            }
            content()
        }
    }
}

@Composable
internal fun LmsAuthenticationWebView(
    request: LmsLoginRequest,
    onComplete: (LmsLoginResult) -> Unit,
    /** The courses read after signing in, and how the sign-in went ([LmsLoginResult.signedIn]). */
    onAuthenticated: (List<LmsCourse>, LmsLoginResult) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    silent: Boolean = false,
) {
    val activity = LocalActivity.current
    val parser = remember { LmsHtmlParser() }
    DisposableEffect(activity, silent) {
        if (!silent) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (!silent) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    LmsAuthenticationFrame(silent, onCancel, modifier) {
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
                        WebView.startSafeBrowsing(context, null)
                        var injected = false
                        // Each credential submission's own number: a 10 s check from an earlier one must not judge
                        // the sign-in sent again after a 3045 answer (D-135).
                        var submission = 0
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
                                LmsLoginFlowCommand.RetryCredentials -> {
                                    // D-135: the 3045 answer already ended the sign-in elsewhere. The official login
                                    // address takes the account once more; the 3045 page's own form re-encodes its
                                    // query and its script does not run, so it is not used.
                                    injected = false
                                    webView.loadUrl(LOGIN_URL)
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
                                    if (command.result.signedIn && extractedCourses != null) {
                                        finished = true
                                        CookieManager.getInstance().flush()
                                        onAuthenticated(extractedCourses, command.result)
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
                                    // A notice on the 3045 page changes nothing: the page's load drives the retry.
                                    result.confirm()
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
                                        // The answer belongs to this page only while the WebView is still on it.
                                        // After the dashboard load has started, a late or repeated callback for
                                        // the main page reads a document in transition and must be ignored.
                                        if (Uri.parse(view.url.orEmpty()).path != MAIN_PATH) return@evaluateJavascript
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
                                            advance(view, LmsLoginFlowEvent.LoginNotEstablished)
                                        }
                                    }
                                    return
                                } else if (path == "/lms/myLecture/doListView.dunet") {
                                    advance(view, LmsLoginFlowEvent.MainFrameFinished(url))
                                    return
                                } else if (isOfficialLmsCredentialPage(url) && !injected) {
                                    injected = true
                                    val mine = ++submission
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
                                                        mine == submission &&
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
                                                            advance(view, LmsLoginFlowEvent.LoginNotEstablished)
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
                                finish(LmsLoginResult.Failure(LmsUserMessages.UNSAFE_PAGE_BLOCKED))
                            }
                        }
                        advance(this, LmsLoginFlowEvent.CredentialsAvailable)
                    }
                },
                onRelease = { webView ->
                    webView.stopLoading()
                    webView.destroy()
                },
                modifier = Modifier.size(1.dp),
            )
    }
}

/** Exact `protocol//host/pathname` values on which stored credentials may be written. */
internal val PORTAL_CREDENTIAL_PAGES = listOf(
    "https://portal.dima.ac.kr/",
    "https://portal.dima.ac.kr/default.aspx",
)
internal val LMS_CREDENTIAL_PAGES = listOf("https://lms.dima.ac.kr/login/doLoginPage.dunet")

/**
 * First statement of the credential script. The Kotlin caller already checked the
 * onPageFinished URL, but the document may have navigated since then (D-073 allows an HTTP
 * SSO hop in this WebView), so the script re-checks the live document location itself before
 * touching any field or calling the site's login function. Only `===` comparisons on the
 * unforgeable `location` object are used, so page-overridable prototypes cannot widen it.
 */
internal fun lmsCredentialPageGuard(portal: Boolean): String {
    val allowed = if (portal) PORTAL_CREDENTIAL_PAGES else LMS_CREDENTIAL_PAGES
    val condition = allowed.joinToString("||") { "page===${jsStringLiteral(it)}" }
    return "var page=location.protocol+'//'+location.host+location.pathname;" +
        "if(!($condition))return 'origin-mismatch';"
}

/** JavaScript string literal for an arbitrary value (pure Kotlin so JVM unit tests can build scripts). */
internal fun jsStringLiteral(value: String): String = buildString(value.length + 2) {
    append('"')
    value.forEach { character ->
        when (character) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '<', '>', ' ', ' ' -> append("\\u%04x".format(character.code))
            else -> if (character < ' ' || character == '\u007f') {
                append("\\u%04x".format(character.code))
            } else {
                append(character)
            }
        }
    }
    append('"')
}

internal fun lmsCredentialSubmissionScript(
    username: String,
    password: String,
    portal: Boolean,
): String {
    val user = jsStringLiteral(username)
    val secret = jsStringLiteral(password)
    val guard = lmsCredentialPageGuard(portal)
    val userSelector = if (portal) "#txtID" else "#id"
    val passwordSelector = if (portal) "#txtPwd" else "#pass"
    val submitCheck = if (portal) "typeof Login==='function'" else "typeof login_proc==='function'"
    val submit = if (portal) "Login('N')" else "login_proc()"
    return """
        (function(){
          $guard
          var i=document.querySelector('$userSelector'),p=document.querySelector('$passwordSelector');
          if(!i||!p||!($submitCheck))return 'interactive';
          function isHidden(control){
            if(control.tagName==='INPUT'&&control.type==='hidden')return true;
            for(var node=control;node;node=node.parentElement){
              if(node.hidden)return true;
              var style=window.getComputedStyle(node);
              if(style.display==='none'||style.visibility==='hidden')return true;
            }
            return false;
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
private const val PORTAL_HOST = "portal.dima.ac.kr"
internal const val MAIN_PATH = "/main/MainView.dunet"
private const val LOGIN_RESULT_TIMEOUT_MILLIS = 10_000L
