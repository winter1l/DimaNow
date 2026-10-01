package com.example.dimanow.lms

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.text.KeyboardOptions
import com.example.dimanow.theme.DimaShapes
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.example.dimanow.ui.DimaLayout
import com.example.dimanow.ui.ScreenScaffold
import org.json.JSONObject

@Composable
internal fun LmsLoginScreen(
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
    ScreenScaffold(
        title = "수업",
        modifier = modifier.imePadding(),
        itemSpacing = DimaLayout.sectionGap,
        listTag = "lms_login_list",
    ) {
        item(key = "lms_login_form") {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("학교 계정으로 로그인", style = MaterialTheme.typography.titleMedium)
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
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("학번") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("lms_username"),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("비밀번호") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("lms_password"),
                    )
                    Row(
                        Modifier.fillMaxWidth().toggleable(value = automatic, role = Role.Switch, onValueChange = { automatic = it }).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("자동 로그인")
                            Text("계정을 이 기기에 암호화해 저장해요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = automatic, onCheckedChange = null)
                    }
                    Button(
                        onClick = { onLogin(username, password, automatic) },
                        enabled = username.isNotBlank() && password.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().testTag("lms_login"),
                    ) { Text("로그인") }
                }
            }
        }
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
                CircularProgressIndicator(
                    Modifier.size(36.dp),
                    strokeWidth = 3.dp,
                    strokeCap = StrokeCap.Round,
                )
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
    onAuthenticated: (List<LmsCourse>) -> Unit,
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
internal const val MAIN_PATH = "/main/MainView.dunet"
private const val LOGIN_RESULT_TIMEOUT_MILLIS = 10_000L
