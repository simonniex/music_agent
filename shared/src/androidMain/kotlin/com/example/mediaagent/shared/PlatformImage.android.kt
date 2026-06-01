package com.example.mediaagent.shared

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlin.math.max

actual object PlatformImage {
    actual fun downscaleToJpeg(bytes: ByteArray, maxDimension: Int, quality: Int): ByteArray? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val longest = max(bounds.outWidth, bounds.outHeight)
            if (longest <= 0) return null

            val sampleOptions = BitmapFactory.Options().apply {
                inSampleSize = computeSampleSize(longest, maxDimension)
            }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, sampleOptions)
                ?: return null

            val scaled = scaleToMaxDimension(decoded, maxDimension)
            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), output)
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
            output.toByteArray()
        }.getOrNull()
    }

    private fun computeSampleSize(longest: Int, maxDimension: Int): Int {
        var sample = 1
        var current = longest
        while (current / 2 >= maxDimension) {
            current /= 2
            sample *= 2
        }
        return sample
    }

    private fun scaleToMaxDimension(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / longest.toFloat()
        val targetWidth = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }
}
