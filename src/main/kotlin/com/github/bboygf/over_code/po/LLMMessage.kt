package com.github.bboygf.over_code.po

/**
 * LLM 消息数据类
 */
data class LLMMessage(
    val role: String,  // "user" | "assistant" | "system"
    val content: String
)