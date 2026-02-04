package com.github.bboygf.over_code.llm

import com.github.bboygf.over_code.po.LLMMessage
import com.github.bboygf.over_code.po.LlmToolCall
import com.github.bboygf.over_code.po.LlmToolDefinition


/**
 * LLM Provider 接口
 */
interface LLMProvider {
    suspend fun chat(messages: List<LLMMessage>): String

    suspend fun chatStream(
        messages: List<LLMMessage>,
        onChunk: (String) -> Unit,
        onToolCall: ((LlmToolCall) -> Unit)?,
        tools: List<LlmToolDefinition>?,
        onThought: ((String) -> Unit)? = null
    )
}

/**
 * LLM 异常
 */
class LLMException(message: String, cause: Throwable? = null) : Exception(message, cause)

