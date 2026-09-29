package moe.antimony.hoshi.features.playback

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import moe.antimony.hoshi.features.readaloud.ReadAloudController
import moe.antimony.hoshi.features.sasayaki.SasayakiPlaybackServiceRuntime

/**
 * Pauses ReadAloud + Sasayaki playback when the app leaves the foreground (switched to another app)
 * or the user opens an in-app settings screen, and resumes it automatically on return.
 *
 * Mirrors the iOS reader's `willResignActiveNotification` / `didBecomeActiveNotification` behaviour:
 * playback is paused (not stopped) on backgrounding and resumed on foreground. Only playback that was
 * auto-paused here is resumed, so a manual pause/stop by the user is never overridden.
 */
@Singleton
class PlaybackLifecycleGate @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val readAloudController: ReadAloudController,
    private val sasayakiRuntime: SasayakiPlaybackServiceRuntime,
) : DefaultLifecycleObserver {

    private var backgrounded = false
    private var inSettings = false
    private var readAloudAutoPaused = false
    private var sasayakiAutoPaused = false

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        setBackgrounded(false)
    }

    override fun onStop(owner: LifecycleOwner) {
        setBackgrounded(true)
    }

    /** Called from the app shell whenever the user enters or leaves an in-app settings screen. */
    fun setInSettings(inSettings: Boolean) {
        this.inSettings = inSettings
        recompute()
    }

    private fun setBackgrounded(backgrounded: Boolean) {
        this.backgrounded = backgrounded
        recompute()
    }

    private fun recompute() {
        val shouldPause = backgrounded || inSettings
        if (shouldPause) {
            pausePlayback()
        } else {
            resumePlayback()
        }
    }

    private fun pausePlayback() {
        val readAloud = readAloudController.state.value
        if (readAloud.isActive && readAloud.isPlaying) {
            readAloudController.pause()
            readAloudAutoPaused = true
        } else {
            readAloudAutoPaused = false
        }
        if (sasayakiRuntime.isActivePlaybackPlaying) {
            sasayakiRuntime.pauseActivePlayback()
            sasayakiAutoPaused = true
        } else {
            sasayakiAutoPaused = false
        }
    }

    private fun resumePlayback() {
        if (readAloudAutoPaused) {
            readAloudController.resume()
            readAloudAutoPaused = false
        }
        if (sasayakiAutoPaused) {
            sasayakiRuntime.resumeActivePlayback()
            sasayakiAutoPaused = false
        }
    }
}
