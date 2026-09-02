package com.example.dimanow.lms

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LmsCredentialSubmissionWebViewTest {
    @Test
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

    @Test
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

    @Test
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
        val completed = CountDownLatch(1)
        var result = ""
        instrumentation.runOnMainSync {
            val webView = WebView(instrumentation.targetContext)
            webView.settings.javaScriptEnabled = true
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    val probe = """
                        (function(){
                          var state=$script;
                          var user=document.querySelector('#txtID').value;
                          var password=document.querySelector('#txtPwd').value;
                          return state+'|'+user+'|'+password+'|'+(window.loginMode||'');
                        })()
                    """.trimIndent()
                    view.evaluateJavascript(probe) { value ->
                        result = value
                        view.destroy()
                        completed.countDown()
                    }
                }
            }
            webView.loadDataWithBaseURL(
                "https://portal.dima.ac.kr/",
                html,
                "text/html",
                "UTF-8",
                null,
            )
        }
        check(completed.await(10, TimeUnit.SECONDS)) { "WebView script timed out" }
        return result
    }
}
