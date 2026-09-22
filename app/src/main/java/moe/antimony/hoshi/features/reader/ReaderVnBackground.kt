package moe.antimony.hoshi.features.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.File

/**
 * Persists the optional Visual Novel background image in app-internal storage and
 * exposes it as a CSS data URI. The image is downscaled on save so the injected
 * base64 stays small enough to embed in the reader appearance script.
 */
internal object ReaderVnBackgroundStore {
    private const val DIR_NAME = "vnBackground"
    private const val FILE_NAME = "background.jpg"
    private const val MAX_DIMENSION = 1600

    private fun file(context: Context): File = File(context.filesDir, "$DIR_NAME/$FILE_NAME")

    /** Relative path under [Context.getFilesDir] used to record an active image in settings. */
    fun storedRelPath(): String = "$DIR_NAME/$FILE_NAME"

    fun isSet(context: Context): Boolean = file(context).exists()

    fun save(context: Context, uri: Uri): Boolean = runCatching {
        val resolver = context.contentResolver
        val bounds = resolver.openInputStream(uri)?.use { input ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, options)
            options.outWidth to options.outHeight
        } ?: return@runCatching false
        val (width, height) = bounds
        if (width <= 0 || height <= 0) return@runCatching false
        val sampleSize = maxOf(1, (maxOf(width, height).toFloat() / MAX_DIMENSION).toInt())
        val bitmap = resolver.openInputStream(uri)?.use { input ->
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeStream(input, null, options)
        } ?: return@runCatching false
        val scaled = if (maxOf(bitmap.width, bitmap.height) > MAX_DIMENSION) {
            val ratio = MAX_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt(),
                (bitmap.height * ratio).toInt(),
                true,
            )
        } else {
            bitmap
        }
        val out = file(context)
        out.parentFile?.mkdirs()
        out.outputStream().use { stream ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        }
        if (scaled != bitmap) scaled.recycle()
        bitmap.recycle()
        true
    }.getOrDefault(false)

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    /** Returns a `url("data:image/jpeg;base64,...")` string, or null when no image is stored. */
    fun cssUrl(context: Context): String? {
        val file = file(context)
        if (!file.exists()) return null
        return runCatching {
            val base64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
            "url(\"data:image/jpeg;base64,$base64\")"
        }.getOrNull()
    }
}
