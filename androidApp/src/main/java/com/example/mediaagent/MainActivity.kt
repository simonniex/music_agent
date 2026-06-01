package com.example.mediaagent

import android.app.Application
import android.content.Context
import android.media.Image
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediaagent.shared.AudioCopywritingConfig
import com.example.mediaagent.shared.AudioCopywritingGateway
import com.example.mediaagent.shared.AudioCopywritingResult
import com.example.mediaagent.shared.AudioInput
import com.example.mediaagent.shared.AuxiliaryContext
import com.example.mediaagent.shared.LlmConfig
import com.example.mediaagent.shared.MediaMetadata
import com.example.mediaagent.shared.MediaMetadataFactory
import com.example.mediaagent.shared.ImageAttachment
import com.example.mediaagent.shared.TencentAsrConfig
import com.example.mediaagent.shared.VisionConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: CopywritingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CopywritingScreen(viewModel)
            }
        }
    }
}

class CopywritingViewModel(application: Application) : AndroidViewModel(application) {
    private val asrConfig = TencentAsrConfig(
        secretId = application.secretString(R.string.tencent_secret_id, LocalTencentConfig.SECRET_ID),
        secretKey = application.secretString(R.string.tencent_secret_key, LocalTencentConfig.SECRET_KEY),
        region = application.secretString(R.string.tencent_asr_region, LocalTencentConfig.REGION),
        engineModelType = application.secretString(R.string.tencent_asr_engine, LocalTencentConfig.ENGINE),
    )

    private val gateway = AudioCopywritingGateway(
        AudioCopywritingConfig(
            llm = LlmConfig(
                apiKey = application.getString(R.string.deepseek_api_key),
                baseUrl = application.getString(R.string.deepseek_base_url),
                model = application.getString(R.string.deepseek_model),
            ),
            asr = asrConfig,
            vision = VisionConfig(
                apiKey = application.getString(R.string.deepseek_api_key),
                baseUrl = application.getString(R.string.deepseek_base_url),
                model = application.getString(R.string.vita_model),
            ),
        ),
    )

    private val _uiState = MutableStateFlow(CopywritingUiState(asrReady = asrConfig.isConfigured))
    val uiState: StateFlow<CopywritingUiState> = _uiState.asStateFlow()

    fun selectAudio(input: AudioInput) {
        _uiState.value = _uiState.value.copy(
            input = input,
            metadata = input.metadata,
            result = null,
            stage = "音频已选择，点击生成文案",
        )
    }

    fun generateCopywriting(auxiliary: AuxiliaryContext) {
        val input = _uiState.value.input ?: return
        val enrichedInput = input.copy(auxiliary = auxiliary)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                metadata = enrichedInput.metadata,
                stage = "正在转写音频并生成文案...",
                result = null,
            )
            val result = gateway.generate(enrichedInput)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                metadata = result.metadata,
                result = result,
                stage = "文案已生成",
            )
        }
    }
}

data class CopywritingUiState(
    val isLoading: Boolean = false,
    val stage: String = "等待上传音频",
    val input: AudioInput? = null,
    val metadata: MediaMetadata? = null,
    val result: AudioCopywritingResult? = null,
    val asrReady: Boolean = true,
)

private enum class ViewMode {
    User,
    Developer,
}

private data class ProcessStep(
    val id: String,
    val title: String,
    val detail: String,
    val streamText: String,
    val detailOutput: String,
    val done: Boolean,
    val active: Boolean,
)

