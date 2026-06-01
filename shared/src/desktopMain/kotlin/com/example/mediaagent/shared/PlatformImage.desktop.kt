package com.example.mediaagent.shared

import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.max

actual object PlatformImage {
    actual fun downscaleToJpeg(bytes: ByteArray, maxDimension: Int, quality: Int): ByteArray? {
        return runCatching {
            val source = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
            val longest = max(source.width, source.height)
            if (longest <= 0) return null

            val target = if (longest <= maxDimension) {
                source
            } else {
                val ratio = maxDimension.toDouble() / longest.toDouble()
                val targetWidth = (source.width * ratio).toInt().coerceAtLeast(1)
                val targetHeight = (source.height * ratio).toInt().coerceAtLeast(1)
                val scaled = source.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH)
                val buffered = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
                val graphics = buffered.createGraphics()
                graphics.drawImage(scaled, 0, 0, null)
                graphics.dispose()
                buffered
            }

            val rgb = if (target.type == BufferedImage.TYPE_INT_RGB) {
                target
            } else {
                val converted = BufferedImage(target.width, target.height, BufferedImage.TYPE_INT_RGB)
                val graphics = converted.createGraphics()
                graphics.drawImage(target, 0, 0, null)
                graphics.dispose()
                converted
            }

            val output = ByteArrayOutputStream()
            ImageIO.write(rgb, "jpg", output)
            output.toByteArray()
        }.getOrNull()
    }
}
