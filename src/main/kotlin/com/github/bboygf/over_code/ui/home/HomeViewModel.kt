package com.github.bboygf.over_code.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.bboygf.over_code.llm.LLMService
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import com.github.bboygf.over_code.utils.ImageUtils
import com.github.bboygf.over_code.utils.ProjectFileUtils
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put


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
        if (userInput.isBlank() && selectedImageBase64List.isEmpty() || isLoading) return

        isLoading = true
        val currentImageList = selectedImageBase64List.toList()
        selectedImageBase64List.clear()

        // 1. 处理用户消息
        val userMessage = ChatMessage(
            id = System.currentTimeMillis().toString(),
            content = userInput,
            isUser = true
        )
        // 优化：简化 List 更新逻辑
        messages = messages + userMessage
        dbService?.saveMessage(userMessage, currentSessionId)

        // 2. 构建 LLM 历史 (注意：此时 UI 上的 messages 已经包含了 userMessage)
        val llmHistory = messages.mapIndexed { index, msg ->
            LLMMessage(
                role = if (msg.isUser) "user" else "assistant",
                content = msg.content,
                // 只给最后一条用户消息附带图片
                images = if (msg.isUser && index == messages.size - 1) currentImageList else emptyList()
            )
        }.toMutableList()

        // 3. 创建 AI 消息占位符
        val aiMessageId = (System.currentTimeMillis() + 1).toString()
        val aiMessage = ChatMessage(
            id = aiMessageId,
            content = "...",
            isUser = false
        )
        messages = messages + aiMessage

        // 工具定义 (保持不变)
        val tools = listOf(
            GeminiTool(
                functionDeclarations = listOf(
                    GeminiFunctionDeclaration(
                        name = "get_project_info",
                        description = "读取当前项目的完整文件结构和内容。",
                        parameters = buildJsonObject {
                            put("type", "object")
                            put("properties", buildJsonObject {})
                        }
                    )
                )
            )
        )

        ioScope.launch {
            // 用于累计最终显示的文本
            val contentBuilder = StringBuilder()
            var errorOccurred = false

            try {
                processChatTurn(
                    history = llmHistory,
                    tools = tools,
                    aiMessageId = aiMessageId,
                    contentBuilder = contentBuilder,
                    onScrollToBottom = onScrollToBottom
                )
            } catch (e: Exception) {
                e.printStackTrace()
                errorOccurred = true
                contentBuilder.append("\n[系统错误: ${e.message}]")
                updateUiMessage(aiMessageId, contentBuilder.toString())
            } finally {
                // 4. 【关键修复】无论成功还是失败，只要有内容生成，就保存到数据库
                val finalContent = contentBuilder.toString()
                if (finalContent.isNotBlank() && finalContent != "...") {
                    dbService?.saveMessage(
                        ChatMessage(
                            id = aiMessageId,
                            content = finalContent,
                            isUser = false
                        ),
                        currentSessionId
                    )

                    // 额外保险：确保 UI 状态最终一致
                    updateUiMessage(aiMessageId, finalContent)
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
     * 核心递归函数：处理 对话 -> 工具调用 -> 再次对话 的循环
     */
     private suspend fun processChatTurn(
        history: MutableList<LLMMessage>,
        tools: List<GeminiTool>,
        aiMessageId: String,
        contentBuilder: StringBuilder, // 用于 UI 显示（累积所有文本）
        onScrollToBottom: () -> Unit
    ) {
        var part: GeminiPart? = null
        // 关键修复：新增一个 StringBuilder 仅记录"当前这一轮"的文本，用于存入 history
        val currentTurnText = StringBuilder()

        val service = llmService ?: run {
            updateUiMessage(aiMessageId, "未配置模型")
            return
        }

        // 调用流式 API
        service.chatStream(
            messages = history,
            tools = tools,
            onChunk = { chunk ->
                // 1. 同时追加到总显示文本 和 当前轮次历史文本
                contentBuilder.append(chunk)
                currentTurnText.append(chunk)

                // 2. 实时更新 UI
                updateUiMessage(aiMessageId, contentBuilder.toString())
                uiScope.launch { onScrollToBottom() }
            },
            onToolCall = { call ->
                part = call
            },
        )

        // API 这一轮结束
        if (part != null) {
            val call = part!!

            // UI 提示（可选，不写入 contentBuilder 以免污染最终结果，或者你可以选择写入）
            // updateUiMessage(aiMessageId, "${contentBuilder}\n\n(正在执行操作: ${call.name}...)")

            // 3. 【关键修复】将这一轮 AI 的思考文本(currentTurnText)和工具调用意图一起加入历史
            history.add(LLMMessage(
                role = "assistant",
                content = currentTurnText.toString(), // 修复：带上刚才生成的思考文本
                functionCall = call.functionCall,
                thoughtSignature=call.thoughtSignature
            ))

            // 4. 执行本地代码
            val toolResultContent = if (call.functionCall!!.name == "get_project_info") {
                try {
                     ProjectFileUtils.exportToMarkdown(project!!)
                } catch (e: Exception) {
                    "读取项目失败: ${e.message}"
                }
            } else {
                "Unknown function: ${call.functionCall.name}"
            }

            // 5. 将“执行结果”加入历史
            history.add(LLMMessage(
                role = "function",
                name = call.functionCall.name,
                content = toolResultContent
            ))
            updateUiMessage(aiMessageId, toolResultContent);
            // 6. 【递归】
            processChatTurn(
                history = history,
                tools = tools,
                aiMessageId = aiMessageId,
                contentBuilder = contentBuilder,
                onScrollToBottom = onScrollToBottom
            )
        }
    }

    // 辅助方法：在 UI 线程更新消息
    private fun updateUiMessage(id: String, newContent: String) {
        uiScope.launch {
            messages = messages.map { msg ->
                if (msg.id == id) {
                    msg.copy(content = newContent)
                } else {
                    msg
                }
            }
        }
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