@Composable
private fun CopywritingScreen(viewModel: CopywritingViewModel) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var mode by remember { mutableStateOf(ViewMode.User) }
    var statusExpanded by remember { mutableStateOf(false) }
    var selectedStepId by remember { mutableStateOf<String?>(null) }
    var showAll by remember { mutableStateOf(false) }
    var songName by remember { mutableStateOf("") }
    var artistName by remember { mutableStateOf("") }
    var favoriteLyrics by remember { mutableStateOf("") }
    var lyricImages by remember { mutableStateOf<List<ImageAttachment>>(emptyList()) }
    var screenshots by remember { mutableStateOf<List<ImageAttachment>>(emptyList()) }
    var screenshotNote by remember { mutableStateOf("") }
    var showAssistInputs by remember { mutableStateOf(false) }
    val steps = processSteps(state)
    val currentStep = steps.firstOrNull { !it.done } ?: steps.last()
    val selectedStep = selectedStepId?.let { id -> steps.firstOrNull { it.id == id } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.selectAudio(context.audioInputFromUri(uri))
        }
    }
    val lyricImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lyricImages = lyricImages + context.imageAttachmentFromUri(uri)
    }
    val screenshotLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) screenshots = screenshots + context.imageAttachmentFromUri(uri)
    }
    val auxiliary = AuxiliaryContext(
        songName = songName,
        artistName = artistName,
        favoriteLyrics = favoriteLyrics,
        lyricImages = lyricImages,
        screenshots = screenshots,
        screenshotNote = screenshotNote,
    )

    Surface(Modifier.fillMaxSize(), color = Color(0xFFF8FAFC)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFFF8FAFC), Color(0xFFFFFBF5), Color.White)))
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TopBar(mode = mode, onModeChange = { mode = it })
            UploadCard(
                state = state,
                onPick = { launcher.launch(arrayOf("audio/*", "video/*")) },
                onGenerate = { viewModel.generateCopywriting(auxiliary) },
                showAssistInputs = showAssistInputs,
                onToggleAssistInputs = { showAssistInputs = !showAssistInputs },
            )
            if (showAssistInputs) {
                AssistInputCard(
                    songName = songName,
                    artistName = artistName,
                    favoriteLyrics = favoriteLyrics,
                    lyricImages = lyricImages,
                    screenshots = screenshots,
                    screenshotNote = screenshotNote,
                    onSongNameChange = { songName = it },
                    onArtistNameChange = { artistName = it },
                    onFavoriteLyricsChange = { favoriteLyrics = it },
                    onPickLyricImage = { lyricImageLauncher.launch(arrayOf("image/*")) },
                    onPickScreenshot = { screenshotLauncher.launch(arrayOf("image/*")) },
                    onRemoveLyricImage = { image -> lyricImages = lyricImages - image },
                    onRemoveScreenshot = { image -> screenshots = screenshots - image },
                    onScreenshotNoteChange = { screenshotNote = it },
                )
            }
            if (mode == ViewMode.Developer) {
                FloatingStatusCard(
                    state = state,
                    steps = steps,
                    expanded = statusExpanded,
                    onToggle = { statusExpanded = !statusExpanded },
                    selectedStepId = selectedStepId,
                    onSelectStep = {
                        selectedStepId = it.id
                        showAll = false
                        statusExpanded = false
                    },
                    onViewAll = {
                        showAll = true
                        selectedStepId = null
                        statusExpanded = false
                    },
                )
            }
            when {
                mode == ViewMode.User -> UserResultArea(state)
                showAll -> {
                    when (mode) {
                        ViewMode.User -> UserResultArea(state)
                        ViewMode.Developer -> DeveloperResultArea(state)
                    }
                }
                selectedStep != null -> StepDetailCard(step = selectedStep)
                else -> CurrentProgressTab(steps = steps, current = currentStep)
            }
        }
    }
}

@Composable
private fun TopBar(mode: ViewMode, onModeChange: (ViewMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "音频文案生成器",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF111827),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeButton("用户模式", mode == ViewMode.User) { onModeChange(ViewMode.User) }
            ModeButton("开发者模式", mode == ViewMode.Developer) { onModeChange(ViewMode.Developer) }
        }
    }
}

