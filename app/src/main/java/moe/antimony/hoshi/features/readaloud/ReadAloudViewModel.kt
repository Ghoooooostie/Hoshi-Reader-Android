package moe.antimony.hoshi.features.readaloud

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ReadAloudViewModel @Inject constructor(
    private val controller: ReadAloudController,
    private val settingsRepository: ReadAloudSettingsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val state: StateFlow<ReadAloudState> = controller.state

    val settings: StateFlow<ReadAloudSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ReadAloudSettings(),
    )

    val sleepTimerMinutes: StateFlow<Int> = controller.state
        .map { it.sleepTimerMinutes }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** See [ReadAloudController.queueExhausted]. */
    val queueExhausted: SharedFlow<Unit> = controller.queueExhausted

    fun start(
        items: List<ReadAloudQueueItem>,
        title: String? = null,
        subtitle: String? = null,
        startIndex: Int = 0,
    ) {
        controller.start(items, title, subtitle, startIndex)
    }

    fun continueWith(items: List<ReadAloudQueueItem>) {
        controller.continueWith(items)
    }

    /** See [ReadAloudController.retarget]. */
    fun retarget(items: List<ReadAloudQueueItem>) {
        controller.retarget(items)
    }

    fun pause() = controller.pause()

    fun resume() = controller.resume()

    fun skipPrevious() = controller.skipPrevious()

    fun skipNext() = controller.skipNext()

    fun stop() = controller.stop()

    fun dismissError() = controller.dismissError()

    fun setSpeechRate(rate: Float) {
        viewModelScope.launch { controller.setSpeechRate(rate) }
    }

    fun setSentenceRepeatCount(count: Int) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(sentenceRepeatCount = count) }
        }
    }

    suspend fun availableSystemEngines(): List<TextToSpeech.EngineInfo> = withContext(Dispatchers.IO) {
        val initialized = CompletableDeferred<Boolean>()
        val tts = TextToSpeech(context) { status ->
            initialized.complete(status == TextToSpeech.SUCCESS)
        }
        val ready = runCatching { initialized.await() }.getOrDefault(false)
        val engines = if (ready) tts.engines ?: emptyList() else emptyList()
        runCatching { tts.shutdown() }
        engines
    }

    fun setSystemEngine(engineName: String?) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(selectedSystemEngineName = engineName) }
        }
    }

    fun updateSettings(transform: (ReadAloudSettings) -> ReadAloudSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun cycleSleepTimer() = controller.cycleSleepTimer()
}
