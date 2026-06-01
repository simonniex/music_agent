package com.example.mediaagent.shared

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TencentAsrClient(
    private val config: TencentAsrConfig,
    private val httpClient: HttpClient = tencentHttpClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    },
) {
    suspend fun transcribe(input: AudioInput): TranscriptResult {
        if (!config.isConfigured) {
            return TranscriptResult(
                text = "",
                source = TranscriptSource.Fallback,
                success = false,
                message = "腾讯云 ASR SecretId/SecretKey 未配置。",
            )
        }

        if (input.bytes != null && input.bytes.size > MAX_LOCAL_BYTES) {
            if (isCompressedAudio(input.metadata.fileName, input.metadata.mimeType)) {
                return TranscriptResult(
                    text = "",
                    source = TranscriptSource.NeedsPublicUrl,
                    success = false,
                    message = "压缩格式音频（如 MP3/M4A）超过 5MB 时不能按字节分片识别，请改用小于 5MB 的音频、上传 COS 公网 URL，或先导出为 WAV 再试。",
                )
            }
            return transcribeInChunks(input)
        }

        val request = when {
            input.publicUrl?.isNotBlank() == true -> CreateRecTaskPayload(
                engineModelType = config.engineModelType,
                sourceType = 0,
                url = input.publicUrl,
            )
            input.canUseLocalAsr -> CreateRecTaskPayload(
                engineModelType = config.engineModelType,
                sourceType = 1,
                data = PlatformCrypto.base64(input.bytes ?: ByteArray(0)),
                dataLen = input.bytes?.size,
            )
            input.bytes != null && input.bytes.isEmpty() -> return TranscriptResult(
                text = "",
                source = TranscriptSource.Fallback,
                success = false,
                message = "当前没有读取到真实音频数据。请点击上传音频，不要使用示例演示来测试 ASR。",
            )
            else -> return TranscriptResult(
                text = "",
                source = TranscriptSource.NeedsPublicUrl,
                success = false,
                message = "未读取到可上传的音频数据；大文件会尝试本地分片，若仍失败建议使用 COS 或公网 URL。",
            )
        }

        val createPayload = json.encodeToString(request)
        var createResponse = callTencent<CreateRecTaskResponse>(
            action = "CreateRecTask",
            payload = createPayload,
        )
        createResponse.response.error?.serverTimestamp()?.let { serverTimestamp ->
            createResponse = callTencent(
                action = "CreateRecTask",
                payload = createPayload,
                timestampOverride = serverTimestamp,
            )
        }
        val task = createResponse.response.data?.taskId
            ?: return TranscriptResult(
                text = "",
                source = TranscriptSource.TencentAsr,
                success = false,
                message = createResponse.response.error?.displayMessage()
                    ?: "腾讯云 ASR 创建任务失败：未返回 TaskId。",
            )

        repeat(40) {
            delay(3_000)
            val describePayload = json.encodeToString(DescribeTaskStatusPayload(taskId = task))
            var describeResponse = callTencent<DescribeTaskStatusResponse>(
                action = "DescribeTaskStatus",
                payload = describePayload,
            )
            describeResponse.response.error?.serverTimestamp()?.let { serverTimestamp ->
                describeResponse = callTencent(
                    action = "DescribeTaskStatus",
                    payload = describePayload,
                    timestampOverride = serverTimestamp,
                )
            }
            val status = describeResponse.response.data
                ?: return TranscriptResult(
                    text = "",
                    source = TranscriptSource.TencentAsr,
                    success = false,
                    message = describeResponse.response.error?.displayMessage()
                        ?: "腾讯云 ASR 查询任务失败：未返回识别状态。",
                )

            if (status.status == 2 || status.statusStr.equals("success", ignoreCase = true)) {
                return TranscriptResult(
                    text = status.result.trim(),
                    source = TranscriptSource.TencentAsr,
                    success = status.result.isNotBlank(),
                    message = "腾讯云 ASR 转写完成。",
                    durationSeconds = status.audioDuration,
                )
            }

            if (status.status == 3 || status.statusStr.equals("failed", ignoreCase = true)) {
                return TranscriptResult(
                    text = "",
                    source = TranscriptSource.TencentAsr,
                    success = false,
                    message = status.errorMsg.ifBlank { "腾讯云 ASR 任务失败。" },
                    durationSeconds = status.audioDuration,
                )
            }
        }

        return TranscriptResult(
            text = "",
            source = TranscriptSource.TencentAsr,
            success = false,
            message = "腾讯云 ASR 任务仍在处理中，请稍后重试或缩短音频。",
        )
    }

    private suspend fun transcribeInChunks(input: AudioInput): TranscriptResult {
        val bytes = input.bytes ?: ByteArray(0)
        val chunks = bytes.toList()
            .chunked(CHUNK_BYTES)
            .map { it.toByteArray() }
        val transcripts = mutableListOf<String>()

        chunks.forEachIndexed { index, chunk ->
            val result = transcribe(
                input.copy(
                    bytes = chunk,
                    publicUrl = null,
                    metadata = input.metadata.copy(
                        fileName = "${input.metadata.fileName}.part${index + 1}",
                        fileSizeBytes = chunk.size.toLong(),
                    ),
                ),
            )
            if (!result.success) {
                return TranscriptResult(
                    text = transcripts.joinToString("\n").trim(),
                    source = TranscriptSource.TencentAsr,
                    success = transcripts.isNotEmpty(),
                    message = "音频已分片处理，片段 ${index + 1}/${chunks.size} 识别失败：${result.message}",
                )
            }
            transcripts += "【片段 ${index + 1}/${chunks.size}】\n${result.text}"
        }

        return TranscriptResult(
            text = transcripts.joinToString("\n\n").trim(),
            source = TranscriptSource.TencentAsr,
            success = transcripts.isNotEmpty(),
            message = "腾讯云 ASR 分片转写完成，共 ${chunks.size} 个片段。",
        )
    }

    private suspend inline fun <reified T> callTencent(
        action: String,
        payload: String,
        timestampOverride: Long? = null,
    ): T {
        val timestamp = timestampOverride ?: PlatformCrypto.epochSeconds()
        val headers = signedHeaders(action, payload, timestamp)
        return httpClient.post("https://$HOST") {
            contentType(ContentType.parse("application/json; charset=utf-8"))
            headers.forEach { (name, value) -> header(name, value) }
            setBody(payload)
        }.body()
    }

    private fun signedHeaders(action: String, payload: String, timestamp: Long): Map<String, String> {
        val canonicalHeaders = "content-type:application/json; charset=utf-8\nhost:$HOST\nx-tc-action:${action.lowercase()}\n"
        val signedHeaders = "content-type;host;x-tc-action"
        val canonicalRequest = listOf(
            "POST",
            "/",
            "",
            canonicalHeaders,
            signedHeaders,
            PlatformCrypto.sha256Hex(payload),
        ).joinToString("\n")

        val date = PlatformCrypto.utcDate(timestamp)
        val credentialScope = "$date/$SERVICE/tc3_request"
        val stringToSign = listOf(
            "TC3-HMAC-SHA256",
            timestamp.toString(),
            credentialScope,
            PlatformCrypto.sha256Hex(canonicalRequest),
        ).joinToString("\n")

        val secretDate = PlatformCrypto.hmacSha256(("TC3" + config.effectiveSecretKey).toByteArray(Charsets.UTF_8), date)
        val secretService = PlatformCrypto.hmacSha256(secretDate, SERVICE)
        val secretSigning = PlatformCrypto.hmacSha256(secretService, "tc3_request")
        val signature = PlatformCrypto.hex(PlatformCrypto.hmacSha256(secretSigning, stringToSign))
        val authorization = "TC3-HMAC-SHA256 Credential=${config.effectiveSecretId}/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

        return mapOf(
            "Authorization" to authorization,
            "Host" to HOST,
            "X-TC-Action" to action,
            "X-TC-Timestamp" to timestamp.toString(),
            "X-TC-Version" to VERSION,
            "X-TC-Region" to config.region,
        )
    }

    companion object {
        const val MAX_LOCAL_BYTES: Int = 5 * 1024 * 1024
        private const val CHUNK_BYTES: Int = 4 * 1024 * 1024
        private const val HOST = "asr.tencentcloudapi.com"
        private const val SERVICE = "asr"
        private const val VERSION = "2019-06-14"

        fun isCompressedAudio(fileName: String, mimeType: String?): Boolean {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            if (ext in COMPRESSED_EXTENSIONS) return true
            val mime = mimeType?.lowercase().orEmpty()
            return mime.contains("mpeg") ||
                mime.contains("mp4") ||
                mime.contains("aac") ||
                mime.contains("ogg") ||
                mime.contains("flac") ||
                mime.contains("x-m4a")
        }

        private val COMPRESSED_EXTENSIONS = setOf("mp3", "m4a", "aac", "ogg", "flac", "wma", "mp4")
    }
}

