package com.example.mediaagent.shared

object LightweightQualityAnalyzer {
    fun attachSignals(metadata: MediaMetadata): MediaMetadata {
        return metadata.copy(qualitySignals = analyze(metadata))
    }

    fun analyze(metadata: MediaMetadata): List<QualitySignal> {
        return buildList {
            add(
                QualitySignal(
                    name = "media_type",
                    status = if (metadata.mediaType == MediaType.Unknown) SignalStatus.Warn else SignalStatus.Pass,
                    value = metadata.mediaType.name,
                    message = if (metadata.mediaType == MediaType.Unknown) {
                        "未能从 MIME 或扩展名识别媒体类型，建议确认文件格式。"
                    } else {
                        "已从真实文件信息识别媒体类型。"
                    },
                ),
            )

            add(
                QualitySignal(
                    name = "file_size",
                    status = when {
                        metadata.fileSizeBytes <= 0 -> SignalStatus.Fail
                        metadata.fileSizeBytes < 64 * 1024 -> SignalStatus.Warn
                        metadata.fileSizeBytes > 150 * 1024 * 1024 -> SignalStatus.Warn
                        else -> SignalStatus.Pass
                    },
                    value = "${metadata.fileSizeBytes} bytes",
                    message = when {
                        metadata.fileSizeBytes <= 0 -> "文件大小为 0，无法用于真实媒体分析。"
                        metadata.fileSizeBytes < 64 * 1024 -> "文件过小，可能不是有效音视频样本。"
                        metadata.fileSizeBytes > 150 * 1024 * 1024 -> "文件偏大，移动端上传和模型处理可能较慢。"
                        else -> "文件大小适合 Demo 处理。"
                    },
                ),
            )

            add(
                QualitySignal(
                    name = "duration",
                    status = when {
                        metadata.durationMs == null -> SignalStatus.Warn
                        metadata.durationMs < 3_000 -> SignalStatus.Warn
                        metadata.durationMs > 60_000 -> SignalStatus.Warn
                        else -> SignalStatus.Pass
                    },
                    value = metadata.displayDuration,
                    message = when {
                        metadata.durationMs == null -> "当前仅做真实 Metadata 读取，时长未可靠解析。"
                        metadata.durationMs < 3_000 -> "片段较短，内容摘要可能不稳定。"
                        metadata.durationMs > 60_000 -> "片段较长，建议截取 5-15 秒用于演示。"
                        else -> "时长适合快速演示。"
                    },
                ),
            )

            add(
                QualitySignal(
                    name = "sample_rate",
                    status = if (metadata.sampleRate == 44_100 || metadata.sampleRate == 48_000) {
                        SignalStatus.Pass
                    } else {
                        SignalStatus.Warn
                    },
                    value = metadata.sampleRate?.let { "${it}Hz" } ?: "unknown",
                    message = if (metadata.sampleRate == null) {
                        "采样率未从文件中解析，当前使用规则检测占位。"
                    } else {
                        "采样率处于常见发布范围。"
                    },
                ),
            )

            add(
                QualitySignal(
                    name = "clipping_probe",
                    status = SignalStatus.Warn,
                    value = "rule-based",
                    message = "当前未读取真实波形，爆音风险由规则检测提示；后续可接入 FFmpeg 或 MediaExtractor 精确分析。",
                ),
            )
        }
    }
}
