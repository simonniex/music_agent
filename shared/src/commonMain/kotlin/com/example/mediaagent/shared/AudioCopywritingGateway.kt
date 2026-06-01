package com.example.mediaagent.shared

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class AudioCopywritingGateway(
    private val config: AudioCopywritingConfig,
    private val asrClient: TencentAsrClient = TencentAsrClient(config.asr),
    private val llmClient: LlmClient = if (config.llm.isConfigured) DeepSeekClient(config.llm) else MockLlmClient(),
    private val visionClient: YoutuVitaClient = YoutuVitaClient(config.effectiveVision()),
) {
    suspend fun generate(input: AudioInput): AudioCopywritingResult {
        val enriched = LightweightQualityAnalyzer.attachSignals(input.metadata)
        val enrichedInput = input.copy(metadata = enriched)

        val (transcript, visionContext) = coroutineScope {
            val asrDeferred = async {
                runCatching { asrClient.transcribe(enrichedInput) }.getOrElse {
                    TranscriptResult(
                        text = "",
                        source = TranscriptSource.Fallback,
                        success = false,
                        message = "ASR 调用失败：${it.message ?: it::class.simpleName ?: "unknown"}",
                    )
                }
            }
            val visionDeferred = async { recognizeVisionContext(enrichedInput.auxiliary) }
            asrDeferred.await() to visionDeferred.await()
        }

        val standardText = LyricTextResolver.resolve(
            auxiliary = enrichedInput.auxiliary,
            asrResult = transcript,
            vision = visionContext,
        )
        val prompt = CopywritingPromptBuilder.build(enriched, transcript, enrichedInput.auxiliary, visionContext, standardText)
        val generated = runCatching {
            llmClient.complete(AgentTask(AgentType.Content, prompt))
        }
        val generatedContent = generated.getOrNull()?.content
            ?: CopywritingFallbackBuilder.build(enriched, transcript, enrichedInput.auxiliary, standardText, generated.exceptionOrNull())
        val llmSuccess = generated.isSuccess && generated.getOrNull()?.fromMock == false

        return AudioCopywritingResult(
            metadata = enriched,
            transcript = transcript,
            visionContext = visionContext,
            standardText = standardText,
            generatedMarkdown = generatedContent,
            fromFallback = !llmSuccess || (!transcript.success && standardText.source == StandardTextSource.Fallback),
            generationMessage = if (llmSuccess) {
                "DeepSeek 已根据标准文本生成文案（来源：${standardText.sourceLabel}）。"
            } else {
                "DeepSeek 生成未完成，已基于标准文本生成本地文案：${generated.exceptionOrNull()?.message ?: "未返回详细原因"}"
            },
            llmSuccess = llmSuccess,
        )
    }

    private suspend fun recognizeVisionContext(auxiliary: AuxiliaryContext): VisionContextBundle {
        if (!config.effectiveVision().isConfigured) {
            return VisionContextBundle()
        }

        return coroutineScope {
            val lyricDeferred = auxiliary.allLyricImages.map { image ->
                async { visionClient.recognizeLyricImage(image) }
            }
            val screenshotDeferred = auxiliary.allScreenshots.map { image ->
                async { visionClient.recognizeScreenshot(image, auxiliary.screenshotNote) }
            }
            val lyricResults = lyricDeferred.map { it.await() }
            val screenshotResults = screenshotDeferred.map { it.await() }
            VisionContextBundle(
                lyricImages = lyricResults,
                screenshots = screenshotResults,
            )
        }
    }
}

