package com.github.bboygf.over_code.ui.home

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.graphics.Color
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
import com.github.bboygf.over_code.ui.model_config.ModelConfigurable
import com.github.bboygf.over_code.vo.SessionInfo
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor

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
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

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
        viewModel.loadMessages(viewModel.currentSessionId)
        if (viewModel.chatMessages.isNotEmpty()) {
            try {
                listState.animateScrollToItem(viewModel.chatMessages.size - 1)
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
                    }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "新建",
                            tint = textSecondaryColor
                        )
                    }
                    IconButton(onClick = {
                        viewModel.clearCurrentSession()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "清空",
                            tint = textSecondaryColor
                        )
                    }
                    IconButton(onClick = {
                        project?.let {
                            ShowSettingsUtil.getInstance().showSettingsDialog(it, ModelConfigurable::class.java)
                        }
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
                if (viewModel.showHistory) {
                    HistoryScreen(
                        viewModel = viewModel,
                        backgroundColor = backgroundColor,
                        surfaceColor = surfaceColor,
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor,
                        primaryColor = primaryColor
                    )
                } else {
                    if (viewModel.chatMessages.isEmpty()) {
                        // 空状态 - 显示欢迎界面
                        WelcomeScreen()
                    } else {
                        // 消息列表
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(1.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                            userScrollEnabled = true
                        ) {
                            items(viewModel.chatMessages) { message ->
                                MessageBubble(
                                    message = message,
                                    onCopyCode = { viewModel.copyToClipboard(it) },
                                    onInsertCode = { viewModel.insertCodeAtCursor(it) }
                                )
                            }
                        }
                        // 2. 添加滚动条
                        VerticalScrollbar(
                            modifier = Modifier
                                .align(Alignment.CenterEnd) // 居右对齐
                                .fillMaxHeight(),            // 占满高度
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
                            // 自动滚动到底部
                            coroutineScope.launch {
                                if (viewModel.chatMessages.isNotEmpty()) {
                                    try {
                                        listState.animateScrollToItem(viewModel.chatMessages.size - 1)
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
                addProjectIndex = { viewModel.addProjectIndex() }
            )

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