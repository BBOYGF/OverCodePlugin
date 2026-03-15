package com.github.bboygf.over_code.ui.home

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bboygf.over_code.EXTRACT_MEMORY_PROMPT
import com.github.bboygf.over_code.llm.LLMService
import com.github.bboygf.over_code.services.ChatDatabaseService
import com.github.bboygf.over_code.services.HomeViewModelService
import com.github.bboygf.over_code.ui.about.AboutDialog
import com.github.bboygf.over_code.ui.component.BottomInputArea
import com.github.bboygf.over_code.ui.component.MessageBubble
import com.github.bboygf.over_code.ui.component.WelcomeScreen
import com.github.bboygf.over_code.ui.history.HistoryScreen
import com.github.bboygf.over_code.ui.memory.MemoryPanel
import com.github.bboygf.over_code.ui.model_config.ModelConfigurable
import com.github.bboygf.over_code.utils.CodeEditNavigator
import com.github.bboygf.over_code.utils.Log
import com.github.bboygf.over_code.vo.ChatTab
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.launch
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

            // 计算最后一个 item 的底部超出视口底部的距离
            // 如果 > 0，说明用户向上滚动了，内容底部在屏幕下方
            // 如果 <= 0，说明内容没有超出屏幕下方（要么刚好贴底，要么内容不够一屏）
            val distanceToBottom = (scrollOffset + itemSize) - viewportEndOffset

            Triple(isLastItem, distanceToBottom, totalItems)
        }.collect { (isAtBottom, distanceToBottom, totalItems) ->
            // 只要超出的距离 <= 50（允许 50 的弹性滚动误差），就认为在底部
            isUserAtBottom = if (isAtBottom && distanceToBottom <= 100) {
                true
                // 用户向上滚动，离开了底部位置
            } else {
                false
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
                ),
                modifier = Modifier.height(40.dp)
            )

            // Tab 栏
            ChatTabRow(
                tabs = viewModel.tabs,
                activeTabId = viewModel.activeTabId,
                onTabClick = { tabId ->
                    viewModel.switchTab(tabId)
                    viewModel.showHistory = false
                    viewModel.showMemoryPanel = false
                },
                onTabClose = { tabId ->
                    viewModel.closeTab(tabId)
                },
                onNewTab = {
                    viewModel.createNewSession()
                },
                backgroundColor = surfaceColor,
                primaryColor = primaryColor,
                textPrimaryColor = textPrimaryColor,
                textSecondaryColor = textSecondaryColor
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
                    if (inputText.text.isNotBlank() || viewModel.selectedImageBase64List.isNotEmpty()) {
                        val userInput = inputText
                        inputText = TextFieldValue("")
                        coroutineScope.launch {
                            try {
                                // 当用户点击发送时，强制锁定为处于底部状态
                                isUserAtBottom = true

                                // 当不在底部时直接滚动可能会被后续 LaunchedEffect 覆盖
                                // 为了避免这种时序问题，使用 animateScrollToItem 既有平滑滚动效果又不会瞬间触发无效判断
                                listState.animateScrollToItem(
                                    index = viewModel.chatMessageVos.size - 1,
                                    scrollOffset = 10000
                                )
                            } catch (e: Exception) {
                                println("聊天结束后滚动异常" + e.message)
                            }
                        }
                        viewModel.sendMessage(userInput.text) {
                            // AI 流式输出时，只有用户在底部时才自动滚动
                            // 不检查 hasUserScrolled，避免发送消息时的重置影响滚动
                            coroutineScope.launch {
                                if (viewModel.chatMessageVos.isNotEmpty() && isUserAtBottom) {
                                    try {
                                        // 增加短暂延迟，等待 Compose 根据新的流式文本完成重组和高度重新测量
                                        kotlinx.coroutines.delay(50)
                                        // 使用 scrollToItem 并设置一个大的 scrollOffset 来滚动到 item 底部
                                        // 这样即使消息很长，也能看到最新的内容
                                        listState.scrollToItem(
                                            index = viewModel.chatMessageVos.size - 1,
                                            scrollOffset = 10000
                                        )
                                    } catch (e: Exception) {
                                        Log.error("获取最后一行数据异常！", e)
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
                isCancelling = viewModel.isCancelling,
                onCancel = { viewModel.cancelCurrentRequest() },
                onExtractMemory = {
                    viewModel.sendMessage(EXTRACT_MEMORY_PROMPT) {
                        coroutineScope.launch {
                            if (viewModel.chatMessageVos.isNotEmpty()) {
                                try {
                                    listState.scrollToItem(
                                        index = viewModel.chatMessageVos.size - 1,
                                        scrollOffset = 10000
                                    )
                                } catch (e: Exception) {
                                    Log.error("获取最后一行数据异常！", e)
                                }
                            }
                        }
                    }
                }
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
 * Chat Tab Row - 聊天标签栏组件
 */
@Composable
fun ChatTabRow(
    tabs: List<ChatTab>,
    activeTabId: String,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onNewTab: () -> Unit,
    backgroundColor: Color,
    primaryColor: Color,
    textPrimaryColor: Color,
    textSecondaryColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(35.dp)
            .background(backgroundColor)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 渲染每个 Tab
        tabs.forEach { tab ->
            val isActive = tab.id == activeTabId
            Surface(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .clickable { onTabClick(tab.id) },
                color = if (isActive) primaryColor.copy(alpha = 0.2f) else backgroundColor,
                shape = RoundedCornerShape(4.dp),
                border = if (isActive) {
                    androidx.compose.foundation.BorderStroke(1.dp, primaryColor)
                } else {
                    androidx.compose.foundation.BorderStroke(1.dp, textSecondaryColor.copy(alpha = 0.3f))
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tab 标题
                    Text(
                        text = tab.title,
                        fontSize = 12.sp,
                        color = if (isActive) primaryColor else textSecondaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 120.dp)
                    )

                    // 加载状态指示器
                    if (tab.isLoading) {
                        Spacer(modifier = Modifier.width(4.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.dp,
                            color = primaryColor
                        )
                    }

                    // 关闭按钮
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        modifier = Modifier
                            .size(14.dp)
                            .clickable { onTabClose(tab.id) },
                        tint = textSecondaryColor
                    )
                }
            }
        }

        // 新建 Tab 按钮
        Surface(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .clickable { onNewTab() },
            color = backgroundColor,
            shape = RoundedCornerShape(4.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, textSecondaryColor.copy(alpha = 0.3f))
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "新建会话",
                modifier = Modifier
                    .padding(8.dp)
                    .size(16.dp),
                tint = textSecondaryColor
            )
        }
    }
}