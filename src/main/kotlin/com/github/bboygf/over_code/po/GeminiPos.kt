package com.github.bboygf.over_code.po

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiConfig? = null,

    val tools: List<GeminiTool>? = null,
    // 可选: 用于控制工具模式 (如 ANY, AUTO, NONE)
    val toolConfig: GeminiToolConfig? = null
)

@Serializable
data class GeminiTool(
    val functionDeclarations: List<GeminiFunctionDeclaration>
)

@Serializable
data class GeminiFunctionDeclaration(
    val name: String,
    val description: String,
    // 参数 schema，通常是一个 OpenAPI 格式的 JsonObject
    val parameters: JsonObject? = null
)

@Serializable
data class GeminiToolConfig(
    val functionCallingConfig: GeminiFunctionCallingConfig? = null
)

@Serializable
data class GeminiFunctionCallingConfig(
    val mode: String? = "AUTO", // AUTO, ANY, NONE
    val allowedFunctionNames: List<String>? = null
)


@Serializable
data class GeminiConfig(
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null
)

@Serializable
data class GeminiContent(
    val role: String, // "user" or "model"
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    @SerialName("inline_data")
    val inlineData: GeminiBlob? = null,
    // 新增: 模型返回的函数调用请求
    val functionCall: GeminiFunctionCall? = null,
    val thoughtSignature: String? = null,
    // 新增: 我们发送给模型的函数执行结果
    val functionResponse: GeminiFunctionResponse? = null
)

@Serializable
data class GeminiFunctionCall(
    val name: String,
    val args: JsonObject
)

// --- 响应相关 ---
@Serializable
data class GeminiFunctionResponse(
    val name: String,
    val response: JsonObject
)


@Serializable
data class GeminiBlob(
    @SerialName("mime_type")
    val mimeType: String,
    val data: String // Raw Base64 string
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null,
    val index: Int? = null
)