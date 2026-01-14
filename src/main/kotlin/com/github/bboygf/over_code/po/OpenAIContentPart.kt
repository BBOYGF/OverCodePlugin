package com.github.bboygf.over_code.po

import kotlinx.serialization.Serializable

@Serializable
data class OpenAIContentPart(
    val type: String, // "text" 或 "image_url"
    val text: String? = null,
    val image_url: OpenAIImageUrl? = null
)