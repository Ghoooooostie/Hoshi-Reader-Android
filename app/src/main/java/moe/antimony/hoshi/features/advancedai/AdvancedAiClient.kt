package moe.antimony.hoshi.features.advancedai

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import moe.antimony.hoshi.features.reader.ReaderSelectionData

/** 高级 AI 请求传输层，便于单测替换。 */
internal fun interface AdvancedAiTransport {
    fun post(url: String, body: String, apiKey: String, timeoutMillis: Int): String
}

/** 基于 HttpURLConnection 的默认传输实现。 */
internal class HttpAdvancedAiTransport : AdvancedAiTransport {
    override fun post(url: String, body: String, apiKey: String, timeoutMillis: Int): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        return try {
            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: throw IOException("Advanced AI HTTP ${connection.responseCode}")
            }
            readAdvancedAiResponse(
                stream = stream,
                contentType = connection.contentType,
            )
        } finally {
            connection.disconnect()
        }
    }
}

/** 高级 AI 能力边界。 */
internal interface AdvancedAiClient {
    suspend fun analyzeWordInSentence(settings: AdvancedAiSettings, selection: ReaderSelectionData): String
    suspend fun translateSentence(settings: AdvancedAiSettings, sentence: String): String
    suspend fun translatePageParagraph(settings: AdvancedAiSettings, paragraph: String): String =
        translateSentence(settings, paragraph)
    suspend fun analyzeSentence(settings: AdvancedAiSettings, sentence: String): String
    suspend fun testConnection(settings: AdvancedAiSettings): Result<Unit>
}

/** OpenAI 兼容 chat completions 客户端。 */
internal class OpenAiCompatibleAdvancedAiClient(
    private val transport: AdvancedAiTransport = HttpAdvancedAiTransport(),
    private val timeoutMillis: Int = 30_000,
) : AdvancedAiClient {
    override suspend fun analyzeWordInSentence(
        settings: AdvancedAiSettings,
        selection: ReaderSelectionData,
    ): String =
        runLoggedRequest {
            val response = transport.post(
                url = chatCompletionsUrl(settings.baseUrl),
                body = buildWordAnalysisRequestBody(settings, selection),
                apiKey = settings.apiKey,
                timeoutMillis = timeoutMillis,
            )
            parseCompletionText(response)
        }

    override suspend fun translateSentence(
        settings: AdvancedAiSettings,
        sentence: String,
    ): String =
        runLoggedRequest {
            val response = transport.post(
                url = chatCompletionsUrl(settings.baseUrl),
                body = buildSentenceTranslationRequestBody(settings, sentence),
                apiKey = settings.apiKey,
                timeoutMillis = timeoutMillis,
            )
            parseCompletionText(response)
        }

    override suspend fun translatePageParagraph(
        settings: AdvancedAiSettings,
        paragraph: String,
    ): String =
        runLoggedRequest {
            val response = transport.post(
                url = chatCompletionsUrl(settings.baseUrl),
                body = buildPageParagraphTranslationRequestBody(settings, paragraph),
                apiKey = settings.apiKey,
                timeoutMillis = timeoutMillis,
            )
            parseCompletionText(response)
        }

    override suspend fun analyzeSentence(
        settings: AdvancedAiSettings,
        sentence: String,
    ): String =
        runLoggedRequest {
            val response = transport.post(
                url = chatCompletionsUrl(settings.baseUrl),
                body = buildSentenceAnalysisRequestBody(settings, sentence),
                apiKey = settings.apiKey,
                timeoutMillis = timeoutMillis,
            )
            parseCompletionText(response)
        }

    override suspend fun testConnection(settings: AdvancedAiSettings): Result<Unit> =
        runCatching {
            analyzeWordInSentence(
                settings = settings,
                selection = ReaderSelectionData(
                    text = "test",
                    sentence = "This is a test sentence.",
                    rect = moe.antimony.hoshi.features.reader.ReaderSelectionRect(0.0, 0.0, 1.0, 1.0),
                    normalizedOffset = 0,
                    sentenceOffset = 0,
                ),
            )
            Unit
        }

    /** 统一记录 AI 请求耗时和结果，便于定位卡在哪一层。 */
    private suspend fun runLoggedRequest(block: () -> String): String =
        withContext(Dispatchers.IO) { block() }
}

/** 构造词语分析请求体。 */
internal fun buildWordAnalysisRequestBody(
    settings: AdvancedAiSettings,
    selection: ReaderSelectionData,
): String = buildChatCompletionsRequest(
    model = settings.model,
    prompt = buildConfiguredPrompt(settings.wordPrompt),
    userContent = buildString {
        appendLine("Selected word: ${selection.text}")
        appendLine("Sentence: ${selection.sentence}")
        appendLine("Sentence offset: ${selection.sentenceOffset ?: -1}")
        appendLine("Normalized offset: ${selection.normalizedOffset ?: -1}")
    }.trim(),
)

/** 构造整句翻译请求体。 */
internal fun buildSentenceTranslationRequestBody(
    settings: AdvancedAiSettings,
    sentence: String,
): String = buildChatCompletionsRequest(
    model = settings.model,
    prompt = buildConfiguredPrompt(settings.sentenceTranslationPrompt),
    userContent = buildTranslationUserContent(
        sourceLabel = "原文",
        sourceText = sentence,
    ),
)

/** 构造全文翻译段落请求体，强制整段逐句完整翻译。 */
internal fun buildPageParagraphTranslationRequestBody(
    settings: AdvancedAiSettings,
    paragraph: String,
): String = buildChatCompletionsRequest(
    model = settings.model,
    prompt = buildConfiguredPrompt(settings.pageParagraphTranslationPrompt),
    userContent = buildTranslationUserContent(
        sourceLabel = "原文段落",
        sourceText = paragraph,
    ),
)

