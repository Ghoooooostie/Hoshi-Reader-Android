package moe.antimony.hoshi.features.audio

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class LocalAudioEntry(
    val source: String,
    val expression: String,
    val reading: String?,
    val file: String,
)

data class LocalAudioFile(
    val source: String,
    val file: String,
)

object AudioSourceResolver {
    fun expandTemplate(template: String, term: String, reading: String): String =
        template
            .replace("{term}", term.urlEncode())
            .replace("{reading}", reading.urlEncode())
}

object LocalAudioResolver {
    private val defaultSources = listOf(
        "nhk16",
        "daijisen",
        "shinmeikai8",
        "jpod",
        "jpod_alternate",
        "taas",
        "ozk5",
        "forvo",
        "forvo_ext",
        "forvo_ext2",
    )

    fun resolve(term: String, reading: String, rows: List<LocalAudioEntry>): LocalAudioEntry? {
        val normalizedReading = katakanaToHiragana(reading)
        return rows
            .asSequence()
            .filter { it.expression == term || (!it.reading.isNullOrBlank() && it.reading == normalizedReading) }
            .filter { it.file.endsWith(".mp3", ignoreCase = true) }
            .sortedWith(
                compareBy<LocalAudioEntry> {
                    if (normalizedReading.isNotBlank() && it.reading == normalizedReading) 0 else 1
                }.thenBy {
                    val index = defaultSources.indexOf(it.source)
                    if (index >= 0) index else Int.MAX_VALUE
                },
            )
            .firstOrNull()
    }

    fun audioUrl(source: String, file: String): String =
        "$LOCAL_AUDIO_SCHEME://${source.urlEncode()}/${file.urlEncode()}"

    fun parseAudioUrl(url: String): LocalAudioFile? {
        if (!url.startsWith("$LOCAL_AUDIO_SCHEME://")) return null
        val tail = url.removePrefix("$LOCAL_AUDIO_SCHEME://")
        val slash = tail.indexOf('/')
        if (slash <= 0 || slash == tail.lastIndex) return null
        return LocalAudioFile(
            source = tail.substring(0, slash).urlDecode(),
            file = tail.substring(slash + 1).urlDecode(),
        )
    }

    fun katakanaToHiragana(text: String): String {
        val builder = StringBuilder()
        text.codePoints().forEach { codePoint ->
            val converted = if (codePoint in 0x30A1..0x30F6) codePoint - 0x60 else codePoint
            builder.appendCodePoint(converted)
        }
        return builder.toString()
    }

    const val LOCAL_AUDIO_SCHEME = "hoshi-local-audio"
}

internal fun String.urlEncode(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name()).replace("+", "%20")

private fun String.urlDecode(): String =
    URLDecoder.decode(this, StandardCharsets.UTF_8.name())
