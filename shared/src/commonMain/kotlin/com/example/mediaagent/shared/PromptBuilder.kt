package com.example.mediaagent.shared

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PromptBuilder(
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
    },
) {
    fun buildTask(type: AgentType, metadata: MediaMetadata): AgentTask {
        val metadataJson = json.encodeToString(metadata)
        val prompt = when (type) {
            AgentType.Content -> contentPrompt(metadataJson)
            AgentType.Qa -> qaPrompt(metadataJson)
            AgentType.Dev -> devPrompt(metadataJson)
        }
        return AgentTask(type = type, prompt = prompt)
    }

    fun buildRoutePlan(metadata: MediaMetadata): RoutePlan {
        return RoutePlan(
            routeId = "kmp-media-gateway-demo",
            mode = "parallel",
            agents = listOf(
                RouteAgent(
                    type = AgentType.Content,
                    reason = "生成音频摘要、乐评和运营文案，服务内容生产场景。",
                ),
                RouteAgent(
                    type = AgentType.Qa,
                    reason = "基于真实 Metadata 和轻量规则信号检查文件风险，服务测试提效场景。",
                ),
                RouteAgent(
                    type = AgentType.Dev,
                    reason = "生成 Android Media3 播放器接入片段，服务开发集成场景。",
                ),
            ),
        )
    }

    private fun contentPrompt(metadataJson: String): String {
        return """
            你是 Content-Agent，一个面向音视频产品和运营的内容创作助手。

            你的职责：
            1. 基于真实媒体文件名、类型、时长、用户目标，生成适合演示的内容摘要。
            2. 如果没有真实转写文本，必须说明这是基于 Metadata 的内容建议，不要伪装成真实 ASR。
            3. 输出要适合移动端卡片展示，短、清晰、有传播感。
            4. 不要声称自己真实听到了音频。

            输出 Markdown，格式如下：
            ### 内容摘要
            用 1 到 2 句话概括这段音频/视频可能表达的内容。

            ### 朋友圈文案
            生成一段 60 字以内的中文文案，适合创作者发布。

            ### 运营建议
            给出 2 条短建议，帮助提升标题、封面或发布节奏。

            媒体元数据：
            $metadataJson
        """.trimIndent()
    }

    private fun qaPrompt(metadataJson: String): String {
        return """
            你是 QA-Agent，一个音视频质量测试助手。

            你的职责：
            1. 基于真实媒体 Metadata 和 qualitySignals 做一次轻量质量检查。
            2. 重点关注媒体类型、文件大小、时长、采样率、码率、声道、潜在爆音风险。
            3. 输出必须是严格 JSON，不要输出 Markdown。
            4. 如果缺少真实波形数据，请使用 ruleBased=true，不要声称已完成完整 DSP 分析。
            5. risks 可以包含 00:05 的“建议复核”风险点，但要说明它来自规则检测而非真实波形峰值。

            输出 JSON Schema：
            {
              "agent": "QA-Agent",
              "ruleBased": true,
              "overallStatus": "pass|warn|fail",
              "summary": "一句话质量结论",
              "checks": [
                {
                  "name": "sample_rate",
                  "status": "pass|warn|fail",
                  "value": "44100Hz",
                  "message": "来自真实 Metadata 或轻量规则检测的说明"
                }
              ],
              "risks": [
                {
                  "timecode": "00:05",
                  "level": "low|medium|high",
                  "type": "clipping|silence|noise|bitrate|metadata",
                  "message": "建议人工或 FFmpeg/MediaExtractor 复核"
                }
              ],
              "recommendations": ["..."]
            }

            媒体元数据：
            $metadataJson
        """.trimIndent()
    }

    private fun devPrompt(metadataJson: String): String {
        return """
            你是 Dev-Agent，一个面向 Android/KMP 开发者的代码生成助手。

            你的职责：
            1. 根据媒体类型和使用场景，生成 Android 端播放器集成建议。
            2. 优先使用 Media3 ExoPlayer 和 Jetpack Compose 示例。
            3. 代码要短，适合 Demo 卡片展示。
            4. 不要生成完整工程，只生成关键代码片段和接入说明。

            输出 Markdown，格式如下：
            ### 集成方案
            用 2 句话说明推荐方案。

            ### Android 代码片段
            ```kotlin
            // 生成可读的 Media3 + Compose 示例
            ```

            ### 注意事项
            列出 2 条接入注意事项。

            媒体元数据：
            $metadataJson
        """.trimIndent()
    }
}
