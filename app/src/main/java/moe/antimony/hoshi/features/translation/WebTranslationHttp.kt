package moe.antimony.hoshi.features.translation

import java.io.IOException
import java.net.CookieHandler
import java.net.CookieManager
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** 免 key web 翻译接口的原始响应。 */
internal class WebTranslationResponse(
    val status: Int,
    val body: String,
    val location: String? = null,
)

/**
 * 免 key web 翻译共用的 HttpURLConnection 封装：查询串、表单、JSON 体与自定义头。
 * 不跟随重定向（Bing 302 需要按 Location 重新 POST），Cookie 由全局 CookieManager 维护。
 */
internal object WebTranslationHttp {
    const val DEFAULT_USER_AGENT: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"

    private const val DEFAULT_TIMEOUT_MILLIS = 20_000

    fun ensureCookieManager() {
        if (CookieHandler.getDefault() == null) {
            CookieHandler.setDefault(CookieManager())
        }
    }

    fun encodeFormValue(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    fun encodeQuery(query: Map<String, String>): String =
        query.entries.joinToString("&") { (key, value) ->
            "${encodeFormValue(key)}=${encodeFormValue(value)}"
        }

    fun request(
        method: String,
        url: String,
        query: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        form: Map<String, String>? = null,
        jsonBody: String? = null,
        contentType: String? = null,
        timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    ): WebTranslationResponse {
        ensureCookieManager()
        val fullUrl = buildString {
            append(url)
            if (query.isNotEmpty()) {
                append(if (url.contains('?')) '&' else '?')
                append(encodeQuery(query))
            }
        }
        val connection = (URL(fullUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
            when {
                form != null -> {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    outputStream.use { output ->
                        output.write(encodeQuery(form).toByteArray(Charsets.UTF_8))
                    }
                }
                jsonBody != null -> {
                    doOutput = true
                    setRequestProperty("Content-Type", contentType ?: "application/json; charset=UTF-8")
                    outputStream.use { output ->
                        output.write(jsonBody.toByteArray(Charsets.UTF_8))
                    }
                }
            }
        }
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bodyText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            WebTranslationResponse(
                status = status,
                body = bodyText,
                location = connection.getHeaderField("Location"),
            )
        } finally {
            connection.disconnect()
        }
    }

    /** 校验 2xx 并返回响应体，否则抛异常（触发调用方的凭据重置重试）。 */
    fun requireSuccess(response: WebTranslationResponse): String {
        if (response.status !in 200..299) {
            throw IOException("Web translation HTTP ${response.status}")
        }
        return response.body
    }
}
