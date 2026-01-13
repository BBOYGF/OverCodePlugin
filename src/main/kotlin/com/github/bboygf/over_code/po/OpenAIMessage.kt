package com.github.bboygf.over_code.po

import kotlinx.serialization.Serializable

@Serializable
data class OpenAIMessage(
    val role: String? = null,
    val content: String? = null,
    val reasoning_content: String? = null
)
