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
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = OpenAIRequest(
                    model = model,
                    messages = messages.map { it.toOpenAIMessage() },
                    stream = false
                ).also {
                    Log.info("Ollama Request: ${Json.encodeToString(it)}")
                }
                val response: OpenAIResponse = client.post("$baseUrl/v1/chat/completions") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }.body()
                Log.info("Ollama Response: ${Json.encodeToString(response)}")
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
                throw LLMException("流式调用 Ollama API 失败: ${e.message}", e)
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
}

