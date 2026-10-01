package moe.antimony.hoshi.features.translation

import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 必应网页翻译（对齐 LunaTranslator bing.py）：先抓取 /Translator 页面提取
 * params_AbusePreventionHelper 里的时效 key/token 与 IG/IID，失败后由路由层重置重试。
 */
internal object BingWebTranslator : FreeWebTranslator {
    private const val SOURCE_LANGUAGE = "auto-detect"
    private const val TARGET_LANGUAGE = "zh-Hans"

    @Volatile
    private var cachedCredentials: Credentials? = null

    private class Credentials(
        val key: String,
        val token: String,
        val ig: String,
        val iid: String,
    )

    override fun reset() {
        cachedCredentials = null
    }

    @Synchronized
    private fun credentials(): Credentials {
        cachedCredentials?.let { return it }
        val html = WebTranslationHttp.requireSuccess(
            WebTranslationHttp.request("GET", "https://www.bing.com/Translator"),
        )
        val keyToken = Regex("""var params_AbusePreventionHelper\s*=\s*\[\s*"([^"]+)"\s*,\s*"([^"]+)"""")
            .find(html)
            ?: throw IOException("Bing abuse prevention params not found")
        val iid = Regex("""<div\s+id="tta_outGDCont"\s+data-iid="([^"]+)">""").find(html)
            ?.groupValues?.get(1)
            ?: throw IOException("Bing data-iid not found")
        val ig = Regex("""IG:"([^"]+)"""").find(html)?.groupValues?.get(1)
            ?: throw IOException("Bing IG not found")
        // 与 LunaTranslator 保持一致：IG 参数取页面 data-iid 值，IID 参数取页面 IG 值。
        val credentials = Credentials(
            key = keyToken.groupValues[1],
            token = keyToken.groupValues[2],
            ig = iid,
            iid = ig,
        )
        cachedCredentials = credentials
        return credentials
    }

    override fun translate(text: String): String {
        val credentials = credentials()
        val form = linkedMapOf(
            "text" to text,
            "fromLang" to SOURCE_LANGUAGE,
            "to" to TARGET_LANGUAGE,
            "tryFetchingGenderDebiasedTranslations" to "true",
            "key" to credentials.key,
            "token" to credentials.token,
        )
        val query = mapOf("IG" to credentials.ig, "IID" to credentials.iid, "isVertical" to "1")
        var response = WebTranslationHttp.request(
            method = "POST",
            url = "https://www.bing.com/ttranslatev3",
            query = query,
            form = form,
        )
        if (response.status == 302) {
            val location = response.location ?: throw IOException("Bing redirect without location")
            response = WebTranslationHttp.request(method = "POST", url = location, form = form)
        }
        val parsed = Json.parseToJsonElement(WebTranslationHttp.requireSuccess(response))
        return parsed.jsonArray[0].jsonObject["translations"]!!.jsonArray[0]
            .jsonObject["text"]!!.jsonPrimitive.content
    }
}
