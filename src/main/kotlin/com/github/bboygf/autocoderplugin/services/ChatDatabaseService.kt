package com.github.bboygf.autocoderplugin.services

import com.github.bboygf.autocoderplugin.toolWindow.ChatMessage
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
 * 聊天数据库服务
 */
@Service(Service.Level.PROJECT)
class ChatDatabaseService(private val project: Project) {
    
    private val database: Database
    
    init {
        // 获取插件数据目录
        val pluginDataDir = File(System.getProperty("user.home"), ".AutoCoderPlugin")
        if (!pluginDataDir.exists()) {
            pluginDataDir.mkdirs()
        }
        
        // 数据库文件路径
        val dbFile = File(pluginDataDir, "qoder_chat.db")
        
        // 连接 SQLite 数据库
        database = Database.connect(
            url = "jdbc:sqlite:${dbFile.absolutePath}",
            driver = "org.sqlite.JDBC"
        )
        
        // 初始化数据库表
        transaction(database) {
            SchemaUtils.create(ChatMessages, ChatSessions)
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
