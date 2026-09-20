package moe.antimony.hoshi.features.readaloud

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.GeneratedAudio
import java.io.File
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline TTS backend powered by a downloaded sherpa-onnx model (e.g. Supertonic 3).
 * Loads the model from [android.content.Context.getFilesDir] and plays through [AudioTrack].
 */
@Singleton
class LocalReadAloudEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: ReadAloudSettingsRepository,
) : ReadAloudEngine {
    override val id: ReadAloudEngineId = ReadAloudEngineId.Local

    private var tts: OfflineTts? = null
    private var track: AudioTrack? = null
    private var speechRate = ReadAloudSettings.DefaultSpeechRate
    private var stopped = false
    private var sampleRate = 0

    override suspend fun prepare(): Boolean = withContext(Dispatchers.IO) {
        try {
            val settings = settingsRepository.settings.first()
            val modelId = settings.selectedModelId ?: return@withContext false
            val model = RecommendedTtsModels.firstOrNull { it.id == modelId } ?: return@withContext false
            if (!isModelInstalled(context.filesDir, model)) return@withContext false
            val modelDirFile = File(modelDirectory(context.filesDir, modelId), model.modelSubDir)
            // Guard against a partially-extracted / corrupt model: the native OfflineTts
            // constructor aborts the whole process (SIGABRT) on a missing file, which a
            // Kotlin try/catch cannot intercept. Verify every referenced file exists first.
            val missingFile = requiredModelFiles(model)
                .firstOrNull { !File(modelDirFile, it).isFile }
            if (missingFile != null) return@withContext false
            val modelDir = modelDirFile.absolutePath
            val config = getOfflineTtsConfig(
                modelDir = modelDir,
                modelName = model.config.modelName,
                acousticModelName = model.config.acousticModelName,
                vocoder = model.config.vocoder,
                voices = model.config.voices,
                lexicon = model.config.lexicon,
                dataDir = model.config.dataDir,
                dictDir = "",
                ruleFsts = model.config.ruleFsts,
                ruleFars = model.config.ruleFars,
                isKitten = model.config.isKitten,
                isSupertonic = model.config.isSupertonic,
                durationPredictor = model.config.durationPredictor,
                textEncoder = model.config.textEncoder,
                vectorEstimator = model.config.vectorEstimator,
                supertonicVocoder = model.config.supertonicVocoder,
                ttsJson = model.config.ttsJson,
                unicodeIndexer = model.config.unicodeIndexer,
                voiceStyle = model.config.voiceStyle,
            )
            // The model lives in app-specific storage (filesDir), so load it from the
            // filesystem via the file-based constructor rather than the asset-based one:
            // OfflineTts(assetManager = null, config) calls newFromFile() internally.
            tts = OfflineTts(config = config)
            sampleRate = tts!!.sampleRate()
            initAudioTrack()
            true
        } catch (e: Throwable) {
            // Native OfflineTts creation throws IllegalArgumentException when the model
            // cannot be loaded; never let it crash the playback coroutine.
            release()
            false
        }
    }

    private fun initAudioTrack() {
        val bufLength = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        val attr = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(sampleRate)
            .build()
        track = AudioTrack(
            attr, format, bufLength, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        track?.play()
    }

    override suspend fun speak(text: String): Boolean {
        if (text.isBlank()) return true
        val engine = tts ?: return false
        val player = track ?: return false
        return try {
            stopped = false
            player.play()
            val written = AtomicLong(0)
            val produced = withContext(Dispatchers.Default) {
                val genConfig = GenerationConfig(sid = 0, speed = speechRate).apply {
                    extra = mapOf("lang" to "ja")
                }
                val audio: GeneratedAudio = engine.generateWithConfigAndCallback(
                    text = text,
                    config = genConfig,
                ) { samples ->
                    if (!stopped) {
                        player.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                        written.addAndGet(samples.size.toLong())
                        1
                    } else {
                        0
                    }
                }
                audio.samples.isNotEmpty()
            }
            // Wait for the AudioTrack to finish playing the buffered samples.
            val millis = (written.get() / sampleRate.toDouble() * 1000.0).toLong().coerceAtLeast(0)
            var elapsed = 0L
            val step = 50L
            while (elapsed < millis && !stopped) {
                delay(step)
                elapsed += step
            }
            player.pause()
            player.flush()
            produced && !stopped
        } catch (e: Throwable) {
            false
        }
    }

    override fun stop() {
        stopped = true
        runCatching {
            track?.pause()
            track?.flush()
        }
    }

    override fun setSpeechRate(rate: Float) {
        speechRate = rate
    }

    override fun release() {
        stopped = true
        runCatching {
            track?.stop()
            track?.release()
        }
        track = null
        runCatching { tts?.release() }
        tts = null
    }
}
