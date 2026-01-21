package com.github.bboygf.over_code.vo

import androidx.compose.ui.text.AnnotatedString

/**
 * 消息部分定义
 */
sealed class MessagePart {
    data class Text(val content: AnnotatedString) : MessagePart() // 使用 AnnotatedString 支持加粗/行内代码
    data class Code(val content: String, val language: String?) : MessagePart()
    data class Header(val content: String, val level: Int) : MessagePart()
    data class ListItem(val content: AnnotatedString, val index: Int? = null) : MessagePart()
}