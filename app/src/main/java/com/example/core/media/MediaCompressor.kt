package com.example.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

data class PreparedMediaFile(
    val file: File,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long
)

object MediaCompressor {

    private const val MAX_IMAGE_DIMENSION = 1600
    private const val JPEG_QUALITY = 82

    suspend fun compressImageUri(context: Context, uri: Uri): Result<PreparedMediaFile> =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, boundsOpts)
                }

                val maxDim = max(boundsOpts.outWidth, boundsOpts.outHeight)
                var sampleSize = 1
                while (maxDim / sampleSize > MAX_IMAGE_DIMENSION * 2) {
                    sampleSize *= 2
                }

                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val decoded = resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOpts)
                } ?: return@withContext Result.failure(IllegalStateException("Unable to decode image"))

                val rotation = readExifRotation(context, uri)
                val oriented = rotateIfNeeded(decoded, rotation)
                val scaled = scaleDownIfNeeded(oriented, MAX_IMAGE_DIMENSION)

                val tempDir = File(context.cacheDir, "media_temp").apply { mkdirs() }
                val outFile = File(tempDir, "img_${UUID.randomUUID()}.jpg")
                FileOutputStream(outFile).use { fos ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos)
                }
                if (scaled != oriented) scaled.recycle()
                if (oriented != decoded) oriented.recycle()
                decoded.recycle()

                val originalName = queryDisplayName(context, uri)
                    .substringBeforeLast('.')
                    .ifBlank { "photo" } + ".jpg"

                Result.success(
                    PreparedMediaFile(
                        file = outFile,
                        fileName = originalName,
                        mimeType = "image/jpeg",
                        sizeBytes = outFile.length()
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun copyUriToTempFile(context: Context, uri: Uri): Result<PreparedMediaFile> =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val rawName = queryDisplayName(context, uri)
                val mimeType = resolver.getType(uri)
                    ?: guessMimeFromName(rawName)
                    ?: "application/octet-stream"

                val safeName = rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val tempDir = File(context.cacheDir, "media_temp").apply { mkdirs() }
                val outFile = File(tempDir, "${UUID.randomUUID()}_$safeName")

                resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                        }
                    }
                } ?: return@withContext Result.failure(IllegalStateException("Unable to read selected file"))

                Result.success(
                    PreparedMediaFile(
                        file = outFile,
                        fileName = rawName,
                        mimeType = mimeType,
                        sizeBytes = outFile.length()
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Safely clears ONLY temporary thumbnails and temporary media cache directories.
     * NEVER deletes Room message database, user downloads, account data, or chat history.
     */
    suspend fun clearTemporaryCacheOnly(context: Context) = withContext(Dispatchers.IO) {
        val tempMediaDir = File(context.cacheDir, "media_temp")
        if (tempMediaDir.exists()) {
            tempMediaDir.deleteRecursively()
        }
        val coilCacheDir = File(context.cacheDir, "image_cache")
        if (coilCacheDir.exists()) {
            coilCacheDir.deleteRecursively()
        }
    }

    fun isDangerousFileType(fileName: String?, mimeType: String?): Boolean {
        val lower = fileName.orEmpty().lowercase()
        val lowerMime = mimeType.orEmpty().lowercase()
        return lower.endsWith(".apk") ||
            lower.endsWith(".zip") ||
            lower.endsWith(".exe") ||
            lower.endsWith(".bat") ||
            lower.endsWith(".sh") ||
            lowerMime.contains("android.package-archive") ||
            lowerMime.contains("zip")
    }

    fun formatFileSize(bytes: Long?): String {
        val b = bytes ?: 0L
        return when {
            b < 1024L -> "$b B"
            b < 1024L * 1024L -> "${b / 1024L} KB"
            else -> String.format("%.1f MB", b / (1024f * 1024f))
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        var name: String? = null
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = cursor.getString(idx)
                }
            }
        return name?.takeIf { it.isNotBlank() } ?: "attachment_${System.currentTimeMillis()}"
    }

    private fun guessMimeFromName(fileName: String): String? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isBlank()) return null
        return if (ext == "apk") {
            "application/vnd.android.package-archive"
        } else {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        }
    }

    private fun readExifRotation(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    private fun rotateIfNeeded(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / max(w, h).toFloat()
        return Bitmap.createScaledBitmap(bitmap, (w * ratio).toInt(), (h * ratio).toInt(), true)
    }
}
