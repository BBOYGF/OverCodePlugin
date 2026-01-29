package com.github.bboygf.over_code.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.bboygf.over_code.enums.ChatPattern
import com.github.bboygf.over_code.enums.ChatRole
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
                description = "文件内容字符串，如果文件不存在则返回 null",
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
                description = "根据文件获取文件内所有方法详情并返回方法的行号信息",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        putJsonObject("fileName") {
                            put("type", "string")
                            put("description", "文件名")
                        }
                    })
                }
            ),
            GeminiFunctionDeclaration(
                name = "get_method_detail",
                description = "根据文件名和方法名获取特定方法的详情及起始终止行号",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        putJsonObject("fileName") {
                            put("type", "string")
                            put("description", "文件名")
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
                description = "检查文件是否有爆红，并直接返回 Markdown 格式的报告",
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
                    description = "根据文件名、行号替换文件内容",
                    parameters = buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {
                            putJsonObject("fileName") {
                                put("type", "string")
                                put("description", "文件名")
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
                            putJsonObject("expectedOldContent") {
                                put("type", "string")
                                put("description", "可选：AI 预期该行号区间内的旧代码。用于校验行号是否过期")
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
        ioScope.launch {
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
                e.printStackTrace()
                errorOccurred = true
                contentBuilder.append("\n[系统错误: ${e.message}]")
            } finally {
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
     * 核心递归函数：处理 对话 -> 工具调用 -> 再次对话 的循环
     */
    private suspend fun processChatTurn(
        history: MutableList<LLMMessage>,
        tools: List<GeminiTool>,
        contentBuilder: StringBuilder, // 用于 UI 显示（累积所有文本）
        onScrollToBottom: () -> Unit
    ) {
        var partList: ArrayList<GeminiPart> = ArrayList()
        // 关键修复：新增一个 StringBuilder 仅记录"当前这一轮"的文本，用于存入 history
        val currentTurnText = StringBuilder()

        val service = llmService ?: run {
            println("请先设置模型！")
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
                uiScope.launch { onScrollToBottom() }
            },
            onToolCall = { call ->
                partList += call
            },
        )
        var contentStr = contentBuilder.toString()
        if (contentStr.isEmpty() && partList.isNotEmpty()) {
            contentStr =
                "调用工具\n" + partList.map { geminiPart -> geminiPart.functionCall?.name + " 参数：" + geminiPart.functionCall?.args }
                    .joinToString("\r")
        }
        val assistantMessage = ChatMessage(
            id = System.currentTimeMillis().toString(),
            content = contentStr,
            chatRole = ChatRole.助理
        )
        // 优化：简化 List 更新逻辑
        chatMessages = chatMessages + assistantMessage
        dbService?.saveMessage(assistantMessage)
        // API 这一轮结束
        if (partList.isNotEmpty()) {
            for (call in partList) {
                // UI 提示（可选，不写入 contentBuilder 以免污染最终结果，或者你可以选择写入
                // updateUiMessage(aiMessageId, "${contentBuilder}\n\n(正在执行操作: ${call.name}...)")
                // 3. 【关键修复】将这一轮 AI 的思考文本(currentTurnText)和工具调用意图一起加入历史
                history.add(
                    LLMMessage(
                        role = "assistant",
                        content = currentTurnText.toString(), // 修复：带上刚才生成的思考文本
                        functionCall = call.functionCall,
                        thoughtSignature = call.thoughtSignature
                    )
                )

                // 4. 执行本地代码
                val toolResultContent = if (call.functionCall!!.name == "get_project_info") {
                    try {
                        ProjectFileUtils.exportToMarkdown(project!!)
                    } catch (e: Exception) {
                        "读取项目失败: ${e.message}"
                    }
                } else if (call.functionCall.name == "create_file_or_dir") {
                    val args = call.functionCall.args
                    // 安全地获取参数
                    val path = args["absolutePath"]?.jsonPrimitive?.content ?: ""
                    val isDir = args["isDirectory"]?.jsonPrimitive?.booleanOrNull ?: false
                    val createFileOrDir = ProjectFileUtils.createFileOrDir(project!!, path, isDir)
                    if (createFileOrDir == null || !createFileOrDir.exists()) {
                        "创建失败！"
                    } else {
                        "创建成功！"
                    }
                } else if (call.functionCall.name == "update_file_content") {
                    val args = call.functionCall.args
                    // 安全地获取参数
                    val path = args["absolutePath"]?.jsonPrimitive?.content ?: ""
                    val content = args["newContent"]?.jsonPrimitive?.content ?: ""
                    ProjectFileUtils.updateFileContent(project!!, path, content)
                } else if (call.functionCall.name == "read_file_content") {
                    val args = call.functionCall.args
                    // 安全地获取参数
                    val path = args["absolutePath"]?.jsonPrimitive?.content ?: ""
                    val readFileContent = ProjectFileUtils.readFileContent(path)
                    readFileContent ?: "null"
                } else if (call.functionCall.name == "delete_file") {
                    val args = call.functionCall.args
                    // 安全地获取参数
                    val path = args["absolutePath"]?.jsonPrimitive?.content ?: ""
                    val deleteFile = ProjectFileUtils.deleteFile(project!!, path)
                    deleteFile
                } else if (call.functionCall.name == "get_file_fun_info") {
                    val args = call.functionCall.args
                    // 安全地获取参数
                    val fileName = args["fileName"]?.jsonPrimitive?.content ?: ""
                    val result = ProjectFileUtils.getFileFunInfo(project!!, fileName)
                    result
                } else if (call.functionCall.name == "get_method_detail") {
                    val args = call.functionCall.args
                    // 安全地获取参数
                    val fileName = args["fileName"]?.jsonPrimitive?.content ?: ""
                    val methodName = args["methodName"]?.jsonPrimitive?.content ?: ""
                    val result = ProjectFileUtils.getMethodDetail(project!!, fileName, methodName)
                    result
                } else if (call.functionCall.name == "replace_code_by_line") {
                    val args = call.functionCall.args
                    // 安全地获取参数
                    val fileName = args["fileName"]?.jsonPrimitive?.content ?: ""
                    val startOffset = args["startLine"]?.jsonPrimitive?.int ?: 0
                    val endOffset = args["endLine"]?.jsonPrimitive?.int ?: 0
                    val newCodeString = args["newCodeString"]?.jsonPrimitive?.content ?: ""
                    val expectedOldContent = args["expectedOldContent"]?.jsonPrimitive?.content ?: ""
                    val result =
                        ProjectFileUtils.replaceCodeByLine(
                            project!!,
                            fileName,
                            startOffset,
                            endOffset,
                            newCodeString,
                            expectedOldContent
                        )
                    result
                } else if (call.functionCall.name == "find_methods_by_name") {
                    val args = call.functionCall.args
                    // 安全地获取参数
                    val methodName = args["methodName"]?.jsonPrimitive?.content ?: ""
                    val result =
                        ProjectFileUtils.findMethodsByName(project!!, methodName)
                    result
                } else if (call.functionCall.name == "review_code_by_file") {
                    val args = call.functionCall.args
                    // 安全地获取参数
                    val filePath = args["filePath"]?.jsonPrimitive?.content ?: ""
                    val result =
                        ProjectFileUtils.reviewCodeByFile(project!!, filePath)
                    result
                } else {
                    "Unknown function: ${call.functionCall.name}"
                }
                // 5. 将“执行结果”加入历史
                history.add(
                    LLMMessage(
                        role = "function",
                        name = call.functionCall.name,
                        content = toolResultContent
                    )
                )
                val toolsMessage = ChatMessage(
                    id = System.currentTimeMillis().toString(),
                    content = toolResultContent,
                    chatRole = ChatRole.工具
                )
                // 优化：简化 List 更新逻辑
                chatMessages = chatMessages + toolsMessage
                dbService?.saveMessage(toolsMessage, currentSessionId)
            }

            // 6. 【递归】
            processChatTurn(
                history = history,
                tools = tools,
                contentBuilder = contentBuilder,
                onScrollToBottom = onScrollToBottom
            )
        }
        partList.clear()
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