object CopywritingPromptBuilder {
    fun build(
        metadata: MediaMetadata,
        transcript: TranscriptResult,
        auxiliary: AuxiliaryContext,
        vision: VisionContextBundle,
        standardText: StandardTextResult,
    ): String {
        val visionLyricBlock = vision.lyricImages
            .mapIndexed { index, result -> formatVisionResult("歌词图片识别 ${index + 1}", result) }
            .joinToString("\n\n")
        val visionScreenshotBlock = vision.screenshots
            .mapIndexed { index, result -> formatVisionResult("截图识别 ${index + 1}", result) }
            .joinToString("\n\n")
        val useStandardText = standardText.text.isNotBlank() &&
            standardText.source != StandardTextSource.Fallback

        return """
            你是一个面向音乐人、播客作者、短视频创作者的中文文案助手。

            用户上传了一段完整音频，系统已经尽可能完成转写、图片识别和基础解析。请面向最终用户输出，不要提 Agent、Gateway、路由、KMP 等技术实现。

            重要约束：
            1. 生成歌词相关表达时，优先使用“标准文本”，不要凭空编造完整歌词。
            2. 标准文本来源优先级：用户手动输入 > 歌词图片 YT-VITA 识别 > 截图 YT-VITA 识别 > ASR 转写 > 歌名歌手线索。
            3. 如果 ASR 转写质量差但已有图片识别或用户输入歌词，请以高置信文本为准，不要把 ASR 错字写进文案。
            4. 如果 transcript.success=false 且没有可靠标准文本，必须说明“当前未完成完整转写”，只能基于文件信息和图片线索给出保守建议。
            5. 不要声称完成了专业编曲分析；可以用“听感方向/内容气质/情绪倾向”这种产品化表达。
            6. 输出要适合 App 直接展示。
            7. 如果用户提供了喜欢的歌词片段，要优先围绕这些句子的情绪和表达生成文案。

            请按以下 Markdown 结构输出：

            ## 音频解析
            - 内容主题：
            - 情绪氛围：
            - 适合人群：

            ## 词/内容亮点
            提炼 3 到 5 个亮点。优先引用标准文本中的真实词句；如果没有可靠文本，说明需要补充歌词图片或手动输入。

            ## 曲风/听感建议
            基于文件信息、标准文本和图片上下文，给出保守的听感描述和发布建议。

            ## 朋友圈文案
            给 2 条，每条 80 字以内。

            ## 小红书文案
            标题 3 个，正文 1 段。

            ## 短视频标题
            给 5 个标题。

            媒体信息：
            文件名：${metadata.fileName}
            类型：${metadata.mediaType}
            大小：${metadata.fileSizeBytes} bytes
            时长：${metadata.displayDuration}
            MIME：${metadata.mimeType ?: "unknown"}

            用户辅助输入：
            歌曲名：${auxiliary.songName.ifBlank { standardText.songName.ifBlank { "未提供" } }}
            歌手：${auxiliary.artistName.ifBlank { standardText.artistName.ifBlank { "未提供" } }}
            喜欢的歌词/片段：${auxiliary.favoriteLyrics.ifBlank { "未提供" }}
            歌词图片：${auxiliary.allLyricImages.map { it.fileName }.filter { it.isNotBlank() }.joinToString("、").ifBlank { "未提供" }}
            截图：${auxiliary.allScreenshots.map { it.fileName }.filter { it.isNotBlank() }.joinToString("、").ifBlank { "未提供" }}
            截图说明：${auxiliary.screenshotNote.ifBlank { "未提供" }}

            标准文本（优先用于歌词/内容理解）：
            来源：${standardText.sourceLabel}
            ${if (useStandardText) standardText.text else "暂无可靠标准文本"}

            ASR 转写（仅供参考，歌曲场景可能不准）：
            success=${transcript.success}
            source=${transcript.source}
            message=${transcript.message}
            ${transcript.text.ifBlank { "无 ASR 转写文本" }}

            $visionLyricBlock
            $visionScreenshotBlock
        """.trimIndent()
    }

    private fun formatVisionResult(title: String, result: VisionRecognitionResult): String {
        if (!result.success && result.extractedText.isBlank()) {
            return "$title：${result.message}"
        }
        return """
            $title（YT-VITA）：
            状态：${result.message}
            识别歌词：${result.lyrics.ifBlank { "无" }}
            识别歌名：${result.songName.ifBlank { "无" }}
            识别歌手：${result.artistName.ifBlank { "无" }}
            氛围/摘要：${result.mood.ifBlank { result.summary.ifBlank { "无" } }}
        """.trimIndent()
    }
}

object CopywritingFallbackBuilder {
    fun build(
        metadata: MediaMetadata,
        transcript: TranscriptResult,
        auxiliary: AuxiliaryContext,
        standardText: StandardTextResult,
        error: Throwable?,
    ): String {
        val text = standardText.text
            .ifBlank { transcript.text }
            .replace(Regex("\\s+"), " ")
            .trim()
        val preview = auxiliary.favoriteLyrics
            .ifBlank { text.take(160) }
            .ifBlank { metadata.fileName }
        val keywords = extractKeywords(text)
        val subject = listOf(
            auxiliary.songName.ifBlank { standardText.songName },
            auxiliary.artistName.ifBlank { standardText.artistName },
        )
            .filter { it.isNotBlank() }
            .joinToString(" - ")
            .ifBlank { preview }

        return """
            ## 音频解析
            - 内容主题：围绕「$subject」展开，适合提炼成创作故事、歌曲介绍或播客摘要。
            - 情绪氛围：整体可包装为真诚、叙事感、适合社交平台分享的内容。
            - 适合人群：关注音乐创作、生活表达、情绪共鸣和个人故事的用户。

            ## 词/内容亮点
            ${keywords.joinToString("\n") { "- $it" }.ifBlank { "- 已完成转写，但关键词较少，建议补充歌词图片或手动输入歌词。" }}

            ## 曲风/听感建议
            目前本地生成无法判断真实编曲细节，但可以根据标准文本（来源：${standardText.sourceLabel}）把它包装成“有故事感、适合安静聆听/分享心情”的作品。

            ## 朋友圈文案
            1. 刚整理了一段关于「$subject」的音频，里面有些情绪和故事，很适合慢慢听完。
            2. ${auxiliary.favoriteLyrics.ifBlank { "有些表达不一定要很用力，一段声音就能把当下的心情讲清楚。" }}

            ## 小红书文案
            标题：
            1. 这段音频里的情绪，真的很适合夜晚听
            2. 用一段声音记录当下的故事
            3. 听完这段，突然理解了创作的意义

            正文：
            今天整理了一段音频，里面有表达、有情绪，也有一些值得被听见的细节。它不只是一个文件，更像是一段可以被分享的创作记录。

            ## 短视频标题
            1. 这段声音，藏着一个故事
            2. 几分钟听完一种情绪
            3. 原来音频也可以这么有画面感
            4. 适合深夜听的一段表达
            5. 把创作变成可以发布的文案

            > 本段为本地辅助生成。DeepSeek 状态：${error?.message ?: "未返回详细原因"}
        """.trimIndent()
    }

    private fun extractKeywords(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        return text
            .split(Regex("[，。！？、\\s,.!?]+"))
            .map { it.trim() }
            .filter { it.length in 2..12 }
            .distinct()
            .take(5)
    }
}
