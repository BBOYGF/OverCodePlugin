package com.github.bboygf.over_code.po

import kotlinx.serialization.Serializable

@Serializable
data class OpenAIToolCall(
    val id: String,
    val type: String = "function",
    val function: FunctionCallDetail
)