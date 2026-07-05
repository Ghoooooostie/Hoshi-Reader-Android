package moe.antimony.hoshi.features.reader

import androidx.annotation.StringRes
import moe.antimony.hoshi.R
import moe.antimony.hoshi.features.advancedai.AdvancedAiAvailability
import moe.antimony.hoshi.features.advancedai.AdvancedAiClient
import moe.antimony.hoshi.features.advancedai.AdvancedAiSettings
import moe.antimony.hoshi.features.advancedai.AdvancedAiCardKind
import moe.antimony.hoshi.features.advancedai.LookupPopupAdvancedAiModeOptionPayload
import moe.antimony.hoshi.features.advancedai.LookupPopupAdvancedAiPayload
import moe.antimony.hoshi.features.advancedai.LookupPopupAdvancedAiState
import moe.antimony.hoshi.features.advancedai.sentenceAvailability
import moe.antimony.hoshi.features.advancedai.sentenceTranslationAvailability
import moe.antimony.hoshi.features.dictionary.LookupPopupItem
import moe.antimony.hoshi.features.dictionary.LookupPopupOptions
import moe.antimony.hoshi.features.dictionary.LookupPopupState
import moe.antimony.hoshi.ui.UiText

internal fun readerAiLongPressSentence(selection: ReaderSelectionData): String =
    selection.sentence.ifBlank { selection.text }.trim()

internal fun readerAiLongPressAvailability(
    settings: AdvancedAiSettings,
    mode: ReaderAiLongPressMode,
): AdvancedAiAvailability =
    when (mode) {
        ReaderAiLongPressMode.Translation -> settings.sentenceTranslationAvailability()
        ReaderAiLongPressMode.Analysis -> settings.sentenceAvailability()
    }

internal suspend fun AdvancedAiClient.requestReaderAiLongPressContent(
    settings: AdvancedAiSettings,
    mode: ReaderAiLongPressMode,
    sentence: String,
): String =
    when (mode) {
        ReaderAiLongPressMode.Translation -> translateSentence(settings, sentence)
        ReaderAiLongPressMode.Analysis -> analyzeSentence(settings, sentence)
    }

internal fun createReaderAiPopupItem(
    selection: ReaderSelectionData,
    options: LookupPopupOptions,
): LookupPopupItem =
    LookupPopupItem(
        state = LookupPopupState(
            selection = selection,
            results = emptyList(),
            advancedAiState = LookupPopupAdvancedAiState.Loading(AdvancedAiCardKind.Sentence),
            dictionarySettings = options.dictionarySettings.normalized(),
            isVertical = options.isVertical,
            isFullWidth = options.isFullWidth,
            width = options.width,
            height = options.height,
            swipeToDismiss = options.swipeToDismiss,
            swipeThreshold = options.swipeThreshold,
            reducedMotionScrolling = options.reducedMotionScrolling,
            reducedMotionScrollPercent = options.reducedMotionScrollPercent,
            reducedMotionSwipeThreshold = options.reducedMotionSwipeThreshold,
            popupScale = options.popupScale,
            topInset = options.topInset,
            bottomInset = options.bottomInset,
            darkMode = options.darkMode,
            eInkMode = options.eInkMode,
            audioSettings = options.audioSettings,
            popupActionBar = options.popupActionBar,
            contentLanguageProfile = options.contentLanguageProfile,
        ),
    )

internal fun LookupPopupAdvancedAiState.toReaderAiPopupPayload(
    mode: ReaderAiLongPressMode,
    resolve: (UiText) -> String,
): LookupPopupAdvancedAiPayload? =
    when (this) {
        LookupPopupAdvancedAiState.Hidden -> null
        is LookupPopupAdvancedAiState.Loading -> LookupPopupAdvancedAiPayload(
            title = resolve(UiText.Resource(readerAiLongPressTitleResId(mode))),
            status = "loading",
            body = resolve(UiText.Resource(readerAiLongPressLoadingResId(mode))),
            modeOptions = readerAiModeOptions(selectedMode = mode, resolve = resolve),
        )
        is LookupPopupAdvancedAiState.Success -> LookupPopupAdvancedAiPayload(
            title = resolve(UiText.Resource(readerAiLongPressTitleResId(mode))),
            status = "success",
            body = content,
            modeOptions = readerAiModeOptions(selectedMode = mode, resolve = resolve),
        )
        is LookupPopupAdvancedAiState.Error -> LookupPopupAdvancedAiPayload(
            title = resolve(UiText.Resource(readerAiLongPressTitleResId(mode))),
            status = "error",
            body = resolve(message),
            modeOptions = readerAiModeOptions(selectedMode = mode, resolve = resolve),
        )
    }

private fun readerAiModeOptions(
    selectedMode: ReaderAiLongPressMode,
    resolve: (UiText) -> String,
): List<LookupPopupAdvancedAiModeOptionPayload> =
    ReaderAiLongPressMode.entries.map { mode ->
        LookupPopupAdvancedAiModeOptionPayload(
            value = mode.name,
            title = resolve(UiText.Resource(readerAiLongPressTitleResId(mode))),
            selected = mode == selectedMode,
        )
    }

@StringRes
internal fun readerAiLongPressTitleResId(mode: ReaderAiLongPressMode): Int =
    when (mode) {
        ReaderAiLongPressMode.Translation -> R.string.reader_translation_ai_mode_translation
        ReaderAiLongPressMode.Analysis -> R.string.reader_translation_ai_mode_analysis
    }

@StringRes
internal fun readerAiLongPressLoadingResId(mode: ReaderAiLongPressMode): Int =
    when (mode) {
        ReaderAiLongPressMode.Translation -> R.string.reader_translation_ai_loading
        ReaderAiLongPressMode.Analysis -> R.string.advanced_ai_loading
    }

@StringRes
internal fun readerAiLongPressRequestFailedResId(mode: ReaderAiLongPressMode): Int =
    when (mode) {
        ReaderAiLongPressMode.Translation -> R.string.reader_translation_ai_request_failed
        ReaderAiLongPressMode.Analysis -> R.string.advanced_ai_request_failed
    }