@Composable
private fun ModeButton(text: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) Color(0xFF111827) else Color.White
    val content = if (selected) Color.White else Color(0xFF111827)
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        colors = CardDefaults.cardColors(containerColor = background),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 0.dp else 2.dp),
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            text = text,
            color = content,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun UploadCard(
    state: CopywritingUiState,
    onPick: () -> Unit,
    onGenerate: () -> Unit,
    showAssistInputs: Boolean,
    onToggleAssistInputs: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("上传音频，生成可发布文案", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text(
                text = state.metadata?.fileName ?: "支持 mp3 / wav / m4a / mp4",
                color = Color(0xFF6B7280),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onPick, enabled = !state.isLoading) {
                    Text(if (state.metadata == null) "上传音频" else "更换音频")
                }
                Button(onClick = onGenerate, enabled = state.input != null && !state.isLoading) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        Text("  生成中")
                    } else {
                        Text("生成文案")
                    }
                }
                IconButton(
                    onClick = onToggleAssistInputs,
                    enabled = !state.isLoading
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "展开/收起辅助输入"
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistInputCard(
    songName: String,
    artistName: String,
    favoriteLyrics: String,
    lyricImages: List<ImageAttachment>,
    screenshots: List<ImageAttachment>,
    screenshotNote: String,
    onSongNameChange: (String) -> Unit,
    onArtistNameChange: (String) -> Unit,
    onFavoriteLyricsChange: (String) -> Unit,
    onPickLyricImage: () -> Unit,
    onPickScreenshot: () -> Unit,
    onRemoveLyricImage: (ImageAttachment) -> Unit,
    onRemoveScreenshot: (ImageAttachment) -> Unit,
    onScreenshotNoteChange: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("可选辅助信息", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = songName,
                    onValueChange = onSongNameChange,
                    label = { Text("歌曲名") },
                    singleLine = true,
                )
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = artistName,
                    onValueChange = onArtistNameChange,
                    label = { Text("歌手") },
                    singleLine = true,
                )
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = favoriteLyrics,
                onValueChange = onFavoriteLyricsChange,
                label = { Text("喜欢的歌词/想突出的句子") },
                minLines = 2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onPickLyricImage) {
                    Text("添加歌词图片")
                }
                OutlinedButton(onClick = onPickScreenshot) {
                    Text("添加参考截图")
                }
            }
            ImageList("歌词图片", lyricImages, onRemoveLyricImage)
            ImageList("参考截图", screenshots, onRemoveScreenshot)
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = screenshotNote,
                onValueChange = onScreenshotNoteChange,
                label = { Text("截图说明，例如评论区情绪、封面风格") },
                minLines = 1,
            )
            Text(
                "上传歌词图片或播放器截图后，会通过腾讯 TokenHub YT-VITA 做多模态识别，优先修正 ASR 歌词错字。",
                color = Color(0xFF6B7280),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ImageList(title: String, images: List<ImageAttachment>, onRemove: (ImageAttachment) -> Unit) {
    if (images.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF374151))
        images.forEachIndexed { index, image ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${index + 1}. ${image.fileName}", color = Color(0xFF6B7280), modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { onRemove(image) }) {
                    Text("移除")
                }
            }
        }
    }
}

