package com.github.bboygf.over_code.po

/**
 * LLM 消息数据类
 */
data class LLMMessage(
    val role: String,  // "user" | "assistant" | "system"
    val content: String,
    val images: List<String> = emptyList(), // 存储 Base64 或 URL
    val functionCall: GeminiFunctionCall? = null,
    val thoughtSignature: String? = null,
    val name: String? = null // 新增：用于存储 function 名称
)