package com.github.bboygf.over_code.vo

/**
 * 会话信息数据类
 */
data class SessionInfo(
    val sessionId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long
)