@Composable
private fun FloatingStatusCard(
    state: CopywritingUiState,
    steps: List<ProcessStep>,
    expanded: Boolean,
    onToggle: () -> Unit,
    selectedStepId: String?,
    onSelectStep: (ProcessStep) -> Unit,
    onViewAll: () -> Unit,
) {
    val current = steps.firstOrNull { !it.done } ?: steps.last()
    val statusText = when {
        state.isLoading -> "处理中"
        state.result?.llmSuccess == true -> "已完成"
        state.result != null -> "已完成"
        state.input != null -> "待生成"
        else -> "待上传"
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xF2FFFFFF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("处理状态", fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                    Text(
                        if (expanded) "点击收起进度" else "点击展开处理链路",
                        color = Color(0xFF6B7280),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        progress = { steps.count { it.done } / steps.size.toFloat() },
                        modifier = Modifier.size(34.dp),
                        strokeWidth = 4.dp,
                        color = Color(0xFF111827),
                        trackColor = Color(0xFFE5E7EB),
                    )
                    StatusPill(statusText)
                }
            }
            if (expanded) {
                steps.forEach { step ->
                    StatusStep(
                        step = step,
                        selected = selectedStepId == step.id,
                        onView = { onSelectStep(step) },
                    )
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onViewAll,
                ) {
                    Text("查看全部")
                }
            } else {
                Text("当前：${current.title} · ${current.detail}", color = Color(0xFF2563EB), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StatusStep(step: ProcessStep, selected: Boolean, onView: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(
                    if (step.done) Color(0xFF10B981) else if (step.active) Color(0xFF2563EB) else Color(0xFFE5E7EB),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (step.done) "✓" else "",
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(step.title, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
            Text(step.detail, color = Color(0xFF6B7280), style = MaterialTheme.typography.bodySmall)
        }
        if (step.done || step.active) {
            OutlinedButton(onClick = onView) {
                Text(if (selected) "查看中" else "查看")
            }
        }
    }
}

@Composable
private fun CurrentProgressTab(steps: List<ProcessStep>, current: ProcessStep) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                progress = { steps.count { it.done } / steps.size.toFloat() },
                modifier = Modifier.size(48.dp),
                strokeWidth = 5.dp,
                color = Color(0xFF2563EB),
                trackColor = Color(0xFFE5E7EB),
            )
            Column {
                Text("当前进度", fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                TypewriterText("${current.title}：${current.streamText}")
            }
        }
    }
}

@Composable
private fun StepDetailCard(step: ProcessStep) {
    SectionCard(
        title = step.title,
        content = step.detailOutput,
        monospace = step.id == "transcript",
    )
}

