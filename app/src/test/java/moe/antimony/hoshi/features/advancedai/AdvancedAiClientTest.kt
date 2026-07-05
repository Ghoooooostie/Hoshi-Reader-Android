package moe.antimony.hoshi.features.advancedai

import moe.antimony.hoshi.features.reader.ReaderSelectionData
import moe.antimony.hoshi.features.reader.ReaderSelectionRect
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AdvancedAiClientTest {
    private val settings = AdvancedAiSettings(
        enabled = true,
        baseUrl = "https://example.invalid/v1",
        apiKey = "sk-test",
        model = "gpt-test",
        wordPrompt = "Explain the role of the selected word.",
        sentenceTranslationPrompt = "Translate the sentence into natural Chinese.",
        sentencePrompt = "Explain the structure of the sentence.",
    )

    @Test
    fun wordRequestBodyIncludesWordSentenceAndOffsets() {
        val selection = ReaderSelectionData(
            text = "read",
            sentence = "I read the line aloud.",
            rect = ReaderSelectionRect(0.0, 0.0, 1.0, 1.0),
            normalizedOffset = 2,
            sentenceOffset = 2,
        )

        val body = buildWordAnalysisRequestBody(settings, selection)

        assertTrue(body.contains("Explain the role of the selected word."))
        assertFalse(body.contains("Output in plain Chinese text only."))
        assertFalse(body.contains("Output concise Chinese only."))
        assertFalse(body.contains("词性："))
        assertFalse(body.contains("句中含义："))
        assertTrue(body.contains("Selected word: read"))
        assertTrue(body.contains("Sentence: I read the line aloud."))
        assertTrue(body.contains("Sentence offset: 2"))
        assertTrue(body.contains("Normalized offset: 2"))
    }

    @Test
    fun requestBodyExplicitlyDisablesStreaming() {
        val selection = ReaderSelectionData(
            text = "read",
            sentence = "I read the line aloud.",
            rect = ReaderSelectionRect(0.0, 0.0, 1.0, 1.0),
            normalizedOffset = 2,
            sentenceOffset = 2,
        )

        val body = buildWordAnalysisRequestBody(settings, selection)

        assertTrue(body.contains("\"stream\":false"))
    }

    @Test
    fun sentenceRequestBodyIncludesSentencePromptAndText() {
        val body = buildSentenceAnalysisRequestBody(
            settings = settings,
            sentence = "Although it was raining, he still went out.",
        )

        assertTrue(body.contains("Explain the structure of the sentence."))
        assertFalse(body.contains("Output in plain Chinese text only."))
        assertFalse(body.contains("结构："))
        assertFalse(body.contains("难点："))
        assertTrue(body.contains("Sentence: Although it was raining, he still went out."))
    }

    @Test
    fun sentenceTranslationRequestBodyIncludesTranslationPromptAndText() {
        val body = buildSentenceTranslationRequestBody(
            settings = settings,
            sentence = "Although it was raining, he still went out.",
        )

        assertTrue(body.contains("Translate the sentence into natural Chinese."))
        assertFalse(body.contains("Output only the final Chinese translation."))
        assertFalse(body.contains("Do not add explanations"))
        assertTrue(body.contains("Sentence: Although it was raining, he still went out."))
    }

    @Test
    fun parseCompletionReturnsTrimmedFirstChoiceContent() {
        val response = """
            {
              "choices": [
                { "message": { "content": "  Functions as the main verb here.  " } }
              ]
            }
        """.trimIndent()

        assertEquals("Functions as the main verb here.", parseCompletionText(response))
    }

    @Test
    fun parseCompletionReturnsCombinedStreamChunks() {
        val response = """
            data: {"choices":[{"delta":{"content":"Functions as the "}}]}
            data: {"choices":[{"delta":{"content":"main verb here."}}]}
            data: [DONE]
        """.trimIndent()

        assertEquals("Functions as the main verb here.", parseCompletionText(response))
    }

    @Test
    fun transportReturnsAfterFinishReasonEvenIfStreamConnectionStaysOpen() {
        ServerSocket(0).use { server ->
            val keepConnectionOpen = CountDownLatch(1)
            val serverThread = thread(start = true) {
                server.accept().use { socket ->
                    val requestReader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                    while (true) {
                        val line = requestReader.readLine() ?: return@use
                        if (line.isBlank()) break
                    }
                    val writer = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
                    writer.write("HTTP/1.1 200 OK\r\n")
                    writer.write("Content-Type: text/event-stream\r\n")
                    writer.write("Connection: keep-alive\r\n")
                    writer.write("\r\n")
                    writer.write("data: {\"choices\":[{\"delta\":{\"content\":\"Functions as the \"},\"finish_reason\":null}]}\n\n")
                    writer.write("data: {\"choices\":[{\"delta\":{\"content\":\"main verb here.\"},\"finish_reason\":\"stop\"}]}\n\n")
                    writer.flush()
                    keepConnectionOpen.await(2, TimeUnit.SECONDS)
                }
            }
            val executor = Executors.newSingleThreadExecutor()
            val future = executor.submit<String> {
                HttpAdvancedAiTransport().post(
                    url = "http://127.0.0.1:${server.localPort}/v1/chat/completions",
                    body = "{}",
                    apiKey = "sk-test",
                    timeoutMillis = 3_000,
                )
            }

            try {
                val response = try {
                    future.get(1, TimeUnit.SECONDS)
                } catch (_: TimeoutException) {
                    fail("Transport should stop reading once the stream reports finish_reason.")
                    return
                }
                assertTrue(response.contains("\"finish_reason\":\"stop\""))
            } finally {
                keepConnectionOpen.countDown()
                future.cancel(true)
                executor.shutdownNow()
                serverThread.join(1_000)
            }
        }
    }

    @Test
    fun analyzeWordUsesFullChatCompletionsEndpointWithoutAppendingAgain() {
        var capturedUrl = ""
        val client = OpenAiCompatibleAdvancedAiClient(
            transport = AdvancedAiTransport { url, _, _, _ ->
                capturedUrl = url
                """
                    {
                      "choices": [
                        { "message": { "content": "ok" } }
                      ]
                    }
                """.trimIndent()
            },
        )
        val endpointSettings = settings.copy(
            baseUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions",
        )

        kotlinx.coroutines.runBlocking {
            client.analyzeWordInSentence(
                settings = endpointSettings,
                selection = ReaderSelectionData(
                    text = "read",
                    sentence = "I read the line aloud.",
                    rect = ReaderSelectionRect(0.0, 0.0, 1.0, 1.0),
                    normalizedOffset = 2,
                    sentenceOffset = 2,
                ),
            )
        }

        assertEquals(
            "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            capturedUrl,
        )
    }
}
