package com.example.mediaagent.shared

class MockLlmClient : LlmClient {
    override suspend fun complete(task: AgentTask): AgentResult {
        val content = when (task.type) {
            AgentType.Content -> contentResult()
            AgentType.Qa -> qaResult()
            AgentType.Dev -> devResult()
        }

        return AgentResult(
            type = task.type,
            title = task.type.displayName,
            content = content,
            fromMock = true,
        )
    }

    private fun contentResult(): String {
        return """
            ### 内容摘要
            这是一段围绕 AI 音乐创作与播客灵感的短音频，适合用来展示创作者如何快速把素材转成可发布内容。

            ### 朋友圈文案
            一段音频，三个 AI 角色同时开工：测质量、写文案、给代码。创作链路真的可以更轻。

            ### 运营建议
            - 标题可以突出“AI 音乐创作”和“多 Agent 工作流”。
            - 发布时搭配 15 秒录屏，更容易让观众理解 Gateway 分流。
        """.trimIndent()
    }

    private fun qaResult(): String {
        return """
            {
              "agent": "QA-Agent",
              "ruleBased": true,
              "overallStatus": "warn",
              "summary": "已基于真实文件 Metadata 和轻量规则完成检查；00:05 为建议复核点，不代表已完成真实波形峰值分析。",
              "checks": [
                {
                  "name": "sample_rate",
                  "status": "pass",
                  "value": "44100Hz",
                  "message": "采样率符合常见音乐和播客发布标准。"
                },
                {
                  "name": "bitrate",
                  "status": "pass",
                  "value": "192kbps",
                  "message": "码率适合移动端在线播放。"
                },
                {
                  "name": "channels",
                  "status": "pass",
                  "value": "2",
                  "message": "双声道配置正常。"
                },
                {
                  "name": "duration",
                  "status": "pass",
                  "value": "12s",
                  "message": "Demo 片段时长适合快速演示。"
                }
              ],
              "risks": [
                {
                  "timecode": "00:05",
                  "level": "medium",
                  "type": "clipping",
                  "message": "规则检测建议复核此时间点；后续可接入 FFmpeg 或 MediaExtractor 做真实峰值判断。"
                },
                {
                  "timecode": "00:09",
                  "level": "low",
                  "type": "noise",
                  "message": "尾部可能存在轻微环境噪声。"
                }
              ],
              "recommendations": [
                "接入真实波形分析后，可用峰值和 LUFS 指标替换当前 Mock 风险。",
                "发布前建议统一响度并做一次端侧播放验证。"
              ]
            }
        """.trimIndent()
    }

    private fun devResult(): String {
        return """
            ### 集成方案
            Android 端建议使用 Media3 ExoPlayer 承载播放能力，KMP shared 层只负责 Gateway 路由、Prompt 构造和结果聚合。

            ### Android 代码片段
            ```kotlin
            val player = ExoPlayer.Builder(context).build()
            player.setMediaItem(MediaItem.fromUri(mediaUri))
            player.prepare()
            player.play()
            ```

            ### 注意事项
            - API Key 不要硬编码在客户端，Demo 阶段可先放在本地配置中。
            - 后续接入真实音频分析时，可以用 MediaExtractor 或 FFmpeg 补齐底层能力。
        """.trimIndent()
    }
}
