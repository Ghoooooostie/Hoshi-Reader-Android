package moe.antimony.hoshi.features.audio

import android.webkit.WebResourceResponse
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class AudioRequestHandler(
    private val localAudioRepository: LocalAudioRepository,
) {
    fun handleAudioRequest(url: String): WebResourceResponse? {
        val requestUri = Uri.parse(url)
        val isIosAudioScheme = requestUri.scheme == "audio"
        val isAndroidAudioEndpoint = requestUri.scheme == "https" &&
            requestUri.host == "hoshi.local" &&
            requestUri.path == "/audio"
        if (!isIosAudioScheme && !isAndroidAudioEndpoint) return null
        val target = requestUri.getQueryParameter("url")
            ?: return jsonResponse(emptyAudioResponse())

        return if (target.startsWith(AudioSettings.LocalAudioUrl.substringBefore("?"))) {
            jsonResponse(localAudioResponse(target))
        } else {
            jsonResponse(fetchRemoteAudioList(target))
        }
    }

    private fun localAudioResponse(targetUrl: String): ByteArray {
        val uri = URI(targetUrl)
        val query = uri.rawQuery.orEmpty()
            .split('&')
            .filter { it.contains('=') }
            .associate { part ->
                val name = part.substringBefore('=')
                val value = java.net.URLDecoder.decode(part.substringAfter('='), Charsets.UTF_8.name())
                name to value
            }
        val term = query["term"].orEmpty()
        val reading = query["reading"].orEmpty()
        val entry = localAudioRepository.findAudio(term, reading) ?: return emptyAudioResponse()
        val response = JSONObject()
            .put("type", "audioSourceList")
            .put(
                "audioSources",
                JSONArray().put(
                    JSONObject()
                        .put("name", entry.source)
                        .put("url", LocalAudioResolver.audioUrl(entry.source, entry.file)),
                ),
            )
        return response.toString().toByteArray()
    }

    private fun fetchRemoteAudioList(targetUrl: String): ByteArray =
        runCatching {
            val connection = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                requestMethod = "GET"
            }
            connection.inputStream.use { it.readBytes() }
        }.getOrElse {
            emptyAudioResponse()
        }

    private fun jsonResponse(body: ByteArray): WebResourceResponse =
        WebResourceResponse(
            "application/json",
            "UTF-8",
            ByteArrayInputStream(body),
        ).apply {
            responseHeaders = mapOf("Access-Control-Allow-Origin" to "*")
        }

    private fun emptyAudioResponse(): ByteArray =
        """{"type":"audioSourceList","audioSources":[]}""".toByteArray()
}
