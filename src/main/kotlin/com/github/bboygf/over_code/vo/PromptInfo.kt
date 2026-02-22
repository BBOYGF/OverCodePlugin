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

/**
 * 记忆库VO (单一表)
 */
data class MemoryVo(
    val memoryId: String = "",
    val summary: String, // 经验概要
    val content: String, // 经验详情
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
