package com.github.bboygf.over_code.ui.home

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bboygf.over_code.llm.LLMService
import com.github.bboygf.over_code.services.ChatDatabaseService
import com.github.bboygf.over_code.services.HomeViewModelService
import com.github.bboygf.over_code.ui.component.BottomInputArea
import com.github.bboygf.over_code.ui.component.MessageBubble
import com.github.bboygf.over_code.ui.component.WelcomeScreen
import com.github.bboygf.over_code.ui.memory.MemoryPanel
import com.github.bboygf.over_code.ui.model_config.ModelConfigurable
import com.github.bboygf.over_code.utils.CodeEditNavigator
import com.github.bboygf.over_code.vo.EditInfo
import com.github.bboygf.over_code.vo.SessionInfo
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.ide.BrowserUtil

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.JComponent

/**
 * Over Code 聊天界面工具窗口
 */
class OverCoderHomeWindowFactory : ToolWindowFactory {

    init {
        System.setProperty("skiko.renderApi", "SOFTWARE")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val composePanel = createComposePanel(project)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(composePanel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    private fun createComposePanel(project: Project): JComponent {
        return ComposePanel().apply {
            setContent {
                OverCodeChatUI(project)
            }
        }
    }
}

/**
 * Over Code 聊天主界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun OverCodeChatUI(project: Project? = null) {
    // 数据库服务
    val dbService = remember(project) {
        project?.let { ChatDatabaseService.getInstance(it) }
    }

    // LLM 服务
    val llmService = remember(project) {
        project?.let { LLMService.getInstance(it) }
    }

    // ViewModel
    val viewModel = remember(project, dbService, llmService) {
        HomeViewModel(project, dbService, llmService)
    }

    // 注册 ViewModel 到 Project Service，以便 Action 可以访问
    LaunchedEffect(project, viewModel) {
        project?.getService(HomeViewModelService::class.java)?.let { holder ->
            holder.viewModel = viewModel
        }
    }

    // UI状态
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 跟踪用户是否滚动到非底部位置
    var isUserAtBottom by remember { mutableStateOf(true) }

    // 跟踪用户是否手动滚动过（一旦手动滚动就锁定，不再自动滚动）
    var hasUserScrolled by remember { mutableStateOf(false) }

    // 注册输入框文本变更回调
    LaunchedEffect(viewModel) {
        viewModel.onInputTextChange = { text ->
            inputText = TextFieldValue(text)
        }
        viewModel.getInputText = {
            inputText.text
        }
    }

    // 初始化加载
    LaunchedEffect(project) {
        viewModel.loadModelConfigs()
    }

    // 加载历史消息
    LaunchedEffect(viewModel.currentSessionId) {
        if (viewModel.currentSessionId.isNotBlank()) {
            viewModel.loadMessages(viewModel.currentSessionId)
        }
        if (viewModel.chatMessageVos.isNotEmpty()) {
            try {
                listState.scrollToItem(viewModel.chatMessageVos.size - 1)
            } catch (e: Exception) {
                println("滚动到最后异常" + e.message)
            }
        }
    }

    LaunchedEffect(viewModel.scrollEvents) {
        viewModel.scrollEvents.collect { index ->
            if (index >= 0) {
                listState.animateScrollToItem(index)
            }
        }
    }

    // 监听聊天结束（isLoading 从 true 变为 false），自动滚动到底部
    LaunchedEffect(viewModel.isLoading) {
        if (!viewModel.isLoading && viewModel.chatMessageVos.isNotEmpty()) {
            // 聊天结束，滚动到底部
            try {
                listState.scrollToItem(
                    index = viewModel.chatMessageVos.size - 1,
                    scrollOffset = Int.MAX_VALUE
                )
            } catch (e: Exception) {
                println("聊天结束后滚动异常" + e.message)
            }
        }
    }

    // 监听滚动状态，判断用户是否在底部（最后一个item的最后位置）
    LaunchedEffect(listState) {
        snapshotFlow {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val lastVisibleItem = visibleItems.lastOrNull()
            val totalItems = listState.layoutInfo.totalItemsCount

            // 判断条件：
            // 1. 最后可见item必须是最后一个item
            // 2. 并且滚动偏移量要足够接近底部（距离底部 < 50dp）
            val isLastItem = lastVisibleItem?.index == totalItems - 1
            val scrollOffset = lastVisibleItem?.offset ?: 0
            val itemSize = lastVisibleItem?.size ?: 0
            val viewportEndOffset = listState.layoutInfo.viewportEndOffset

            // 计算最后一个item底部距离视口底部的距离
            val distanceToBottom = viewportEndOffset - (scrollOffset + itemSize)

            Triple(isLastItem, distanceToBottom, totalItems)
        }.collect { (isAtBottom, distanceToBottom, totalItems) ->
            // 如果用户在底部，释放锁定
            if (isAtBottom && distanceToBottom <= 50) {
                isUserAtBottom = true
                // 用户手动滚动到底部，重置锁定状态
                hasUserScrolled = false
            } else {
                // 用户滚动到非底部位置，设置锁定状态
                isUserAtBottom = false
                hasUserScrolled = true
            }
        }
    }


    // 深色主题配色
    val backgroundColor = Color(0xFF2B2B2B)
    val surfaceColor = Color(0xFF3C3F41)
    val primaryColor = Color(0xFF4A9D5F)
    val textPrimaryColor = Color(0xFFBBBBBB)
    val textSecondaryColor = Color(0xFF808080)

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = backgroundColor,
            surface = surfaceColor,
            primary = primaryColor,
            onBackground = textPrimaryColor,
            onSurface = textPrimaryColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
        ) {
            // 顶部工具栏
            TopAppBar(
                title = {
                    Text(
                        text = "会话",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimaryColor
                    )
                },
                actions = {
                    IconButton(onClick = {
                        if (viewModel.showHistory) {
                            viewModel.showHistory = false
                        } else {
                            viewModel.loadSessions()
                            viewModel.showHistory = true
                            viewModel.showMemoryPanel = false // 关闭记忆库
                        }
                    }) {
                        Icon(
                            imageVector = if (viewModel.showHistory) Icons.Default.Edit else Icons.AutoMirrored.Filled.List,
                            contentDescription = "历史记录",
                            tint = if (viewModel.showHistory) primaryColor else textSecondaryColor
                        )
                    }
                    IconButton(onClick = {
                        viewModel.createNewSession()
                        viewModel.showHistory = false // 关闭历史
                        viewModel.showMemoryPanel = false // 关闭记忆库
                    }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "新建",
                            tint = textSecondaryColor
                        )
                    }
                    // 记忆库按钮
                    IconButton(onClick = {
                        if (viewModel.showMemoryPanel) {
                            viewModel.showMemoryPanel = false
                        } else {
                            viewModel.loadMemories() // 加载记忆数据
                            viewModel.showMemoryPanel = true
                            viewModel.showHistory = false // 关闭历史
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "记忆库",
                            tint = if (viewModel.showMemoryPanel) primaryColor else textSecondaryColor
                        )
                    }
                    IconButton(onClick = {
                        viewModel.clearCurrentSession()
                        viewModel.showHistory = false // 关闭历史
                        viewModel.showMemoryPanel = false // 关闭记忆库
                    }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "清空",
                            tint = textSecondaryColor
                        )
                    }
                    // 关于按钮
                    IconButton(onClick = {
                        showAboutDialog = true
                        viewModel.showHistory = false // 关闭历史
                        viewModel.showMemoryPanel = false // 关闭记忆库
                    }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "关于",
                            tint = textSecondaryColor
                        )
                    }
                    IconButton(onClick = {
                        project?.let {
                            ShowSettingsUtil.getInstance().showSettingsDialog(it, ModelConfigurable::class.java)
                        }
                        viewModel.showHistory = false // 关闭历史
                        viewModel.showMemoryPanel = false // 关闭记忆库
                    }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置",
                            tint = textSecondaryColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = surfaceColor,
                    titleContentColor = textPrimaryColor
                )
            )

            // 中央区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // 判断显示哪个面板：历史 > 记忆 > 聊天
                when {
                    viewModel.showHistory -> {
                        HistoryScreen(
                            viewModel = viewModel,
                            backgroundColor = backgroundColor,
                            surfaceColor = surfaceColor,
                            textPrimaryColor = textPrimaryColor,
                            textSecondaryColor = textSecondaryColor,
                            primaryColor = primaryColor
                        )
                    }

                    viewModel.showMemoryPanel -> {
                        MemoryPanel(
                            memories = viewModel.memories,
                            onAddMemory = { viewModel.addMemory(it) },
                            onUpdateMemory = { id, memory ->
                                viewModel.updateMemory(
                                    id,
                                    memory.summary,
                                    memory.content
                                )
                            },
                            onDeleteMemory = { viewModel.deleteMemory(it) },
                            onGenerateMemory = { viewModel.generateMemoryFromSession() },
                            isGenerating = viewModel.isGeneratingMemory,
                            backgroundColor = backgroundColor,
                            surfaceColor = surfaceColor,
                            textPrimaryColor = textPrimaryColor,
                            textSecondaryColor = textSecondaryColor,
                            primaryColor = primaryColor
                        )
                    }

                    viewModel.chatMessageVos.isEmpty() -> {
                        // 空状态 - 显示欢迎界面
                        WelcomeScreen()
                    }

                    else -> {
                        // 消息列表
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(1.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                            userScrollEnabled = true
                        ) {
                            items(viewModel.chatMessageVos) { message ->
                                MessageBubble(
                                    message = message,
                                    onCopyCode = { viewModel.copyToClipboard(it) },
                                    onInsertCode = { viewModel.insertCodeAtCursor(it) },
                                    onNavigateToEdit = { editInfo ->
                                        project?.let { p ->
                                            CodeEditNavigator.navigateAndHighlight(
                                                project = p,
                                                filePath = editInfo.filePath,
                                                startLine = editInfo.startLine,
                                                endLine = editInfo.endLine
                                            )
                                        }
                                    },
                                    onShowDiff = { editInfo ->
                                        project?.let { p ->
                                            CodeEditNavigator.navigateAndShowDiff(
                                                project = p,
                                                filePath = editInfo.filePath,
                                                oldContent = editInfo.oldContent,
                                                newContent = editInfo.newContent,
                                                startLine = editInfo.startLine,
                                                endLine = editInfo.endLine
                                            )
                                        }
                                    }
                                )
                            }
                        }
                        // 2. 添加滚动条
                        VerticalScrollbar(
                            modifier = Modifier
                                .align(Alignment.CenterEnd) // 居右对齐
                                .fillMaxHeight()            // 占满高度
                                .width(15.dp)                 // 设置滚动条宽度
                                .padding(vertical = 10.dp),  // 设置滚动条高度
                            adapter = rememberScrollbarAdapter(scrollState = listState) // 关键：适配 LazyListState
                        )
                    }
                }
            }

            // 底部输入区域
            BottomInputArea(
                inputText = inputText,
                onInputChange = { inputText = it },
                isLoading = viewModel.isLoading,
                onSend = {
                    if (inputText.text.isNotBlank() || viewModel.selectedImageBase64List.isEmpty() != null) {

                        val userInput = inputText
                        inputText = TextFieldValue("")
                        viewModel.sendMessage(userInput.text) {
                            // 只有当用户没有手动滚动过且在底部时才自动滚动
                            coroutineScope.launch {
                                if (viewModel.chatMessageVos.isNotEmpty() && !hasUserScrolled && isUserAtBottom) {
                                    try {
                                        // 使用 scrollToItem 并设置一个大的 scrollOffset 来滚动到 item 底部
                                        // 这样即使消息很长，也能看到最新的内容
                                        listState.scrollToItem(
                                            index = viewModel.chatMessageVos.size - 1,
                                            scrollOffset = Int.MAX_VALUE
                                        )
                                    } catch (e: Exception) {
                                        println("获取最后一行数据异常！" + e.message)
                                    }
                                }
                            }
                        }
                    }
                },
                activeModelName = viewModel.activeModel?.name ?: "选择模型",
                chatMode = viewModel.chatMode,
                onModeChange = { viewModel.chatMode = it },
                modelConfigs = viewModel.modelConfigs,
                onModelSelect = { viewModel.setActiveModel(it) },
                backgroundColor = surfaceColor,
                textColor = textPrimaryColor,
                isChecked = viewModel.loadHistory,
                onLoadHistoryChange = { viewModel.onLoadHistoryChange(it) },
                selectedImageBase64List = viewModel.selectedImageBase64List,
                onDeleteImage = { base64 -> viewModel.removeImage(base64) },
                onPasteImage = { viewModel.checkClipboardForImage() },
                onSelectImage = {
                    val descriptor = FileChooserDescriptor(true, false, false, false, false, true)
                        .withTitle("选择图片")
                        .withDescription("支持 png, jpg, jpeg, bmp 格式")
                        .withExtensionFilter("图片", "png", "jpg", "jpeg", "bmp")
                    FileChooser.chooseFiles(descriptor, project, null).forEach { file ->
                        val bytes = file.contentsToByteArray()
                        val base64 = Base64.getEncoder().encodeToString(bytes)
                        viewModel.addImage(base64)
                    }
                },
                addProjectIndex = { viewModel.addProjectIndex() },
                isCancelling = viewModel.isCancelling,
                onCancel = { viewModel.cancelCurrentRequest() }
            )

            // 错误提示对话框
            if (viewModel.showErrorDialog) {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissError() },
                    title = {
                        Text(
                            text = "请求失败",
                            color = textPrimaryColor
                        )
                    },
                    text = {
                        Text(
                            text = viewModel.errorMessage ?: "未知错误",
                            color = textSecondaryColor
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.dismissError() }) {
                            Text("关闭")
                        }
                    },
                    containerColor = surfaceColor,
                    titleContentColor = textPrimaryColor
                )
            }

            // 关于对话框
            if (showAboutDialog) {
                AboutDialog(
                    onDismissRequest = { showAboutDialog = false },
                    backgroundColor = backgroundColor,
                    surfaceColor = surfaceColor,
                    primaryColor = primaryColor,
                    textPrimaryColor = textPrimaryColor,
                    textSecondaryColor = textSecondaryColor
                )
            }

        }
    }
}

/**
 * 历史会话列表界面
 */
@Composable
fun HistoryScreen(
    viewModel: HomeViewModel,
    backgroundColor: Color,
    surfaceColor: Color,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    primaryColor: Color
) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(8.dp)
    ) {
        Text(
            text = "历史会话",
            fontSize = 16.sp,
            color = textPrimaryColor,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (viewModel.sessions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "暂无历史记录", color = textSecondaryColor)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(viewModel.sessions) { session ->
                    HistoryItem(
                        session = session,
                        isSelected = session.sessionId == viewModel.currentSessionId,
                        dateFormat = dateFormat,
                        surfaceColor = surfaceColor,
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor,
                        primaryColor = primaryColor,
                        onClick = {
                            viewModel.loadMessages(session.sessionId)
                            viewModel.showHistory = false
                        },
                        onDelete = {
                            viewModel.deleteSession(session.sessionId)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryItem(
    session: SessionInfo,
    isSelected: Boolean,
    dateFormat: SimpleDateFormat,
    surfaceColor: Color,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    primaryColor: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) surfaceColor.copy(alpha = 0.8f) else surfaceColor
        ),
        border = if (isSelected) CardDefaults.outlinedCardBorder()
            .copy(brush = androidx.compose.ui.graphics.SolidColor(primaryColor)) else null
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    fontSize = 14.sp,
                    color = textPrimaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateFormat.format(Date(session.updatedAt)),
                    fontSize = 12.sp,
                    color = textSecondaryColor
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除会话",
                    tint = textSecondaryColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 功能行组件
 */
@Composable
private fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    primaryColor: Color,
    textSecondaryColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = primaryColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = textSecondaryColor
        )
    }
}

/**
 * 链接按钮组件
 */
@Composable
private fun LinkButton(
    text: String,
    url: String,
    primaryColor: Color
) {
    Surface(
        modifier = Modifier
            .clickable { BrowserUtil.browse(url) },
        color = primaryColor.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = primaryColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/**
 * 关于对话框 - 带章鱼设计
 */
@Composable
private fun AboutDialog(
    onDismissRequest: () -> Unit,
    backgroundColor: Color,
    surfaceColor: Color,
    primaryColor: Color,
    textPrimaryColor: Color,
    textSecondaryColor: Color
) {
    // 动态获取插件版本号
    val pluginVersion = remember {
        PluginManagerCore.getPlugin(PluginId.getId("com.guofan.overcode"))?.version?.toString() ?: "未知"
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "关于 OverCode",
                    color = textPrimaryColor,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 章鱼 Logo 区域 - 带渐变光晕
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        modifier = Modifier
                            .size(150.dp)
                            .padding(8.dp),
                        painter = painterResource("/image/o.png"),
                        contentDescription = "Octopus Logo"
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 插件名称
                Text(
                    text = "OverCode",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor
                )

                Text(
                    text = "IntelliJ IDEA AI 编程助手",
                    fontSize = 12.sp,
                    color = textSecondaryColor,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // 版本号
                Surface(
                    color = primaryColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Version $pluginVersion",
                        fontSize = 11.sp,
                        color = primaryColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = textSecondaryColor.copy(alpha = 0.3f)
                )

                // 功能介绍
                Text(
                    text = "功能介绍",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimaryColor,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // 功能卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = backgroundColor
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        FeatureRow(
                            icon = Icons.Default.Star,
                            text = "AI 智能对话编程助手",
                            primaryColor = primaryColor,
                            textSecondaryColor = textSecondaryColor
                        )
                        FeatureRow(
                            icon = Icons.Default.Settings,
                            text = "支持多种大语言模型",
                            primaryColor = primaryColor,
                            textSecondaryColor = textSecondaryColor
                        )
                        FeatureRow(
                            icon = Icons.Default.Add,
                            text = "代码解释、生成、优化",
                            primaryColor = primaryColor,
                            textSecondaryColor = textSecondaryColor
                        )
                        FeatureRow(
                            icon = Icons.Default.Edit,
                            text = "项目上下文理解",
                            primaryColor = primaryColor,
                            textSecondaryColor = textSecondaryColor
                        )
                        FeatureRow(
                            icon = Icons.Default.Star,
                            text = "记忆库功能",
                            primaryColor = primaryColor,
                            textSecondaryColor = textSecondaryColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = textSecondaryColor.copy(alpha = 0.3f)
                )

                // 点赞区域
                Text(
                    text = "喜欢这个插件？",
                    fontSize = 13.sp,
                    color = textPrimaryColor,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "给我们点赞支持一下！",
                    fontSize = 11.sp,
                    color = textSecondaryColor,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // 链接按钮组
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    LinkButton(
                        text = "👍 点赞",
                        url = "https://plugins.jetbrains.com/plugin/30117-overcode",
                        primaryColor = primaryColor
                    )
                    LinkButton(
                        text = "🌐 网站",
                        url = "https://felinetech.cn/",
                        primaryColor = primaryColor
                    )
                    LinkButton(
                        text = "📧 邮箱",
                        url = "mailto:fanguo922@gmail.com",
                        primaryColor = primaryColor
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "感谢您的支持！",
                    fontSize = 11.sp,
                    color = primaryColor,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("知道了", color = primaryColor)
            }
        },
        containerColor = surfaceColor,
        titleContentColor = textPrimaryColor
    )
}