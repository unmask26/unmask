package com.example.unmask.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class NSFWResult(
    val isNSFW: Boolean,
    val score: Float, // 0.0f - 1.0f
    val reason: String = ""
)

object NSFWDetector {

    /**
     * Video dosyasını inceleyerek kilit karelerden (keyframes) müstehcenlik/NSFW skorunu hesaplar.
     * Skor >= threshold (örneğin 0.70f) ise isNSFW = true döner.
     */
    suspend fun analyzeVideo(
        context: Context,
        videoUri: Uri,
        frameCount: Int = 6,
        threshold: Float = 0.65f
    ): NSFWResult = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 3000L

            var maxNsfwScore = 0.0f
            var flaggedFrameIndex = -1

            val stepMs = durationMs / (frameCount + 1)
            for (i in 1..frameCount) {
                val timeUs = (i * stepMs) * 1000L
                val frameBitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST) 
                    ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

                if (frameBitmap != null) {
                    val frameScore = evaluateFrameNsfwScore(frameBitmap)
                    if (frameScore > maxNsfwScore) {
                        maxNsfwScore = frameScore
                        flaggedFrameIndex = i
                    }
                    frameBitmap.recycle()
                }
            }

            val isNSFW = maxNsfwScore >= threshold
            val reason = if (isNSFW) {
                "Müstehcenlik / açık içerik saptandı (Kare #$flaggedFrameIndex, Skor: ${(maxNsfwScore * 100).toInt()}%)"
            } else ""

            NSFWResult(
                isNSFW = isNSFW,
                score = maxNsfwScore,
                reason = reason
            )
        } catch (e: Exception) {
            e.printStackTrace()
            // Hata durumunda varsayılan güvenli geçiş
            NSFWResult(isNSFW = false, score = 0.0f, reason = "Analiz hatası: ${e.localizedMessage}")
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * YCbCr ve RGB hibrit ten rengi piksel oranını ve müstehcenlik skorunu analiz eder.
     */
    private fun evaluateFrameNsfwScore(bitmap: Bitmap): Float {
        val scaled = Bitmap.createScaledBitmap(bitmap, 120, 120, false)
        val width = scaled.width
        val height = scaled.height
        val totalPixels = width * height

        var skinPixelCount = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = scaled.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                if (isSkinPixel(r, g, b)) {
                    skinPixelCount++
                }
            }
        }

        if (scaled != bitmap) {
            scaled.recycle()
        }

        val skinRatio = skinPixelCount.toFloat() / totalPixels.toFloat()

        return when {
            skinRatio >= 0.50f -> 0.95f
            skinRatio >= 0.40f -> 0.85f
            skinRatio >= 0.32f -> 0.70f
            skinRatio >= 0.25f -> 0.45f
            skinRatio >= 0.15f -> 0.20f
            else -> 0.05f
        }
    }

    private fun isSkinPixel(r: Int, g: Int, b: Int): Boolean {
        // YCbCr Renk Uzayı Dönüşümü
        val cb = 128 - 0.168736f * r - 0.331264f * g + 0.5f * b
        val cr = 128 + 0.5f * r - 0.418688f * g - 0.081312f * b

        val isYCbCrSkin = cb in 77.0f..127.0f && cr in 133.0f..173.0f
        val isRgbSkin = r > 95 && g > 40 && b > 20 && (maxOf(r, maxOf(g, b)) - minOf(r, minOf(g, b))) > 15 && Math.abs(r - g) > 15 && r > g && r > b

        return isYCbCrSkin && isRgbSkin
    }
}
