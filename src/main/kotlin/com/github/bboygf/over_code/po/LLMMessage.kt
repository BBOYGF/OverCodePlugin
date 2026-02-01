package com.github.bboygf.over_code.po

/**
 * LLM 消息数据类
 */
data class LLMMessage(
    val role: String,
    val content: String,
    val images: List<String> = emptyList(),
    val functionCall: GeminiFunctionCall? = null,
    val functionCalls: List<GeminiFunctionCall>? = null,
    val parts: List<GeminiPart>? = null,
    val thought: String? = null,
    val thoughtSignature: String? = null,
    val name: String? = null
)