package com.example.mediaagent.shared

/**
 * 跨平台图片压缩。
 *
 * 视觉模型（YT-VITA）直接处理高分辨率原图会很慢，常常导致请求超时。
 * 发送前先把图片降采样并重压成 JPEG，可以显著降低请求体大小和模型处理时间。
 */
expect object PlatformImage {
    /**
     * 把图片降采样到 [maxDimension] 以内，并按 [quality] 重压成 JPEG。
     * 失败时返回 null，调用方应回退到原始 bytes。
     */
    fun downscaleToJpeg(bytes: ByteArray, maxDimension: Int, quality: Int): ByteArray?
}