@Composable
private fun TypewriterText(text: String) {
    var visible by remember(text) { mutableStateOf("") }
    LaunchedEffect(text) {
        visible = ""
        text.forEachIndexed { index, _ ->
            visible = text.take(index + 1)
            kotlinx.coroutines.delay(12)
        }
    }
    Text(visible, color = Color(0xFF6B7280), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun StatusPill(text: String) {
    Box(
        modifier = Modifier
            .background(Color(0xFF111827), RoundedCornerShape(999.dp))
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
    }
}

private fun processSteps(state: CopywritingUiState): List<ProcessStep> {
    val result = state.result
    val markdown = result?.generatedMarkdown.orEmpty()
    val transcriptText = result?.transcript?.text.orEmpty()
    val visionLyrics = result?.visionContext?.lyricImages.orEmpty()
    val visionScreenshots = result?.visionContext?.screenshots.orEmpty()

    return listOf(
        ProcessStep(
            id = "file",
            title = "系统步骤 · 音频读取",
            detail = state.metadata?.fileName ?: "等待选择音频",
            streamText = state.metadata?.let { "已读取文件名、大小和音频类型，等待生成文案。" } ?: "等待用户上传音频文件。",
            detailOutput = state.metadata?.let {
                systemStepDoc(
                    role = "接收用户上传的音频文件，并把平台文件信息标准化成后续流程能消费的输入。",
                    tools = "Android ContentResolver、OpenableColumns、MIME 类型读取、本地 bytes 读取。",
                    workflow = "用户选择音频 -> 读取元信息 -> 判断是否小于 5MB -> 构造 AudioInput。",
                    output = listOf(
                    "文件名：${it.fileName}",
                    "大小：${formatBytes(it.fileSizeBytes)}",
                    "类型：${it.mimeType ?: it.mediaType.name}",
                    "时长：${it.displayDuration}",
                    ).joinToString("\n"),
                )
            } ?: "还没有选择音频。",
            done = state.metadata != null,
            active = state.input == null,
        ),
        ProcessStep(
            id = "transcript",
            title = "工具步骤 · 腾讯云 ASR 转写",
            detail = result?.transcript?.message ?: if (state.isLoading) "腾讯云 ASR 正在转写音频" else "等待生成后开始转写",
            streamText = when {
                result?.transcript?.success == true -> "转写完成，已获得可用于文案生成的文本。"
                state.isLoading -> "正在把音频发送到腾讯云 ASR，并等待识别任务返回。"
                else -> "等待点击生成文案。"
            },
            detailOutput = systemStepDoc(
                role = "把音频中的歌词、口播或语音内容转成文本，是后续 Agent 的事实来源。",
                tools = "腾讯云 CreateRecTask、DescribeTaskStatus、TC3-HMAC-SHA256 签名。",
                workflow = "读取音频 bytes -> 提交 ASR 任务 -> 轮询结果 -> 输出 transcript。",
                output = transcriptText.ifBlank { result?.transcript?.message ?: "暂无转写内容。" },
            ),
            done = result?.transcript?.success == true,
            active = state.isLoading && result == null,
        ),
        ProcessStep(
            id = "vision",
            title = "Vision Agent · YT-VITA 图片识别",
            detail = when {
                (visionLyrics + visionScreenshots).any { it.success } -> "已完成歌词/截图多模态识别"
                state.isLoading -> "正在调用 YT-VITA 识别图片"
                else -> "未上传图片或等待生成"
            },
            streamText = when {
                (visionLyrics + visionScreenshots).any { it.success } ->
                    "已提取图片中的歌词/歌名/氛围，用于修正 ASR 错字。"
                state.isLoading -> "并行识别歌词图片和截图内容。"
                else -> "可选：上传歌词图片可显著提升歌曲文案准确度。"
            },
            detailOutput = agentDoc(
                role = "理解歌词图片或截图，提取歌词、歌名、歌手和视觉氛围，作为比 ASR 更可信的文本来源。",
                brain = "腾讯 TokenHub YT-VITA（youtu-vita）。",
                tools = "图片 bytes、Base64 data URL、OpenAI 兼容多模态 Chat Completions。",
                memory = "保存 OCR/视觉识别结果、识别状态和提取到的歌名歌手。",
                workflow = "读取图片 -> YT-VITA 多模态识别 -> 解析 JSON -> 写入标准文本优先级链路。",
                output = (
                    visionLyrics.mapIndexed { index, item -> "【歌词图片 ${index + 1}】${item.message}\n${item.lyrics.ifBlank { item.extractedText }}" } +
                        visionScreenshots.mapIndexed { index, item -> "【参考截图 ${index + 1}】${item.message}\n${item.lyrics.ifBlank { item.extractedText }}"
                        }
                    ).joinToString("\n\n").ifBlank { "本次未上传图片或识别未返回内容。" },
            ),
            done = result != null,
            active = state.isLoading && result == null,
        ),
        ProcessStep(
            id = "analysis",
            title = "Insight Agent · 内容解析",
            detail = if (result != null) "已解析主题、亮点和听感建议" else "等待标准文本就绪",
            streamText = if (result != null) "正在从标准文本中抽取主题、情绪、亮点和听感表达。" else "等待 ASR / YT-VITA 结果。",
            detailOutput = agentDoc(
                role = "负责把标准文本变成用户能理解的内容解析，提炼主题、情绪、亮点和听感建议。",
                brain = "共用腾讯 MaaS DeepSeek-V4-Flash。",
                tools = "CopywritingPromptBuilder、标准文本、YT-VITA 识别结果、媒体 Metadata。",
                memory = "保存本次标准文本来源、ASR 粗转写、图片上下文和模型生成的解析段落。",
                workflow = "读取 standardText -> 融合用户偏好 -> 提取主题/情绪 -> 总结词句亮点 -> 给出听感建议。",
                output = listOf(
                    "## 音频解析",
                    markdown.section("音频解析"),
                    "",
                    "## 词/内容亮点",
                    markdown.section("词/内容亮点"),
                    "",
                    "## 曲风/听感建议",
                    markdown.section("曲风/听感建议"),
                ).joinToString("\n").trim(),
            ),
            done = result != null,
            active = state.isLoading,
        ),
        ProcessStep(
            id = "copywriting",
            title = "Copywriting Agent · 文案生成",
            detail = result?.generationMessage ?: "等待 DeepSeek 生成",
            streamText = when {
                result?.llmSuccess == true -> "DeepSeek 已完成朋友圈、小红书和短视频标题生成。"
                result != null -> "DeepSeek 未完成，已基于转写文本生成本地文案。"
                state.isLoading -> "正在将转写文本交给 DeepSeek 生成文案。"
                else -> "等待内容解析完成。"
            },
            detailOutput = agentDoc(
                role = "负责把解析结果改写成可以直接发布的中文社交文案。",
                brain = "共用腾讯 MaaS DeepSeek-V4-Flash。",
                tools = "DeepSeek Chat Completions、平台文案模板、转写文本。",
                memory = "保存本次模型输出、生成状态和失败原因；失败时启用本地辅助生成。",
                workflow = "组装 Prompt -> 调用 DeepSeek -> 校验输出 -> 失败则基于转写内容生成本地文案。",
                output = markdown.ifBlank { "暂无生成结果。" },
            ),
            done = result != null,
            active = state.isLoading,
        ),
        ProcessStep(
            id = "platform",
            title = "Publish Agent · 平台整理",
            detail = if (result != null) "已整理朋友圈、小红书、短视频标题" else "等待生成结果",
            streamText = if (result != null) "平台文案已拆分完成，可在用户模式直接复制使用。" else "等待文案生成 Agent 输出。",
            detailOutput = agentDoc(
                role = "负责把大模型输出拆成最终用户可直接查看和复制的平台结果。",
                brain = "不再次调用 LLM，使用结构化 Markdown 解析。",
                tools = "Markdown section parser、用户模式结果卡片。",
                memory = "保存朋友圈、小红书、短视频标题三个平台分区。",
                workflow = "解析生成 Markdown -> 拆分平台段落 -> 投递到用户模式卡片。",
                output = listOf(
                    "## 朋友圈文案",
                    markdown.section("朋友圈文案"),
                    "",
                    "## 小红书文案",
                    markdown.section("小红书文案"),
                    "",
                    "## 短视频标题",
                    markdown.section("短视频标题"),
                ).joinToString("\n").trim(),
            ),
            done = result != null,
            active = false,
        ),
    )
}

private fun agentDoc(
    role: String,
    brain: String,
    tools: String,
    memory: String,
    workflow: String,
    output: String,
): String {
    return """
        作用：$role

        大脑（LLM）：$brain

        手脚（Tools / MCP）：$tools

        记忆（Memory）：$memory

        规划（Planning / Workflow）：$workflow

        输出：
        $output
    """.trimIndent()
}

private fun systemStepDoc(
    role: String,
    tools: String,
    workflow: String,
    output: String,
): String {
    return """
        类型：系统/工具步骤，不算 Agent

        作用：$role

        手脚（Tools）：$tools

        流程（Workflow）：$workflow

        输出：
        $output
    """.trimIndent()
}

@Composable
private fun UserResultArea(state: CopywritingUiState) {
    val result = state.result ?: return EmptyHint()
    val markdown = result.generatedMarkdown
    SectionCard("朋友圈文案", markdown.section("朋友圈文案"), copyable = true)
    SectionCard("小红书文案", markdown.section("小红书文案"), copyable = true)
    SectionCard("短视频标题", markdown.section("短视频标题"), copyable = true)
}

@Composable
private fun DeveloperResultArea(state: CopywritingUiState) {
    val result = state.result
    if (result == null) {
        EmptyHint()
        state.metadata?.let { FileInfoCard(it) }
        return
    }
    val markdown = result.generatedMarkdown
    val resolvedStandardText = result.standardText.text
    SectionCard("音频解析", markdown.section("音频解析"))
    SectionCard("词/内容亮点", markdown.section("词/内容亮点"))
    SectionCard("曲风/听感建议", markdown.section("曲风/听感建议"))
    SectionCard("标准文本预览", resolvedStandardText.take(1200).ifBlank { "暂无标准文本" }, monospace = true)
    SectionCard(
        "文本来源",
        result.standardText.let {
            "来源：${it.sourceLabel}\n歌名：${it.songName.ifBlank { "无" }}\n歌手：${it.artistName.ifBlank { "无" }}"
        },
    )
    SectionCard("ASR 转写预览", result.transcript.text.take(1200).ifBlank { "暂无 ASR 转写" }, monospace = true)
    SectionCard(
        "运行状态",
        "转写状态：${result.transcript.message}\n\n标准文本来源：${result.standardText.sourceLabel}\n\n生成状态：${result.generationMessage}",
    )
    FileInfoCard(result.metadata)
}

@Composable
private fun EmptyHint() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("还没有生成结果", fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
            Text("上传音频后点击“生成文案”，这里会展示可直接发布的内容。", color = Color(0xFF92400E))
        }
    }
}

