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
class LmsSessionTakeoverWebViewTest {
    @Test
    fun actual3045HiddenFormAndOfficialPortalRedirectShapeIsVerified() {
        val result = evaluate(
            baseUrl = "https://portal.dima.ac.kr/sso/error.aspx?errorCode=3045&errorMsg=fixture",
            html = """
                <html><head><script type="text/plain">top.location.href='https://portal.dima.ac.kr/' ;}</script></head>
                <body><form method="post" action="./error.aspx?errorCode=3045&amp;errorMsg=fixture">
                  <input type="hidden" name="__VIEWSTATE" value="fixture" />
                  <input type="hidden" name="__VIEWSTATEGENERATOR" value="fixture" />
                </form></body></html>
            """.trimIndent(),
        )

        assertEquals("\"verified\"", result)
    }

    @Test
    fun extraQueryOrInteractiveCredentialControlIsRejected() {
        assertEquals(
            "\"unavailable\"",
            evaluate(
                baseUrl = "https://portal.dima.ac.kr/sso/error.aspx?errorCode=3045&next=login",
                html = hiddenRedirectFixture(),
            ),
        )
        assertEquals(
            "\"interactive\"",
            evaluate(
                baseUrl = "https://portal.dima.ac.kr/sso/error.aspx?errorCode=3045",
                html = "<html><body><input type=\"password\" /></body></html>",
            ),
        )
    }

    @Test
    fun conflictWordingAndOneVisibleButtonCannotAuthorizeAutomaticTakeover() {
        val result = evaluate(
            baseUrl = "https://portal.dima.ac.kr/sso/error.aspx?errorCode=3045",
            html = """
                <html><body>
                  <p>기존 로그인 세션이 사용 중입니다.</p>
                  <button type="button" onclick="window.location='https://portal.dima.ac.kr/'">확인</button>
                </body></html>
            """.trimIndent(),
        )

        assertEquals("\"unavailable\"", result)
    }

    @Test
    fun hiddenRedirectFixtureWithAnyExtraInteractiveNodeIsRejected() {
        val result = evaluate(
            baseUrl = "https://portal.dima.ac.kr/sso/error.aspx?errorCode=3045",
            html = """
                <html><head><script type="text/plain">top.location.href='https://portal.dima.ac.kr/' ;}</script></head>
                <body>
                  <form method="post"><input type="hidden" name="__VIEWSTATE" value="fixture" /></form>
                  <a onclick="window.location='https://portal.dima.ac.kr/'">계속</a>
                </body></html>
            """.trimIndent(),
        )

        assertEquals("\"unavailable\"", result)
    }

    private fun hiddenRedirectFixture(): String = """
        <html><head><script type="text/plain">top.location.href='https://portal.dima.ac.kr/' ;}</script></head>
        <body><form method="post"><input type="hidden" name="__VIEWSTATE" value="fixture" /></form></body></html>
    """.trimIndent()

    private fun evaluate(baseUrl: String, html: String): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val completed = CountDownLatch(1)
        var result = ""
        instrumentation.runOnMainSync {
            val webView = WebView(instrumentation.targetContext)
            webView.settings.javaScriptEnabled = true
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript(VERIFY_LMS_SESSION_TAKEOVER_ACTION_SCRIPT) { value ->
                        result = value
                        view.destroy()
                        completed.countDown()
                    }
                }
            }
            webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
        }
        check(completed.await(10, TimeUnit.SECONDS)) { "WebView script timed out" }
        return result
    }
}
