package com.github.bboygf.over_code.llm

import com.github.bboygf.over_code.po.GeminiFunctionCall
import com.github.bboygf.over_code.po.GeminiPart
import com.github.bboygf.over_code.po.GeminiTool
import com.github.bboygf.over_code.po.LLMMessage


/**
 * LLM Provider 接口
 */
interface LLMProvider {
    suspend fun chat(messages: List<LLMMessage>): String

    suspend fun chatStream(
        messages: List<LLMMessage>,
        onChunk: (String) -> Unit,
        onToolCall: ((GeminiPart) -> Unit)? = null,
        tools: List<GeminiTool>?,
        onThought: ((String) -> Unit)? = null
    )
}

/**
 * LLM 异常
 */
class LLMException(message: String, cause: Throwable? = null) : Exception(message, cause)
