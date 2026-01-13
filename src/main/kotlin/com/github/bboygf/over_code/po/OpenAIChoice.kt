package com.github.bboygf.over_code.po

import kotlinx.serialization.Serializable

@Serializable
data class OpenAIChoice(
    val index: Int? = null,
    val message: OpenAIMessage? = null, // 非流式返回
    val delta: OpenAIMessage? = null,   // 流式返回
    val finish_reason: String? = null
)