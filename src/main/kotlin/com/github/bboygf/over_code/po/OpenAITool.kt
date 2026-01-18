package com.github.bboygf.over_code.po

import kotlinx.serialization.Serializable

@Serializable
data class OpenAITool(
    val type: String = "function",
    val function: OpenAIFunction
)