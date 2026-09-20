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

data class ReadAloudState(
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
    val sentences: List<String> = emptyList(),
    val currentIndex: Int = -1,
    val error: UiText? = null,
) {
    val currentSentence: String?
        get() = sentences.getOrNull(currentIndex)

    val sentenceCount: Int
        get() = sentences.size
}
