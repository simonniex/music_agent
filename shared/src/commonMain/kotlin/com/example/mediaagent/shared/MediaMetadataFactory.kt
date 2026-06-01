package com.example.mediaagent.shared

object MediaMetadataFactory {
    fun fromFileInput(
        fileName: String,
        fileSizeBytes: Long,
        mimeType: String? = null,
        inputPathOrUri: String? = null,
        durationMs: Long? = null,
        source: MediaSource = MediaSource.LocalFile,
        userGoal: String = "真实媒体文件输入，用于多 Agent 网关分析",
    ): MediaMetadata {
        val mediaType = detectMediaType(fileName, mimeType)
        val base = MediaMetadata(
            fileName = fileName.ifBlank { "unknown_media_file" },
            mediaType = mediaType,
            durationMs = durationMs,
            fileSizeBytes = fileSizeBytes.coerceAtLeast(0),
            mimeType = mimeType,
            inputPathOrUri = inputPathOrUri,
            sampleRate = if (mediaType == MediaType.Audio) 44_100 else null,
            bitRate = if (mediaType == MediaType.Audio) 192_000 else null,
            channels = if (mediaType == MediaType.Audio) 2 else null,
            source = source,
            analysisMode = AnalysisMode.RuleBasedQuality,
            userGoal = userGoal,
        )
        return LightweightQualityAnalyzer.attachSignals(base)
    }

    private fun detectMediaType(fileName: String, mimeType: String?): MediaType {
        val lowerMime = mimeType.orEmpty().lowercase()
        val lowerName = fileName.lowercase()

        return when {
            lowerMime.startsWith("audio/") -> MediaType.Audio
            lowerMime.startsWith("video/") -> MediaType.Video
            lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") ||
                lowerName.endsWith(".m4a") || lowerName.endsWith(".aac") ||
                lowerName.endsWith(".flac") -> MediaType.Audio
            lowerName.endsWith(".mp4") || lowerName.endsWith(".mov") ||
                lowerName.endsWith(".mkv") || lowerName.endsWith(".webm") -> MediaType.Video
            lowerName.startsWith("http://") || lowerName.startsWith("https://") -> MediaType.RemoteUrl
            else -> MediaType.Unknown
        }
    }
}
