package com.github.bboygf.over_code.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.bboygf.over_code.LAST_SESSION
import com.github.bboygf.over_code.DEFAULT_SYSTEM_PROMPT
import com.github.bboygf.over_code.enums.ChatPattern
import com.github.bboygf.over_code.enums.ChatRole
import com.github.bboygf.over_code.llm.LLMService
import com.github.bboygf.over_code.po.LLMMessage
import com.github.bboygf.over_code.po.LlmToolCall
import com.github.bboygf.over_code.po.LlmToolDefinition
import com.github.bboygf.over_code.services.ChatDatabaseService
import com.github.bboygf.over_code.vo.ChatMessageVo
import com.github.bboygf.over_code.vo.MemoryVo
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
import com.github.bboygf.over_code.utils.Log
import com.github.bboygf.over_code.utils.ProjectFileUtils
import com.github.bboygf.over_code.utils.ToolRegistry
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
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

    // 错误提示状态
    var errorMessage by mutableStateOf<String?>(null)
        private set

    var showErrorDialog by mutableStateOf(false)
        private set

    // 模型和模式状态
    var modelConfigs by mutableStateOf(listOf<ModelConfigInfo>())
        private set

    var activeModel by mutableStateOf<ModelConfigInfo?>(null)
        private set

    var chatMode by mutableStateOf(ChatPattern.计划模式) // "计划模式" 或 "执行模式"

    var selectedImageBase64List = mutableStateListOf<String>()
        private set

    // 记忆功能状态
    var showMemoryPanel by mutableStateOf(false)


    // 记忆数据
    var memories by mutableStateOf<List<MemoryVo>>(emptyList())
        private set

    // AI 生成记忆状态
    var isGeneratingMemory by mutableStateOf(false)
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

    /**
     * 关闭错误提示对话框
     */
    fun dismissError() {
        showErrorDialog = false
        errorMessage = null
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

    // ==================== 记忆功能 ====================

    /**
     * 加载所有记忆概要
     */
    fun loadMemories() {
        memories = dbService?.getAllMemorySummaries() ?: emptyList()
    }

    /**
     * 根据记忆概要获取记忆详情
     */
    fun getMemoryDetailBySummary(summary: String): MemoryVo? {
        return dbService?.getMemoryDetailBySummary(summary)
    }

    /**
     * 根据记忆ID获取记忆详情
     */
    fun getMemoryById(memoryId: String): MemoryVo? {
        return dbService?.getMemoryById(memoryId)
    }

    /**
     * 添加记忆
     */
    fun addMemory(memory: MemoryVo) {
        dbService?.addMemory(memory)
        loadMemories()
    }

    /**
     * 更新记忆概要和详情
     */
    fun updateMemory(memoryId: String, summary: String, content: String) {
        dbService?.updateMemory(memoryId, summary, content)
        loadMemories()
    }

    /**
     * 删除记忆
     */
    fun deleteMemory(memoryId: String) {
        dbService?.deleteMemory(memoryId)
        loadMemories()
    }

    /**
     * 获取记忆上下文（用于AI对话）
     */
    fun getMemoryContext(): String {
        if (memories.isEmpty()) return ""

        return memories.joinToString("\n\n") { memory ->
            val detail = dbService?.getMemoryById(memory.memoryId)
            "【${memory.summary}】\n${detail?.content ?: ""}"
        }
    }


    /**
     * 从当前会话生成记忆（用户手动触发）
     */
    fun generateMemoryFromSession() {
        if (chatMessageVos.isEmpty()) return

        ioScope.launch {
            isGeneratingMemory = true
            try {
                analyzeAndSaveMemory(chatMessageVos)
            } finally {
                withContext(Dispatchers.Main) {
                    isGeneratingMemory = false
                }
            }
        }
    }

    /**
     * 分析会话内容并生成记忆
     */
    private suspend fun analyzeAndSaveMemory(messages: List<ChatMessageVo>) {
        if (messages.isEmpty()) return

        val service = llmService ?: return

        // 构建对话内容摘要
        val conversationText = buildString {
            messages.forEach { msg ->
                append("【${msg.chatRole.name}】: ${msg.content.take(500)}\n")
            }
        }

        // 调用 AI 分析并生成记忆
        val analysisPrompt = """
            请分析以下对话内容，识别出与传统项目不一样的"意外值"或特殊配置。

            意外值的定义：
            - 与常规开发实践不同的配置或设置
            - 项目特有的架构或约定
            - 特殊的依赖库或工具版本
            - 自定义的代码风格或规范
            - 任何可能影响后续开发的重要信息

            对话内容：
            $conversationText

            请以JSON格式返回分析结果，格式如下：
            {
                "memories": [
                    {
                        "summary": "经验概要（简短描述，如：使用XX框架的特定版本）",
                        "content": "经验详情（完整的配置、代码或说明）"
                    }
                ],
                "summary": "整体总结"
            }

            注意：
            1. 只返回JSON，不要包含其他内容
            2. 如果没有发现任何意外值，返回 {"memories": [], "summary": "未发现需要记忆的内容"}
            3. 每条记忆的 summary 和 content 都要有实际内容
        """.trimIndent()

        try {
            val result = service.chat(
                listOf(
                    LLMMessage(role = "user", content = analysisPrompt)
                )
            )

            // 解析 AI 返回的 JSON 结果
            parseAndSaveMemory(result)
        } catch (e: Exception) {
            println("生成记忆失败: ${e.message}")
        }
    }

    /**
     * 解析 AI 返回的记忆并保存到数据库
     */
    private fun parseAndSaveMemory(jsonResult: String) {
        try {
            // 简单的 JSON 解析
            val cleanedJson = jsonResult
                .trim()
                .replaceFirst("^```json".toRegex(), "")
                .replaceFirst("^```".toRegex(), "")
                .replace("```".toRegex(), "")
                .trim()

            val jsonElement = Json.parseToJsonElement(cleanedJson)
            val rootObj = jsonElement.jsonObject

            // 解析记忆列表
            val memoriesArray = rootObj["memories"]?.jsonArray
            memoriesArray?.forEach { memoryElement ->
                val memoryObj = memoryElement.jsonObject
                val summary = memoryObj["summary"]?.toString()?.replace("\"", "") ?: return@forEach
                val content = memoryObj["content"]?.toString()?.replace("\"", "") ?: return@forEach

                if (summary.isNotBlank() && content.isNotBlank()) {
                    val memory = MemoryVo(
                        summary = summary,
                        content = content
                    )
                    dbService?.addMemory(memory)
                }
            }

            // 刷新记忆列表
            loadMemories()
        } catch (e: Exception) {
            println("解析记忆JSON失败: ${e.message}")
            // 尝试使用更简单的方式解析
            parseMemorySimple(jsonResult)
        }
    }

    /**
     * 简化的记忆解析方法（处理不标准的JSON格式）
     */
    private fun parseMemorySimple(text: String) {
        try {
            // 检查是否没有发现内容
            if (text.contains("未发现") || text.contains("没有发现") || text.contains("{}")) {
                return
            }

            // 简单解析：提取 summary 和 content
            val lines = text.lines()
            var currentSummary = ""
            var currentContent = StringBuilder()

            for (line in lines) {
                when {
                    // 检测概要行（包含"概要"、"标题"或【】包围的内容）
                    line.contains("概要") || line.contains("标题") || (line.contains("【") && line.contains("】")) -> {
                        // 保存前一个记忆
                        if (currentSummary.isNotBlank() && currentContent.isNotEmpty()) {
                            saveMemoryIfNeeded(currentSummary, currentContent.toString())
                        }

                        // 提取新概要
                        currentSummary = line
                            .replace(Regex(".*[概要标题]"), "")
                            .replace(Regex("[【】:]"), "")
                            .trim()
                        if (currentSummary.isBlank()) {
                            currentSummary = line.replace(Regex("[【】]"), "").trim()
                        }
                        currentContent = StringBuilder()
                    }
                    // 检测详情/内容行
                    (line.contains("详情") || line.contains("内容") || line.contains("说明")) && line.contains(":") -> {
                        currentContent.append(line.substringAfter(":").trim())
                    }
                    // 普通内容行
                    line.trim()
                        .isNotBlank() && !line.contains("{") && !line.contains("}") && !line.contains("概要") && !line.contains(
                        "详情"
                    ) -> {
                        if (currentSummary.isNotBlank()) {
                            currentContent.append(" ").append(line.trim())
                        }
                    }
                }
            }

            // 保存最后一个记忆
            if (currentSummary.isNotBlank() && currentContent.isNotEmpty()) {
                saveMemoryIfNeeded(currentSummary, currentContent.toString())
            }

            // 刷新记忆列表
            loadMemories()
        } catch (e: Exception) {
            println("简单解析记忆失败: ${e.message}")
        }
    }

    /**
     * 保存记忆（如果存在则更新）
     */
    private fun saveMemoryIfNeeded(summary: String, content: String) {
        if (summary.isBlank() || content.isBlank()) return

        // 检查是否已存在相同 summary 的记忆
        val existingMemory = dbService?.getMemoryDetailBySummary(summary)
        if (existingMemory != null) {
            // 更新已存在的记忆
            dbService?.updateMemory(existingMemory.memoryId, summary, content)
        } else {
            // 添加新记忆
            dbService?.addMemory(MemoryVo(summary = summary, content = content))
        }
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
        val sessionInfo = dbService?.getSessionBySessionId(currentSessionId)

        if (sessionInfo != null && sessionInfo.title == "新对话") {
            dbService.updateSessionTitle(currentSessionId, userInput.substring(0, userInput.length.coerceAtMost(100)))
        }

        // 2. 构建 LLM 消息列表
        val llmMessages = mutableListOf<LLMMessage>()

        // 2.1 添加系统提示词
        llmMessages.add(LLMMessage(role = "system", content = DEFAULT_SYSTEM_PROMPT))

        // 2.2 添加记忆上下文
//        val memoryContext = getMemoryContext()
//        if (memoryContext.isNotBlank()) {
//            llmMessages.add(
//                LLMMessage(
//                    role = "system",
//                    content = "【项目记忆库】以下是这个项目的特殊记忆，在后续开发中请参考这些信息：\n\n$memoryContext\n\n请在发现项目特有的配置、架构约定、特殊依赖或代码规范时，主动调用 save_memory 工具保存到记忆库中。"
//                )
//            )
//        }

        // 2.3 添加历史消息
        llmMessages.addAll(chatMessageVos.mapIndexed { index, msg ->
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
        })


        // 工具定义
        // 使用注册表自动加载工具
        // 工具定义
        val tools = ToolRegistry.allTools
            .filter { !it.isWriteTool || chatMode == ChatPattern.执行模式 }
            .map { it.toGenericDefinition() }

        currentJob = ioScope.launch {
            // 用于累计最终显示的文本
            val contentBuilder = StringBuilder()
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
                Log.error("LLM 调用产生异常",e)
                // 显示错误弹窗
                errorMessage = e.message ?: "未知错误"
                showErrorDialog = true
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
            content = "思考中...",
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
