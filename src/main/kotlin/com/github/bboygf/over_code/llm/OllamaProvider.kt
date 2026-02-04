package com.github.bboygf.over_code.llm

import com.github.bboygf.over_code.po.*
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
import kotlinx.serialization.json.*

/**
 * Ollama LLM Provider
 * 适配 OpenAI 兼容接口
 */
class OllamaProvider(
    private val baseUrl: String = "http://localhost:11434",
    private val model: String = "llama2"
) : LLMProvider {

    private val logger = thisLogger()

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                isLenient = true
            })
        }
        install(HttpTimeout) { requestTimeoutMillis = 60000 }
    }

    override suspend fun chat(messages: List<LLMMessage>): String {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = OpenAIRequest(
                    model = model,
                    messages = messages.map { it.toOpenAIMessage() },
                    stream = false
                )
                val response: OpenAIResponse = client.post("$baseUrl/v1/chat/completions") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }.body()
                return@withContext response.choices.first().message?.getContentString() ?: ""
            } catch (e: Exception) {
                throw LLMException("调用 Ollama API 失败: ${e.message}", e)
            }
        }
    }

    override suspend fun chatStream(
        messages: List<LLMMessage>,
        onChunk: (String) -> Unit,
        onToolCall: ((LlmToolCall) -> Unit)?,
        tools: List<LlmToolDefinition>?,
        onThought: ((String) -> Unit)?
    ) {
        withContext(Dispatchers.IO) {
            try {
                val requestBody = OpenAIRequest(
                    model = model,
                    messages = messages.map { it.toOpenAIMessage() },
                    stream = true
                )

                client.preparePost("$baseUrl/v1/chat/completions") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }.execute { response ->
                    if (!response.status.isSuccess()) {
                        throw LLMException("Ollama API 响应错误: ${response.status}")
                    }
                    val channel: ByteReadChannel = response.bodyAsChannel()
                    while (!channel.isClosedForRead) {
                        var line = channel.readUTF8Line() ?: break
                        if (line.isBlank()) continue
                        if (line.startsWith("data: ")) {
                            line = line.removePrefix("data: ")
                        }
                        if (line == "[DONE]") break

                        try {
                            val responseObj = Json { ignoreUnknownKeys = true }.decodeFromString<OpenAIResponse>(line)
                            val content = responseObj.choices.firstOrNull()?.delta?.getContentString() ?: ""
                            if (content.isNotEmpty()) {
                                onChunk(content)
                            }
                        } catch (e: Exception) {
                            logger.error("解析 Ollama 响应失败: ${e.message}\n原始响应: $line", e)
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                throw LLMException("流式调用 Ollama API 失败: ${e.message}", e)
            }
        }
    }

    private fun LLMMessage.toOpenAIMessage(): OpenAIMessage {
        // 简化映射逻辑，Ollama 通常用于本地运行，工具调用支持情况视具体模型而定
        return OpenAIMessage(
            role = role,
            content = JsonPrimitive(content)
        )
    }
}

