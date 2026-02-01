package com.github.bboygf.over_code.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.bboygf.over_code.enums.ChatPattern
import com.github.bboygf.over_code.enums.ChatRole
import com.github.bboygf.over_code.llm.LLMService
import com.github.bboygf.over_code.po.GeminiFunctionCall
import com.github.bboygf.over_code.po.GeminiFunctionDeclaration
import com.github.bboygf.over_code.po.GeminiPart
import com.github.bboygf.over_code.po.GeminiTool
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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject


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
    var chatMessages by mutableStateOf(listOf<ChatMessage>())
        private set

    var currentSessionId by mutableStateOf("default")
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
        currentSessionId = sessionId
        chatMessages = dbService?.loadMessages(sessionId) ?: emptyList()
    }

    /**
     * 创建新会话
     */
    fun createNewSession() {
        dbService?.let {
            currentSessionId = it.createSession()
            chatMessages = emptyList()
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
            chatMessages = emptyList()
            currentSessionId = "default"
        }
    }

    /**
     * 清空当前会话
     */
    fun clearCurrentSession() {
        dbService?.clearMessages(currentSessionId)
        chatMessages = emptyList()
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
        val userMessage = ChatMessage(
            id = System.currentTimeMillis().toString(),
            content = userInput,
            chatRole = ChatRole.用户
        )
        // 优化：简化 List 更新逻辑
        chatMessages = chatMessages + userMessage
        dbService?.saveMessage(userMessage, currentSessionId)

        // 2. 构建 LLM 历史 (注意：此时 UI 上的 messages 已经包含了 userMessage)
        val llmMessages = chatMessages.mapIndexed { index, msg ->

            LLMMessage(
                role = msg.chatRole.role,
                content = msg.content,
                // 只给最后一条用户消息附带图片
                images = if (msg.chatRole == ChatRole.用户 && index == chatMessages.size - 1) currentImageList else emptyList()
            )
        }.toMutableList()

        // 工具定义
        val tools = mutableListOf(
            GeminiFunctionDeclaration(
                name = "get_project_info",
                description = "读取当前项目的完整文件结构和内容。",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {})
                }
            ),
            GeminiFunctionDeclaration(
                name = "read_file_content",
                description = "根据文件的绝对路径读取文件内容",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        putJsonObject("absolutePath") {
                            put("type", "string")
                            put("description", "文件的绝对路径")
                        }
                    })
                }
            ),
            GeminiFunctionDeclaration(
                name = "get_file_fun_info",
                description = "根据文件的绝对路径获取文件内所有方法详情并返回方法的行号信息",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        putJsonObject("filePath") {
                            put("type", "string")
                            put("description", "文件的绝对路径")
                        }
                    })
                }
            ),
            GeminiFunctionDeclaration(
                name = "get_method_detail",
                description = "根据文件的绝对路径和方法名获取特定方法的详情及起始终止行号",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        putJsonObject("fileName") {
                            put("type", "string")
                            put("description", "文件的绝对路径")
                        }
                        putJsonObject("methodName") {
                            put("type", "string") // 建议直接用 boolean 类型
                            put("description", "要查找的方法名")
                        }
                    })
                }
            ),
            GeminiFunctionDeclaration(
                name = "find_methods_by_name",
                description = "根据方法名在项目中查找其所属的类、文件路径和行号",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        putJsonObject("methodName") {
                            put("type", "string")
                            put("description", "方法名")
                        }
                    })
                }
            ),
            GeminiFunctionDeclaration(
                name = "review_code_by_file",
                description = "检查文件是否有爆红，代码格式是否异常，并直接返回 Markdown 格式的报告",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        putJsonObject("filePath") {
                            put("type", "string")
                            put("description", "文件绝对路径")
                        }
                    })
                }
            )
        )
        if (chatMode == ChatPattern.执行模式) {
            Log.info("执行模式！")
            val writeTools: List<GeminiFunctionDeclaration> = listOf(
                GeminiFunctionDeclaration(
                    name = "create_file_or_dir",
                    description = "根据文件绝对路径创建文件或者目录",
                    parameters = buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {
                            putJsonObject("absolutePath") {
                                put("type", "string")
                                put("description", "文件的绝对路径")
                            }
                            putJsonObject("isDirectory") {
                                put("type", "boolean") // 建议直接用 boolean 类型
                                put("description", "是否为目录，true表示目录，false表示文件")
                            }
                        })
                    }
                ),
                GeminiFunctionDeclaration(
                    name = "replace_code_by_line",
                    description = "根据文件名、起始行号、终止行号替换文件内容。",
                    parameters = buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {
                            putJsonObject("filePath") {
                                put("type", "string")
                                put("description", "文件的绝对路径")
                            }
                            putJsonObject("startLine") {
                                put("type", "integer")
                                put("description", "起始行号 (从 1 开始)")
                            }
                            putJsonObject("endLine") {
                                put("type", "integer")
                                put("description", "结束行号 (从 1 开始)")
                            }
                            putJsonObject("newCodeString") {
                                put("type", "string")
                                put("description", "新的代码")
                            }
                        })
                    }
                ),
                GeminiFunctionDeclaration(
                    name = "delete_file",
                    description = "根据绝对路径删除文件或目录",
                    parameters = buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {
                            putJsonObject("absolutePath") {
                                put("type", "string")
                                put("description", "文件的绝对路径")
                            }
                        })
                    }
                ))
            tools.addAll(writeTools)
        }
        val geminiTools = listOf(GeminiTool(tools))
        currentJob = ioScope.launch {
            // 用于累计最终显示的文本
            val contentBuilder = StringBuilder()
            var errorOccurred = false
            try {
                processChatTurn(
                    history = llmMessages,
                    tools = geminiTools,
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
     * 从外部（如Action）发送消息到聊天窗口
     * 此方法不需要 onScrollToBottom 回调
     */
    fun sendMessageFromExternal(userInput: String) {
        sendMessage(userInput) {
            // 空回调，因为从外部调用时可能无法访问UI的滚动状态
        }
        ioScope.launch {
            // 发送信号：滚动到最后一条（索引 0 或 messages.size - 1）
            _scrollEvents.emit(chatMessages.size - 1)
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
        val call: GeminiPart,
        val result: String
    )

    /**
     * 执行工具调用
     */
    private fun executeTool(functionCall: GeminiFunctionCall): String {
        val args = functionCall.args
        return when (functionCall.name) {
            "get_project_info" -> try {
                ProjectFileUtils.exportToMarkdown(project!!)
            } catch (e: Exception) {
                "读取项目失败: ${e.message}"
            }
            "create_file_or_dir" -> {
                val path = args["absolutePath"]?.jsonPrimitive?.content ?: ""
                val isDir = args["isDirectory"]?.jsonPrimitive?.booleanOrNull ?: false
                val createFileOrDir = ProjectFileUtils.createFileOrDir(project!!, path, isDir)
                if (createFileOrDir == null || !createFileOrDir.exists()) {
                    "创建失败！"
                } else {
                    "创建成功！"
                }
            }
            "read_file_content" -> {
                val path = args["absolutePath"]?.jsonPrimitive?.content ?: ""
                ProjectFileUtils.readFileContent(path) ?: "null"
            }
            "delete_file" -> {
                val path = args["absolutePath"]?.jsonPrimitive?.content ?: ""
                ProjectFileUtils.deleteFile(project!!, path)
            }
            "get_file_fun_info" -> {
                val filePath = args["filePath"]?.jsonPrimitive?.content ?: ""
                ProjectFileUtils.getFileFunInfo(project!!, filePath)
            }
            "get_method_detail" -> {
                val fileName = args["fileName"]?.jsonPrimitive?.content ?: ""
                val methodName = args["methodName"]?.jsonPrimitive?.content ?: ""
                ProjectFileUtils.getMethodDetail(project!!, fileName, methodName)
            }
            "replace_code_by_line" -> {
                val filePath = args["filePath"]?.jsonPrimitive?.content ?: ""
                val startOffset = args["startLine"]?.jsonPrimitive?.int ?: 0
                val endOffset = args["endLine"]?.jsonPrimitive?.int ?: 0
                val newCodeString = args["newCodeString"]?.jsonPrimitive?.content ?: ""
                ProjectFileUtils.replaceCodeByLine(project!!, filePath, startOffset, endOffset, newCodeString)
            }
            "find_methods_by_name" -> {
                val methodName = args["methodName"]?.jsonPrimitive?.content ?: ""
                ProjectFileUtils.findMethodsByName(project!!, methodName)
            }
            "review_code_by_file" -> {
                val filePath = args["filePath"]?.jsonPrimitive?.content ?: ""
                ProjectFileUtils.reviewCodeByFile(project!!, filePath)
            }
            else -> "Unknown function: ${functionCall.name}"
        }
    }

    /**
     * 核心递归函数：处理 对话 -> 工具调用 -> 再次对话 的循环
     * 支持并行函数调用：并行执行所有工具，然后将结果一起返回给模型
     */
    private suspend fun processChatTurn(
        history: MutableList<LLMMessage>,
        tools: List<GeminiTool>,
        contentBuilder: StringBuilder,
        onScrollToBottom: () -> Unit
    ) {
        val partList = CopyOnWriteArrayList<GeminiPart>()
        val currentTurnText = StringBuilder()
        val currentThought = StringBuilder()

        val service = llmService ?: run {
            println("请先设置模型！")
            return
        }

        // 调用流式 API
        service.chatStream(
            messages = history,
            tools = tools,
            onChunk = { chunk ->
                if (chunk.isNotEmpty()) {
                    contentBuilder.append(chunk)
                    currentTurnText.append(chunk)
                    onScrollToBottom()
                }
            },
            onToolCall = { call ->
                partList.add(call)
            },
            onThought = { thought ->
                currentThought.append(thought)
            },
        )

        // 如果没有工具调用，直接返回
        if (partList.isEmpty()) {
            // 即使没有工具调用，也需要保存 AI 的文本回复
            val responseText = contentBuilder.toString()
            if (responseText.isNotEmpty()) {
                val aiMessage = ChatMessage(
                    id = System.currentTimeMillis().toString(),
                    content = responseText,
                    chatRole = ChatRole.助理,
                    thought = currentThought.toString().takeIf { it.isNotEmpty() }
                )
                chatMessages = chatMessages + aiMessage
                dbService?.saveMessage(aiMessage, currentSessionId)
                uiScope.launch { onScrollToBottom() }
            }
            return
        }

        // 显示 AI 正在调用工具的提示
        val toolCallDescription = partList.map {
            "${it.functionCall?.name}: ${it.functionCall?.args}"
        }.joinToString("\n")
        val assistantMessage = ChatMessage(
            id = System.currentTimeMillis().toString(),
            content = "并行调用工具:\n$toolCallDescription",
            chatRole = ChatRole.助理,
            thought = currentThought.toString().takeIf { it.isNotEmpty() }
        )
        chatMessages = chatMessages + assistantMessage
        dbService?.saveMessage(assistantMessage)
        uiScope.launch { onScrollToBottom() }

        // 【关键修改】并行执行所有工具调用
        val toolResults = ioScope.async {
            partList.map { call ->
                async(Dispatchers.IO) {
                    val result = executeTool(call.functionCall!!)
                    ToolExecutionResult(call, result)
                }
            }.awaitAll()
        }.await()

        // 从 toolCalls 中提取 thoughtSignature（取第一个非空的）
        val currentThoughtSignature = partList.firstOrNull { it.thoughtSignature != null }?.thoughtSignature

        // 将批量函数调用添加到历史，保留每个 part 的 thoughtSignature
        history.add(
            LLMMessage(
                role = "assistant",
                content = currentTurnText.toString(),
                parts = partList.toList(),
                thought = currentThought.toString().takeIf { it.isNotEmpty() },
                thoughtSignature = currentThoughtSignature
            )
        )

        // 将所有工具结果一起添加到历史
        toolResults.forEach { result ->
            history.add(
                LLMMessage(
                    role = "function",
                    name = result.call.functionCall!!.name,
                    content = result.result
                )
            )

            // 保存工具结果到 UI
            val toolsMessage = ChatMessage(
                id = System.currentTimeMillis().toString(),
                content = result.result,
                chatRole = ChatRole.工具
            )
            chatMessages = chatMessages + toolsMessage
            dbService?.saveMessage(toolsMessage, currentSessionId)
            uiScope.launch { onScrollToBottom() }
        }

        // 【关键修改】一次性递归，让模型看到所有结果后再决定下一步
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