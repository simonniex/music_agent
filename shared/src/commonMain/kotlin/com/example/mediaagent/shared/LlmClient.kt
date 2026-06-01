package com.example.mediaagent.shared

interface LlmClient {
    suspend fun complete(task: AgentTask): AgentResult
}
