package com.github.bboygf.autocoderplugin.toolWindow

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bboygf.autocoderplugin.llm.LLMMessage
import com.github.bboygf.autocoderplugin.llm.LLMService
import com.github.bboygf.autocoderplugin.services.ChatDatabaseService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.launch
import javax.swing.JComponent

/**
 * Qoder 聊天界面工具窗口
 */
class QoderChatToolWindowFactory : ToolWindowFactory {
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val composePanel = createComposePanel(project)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(composePanel, "", false)
        toolWindow.contentManager.addContent(content)
    }
    
    private fun createComposePanel(project: Project): JComponent {
        return ComposePanel().apply {
            setContent {
                QoderChatUI(project)
            }
        }
    }
}

/**
 * 消息数据类
 */
data class ChatMessage(
    val id: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Qoder 聊天主界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun QoderChatUI(project: Project? = null) {
    // 数据库服务
    val dbService = remember(project) { 
        project?.let { ChatDatabaseService.getInstance(it) }
    }
    
    // LLM 服务
    val llmService = remember(project) {
        project?.let { LLMService.getInstance(it) }
    }
    
    // 当前会话 ID
    var currentSessionId by remember { mutableStateOf("default") }
    
    // 消息列表状态
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // 加载历史消息
    LaunchedEffect(currentSessionId, dbService) {
        dbService?.let {
            messages = it.loadMessages(currentSessionId)
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
                        // 新建会话
                        coroutineScope.launch {
                            dbService?.let {
                                currentSessionId = it.createSession()
                                messages = emptyList()
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "新建",
                            tint = textSecondaryColor
                        )
                    }
                    IconButton(onClick = { /* 分享 */ }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "分享",
                            tint = textSecondaryColor
                        )
                    }
                    IconButton(onClick = { 
                        // 清空当前会话
                        coroutineScope.launch {
                            dbService?.clearMessages(currentSessionId)
                            messages = emptyList()
                        }
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
                if (messages.isEmpty()) {
                    // 空状态 - 显示欢迎界面
                    WelcomeScreen()
                } else {
                    // 消息列表
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(messages) { message ->
                            MessageBubble(message)
                        }
                    }
                }
            }
            
            // 底部输入区域
            BottomInputArea(
                inputText = inputText,
                onInputChange = { inputText = it },
                isLoading = isLoading,
                onSend = {
                    if (inputText.isNotBlank() && !isLoading) {
                        val userInput = inputText
                        inputText = ""
                        isLoading = true
                        
                        // 使用普通线程而不是协程
                        Thread {
                            try {
                                // 添加用户消息
                                val userMessage = ChatMessage(
                                    id = System.currentTimeMillis().toString(),
                                    content = userInput,
                                    isUser = true
                                )
                                
                                // 在 UI 线程更新消息列表
                                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                                    messages = messages + userMessage
                                }
                                
                                dbService?.saveMessage(userMessage, currentSessionId)
                                
                                // 构建历史消息
                                val llmMessages = messages.map { msg ->
                                    LLMMessage(
                                        role = if (msg.isUser) "user" else "assistant",
                                        content = msg.content
                                    )
                                }
                                
                                // 调用 LLM API（已经在后台线程）
                                val aiResponse = try {
                                    llmService?.chat(llmMessages)
                                        ?: "未配置模型，请先在设置中配置并激活一个模型"
                                } catch (e: Exception) {
                                    "错误: ${e.message ?: "未知错误"}"
                                }
                                
                                // 添加 AI 回复
                                val aiMessage = ChatMessage(
                                    id = (System.currentTimeMillis() + 1).toString(),
                                    content = aiResponse,
                                    isUser = false
                                )
                                
                                // 在 UI 线程更新消息列表
                                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                                    messages = messages + aiMessage
                                    isLoading = false
                                    
                                    // 滚动到底部
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(messages.size - 1)
                                    }
                                }
                                
                                dbService?.saveMessage(aiMessage, currentSessionId)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                // 显示错误消息
                                val errorMessage = ChatMessage(
                                    id = (System.currentTimeMillis() + 1).toString(),
                                    content = "错误: ${e.message ?: "未知错误"}",
                                    isUser = false
                                )
                                
                                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                                    messages = messages + errorMessage
                                    isLoading = false
                                }
                            }
                        }.start()
                    }
                },
                backgroundColor = surfaceColor,
                textColor = textPrimaryColor
            )
        }
    }
}

/**
 * 欢迎屏幕
 */
@Composable
fun WelcomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Qoder Logo (使用简单的圆形图标代替)
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    color = Color(0xFF4A4A4A),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Q",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF888888)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Qoder 标题
        Text(
            text = "Qoder",
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF888888)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 欢迎文字
        Text(
            text = "打开关闭窗口",
            fontSize = 13.sp,
            color = Color(0xFF606060)
        )
        
        Spacer(modifier = Modifier.height(40.dp))
        
        // 底部介绍卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF3C3F41)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Logo
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = Color(0xFF4A9D5F),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Q",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // 介绍文字
                Text(
                    text = "嗨！我是Qoder。我可以帮你一起编程，或者为你解答编程相关问题。",
                    fontSize = 13.sp,
                    color = Color(0xFFBBBBBB),
                    lineHeight = 20.sp
                )
            }
        }
    }
}

/**
 * 消息气泡
 */
@Composable
fun MessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isUser) {
            // AI 头像
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = Color(0xFF4A9D5F),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Q",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        // 消息内容
        Card(
            modifier = Modifier.widthIn(max = 300.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isUser) Color(0xFF4A9D5F) else Color(0xFF3C3F41)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                fontSize = 13.sp,
                color = if (message.isUser) Color.White else Color(0xFFBBBBBB),
                lineHeight = 20.sp
            )
        }
        
        if (message.isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            // 用户头像
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = Color(0xFF5C5C5C),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "用户",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 底部输入区域
 */
@Composable
fun BottomInputArea(
    inputText: String,
    onInputChange: (String) -> Unit,
    isLoading: Boolean,
    onSend: () -> Unit,
    backgroundColor: Color,
    textColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
    ) {
        Divider(color = Color(0xFF2B2B2B), thickness = 1.dp)
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // 添加按钮
            IconButton(
                onClick = { /* 添加附件 */ },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "添加",
                    tint = Color(0xFF808080)
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // 输入框
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = "添加上下文\n自主规则及编程\n智能性 Auto",
                        fontSize = 12.sp,
                        color = Color(0xFF606060),
                        lineHeight = 16.sp
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    focusedContainerColor = Color(0xFF2B2B2B),
                    unfocusedContainerColor = Color(0xFF2B2B2B),
                    focusedBorderColor = Color(0xFF4A4A4A),
                    unfocusedBorderColor = Color(0xFF3C3F41)
                ),
                shape = RoundedCornerShape(8.dp),
                minLines = 2,
                maxLines = 6
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // 发送按钮
            IconButton(
                onClick = onSend,
                modifier = Modifier.size(36.dp),
                enabled = inputText.isNotBlank() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF4A9D5F)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "发送",
                        tint = if (inputText.isNotBlank()) Color(0xFF4A9D5F) else Color(0xFF606060)
                    )
                }
            }
        }
    }
}
