package com.github.bboygf.over_code.llm

import com.github.bboygf.over_code.po.LLMMessage
import com.github.bboygf.over_code.po.OpenAIMessage
import com.github.bboygf.over_code.po.OpenAIRequest
import com.github.bboygf.over_code.po.OpenAIResponse
import com.intellij.openapi.diagnostic.thisLogger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * OpenAI 兼容的 LLM Provider
 * 支持：OpenAI, 智谱GLM, 通义千问, Deepseek, Moonshot 等
 */
class OpenAICompatibleProvider(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String
) : LLMProvider {
    /**
     * 日志
     */
    val logger = thisLogger()
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
            val messageDtos = messages.map { message ->
                OpenAIMessage(
                    role = message.role,
                    content = message.content,
                )
            }
            val requestBody = OpenAIRequest(
                model = model,
                messages = messageDtos,
                stream = false
            )
            try {
                val response: OpenAIResponse = client.post("$baseUrl/chat/completions") {
                    header("Authorization", "Bearer $apiKey")
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }.body()
                return@withContext response.choices.first().message?.content ?: ""
            } catch (e: Exception) {
                throw LLMException("调用 LLM API 失败: ${e.message}", e)
            }
        }
    }

    /**
     * 流式聊天 - 逐字输出
     */
    override suspend fun chatStream(messages: List<LLMMessage>, onChunk: (String) -> Unit) {
        withContext(Dispatchers.IO) {
            val messageDtos = messages.map { message ->
                OpenAIMessage(
                    role = message.role,
                    content = message.content,
                )
            }
            val requestBody = OpenAIRequest(
                model = model,
                messages = messageDtos,
                stream = true
            )
            try {
                client.preparePost("$baseUrl/chat/completions") {
                    header("Authorization", "Bearer $apiKey")
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }.execute { response ->
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
                            val delta = responseObj.choices.firstOrNull()?.delta
                            val content = delta?.content
                            val reasoning = delta?.reasoning_content
                            logger.debug("思考: $reasoning")
                            if (!content.isNullOrEmpty()) {
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
                throw LLMException("流式调用 LLM API 失败: ${e.message}", e)
            }
        }
    }

}