@Composable
private fun SectionCard(title: String, content: String, monospace: Boolean = false, copyable: Boolean = false) {
    val clipboard = LocalClipboardManager.current
    val safeContent = content.ifBlank { "本次结果中没有提取到该部分。" }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                if (copyable) {
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(safeContent)) }) {
                        Text("复制")
                    }
                }
            }
            Text(
                text = safeContent,
                color = Color(0xFF1F2937),
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            )
        }
    }
}

@Composable
private fun FileInfoCard(metadata: MediaMetadata) {
    SectionCard(
        title = "音频文件信息",
        content = listOf(
            "文件名：${metadata.fileName}",
            "大小：${formatBytes(metadata.fileSizeBytes)}",
            "类型：${metadata.mimeType ?: metadata.mediaType.name}",
            "时长：${metadata.displayDuration}",
            "来源：${metadata.source}",
        ).joinToString("\n"),
    )
}

private fun String.section(title: String): String {
    val pattern = Regex("(?s)##\\s*$title\\s*(.*?)(?=\\n##\\s|$)")
    return pattern.find(this)?.groupValues?.getOrNull(1)?.trim().orEmpty()
}

private fun Context.audioInputFromUri(uri: Uri): AudioInput {
    val info = openableInfo(uri)
    val displayName = info.first
    val size = info.second
    val bytes = if (size in 1..MAX_LOCAL_AUDIO_BYTES_TO_READ) {
        contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } else {
        null
    }
    val metadata = MediaMetadataFactory.fromFileInput(
        fileName = displayName,
        fileSizeBytes = size,
        mimeType = contentResolver.getType(uri),
        inputPathOrUri = uri.toString(),
        userGoal = "解析完整音频并生成社交平台文案",
    )
    return AudioInput(metadata = metadata, bytes = bytes)
}

