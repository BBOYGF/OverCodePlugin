package com.github.bboygf.over_code.vo

/**
 * Tab 数据类 - 每个 Tab 对应一个独立的聊天会话
 */
data class ChatTab(
    val id: String,                    // Tab 唯一标识
    val sessionId: String,            // 对应的会话ID
    var title: String = "新对话",      // Tab 标题（会话标题）
    var messages: List<ChatMessageVo> = emptyList(),
    var isLoading: Boolean = false,
    var isCancelling: Boolean = false
)