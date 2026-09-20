package moe.antimony.hoshi.features.readaloud

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Speaks through the platform text-to-speech engine, so the voice follows the
 * Japanese voice data the user already installed on the device.
 */
@Singleton
class SystemReadAloudEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: ReadAloudSettingsRepository,
) : ReadAloudEngine {
    override val id: ReadAloudEngineId = ReadAloudEngineId.System

    private var textToSpeech: TextToSpeech? = null
    private var japaneseReady = false
    private var speechRate = ReadAloudSettings.DefaultSpeechRate
    private var utteranceCounter = 0
    private var systemEngineName: String? = null
    private val pending = ConcurrentHashMap<String, CancellableContinuation<Boolean>>()

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) = finish(utteranceId, played = true)

        override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId, played = false)

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onError(utteranceId: String?) = finish(utteranceId, played = false)
    }

    override suspend fun prepare(): Boolean = withContext(Dispatchers.Main.immediate) {
        val preferredName =
            settingsRepository.settings.first().selectedSystemEngineName?.takeIf { it.isNotEmpty() }
        // When no engine is explicitly chosen, also try every installed system TTS engine so the
        // read-aloud fallback "just works" with whatever Japanese engine the device has (e.g. one
        // installed from an APK). Some engines report LANG_NOT_SUPPORTED yet still speak Japanese.
        val candidates: List<String?> = if (preferredName != null) {
            listOf(preferredName)
        } else {
            buildList<String?> {
                add(null) // device default engine
                addAll(availableEngineNames())
            }
        }
        for (name in candidates) {
            val engine = ensureEngine(name) ?: continue
            val status = engine.setLanguage(Locale.JAPANESE)
            if (status == TextToSpeech.LANG_MISSING_DATA) continue
            engine.setSpeechRate(speechRate)
            japaneseReady = true
            systemEngineName = name
            return@withContext true
        }
        japaneseReady = false
        false
    }

    override suspend fun speak(text: String): Boolean {
        if (text.isBlank()) return true
        val engine = withContext(Dispatchers.Main.immediate) { ensureEngine() } ?: return false
        if (!japaneseReady) return false
        return suspendCancellableCoroutine { continuation ->
            val utteranceId = "hoshi-read-aloud-${utteranceCounter++}"
            pending[utteranceId] = continuation
            continuation.invokeOnCancellation {
                pending.remove(utteranceId)
                runCatching { engine.stop() }
            }
            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            }
            val queued = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (queued != TextToSpeech.SUCCESS) {
                pending.remove(utteranceId)?.resume(false)
            }
        }
    }

    override fun stop() {
        val engine = textToSpeech ?: return
        pending.keys.toList().forEach { utteranceId ->
            pending.remove(utteranceId)?.takeIf { it.isActive }?.resume(false)
        }
        runCatching { engine.stop() }
    }

    override fun setSpeechRate(rate: Float) {
        speechRate = rate
        runCatching { textToSpeech?.setSpeechRate(rate) }
    }

    override fun release() {
        releaseInternal()
        japaneseReady = false
        systemEngineName = null
    }

    private fun releaseInternal() {
        pending.clear()
        runCatching { textToSpeech?.shutdown() }
        textToSpeech = null
    }

    private suspend fun availableEngineNames(): List<String> = withContext(Dispatchers.IO) {
        val initialized = CompletableDeferred<Boolean>()
        val probe = TextToSpeech(context) { status ->
            initialized.complete(status == TextToSpeech.SUCCESS)
        }
        val ready = runCatching { initialized.await() }.getOrDefault(false)
        val names = if (ready) {
            probe.engines?.mapNotNull { it.name }?.distinct() ?: emptyList()
        } else {
            emptyList()
        }
        runCatching { probe.shutdown() }
        names
    }

    private suspend fun ensureEngine(): TextToSpeech? = ensureEngine(systemEngineName)

    private suspend fun ensureEngine(name: String?): TextToSpeech? {
        if (textToSpeech != null && systemEngineName == name) return textToSpeech
        releaseInternal()
        val initialized = CompletableDeferred<Boolean>()
        val engine = if (name != null) {
            TextToSpeech(context, { status ->
                initialized.complete(status == TextToSpeech.SUCCESS)
            }, name)
        } else {
            TextToSpeech(context) { status ->
                initialized.complete(status == TextToSpeech.SUCCESS)
            }
        }
        if (!initialized.await()) {
            runCatching { engine.shutdown() }
            return null
        }
        engine.setOnUtteranceProgressListener(progressListener)
        textToSpeech = engine
        systemEngineName = name
        return engine
    }

    private fun finish(utteranceId: String?, played: Boolean) {
        val id = utteranceId ?: return
        pending.remove(id)?.takeIf { it.isActive }?.resume(played)
    }
}
