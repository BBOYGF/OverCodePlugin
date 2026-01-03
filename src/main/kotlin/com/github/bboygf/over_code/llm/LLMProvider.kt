package com.github.bboygf.over_code.llm

/**
 * LLM 消息数据类
 */
data class LLMMessage(
    val role: String,  // "user" | "assistant" | "system"
    val content: String
)

/**
 * LLM Provider 接口
 */
interface LLMProvider {
    /**
     * 发送聊天消息并获取回复（异步）
     */
    suspend fun chat(messages: List<LLMMessage>): String
    
    /**
     * 发送聊天消息并获取回复（同步）
     */
    fun chatSync(messages: List<LLMMessage>): String {
        // 默认实现：直接同步调用
        throw UnsupportedOperationException("请实现 chatSync 方法")
    }
    
    /**
     * 流式聊天（可选实现）
     */
    suspend fun chatStream(messages: List<LLMMessage>, onChunk: (String) -> Unit) {
        // 默认实现：直接返回完整结果
        val result = chat(messages)
        onChunk(result)
    }
}

/**
 * LLM 异常
 */
class LLMException(message: String, cause: Throwable? = null) : Exception(message, cause)
