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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * TTS 引擎初始化回调超时。第三方引擎（如 MultiTTS）的服务被系统回收/重启时，
 * [TextToSpeech] 的初始化回调可能永远不来，此时不能无限等待。
 */
private const val TtsInitializationTimeoutMillis = 4_000L

/**
 * 一次朗读既不回调 onStart 也不回调 onDone/onError 时的兜底超时：此时 [speak] 会永远挂起，
 * 上层朗读会停在"播放中"却毫无声音和推进，用户看到的就是"朗读突然停止"。
 */
private const val UtteranceStartTimeoutMillis = 15_000L

/**
 * Speaks through the platform text-to-speech engine, so the voice follows the
 * Japanese voice data the user already installed on the device.
 */
@Singleton
class SystemReadAloudEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: ReadAloudSettingsRepository,
) : ReadAloudEngine {
    private var textToSpeech: TextToSpeech? = null
    private var japaneseReady = false
    private var speechRate = ReadAloudSettings.DefaultSpeechRate
    private var utteranceCounter = 0
    private var systemEngineName: String? = null
    private val pending = ConcurrentHashMap<String, CancellableContinuation<Boolean>>()
    private val utteranceStartWatchdogs = ConcurrentHashMap<String, Job>()
    private val watchdogScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 引擎是否上报过 onStart；只有支持该回调的引擎才启用启动看门狗。 */
    private var engineReportsUtteranceStart = false

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            engineReportsUtteranceStart = true
            utteranceId?.let { utteranceStartWatchdogs.remove(it)?.cancel() }
        }

        override fun onDone(utteranceId: String?) = finish(utteranceId, played = true)

        override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId, played = false)

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onError(utteranceId: String?) {
            // 连 onStart 都没上报就报错，通常说明引擎连接已经不可用（第三方引擎服务被回收/重启）：
            // 丢弃连接让下一次 prepare() 重新建立，否则会在坏连接上反复失败、朗读一直起不来。
            val neverStarted = utteranceId != null && utteranceStartWatchdogs.containsKey(utteranceId)
            finish(utteranceId, played = false)
            if (neverStarted) releaseInternal()
        }
    }

    override suspend fun prepare(): Boolean = withContext(Dispatchers.Main.immediate) {
        val preferredName =
            settingsRepository.settings.first().selectedSystemEngineName?.takeIf { it.isNotEmpty() }
        // 已就绪的引擎直接复用：跟读翻页模式（VN 每换一屏都会重建队列）会频繁调用 prepare，
        // 反复重连/枚举 TTS 引擎既慢，又可能撞上引擎服务偶发不回调而失败，
        // 表现为"朗读中途突然停止"。
        if (japaneseReady && textToSpeech != null && (preferredName == null || systemEngineName == preferredName)) {
            runCatching { textToSpeech?.setSpeechRate(speechRate) }
            return@withContext true
        }
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
            armUtteranceStartWatchdog(utteranceId)
            continuation.invokeOnCancellation {
                pending.remove(utteranceId)
                utteranceStartWatchdogs.remove(utteranceId)?.cancel()
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
        utteranceStartWatchdogs.values.forEach(Job::cancel)
        utteranceStartWatchdogs.clear()
        runCatching { engine.stop() }
    }

    /**
     * 引擎既不上报 onStart 也不上报结束回调时，[speak] 会一直挂起，上层朗读就卡在"播放中"
     * 却没有任何声音和推进。对已确认会上报 onStart 的引擎启用兜底超时，把它转成一次失败。
     */
    private fun armUtteranceStartWatchdog(utteranceId: String) {
        if (!engineReportsUtteranceStart) return
        utteranceStartWatchdogs[utteranceId] = watchdogScope.launch {
            delay(UtteranceStartTimeoutMillis)
            finish(utteranceId, played = false)
        }
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
        utteranceStartWatchdogs.values.forEach(Job::cancel)
        utteranceStartWatchdogs.clear()
        runCatching { textToSpeech?.shutdown() }
        textToSpeech = null
    }

    private suspend fun availableEngineNames(): List<String> = withContext(Dispatchers.IO) {
        val initialized = CompletableDeferred<Boolean>()
        val probe = TextToSpeech(context) { status ->
            initialized.complete(status == TextToSpeech.SUCCESS)
        }
        val ready = awaitInitialization(initialized)
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
        if (!awaitInitialization(initialized)) {
            runCatching { engine.shutdown() }
            return null
        }
        engine.setOnUtteranceProgressListener(progressListener)
        textToSpeech = engine
        systemEngineName = name
        return engine
    }

    /** 引擎初始化回调可能永远不来（服务被回收），超时按失败处理，不能无限等待。 */
    private suspend fun awaitInitialization(initialized: CompletableDeferred<Boolean>): Boolean =
        withTimeoutOrNull(TtsInitializationTimeoutMillis) { initialized.await() } ?: false

    private fun finish(utteranceId: String?, played: Boolean) {
        val id = utteranceId ?: return
        utteranceStartWatchdogs.remove(id)?.cancel()
        pending.remove(id)?.takeIf { it.isActive }?.resume(played)
    }
}
