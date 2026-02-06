package com.github.bboygf.over_code.vo

import com.github.bboygf.over_code.enums.ChatRole
import com.github.bboygf.over_code.po.LlmToolCall

/**
 * 消息数据类
 */
data class ChatMessageVo(
    val id: String,
    val content: String,
    val chatRole: ChatRole,
    val timestamp: Long = System.currentTimeMillis(),
    val thought: String? = null,
    val toolCalls: List<LlmToolCall>? = null, // 保存工具调用结构
    val toolCallId: String? = null,           // 保存工具调用结果对应的 ID
    val thoughtSignature: String? = null,      // 新增：保存 Gemini 的思考签名
    val images: List<String> = emptyList()     // 新增：保存图片 (Base64)
)
