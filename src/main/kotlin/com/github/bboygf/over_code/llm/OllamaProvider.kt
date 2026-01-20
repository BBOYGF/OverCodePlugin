package com.github.bboygf.over_code.llm

import com.github.bboygf.over_code.po.GeminiFunctionCall
import com.github.bboygf.over_code.po.GeminiPart
import com.github.bboygf.over_code.po.GeminiTool
import com.github.bboygf.over_code.po.LLMMessage
import com.github.bboygf.over_code.po.OpenAIContentPart
import com.github.bboygf.over_code.po.OpenAIImageUrl
import com.github.bboygf.over_code.po.OpenAIMessage
import com.github.bboygf.over_code.po.OpenAIRequest
import com.github.bboygf.over_code.po.OpenAIResponse
import com.intellij.openapi.diagnostic.thisLogger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Ollama LLM Provider
 */
class OllamaProvider(
    private val baseUrl: String = "http://localhost:11434",
    private val model: String = "llama2"
) : LLMProvider {

    private val logger = thisLogger()

    private val client = HttpClient(CIO) {

        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true // 忽略响应中多余的字段（重要！）
                encodeDefaults = true    // 编码默认值
                isLenient = true
            })
        }
        install(HttpTimeout) { requestTimeoutMillis = 60000 }
    }

    override suspend fun chat(messages: List<LLMMessage>): String {
        return withContext(Dispatchers.IO) {
            try {
                // 1. 将原本的 LLMMessage 转换为 DTO
                val messageDtos = messages.map { msg ->
                    if (msg.images.isEmpty()) {
                        OpenAIMessage(
                            role = msg.role,
                            content = JsonPrimitive(msg.content),
                        )
                    } else {
                        val contentList = mutableListOf<JsonElement>()
                        if (msg.content.isNotEmpty()) {
                            contentList.add(
                                Json.encodeToJsonElement(
                                    OpenAIContentPart(type = "text", text = msg.content)
                                )
                            )
                        }
                        msg.images.forEach { imgData ->
                            // 自动判断是否需要加 base64 前缀 (如果已经是 URL 则不用)
                            val formattedUrl =
                                if (imgData.startsWith("http")) imgData else "data:image/jpeg;base64,$imgData"

                            contentList.add(
                                Json.encodeToJsonElement(
                                    OpenAIContentPart(
                                        type = "image_url",
                                        image_url = OpenAIImageUrl(url = formattedUrl)
                                    )
                                )
                            )

                        }
                        OpenAIMessage(
                            role = msg.role,
                            content = JsonArray(contentList) // 包装成 JsonArray
                        )
                    }
                }
                // 2. 构建请求体对象 (代替 buildRequestBody)
                val requestBody = OpenAIRequest(
                    model = model, // 假设类中有这个变量
                    messages = messageDtos,
                    stream = false
                )
                // 3. 发起请求
                val response: OpenAIResponse = client.post("$baseUrl/v1/chat/completions") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }.body() // 自动反序列化为 OllamaResponse 对象
                // 4. 返回内容 (代替 parseResponse)
                val content: String = when (val c = response.choices.first().message?.content) {
                    is JsonPrimitive -> c.content // 如果是简单字符串
                    is JsonArray -> "" // 响应流中通常不包含图片数组，暂忽略
                    else -> "" // 如果是 JsonNull 或不存在
                }
                return@withContext content
            } catch (e: Exception) {
                // 统一异常处理
                throw LLMException("调用 Ollama API 失败: ${e.message}", e)
            }
        }
    }


    /**
     * 流式聊天 - 逐字输出
     */
    override suspend fun chatStream(
        messages: List<LLMMessage>, onChunk: (String) -> Unit, onToolCall: ((GeminiPart) -> Unit)?,
        tools: List<GeminiTool>?
    ) {
        withContext(Dispatchers.IO) {
            try {
                // 1. 准备请求数据 (复用之前定义的 DTO)
                val messageDtos = messages.map { msg ->
                    if (msg.images.isEmpty()) {
                        OpenAIMessage(
                            role = msg.role,
                            content = JsonPrimitive(msg.content),
                        )
                    } else {
                        val contentList = mutableListOf<JsonElement>()
                        if (msg.content.isNotEmpty()) {
                            contentList.add(
                                Json.encodeToJsonElement(
                                    OpenAIContentPart(type = "text", text = msg.content)
                                )
                            )
                        }
                        msg.images.forEach { imgData ->
                            // 自动判断是否需要加 base64 前缀 (如果已经是 URL 则不用)
                            val formattedUrl =
                                if (imgData.startsWith("http")) imgData else "data:image/jpeg;base64,$imgData"

                            contentList.add(
                                Json.encodeToJsonElement(
                                    OpenAIContentPart(
                                        type = "image_url",
                                        image_url = OpenAIImageUrl(url = formattedUrl)
                                    )
                                )
                            )

                        }
                        OpenAIMessage(
                            role = msg.role,
                            content = JsonArray(contentList) // 包装成 JsonArray
                        )
                    }
                }

                // 注意：这里 stream 必须设为 true
                val requestBody = OpenAIRequest(
                    model = model,
                    messages = messageDtos,
                    stream = true
                )

                // 2. 建立流式连接
                // 使用 preparePost 而不是 post，这样可以获得 HttpStatement
                client.preparePost("$baseUrl/v1/chat/completions") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }.execute { response ->
                    // execute 块内连接保持打开状态

                    // 获取读取通道
                    val channel: ByteReadChannel = response.bodyAsChannel()

                    // 3. 循环读取流
                    while (!channel.isClosedForRead) {
                        // readUTF8Line 是挂起函数，非阻塞
                        var line = channel.readUTF8Line() ?: break

                        if (line.isBlank()) continue
                        if (line.startsWith(":")) {
                            continue
                        }
                        if (line.startsWith("data: ")) {
                            line = line.removePrefix("data: ")
                        }
                        if (line == "[DONE]") {
                            break
                        }
                        try {
                            // 使用 Kotlinx Serialization 反序列化，替代 JSONObject
                            // 这里需要配置 ignoreUnknownKeys = true 的 Json 实例
                            // 建议使用类成员变量或全局单例 Json，这里为了演示直接调用
                            val responseObj = Json { ignoreUnknownKeys = true }.decodeFromString<OpenAIResponse>(line)

                            val deltaContent = responseObj.choices.firstOrNull()?.delta?.content
                            val content: String = when (val c = deltaContent) {
                                is JsonPrimitive -> c.content // 如果是简单字符串
                                is JsonArray -> "" // 响应流中通常不包含图片数组，暂忽略
                                else -> "" // 如果是 JsonNull 或不存在
                            }
                            if (content.isNotEmpty()) {
                                onChunk(content)
                            }

                            // 检查是否结束 (部分模型可能通过 finish_reason 标记)
                            if (responseObj.choices.firstOrNull()?.finish_reason != null) {
                                // 通常可以忽略，或者在这里 break
                            }
                        } catch (e: Exception) {
                            logger.error("解析 Ollama 响应失败: ${e.message}\n原始响应: $line", e)
                        }
                    }
                }
            } catch (e: Exception) {
                // 4. 异常处理
                // 协程取消（用户点击停止）不应被视为 API 错误
                if (e is CancellationException) {
                    throw e
                }
                throw LLMException("流式调用 Ollama API 失败: ${e.message}", e)
            }
        }
    }
}
