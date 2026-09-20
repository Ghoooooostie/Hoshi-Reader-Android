package moe.antimony.hoshi.features.readaloud

import moe.antimony.hoshi.ui.UiText

/**
 * A speech backend that speaks one sentence at a time.
 *
 * Implementations own their own playback: [speak] suspends until the sentence has
 * finished playing, and returns false when it was interrupted or could not be played.
 */
interface ReadAloudEngine {
    val id: ReadAloudEngineId

    /** Prepares the backend for Japanese speech. Returns false when it cannot speak. */
    suspend fun prepare(): Boolean

    suspend fun speak(text: String): Boolean

    fun stop()

    fun setSpeechRate(rate: Float)

    fun release()
}

/**
 * One queued utterance. [paragraphId] is the reader DOM element id the text came from, so the
 * reader can highlight and follow the currently spoken paragraph; it is null for page-level
 * utterances (按页朗读) that span multiple paragraphs.
 */
data class ReadAloudQueueItem(
    val text: String,
    val paragraphId: String? = null,
)

data class ReadAloudState(
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
    val items: List<ReadAloudQueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val error: UiText? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val sleepTimerMinutes: Int = 0,
) {
    val currentSentence: String?
        get() = items.getOrNull(currentIndex)?.text

    /** Id of the reader paragraph currently being spoken, used for highlight/follow sync. */
    val currentParagraphId: String?
        get() = items.getOrNull(currentIndex)?.paragraphId

    val sentenceCount: Int
        get() = items.size
}
