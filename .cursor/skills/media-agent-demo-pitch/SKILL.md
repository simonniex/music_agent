---
name: media-agent-demo-pitch
description: Prepare scripts, summaries, recording flows, and interview-style explanations for the KMP audio/video multi-agent gateway demo. Use when writing demo narration, WeChat pitch copy, project summaries, README content, or presentation materials for this media Agent Gateway project.
---

# Media Agent Demo Pitch

## Positioning

Present the project as:

> 基于 KMP 的音视频智能处理多脑网关：端侧 Gateway 捕捉媒体输入，并将同一份 Metadata 并行路由给 QA、运营、开发三个角色 Agent。

The key story is not deep audio processing. The key story is:

- KMP shared 层承载跨端 Gateway。
- Gateway 分流体现角色隔离。
- 多 Agent 并行处理贴合音视频工具链。
- Android Compose UI 快速展示完整闭环。

## 90-Second Demo Flow

Use this structure for recording:

1. Open Android app and show the title: “Media Agent Gateway”.
2. Select or mock one audio/video file.
3. Show metadata captured by KMP shared layer.
4. Show Gateway route graph: one input, three parallel agents.
5. Reveal three result cards with typewriter animation:
   - Content-Agent: 摘要和朋友圈文案。
   - QA-Agent: JSON 风险检查结果。
   - Dev-Agent: Android 播放器接入代码。
6. End with architecture sentence: “端侧 KMP 路由，云端多角色 Agent 并行处理。”

## Explanation Template

Use this short explanation:

```text
这个 Demo 的核心不是做一个大而全的播放器，而是验证一种架构：在 KMP shared 层实现端侧 Gateway，把同一份音视频 Metadata 按角色并行分发给多个 Agent。

QA-Agent 负责质量检查，Content-Agent 负责内容摘要和运营文案，Dev-Agent 负责输出端侧集成代码。这样可以把音视频团队里的测试、运营、开发诉求拆成清晰的角色边界。
```

## WeChat Pitch Template

```text
老师早！昨天拜读了您关于 OpenClaw 多 Agent 团队的文章后，我特别认同其中“Gateway 分流”和“角色隔离”的架构思路。

结合您提到的音视频业务和 KMP 技术栈，我昨晚做了一个概念 Demo：基于 KMP 的“音视频智能处理多脑网关”。

我在 KMP shared 层实现了 Gateway 路由逻辑：当 Android 端输入一段音频/视频后，端侧网关会把同一份媒体 Metadata 并行路由给三个独立 Agent：

QA-Agent 负责媒体参数和潜在缺陷检查；
Content-Agent 负责音频摘要、乐评和运营文案；
Dev-Agent 负责生成 Android 播放器集成代码。

时间比较紧，底层音视频分析目前做了部分 Mock，但 KMP 跨端路由、大模型 Prompt 分发、多角色 Agent 返回结果的闭环已经跑通。我录了一个 1 分钟演示，也把代码整理好了。

我觉得这种“端侧 KMP Gateway + 云端多角色 Agent”的模式，未来在音视频工具链、测试提效和内容生产上都有扩展空间。希望有机会请您指点。
```

## Tone Rules

- 强调“架构验证”和“业务贴合”，不要夸成完整产品。
- 主动说明音视频底层分析是 Demo Mock，显得可信。
- 多说 Gateway、角色隔离、KMP shared 层，少说泛泛的 AI 套壳。
- 面向老师或面试官时，用“请您指点”收尾。
