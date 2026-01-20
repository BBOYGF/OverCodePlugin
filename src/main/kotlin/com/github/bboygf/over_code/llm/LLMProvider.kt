package com.github.bboygf.over_code.llm

import com.github.bboygf.over_code.po.GeminiFunctionCall
import com.github.bboygf.over_code.po.GeminiPart
import com.github.bboygf.over_code.po.GeminiTool
import com.github.bboygf.over_code.po.LLMMessage


/**
 * LLM Provider 接口
 */
interface LLMProvider {
    /**
     * 发送聊天消息并获取回复（异步）
     */
    suspend fun chat(messages: List<LLMMessage>): String


    /**
     * 流式聊天（可选实现）
     */
    suspend fun chatStream(
        messages: List<LLMMessage>,
        onChunk: (String) -> Unit,
        onToolCall: ((GeminiPart) -> Unit)? = null,
        tools: List<GeminiTool>?
    )
}

/**
 * LLM 异常
 */
class LLMException(message: String, cause: Throwable? = null) : Exception(message, cause)
