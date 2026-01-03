package com.github.bboygf.over_code.services

import com.github.bboygf.over_code.ui.toolWindow.ChatMessage
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

/**
 * 聊天消息数据库表定义
 */
object ChatMessages : IntIdTable("chat_messages") {
    val messageId = varchar("message_id", 50).uniqueIndex()
    val content = text("content")
    val isUser = bool("is_user")
    val timestamp = long("timestamp")
    val sessionId = varchar("session_id", 50).default("default")
}

/**
 * 聊天会话数据库表定义
 */
object ChatSessions : IntIdTable("chat_sessions") {
    val sessionId = varchar("session_id", 50).uniqueIndex()
    val title = varchar("title", 200).default("新对话")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
}

/**
 * 模型配置数据库表定义
 */
object ModelConfigs : IntIdTable("model_configs") {
    val modelId = varchar("model_id", 50).uniqueIndex()
    val name = varchar("name", 100)
    val provider = varchar("provider", 50) // "openai", "ollama", "zhipu", etc.
    val baseUrl = varchar("base_url", 500)
    val apiKey = varchar("api_key", 500).default("")
    val modelName = varchar("model_name", 100) // "gpt-3.5-turbo", "llama2", etc.
    val isActive = bool("is_active").default(false)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
}

/**
 * 聊天数据库服务
 */
@Service(Service.Level.PROJECT)
class ChatDatabaseService(private val project: Project) {
    
    private val database: Database
    
    init {
        // 获取插件数据目录
        val pluginDataDir = File(System.getProperty("user.home"), ".over_code")
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
            SchemaUtils.createMissingTablesAndColumns(ChatMessages, ChatSessions, ModelConfigs)
        }
    }
    
    /**
     * 保存消息到数据库
     */
    fun saveMessage(message: ChatMessage, sessionId: String = "default") {
        transaction(database) {
            ChatMessages.insert {
                it[messageId] = message.id
                it[content] = message.content
                it[isUser] = message.isUser
                it[timestamp] = message.timestamp
                it[ChatMessages.sessionId] = sessionId
            }
            
            // 更新会话的最后更新时间
            updateSessionTimestamp(sessionId)
        }
    }
    
    /**
     * 批量保存消息
     */
    fun saveMessages(messages: List<ChatMessage>, sessionId: String = "default") {
        transaction(database) {
            messages.forEach { message ->
                ChatMessages.insert {
                    it[messageId] = message.id
                    it[content] = message.content
                    it[isUser] = message.isUser
                    it[timestamp] = message.timestamp
                    it[ChatMessages.sessionId] = sessionId
                }
            }
            updateSessionTimestamp(sessionId)
        }
    }
    
    /**
     * 加载指定会话的所有消息
     */
    fun loadMessages(sessionId: String = "default"): List<ChatMessage> {
        return transaction(database) {
            ChatMessages.selectAll()
                .where { ChatMessages.sessionId eq sessionId }
                .orderBy(ChatMessages.timestamp to SortOrder.ASC)
                .map { row ->
                    ChatMessage(
                        id = row[ChatMessages.messageId],
                        content = row[ChatMessages.content],
                        isUser = row[ChatMessages.isUser],
                        timestamp = row[ChatMessages.timestamp]
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
                        updatedAt = row[ModelConfigs.updatedAt]
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
                        updatedAt = row[ModelConfigs.updatedAt]
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
    
    companion object {
        fun getInstance(project: Project): ChatDatabaseService {
            return project.getService(ChatDatabaseService::class.java)
        }
    }
}

/**
 * 会话信息数据类
 */
data class SessionInfo(
    val sessionId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * 模型配置信息数据类
 */
data class ModelConfigInfo(
    val modelId: String = "",
    val name: String,
    val provider: String,
    val baseUrl: String,
    val apiKey: String = "",
    val modelName: String,
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
