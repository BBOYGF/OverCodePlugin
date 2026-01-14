package com.github.bboygf.over_code.po

import kotlinx.serialization.Serializable

@Serializable
data class OpenAIImageUrl(
    val url: String // "data:image/jpeg;base64,..." 或网络链接
)