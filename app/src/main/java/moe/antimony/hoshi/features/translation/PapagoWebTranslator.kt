package moe.antimony.hoshi.features.translation

import java.io.IOException
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Papago 网页版（对齐 LunaTranslator papago.py）：
 * 从首页 JS chunk 里提取 HMAC-MD5 密钥，按 "PPG deviceId:signature" 签名请求。
 */
internal object PapagoWebTranslator : FreeWebTranslator {
    private const val SOURCE_LANGUAGE = "auto"
    private const val TARGET_LANGUAGE = "zh-CN"
    private const val TRANSLATE_URL = "https://papago.naver.com/apis/n2mt/translate"

    @Volatile
    private var cachedAuthKey: String? = null
    private val deviceId: String = UUID.randomUUID().toString()

    override fun reset() {
        cachedAuthKey = null
    }

    @Synchronized
    private fun authKey(): String {
        cachedAuthKey?.let { return it }
        val homeHtml = WebTranslationHttp.requireSuccess(
            WebTranslationHttp.request("GET", "https://papago.naver.com/"),
        )
        val chunkPath = Regex("/main\\..*?\\.chunk\\.js").find(homeHtml)?.value
            ?: throw IOException("Papago main chunk js not found")
        val chunkJs = WebTranslationHttp.requireSuccess(
            WebTranslationHttp.request("GET", "https://papago.naver.com$chunkPath"),
        )
        val key = Regex("\"PPG \"(.*)\"(.*?)\"\\)\\.toString").find(chunkJs)
            ?.groupValues?.get(2)
            ?: throw IOException("Papago auth key not found")
        cachedAuthKey = key
        return key
    }

    override fun translate(text: String): String {
        val timestamp = System.currentTimeMillis().toString()
        val signature = Base64.getEncoder().encodeToString(
            hmacMd5(authKey(), "$deviceId\n$TRANSLATE_URL\n$timestamp"),
        )
        val response = WebTranslationHttp.request(
            method = "POST",
            url = TRANSLATE_URL,
            headers = mapOf(
                "authorization" to "PPG $deviceId:$signature",
                "device-type" to "pc",
                "origin" to "https://papago.naver.com",
                "referer" to "https://papago.naver.com/",
                "timestamp" to timestamp,
                "x-apigw-partnerid" to "papago",
            ),
            form = mapOf(
                "deviceId" to deviceId,
                "locale" to TARGET_LANGUAGE,
                "dict" to "true",
                "dictDisplay" to "30",
                "honorific" to "false",
                "instant" to "false",
                "paging" to "false",
                "source" to SOURCE_LANGUAGE,
                "target" to TARGET_LANGUAGE,
                "text" to text,
            ),
        )
        return Json.parseToJsonElement(WebTranslationHttp.requireSuccess(response))
            .jsonObject["translatedText"]?.jsonPrimitive?.content
            ?: throw IOException("Papago response missing translatedText")
    }
}

internal fun hmacMd5(key: String, message: String): ByteArray {
    val mac = Mac.getInstance("HmacMD5")
    mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacMD5"))
    return mac.doFinal(message.toByteArray(Charsets.UTF_8))
}
