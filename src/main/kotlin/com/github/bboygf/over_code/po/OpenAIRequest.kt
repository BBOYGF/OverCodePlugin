package com.github.bboygf.over_code.po

import kotlinx.serialization.Serializable

@Serializable
data class OpenAIRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val stream: Boolean,
    val tools: List<OpenAITool>? = null, // 新增：可选的工具列表
    val tool_choice: String? = null      // 新增："auto", "required" 或 "none"
)
