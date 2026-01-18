package com.github.bboygf.over_code.po

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiConfig? = null
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
    val inlineData: GeminiBlob? = null
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