/** 构造长难句分析请求体。 */
internal fun buildSentenceAnalysisRequestBody(
    settings: AdvancedAiSettings,
    sentence: String,
): String = buildChatCompletionsRequest(
    model = settings.model,
    prompt = buildConfiguredPrompt(settings.sentencePrompt),
    userContent = "Sentence: $sentence",
)

/** 直接使用用户当前保存的提示词，避免隐式追加额外要求。 */
private fun buildConfiguredPrompt(prompt: String): String = prompt.trim()

/** 给翻译请求补足明确的中文目标和输出约束，减少模型偏回原文语言。 */
private fun buildTranslationUserContent(
    sourceLabel: String,
    sourceText: String,
): String = buildString {
    appendLine("目标语言：简体中文")
    appendLine("输出要求：只输出最终译文，不要输出原文，不要解释。")
    append(sourceLabel)
    append('：')
    append(sourceText)
}

/** 解析 chat completions 的首条文本返回。 */
internal fun parseCompletionText(responseText: String): String {
    if (responseText.lineSequence().any { it.trimStart().startsWith("data:") }) {
        return parseStreamingCompletionText(responseText)
    }
    val response = advancedAiJson.decodeFromString(ChatCompletionsResponse.serializer(), responseText)
    return response.choices.firstOrNull()?.message?.content?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: error("Advanced AI response did not contain message content.")
}

/** 生成 OpenAI 兼容请求 JSON。 */
private fun buildChatCompletionsRequest(
    model: String,
    prompt: String,
    userContent: String,
): String = advancedAiJson.encodeToString(
    ChatCompletionsRequest.serializer(),
    ChatCompletionsRequest(
        model = model,
        stream = false,
        messages = listOf(
            ChatMessage(role = "system", content = prompt),
            ChatMessage(role = "user", content = userContent),
        ),
    ),
)

/** 生成 completions 接口地址。 */
private fun chatCompletionsUrl(baseUrl: String): String =
    baseUrl
        .trim()
        .trimEnd('/')
        .let { normalized ->
            if (normalized.endsWith("/chat/completions", ignoreCase = true)) {
                normalized
            } else {
                "$normalized/chat/completions"
            }
        }

private val advancedAiJson = Json {
    ignoreUnknownKeys = true
}

/** 读取普通 JSON 或 SSE 流式响应，避免等待服务端主动断开连接。 */
private fun readAdvancedAiResponse(
    stream: InputStream,
    contentType: String? = null,
): String =
    stream.bufferedReader(Charsets.UTF_8).use { reader ->
        val firstContentLine = generateSequence { reader.readLine() }
            .firstOrNull { it.isNotBlank() }
            ?: return@use ""
        val isEventStream = contentType?.contains("text/event-stream", ignoreCase = true) == true ||
            firstContentLine.trimStart().startsWith("data:") ||
            firstContentLine.trimStart().startsWith("event:")
        if (isEventStream) {
            buildString {
                append(firstContentLine)
                if (shouldStopReadingStreamLine(firstContentLine)) {
                    return@buildString
                }
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    append('\n')
                    append(line)
                    if (shouldStopReadingStreamLine(line)) break
                }
            }
        } else {
            buildString {
                append(firstContentLine)
                while (true) {
                    val line = reader.readLine() ?: break
                    append('\n')
                    append(line)
                }
            }
        }
    }

/** 解析 OpenAI 兼容 SSE 返回的分片文本。 */
private fun parseStreamingCompletionText(responseText: String): String {
    var fullContent: String? = null
    val deltaContent = StringBuilder()
    responseText.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        if (!line.startsWith("data:")) return@forEach
        val payload = line.removePrefix("data:").trim()
        if (payload.isBlank() || payload == "[DONE]") return@forEach
        val response = advancedAiJson.decodeFromString(ChatCompletionsStreamResponse.serializer(), payload)
        val choice = response.choices.firstOrNull()
        choice?.message?.content
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { fullContent = it }
        choice?.delta?.content
            ?.takeIf { it.isNotBlank() }
            ?.let(deltaContent::append)
    }
    return fullContent
        ?: deltaContent.toString().trim().takeIf { it.isNotEmpty() }
        ?: error("Advanced AI response did not contain message content.")
}

/** 判断流式返回是否已经明确结束，避免一直等待连接关闭。 */
private fun shouldStopReadingStreamLine(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.startsWith("data:")) return false
    val payload = trimmed.removePrefix("data:").trim()
    if (payload == "[DONE]") return true
    if (payload.isBlank()) return false
    val response = runCatching {
        advancedAiJson.decodeFromString(ChatCompletionsStreamResponse.serializer(), payload)
    }.getOrNull() ?: return false
    return response.choices.any { !it.finishReason.isNullOrBlank() }
}

@Serializable
private data class ChatCompletionsRequest(
    val model: String,
    val stream: Boolean,
    val messages: List<ChatMessage>,
)

@Serializable
private data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class ChatCompletionsResponse(
    val choices: List<ChatChoice> = emptyList(),
)

@Serializable
private data class ChatCompletionsStreamResponse(
    val choices: List<ChatStreamChoice> = emptyList(),
)

@Serializable
private data class ChatChoice(
    @SerialName("message")
    val message: ChatResponseMessage? = null,
)

@Serializable
private data class ChatStreamChoice(
    @SerialName("message")
    val message: ChatResponseMessage? = null,
    @SerialName("delta")
    val delta: ChatResponseMessage? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null,
)

@Serializable
private data class ChatResponseMessage(
    val content: String? = null,
)
