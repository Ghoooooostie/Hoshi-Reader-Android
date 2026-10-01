package moe.antimony.hoshi.features.translation

import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 有道桌面词典接口（对齐 LunaTranslator youdaodict.py）：
 * 模拟 deskdict 客户端，签名 key 硬编码，签名只是 MD5 时间戳串。
 */
internal object YoudaoWebTranslator : FreeWebTranslator {
    private const val SIGN_KEY = "cybibtzhdwayqjmrncst"
    private const val SOURCE_LANGUAGE = "auto"
    private const val TARGET_LANGUAGE = "zh-CHS"

    override fun translate(text: String): String {
        val timestamp = System.currentTimeMillis().toString()
        val sign = md5Hex("client=deskdict&mysticTime=$timestamp&product=deskdict&key=$SIGN_KEY")
        val query = mapOf(
            "keyfrom" to "deskdict.main",
            "client" to "deskdict",
            "from" to SOURCE_LANGUAGE,
            "to" to TARGET_LANGUAGE,
            "keyid" to "deskdict",
            "mysticTime" to timestamp,
            "pointParam" to "client,product,mysticTime",
            "sign" to sign,
            "domain" to "0",
            "useTerm" to "false",
            "noCheckPrivate" to "false",
            "recTerms" to "[]",
            "id" to "0a464aedddbc6e4b9",
            "vendor" to "fanyiweb_navigation",
            "in" to "YoudaoDict_fanyiweb_navigation",
            "appVer" to "11.2.0.0",
            "appZengqiang" to "0",
            "abTest" to "0",
            "model" to "LENOVO",
            "screen" to "1920*1080",
            "OsVersion" to "10.0.19045",
            "network" to "none",
            "mid" to "windows10.0.19045",
            "appVersion" to "11.2.0.0",
            "product" to "deskdict",
            "source" to "mine_transtab_realtime",
        )
        val response = WebTranslationHttp.request(
            method = "POST",
            url = "https://dict.youdao.com/dicttranslate",
            query = query,
            headers = mapOf("User-Agent" to "Youdao Desktop Dict (Windows NT 10.0)"),
            form = mapOf("i" to text),
        )
        val parsed = Json.parseToJsonElement(WebTranslationHttp.requireSuccess(response))
        val translateResult = parsed.jsonObject["translateResult"]?.jsonArray
            ?: throw IOException("Youdao response missing translateResult")
        return buildString {
            for (row in translateResult) {
                for (item in row.jsonArray) {
                    append(item.jsonObject["tgt"]?.jsonPrimitive?.content.orEmpty())
                }
            }
        }
    }
}
