# 音频文案生成器

基于 Kotlin Multiplatform 的 Android + Compose Desktop 双端 Demo。上传音频后，系统调用腾讯云 ASR 转写，结合可选辅助输入与图片识别，再调用 DeepSeek 生成适合发布的内容解析和社交平台文案。

## 用户流程

```text
上传音频
-> 可选填写歌名/歌手/喜欢的歌词
-> 可选上传多张歌词图片 / 多张参考截图
-> 腾讯云 ASR 转写
-> 歌词/截图上下文增强
-> DeepSeek 解析内容、情绪、亮点
-> 生成朋友圈 / 小红书 / 短视频文案
```

用户模式只展示最终可用结果；开发者模式展示转写、步骤状态和调试信息。

## 辅助输入

歌曲 ASR 容易受伴奏、混响、转音影响而识别错误，因此支持以下可选输入：

- 歌曲名 + 歌手：作为歌词检索与上下文补充（当前未接入独立歌词库 API）。
- 喜欢的歌词：控制文案情绪和表达角度。
- 歌词图片：通过腾讯 TokenHub YT-VITA 识别图片中的歌词。
- 参考截图：播放器截图、歌词页、评论区、专辑封面等，用于提取歌名、歌手、评论情绪和视觉氛围。

文本来源优先级：

```text
用户手动粘贴歌词 / LRC
> 歌词图片识别
> 参考截图识别
> 歌曲名 + 歌手（ASR 为空时）
> ASR 转写
> 文件名/元信息推断
```

文案生成优先使用「标准文本」，而不是直接信任 ASR 转写。歌词内容需有用户输入、OCR 或检索结果作为依据，不应凭空编造。

## 配置

本地密钥放在 `local.properties`，该文件已被 `.gitignore` 忽略。

```properties
sdk.dir=D\:\\AndroidTools\\AndroidSDK

DEEPSEEK_API_KEY=<DeepSeek API Key>
DEEPSEEK_BASE_URL=https://tokenhub.tencentmaas.com/v1
DEEPSEEK_MODEL=deepseek-v4-flash

TENCENT_SECRET_ID=<腾讯云 SecretId>
TENCENT_SECRET_KEY=<腾讯云 SecretKey>
TENCENT_ASR_REGION=ap-guangzhou
TENCENT_ASR_ENGINE=16k_zh
```

密钥请勿提交到版本库。若曾在开发过程中泄露，建议在对应控制台轮换。

## 腾讯云 ASR 限制

当前实现使用腾讯云录音文件识别 `CreateRecTask / DescribeTaskStatus`。

- 小于等于 5MB 的本地音频：App 直接 Base64 上传到腾讯云 ASR。
- 大于 5MB 的音频：本地直传接口不支持，需上传到 COS 或提供公网可下载 URL 后再提交 `Url`。
- 几分钟音频通常可能超过 5MB，完整链路建议后续补 COS 上传。

大于 5MB 的音频不能通过「流式输入并传递模型隐藏状态」解决，因为腾讯云 ASR API 不暴露模型 hidden state。可落地方案：

- 上传到腾讯云 COS，使用公网/COS URL 调用 `CreateRecTask(SourceType=0)`。
- 或在客户端/服务端用 FFmpeg 按时间切成多个小于 5MB 的片段，分别 ASR 后拼接转写文本。

当前版本也支持本地分片：当音频大于 5MB 但仍在本地读取上限内时，shared 层按约 4MB 分片分别提交 ASR，再拼接 transcript。该方案适合 Demo 验证；生产环境更推荐 COS URL 或 FFmpeg 按时间切片。

## 运行 Android

```powershell
.\gradlew.bat :androidApp:assembleDebug
```

或在 Android Studio 中选择 `androidApp` 运行。进入 App 后点击「上传音频生成文案」，选择 `.mp3`、`.wav`、`.m4a` 等音频文件。

## 运行桌面端

```powershell
.\gradlew.bat :desktopApp:run
```

桌面窗口打开后点击「选择音频文件」。

Android 和桌面端交互一致：

- 用户模式：上传、生成、朋友圈文案、小红书文案和短视频标题。
- 开发者模式：转写预览、解析结果、生成状态、音频文件信息和各步骤详情。
- 处理状态：默认悬浮状态按钮；展开后可查看每一步；「查看」只显示当前步骤，「查看全部」显示完整结果。

## 技术结构

```text
androidApp       Android 用户界面
desktopApp       Compose Desktop 用户界面
shared           KMP 共享层
  - TencentAsrClient
  - YoutuVitaClient
  - AudioCopywritingGateway
  - LyricTextResolver
  - DeepSeekClient
  - CopywritingPromptBuilder
  - MediaMetadataFactory
```

架构说明见 [AUDIO_COPYWRITING_TECH_DOC.md](./AUDIO_COPYWRITING_TECH_DOC.md)

## 技术栈

- Kotlin Multiplatform
- Android Jetpack Compose
- Compose Desktop
- Tencent Cloud ASR
- Tencent TokenHub YT-VITA
- DeepSeek Chat Completions API
- Ktor Client
- kotlinx.serialization
- Kotlin Coroutines
