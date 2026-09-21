package moe.antimony.hoshi.features.readaloud

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.content.Context.TELEPHONY_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.antimony.hoshi.R
import moe.antimony.hoshi.di.ApplicationScope
import moe.antimony.hoshi.ui.UiText

/**
 * Drives sentence-by-sentence read aloud playback for the reader.
 * It owns the playback queue and state only; speech is delegated to a [ReadAloudEngine]
 * chosen from the user's engine preference (system TTS or a downloaded local model).
 *
 * Mirrors the media-aware behaviour of legadoT's read-aloud service: it requests audio
 * focus, pauses when headphones are unplugged or a phone call interrupts, and supports a
 * sleep timer (minutes). A foreground [ReadAloudService] mirrors this state into a
 * MediaSession + notification for lock-screen / shade controls.
 */
@Singleton
class ReadAloudController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: ReadAloudSettingsRepository,
    private val systemEngine: SystemReadAloudEngine,
    private val localEngine: LocalReadAloudEngine,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : AudioManager.OnAudioFocusChangeListener {

    private val mutableState = MutableStateFlow(ReadAloudState())
    val state: StateFlow<ReadAloudState> = mutableState.asStateFlow()

    /**
     * Emitted exactly once each time the sentence queue finishes naturally while the session
     * is still active. The reader listens for it to turn/scroll to the next page and continue
     * playback (跟读翻页), or stop when the chapter end is reached.
     */
    private val mutableQueueExhausted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val queueExhausted: SharedFlow<Unit> = mutableQueueExhausted.asSharedFlow()

    private var playbackJob: Job? = null
    private var speechRate = ReadAloudSettings.DefaultSpeechRate
    private var activeEngine: ReadAloudEngine = systemEngine
    private var ignoreAudioFocus = false
    private var pauseWhilePhoneCalls = false
    private var mediaButtonPerNext = false

    private val audioManager = context.getSystemService(AUDIO_SERVICE) as AudioManager
    private val focusRequest: AudioFocusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setOnAudioFocusChangeListener(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build(),
            )
            .setWillPauseWhenDucked(true)
            .build()
    }
    private var focusRequested = false
    private var needResumeOnFocusGain = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pause(abandonFocus = false)
            }
        }
    }
    private var noisyRegistered = false

    private var sleepTimerJob: Job? = null

    init {
        var prevEngineId: ReadAloudEngineId? = null
        var prevSelectedSystemEngineName: String? = null
        applicationScope.launch {
            settingsRepository.settings.collect { settings ->
                speechRate = settings.speechRate
                ignoreAudioFocus = settings.ignoreAudioFocus
                pauseWhilePhoneCalls = settings.pauseWhilePhoneCalls
                mediaButtonPerNext = settings.mediaButtonPerNext
                if (!settings.pauseWhilePhoneCalls) unregisterPhoneStateListener()
                systemEngine.setSpeechRate(settings.speechRate)
                localEngine.setSpeechRate(settings.speechRate)

                // 切换引擎（系统/本地，或在系统引擎下切换具体语音）后即时生效：
                // 正在朗读时用新引擎从当前句重新开始，避免继续用旧引擎读完本章。
                val engineBackendChanged = settings.engineId != prevEngineId
                val systemVoiceChanged = settings.selectedSystemEngineName != prevSelectedSystemEngineName
                val affectsActive = engineBackendChanged ||
                    (systemVoiceChanged && settings.engineId == ReadAloudEngineId.System)
                prevEngineId = settings.engineId
                prevSelectedSystemEngineName = settings.selectedSystemEngineName
                if (affectsActive && mutableState.value.isActive && mutableState.value.isPlaying) {
                    val restartAt = mutableState.value.currentIndex.coerceAtLeast(0)
                    playbackJob?.cancel()
                    activeEngine.stop()
                    playFrom(restartAt)
                }
            }
        }
    }

    private suspend fun selectEngine(): ReadAloudEngine {
        val engineId = settingsRepository.settings.first().engineId
        activeEngine = if (engineId == ReadAloudEngineId.Local) localEngine else systemEngine
        return activeEngine
    }

    /**
     * Prepares the preferred engine, falling back to the other backend when it cannot be
     * prepared (e.g. the local model is missing/corrupt). Returns the engine to use, or null
     * if neither backend is available. Never lets a failed local model crash the app.
     */
    private suspend fun prepareEngine(preferred: ReadAloudEngine): ReadAloudEngine? {
        activeEngine = preferred
        if (preferred.prepare()) return preferred
        val fallback = if (preferred.id == ReadAloudEngineId.Local) systemEngine else localEngine
        activeEngine = fallback
        if (fallback.prepare()) return fallback
        val settings = settingsRepository.settings.first()
        val errorRes = if (settings.engineId == ReadAloudEngineId.Local) {
            R.string.read_aloud_error_local_model_unavailable
        } else {
            R.string.read_aloud_error_japanese_voice_missing
        }
        update { copy(isPlaying = false, error = UiText.Resource(errorRes)) }
        return null
    }

    fun start(
        items: List<ReadAloudQueueItem>,
        title: String? = null,
        subtitle: String? = null,
        startIndex: Int = 0,
    ) {
        if (items.isEmpty()) return
        playbackJob?.cancel()
        mutableState.value = ReadAloudState(
            items = items,
            isActive = true,
            title = title,
            subtitle = subtitle,
        )
        playFrom(startIndex.coerceIn(0, items.lastIndex))
    }

    /**
     * Continues playback with a new queue (next page) while keeping the session active, so the
     * media session / notification do not flash away between pages.
     */
    fun continueWith(items: List<ReadAloudQueueItem>) {
        if (!mutableState.value.isActive) return
        if (items.isEmpty()) {
            stop()
            return
        }
        playbackJob?.cancel()
        mutableState.value = mutableState.value.copy(items = items, currentIndex = -1)
        playFrom(0)
    }

    fun pause(abandonFocus: Boolean = true) {
        playbackJob?.cancel()
        activeEngine.stop()
        if (abandonFocus) releaseAudioFocus()
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
        if (mediaButtonPerNext) {
            playFrom(paragraphBoundaryIndex(current, current.currentIndex, backwards = true))
        } else {
            playFrom((current.currentIndex - 1).coerceAtLeast(0))
        }
    }

    fun skipNext() {
        val current = mutableState.value
        if (!current.isActive) return
        val next = if (mediaButtonPerNext) {
            paragraphBoundaryIndex(current, current.currentIndex, backwards = false)
        } else {
            current.currentIndex + 1
        }
        if (next >= current.items.size) {
            stop()
            return
        }
        playFrom(next)
    }

    /** Returns the first index of the previous/next paragraph, mirroring legadoT's prevP/nextP. */
    private fun paragraphBoundaryIndex(state: ReadAloudState, index: Int, backwards: Boolean): Int {
        fun paragraphIdAt(i: Int): String? = state.items.getOrNull(i)?.paragraphId
        val currentId = paragraphIdAt(index)
        return if (backwards) {
            var start = index
            while (start > 0 && paragraphIdAt(start - 1) == currentId) start--
            if (start < index) {
                start
            } else {
                var previous = start - 1
                while (previous > 0 && paragraphIdAt(previous - 1) == paragraphIdAt(previous)) previous--
                previous.coerceAtLeast(0)
            }
        } else {
            var next = index + 1
            while (next < state.items.size && paragraphIdAt(next) == currentId) next++
            next
        }
    }

    fun stop() {
        playbackJob?.cancel()
        activeEngine.stop()
        releaseAudioFocus()
        unregisterNoisy()
        unregisterPhoneStateListener()
        needResumeOnCallIdle = false
        cancelSleepTimer()
        mutableState.value = mutableState.value.copy(
            isActive = false,
            isPlaying = false,
            currentIndex = -1,
            items = emptyList(),
            sleepTimerMinutes = 0,
        )
    }

    fun dismissError() {
        update { copy(error = null) }
    }

    /**
     * Cycle the sleep timer: off -> 10 -> 20 -> ... -> 180 -> off (legadoT style).
     * Only counts down while actively playing.
     */
    fun cycleSleepTimer() {
        val next = when (val current = mutableState.value.sleepTimerMinutes) {
            0 -> 10
            in 10 until 180 -> current + 10
            else -> 0
        }
        setSleepTimer(next)
    }

    fun setSleepTimer(minutes: Int) {
        val clamped = minutes.coerceIn(0, 180)
        update { copy(sleepTimerMinutes = clamped) }
        cancelSleepTimer()
        if (clamped > 0) {
            sleepTimerJob = applicationScope.launch(Dispatchers.Main.immediate) {
                while (isActive && mutableState.value.sleepTimerMinutes > 0) {
                    delay(60_000)
                    if (!mutableState.value.isPlaying) continue
                    val remaining = mutableState.value.sleepTimerMinutes - 1
                    update { copy(sleepTimerMinutes = remaining) }
                    if (remaining <= 0) {
                        stop()
                        break
                    }
                }
            }
        }
    }

    suspend fun setSpeechRate(rate: Float) {
        settingsRepository.update { settings -> settings.copy(speechRate = rate) }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        if (ignoreAudioFocus) return
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (needResumeOnFocusGain) {
                    needResumeOnFocusGain = false
                    resume()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                pause(abandonFocus = false)
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (!mutableState.value.isPlaying) return
                needResumeOnFocusGain = true
                pause(abandonFocus = false)
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> Unit
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (ignoreAudioFocus) return true
        val result = audioManager.requestAudioFocus(focusRequest)
        focusRequested = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return focusRequested
    }

    private fun releaseAudioFocus() {
        if (focusRequested) {
            audioManager.abandonAudioFocusRequest(focusRequest)
            focusRequested = false
        }
    }

    private fun registerNoisy() {
        if (noisyRegistered) return
        context.registerReceiver(
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
        )
        noisyRegistered = true
    }

    private fun unregisterNoisy() {
        if (!noisyRegistered) return
        runCatching { context.unregisterReceiver(noisyReceiver) }
        noisyRegistered = false
    }

    // ---------------------------------------------------------------------------------------------
    // 来电期间暂停朗读：配合"忽略音频焦点"使用，因为忽略焦点后系统不会因通话自动暂停。
    // 需要 READ_PHONE_STATE 权限；未授权时保持静默（设置界面负责请求权限）。
    // ---------------------------------------------------------------------------------------------
    private val telephonyManager =
        context.getSystemService(TELEPHONY_SERVICE) as? TelephonyManager
    private var phoneStateRegistered = false
    private var needResumeOnCallIdle = false

    @Suppress("DEPRECATION")
    private val legacyPhoneStateListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            onCallStateChanged(state)
        }
    }

    private fun onCallStateChanged(callState: Int) {
        when (callState) {
            TelephonyManager.CALL_STATE_RINGING, TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (mutableState.value.isPlaying) {
                    needResumeOnCallIdle = true
                    pause(abandonFocus = false)
                }
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                if (needResumeOnCallIdle) {
                    needResumeOnCallIdle = false
                    resume()
                }
            }
        }
    }

    private fun hasReadPhoneStatePermission(): Boolean =
        context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    private fun registerPhoneStateListener() {
        if (phoneStateRegistered || !pauseWhilePhoneCalls) return
        val manager = telephonyManager ?: return
        if (!hasReadPhoneStatePermission()) return
        phoneStateRegistered = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                manager.registerTelephonyCallback(
                    Dispatchers.Main.immediate.asExecutor(),
                    telephonyCallback,
                )
            } else {
                @Suppress("DEPRECATION")
                manager.listen(legacyPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
            }
            true
        }.getOrDefault(false)
    }

    private fun unregisterPhoneStateListener() {
        if (!phoneStateRegistered) return
        runCatching {
            val manager = telephonyManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                manager.unregisterTelephonyCallback(
                    // The only callback we registered is recoverable from the registration call;
                    // keep a reference so unregister uses the same instance.
                    telephonyCallback,
                )
            } else {
                @Suppress("DEPRECATION")
                manager.listen(legacyPhoneStateListener, PhoneStateListener.LISTEN_NONE)
            }
        }
        phoneStateRegistered = false
    }

    private val telephonyCallback: TelephonyCallback by lazy {
        object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                this@ReadAloudController.onCallStateChanged(state)
            }
        }
    }

    private fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
    }

    private fun playFrom(index: Int) {
        playbackJob?.cancel()
        playbackJob = applicationScope.launch {
            if (!requestAudioFocus()) {
                update {
                    copy(
                        isPlaying = false,
                        error = UiText.Resource(R.string.read_aloud_error_audio_focus),
                    )
                }
                return@launch
            }
            registerNoisy()
            if (pauseWhilePhoneCalls) registerPhoneStateListener()
            val engine = prepareEngine(selectEngine()) ?: run {
                releaseAudioFocus()
                unregisterNoisy()
                return@launch
            }
            engine.setSpeechRate(speechRate)
            var cursor = index
            while (isActive && cursor in mutableState.value.items.indices) {
                update { copy(currentIndex = cursor, isPlaying = true) }
                val played = engine.speak(mutableState.value.items[cursor].text)
                if (!played) return@launch
                cursor++
            }
            update { copy(isPlaying = false) }
            if (cursor >= mutableState.value.items.size) {
                // Notify the reader so it can turn/scroll the page and keep reading (跟读翻页).
                // The reader stops the session itself at chapter end / on dispose.
                mutableQueueExhausted.tryEmit(Unit)
            }
        }
    }

    private inline fun update(transform: ReadAloudState.() -> ReadAloudState) {
        mutableState.value = mutableState.value.transform()
    }
}
