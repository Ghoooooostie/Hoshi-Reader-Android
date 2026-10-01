package moe.antimony.hoshi.features.translation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Google 翻译网页版 protobuf 接口（对齐 LunaTranslator google.py）：
 * 使用网页前端自带的公开 ApiKey，逐行翻译后拼回。
 */
internal object GoogleWebTranslator : FreeWebTranslator {
    private const val SOURCE_LANGUAGE = "auto"
    private const val TARGET_LANGUAGE = "zh-CN"
    private const val API_KEY = "AIzaSyATBXajvzQLTDHEQbcpq0Ihe0vWDHmO520"

    override fun translate(text: String): String {
        if (text.isEmpty()) return ""
        return text.split('\n').joinToString("\n") { line -> translateLine(line) }
    }

    private fun translateLine(content: String): String {
        if (content.isEmpty()) return ""
        val body = buildJsonArray {
            add(
                buildJsonArray {
                    add(buildJsonArray { add(JsonPrimitive(content)) })
                    add(JsonPrimitive(SOURCE_LANGUAGE))
                    add(JsonPrimitive(TARGET_LANGUAGE))
                },
            )
            add(JsonPrimitive("wt_lib"))
        }.toString()
        val response = WebTranslationHttp.request(
            method = "POST",
            url = "https://translate-pa.googleapis.com/v1/translateHtml",
            headers = mapOf(
                "Content-Type" to "application/json+protobuf",
                "X-Goog-Api-Key" to API_KEY,
            ),
            jsonBody = body,
        )
        val parsed = Json.parseToJsonElement(WebTranslationHttp.requireSuccess(response))
        return unescapeHtmlEntities(parsed.jsonArray[0].jsonArray[0].jsonPrimitive.content)
    }
}
