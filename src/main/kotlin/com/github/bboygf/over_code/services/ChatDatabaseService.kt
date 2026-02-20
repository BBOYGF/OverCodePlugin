package com.github.bboygf.over_code.services

import com.github.bboygf.over_code.po.ChatMessages
import com.github.bboygf.over_code.po.ChatSessions
import com.github.bboygf.over_code.po.Memory
import com.github.bboygf.over_code.po.ModelConfigs
import com.github.bboygf.over_code.po.OtherConfigs
import com.github.bboygf.over_code.po.PromptTemplates
import com.github.bboygf.over_code.vo.ChatMessageVo
import com.github.bboygf.over_code.vo.MemoryVo
import com.github.bboygf.over_code.vo.ModelConfigInfo
import com.github.bboygf.over_code.vo.PromptInfo
import com.github.bboygf.over_code.vo.SessionInfo
import com.github.bboygf.over_code.po.LlmToolCall
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File


/**
 * 聊天数据库服务
 */
@Service(Service.Level.PROJECT)
class ChatDatabaseService(private val project: Project) {

    private val database: Database

    init {
        // 获取插件数据目录
        val pluginDataDir = File(project.basePath + "/.idea", ".over_code")
        if (!pluginDataDir.exists()) {
            pluginDataDir.mkdirs()
        }

        // 数据库文件路径
        val dbFile = File(pluginDataDir, "over_code.db")

        // 连接 SQLite 数据库
        database = Database.connect(
            url = "jdbc:sqlite:${dbFile.absolutePath}",
            driver = "org.sqlite.JDBC"
        )

        // 初始化数据库表（自动创建表和新增列）
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(
                ChatMessages, ChatSessions, ModelConfigs, PromptTemplates,
                OtherConfigs, Memory
            )
            initializeDefaultPrompts()
        }
    }

    /**
     * 保存消息到数据库
     */
    fun saveMessage(message: ChatMessageVo, sessionId: String = "default") {
        transaction(database) {
            ChatMessages.insert {
                it[messageId] = message.id
                it[content] = message.content
                it[chatRole] = message.chatRole
                it[timestamp] = message.timestamp
                it[ChatMessages.sessionId] = sessionId
                it[thought] = message.thought
                it[toolCalls] = message.toolCalls?.let { calls -> Json.encodeToString(calls) }
                it[toolCallId] = message.toolCallId
                it[thoughtSignature] = message.thoughtSignature
                it[images] = if (message.images.isNotEmpty()) Json.encodeToString(message.images) else null
            }

            updateSessionTimestamp(sessionId)
        }
    }

    /**
     * 批量保存消息
     */
    fun saveMessages(messages: List<ChatMessageVo>, sessionId: String = "default") {
        transaction(database) {
            messages.forEach { message ->
                ChatMessages.insert {
                    it[messageId] = message.id
                    it[content] = message.content
                    it[chatRole] = message.chatRole
                    it[timestamp] = message.timestamp
                    it[ChatMessages.sessionId] = sessionId
                    it[thought] = message.thought
                    it[toolCalls] = message.toolCalls?.let { calls -> Json.encodeToString(calls) }
                    it[toolCallId] = message.toolCallId
                    it[thoughtSignature] = message.thoughtSignature
                    it[images] = if (message.images.isNotEmpty()) Json.encodeToString(message.images) else null
                }
            }
            updateSessionTimestamp(sessionId)
        }
    }

    /**
     * 加载指定会话的所有消息
     */
    fun loadMessages(sessionId: String = "default"): List<ChatMessageVo> {
        return transaction(database) {
            ChatMessages.selectAll()
                .where { ChatMessages.sessionId eq sessionId }
                .orderBy(ChatMessages.timestamp to SortOrder.ASC)
                .map { row ->
                    val toolCallsJson = row[ChatMessages.toolCalls]
                    val imagesJson = row[ChatMessages.images]

                    ChatMessageVo(
                        id = row[ChatMessages.messageId],
                        content = row[ChatMessages.content],
                        chatRole = row[ChatMessages.chatRole],
                        timestamp = row[ChatMessages.timestamp],
                        thought = row[ChatMessages.thought],
                        toolCalls = toolCallsJson?.let { Json.decodeFromString<List<LlmToolCall>>(it) },
                        toolCallId = row[ChatMessages.toolCallId],
                        thoughtSignature = row[ChatMessages.thoughtSignature],
                        images = imagesJson?.let { Json.decodeFromString<List<String>>(it) } ?: emptyList()
                    )
                }
        }
    }

    /**
     * 删除指定会话的所有消息
     */
    fun clearMessages(sessionId: String = "default") {
        transaction(database) {
            ChatMessages.deleteWhere { ChatMessages.sessionId eq sessionId }
        }
    }

    /**
     * 创建新会话
     */
    fun createSession(sessionId: String = generateSessionId(), title: String = "新对话"): String {
        transaction(database) {
            val now = System.currentTimeMillis()
            ChatSessions.insert {
                it[ChatSessions.sessionId] = sessionId
                it[ChatSessions.title] = title
                it[createdAt] = now
                it[updatedAt] = now
            }
        }
        return sessionId
    }

    fun getSessionBySessionId(sessionId: String): SessionInfo? {
        return transaction(database) {
            ChatSessions.selectAll()
                .filter { it[ChatSessions.sessionId] == sessionId }
                .map { row ->
                    SessionInfo(
                        sessionId = row[ChatSessions.sessionId],
                        title = row[ChatSessions.title],
                        createdAt = row[ChatSessions.createdAt],
                        updatedAt = row[ChatSessions.updatedAt]
                    )
                }
                .firstOrNull()
        }
    }


    /**
     * 获取所有会话
     */
    fun loadSessions(): List<SessionInfo> {
        return transaction(database) {
            ChatSessions.selectAll()
                .orderBy(ChatSessions.updatedAt to SortOrder.DESC)
                .map { row ->
                    SessionInfo(
                        sessionId = row[ChatSessions.sessionId],
                        title = row[ChatSessions.title],
                        createdAt = row[ChatSessions.createdAt],
                        updatedAt = row[ChatSessions.updatedAt]
                    )
                }
        }
    }

    /**
     * 更新会话标题
     */
    fun updateSessionTitle(sessionId: String, newTitle: String) {
        transaction(database) {
            ChatSessions.update({ ChatSessions.sessionId eq sessionId }) {
                it[title] = newTitle
                it[updatedAt] = System.currentTimeMillis()
            }
        }
    }

    /**
     * 删除会话及其所有消息
     */
    fun deleteSession(sessionId: String) {
        transaction(database) {
            ChatMessages.deleteWhere { ChatMessages.sessionId eq sessionId }
            ChatSessions.deleteWhere { ChatSessions.sessionId eq sessionId }
        }
    }

    /**
     * 更新会话时间戳
     */
    private fun updateSessionTimestamp(sessionId: String) {
        val exists = ChatSessions.selectAll()
            .where { ChatSessions.sessionId eq sessionId }
            .count() > 0

        if (exists) {
            ChatSessions.update({ ChatSessions.sessionId eq sessionId }) {
                it[updatedAt] = System.currentTimeMillis()
            }
        } else {
            // 如果会话不存在，创建它
            val now = System.currentTimeMillis()
            ChatSessions.insert {
                it[ChatSessions.sessionId] = sessionId
                it[title] = "新对话"
                it[createdAt] = now
                it[updatedAt] = now
            }
        }
    }

    /**
     * 生成唯一会话 ID
     */
    private fun generateSessionId(): String {
        return "session_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }

// ==================== 模型管理 ====================

    /**
     * 添加模型配置
     */
    fun addModelConfig(config: ModelConfigInfo): String {
        val modelId = generateModelId()
        transaction(database) {
            // 如果设置为当前模型，先取消其他模型的激活状态
            if (config.isActive) {
                ModelConfigs.update({ ModelConfigs.isActive eq true }) {
                    it[isActive] = false
                }
            }

            val now = System.currentTimeMillis()
            ModelConfigs.insert {
                it[ModelConfigs.modelId] = modelId
                it[name] = config.name
                it[provider] = config.provider
                it[baseUrl] = config.baseUrl
                it[apiKey] = config.apiKey
                it[modelName] = config.modelName
                it[isActive] = config.isActive
                it[createdAt] = now
                it[updatedAt] = now
                it[useProxy] = config.useProxy
            }
        }
        return modelId
    }

    /**
     * 更新模型配置
     */
    fun updateModelConfig(modelId: String, config: ModelConfigInfo) {
        transaction(database) {
            // 如果设置为当前模型，先取消其他模型的激活状态
            if (config.isActive) {
                ModelConfigs.update({ ModelConfigs.isActive eq true }) {
                    it[isActive] = false
                }
            }

            ModelConfigs.update({ ModelConfigs.modelId eq modelId }) {
                it[name] = config.name
                it[provider] = config.provider
                it[baseUrl] = config.baseUrl
                it[apiKey] = config.apiKey
                it[modelName] = config.modelName
                it[isActive] = config.isActive
                it[updatedAt] = System.currentTimeMillis()
                it[useProxy] = config.useProxy
            }
        }
    }

    /**
     * 删除模型配置
     */
    fun deleteModelConfig(modelId: String) {
        transaction(database) {
            ModelConfigs.deleteWhere { ModelConfigs.modelId eq modelId }
        }
    }

    /**
     * 获取所有模型配置
     */
    fun getAllModelConfigs(): List<ModelConfigInfo> {
        return transaction(database) {
            ModelConfigs.selectAll()
                .orderBy(ModelConfigs.createdAt to SortOrder.DESC)
                .map { row ->
                    ModelConfigInfo(
                        modelId = row[ModelConfigs.modelId],
                        name = row[ModelConfigs.name],
                        provider = row[ModelConfigs.provider],
                        baseUrl = row[ModelConfigs.baseUrl],
                        apiKey = row[ModelConfigs.apiKey],
                        modelName = row[ModelConfigs.modelName],
                        isActive = row[ModelConfigs.isActive],
                        createdAt = row[ModelConfigs.createdAt],
                        updatedAt = row[ModelConfigs.updatedAt],
                        useProxy = row[ModelConfigs.useProxy]
                    )
                }
        }
    }

    /**
     * 获取当前激活的模型配置
     */
    fun getActiveModelConfig(): ModelConfigInfo? {
        return transaction(database) {
            ModelConfigs.selectAll()
                .where { ModelConfigs.isActive eq true }
                .limit(1)
                .firstOrNull()
                ?.let { row ->
                    ModelConfigInfo(
                        modelId = row[ModelConfigs.modelId],
                        name = row[ModelConfigs.name],
                        provider = row[ModelConfigs.provider],
                        baseUrl = row[ModelConfigs.baseUrl],
                        apiKey = row[ModelConfigs.apiKey],
                        modelName = row[ModelConfigs.modelName],
                        isActive = row[ModelConfigs.isActive],
                        createdAt = row[ModelConfigs.createdAt],
                        updatedAt = row[ModelConfigs.updatedAt],
                        useProxy = row[ModelConfigs.useProxy]
                    )
                }
        }
    }

    /**
     * 设置当前激活的模型
     */
    fun setActiveModel(modelId: String) {
        transaction(database) {
            // 取消所有模型的激活状态
            ModelConfigs.update({ ModelConfigs.isActive eq true }) {
                it[isActive] = false
            }

            // 设置指定模型为激活
            ModelConfigs.update({ ModelConfigs.modelId eq modelId }) {
                it[isActive] = true
                it[updatedAt] = System.currentTimeMillis()
            }
        }
    }

    /**
     * 生成唯一模型 ID
     */
    private fun generateModelId(): String {
        return "model_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }

// ==================== Prompt 模板管理 ====================

    /**
     * 初始化默认 Prompt 模板
     */
    private fun initializeDefaultPrompts() {
        val existingPrompts = PromptTemplates.selectAll().count()
        if (existingPrompts > 0) return

        val defaultPrompts = listOf(
            PromptInfo(
                key = "explain_code",
                name = "解释代码",
                template = """请帮我解释一下这段代码的意思：

文件名：{{fileName}}
语言：{{language}}

```{{language}}
{{code}}
```""",
                description = "用于解释选中的代码片段",
                isBuiltIn = true
            ),
            PromptInfo(
                key = "translate_chat",
                name = "翻译（聊天窗口）",
                template = """请帮我将以下内容翻译成中文。如果内容是代码，请解释代码的含义并翻译其中的注释或字符串：
{{text}}""",
                description = "将选中内容翻译并在聊天窗口显示",
                isBuiltIn = true
            ),
            PromptInfo(
                key = "translate_quick",
                name = "快速翻译（弹窗）",
                template = "你是一个专业的翻译助手。请直接输出以下内容的中文翻译结果，不需要任何开场白或解释：\n\n{{text}}",
                description = "快速翻译并以弹窗形式显示结果",
                isBuiltIn = true
            ),
            PromptInfo(
                key = "refactor_code",
                name = "重构代码",
                template = """请帮我重构以下代码，使其更加简洁、高效和易于维护：

```{{language}}
{{code}}
```

请提供重构后的代码和改进说明。""",
                description = "重构代码使其更优雅",
                isBuiltIn = false
            ),
            PromptInfo(
                key = "find_bugs",
                name = "查找Bug",
                template = """请仔细检查以下代码，找出可能存在的 Bug、性能问题或潜在风险：

```{{language}}
{{code}}
```

请列出发现的问题和修复建议。""",
                description = "检查代码中的潜在问题",
                isBuiltIn = false
            ),
            PromptInfo(
                key = "add_comments",
                name = "添加注释",
                template = """请为以下代码添加详细的注释，包括函数说明、参数说明和关键逻辑说明：

```{{language}}
{{code}}
```

请返回带注释的完整代码。""",
                description = "为代码添加详细注释",
                isBuiltIn = false
            ),
            PromptInfo(
                key = "generate_tests",
                name = "生成单元测试",
                template = """请为以下代码生成完整的单元测试：

```{{language}}
{{code}}
```

请包含正常场景、边界情况和异常处理的测试用例。""",
                description = "自动生成单元测试代码",
                isBuiltIn = false
            )
        )

        defaultPrompts.forEach { prompt ->
            addPromptTemplate(prompt)
        }
    }

    /**
     * 添加 Prompt 模板
     */
    fun addPromptTemplate(prompt: PromptInfo): String {
        val promptId = prompt.promptId.ifEmpty { generatePromptId() }
        transaction(database) {
            val now = System.currentTimeMillis()
            PromptTemplates.insert {
                it[PromptTemplates.promptId] = promptId
                it[name] = prompt.name
                it[key] = prompt.key
                it[template] = prompt.template
                it[description] = prompt.description
                it[isBuiltIn] = prompt.isBuiltIn
                it[createdAt] = now
                it[updatedAt] = now
            }
        }
        return promptId
    }

    /**
     * 更新 Prompt 模板
     */
    fun updatePromptTemplate(promptId: String, prompt: PromptInfo) {
        transaction(database) {
            PromptTemplates.update({ PromptTemplates.promptId eq promptId }) {
                it[name] = prompt.name
                it[key] = prompt.key
                it[template] = prompt.template
                it[description] = prompt.description
                it[updatedAt] = System.currentTimeMillis()
            }
        }
    }

    /**
     * 删除 Prompt 模板
     */
    fun deletePromptTemplate(promptId: String) {
        transaction(database) {
            PromptTemplates.deleteWhere { PromptTemplates.promptId eq promptId }
        }
    }

    /**
     * 获取所有 Prompt 模板
     */
    fun getAllPromptTemplates(): List<PromptInfo> {
        return transaction(database) {
            PromptTemplates.selectAll()
                .orderBy(PromptTemplates.createdAt to SortOrder.ASC)
                .map { row ->
                    PromptInfo(
                        promptId = row[PromptTemplates.promptId],
                        name = row[PromptTemplates.name],
                        key = row[PromptTemplates.key],
                        template = row[PromptTemplates.template],
                        description = row[PromptTemplates.description],
                        isBuiltIn = row[PromptTemplates.isBuiltIn],
                        createdAt = row[PromptTemplates.createdAt],
                        updatedAt = row[PromptTemplates.updatedAt]
                    )
                }
        }
    }

    /**
     * 根据 Key 获取 Prompt 模板
     */
    fun getPromptTemplateByKey(key: String): PromptInfo? {
        return transaction(database) {
            PromptTemplates.selectAll()
                .where { PromptTemplates.key eq key }
                .limit(1)
                .firstOrNull()
                ?.let { row ->
                    PromptInfo(
                        promptId = row[PromptTemplates.promptId],
                        name = row[PromptTemplates.name],
                        key = row[PromptTemplates.key],
                        template = row[PromptTemplates.template],
                        description = row[PromptTemplates.description],
                        isBuiltIn = row[PromptTemplates.isBuiltIn],
                        createdAt = row[PromptTemplates.createdAt],
                        updatedAt = row[PromptTemplates.updatedAt]
                    )
                }
        }
    }

    // 1. 获取值 (根据 key 查询 value)
    fun getValue(targetKey: String): String? {
        return transaction {
            // 查询 key 等于 targetKey 的记录
            OtherConfigs
                .selectAll().where { OtherConfigs.key eq targetKey }
                .singleOrNull() // 取第一条，如果没有则返回 null
                ?.get(OtherConfigs.value) // 从记录中取出 value 列的值
        }
    }

    /**
     * add Key Value
     */
// 2. 添加或更新值 (Upsert)
    fun addOrUpdateValue(targetKey: String, targetValue: String) {
        transaction {
            // 尝试先查询是否存在
            val existing = OtherConfigs.selectAll().where { OtherConfigs.key eq targetKey }.count()

            if (existing > 0) {
                // 如果存在，则更新
                OtherConfigs.update({ OtherConfigs.key eq targetKey }) {
                    // 注意：这里 it[OtherConfigs.value] 代表列，targetValue 代表参数
                    it[OtherConfigs.value] = targetValue
                }
            } else {
                // 如果不存在，则插入
                OtherConfigs.insert {
                    it[OtherConfigs.key] = targetKey
                    it[OtherConfigs.value] = targetValue
                }
            }
        }
    }


    /**
     * 渲染 Prompt 模板（替换占位符）
     */
    fun renderPrompt(key: String, variables: Map<String, String>): String {
        val template = getPromptTemplateByKey(key)?.template
            ?: throw IllegalArgumentException("Prompt template not found: $key")

        var result = template
        variables.forEach { (varKey, value) ->
            result = result.replace("{{$varKey}}", value)
        }
        return result
    }

    // ==================== 记忆管理 ====================

    /**
     * 获取所有记忆概要
     * 返回所有记忆的 ID 和 summary，用于列表展示
     */
    fun getAllMemorySummaries(): List<MemoryVo> {
        return transaction(database) {
            Memory.selectAll()
                .orderBy(Memory.updatedAt to SortOrder.DESC)
                .map { row ->
                    MemoryVo(
                        memoryId = row[Memory.memoryId],
                        summary = row[Memory.summary],
                        content = row[Memory.content], // 不返回详情内容，减少数据传输
                        createdAt = row[Memory.createdAt],
                        updatedAt = row[Memory.updatedAt]
                    )
                }
        }
    }

    /**
     * 根据记忆概要获取记忆详情
     * @param summary 经验概要，用于查找对应的记忆详情
     * @return 匹配的记忆详情，如果找不到返回 null
     */
    fun getMemoryDetailBySummary(summary: String): MemoryVo? {
        return transaction(database) {
            Memory.selectAll()
                .where { Memory.summary eq summary }
                .firstOrNull()
                ?.let { row ->
                    MemoryVo(
                        memoryId = row[Memory.memoryId],
                        summary = row[Memory.summary],
                        content = row[Memory.content],
                        createdAt = row[Memory.createdAt],
                        updatedAt = row[Memory.updatedAt]
                    )
                }
        }
    }

    /**
     * 根据记忆ID获取记忆详情
     * @param memoryId 记忆唯一标识
     * @return 记忆详情，如果找不到返回 null
     */
    fun getMemoryById(memoryId: String): MemoryVo? {
        return transaction(database) {
            Memory.selectAll()
                .where { Memory.memoryId eq memoryId }
                .firstOrNull()
                ?.let { row ->
                    MemoryVo(
                        memoryId = row[Memory.memoryId],
                        summary = row[Memory.summary],
                        content = row[Memory.content],
                        createdAt = row[Memory.createdAt],
                        updatedAt = row[Memory.updatedAt]
                    )
                }
        }
    }

    /**
     * 修改记忆概要和详情
     * @param memoryId 记忆唯一标识
     * @param summary 新的经验概要
     * @param content 新的经验详情
     * @return 是否修改成功
     */
    fun updateMemory(memoryId: String, summary: String, content: String): Boolean {
        return transaction(database) {
            val updated = Memory.update({ Memory.memoryId eq memoryId }) {
                it[Memory.summary] = summary
                it[Memory.content] = content
                it[updatedAt] = System.currentTimeMillis()
            }
            updated > 0
        }
    }

    /**
     * 添加新记忆
     */
    fun addMemory(memory: MemoryVo): String {
        val newMemoryId = generateMemoryId()
        transaction(database) {
            val now = System.currentTimeMillis()
            Memory.insert {
                it[Memory.memoryId] = newMemoryId
                it[Memory.summary] = memory.summary
                it[Memory.content] = memory.content
                it[createdAt] = now
                it[updatedAt] = now
            }
        }
        return newMemoryId
    }

    /**
     * 删除记忆
     */
    fun deleteMemory(memoryId: String) {
        transaction(database) {
            Memory.deleteWhere { Memory.memoryId eq memoryId }
        }
    }

    private fun generateMemoryId(): String = "memory_${System.currentTimeMillis()}_${(1000..9999).random()}"

    /**
     * 生成唯一 Prompt ID
     */
    private fun generatePromptId(): String {
        return "prompt_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }

    companion object {
        fun getInstance(project: Project): ChatDatabaseService {
            return project.getService(ChatDatabaseService::class.java)
        }
    }
}
