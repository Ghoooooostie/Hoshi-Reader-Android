package moe.antimony.hoshi.features.readaloud

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.antimony.hoshi.R
import moe.antimony.hoshi.di.ApplicationScope
import moe.antimony.hoshi.ui.UiText

/**
 * Drives sentence-by-sentence read aloud playback for the reader.
 * It owns the playback queue and state only; speech is delegated to a [ReadAloudEngine].
 */
@Singleton
class ReadAloudController @Inject constructor(
    private val settingsRepository: ReadAloudSettingsRepository,
    private val systemEngine: SystemReadAloudEngine,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(ReadAloudState())
    val state: StateFlow<ReadAloudState> = mutableState.asStateFlow()

    private var playbackJob: Job? = null
    private var speechRate = ReadAloudSettings.DefaultSpeechRate

    init {
        applicationScope.launch {
            settingsRepository.settings.collect { settings ->
                speechRate = settings.speechRate
                systemEngine.setSpeechRate(settings.speechRate)
            }
        }
    }

    fun start(sentences: List<String>, startIndex: Int = 0) {
        if (sentences.isEmpty()) return
        playbackJob?.cancel()
        mutableState.value = ReadAloudState(sentences = sentences, isActive = true)
        playFrom(startIndex.coerceIn(0, sentences.lastIndex))
    }

    fun pause() {
        playbackJob?.cancel()
        systemEngine.stop()
        update { copy(isPlaying = false) }
    }

    fun resume() {
        val current = mutableState.value
        if (!current.isActive || current.isPlaying) return
        playFrom(current.currentIndex.coerceAtLeast(0))
    }

    fun skipPrevious() {
        val current = mutableState.value
        if (!current.isActive) return
        playFrom((current.currentIndex - 1).coerceAtLeast(0))
    }

    fun skipNext() {
        val current = mutableState.value
        if (!current.isActive) return
        val next = current.currentIndex + 1
        if (next >= current.sentences.size) {
            stop()
            return
        }
        playFrom(next)
    }

    fun stop() {
        playbackJob?.cancel()
        systemEngine.stop()
        mutableState.value = mutableState.value.copy(
            isActive = false,
            isPlaying = false,
            currentIndex = -1,
            sentences = emptyList(),
        )
    }

    fun dismissError() {
        update { copy(error = null) }
    }

    suspend fun setSpeechRate(rate: Float) {
        settingsRepository.update { settings -> settings.copy(speechRate = rate) }
    }

    private fun playFrom(index: Int) {
        playbackJob?.cancel()
        playbackJob = applicationScope.launch {
            if (!systemEngine.prepare()) {
                update {
                    copy(
                        isPlaying = false,
                        error = UiText.Resource(R.string.read_aloud_error_japanese_voice_missing),
                    )
                }
                return@launch
            }
            systemEngine.setSpeechRate(speechRate)
            var cursor = index
            while (isActive && cursor in mutableState.value.sentences.indices) {
                update { copy(currentIndex = cursor, isPlaying = true) }
                val played = systemEngine.speak(mutableState.value.sentences[cursor])
                if (!played) return@launch
                cursor++
            }
            update { copy(isPlaying = false) }
            if (cursor >= mutableState.value.sentences.size) {
                mutableState.value = mutableState.value.copy(isActive = false, currentIndex = -1)
            }
        }
    }

    private inline fun update(transform: ReadAloudState.() -> ReadAloudState) {
        mutableState.value = mutableState.value.transform()
    }
}
