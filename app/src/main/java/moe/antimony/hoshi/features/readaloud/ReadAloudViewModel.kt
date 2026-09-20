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
    private val modelRepository: ReadAloudModelRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val state: StateFlow<ReadAloudState> = controller.state

    val settings: StateFlow<ReadAloudSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ReadAloudSettings(),
    )

    val availableModels: List<RecommendedTtsModel> = RecommendedTtsModels

    val downloadProgress: StateFlow<Map<String, ReadAloudModelDownloadProgress>> =
        modelRepository.downloadProgress

    val sleepTimerMinutes: StateFlow<Int> = controller.state
        .map { it.sleepTimerMinutes }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** See [ReadAloudController.queueExhausted]. */
    val queueExhausted: SharedFlow<Unit> = controller.queueExhausted

    val installedModels: StateFlow<List<RecommendedTtsModel>> =
        downloadProgress.map { installedTtsModels(context.filesDir) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, installedTtsModels(context.filesDir))

    fun start(items: List<ReadAloudQueueItem>, title: String? = null, subtitle: String? = null) {
        controller.start(items, title, subtitle)
    }

    fun continueWith(items: List<ReadAloudQueueItem>) {
        controller.continueWith(items)
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

    fun setEngine(engineId: ReadAloudEngineId) {
        viewModelScope.launch { settingsRepository.update { it.copy(engineId = engineId) } }
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

    fun selectModel(modelId: String) {
        viewModelScope.launch { settingsRepository.update { it.copy(selectedModelId = modelId) } }
    }

    fun downloadModel(model: RecommendedTtsModel) {
        modelRepository.startDownload(model)
    }

    fun deleteModel(model: RecommendedTtsModel) {
        modelRepository.delete(model)
    }
}
