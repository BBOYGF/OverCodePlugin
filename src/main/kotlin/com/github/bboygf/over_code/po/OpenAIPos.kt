package com.github.bboygf.over_code.po

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class OpenAIRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val stream: Boolean,
    // 移除顶层 system 字段，OpenAI 官方 API 不支持
    val tools: List<OpenAITool>? = null, // 新增：可选的工具列表
    val tool_choice: String? = null      // 新增："auto", "required" 或 "none"
)


@Serializable
data class OpenAIMessage(
    val role: String? = null,
    val content: JsonElement? = null,
    val reasoning_content: String? = null,
    val reasoning: String? = null,
    val tool_calls: List<OpenAIToolCall>? = null, // 模型返回的函数调用
    val tool_call_id: String? = null             // 角色为 "tool" 时必须提供
) {
    // 辅助方法：方便获取文本内容 (用于解析响应)
    fun getContentString(): String? {
        return when (content) {
            is JsonPrimitive -> content.content // 简单字符串
            is JsonArray -> "" // 响应通常不会返给我们图片数组，暂时忽略
            else -> null
        }
    }
}


@Serializable
data class OpenAIContentPart(
    val type: String, // "text" 或 "image_url"
    val text: String? = null,
    val image_url: OpenAIImageUrl? = null
)


@Serializable
data class OpenAIImageUrl(
    val url: String // "data:image/jpeg;base64,..." 或网络链接
)

@Serializable
data class OpenAIFunction(
    val name: String,
    val description: String,
    val parameters: JsonElement // 通常是一个 JSON Schema 对象
)


@Serializable
data class OpenAITool(
    @EncodeDefault val type: String = "function",
    val function: OpenAIFunction
)

@Serializable
data class OpenAIToolCall(
    val index: Int? = null, // 新增：流式返回中的索引
    val id: String? = null,
    val type: String? = "function",
    val function: FunctionCallDetail
)


@Serializable
data class OpenAIResponse(
    val id: String? = null,
    val choices: List<OpenAIChoice>
)

@Serializable
data class OpenAIChoice(
    val index: Int? = null,
    val message: OpenAIMessage? = null, // 非流式返回
    val delta: OpenAIMessage? = null,   // 流式返回
    val finish_reason: String? = null
)

// 定义一个内部类或结构体来累积工具调用数据
class ToolCallAccumulator(
    var id: String = "",
    var name: String = "",
    val argsBuilder: StringBuilder = StringBuilder()
)