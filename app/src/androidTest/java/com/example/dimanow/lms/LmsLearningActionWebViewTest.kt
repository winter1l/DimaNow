package com.example.dimanow.lms

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LmsLearningActionWebViewTest {
    private var webView: WebView? = null

    @After
    fun releaseWebView() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }

    @Test
    fun verifiedExactCourseRowExecutesItsOfficialLearningActionOnlyOnce() {
        loadFixture(
            """
                <div class="view_act_cont">
                  <strong>사운드디자인 기초(1) (영상콘텐츠(MP4)) | 출석인정시간 : 28분</strong>
                  <button class="btn_learn" onclick="fncLearningWindow('2','100','2','COURSE_V','LV','CONTENTSTYPE_V');">학습시작</button>
                </div>
                <div class="view_act_cont">
                  <strong>사운드디자인 기초(2) (영상콘텐츠(MP4)) | 출석인정시간 : 26분</strong>
                  <button class="btn_learn" onclick="fncLearningWindow('2','101','2','COURSE_V','LV','CONTENTSTYPE_V');">학습시작</button>
                </div>
                <script>
                  window.learningCallCount=0;
                  function fncLearningWindow(){ window.learningCallCount += 1; }
                </script>
            """.trimIndent(),
        )

        val target = normalizeOfficialLearningTitle("사운드디자인 기초(1)(0분/28분)")
        assertEquals("\"ready\"", evaluate(officialLearningActionResolutionScript(target)))
        assertEquals("\"started\"", evaluate(officialLearningActionExecutionScript(target)))
        assertEquals("\"already_started\"", evaluate(officialLearningActionExecutionScript(target)))
        assertEquals("1", evaluate("window.learningCallCount"))
    }

    private fun loadFixture(body: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val loaded = CountDownLatch(1)
        instrumentation.runOnMainSync {
            webView = WebView(instrumentation.targetContext).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        loaded.countDown()
                    }
                }
                loadDataWithBaseURL(
                    "https://lms.dima.ac.kr/lms/class/courseSchedule/doListView.dunet",
                    body,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        }
        assertTrue("fixture did not load", loaded.await(5, TimeUnit.SECONDS))
    }

    private fun evaluate(script: String): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val complete = CountDownLatch(1)
        var result: String? = null
        instrumentation.runOnMainSync {
            requireNotNull(webView).evaluateJavascript(script) { value ->
                result = value
                complete.countDown()
            }
        }
        assertTrue("script did not complete", complete.await(5, TimeUnit.SECONDS))
        return requireNotNull(result)
    }
}
