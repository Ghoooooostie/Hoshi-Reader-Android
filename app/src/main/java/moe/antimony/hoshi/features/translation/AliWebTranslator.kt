package moe.antimony.hoshi.features.translation

import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 阿里翻译网页版（对齐 LunaTranslator ali.py）：
 * 先请求 csrftoken，再以查询串方式发起翻译，无需用户 key。
 */
internal object AliWebTranslator : FreeWebTranslator {
    private const val SOURCE_LANGUAGE = "auto"
    private const val TARGET_LANGUAGE = "zh"

    @Volatile
    private var cachedCsrfToken: String? = null

    override fun reset() {
        cachedCsrfToken = null
    }

    @Synchronized
    private fun csrfToken(): String {
        cachedCsrfToken?.let { return it }
        val response = WebTranslationHttp.request(
            method = "GET",
            url = "https://translate.alibaba.com/api/translate/csrftoken",
        )
        val token = Json.parseToJsonElement(WebTranslationHttp.requireSuccess(response))
            .jsonObject["token"]?.jsonPrimitive?.content
            ?: throw IOException("Ali csrf response missing token")
        cachedCsrfToken = token
        return token
    }

    override fun translate(text: String): String {
        val response = WebTranslationHttp.request(
            method = "POST",
            url = "https://translate.alibaba.com/api/translate/text",
            headers = mapOf("Referer" to "https://translate.alibaba.com"),
            query = mapOf(
                "srcLang" to SOURCE_LANGUAGE,
                "tgtLang" to TARGET_LANGUAGE,
                "domain" to "general",
                "query" to text,
                "_csrf" to csrfToken(),
            ),
        )
        val parsed = Json.parseToJsonElement(WebTranslationHttp.requireSuccess(response))
        val translated = parsed.jsonObject["data"]?.jsonObject?.get("translateText")?.jsonPrimitive?.content
            ?: throw IOException("Ali response missing translateText")
        return unescapeHtmlEntities(translated)
    }
}
