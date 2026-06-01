package com.example.mediaagent.shared

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class AgentRouter(
    private val promptBuilder: PromptBuilder = PromptBuilder(),
    private val primaryClient: LlmClient,
    private val fallbackClient: LlmClient = MockLlmClient(),
) {
    suspend fun run(metadata: MediaMetadata): GatewayRunResult = coroutineScope {
        val enrichedMetadata = if (metadata.qualitySignals.isEmpty()) {
            LightweightQualityAnalyzer.attachSignals(metadata)
        } else {
            metadata
        }
        val routePlan = promptBuilder.buildRoutePlan(enrichedMetadata)
        val tasks = routePlan.agents.map { promptBuilder.buildTask(it.type, enrichedMetadata) }

        val results = tasks
            .map { task ->
                async {
                    try {
                        AgentCallOutcome(primaryClient.complete(task), null)
                    } catch (throwable: Throwable) {
                        AgentCallOutcome(
                            result = fallbackClient.complete(task),
                            failureReason = throwable.message ?: throwable::class.simpleName ?: "unknown error",
                        )
                    }
                }
            }
            .awaitAll()
        val agentResults = results.map { it.result }.sortedBy { it.type.ordinal }
        val failureReason = results.firstNotNullOfOrNull { it.failureReason }

        GatewayRunResult(
            metadata = enrichedMetadata,
            routePlan = routePlan,
            results = agentResults,
            usedFallback = failureReason != null,
            failureReason = failureReason,
        )
    }
}

class MediaAgentGateway(
    config: LlmConfig,
) {
    private val router = AgentRouter(
        primaryClient = if (config.isConfigured) DeepSeekClient(config) else MockLlmClient(),
        fallbackClient = MockLlmClient(),
    )

    suspend fun analyze(metadata: MediaMetadata = demoMetadata()): GatewayRunResult {
        return router.run(metadata)
    }
}

private data class AgentCallOutcome(
    val result: AgentResult,
    val failureReason: String?,
)
