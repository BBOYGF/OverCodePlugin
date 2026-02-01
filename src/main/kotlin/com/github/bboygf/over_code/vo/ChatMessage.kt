package com.github.bboygf.over_code.vo

import com.github.bboygf.over_code.enums.ChatRole

/**
 * 消息数据类
 */
data class ChatMessage(
    val id: String,
    val content: String,
    val chatRole: ChatRole,
    val timestamp: Long = System.currentTimeMillis(),
    val images: List<String> = emptyList(),
    val thought: String? = null
)