package com.github.bboygf.over_code.vo

import androidx.compose.ui.text.AnnotatedString

/**
 * 消息部分定义
 */
sealed class MessagePart {
    data class Text(val content: AnnotatedString) : MessagePart()
    data class Code(val content: String, val language: String?) : MessagePart()
    data class Header(val content: String, val level: Int) : MessagePart()
    data class ListItem(val content: AnnotatedString, val index: Int? = null) : MessagePart()
}