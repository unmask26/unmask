package com.example.unmask.data

import android.content.Context
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest

object VideoCacheManager {
    private const val CACHE_DIR_NAME = "video_cache"
    private val client = OkHttpClient()

    private fun getCacheFolder(context: Context): File {
        val folder = File(context.cacheDir, CACHE_DIR_NAME)
        if (!folder.exists()) {
            folder.mkdirs()
        }
        return folder
    }

    private fun getCacheFile(context: Context, videoUrl: String): File {
        val digest = MessageDigest.getInstance("MD5")
        val hash = digest.digest(videoUrl.toByteArray())
        val hexString = hash.joinToString("") { String.format("%02x", it) }
        return File(getCacheFolder(context), "$hexString.mp4")
    }

    fun getCachedVideoFile(context: Context, videoUrl: String): File? {
        val file = getCacheFile(context, videoUrl)
        return if (file.exists() && file.length() > 0) file else null
    }

    suspend fun prefetchVideo(context: Context, videoUrl: String): File = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val file = getCacheFile(context, videoUrl)
        if (file.exists() && file.length() > 0) {
            return@withContext file
        }

        val tempFile = File(getCacheFolder(context), "${file.name}.tmp")
        try {
            val request = Request.Builder().url(videoUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw java.io.IOException("Failed to download video: $response")
                }
                val body = response.body ?: throw java.io.IOException("Response body is null")
                body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            if (tempFile.exists() && tempFile.length() > 0) {
                tempFile.renameTo(file)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
        return@withContext file
    }
}
