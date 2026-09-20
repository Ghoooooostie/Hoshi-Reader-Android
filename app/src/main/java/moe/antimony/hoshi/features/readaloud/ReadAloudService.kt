package moe.antimony.hoshi.features.readaloud

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.PowerManager
import android.media.session.MediaSession
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import moe.antimony.hoshi.MainActivity

/**
 * Foreground service that mirrors [ReadAloudController] state into a MediaSession and a
 * transport notification (lock-screen / shade controls). It owns no playback logic itself;
 * all commands are forwarded to the shared [ReadAloudController] singleton.
 */
@AndroidEntryPoint
class ReadAloudService : android.app.Service() {

    @Inject lateinit var controller: ReadAloudController

    @Inject lateinit var settingsRepository: ReadAloudSettingsRepository

    private lateinit var mediaSession: MediaSession
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var foregroundStarted = false
    private var wakeLockEnabled = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSession(this, "hoshi-read-aloud").apply {
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(mediaSessionCallback)
            isActive = true
        }
        // The service is always started with startForegroundService(), so it must call
        // startForeground() right away. The system crashes the app with
        // ForegroundServiceDidNotStartInTimeException when that call does not happen in time,
        // even if playback stopped between the start call and this callback, so the promotion
        // must not depend on the current state.
        startInForeground()
        controller.state
            .onEach { state ->
                if (!foregroundStarted && state.isActive) {
                    startInForeground()
                }
                if (foregroundStarted) {
                    updateNotification(state)
                }
                updateWakeLock(state.isActive && state.isPlaying)
                if (foregroundStarted && !state.isActive) {
                    stopSelf()
                }
            }
            .launchIn(scope)
        // 朗读服务唤醒锁：防止朗读期间设备休眠导致 TTS 被中断（对应 legadoT 的 readAloudWakeLock）。
        settingsRepository.settings
            .onEach { settings ->
                wakeLockEnabled = settings.wakeLock
                updateWakeLock(controller.state.value.isActive && controller.state.value.isPlaying)
            }
            .launchIn(scope)
    }

    private fun updateWakeLock(needed: Boolean) {
        if (needed && wakeLockEnabled) {
            if (wakeLock?.isHeld != true) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "hoshi:ReadAloudService",
                ).apply {
                    setReferenceCounted(false)
                    acquire(6 * 60 * 60 * 1000L)
                }
            }
        } else {
            wakeLock?.takeIf { it.isHeld }?.release()
            wakeLock = null
        }
    }

    private val mediaSessionCallback = object : MediaSession.Callback() {
        override fun onPlay() = controller.resume()

        override fun onPause() = controller.pause()

        override fun onStop() = controller.stop()

        override fun onSkipToNext() = controller.skipNext()

        override fun onSkipToPrevious() = controller.skipPrevious()

        override fun onCustomAction(action: String, extras: Bundle?) {
            if (action == ACTION_TIMER) controller.cycleSleepTimer()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Reused instances can receive a new startForegroundService() request, so keep the
        // foreground contract satisfied before handling any transport action.
        if (!foregroundStarted) {
            startInForeground()
        }
        when (intent?.action) {
            ACTION_PLAY, ACTION_RESUME -> controller.resume()
            ACTION_PAUSE -> controller.pause()
            ACTION_STOP -> controller.stop()
            ACTION_NEXT -> controller.skipNext()
            ACTION_PREV -> controller.skipPrevious()
            ACTION_TIMER -> controller.cycleSleepTimer()
        }
        return START_NOT_STICKY
    }

    private fun startInForeground() {
        foregroundStarted = true
        val state = controller.state.value
        ServiceCompat.startForeground(
            this,
            ReadAloudNotificationId,
            buildReadAloudNotification(this, state, contentIntent(), actionIntents()),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    private fun updateNotification(state: ReadAloudState) {
        val notification = buildReadAloudNotification(this, state, contentIntent(), actionIntents())
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(ReadAloudNotificationId, notification)
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionIntents() = ReadAloudActionIntents(
        prev = serviceIntent(ACTION_PREV, 1),
        play = serviceIntent(ACTION_PLAY, 2),
        pause = serviceIntent(ACTION_PAUSE, 3),
        stop = serviceIntent(ACTION_STOP, 4),
        next = serviceIntent(ACTION_NEXT, 5),
        timer = serviceIntent(ACTION_TIMER, 6),
        mediaSessionToken = mediaSession.sessionToken,
    )

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, ReadAloudService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun onDestroy() {
        scope.cancel()
        mediaSession.release()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        const val ACTION_START = "moe.antimony.hoshi.readaloud.START"
        const val ACTION_PLAY = "moe.antimony.hoshi.readaloud.PLAY"
        const val ACTION_PAUSE = "moe.antimony.hoshi.readaloud.PAUSE"
        const val ACTION_RESUME = "moe.antimony.hoshi.readaloud.RESUME"
        const val ACTION_STOP = "moe.antimony.hoshi.readaloud.STOP"
        const val ACTION_NEXT = "moe.antimony.hoshi.readaloud.NEXT"
        const val ACTION_PREV = "moe.antimony.hoshi.readaloud.PREV"
        const val ACTION_TIMER = "moe.antimony.hoshi.readaloud.TIMER"

        fun startIntent(context: Context): Intent =
            Intent(context, ReadAloudService::class.java).apply { action = ACTION_START }
    }
}
