package moe.antimony.hoshi.features.readaloud

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.antimony.hoshi.di.ApplicationScope
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

enum class ReadAloudModelDownloadStage { Downloading, Extracting, Done, Failed }

data class ReadAloudModelDownloadProgress(
    val stage: ReadAloudModelDownloadStage,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
)

/**
 * Downloads offline TTS model archives and extracts them under
 * [readAloudModelsRoot], reusing the same streaming-HTTP pattern as the
 * dictionary downloader.
 */
@Singleton
class ReadAloudModelRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val mutableDownloadProgress = MutableStateFlow<Map<String, ReadAloudModelDownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, ReadAloudModelDownloadProgress>> = mutableDownloadProgress.asStateFlow()

    private var downloadJob: Job? = null

    /**
     * Starts a download that keeps running while the app process lives, even when the reader and
     * its settings sheet leave composition. A cancelled extraction leaves a half-written model
     * behind, which would make the local engine permanently unavailable.
     */
    fun startDownload(model: RecommendedTtsModel) {
        if (downloadJob?.isActive == true) return
        downloadJob = applicationScope.launch { download(model) }
    }

    suspend fun download(
        model: RecommendedTtsModel,
        onProgress: (ReadAloudModelDownloadProgress) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        val targetDir = modelDirectory(context.filesDir, model.id)
        targetDir.mkdirs()
        val tempArchive = File(targetDir, ".download-${model.id}.tar.bz2")
        val extractedDir = File(targetDir, model.modelSubDir)
        try {
            mutableDownloadProgress.value = mutableDownloadProgress.value + (
                model.id to ReadAloudModelDownloadProgress(ReadAloudModelDownloadStage.Downloading, 0, -1)
            )
            var total = -1L
            downloadToFile(model.downloadUrl, tempArchive) { bytes, t ->
                total = t
                val progress = ReadAloudModelDownloadProgress(
                    ReadAloudModelDownloadStage.Downloading, bytes, t,
                )
                onProgress(progress)
                mutableDownloadProgress.value = mutableDownloadProgress.value + (model.id to progress)
            }
            val extracting = ReadAloudModelDownloadProgress(
                ReadAloudModelDownloadStage.Extracting, total, total,
            )
            onProgress(extracting)
            mutableDownloadProgress.value = mutableDownloadProgress.value + (model.id to extracting)
            // A leftover directory from an interrupted run would otherwise keep partially
            // written files that later look like a complete model.
            extractedDir.deleteRecursively()
            extractTarBz2(tempArchive, targetDir)
            val installed = isModelInstalled(context.filesDir, model)
            if (!installed) {
                // Extraction stopped early (interrupted or truncated archive). Never report a
                // half-extracted model as installed: the local engine would then fail on every
                // sentence without an obvious cause.
                extractedDir.deleteRecursively()
                throw IOException("Extracted model is incomplete: ${model.modelSubDir}")
            }
            tempArchive.delete()
            val done = ReadAloudModelDownloadProgress(ReadAloudModelDownloadStage.Done, total, total)
            onProgress(done)
            mutableDownloadProgress.value = mutableDownloadProgress.value + (model.id to done)
            true
        } catch (error: Throwable) {
            tempArchive.delete()
            val failed = ReadAloudModelDownloadProgress(ReadAloudModelDownloadStage.Failed)
            onProgress(failed)
            mutableDownloadProgress.value = mutableDownloadProgress.value + (model.id to failed)
            false
        }
    }

    fun isInstalled(model: RecommendedTtsModel): Boolean =
        isModelInstalled(context.filesDir, model)

    fun delete(model: RecommendedTtsModel) {
        modelDirectory(context.filesDir, model.id).deleteRecursively()
        mutableDownloadProgress.value = mutableDownloadProgress.value - model.id
    }

    private fun downloadToFile(url: String, out: File, onBytes: (Long, Long) -> Unit): Long {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 0
            instanceFollowRedirects = true
        }
        val total = connection.contentLengthLong.takeIf { it > 0 } ?: -1L
        connection.inputStream.use { input ->
            FileOutputStream(out).use { output ->
                val buffer = ByteArray(64 * 1024)
                var read: Int
                var downloaded = 0L
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloaded += read
                    onBytes(downloaded, total)
                }
            }
        }
        return total
    }

    private fun extractTarBz2(archive: File, dest: File) {
        val destCanonical = dest.canonicalPath
        archive.inputStream().use { raw ->
            BZip2CompressorInputStream(raw).use { bz ->
                TarArchiveInputStream(bz).use { tar ->
                    var entry = tar.nextEntry
                    while (entry != null) {
                        val file = File(dest, entry.name)
                        // Guard against path traversal in the archive.
                        if (!file.canonicalPath.startsWith(destCanonical)) {
                            entry = tar.nextEntry
                            continue
                        }
                        if (entry.isDirectory) {
                            file.mkdirs()
                        } else {
                            file.parentFile?.mkdirs()
                            file.outputStream().use { out -> tar.copyTo(out) }
                        }
                        entry = tar.nextEntry
                    }
                }
            }
        }
    }
}
