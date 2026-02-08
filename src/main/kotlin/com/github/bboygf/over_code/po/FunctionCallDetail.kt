package com.github.bboygf.over_code.po

import kotlinx.serialization.Serializable


@Serializable
data class FunctionCallDetail(
    val name: String? = null,
    val arguments: String? = null // 模型生成的参数 JSON 字符串
)