package moe.antimony.hoshi.features.reader

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderPaginationWebViewTest {
    @Test
    fun progressIncludesTextAtCurrentPageStart() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val pageLoaded = CountDownLatch(1)
        val scriptFinished = CountDownLatch(1)
        var progress = Double.NaN
        lateinit var webView: WebView

        instrumentation.runOnMainSync {
            webView = WebView(context)
            webView.settings.javaScriptEnabled = true
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    pageLoaded.countDown()
                }
            }
            webView.loadDataWithBaseURL(
                null,
                """
                <!doctype html>
                <html lang="ja">
                    <head>${ReaderPaginationScripts.shellScript(initialProgress = 0.0)}</head>
                    <body>一二三四五六七八九十</body>
                </html>
                """.trimIndent(),
                "text/html",
                "utf-8",
                null,
            )
        }

        assertTrue(pageLoaded.await(5, TimeUnit.SECONDS))

        instrumentation.runOnMainSync {
            webView.evaluateJavascript(progressAtPageStartScript()) { result ->
                progress = result.toDouble()
                scriptFinished.countDown()
            }
        }

        assertTrue(scriptFinished.await(5, TimeUnit.SECONDS))
        assertEquals(0.5, progress, 0.000001)
    }
}

private fun progressAtPageStartScript(): String =
    """
    (() => {
        window.hoshiReader.paginationMetrics = {
            totalChars: 100,
            progressStops: [
                { scroll: 0, exploredChars: 0 },
                { scroll: 100, exploredChars: 50 }
            ]
        };
        window.hoshiReader.getScrollContext = function() {
            return { vertical: true, scrollEl: { scrollTop: 100, scrollLeft: 0 }, pageSize: 100, maxScroll: 200 };
        };
        window.hoshiReader.getPagePosition = function() { return 100; };
        return window.hoshiReader.calculateProgress();
    })();
    """.trimIndent()
