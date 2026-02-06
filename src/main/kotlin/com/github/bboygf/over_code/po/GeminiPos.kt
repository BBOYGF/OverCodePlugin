package com.github.bboygf.over_code.po

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null,
    val tools: List<GeminiTool>? = null,
    val toolConfig: GeminiToolConfig? = null,
    val systemInstruction: GeminiContent? = null
)

@Serializable
data class GeminiTool(
    // 工具声明
    val functionDeclarations: List<GeminiFunctionDeclaration>
)

@Serializable
data class GeminiFunctionDeclaration(
    // 工具名称
    val name: String,
    // 工具说明
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
data class GeminiThinkingConfig(
    @SerialName("include_thoughts")
    val includeThoughts: Boolean = true
)

@Serializable
data class GeminiGenerationConfig(
    val thinkingConfig: GeminiThinkingConfig? = null,
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null
)

@Serializable
data class GeminiContent(
    val role: String? = null, // "user" or "model"
    val parts: List<GeminiPart>? = null
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    @SerialName("inlineData")
    val inlineData: GeminiBlob? = null,
    val functionCall: GeminiFunctionCall? = null,
    val thought: JsonElement? = null,
    @SerialName("thoughtSignature")
    val thoughtSignature: String? = null,
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
    @SerialName("mimeType")
    val mimeType: String,
    val data: String // Raw Base64 string
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    val usageMetadata: GeminiUsageMetadata? = null,
    val modelVersion: String? = null,
    val responseId: String? = null
)

@Serializable
data class GeminiUsageMetadata(
    @SerialName("promptTokenCount")
    val promptTokenCount: Int? = null,
    @SerialName("candidatesTokenCount")
    val candidatesTokenCount: Int? = null,
    @SerialName("totalTokenCount")
    val totalTokenCount: Int? = null,
    @SerialName("promptTokensDetails")
    val promptTokensDetails: List<GeminiTokenDetails>? = null,
    @SerialName("thoughtsTokenCount")
    val thoughtsTokenCount: Int? = null
)

@Serializable
data class GeminiTokenDetails(
    val modality: String? = null,
    @SerialName("tokenCount")
    val tokenCount: Int? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null,
    val index: Int? = null,
    @SerialName("safetyRatings")
    val safetyRatings: List<GeminiSafetyRating>? = null
)

@Serializable
data class GeminiSafetyRating(
    val category: String? = null,
    val probability: String? = null
)