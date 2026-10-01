package moe.antimony.hoshi.features.translation

import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 微软翻译 Android 客户端接口（对齐 LunaTranslator microsoft.py）：
 * HMAC-SHA256 私钥硬编码在客户端里，每次请求现算签名，无需用户配置 key。
 */
internal object MicrosoftWebTranslator : FreeWebTranslator {
    private const val TARGET_LANGUAGE = "zh-Hans"
    private const val ENDPOINT = "https://api.cognitive.microsofttranslator.com/translate?api-version=3.0&to=$TARGET_LANGUAGE"

    /** LunaTranslator 内置的 MSTranslatorAndroidApp 签名私钥（64 字节）。 */
    private const val PRIVATE_KEY_HEX =
        "a2293a3dd0dd32977a64dbc2f327f5d7bf87d9459df05a0966c630c66aaa849a41aa943aa8d51a6e4daac9a3701235c7eb12f6e823079e471095918855d817"

    override fun translate(text: String): String {
        val body = buildJsonArray {
            add(buildJsonObject { put("Text", text) })
        }.toString()
        val response = WebTranslationHttp.request(
            method = "POST",
            url = ENDPOINT,
            headers = mapOf("X-MT-Signature" to buildSignature()),
            jsonBody = body,
        )
        val parsed = Json.parseToJsonElement(WebTranslationHttp.requireSuccess(response))
        return parsed.jsonArray[0].jsonObject["translations"]!!.jsonArray[0]
            .jsonObject["text"]!!.jsonPrimitive.content
    }

    private fun buildSignature(): String {
        val guid = UUID.randomUUID().toString().replace("-", "")
        val escapedUrl = URLEncoder.encode(
            ENDPOINT.removePrefix("https://"),
            Charsets.UTF_8.name(),
        )
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("GMT")
        val dateTime = dateFormat.format(Date())
        val message = "MSTranslatorAndroidApp$escapedUrl$dateTime$guid".lowercase(Locale.US)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(privateKey(), "HmacSHA256"))
        val digest = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        return "MSTranslatorAndroidApp::${Base64.getEncoder().encodeToString(digest)}::$dateTime::$guid"
    }

    private fun privateKey(): ByteArray {
        val bytes = ByteArray(PRIVATE_KEY_HEX.length / 2)
        for (index in bytes.indices) {
            bytes[index] = PRIVATE_KEY_HEX.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
        return bytes
    }
}
