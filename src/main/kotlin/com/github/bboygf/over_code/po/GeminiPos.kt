package com.github.bboygf.over_code.po

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@kotlinx.serialization.Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiConfig? = null
)

@kotlinx.serialization.Serializable
data class GeminiConfig(
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null
)

@kotlinx.serialization.Serializable
data class GeminiContent(
    val role: String, // "user" or "model"
    val parts: List<GeminiPart>
)

@kotlinx.serialization.Serializable
data class GeminiPart(
    val text: String? = null,
    @SerialName("inline_data")
    val inlineData: GeminiBlob? = null
)

@kotlinx.serialization.Serializable
data class GeminiBlob(
    @SerialName("mime_type")
    val mimeType: String,
    val data: String // Raw Base64 string
)

@kotlinx.serialization.Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null,
    val index: Int? = null
)