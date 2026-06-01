---
name: media-agent-gateway
description: Design and implement a KMP-based audio/video Agent Gateway demo. Use when building AgentRouter, PromptBuilder, Ktor LLM clients, media metadata models, or multi-agent routing for Content-Agent, QA-Agent, and Dev-Agent.
---

# Media Agent Gateway

## Use This Skill When

Use this skill when the task involves the “音视频智能处理多脑网关” Demo, especially:

- KMP shared 层的 Agent 路由逻辑。
- Content-Agent、QA-Agent、Dev-Agent 的 Prompt 分发。
- 媒体 Metadata 建模。
- Ktor 大模型 API 调用封装。
- Android Compose 展示三脑并行结果。

## Core Architecture

Keep the business logic in the KMP `shared` module:

- `MediaMetadata`: media input model.
- `AgentType`: `CONTENT`, `QA`, `DEV`.
- `AgentPrompt`: rendered prompt payload.
- `AgentResponse`: unified response model.
- `PromptBuilder`: converts metadata into role-specific prompts.
- `LLMClient`: wraps Ktor request/response handling.
- `AgentRouter`: fans out one media input to multiple agents in parallel.

Android UI should stay thin:

- Pick or mock one media file.
- Send metadata to shared `AgentRouter`.
- Render Gateway status and three result cards.
- Add typewriter animation at the UI layer only.

## Routing Rules

Default route:

```kotlin
listOf(AgentType.CONTENT, AgentType.QA, AgentType.DEV)
```

Run agents concurrently with coroutines:

```kotlin
coroutineScope {
    agentTypes
        .map { type -> async { callAgent(type, metadata) } }
        .awaitAll()
}
```

Do not perform real DSP/audio analysis for the demo unless explicitly requested. Prefer metadata extraction plus mock risk results.

## Prompt Source

Use `prompts/media-agent-prompts.md` as the human-readable source of truth.

Use `prompts/media-agent-prompts.json` when a compact machine-readable prompt map is easier to load into code.

## Expected Agent Behavior

Content-Agent:

- Outputs a short content summary.
- Generates creator/朋友圈 copy.
- Gives 1-2 operation suggestions.

QA-Agent:

- Outputs strict JSON.
- Includes `ruleBased` when only lightweight Metadata rules are available.
- Reports media type, file size, duration, sample rate, bitrate, channels, and review-needed risks.

Dev-Agent:

- Outputs concise Markdown.
- Recommends Media3 ExoPlayer for Android playback.
- Shows a short Kotlin/Compose code snippet.

## Implementation Bias

Prioritize demo clarity over completeness:

- Prefer real Metadata and explicit rule-based analysis over pretending to have full DSP.
- Keep shared APIs small and easy to explain in a 90-second recording.
- Make the Gateway visible in code and UI; it is the main technical point.
- Keep prompts role-isolated; do not let one Agent answer all concerns.
