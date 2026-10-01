package com.example.dimanow.lms

import android.net.Uri
import android.view.View
import android.webkit.JsResult
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

@Composable
internal fun LmsOfficialCourseWebView(
    page: LmsOfficialCoursePage,
    onBack: () -> Unit,
    onLaunched: () -> Unit,
    onSessionExpired: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var ready by remember(page) { mutableStateOf(false) }
    LmsFullScreenPane(
        title = page.item.title,
        onBack = onBack,
        modifier = modifier.testTag("lms_official_course_screen"),
    ) {
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
                                onMessage(LmsUserMessages.UNSAFE_PAGE_BLOCKED)
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
                                            onMessage("공식 LMS 항목을 열 수 없어요")
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
                                                onMessage("공식 LMS 항목을 열 수 없어요")
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
                                        onMessage("영상 플레이어를 열지 못해 공식 수업 화면을 열었어요")
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
                                                        "영상 플레이어를 열지 못해 공식 수업 화면을 열었어요",
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
                                                            "같은 이름의 영상이 있어 공식 화면을 열었어요"
                                                        } else {
                                                            "해당 영상 차시를 찾지 못해 공식 화면을 열었어요"
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
                                                    markUnavailable("해당 영상 차시를 찾지 못해 공식 화면을 열었어요")
                                                }
                                                "\"ambiguous\"" -> markUnavailable("같은 이름의 영상이 있어 공식 화면을 열었어요")
                                                else -> markUnavailable("해당 영상 차시를 찾지 못해 공식 화면을 열었어요")
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
                    Modifier.align(Alignment.Center).size(36.dp),
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
