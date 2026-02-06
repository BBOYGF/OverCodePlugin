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
                    tools = tools?.map { OpenAITool(function = OpenAIFunction(it.name, it.description, it.parameters)) },
                    tool_choice = if (tools != null) "auto" else null
                ).also {
                    Log.info("Ollama Request: ${Json.encodeToString(it)}")
                }

                client.preparePost("$baseUrl/v1/chat/completions") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }.execute { response ->
                    if (!response.status.isSuccess()) {
                        throw LLMException("Ollama API 响应错误: ${response.status}")
                    }
                    val channel: ByteReadChannel = response.bodyAsChannel()
                    var currentToolId = ""
                    var currentFuncName = ""
                    val argsBuilder = StringBuilder()

                    while (!channel.isClosedForRead) {
                        var line = channel.readUTF8Line() ?: break
                        Log.info("Ollama Response: $line")
                        if (line.isBlank()) continue
                        if (line.startsWith("data: ")) {
                            line = line.removePrefix("data: ")
                        }
                        if (line == "[DONE]") break

                        try {
                            val responseObj = Json { ignoreUnknownKeys = true }.decodeFromString<OpenAIResponse>(line)
                            val delta = responseObj.choices.firstOrNull()?.delta

                            val content = delta?.getContentString() ?: ""
                            if (content.isNotEmpty()) {
                                onChunk(content)
                            }
                            
                            delta?.reasoning_content?.let { onThought?.invoke(it) }

                            delta?.tool_calls?.firstOrNull()?.let { tc ->
                                if (!tc.id.isNullOrEmpty()) currentToolId = tc.id
                                if (!tc.function.name.isNullOrEmpty()) currentFuncName = tc.function.name
                                if (!tc.function.arguments.isNullOrEmpty()) argsBuilder.append(tc.function.arguments)
                            }

                            if (responseObj.choices.firstOrNull()?.finish_reason == "tool_calls") {
                                if (currentToolId.isNotEmpty()) {
                                    onToolCall?.invoke(
                                        LlmToolCall(
                                            currentToolId,
                                            currentFuncName,
                                            argsBuilder.toString()
                                        )
                                    )
                                    currentToolId = ""
                                    currentFuncName = ""
                                    argsBuilder.clear()
                                }
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
                            OpenAIToolCall(id = it.id ?: "", function = FunctionCallDetail(it.functionName, it.arguments))
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

