package com.github.bboygf.over_code.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.bboygf.over_code.llm.LLMService
import com.github.bboygf.over_code.po.LLMMessage
import com.github.bboygf.over_code.services.ChatDatabaseService
import com.github.bboygf.over_code.vo.ChatMessage
import com.github.bboygf.over_code.vo.ModelConfigInfo
import com.github.bboygf.over_code.vo.SessionInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import com.github.bboygf.over_code.utils.ImageUtils
import com.github.bboygf.over_code.utils.ProjectFileUtils
import kotlinx.coroutines.withContext


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

    var loadHistory by mutableStateOf(true)
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

    var selectedImageBase64List = mutableStateListOf<String>()
        private set


    // 使用 SharedFlow 发送单次执行的事件
    private val _scrollEvents = MutableSharedFlow<Int>()
    val scrollEvents = _scrollEvents.asSharedFlow()

    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val uiScope = CoroutineScope(Dispatchers.Main)

    // 2. 暴露事件处理函数
    fun onLoadHistoryChange(newValue: Boolean) {
        loadHistory = newValue
    }

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
     * 设置当前选中的图片
     */
    fun addImage(base64: String) {
        if (!selectedImageBase64List.contains(base64)) {
            selectedImageBase64List.add(base64)
        }
    }

    /**
     * 删除图片
     */
    fun removeImage(base64: String) {
        selectedImageBase64List.remove(base64)
    }

    /**
     * 尝试从剪贴板读取图片
     */
    fun checkClipboardForImage(): Boolean {
        val base64 = ImageUtils.getClipboardImageBase64()
        if (base64 != null) {
            selectedImageBase64List += base64
            return true
        }
        return false
    }


    fun sendMessage(userInput: String, onScrollToBottom: () -> Unit) {
        if (userInput.isBlank() && selectedImageBase64List == null || isLoading) return

        isLoading = true
        val currentImageList = selectedImageBase64List.toList()
        selectedImageBase64List.clear()


        isLoading = true

        // 添加用户消息
        val userMessage = ChatMessage(
            id = System.currentTimeMillis().toString(),
            content = userInput,
            isUser = true
        )
        var msgList = mutableListOf<ChatMessage>()
        if (loadHistory) {
            messages = messages + userMessage
            msgList = messages.toMutableList()
        } else {
            messages += userMessage
            msgList += userMessage
        }
        dbService?.saveMessage(userMessage, currentSessionId)

        // 构建历史消息
        // 构建历史消息
        val llmMessages = msgList.mapIndexed { index, msg ->
            LLMMessage(
                role = if (msg.isUser) "user" else "assistant",
                content = msg.content,
                images = if (msg.isUser && index == msgList.size - 1) currentImageList else emptyList()
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
                    uiScope.launch {
                        messages = messages.map { msg ->
                            if (msg.id == aiMessageId) {
                                msg.copy(content = fullContent)
                            } else {
                                msg
                            }
                        }
                        // 触发滚动到底部
                        onScrollToBottom()
                    }
                } ?: run {
                    uiScope.launch {
                        fullContent = "未配置模型，请先在设置中配置并激活一个模型"
                        messages = messages.map { msg ->
                            if (msg.id == aiMessageId) {
                                msg.copy(content = fullContent)
                            } else {
                                msg
                            }
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
        ioScope.launch {
            // 发送信号：滚动到最后一条（索引 0 或 messages.size - 1）
            _scrollEvents.emit(messages.size - 1)
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

    /**
     * 添加项目索引
     */
    fun addProjectIndex() {
        if (project == null) {
            return
        }
        ioScope.launch {
            val projectIndexMsg = ProjectFileUtils.exportToMarkdown(project)
            val currentText = getInputText?.invoke() ?: ""
            uiScope.launch {
                onInputTextChange?.invoke(currentText + "\r\n" + projectIndexMsg)
            }
        }
    }
}