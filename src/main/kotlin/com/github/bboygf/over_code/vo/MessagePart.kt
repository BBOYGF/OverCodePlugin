package com.github.bboygf.over_code.vo

/**
 * 消息部分定义
 */
sealed class MessagePart {
    data class Text(val content: String) : MessagePart()
    data class Code(val content: String, val language: String? = null) : MessagePart()
}
