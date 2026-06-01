package com.example.mediaagent.shared

import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class DeepSeekClient(
    private val config: LlmConfig,
    private val httpClient: HttpClient = defaultHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : LlmClient {
    override suspend fun complete(task: AgentTask): AgentResult {
        if (!config.isConfigured) {
            error("DeepSeek API key is not configured.")
        }

        val url = config.baseUrl.trimEnd('/') + "/chat/completions"
        val rawResponse = httpClient.post(url) {
            bearerAuth(config.apiKey)
            contentType(ContentType.Application.Json)
            setBody(
                ChatCompletionRequest(
                    model = config.model,
                    messages = listOf(
                        ChatMessage(
                            role = "system",
                            content = "你是音视频智能处理多脑网关中的一个专属 Agent。请严格按用户要求输出，避免夸大真实音视频分析能力。",
                        ),
                        ChatMessage(role = "user", content = task.prompt),
                    ),
                    temperature = when (task.type) {
                        AgentType.Qa -> 0.2
                        AgentType.Dev -> 0.3
                        AgentType.Content -> 0.7
                    },
                    maxTokens = 1800,
                ),
            )
        }.bodyAsText()

        val errorResponse = runCatching {
            json.decodeFromString<DeepSeekErrorResponse>(rawResponse)
        }.getOrNull()
        if (errorResponse?.error != null) {
            error("DeepSeek API error: ${errorResponse.error.code} - ${errorResponse.error.message}")
        }

        val response = runCatching {
            json.decodeFromString<ChatCompletionResponse>(rawResponse)
        }.getOrElse {
            error("DeepSeek response parse failed: ${it.message}")
        }

        val content = response.choices.firstOrNull()?.message?.content.orEmpty().trim()
        if (content.isBlank()) {
            val finishReason = response.choices.firstOrNull()?.finishReason ?: "none"
            error("DeepSeek returned an empty response. finish_reason=$finishReason")
        }

        return AgentResult(
            type = task.type,
            title = task.type.displayName,
            content = content,
            fromMock = false,
        )
    }
}

private fun defaultHttpClient(): HttpClient {
    return HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                },
            )
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 25_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 25_000
        }
    }
}

@Serializable
private data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double,
    @SerialName("max_tokens")
    val maxTokens: Int,
    val stream: Boolean = false,
)

@Serializable
private data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class ChatCompletionResponse(
    val choices: List<ChatChoice> = emptyList(),
)

@Serializable
private data class ChatChoice(
    val message: ChatMessage,
    @SerialName("finish_reason")
    val finishReason: String? = null,
)

@Serializable
private data class DeepSeekErrorResponse(
    val error: DeepSeekError? = null,
)

@Serializable
private data class DeepSeekError(
    val message: String = "",
    val type: String? = null,
    val code: String? = null,
)
