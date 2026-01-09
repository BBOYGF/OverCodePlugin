package com.github.bboygf.over_code.vo

/**
 * 消息数据类
 */
data class ChatMessage(
    val id: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)