package com.github.bboygf.over_code.po

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class OpenAIMessage(
    val role: String? = null,
    val content: JsonElement? = null,
    val reasoning_content: String? = null
){
    // 辅助方法：方便获取文本内容 (用于解析响应)
    fun getContentString(): String? {
        return when (content) {
            is JsonPrimitive -> content.content // 简单字符串
            is JsonArray -> "" // 响应通常不会返给我们图片数组，暂时忽略
            else -> null
        }
    }
}

