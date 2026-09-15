package com.example.dimanow.lms

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LmsCredentialSubmissionWebViewTest {
    @Test(timeout = WEB_VIEW_TEST_TIMEOUT_MS)
    fun dormantPopupFramesDoNotBlockTheOfficialLoginForm() {
        val result = evaluate(
            html = """
                <html><body>
                  <input id="txtID" /><input id="txtPwd" type="password" />
                  <div style="display:none"><iframe></iframe><input name="popupValue" /></div>
                  <script>function Login(mode){ window.loginMode = mode; }</script>
                </body></html>
            """.trimIndent(),
            script = lmsCredentialSubmissionScript("fixture-user", "fixture-password", portal = true),
        )
        assertEquals("\"submitted|fixture-user|fixture-password|N\"", result)
    }

    @Test(timeout = WEB_VIEW_TEST_TIMEOUT_MS)
    fun captchaDomStopsSubmissionBeforeCredentialsAreWritten() {
        val result = evaluate(
            html = """
                <html><body>
                  <input id="txtID" />
                  <input id="txtPwd" type="password" />
                  <input id="captchaAnswer" />
                  <script>function Login(mode){ window.loginMode = mode; }</script>
                </body></html>
            """.trimIndent(),
            script = lmsCredentialSubmissionScript(
                username = "fixture-user",
                password = "fixture-password",
                portal = true,
            ),
        )

        assertEquals("\"interactive|||\"", result)
    }

    @Test(timeout = WEB_VIEW_TEST_TIMEOUT_MS)
    fun ordinaryOfficialLoginFormStillSubmitsStoredCredentials() {
        val result = evaluate(
            html = """
                <html><body>
                  <input id="txtID" />
                  <input id="txtPwd" type="password" />
                  <script>function Login(mode){ window.loginMode = mode; }</script>
                </body></html>
            """.trimIndent(),
            script = lmsCredentialSubmissionScript(
                username = "fixture-user",
                password = "fixture-password",
                portal = true,
            ),
        )

        assertEquals("\"submitted|fixture-user|fixture-password|N\"", result)
    }

    @Test(timeout = WEB_VIEW_TEST_TIMEOUT_MS)
    fun unexpectedVisibleAdditionalAuthenticationFieldStopsSubmission() {
        val result = evaluate(
            html = """
                <html><body>
                  <input id="txtID" />
                  <input id="txtPwd" type="password" />
                  <input name="mfaCode" inputmode="numeric" />
                  <script>function Login(mode){ window.loginMode = mode; }</script>
                </body></html>
            """.trimIndent(),
            script = lmsCredentialSubmissionScript(
                username = "fixture-user",
                password = "fixture-password",
                portal = true,
            ),
        )

        assertEquals("\"interactive|||\"", result)
    }

    private fun evaluate(html: String, script: String): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val completed = CompletableFuture<String>()
        val evaluationStarted = AtomicBoolean(false)
        var webView: WebView? = null
        instrumentation.runOnMainSync {
            val createdWebView = WebView(instrumentation.targetContext)
            webView = createdWebView
            createdWebView.settings.javaScriptEnabled = true
            createdWebView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (!evaluationStarted.compareAndSet(false, true)) return
                    val probe = """
                        (function(){
                          var state=$script;
                          var user=document.querySelector('#txtID').value;
                          var password=document.querySelector('#txtPwd').value;
                          return state+'|'+user+'|'+password+'|'+(window.loginMode||'');
                        })()
                    """.trimIndent()
                    view.evaluateJavascript(probe) { value ->
                        completed.complete(value)
                    }
                }
            }
            createdWebView.loadDataWithBaseURL(
                "https://portal.dima.ac.kr/",
                html,
                "text/html",
                "UTF-8",
                null,
            )
        }
        return try {
            completed.get()
        } finally {
            instrumentation.runOnMainSync {
                webView?.stopLoading()
                webView?.destroy()
            }
        }
    }

    private companion object {
        const val WEB_VIEW_TEST_TIMEOUT_MS = 30_000L
    }
}
