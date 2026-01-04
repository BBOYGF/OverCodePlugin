package com.github.bboygf.over_code.ui.home

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bboygf.over_code.actions.ChatViewModelHolder
import com.github.bboygf.over_code.llm.LLMService
import com.github.bboygf.over_code.services.ChatDatabaseService
import com.github.bboygf.over_code.ui.component.BottomInputArea
import com.github.bboygf.over_code.ui.component.MessageBubble
import com.github.bboygf.over_code.ui.component.WelcomeScreen
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.launch
import javax.swing.JComponent
/**
 * Over Code 聊天界面工具窗口
 */
class OverCoderHomeWindowFactory : ToolWindowFactory {

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
    val viewModel = remember(dbService, llmService) {
        HomeViewModel(dbService, llmService)
    }
    
    // 注册 ViewModel 到 Project Service，以便 Action 可以访问
    LaunchedEffect(project, viewModel) {
        project?.getService(ChatViewModelHolder::class.java)?.let { holder ->
            holder.viewModel = viewModel
        }
    }

    // UI状态
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 加载历史消息
    LaunchedEffect(viewModel.currentSessionId) {
        viewModel.loadMessages(viewModel.currentSessionId)
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = surfaceColor,
                    titleContentColor = textPrimaryColor
                )
            )

            // 中央对话区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (viewModel.messages.isEmpty()) {
                    // 空状态 - 显示欢迎界面
                    WelcomeScreen()
                } else {
                    // 消息列表
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(1.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(viewModel.messages) { message ->
                            MessageBubble(message)
                        }
                    }
                }
            }

            // 底部输入区域
            BottomInputArea(
                inputText = inputText,
                onInputChange = { inputText = it },
                isLoading = viewModel.isLoading,
                onSend = {
                    if (inputText.isNotBlank()) {
                        val userInput = inputText
                        inputText = ""
                        
                        viewModel.sendMessage(userInput) {
                            // 自动滚动到底部
                            coroutineScope.launch {
                                if (viewModel.messages.isNotEmpty()) {
                                    listState.animateScrollToItem(viewModel.messages.size - 1)
                                }
                            }
                        }
                    }
                },
                backgroundColor = surfaceColor,
                textColor = textPrimaryColor
            )
        }
    }
}

