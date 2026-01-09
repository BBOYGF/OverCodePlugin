package com.github.bboygf.over_code.vo

/**
 * Prompt 模板信息数据类
 */
data class PromptInfo(
    val promptId: String = "",
    val name: String,
    val key: String,
    val template: String,
    val description: String = "",
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
