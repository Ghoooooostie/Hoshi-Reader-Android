package moe.antimony.hoshi.features.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WordAudioPlayerSourceTest {
    @Test
    fun wordAudioPlayerUsesMedia3ExoPlayerForRemoteAndLocalAudio() {
        val source = File("src/main/java/moe/antimony/hoshi/features/audio/WordAudioPlayer.kt").readText()
        val play = source.substringAfter("fun play(url: String, mode: AudioPlaybackMode)")
            .substringBefore("fun stop()")

        assertFalse(source.contains("import android.media.MediaDataSource"))
        assertFalse(source.contains("import android.media.MediaPlayer"))
        assertTrue(source.contains("import androidx.media3.common.AudioAttributes"))
        assertTrue(source.contains("import androidx.media3.common.C"))
        assertTrue(source.contains("import androidx.media3.common.MediaItem"))
        assertTrue(source.contains("import androidx.media3.common.Player"))
        assertTrue(source.contains("import androidx.media3.datasource.ByteArrayDataSource"))
        assertTrue(source.contains("import androidx.media3.exoplayer.ExoPlayer"))
        assertTrue(source.contains("import androidx.media3.exoplayer.source.ProgressiveMediaSource"))
        assertTrue(play.contains("ExoPlayer.Builder(appContext).build()"))
        assertFalse(play.contains("setAudioAttributes(audioAttributes(), true)"))
        assertTrue(play.contains("setAudioAttributes(audioAttributes(), false)"))
        assertTrue(play.contains("ProgressiveMediaSource.Factory"))
        assertTrue(play.contains("ByteArrayDataSource(data)"))
        assertTrue(play.contains("setMediaSource("))
        assertTrue(play.contains("setMediaItem(MediaItem.fromUri(url))"))
        assertTrue(play.contains("Player.STATE_ENDED"))
        assertTrue(play.contains("override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int)"))
        assertTrue(play.contains("Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM"))
        assertTrue(play.contains("override fun onPlayerError(error: PlaybackException)"))
        assertTrue(play.contains("this@WordAudioPlayer.stop()"))
        assertFalse(play.contains("                            stop()\n"))
        assertTrue(play.contains("prepare()"))
        assertTrue(play.contains("playWhenReady = true"))
        assertTrue(source.contains("C.AUDIO_CONTENT_TYPE_SPEECH"))
        assertTrue(source.contains("C.USAGE_MEDIA"))
    }

    @Test
    fun wordAudioPlayerPreservesShortAudioFocusModes() {
        val source = File("src/main/java/moe/antimony/hoshi/features/audio/WordAudioPlayer.kt").readText()
        val requestFocus = source.substringAfter("private fun requestFocus(mode: AudioPlaybackMode)")
            .substringBefore("private fun abandonFocus()")
        val abandonFocus = source.substringAfter("private fun abandonFocus()")
            .substringBefore("private fun audioAttributes()")

        assertTrue(requestFocus.contains("if (mode == AudioPlaybackMode.Mix) return true"))
        assertTrue(requestFocus.contains("AudioPlaybackMode.Interrupt -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT"))
        assertTrue(requestFocus.contains("AudioPlaybackMode.Duck -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK"))
        assertTrue(requestFocus.contains("AudioPlaybackMode.Mix -> AudioManager.AUDIOFOCUS_NONE"))
        assertTrue(requestFocus.contains(".setOnAudioFocusChangeListener(focusChangeListener)"))
        assertTrue(requestFocus.contains("val granted = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED"))
        assertTrue(requestFocus.contains("if (granted) focusRequest = request"))
        assertTrue(requestFocus.contains("audioManager.requestAudioFocus(focusChangeListener, AudioManager.STREAM_MUSIC, gain)"))
        assertTrue(abandonFocus.contains("val request = focusRequest ?: return"))
        assertTrue(source.contains("audioManager.abandonAudioFocusRequest(request)"))
        assertTrue(source.contains("audioManager.abandonAudioFocus(focusChangeListener)"))
    }
}
