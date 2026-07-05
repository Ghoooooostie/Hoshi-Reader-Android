package moe.antimony.hoshi.features.advancedai

import moe.antimony.hoshi.R
import moe.antimony.hoshi.features.dictionary.lookupPopupContentKey
import moe.antimony.hoshi.features.dictionary.LookupPopupItem
import moe.antimony.hoshi.features.dictionary.LookupPopupState
import moe.antimony.hoshi.features.reader.ReaderSelectionData
import moe.antimony.hoshi.features.reader.ReaderSelectionRect
import moe.antimony.hoshi.ui.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedAiPopupStateTest {
    @Test
    fun hiddenStateProducesNoPayload() {
        val payload = LookupPopupAdvancedAiState.Hidden.toPayload(::resolveUiText)

        assertNull(payload)
    }

    @Test
    fun wordSuccessPayloadUsesWordTitleAndBody() {
        val payload = LookupPopupAdvancedAiState.Success(
            kind = AdvancedAiCardKind.Word,
            content = "这里是句中的谓语动词。",
        ).toPayload(::resolveUiText)

        requireNotNull(payload)
        assertEquals("AI 词语分析", payload.title)
        assertEquals("success", payload.status)
        assertEquals("这里是句中的谓语动词。", payload.body)
    }

    @Test
    fun sentenceLoadingPayloadUsesSentenceTitle() {
        val payload = LookupPopupAdvancedAiState.Loading(
            kind = AdvancedAiCardKind.Sentence,
        ).toPayload(::resolveUiText)

        requireNotNull(payload)
        assertEquals("长难句分析", payload.title)
        assertEquals("loading", payload.status)
        assertEquals("分析中", payload.body)
    }

    @Test
    fun advancedAiOnlyPopupStillProducesContentKey() {
        val payload = LookupPopupAdvancedAiPayload(
            title = "整句翻译",
            status = "success",
            body = "这是翻译结果。",
        )

        val contentKey = lookupPopupContentKey(
            results = emptyList(),
            advancedAi = payload,
        )

        assertTrue(contentKey?.isNotBlank() == true)
    }

    @Test
    fun updateAdvancedAiStateOnlyReplacesTargetPopup() {
        val first = popup("first")
        val second = popup("second")

        val updated = listOf(first, second).updateAdvancedAiState(
            popupId = "second",
            nextState = LookupPopupAdvancedAiState.Error(
                kind = AdvancedAiCardKind.Word,
                message = UiText.Resource(R.string.advanced_ai_request_failed),
            ),
        )

        assertTrue(updated[0].state.advancedAiState === LookupPopupAdvancedAiState.Hidden)
        assertTrue(updated[1].state.advancedAiState is LookupPopupAdvancedAiState.Error)
        val error = updated[1].state.advancedAiState as LookupPopupAdvancedAiState.Error
        assertEquals(AdvancedAiCardKind.Word, error.kind)
    }

    private fun popup(id: String): LookupPopupItem =
        LookupPopupItem(
            id = id,
            state = LookupPopupState(
                selection = ReaderSelectionData(
                    text = "read",
                    sentence = "I read the line aloud.",
                    rect = ReaderSelectionRect(0.0, 0.0, 1.0, 1.0),
                    normalizedOffset = 2,
                    sentenceOffset = 2,
                ),
                results = emptyList(),
            ),
        )

    private fun resolveUiText(text: UiText): String =
        when (text) {
            is UiText.Literal -> text.value
            is UiText.Resource -> when (text.id) {
                R.string.advanced_ai_word_analysis_title -> "AI 词语分析"
                R.string.advanced_ai_sentence_analysis_title -> "长难句分析"
                R.string.advanced_ai_loading -> "分析中"
                R.string.advanced_ai_request_failed -> "请求失败"
                else -> error("Unexpected resource ${text.id}")
            }
            is UiText.Plural -> error("Unexpected plural ${text.id}")
        }
}
