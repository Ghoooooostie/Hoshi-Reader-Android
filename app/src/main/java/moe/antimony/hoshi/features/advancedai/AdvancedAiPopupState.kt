package moe.antimony.hoshi.features.advancedai

import kotlinx.serialization.Serializable
import moe.antimony.hoshi.R
import moe.antimony.hoshi.features.dictionary.LookupPopupItem
import moe.antimony.hoshi.ui.UiText

/** 区分词语分析和长难句分析卡片。 */
internal enum class AdvancedAiCardKind {
    Word,
    Sentence,
}

/** popup 顶部 AI 区块的可序列化展示数据。 */
@Serializable
internal data class LookupPopupAdvancedAiPayload(
    val title: String,
    val status: String,
    val body: String,
)

/** popup 顶部 AI 区块的状态。 */
internal sealed interface LookupPopupAdvancedAiState {
    data object Hidden : LookupPopupAdvancedAiState

    data class Loading(
        val kind: AdvancedAiCardKind,
    ) : LookupPopupAdvancedAiState

    data class Success(
        val kind: AdvancedAiCardKind,
        val content: String,
    ) : LookupPopupAdvancedAiState

    data class Error(
        val kind: AdvancedAiCardKind,
        val message: UiText,
    ) : LookupPopupAdvancedAiState
}

/** 转成 popup iframe 需要的简单载荷。 */
internal fun LookupPopupAdvancedAiState.toPayload(
    resolve: (UiText) -> String,
): LookupPopupAdvancedAiPayload? =
    when (this) {
        LookupPopupAdvancedAiState.Hidden -> null
        is LookupPopupAdvancedAiState.Loading -> LookupPopupAdvancedAiPayload(
            title = resolve(UiText.Resource(kind.titleResId())),
            status = "loading",
            body = resolve(UiText.Resource(R.string.advanced_ai_loading)),
        )
        is LookupPopupAdvancedAiState.Success -> LookupPopupAdvancedAiPayload(
            title = resolve(UiText.Resource(kind.titleResId())),
            status = "success",
            body = content,
        )
        is LookupPopupAdvancedAiState.Error -> LookupPopupAdvancedAiPayload(
            title = resolve(UiText.Resource(kind.titleResId())),
            status = "error",
            body = resolve(message),
        )
    }

/** 只更新目标 popup 的 AI 状态。 */
internal fun List<LookupPopupItem>.updateAdvancedAiState(
    popupId: String,
    nextState: LookupPopupAdvancedAiState,
): List<LookupPopupItem> =
    map { popup ->
        if (popup.id == popupId) {
            popup.copy(state = popup.state.copy(advancedAiState = nextState))
        } else {
            popup
        }
    }

/** 读取词语分析成功文案。 */
internal fun LookupPopupAdvancedAiState.wordSuccessContent(): String? =
    (this as? LookupPopupAdvancedAiState.Success)
        ?.takeIf { it.kind == AdvancedAiCardKind.Word }
        ?.content

/** 读取长难句分析成功文案。 */
internal fun LookupPopupAdvancedAiState.sentenceSuccessContent(): String? =
    (this as? LookupPopupAdvancedAiState.Success)
        ?.takeIf { it.kind == AdvancedAiCardKind.Sentence }
        ?.content

/** 返回卡片标题资源。 */
private fun AdvancedAiCardKind.titleResId(): Int =
    when (this) {
        AdvancedAiCardKind.Word -> R.string.advanced_ai_word_analysis_title
        AdvancedAiCardKind.Sentence -> R.string.advanced_ai_sentence_analysis_title
    }
