package com.github.bboygf.over_code.po

import com.github.bboygf.over_code.enums.ChatRole
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable

/**
 * 聊天消息数据库表定义
 */
object ChatMessages : IntIdTable("chat_messages") {
    val messageId = varchar("message_id", 50).uniqueIndex()
    val content = text("content")
    val chatRole = enumerationByName("chat_role", 20, ChatRole::class)
    val timestamp = long("timestamp")
    val sessionId = varchar("session_id", 50).default("default")
    val thought = text("thought").nullable()
    val toolCalls = text("tool_calls").nullable() // JSON string of List<LlmToolCall>
    val toolCallId = text("tool_call_id").nullable()
    val thoughtSignature = text("thought_signature").nullable()
    val images = text("images").nullable() // JSON string of List<String>
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
    val useProxy = bool("use_proxy").default(false)
}

class ModelConfigEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ModelConfigEntity>(ModelConfigs)

    var modelId by ModelConfigs.modelId
    var name by ModelConfigs.name
    var provider by ModelConfigs.provider
    var baseUrl by ModelConfigs.baseUrl
    var apiKey by ModelConfigs.apiKey
    var modelName by ModelConfigs.modelName
    var isActive by ModelConfigs.isActive
    var createdAt by ModelConfigs.createdAt
    var updatedAt by ModelConfigs.updatedAt
    var useProxy by ModelConfigs.useProxy
}

/**
 * Prompt 模板数据库表定义
 */
object PromptTemplates : IntIdTable("prompt_templates") {
    val promptId = varchar("prompt_id", 50).uniqueIndex()
    val name = varchar("name", 100)
    val key = varchar("key", 50).uniqueIndex()
    val template = text("template")
    val description = varchar("description", 500).default("")
    val isBuiltIn = bool("is_built_in").default(false)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
}

object OtherConfigs : IntIdTable("other_configs") {
    val key = varchar("key", 50).uniqueIndex()
    val value = varchar("value", 500)
}

/**
 * 记忆库数据库表定义 (单一表)
 */
object Memory : IntIdTable("memory") {
    val memoryId = varchar("memory_id", 50).uniqueIndex()
    val summary = varchar("summary", 500) // 经验概要
    val content = text("content") // 经验详情
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
}

