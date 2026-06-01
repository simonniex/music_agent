package com.example.mediaagent.shared

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class YoutuVitaClient(
    private val config: VisionConfig,
    private val httpClient: HttpClient = visionHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun recognizeLyricImage(image: ImageAttachment): VisionRecognitionResult {
        if (!config.isConfigured) {
            return VisionRecognitionResult(
                success = false,
                message = "YT-VITA API Key 未配置，无法识别歌词图片。",
            )
        }
        if (!image.hasContent) {
            return VisionRecognitionResult(
                success = false,
                message = "未读取到歌词图片内容。",
            )
        }
        return analyzeImage(
            image = image,
            prompt = """
                这是一张歌词相关图片，可能是歌词截图、歌词海报、播放器界面或 LRC 图片。
                请尽量逐字还原图片中的歌词文字，不要编造图片里没有的内容。
                如果能识别出歌名、歌手，也请一并提取。
                只输出 JSON，不要 Markdown，格式如下：
                {"lyrics":"完整歌词或可见歌词片段","songName":"歌名或空字符串","artistName":"歌手或空字符串","mood":"图片氛围或空字符串"}
            """.trimIndent(),
            kind = VisionImageKind.LyricImage,
        )
    }

    suspend fun recognizeScreenshot(image: ImageAttachment, note: String): VisionRecognitionResult {
        if (!config.isConfigured) {
            return VisionRecognitionResult(
                success = false,
                message = "YT-VITA API Key 未配置，无法识别截图。",
            )
        }
        if (!image.hasContent) {
            return VisionRecognitionResult(
                success = false,
                message = "未读取到截图内容。",
            )
        }
        val noteHint = note.ifBlank { "无" }
        return analyzeImage(
            image = image,
            prompt = """
                这是一张与音乐或内容创作相关的截图，可能是播放器界面、歌词页、评论区、专辑封面或短视频草稿。
                用户补充说明：$noteHint
                请提取图片中与文案生成相关的信息，尽量逐字还原可见歌词，不要编造图片里没有的内容。
                只输出 JSON，不要 Markdown，格式如下：
                {"lyrics":"可见歌词或空字符串","songName":"歌名或空字符串","artistName":"歌手或空字符串","mood":"评论情绪/封面氛围/平台风格或空字符串","summary":"一句话概括截图信息"}
            """.trimIndent(),
            kind = VisionImageKind.Screenshot,
        )
    }

    private suspend fun analyzeImage(
        image: ImageAttachment,
        prompt: String,
        kind: VisionImageKind,
    ): VisionRecognitionResult {
        val originalBytes = image.bytes ?: return VisionRecognitionResult(success = false, message = "图片 bytes 为空。")

        // 发送前降采样压缩，避免高分辨率原图导致 YT-VITA 请求超时。
        val compressed = PlatformImage.downscaleToJpeg(originalBytes, MAX_IMAGE_DIMENSION, JPEG_QUALITY)
        val bytes = compressed ?: originalBytes
        val mimeType = if (compressed != null) "image/jpeg" else image.effectiveMimeType

        if (bytes.size > MAX_IMAGE_BYTES) {
            return VisionRecognitionResult(
                success = false,
                message = "图片过大（${bytes.size} bytes），请使用更小或分辨率更低的截图。",
            )
        }

        val dataUrl = "data:$mimeType;base64,${PlatformCrypto.base64(bytes)}"
        val url = config.baseUrl.trimEnd('/') + "/chat/completions"
        val body = buildJsonObject {
            put("model", config.model)
            put("stream", false)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put(
                                "content",
                                buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put("type", "image_url")
                                            put(
                                                "image_url",
                                                buildJsonObject {
                                                    put("url", dataUrl)
                                                },
                                            )
                                        },
                                    )
                                    add(
                                        buildJsonObject {
                                            put("type", "text")
                                            put("text", prompt)
                                        },
                                    )
                                },
                            )
                        },
                    )
                },
            )
        }

        return runCatching {
            val raw = httpClient.post(url) {
                bearerAuth(config.apiKey)
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }.body<String>()

            val error = runCatching { json.decodeFromString<VisionErrorResponse>(raw) }.getOrNull()?.error
            if (error != null) {
                return VisionRecognitionResult(
                    success = false,
                    message = "YT-VITA 返回错误：${error.code ?: error.type ?: "unknown"} - ${error.message}",
                )
            }

            val response = json.decodeFromString<VisionChatResponse>(raw)
            val content = response.choices.firstOrNull()?.message?.content.orEmpty().trim()
            if (content.isBlank()) {
                return VisionRecognitionResult(success = false, message = "YT-VITA 返回空内容。")
            }
            parseVisionContent(content, kind)
        }.getOrElse {
            VisionRecognitionResult(
                success = false,
                message = "YT-VITA 调用失败：${it.message ?: it::class.simpleName ?: "unknown"}",
            )
        }
    }

    private fun parseVisionContent(content: String, kind: VisionImageKind): VisionRecognitionResult {
        val jsonText = extractJsonObject(content)
        if (jsonText != null) {
            runCatching {
                val obj = json.parseToJsonElement(jsonText).jsonObject
                val lyrics = obj.stringValue("lyrics")
                val songName = obj.stringValue("songName")
                val artistName = obj.stringValue("artistName")
                val mood = obj.stringValue("mood")
                val summary = obj.stringValue("summary")
                val extracted = lyrics.ifBlank { summary.ifBlank { content } }
                return VisionRecognitionResult(
                    success = extracted.isNotBlank(),
                    extractedText = extracted,
                    songName = songName,
                    artistName = artistName,
                    lyrics = lyrics,
                    mood = mood,
                    summary = summary,
                    message = if (extracted.isNotBlank()) "YT-VITA 图片识别完成。" else "YT-VITA 未提取到有效文本。",
                    kind = kind,
                )
            }
        }

        return VisionRecognitionResult(
            success = content.isNotBlank(),
            extractedText = content,
            lyrics = content,
            message = "YT-VITA 图片识别完成（非 JSON 响应，已保留原文）。",
            kind = kind,
        )
    }

    private fun extractJsonObject(content: String): String? {
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(content)?.groupValues?.getOrNull(1)?.trim()
        if (!fenced.isNullOrBlank() && fenced.startsWith("{")) return fenced
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start >= 0 && end > start) return content.substring(start, end + 1)
        return null
    }

    companion object {
        const val MAX_IMAGE_BYTES: Int = 4 * 1024 * 1024
        const val MAX_IMAGE_DIMENSION: Int = 1280
        const val JPEG_QUALITY: Int = 80
    }
}

private fun JsonObject.stringValue(key: String): String {
    return this[key]?.jsonPrimitive?.content.orEmpty().trim()
}

private fun visionHttpClient(): HttpClient {
    return HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 120_000
        }
    }
}

@Serializable
private data class VisionChatResponse(
    val choices: List<VisionChoice> = emptyList(),
)

@Serializable
private data class VisionChoice(
    val message: VisionMessage = VisionMessage(),
)

@Serializable
private data class VisionMessage(
    val content: String = "",
)

@Serializable
private data class VisionErrorResponse(
    val error: VisionError? = null,
)

@Serializable
private data class VisionError(
    val message: String = "",
    val type: String? = null,
    val code: String? = null,
)
