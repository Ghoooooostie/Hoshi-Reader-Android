package moe.antimony.hoshi.features.readaloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ReadAloudViewModel @Inject constructor(
    private val controller: ReadAloudController,
    settingsRepository: ReadAloudSettingsRepository,
) : ViewModel() {
    val state: StateFlow<ReadAloudState> = controller.state

    val settings: StateFlow<ReadAloudSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ReadAloudSettings(),
    )

    fun start(sentences: List<String>) {
        controller.start(sentences)
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
}