private fun tencentHttpClient(): HttpClient {
    return HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) {
            requestTimeoutMillis = 75_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 75_000
        }
    }
}

@Serializable
private data class CreateRecTaskPayload(
    @SerialName("EngineModelType")
    val engineModelType: String,
    @SerialName("ChannelNum")
    val channelNum: Int = 1,
    @SerialName("ResTextFormat")
    val resTextFormat: Int = 0,
    @SerialName("SourceType")
    val sourceType: Int,
    @SerialName("Data")
    val data: String? = null,
    @SerialName("DataLen")
    val dataLen: Int? = null,
    @SerialName("Url")
    val url: String? = null,
)

@Serializable
private data class DescribeTaskStatusPayload(
    @SerialName("TaskId")
    val taskId: Long,
)

@Serializable
private data class CreateRecTaskResponse(
    @SerialName("Response")
    val response: CreateRecTaskOuter,
)

@Serializable
private data class CreateRecTaskOuter(
    @SerialName("Data")
    val data: CreateRecTaskData? = null,
    @SerialName("Error")
    val error: TencentApiError? = null,
)

@Serializable
private data class CreateRecTaskData(
    @SerialName("TaskId")
    val taskId: Long,
)

@Serializable
private data class DescribeTaskStatusResponse(
    @SerialName("Response")
    val response: DescribeTaskOuter,
)

@Serializable
private data class DescribeTaskOuter(
    @SerialName("Data")
    val data: TaskStatusData? = null,
    @SerialName("Error")
    val error: TencentApiError? = null,
)

@Serializable
private data class TaskStatusData(
    @SerialName("Status")
    val status: Int = 0,
    @SerialName("StatusStr")
    val statusStr: String = "",
    @SerialName("Result")
    val result: String = "",
    @SerialName("ErrorMsg")
    val errorMsg: String = "",
    @SerialName("AudioDuration")
    val audioDuration: Double? = null,
)

@Serializable
private data class TencentApiError(
    @SerialName("Code")
    val code: String = "",
    @SerialName("Message")
    val message: String = "",
) {
    fun displayMessage(): String {
        return "腾讯云 ASR 返回错误：$code${if (message.isNotBlank()) " - $message" else ""}"
    }

    fun serverTimestamp(): Long? {
        if (!code.equals("AuthFailure.SignatureExpire", ignoreCase = true)) return null
        return Regex("\\d{10}")
            .findAll(message)
            .lastOrNull()
            ?.value
            ?.toLongOrNull()
    }
}
