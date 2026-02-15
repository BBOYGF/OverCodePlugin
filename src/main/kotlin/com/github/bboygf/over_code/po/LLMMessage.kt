package com.github.bboygf.over_code.po

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * 通用的工具定义（用于在请求时发送给 AI）
 */
@Serializable
data class LlmToolDefinition(
    val name: String,
    val description: String,
    val parameters: JsonObject // 符合 JSON Schema 格式的参数定义
)

/**
 * 通用的工具调用指令（由 AI 返回给我们的）
 */
@Serializable
data class LlmToolCall(
    val id: String?,          // 调用 ID (OpenAI/Claude 必须，Gemini 可自动生成)
    val functionName: String, // 函数名称
    val arguments: String    // JSON 格式的参数字符串
)

/**
 * 通用的 LLM 消息数据类
 * 已经过重构，不再依赖特定厂商的类（如 GeminiPart）
 */
@Serializable
data class LLMMessage(
    var role: String,                    // "user", "assistant", "system", "tool"
    val content: String,                 // 消息文本内容
    val images: List<String> = emptyList(), // Base64 图片列表

    // --- 具调用相关 ---
    val toolCalls: List<LlmToolCall>? = null, // 当 role 为 "assistant" 时，模型发起的工具调用
    val toolCallId: String? = null,           // 当 role 为 "tool" 时，该结果对应的调用 ID

    // --- 思考过程相关 ---
    val thought: String? = null,              // 模型返回的思考过程内容
    val thoughtSignature: String? = null,     // 特殊字段（如 Gemini 的思考签名，可选）

    // --- 兼容性字段 ---
    val name: String? = null                  // 某些旧版模型协议可能需要的函数名顶层字段
)
