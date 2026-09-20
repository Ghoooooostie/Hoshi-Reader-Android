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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Speaks through the platform text-to-speech engine, so the voice follows the
 * Japanese voice data the user already installed on the device.
 */
@Singleton
class SystemReadAloudEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ReadAloudEngine {
    override val id: ReadAloudEngineId = ReadAloudEngineId.System

    private var textToSpeech: TextToSpeech? = null
    private var japaneseReady = false
    private var speechRate = ReadAloudSettings.DefaultSpeechRate
    private var utteranceCounter = 0
    private val pending = ConcurrentHashMap<String, CancellableContinuation<Boolean>>()

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) = finish(utteranceId, played = true)

        override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId, played = false)

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onError(utteranceId: String?) = finish(utteranceId, played = false)
    }

    override suspend fun prepare(): Boolean = withContext(Dispatchers.Main.immediate) {
        val engine = ensureEngine() ?: return@withContext false
        val status = engine.setLanguage(Locale.JAPANESE)
        japaneseReady = status != TextToSpeech.LANG_MISSING_DATA && status != TextToSpeech.LANG_NOT_SUPPORTED
        engine.setSpeechRate(speechRate)
        japaneseReady
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
        pending.clear()
        runCatching { textToSpeech?.shutdown() }
        textToSpeech = null
        japaneseReady = false
    }

    private suspend fun ensureEngine(): TextToSpeech? {
        textToSpeech?.let { return it }
        val initialized = CompletableDeferred<Boolean>()
        val engine = TextToSpeech(context) { status ->
            initialized.complete(status == TextToSpeech.SUCCESS)
        }
        if (!initialized.await()) {
            runCatching { engine.shutdown() }
            return null
        }
        engine.setOnUtteranceProgressListener(progressListener)
        textToSpeech = engine
        return engine
    }

    private fun finish(utteranceId: String?, played: Boolean) {
        val id = utteranceId ?: return
        pending.remove(id)?.takeIf { it.isActive }?.resume(played)
    }
}
