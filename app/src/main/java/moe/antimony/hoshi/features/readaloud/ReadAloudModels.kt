package moe.antimony.hoshi.features.readaloud

import java.io.File

/**
 * Configuration for a local TTS model, mirroring sherpa-onnx's [getOfflineTtsConfig] fields.
 * File names are relative to the model directory.
 */
data class TtsModelConfig(
    val isSupertonic: Boolean = false,
    val modelName: String = "",
    val acousticModelName: String = "",
    val vocoder: String = "",
    val voices: String = "",
    val lexicon: String = "",
    val dataDir: String = "",
    val ruleFsts: String = "",
    val ruleFars: String = "",
    val isKitten: Boolean = false,
    val durationPredictor: String = "",
    val textEncoder: String = "",
    val vectorEstimator: String = "",
    val supertonicVocoder: String = "",
    val ttsJson: String = "",
    val unicodeIndexer: String = "",
    val voiceStyle: String = "",
    val languageTag: String = "ja",
)

data class RecommendedTtsModel(
    val id: String,
    val name: String,
    val description: String,
    val downloadUrl: String,
    /** Directory name inside the archive that contains the model files. */
    val modelSubDir: String,
    val config: TtsModelConfig,
)

/** Local, offline TTS models the user can download, mirroring dictionary recommendations. */
val RecommendedTtsModels: List<RecommendedTtsModel> = listOf(
    RecommendedTtsModel(
        id = "supertonic-ja",
        name = "Supertonic 3 (日语)",
        description = "离线日语神经 TTS，首次需下载约 120MB",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/" +
            "sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2",
        modelSubDir = "sherpa-onnx-supertonic-3-tts-int8-2026-05-11",
        config = TtsModelConfig(
            isSupertonic = true,
            modelName = "text_encoder.int8.onnx",
            durationPredictor = "duration_predictor.int8.onnx",
            textEncoder = "text_encoder.int8.onnx",
            vectorEstimator = "vector_estimator.int8.onnx",
            supertonicVocoder = "vocoder.int8.onnx",
            ttsJson = "tts.json",
            unicodeIndexer = "unicode_indexer.bin",
            voiceStyle = "voice.bin",
            languageTag = "ja",
        ),
    ),
)

fun readAloudModelsRoot(filesDir: File): File = File(filesDir, "ReadAloudModels")

fun modelDirectory(filesDir: File, modelId: String): File =
    File(readAloudModelsRoot(filesDir), modelId)

/**
 * Relative file names (within [RecommendedTtsModel.modelSubDir]) that the engine needs to
 * load the model. Used both for the "installed" check and as a guard before constructing the
 * native [com.k2fsa.sherpa.onnx.OfflineTts], which aborts the whole process on a missing file.
 * File names MUST match the actual files shipped in the model archive (verified on device).
 */
fun requiredModelFiles(model: RecommendedTtsModel): List<String> = listOf(
    model.config.modelName,
    model.config.acousticModelName,
    model.config.vocoder,
    model.config.voices,
    model.config.lexicon,
    model.config.durationPredictor,
    model.config.textEncoder,
    model.config.vectorEstimator,
    model.config.supertonicVocoder,
    model.config.ttsJson,
    model.config.unicodeIndexer,
    model.config.voiceStyle,
).map { it.trim() }.filter { it.isNotBlank() }

fun isModelInstalled(filesDir: File, model: RecommendedTtsModel): Boolean {
    val dir = File(modelDirectory(filesDir, model.id), model.modelSubDir)
    if (!dir.isDirectory) return false
    return requiredModelFiles(model).all { File(dir, it).isFile }
}

fun installedTtsModels(filesDir: File): List<RecommendedTtsModel> =
    RecommendedTtsModels.filter { isModelInstalled(filesDir, it) }
