package com.github.bboygf.over_code.po

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class OpenAIFunction(
    val name: String,
    val description: String,
    val parameters: JsonElement // 通常是一个 JSON Schema 对象
)