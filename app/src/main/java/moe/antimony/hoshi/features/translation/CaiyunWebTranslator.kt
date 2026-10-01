package moe.antimony.hoshi.features.translation

import java.io.IOException
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 彩云小译网页版接口（对齐 LunaTranslator caiyun.py）：
 * 固定 web token + browser_id 换取一次性 JWT，再发起翻译；返回的 target 需解密。
 */
internal object CaiyunWebTranslator : FreeWebTranslator {
    private const val TOKEN = "token:qgemv4jr1y38jyq6vhvi"
    private const val BROWSER_ID = "beba19f9d7f10c74c98334c9e8afcd34"
    private const val SOURCE_LANGUAGE = "auto"
    private const val TARGET_LANGUAGE = "zh"
    private const val API = "https://api.interpreter.caiyunai.com"

    override fun translate(text: String): String {
        val headers = baseHeaders() + mapOf("t-authorization" to requestJwt())
        val body = buildJsonObject {
            put("source", text)
            put("trans_type", "${SOURCE_LANGUAGE}2${TARGET_LANGUAGE}")
            put("request_id", "web_fanyi")
            put("media", "text")
            put("os_type", "web")
            put("dict", true)
            put("cached", true)
            put("replaced", true)
            put("detect", true)
            put("browser_id", BROWSER_ID)
        }.toString()
        val response = WebTranslationHttp.request(
            method = "POST",
            url = "$API/v1/translator",
            headers = headers,
            jsonBody = body,
        )
        val target = Json.parseToJsonElement(WebTranslationHttp.requireSuccess(response))
            .jsonObject["target"]?.jsonPrimitive?.content
            ?: throw IOException("Caiyun response missing target")
        return decryptCaiyun(target)
    }

    private fun requestJwt(): String {
        val response = WebTranslationHttp.request(
            method = "POST",
            url = "$API/v1/user/jwt/generate",
            headers = baseHeaders(),
            jsonBody = buildJsonObject { put("browser_id", BROWSER_ID) }.toString(),
        )
        return Json.parseToJsonElement(WebTranslationHttp.requireSuccess(response))
            .jsonObject["jwt"]?.jsonPrimitive?.content
            ?: throw IOException("Caiyun jwt response missing jwt")
    }

    private fun baseHeaders() = mapOf(
        "accept" to "application/json, text/plain, */*",
        "app-name" to "xy",
        "origin" to "https://fanyi.caiyunapp.com",
        "os-type" to "web",
        "referer" to "https://fanyi.caiyunapp.com/",
        "x-authorization" to TOKEN,
    )
}

/** 彩云返回的 target 是 ROT13 字母表映射后的 base64，需要先还原再解码。 */
internal fun decryptCaiyun(cipherText: String): String {
    val normalKey = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789=.+-_/"
    val cipherKey = "NOPQRSTUVWXYZABCDEFGHIJKLMnopqrstuvwxyzabcdefghijklm0123456789=.+-_/"
    val mapping = cipherKey.zip(normalKey).toMap()
    val restored = buildString {
        for (character in cipherText) append(mapping[character] ?: character)
    }
    return String(Base64.getDecoder().decode(restored), Charsets.UTF_8)
}
