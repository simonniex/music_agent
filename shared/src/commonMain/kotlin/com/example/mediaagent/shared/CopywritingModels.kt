package com.example.mediaagent.shared

data class TencentAsrConfig(
    val secretId: String,
    val secretKey: String,
    val region: String = "ap-guangzhou",
    val engineModelType: String = "16k_zh",
) {
    val effectiveSecretId: String
        get() = secretId.ifBlank { DEMO_SECRET_ID }

    val effectiveSecretKey: String
        get() = secretKey.ifBlank { DEMO_SECRET_KEY }

    val isConfigured: Boolean
        get() = effectiveSecretId.isNotBlank() && effectiveSecretKey.isNotBlank()

    companion object {
        private const val DEMO_SECRET_ID = ""
        private const val DEMO_SECRET_KEY = ""
    }
}

data class AudioInput(
    val metadata: MediaMetadata,
    val bytes: ByteArray? = null,
    val publicUrl: String? = null,
    val auxiliary: AuxiliaryContext = AuxiliaryContext(),
) {
    val canUseLocalAsr: Boolean
        get() = bytes != null && bytes.size in 1..TencentAsrClient.MAX_LOCAL_BYTES
}

data class ImageAttachment(
    val fileName: String = "",
    val bytes: ByteArray? = null,
    val mimeType: String? = null,
) {
    val hasContent: Boolean
        get() = bytes != null && bytes.isNotEmpty()

    val effectiveMimeType: String
        get() = mimeType?.takeIf { it.isNotBlank() }
            ?: guessMimeTypeFromName(fileName)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImageAttachment) return false
        return fileName == other.fileName &&
            mimeType == other.mimeType &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + (bytes?.contentHashCode() ?: 0)
        result = 31 * result + (mimeType?.hashCode() ?: 0)
        return result
    }

    companion object {
        fun guessMimeTypeFromName(fileName: String): String {
            return when (fileName.substringAfterLast('.', "").lowercase()) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                "heic", "heif" -> "image/heic"
                else -> "image/jpeg"
            }
        }
    }
}

data class AuxiliaryContext(
    val songName: String = "",
    val artistName: String = "",
    val favoriteLyrics: String = "",
    val lyricImages: List<ImageAttachment> = emptyList(),
    val screenshots: List<ImageAttachment> = emptyList(),
    val screenshotNote: String = "",
) {
    val allLyricImages: List<ImageAttachment>
        get() = lyricImages.filter { it.fileName.isNotBlank() || it.hasContent }

    val allScreenshots: List<ImageAttachment>
        get() = screenshots.filter { it.fileName.isNotBlank() || it.hasContent }

    val hasAny: Boolean
        get() = songName.isNotBlank() ||
            artistName.isNotBlank() ||
            favoriteLyrics.isNotBlank() ||
            allLyricImages.isNotEmpty() ||
            allScreenshots.isNotEmpty() ||
            screenshotNote.isNotBlank()
}

enum class VisionImageKind {
    LyricImage,
    Screenshot,
}

data class VisionRecognitionResult(
    val success: Boolean,
    val extractedText: String = "",
    val songName: String = "",
    val artistName: String = "",
    val lyrics: String = "",
    val mood: String = "",
    val summary: String = "",
    val message: String = "",
    val kind: VisionImageKind? = null,
)

data class VisionContextBundle(
    val lyricImages: List<VisionRecognitionResult> = emptyList(),
    val screenshots: List<VisionRecognitionResult> = emptyList(),
)

enum class StandardTextSource {
    UserInput,
    VisionLyricImage,
    VisionScreenshot,
    SongMetadata,
    Asr,
    Fallback,
}

data class StandardTextResult(
    val text: String,
    val source: StandardTextSource,
    val asrText: String = "",
    val visionLyrics: String = "",
    val songName: String = "",
    val artistName: String = "",
) {
    val sourceLabel: String
        get() = when (source) {
            StandardTextSource.UserInput -> "用户输入歌词"
            StandardTextSource.VisionLyricImage -> "歌词图片 YT-VITA 识别"
            StandardTextSource.VisionScreenshot -> "截图 YT-VITA 识别"
            StandardTextSource.SongMetadata -> "歌名/歌手线索"
            StandardTextSource.Asr -> "腾讯云 ASR 转写"
            StandardTextSource.Fallback -> "文件名/兜底线索"
        }
}

data class VisionConfig(
    val apiKey: String,
    val baseUrl: String = "",
    val model: String = "",
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && !apiKey.startsWith("replace", ignoreCase = true)
}

data class TranscriptResult(
    val text: String,
    val source: TranscriptSource,
    val success: Boolean,
    val message: String,
    val durationSeconds: Double? = null,
)

enum class TranscriptSource {
    TencentAsr,
    NeedsPublicUrl,
    Fallback,
}

data class AudioCopywritingResult(
    val metadata: MediaMetadata,
    val transcript: TranscriptResult,
    val visionContext: VisionContextBundle,
    val standardText: StandardTextResult,
    val generatedMarkdown: String,
    val fromFallback: Boolean,
    val generationMessage: String,
    val llmSuccess: Boolean,
)

data class AudioCopywritingConfig(
    val llm: LlmConfig,
    val asr: TencentAsrConfig,
    val vision: VisionConfig? = null,
) {
    fun effectiveVision(): VisionConfig {
        return vision ?: VisionConfig(
            apiKey = llm.apiKey,
            baseUrl = llm.baseUrl,
            model = DEFAULT_VITA_MODEL,
        )
    }

    companion object {
        const val DEFAULT_VITA_MODEL = ""
    }
}
