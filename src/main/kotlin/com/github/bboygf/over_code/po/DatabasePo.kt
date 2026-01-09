package com.github.bboygf.over_code.po

import org.jetbrains.exposed.dao.id.IntIdTable

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