private fun Context.imageAttachmentFromUri(uri: Uri): ImageAttachment {
    val info = openableInfo(uri)
    val mimeType = contentResolver.getType(uri)
    // 原图允许更大，发送前会在 shared 层降采样压缩。
    val bytes = if (info.second in 1..MAX_IMAGE_BYTES_TO_READ) {
        contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } else {
        null
    }
    return ImageAttachment(
        fileName = info.first,
        bytes = bytes,
        mimeType = mimeType,
    )
}

private fun Context.displayNameFromUri(uri: Uri): String {
    return openableInfo(uri).first
}

private fun Context.openableInfo(uri: Uri): Pair<String, Long> {
    var displayName = uri.lastPathSegment ?: "selected_file"
    var size = 0L
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            if (nameIndex >= 0) displayName = cursor.getString(nameIndex) ?: displayName
            if (sizeIndex >= 0) size = cursor.getLong(sizeIndex)
        }
    }
    return displayName to size
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "unknown"
    val kb = bytes / 1024.0
    return if (kb < 1024) "${kb.toInt()} KB" else String.format("%.1f MB", kb / 1024.0)
}

private const val MAX_LOCAL_AUDIO_BYTES_TO_READ: Long = 80L * 1024L * 1024L
private const val MAX_IMAGE_BYTES_TO_READ: Long = 20L * 1024L * 1024L

private fun Application.secretString(resId: Int, fallback: String): String {
    return runCatching { getString(resId) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: fallback
}

private object LocalTencentConfig {
    const val SECRET_ID = ""
    const val SECRET_KEY = ""
    const val REGION = "ap-guangzhou"
    const val ENGINE = "16k_zh"
}
