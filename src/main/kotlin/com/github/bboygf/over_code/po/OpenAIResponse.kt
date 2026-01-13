package com.github.bboygf.over_code.po

import kotlinx.serialization.Serializable

@Serializable
data class OpenAIResponse(
    val id: String? = null,
    val choices: List<OpenAIChoice>
)