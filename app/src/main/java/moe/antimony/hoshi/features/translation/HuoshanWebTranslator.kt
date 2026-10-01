package moe.antimony.hoshi.features.translation

import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 火山翻译浏览器插件接口（对齐 LunaTranslator huoshan.py）：
 * 伪装 chrome 插件 origin，端点无鉴权，源语言自动检测。
 */
internal object HuoshanWebTranslator : FreeWebTranslator {
    private const val TARGET_LANGUAGE = "zh"

    override fun translate(text: String): String {
        val body = buildJsonObject {
            put("text", text)
            put("target_language", TARGET_LANGUAGE)
            put("enable_user_glossary", false)
            put("glossary_list", buildJsonArray {})
            put("category", "")
        }.toString()
        val response = WebTranslationHttp.request(
            method = "POST",
            url = "https://translate.volcengine.com/crx/translate/v1/",
            headers = mapOf("Origin" to "chrome-extension://klgfhbiooeogdfodpopgppeadghjjemk"),
            jsonBody = body,
        )
        return Json.parseToJsonElement(WebTranslationHttp.requireSuccess(response))
            .jsonObject["translation"]?.jsonPrimitive?.content
            ?: throw IOException("Huoshan response missing translation")
    }
}
