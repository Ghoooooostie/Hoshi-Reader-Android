package moe.antimony.hoshi.features.translation

import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * ModernMt 网页接口（对齐 LunaTranslator ModernMt.py）：
 * 前端 webkey 硬编码，verify = md5("webkey_...#{ts}#{text}")。
 */
internal object ModernMtWebTranslator : FreeWebTranslator {
    private const val SOURCE_LANGUAGE = ""
    private const val TARGET_LANGUAGE = "zh"

    override fun translate(text: String): String {
        val timestamp = System.currentTimeMillis()
        val verify = md5Hex("webkey_E3sTuMjpP8Jez49GcYpDVH7r#${timestamp}#${text}")
        val body = buildJsonObject {
            put("q", text)
            put("source", SOURCE_LANGUAGE)
            put("target", TARGET_LANGUAGE)
            put("ts", timestamp)
            put("verify", verify)
            put("hints", "")
            put("multiline", "true")
        }.toString()
        val response = WebTranslationHttp.request(
            method = "POST",
            url = "https://webapi.modernmt.com/translate",
            headers = mapOf(
                "Origin" to "https://www.modernmt.com",
                "Referer" to "https://www.modernmt.com/translate",
                "X-Requested-With" to "XMLHttpRequest",
                "X-HTTP-Method-Override" to "GET",
            ),
            jsonBody = body,
        )
        val parsed = Json.parseToJsonElement(WebTranslationHttp.requireSuccess(response))
        return parsed.jsonObject["data"]?.jsonObject?.get("translation")?.jsonPrimitive?.content
            ?: throw IOException("ModernMt response missing translation")
    }
}
