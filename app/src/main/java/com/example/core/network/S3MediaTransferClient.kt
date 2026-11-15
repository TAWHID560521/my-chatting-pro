package com.example.core.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

class S3MediaTransferClient(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val activeCalls = ConcurrentHashMap<String, Call>()

    suspend fun uploadFileToPresignedUrl(
        transferId: String,
        uploadUrl: String,
        file: File,
        mimeType: String,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val totalBytes = file.length().coerceAtLeast(1L)
        val body = object : RequestBody() {
            override fun contentType() = mimeType.toMediaTypeOrNull()
            override fun contentLength() = file.length()
            override fun writeTo(sink: BufferedSink) {
                file.source().use { source ->
                    var uploaded = 0L
                    val buffer = okio.Buffer()
                    while (true) {
                        val read = source.read(buffer, 16 * 1024L)
                        if (read == -1L) break
                        sink.write(buffer, read)
                        uploaded += read
                        onProgress((uploaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                    }
                }
            }
        }

        val request = Request.Builder()
            .url(uploadUrl)
            .put(body)
            .header("Content-Type", mimeType)
            .build()

        val call = okHttpClient.newCall(request)
        activeCalls[transferId] = call
        try {
            call.execute().use { response ->
                if (response.isSuccessful) {
                    onProgress(1f)
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("Storage upload rejected (${response.code})"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            activeCalls.remove(transferId)
        }
    }

    suspend fun downloadFileFromPresignedUrl(
        transferId: String,
        downloadUrl: String,
        destinationFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        destinationFile.parentFile?.mkdirs()
        val request = Request.Builder().url(downloadUrl).get().build()
        val call = okHttpClient.newCall(request)
        activeCalls[transferId] = call
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Storage download failed (${response.code})")
                    )
                }
                val body = response.body ?: return@withContext Result.failure(
                    IllegalStateException("Empty download stream")
                )
                val contentLength = body.contentLength().coerceAtLeast(1L)
                body.byteStream().use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var totalRead = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            totalRead += read
                            if (body.contentLength() > 0) {
                                onProgress((totalRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                onProgress(1f)
                Result.success(destinationFile)
            }
        } catch (e: Exception) {
            if (destinationFile.exists()) destinationFile.delete()
            Result.failure(e)
        } finally {
            activeCalls.remove(transferId)
        }
    }

    fun cancelTransfer(transferId: String) {
        activeCalls.remove(transferId)?.cancel()
    }

    fun getSafeDownloadFile(mediaObjectId: String, fileName: String): File {
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val dir = File(context.filesDir, "downloads")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${mediaObjectId}_$safeName")
    }

    fun getTempMediaCacheFile(name: String): File {
        val dir = File(context.cacheDir, "media_temp")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, name)
    }
}
