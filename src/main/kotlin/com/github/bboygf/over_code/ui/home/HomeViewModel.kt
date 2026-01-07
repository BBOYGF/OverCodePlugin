package com.github.bboygf.over_code.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.bboygf.over_code.llm.LLMMessage
import com.github.bboygf.over_code.llm.LLMService
import com.github.bboygf.over_code.po.ChatMessage
import com.github.bboygf.over_code.services.ChatDatabaseService
import com.github.bboygf.over_code.services.ModelConfigInfo
import com.github.bboygf.over_code.services.SessionInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorModificationUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.intellij.openapi.project.Project
import com.intellij.openapi.fileEditor.FileEditorManager
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * 聊天界面的ViewModel
 * 负责管理聊天消息、会话状态和与LLM服务的交互
 */
class HomeViewModel(
    private val project: Project?,
    private val dbService: ChatDatabaseService?,
    private val llmService: LLMService?
) {
    // UI状态
    var messages by mutableStateOf(listOf<ChatMessage>())
        private set
    
    var currentSessionId by mutableStateOf("default")
        private set
    
    var isLoading by mutableStateOf(false)
        private set

    var sessions by mutableStateOf(listOf<SessionInfo>())
        private set

    var showHistory by mutableStateOf(false)

    // 模型和模式状态
    var modelConfigs by mutableStateOf(listOf<ModelConfigInfo>())
        private set

    var activeModel by mutableStateOf<ModelConfigInfo?>(null)
        private set

    var chatMode by mutableStateOf("计划模式") // "计划模式" 或 "执行模式"
    
    private val ioScope = CoroutineScope(Dispatchers.IO)
    
    /**
     * 加载所有模型配置并获取当前激活的模型
     */
    fun loadModelConfigs() {
        dbService?.let {
            val configs = it.getAllModelConfigs()
            modelConfigs = configs
            activeModel = configs.find { it.isActive }
        }
    }

    /**
     * 切换激活模型
     */
    fun setActiveModel(modelId: String) {
        dbService?.let {
            it.setActiveModel(modelId)
            loadModelConfigs()
        }
    }

    /**
     * 加载所有会话
     */
    fun loadSessions() {
        sessions = dbService?.loadSessions() ?: emptyList()
    }

    /**
     * 加载指定会话的历史消息
     */
    fun loadMessages(sessionId: String) {
        currentSessionId = sessionId
        messages = dbService?.loadMessages(sessionId) ?: emptyList()
    }
    
    /**
     * 创建新会话
     */
    fun createNewSession() {
        dbService?.let {
            currentSessionId = it.createSession()
            messages = emptyList()
            showHistory = false
        }
    }

    /**
     * 删除会话
     */
    fun deleteSession(sessionId: String) {
        dbService?.deleteSession(sessionId)
        loadSessions()
        if (currentSessionId == sessionId) {
            messages = emptyList()
            currentSessionId = "default"
        }
    }
    
    /**
     * 清空当前会话
     */
    fun clearCurrentSession() {
        dbService?.clearMessages(currentSessionId)
        messages = emptyList()
    }
    
    /**
     * 发送用户消息并获取AI响应
     */
    fun sendMessage(userInput: String, onScrollToBottom: () -> Unit) {
        if (userInput.isBlank() || isLoading) return
        
        isLoading = true
        
        // 添加用户消息
        val userMessage = ChatMessage(
            id = System.currentTimeMillis().toString(),
            content = userInput,
            isUser = true
        )
        messages = messages + userMessage
        dbService?.saveMessage(userMessage, currentSessionId)
        
        // 构建历史消息
        val llmMessages = messages.map { msg ->
            LLMMessage(
                role = if (msg.isUser) "user" else "assistant",
                content = msg.content
            )
        }
        
        // 创建AI消息占位符
        val aiMessageId = (System.currentTimeMillis() + 1).toString()
        val aiMessage = ChatMessage(
            id = aiMessageId,
            content = "",
            isUser = false
        )
        messages = messages + aiMessage
        
        // 调用流式API
        ioScope.launch {
            try {
                var fullContent = ""
                llmService?.chatStream(llmMessages) { chunk ->
                    fullContent += chunk
                    
                    // 更新消息内容
                    messages = messages.map { msg ->
                        if (msg.id == aiMessageId) {
                            msg.copy(content = fullContent)
                        } else {
                            msg
                        }
                    }
                    
                    // 触发滚动到底部
                    onScrollToBottom()
                } ?: run {
                    fullContent = "未配置模型，请先在设置中配置并激活一个模型"
                    messages = messages.map { msg ->
                        if (msg.id == aiMessageId) {
                            msg.copy(content = fullContent)
                        } else {
                            msg
                        }
                    }
                }
                
                // 保存完整消息
                dbService?.saveMessage(
                    ChatMessage(
                        id = aiMessageId,
                        content = fullContent,
                        isUser = false
                    ),
                    currentSessionId
                )
                
                isLoading = false
            } catch (e: Exception) {
                e.printStackTrace()
                
                // 更新错误消息
                val errorContent = "错误: ${e.message ?: "未知错误"}"
                messages = messages.map { msg ->
                    if (msg.id == aiMessageId) {
                        msg.copy(content = errorContent)
                    } else {
                        msg
                    }
                }
                
                isLoading = false
            }
        }
    }
    
    /**
     * 从外部（如Action）发送消息到聊天窗口
     * 此方法不需要 onScrollToBottom 回调
     */
    fun sendMessageFromExternal(userInput: String) {
        sendMessage(userInput) {
            // 空回调，因为从外部调用时可能无法访问UI的滚动状态
        }
    }

    // 用于暴露给外部修改输入框内容的回调
    var onInputTextChange: ((String) -> Unit)? = null
    var getInputText: (() -> String)? = null

    /**
     * 从外部（如Action）插入文本到输入框，不自动发送
     * 如果输入框有内容，则在光标位置插入；否则直接设置
     */
    fun insertTextToInput(text: String) {
        val currentText = getInputText?.invoke() ?: ""
        val newText = if (currentText.isEmpty()) {
            text
        } else {
            // 如果已有内容，在末尾添加
            "$currentText\n\n$text"
        }
        onInputTextChange?.invoke(newText)
    }

    /**
     * 复制文本到剪贴板
     */
    fun copyToClipboard(text: String) {
        val selection = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
    }

    /**
     * 在当前活动编辑器的光标位置插入代码
     */
    fun insertCodeAtCursor(code: String) {
        val currentProject = project ?: return
        val editor = FileEditorManager.getInstance(currentProject).selectedTextEditor ?: return
        WriteCommandAction.runWriteCommandAction(currentProject) {
            // 一行代码搞定：插入字符串，自动处理光标移动和选区替换
            EditorModificationUtil.insertStringAtCaret(editor, code)
        }
    }
}
