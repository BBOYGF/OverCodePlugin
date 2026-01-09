package com.github.bboygf.over_code.vo

/**
 * 模型配置信息数据类
 */
data class ModelConfigInfo(
    val modelId: String = "",
    val name: String,
    val provider: String,
    val baseUrl: String,
    val apiKey: String = "",
    val modelName: String,
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)