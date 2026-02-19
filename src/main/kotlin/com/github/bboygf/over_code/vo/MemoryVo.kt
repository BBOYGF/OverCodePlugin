package com.github.bboygf.over_code.vo

/**
 * AI 分析会话后返回的记忆数据结构
 */
data class MemoryAnalysisResult(
    val memories: List<MemoryVo>,
    val summary: String // 总结说明
)