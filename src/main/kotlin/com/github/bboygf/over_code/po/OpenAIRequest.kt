package com.github.bboygf.over_code.po

import kotlinx.serialization.Serializable

@Serializable
data class OpenAIRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val stream: Boolean
)
