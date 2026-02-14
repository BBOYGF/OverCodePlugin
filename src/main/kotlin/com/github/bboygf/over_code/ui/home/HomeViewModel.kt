package com.github.bboygf.over_code.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.bboygf.over_code.LAST_SESSION
import com.github.bboygf.over_code.enums.ChatPattern
import com.github.bboygf.over_code.enums.ChatRole
import com.github.bboygf.over_code.llm.LLMService
import com.github.bboygf.over_code.po.LLMMessage
import com.github.bboygf.over_code.po.LlmToolCall
import com.github.bboygf.over_code.po.LlmToolDefinition
import com.github.bboygf.over_code.services.ChatDatabaseService
import com.github.bboygf.over_code.vo.ChatMessageVo
import com.github.bboygf.over_code.vo.ModelConfigInfo
import com.github.bboygf.over_code.vo.SessionInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.util.concurrent.CopyOnWriteArrayList
import com.github.bboygf.over_code.utils.ImageUtils
import com.github.bboygf.over_code.utils.ProjectFileUtils
import com.github.bboygf.over_code.utils.ToolRegistry
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject


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
    var chatMessageVos by mutableStateOf(listOf<ChatMessageVo>())
        private set

    var currentSessionId by mutableStateOf("")
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isCancelling by mutableStateOf(false)
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

    var chatMode by mutableStateOf(ChatPattern.计划模式) // "计划模式" 或 "执行模式"

    var selectedImageBase64List = mutableStateListOf<String>()
        private set


    // 使用 SharedFlow 发送单次执行的事件
    private val _scrollEvents = MutableSharedFlow<Int>()
    val scrollEvents = _scrollEvents.asSharedFlow()

    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val uiScope = CoroutineScope(Dispatchers.Main)
    private var currentJob: Job? = null

    init {
        ioScope.launch {
            val lastSession = dbService?.getValue(LAST_SESSION) ?: "default";
            withContext(Dispatchers.Main) {
                currentSessionId = lastSession
            }
        }
    }

    /**
     * 取消当前正在进行的请求
     */
    fun cancelCurrentRequest() {
        currentJob?.cancel()
        currentJob = null
        isCancelling = false
        isLoading = false
    }

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
        if (sessionId.isEmpty()) return
        ioScope.launch {
            chatMessageVos = dbService?.loadMessages(sessionId) ?: emptyList()
            withContext(Dispatchers.Main) {
                if (currentSessionId != sessionId) {
                    currentSessionId = sessionId
                }
            }
            // 只有在 ID 确实变化时才更新“最后打开的会话”到数据库
            val lastSaved = dbService?.getValue(LAST_SESSION)
            if (lastSaved != sessionId) {
                dbService?.addOrUpdateValue(LAST_SESSION, sessionId)
            }
        }
    }

    /**
     * 创建新会话
     */
    fun createNewSession() {
        dbService?.let {
            currentSessionId = it.createSession()
            chatMessageVos = emptyList()
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
            chatMessageVos = emptyList()
            currentSessionId = "default"
        }
    }

    /**
     * 清空当前会话
     */
    fun clearCurrentSession() {
        dbService?.clearMessages(currentSessionId)
        chatMessageVos = emptyList()
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
        if (userInput.isBlank() && selectedImageBase64List.isEmpty() || isLoading) return

        isLoading = true
        val currentImageList = selectedImageBase64List.toList()
        selectedImageBase64List.clear()

        // 1. 处理用户消息
        val userMessage = ChatMessageVo(
            id = System.currentTimeMillis().toString(),
            content = userInput,
            chatRole = ChatRole.用户,
            images = currentImageList
        )
        // 优化：简化 List 更新逻辑
        chatMessageVos = chatMessageVos + userMessage
        dbService?.saveMessage(userMessage, currentSessionId)

        // 2. 构建 LLM 历史 (注意：此时 UI 上的 messages 已经包含了 userMessage)
        val llmMessages = chatMessageVos.mapIndexed { index, msg ->
            LLMMessage(
                role = msg.chatRole.role,
                content = msg.content,
                // 只给最后一条用户消息附带图片
                images = msg.images,
                toolCalls = msg.toolCalls,
                toolCallId = msg.toolCallId,
                thought = msg.thought,
                thoughtSignature = msg.thoughtSignature
            )
        }.toMutableList()


        // 工具定义
        // 使用注册表自动加载工具
        // 工具定义
        val tools = ToolRegistry.allTools
            .filter { !it.isWriteTool || chatMode == ChatPattern.执行模式 }
            .map { it.toGenericDefinition() }

        currentJob = ioScope.launch {
            // 用于累计最终显示的文本
            val contentBuilder = StringBuilder()
            var errorOccurred = false
            try {
                processChatTurn(
                    history = llmMessages,
                    tools = tools,
                    contentBuilder = contentBuilder,
                    onScrollToBottom = onScrollToBottom
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    println("请求已取消")
                    return@launch
                }
                e.printStackTrace()
                errorOccurred = true
                contentBuilder.append("\n[系统错误: ${e.message}]")
            } finally {
                isLoading = false
                isCancelling = false
                currentJob = null
            }
        }
    }


    /**
     * 执行工具调用
     */
    private fun executeTool(functionCall: LlmToolCall): String {
        val tool = ToolRegistry.allTools.find { it.name == functionCall.functionName }
            ?: return "Unknown function: ${functionCall.functionName}"

        val argsMap = try {
            Json.parseToJsonElement(functionCall.arguments).jsonObject
        } catch (e: Exception) {
            emptyMap<String, JsonElement>()
        }
        return tool.execute(project!!, argsMap, chatMode)
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
            _scrollEvents.emit(chatMessageVos.size - 1)
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
     * 工具执行结果数据类
     */
    data class ToolExecutionResult(
        val call: LlmToolCall,
        val result: String
    )


    /**
     * 核心递归函数：处理 对话 -> 工具调用 -> 再次对话 的循环
     * 支持并行函数调用：并行执行所有工具，然后将结果一起返回给模型
     */
    private suspend fun processChatTurn(
        history: MutableList<LLMMessage>,
        tools: List<LlmToolDefinition>,
        contentBuilder: StringBuilder,
        onScrollToBottom: () -> Unit
    ) {
        val callList = CopyOnWriteArrayList<LlmToolCall>()
        val currentThought = StringBuilder()

        val service = llmService ?: run {
            println("请先设置模型！")
            return
        }

        // 创建一个临时的 AI 消息用于实时显示流式输出
        val streamingMessageId = System.currentTimeMillis().toString()
        var streamingMessage = ChatMessageVo(
            id = streamingMessageId,
            content = "",
            chatRole = ChatRole.助理,
            thought = null
        )

        // 先添加到列表
        withContext(Dispatchers.Main) {
            chatMessageVos = chatMessageVos + streamingMessage
        }

        // 调用流式 API
        service.chatStream(
            messages = history,
            tools = tools,
            onChunk = { chunk ->
                if (chunk.isNotEmpty()) {
                    contentBuilder.append(chunk)
                    // 实时更新消息内容
                    streamingMessage = streamingMessage.copy(content = contentBuilder.toString())
                    uiScope.launch {
                        chatMessageVos = chatMessageVos.map {
                            if (it.id == streamingMessageId) streamingMessage else it
                        }
                    }
                    onScrollToBottom()
                }
            },
            onToolCall = { call ->
                callList.add(call)
            },
            onThought = { thought ->
                currentThought.append(thought)
                // 实时更新思考内容
                streamingMessage = streamingMessage.copy(thought = currentThought.toString())
                uiScope.launch {
                    chatMessageVos = chatMessageVos.map {
                        if (it.id == streamingMessageId) streamingMessage else it
                    }
                }
            }
        )

        // 如果没有工具调用，保存最终消息并返回
        if (callList.isEmpty()) {
            val responseText = contentBuilder.toString()
            if (responseText.isNotEmpty()) {
                val finalMessage = streamingMessage.copy(
                    content = responseText,
                    thought = currentThought.toString().takeIf { it.isNotEmpty() }
                )
                withContext(Dispatchers.Main) {
                    chatMessageVos = chatMessageVos.map {
                        if (it.id == streamingMessageId) finalMessage else it
                    }
                }
                dbService?.saveMessage(finalMessage, currentSessionId)
                uiScope.launch { onScrollToBottom() }
            }
            return
        }

        // 构造工具调用提示
        val toolCallDescription = callList.joinToString("\n") {
            "${it.functionName}: ${it.arguments}"
        }

        // 更新流式消息为包含工具调用的最终版本
        val assistantMessage = streamingMessage.copy(
            content = contentBuilder.toString().ifEmpty { "parallel calling tools:\n$toolCallDescription" },
            thought = currentThought.toString().takeIf { it.isNotEmpty() },
            toolCalls = callList.toList(),
            thoughtSignature = callList.firstOrNull()?.id
        )
        withContext(Dispatchers.Main) {
            chatMessageVos = chatMessageVos.map {
                if (it.id == streamingMessageId) assistantMessage else it
            }
        }
        dbService?.saveMessage(assistantMessage, currentSessionId)
        uiScope.launch { onScrollToBottom() }

        // 并行执行工具
        val toolResults = ioScope.async {
            callList.map { call ->
                async(Dispatchers.IO) {
                    val result = executeTool(call)
                    ToolExecutionResult(call, result)
                }
            }.awaitAll()
        }.await()

        // 添加 assistant 消息到历史 (包含 toolCalls)
        history.add(
            LLMMessage(
                role = "assistant",
                content = contentBuilder.toString(),
                toolCalls = callList.toList(),
                thought = currentThought.toString().takeIf { it.isNotEmpty() },
                thoughtSignature = callList.firstOrNull()?.id
            )
        )

        // 添加所有工具结果到历史和 UI
        toolResults.forEach { result ->
            history.add(
                LLMMessage(
                    role = "tool",
                    toolCallId = result.call.id,
                    name = result.call.functionName,
                    content = result.result
                )
            )

            val toolsMessage = ChatMessageVo(
                id = System.currentTimeMillis().toString(),
                content = result.result,
                chatRole = ChatRole.工具,
                toolCallId = result.call.id
            )
            chatMessageVos = chatMessageVos + toolsMessage
            dbService?.saveMessage(toolsMessage, currentSessionId)
            uiScope.launch { onScrollToBottom() }
        }

        contentBuilder.setLength(0)

        // 递归进入下一轮
        processChatTurn(
            history = history,
            tools = tools,
            contentBuilder = contentBuilder,
            onScrollToBottom = onScrollToBottom
        )
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
            EditorModificationUtil.insertStringAtCaret(editor, code)
        }
    }

    /**
     * 添加项目索引
     */
    fun addProjectIndex() {
        if (project == null) return
        ioScope.launch {
            val projectIndexMsg = ProjectFileUtils.exportToMarkdown(project)
            val currentText = getInputText?.invoke() ?: ""
            uiScope.launch {
                onInputTextChange?.invoke(currentText + "\r\n" + projectIndexMsg)
            }
        }
    }
}
