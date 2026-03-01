package com.github.bboygf.over_code.llm

import com.github.bboygf.over_code.po.*
import com.github.bboygf.over_code.utils.Log
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/**
 * Ollama LLM Provider
 * 适配 OpenAI 兼容接口
 */
class OllamaProvider(
    private val baseUrl: String = "http://localhost:11434",
    private val model: String = "llama2"
) : LLMProvider {


    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                isLenient = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 5 * 60 * 1000
            socketTimeoutMillis = 3 * 60 * 1000
            connectTimeoutMillis = 10 * 1000
        }
    }

    override suspend fun chat(messages: List<LLMMessage>): String {
        return try {
            withContext(Dispatchers.IO) {
                // 提取 system 消息
                val filteredMessages = messages.filter { it.role != "system" }
                val requestBody = OpenAIRequest(
                    model = model,
                    messages = filteredMessages.map { it.toOpenAIMessage() },
                    stream = false,
                ).also {
                    Log.info("Ollama Request: ${Json.encodeToString(it)}")
                }
                val response: OpenAIResponse = client.post("$baseUrl/v1/chat/completions") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }.body()
                Log.info("Ollama Response: ${Json.encodeToString(response)}")
                response.choices.first().message?.getContentString() ?: ""
            }
        } catch (e: Exception) {
            Log.error("OllamaProvider chat方法调用失败", e)
            throw LLMException(parseOllamaError(e), e)
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
                // 提取 system 消息
                val filteredMessages = messages.filter { it.role != "system" }
                val requestBody = OpenAIRequest(
                    model = model,
                    messages = filteredMessages.map { it.toOpenAIMessage() },
                    stream = true,
                    tools = tools?.map {
                        OpenAITool(
                            function = OpenAIFunction(
                                it.name,
                                it.description,
                                it.parameters
                            )
                        )
                    },
                    tool_choice = if (tools != null) "auto" else null
                ).also {
                    Log.info("Ollama Request: ${Json.encodeToString(it)}")
                }
                val toolCallsMap = mutableMapOf<Int, ToolCallAccumulator>()
                var line = ""
                client.preparePost("$baseUrl/v1/chat/completions") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }.execute { response ->
                    if (!response.status.isSuccess()) {
                        throw LLMException("LLM API 响应错误: ${response.status}")
                    }
                    val channel: ByteReadChannel = response.bodyAsChannel()
                    while (!channel.isClosedForRead) {
                        line = channel.readUTF8Line() ?: break
                        Log.info("OpenAI Response: $line")
                        if (line.isBlank()) continue
                        if (line.startsWith(":")) continue
                        if (line.startsWith("data: ")) line = line.removePrefix("data: ")
                        if (line == "[DONE]") break
                        try {
                            val responseObj = Json { ignoreUnknownKeys = true }.decodeFromString<OpenAIResponse>(line)
                            val delta = responseObj.choices.firstOrNull()?.delta ?: continue

                            val content: String? = delta.getContentString()
                            withContext(Dispatchers.Main) {
                                if (!content.isNullOrEmpty()) onChunk(content)

                                delta.reasoning_content?.let { onThought?.invoke(it) }
                                delta.reasoning?.let { onThought?.invoke(it) }
                            }

                            delta.tool_calls?.forEach { tc ->
                                val idx = tc.index ?: 0
                                val acc = toolCallsMap.getOrPut(idx) { ToolCallAccumulator() }

                                tc.id?.let { if (it.isNotEmpty()) acc.id = it }
                                tc.function.name?.let { if (it.isNotEmpty()) acc.name = it }
                                tc.function.arguments?.let { acc.argsBuilder.append(it) }
                            }
                        } catch (e: Exception) {
                            Log.error("解析 OpenAI 响应失败: ${e.message}\n原始响应: $line", e)
                        }
                    }
                    if (toolCallsMap.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            toolCallsMap.keys.sorted().forEach { idx ->
                                toolCallsMap[idx]?.let { acc ->
                                    if (acc.id.isNotEmpty() || acc.name.isNotEmpty()) {
                                        onToolCall?.invoke(
                                            LlmToolCall(acc.id, acc.name, acc.argsBuilder.toString())
                                        )
                                    }
                                }
                            }
                        }
                        toolCallsMap.clear()
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                throw LLMException(parseOllamaError(e), e)
            }
        }
    }

    private fun LLMMessage.toOpenAIMessage(): OpenAIMessage {
        return when (role) {
            "tool" -> OpenAIMessage(
                role = "tool",
                tool_call_id = toolCallId,
                content = JsonPrimitive(content)
            )

            else -> {
                if (images.isEmpty()) {
                    OpenAIMessage(
                        role = role,
                        content = JsonPrimitive(content),
                        tool_calls = toolCalls?.map {
                            OpenAIToolCall(
                                id = it.id ?: "",
                                function = FunctionCallDetail(it.functionName, it.arguments)
                            )
                        }
                    )
                } else {
                    val contentList = mutableListOf<JsonElement>()
                    if (content.isNotEmpty()) {
                        contentList.add(Json.encodeToJsonElement(OpenAIContentPart(type = "text", text = content)))
                    }
                    images.forEach { imgData ->
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
                    OpenAIMessage(role = role, content = JsonArray(contentList))
                }
            }
        }
    }

    /**
     * 解析 Ollama API 错误，返回友好的错误消息
     * 根据 HTTP 状态码返回对应的中文提示
     */
    private fun parseOllamaError(e: Exception): String {
        val statusCode = extractHttpStatusCode(e)

        return when (statusCode) {
            400 -> "请求参数无效，请检查输入格式是否正确"
            404 -> "请求的资源未找到，请检查模型名称是否正确"
            422 -> "请求参数无效，请检查模型名称和输入格式"
            429 -> "请求过于频繁，请稍后再试"
            500 -> "Ollama服务器内部错误，请检查Ollama服务是否正常运行"
            503 -> "服务暂时不可用，请检查Ollama服务是否启动"
            else -> "调用Ollama API失败: ${e.message}"
        }
    }

    /**
     * 从异常中提取 HTTP 状态码
     */
    private fun extractHttpStatusCode(e: Exception): Int? {
        val message = e.message ?: return null

        val regex = "\\((\\d+)\\s*-".toRegex()
        regex.find(message)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }

        var cause: Throwable? = e.cause
        while (cause != null) {
            val causeMessage = cause.message
            if (causeMessage != null) {
                regex.find(causeMessage)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
            }
            cause = cause.cause
        }

        return null
    }
}

