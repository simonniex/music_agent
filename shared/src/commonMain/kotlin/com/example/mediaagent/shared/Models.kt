package com.example.mediaagent.shared

import kotlinx.serialization.Serializable

@Serializable
data class MediaMetadata(
    val fileName: String,
    val mediaType: MediaType,
    val durationMs: Long? = null,
    val fileSizeBytes: Long,
    val mimeType: String? = null,
    val inputPathOrUri: String? = null,
    val sampleRate: Int? = null,
    val bitRate: Int? = null,
    val channels: Int? = null,
    val source: MediaSource = MediaSource.DemoMock,
    val analysisMode: AnalysisMode = AnalysisMode.RealMetadata,
    val qualitySignals: List<QualitySignal> = emptyList(),
    val userGoal: String = "用于一分半钟 Demo 演示",
) {
    val displayDuration: String
        get() = durationMs?.let { "${it / 1000}s" } ?: "unknown"
}

@Serializable
enum class MediaType {
    Audio,
    Video,
    RemoteUrl,
    Unknown,
}

@Serializable
enum class MediaSource {
    LocalFile,
    RemoteUrl,
    DemoMock,
}

@Serializable
enum class AnalysisMode {
    RealMetadata,
    RuleBasedQuality,
    DemoMock,
}

@Serializable
data class QualitySignal(
    val name: String,
    val status: SignalStatus,
    val value: String,
    val message: String,
)

@Serializable
enum class SignalStatus {
    Pass,
    Warn,
    Fail,
}

enum class AgentType(val displayName: String) {
    Content("Content-Agent"),
    Qa("QA-Agent"),
    Dev("Dev-Agent"),
}

data class AgentTask(
    val type: AgentType,
    val prompt: String,
)

data class AgentResult(
    val type: AgentType,
    val title: String,
    val content: String,
    val fromMock: Boolean,
)

data class GatewayRunResult(
    val metadata: MediaMetadata,
    val routePlan: RoutePlan,
    val results: List<AgentResult>,
    val usedFallback: Boolean,
    val failureReason: String? = null,
)

data class RoutePlan(
    val routeId: String,
    val mode: String,
    val agents: List<RouteAgent>,
)

data class RouteAgent(
    val type: AgentType,
    val reason: String,
)

data class LlmConfig(
    val apiKey: String,
    val baseUrl: String = "https://tokenhub.tencentmaas.com/v1",
    val model: String = "deepseek-v4-flash",
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && !apiKey.startsWith("replace", ignoreCase = true)
}

fun demoMetadata(): MediaMetadata {
    return MediaMetadata(
        fileName = "podcast_ai_music_demo.mp3",
        mediaType = MediaType.Audio,
        durationMs = 12_000,
        fileSizeBytes = 2_480_000,
        mimeType = "audio/mpeg",
        inputPathOrUri = "demo://podcast_ai_music_demo.mp3",
        sampleRate = 44_100,
        bitRate = 192_000,
        channels = 2,
        source = MediaSource.DemoMock,
        analysisMode = AnalysisMode.DemoMock,
        userGoal = "展示 KMP Gateway 将音频 Metadata 并行分发给三个 Agent",
    )
}
