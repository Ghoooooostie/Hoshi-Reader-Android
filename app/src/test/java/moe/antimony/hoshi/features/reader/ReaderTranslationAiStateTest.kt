package moe.antimony.hoshi.features.reader

import kotlinx.coroutines.runBlocking
import moe.antimony.hoshi.features.advancedai.AdvancedAiAvailability
import moe.antimony.hoshi.features.advancedai.AdvancedAiCardKind
import moe.antimony.hoshi.features.advancedai.AdvancedAiClient
import moe.antimony.hoshi.features.advancedai.AdvancedAiMissingField
import moe.antimony.hoshi.features.advancedai.AdvancedAiSettings
import moe.antimony.hoshi.features.advancedai.LookupPopupAdvancedAiState
import moe.antimony.hoshi.features.dictionary.LookupPopupOptions
import moe.antimony.hoshi.ui.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTranslationAiStateTest {
    @Test
    fun readerAiPopupUsesLookupPopupShellWithoutDictionaryEntries() {
        val selection = sampleSelection()

        val popup = createReaderAiPopupItem(
            selection = selection,
            options = LookupPopupOptions(
                isVertical = true,
                isFullWidth = true,
                width = 360,
                height = 280,
                popupScale = 1.1,
            ),
        )

        assertTrue(popup.state.results.isEmpty())
        assertEquals(
            LookupPopupAdvancedAiState.Loading(AdvancedAiCardKind.Sentence),
            popup.state.advancedAiState,
        )
        assertEquals(selection.rect, popup.state.selection.rect)
        assertEquals(360, popup.state.width)
        assertEquals(280, popup.state.height)
        assertEquals(1.1, popup.state.popupScale, 0.0)
    }

    @Test
    fun translationPayloadTracksCurrentModeAndOptions() {
        val payload = LookupPopupAdvancedAiState.Loading(
            kind = AdvancedAiCardKind.Sentence,
        ).toReaderAiPopupPayload(
            mode = ReaderAiLongPressMode.Translation,
            resolve = ::resolveUiText,
        )

        requireNotNull(payload)
        assertEquals("整句翻译", payload.title)
        assertEquals("loading", payload.status)
        assertEquals("AI 处理中", payload.body)
        assertEquals(2, payload.modeOptions.size)
        assertEquals("Translation", payload.modeOptions[0].value)
        assertTrue(payload.modeOptions[0].selected)
        assertEquals("长难句分析", payload.modeOptions[1].title)
    }

    @Test
    fun modeAvailabilityTracksSelectedLongPressAction() {
        val settings = sampleAdvancedAiSettings(
            sentenceTranslationPrompt = "",
        )

        val translationAvailability = readerAiLongPressAvailability(
            settings = settings,
            mode = ReaderAiLongPressMode.Translation,
        )
        val analysisAvailability = readerAiLongPressAvailability(
            settings = settings,
            mode = ReaderAiLongPressMode.Analysis,
        )

        assertEquals(
            AdvancedAiAvailability.MissingConfiguration(AdvancedAiMissingField.SentenceTranslationPrompt),
            translationAvailability,
        )
        assertTrue(analysisAvailability is AdvancedAiAvailability.Ready)
    }

    @Test
    fun requestContentUsesMatchingAdvancedAiEndpoint() = runBlocking {
        val client = FakeAdvancedAiClient()
        val settings = sampleAdvancedAiSettings()

        val translation = client.requestReaderAiLongPressContent(
            settings = settings,
            mode = ReaderAiLongPressMode.Translation,
            sentence = "翻译句子",
        )
        val analysis = client.requestReaderAiLongPressContent(
            settings = settings,
            mode = ReaderAiLongPressMode.Analysis,
            sentence = "分析句子",
        )

        assertEquals("translation: 翻译句子", translation)
        assertEquals("analysis: 分析句子", analysis)
        assertEquals(listOf("翻译句子"), client.translationRequests)
        assertEquals(listOf("分析句子"), client.analysisRequests)
    }

    @Test
    fun popupAnchorRectUsesFullWrappedSelectionBoundsAcrossMultipleLines() {
        val anchor = readerPopupAnchorRect(
            selectionRects = listOf(
                ReaderSelectionRect(x = 210.0, y = 420.0, width = 180.0, height = 36.0),
                ReaderSelectionRect(x = 96.0, y = 456.0, width = 260.0, height = 36.0),
                ReaderSelectionRect(x = 24.0, y = 468.0, width = 250.0, height = 36.0),
            ),
            fallback = ReaderSelectionRect(x = 210.0, y = 420.0, width = 180.0, height = 36.0),
        )

        assertEquals(24.0, anchor.x, 0.0)
        assertEquals(420.0, anchor.y, 0.0)
        assertEquals(366.0, anchor.width, 0.0)
        assertEquals(84.0, anchor.height, 0.0)
    }

    private fun sampleSelection(): ReaderSelectionData =
        ReaderSelectionData(
            text = "整句",
            sentence = "これは長い文です。",
            rect = ReaderSelectionRect(x = 12.0, y = 24.0, width = 80.0, height = 32.0),
            normalizedOffset = 0,
            sentenceOffset = 0,
        )

    private fun sampleAdvancedAiSettings(
        sentenceTranslationPrompt: String = "translate",
        sentencePrompt: String = "analyze",
    ): AdvancedAiSettings =
        AdvancedAiSettings(
            enabled = true,
            baseUrl = "https://example.invalid/v1",
            apiKey = "sk-test",
            model = "gpt-test",
            wordPrompt = "word",
            sentenceTranslationPrompt = sentenceTranslationPrompt,
            pageParagraphTranslationPrompt = "paragraph-translate",
            sentencePrompt = sentencePrompt,
        )

    private fun resolveUiText(text: UiText): String =
        when (text) {
            is UiText.Literal -> text.value
            is UiText.Resource -> when (text.id) {
                moe.antimony.hoshi.R.string.reader_translation_ai_mode_translation -> "整句翻译"
                moe.antimony.hoshi.R.string.reader_translation_ai_mode_analysis -> "长难句分析"
                moe.antimony.hoshi.R.string.reader_translation_ai_loading -> "AI 处理中"
                moe.antimony.hoshi.R.string.reader_translation_ai_request_failed -> "AI 翻译加载失败。"
                moe.antimony.hoshi.R.string.advanced_ai_loading -> "分析中"
                moe.antimony.hoshi.R.string.advanced_ai_request_failed -> "AI 分析加载失败。"
                moe.antimony.hoshi.R.string.reader_translation_ai_unavailable_hint -> "请先在高级 AI 里完成配置。"
                else -> error("Unexpected resource ${text.id}")
            }
            is UiText.Plural -> error("Unexpected plural ${text.id}")
        }

    private class FakeAdvancedAiClient : AdvancedAiClient {
        val translationRequests = mutableListOf<String>()
        val analysisRequests = mutableListOf<String>()

        override suspend fun analyzeWordInSentence(
            settings: AdvancedAiSettings,
            selection: ReaderSelectionData,
        ): String = error("Not used in this test")

        override suspend fun translateSentence(settings: AdvancedAiSettings, sentence: String): String {
            translationRequests += sentence
            return "translation: $sentence"
        }

        override suspend fun analyzeSentence(settings: AdvancedAiSettings, sentence: String): String {
            analysisRequests += sentence
            return "analysis: $sentence"
        }

        override suspend fun testConnection(settings: AdvancedAiSettings): Result<Unit> =
            Result.success(Unit)
    }
}
