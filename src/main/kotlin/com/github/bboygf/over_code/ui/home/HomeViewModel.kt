package com.github.bboygf.over_code.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.bboygf.over_code.llm.LLMMessage
import com.github.bboygf.over_code.llm.LLMService
import com.github.bboygf.over_code.po.ChatMessage
import com.github.bboygf.over_code.services.ChatDatabaseService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 聊天界面的ViewModel
 * 负责管理聊天消息、会话状态和与LLM服务的交互
 */
class HomeViewModel(
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
    
    private val ioScope = CoroutineScope(Dispatchers.IO)
    
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
}
