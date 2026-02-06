package com.github.bboygf.over_code.llm

import com.github.bboygf.over_code.po.*
import com.github.bboygf.over_code.utils.Log
import com.intellij.openapi.diagnostic.thisLogger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.CIOEngineConfig
import io.ktor.client.engine.http
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/**
 * OpenAI 兼容的 LLM Provider
 * 支持：OpenAI, 智谱GLM, 通义千问, Deepseek, Moonshot 等
 */
class OpenAICompatibleProvider(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val useProxy: Boolean,
    private val host: String,
    private val port: String
) : LLMProvider {
    /**
     * 日志
     */
    val logger = thisLogger()
    private val client = HttpClient(CIO) {
        if (useProxy) {
            engine {
                configureProxy(this)
            }
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                isLenient = true
            })
        }
        install(HttpTimeout) { requestTimeoutMillis = 100000 }
    }

    private fun configureProxy(config: CIOEngineConfig) {
        try {
            val proxyUrl = "http://${host}:${port}"
            config.proxy = ProxyBuilder.http(proxyUrl)
        } catch (e: Exception) {
            logger.warn("OpenAICompatibleProvider: 自动获取代理设置失败，将尝试直连", e)
        }
    }

    override suspend fun chat(messages: List<LLMMessage>): String {
        return withContext(Dispatchers.IO) {
            val requestBody = OpenAIRequest(
                model = model,
                messages = messages.map { it.toOpenAIMessage() },
                stream = false
            ).also {
                Log.info("OpenAI Request: ${Json.encodeToString(it)}")
            }
            try {
                val response: OpenAIResponse = client.post("$baseUrl/chat/completions") {
                    header("Authorization", "Bearer $apiKey")
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }.body()
                Log.info("OpenAI Response: ${Json.encodeToString(response)}")
                val content: String = response.choices.first().message?.getContentString() ?: ""
                return@withContext content
            } catch (e: Exception) {
                throw LLMException("调用 LLM API 失败: ${e.message}", e)
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
            val requestBody = OpenAIRequest(
                model = model,
                messages = messages.map { it.toOpenAIMessage() },
                stream = true,
                tools = tools?.map { OpenAITool(function = OpenAIFunction(it.name, it.description, it.parameters)) },
                tool_choice = if (tools != null) "auto" else null
            ).also {
                Log.info("OpenAI Request: ${Json.encodeToString(it)}")
            }
            try {
                client.preparePost("$baseUrl/chat/completions") {
                    header("Authorization", "Bearer $apiKey")
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }.execute { response ->
                    if (!response.status.isSuccess()) {
                        throw LLMException("LLM API 响应错误: ${response.status}")
                    }
                    val channel: ByteReadChannel = response.bodyAsChannel()
                    var currentToolId = ""
                    var currentFuncName = ""
                    val argsBuilder = StringBuilder()

                    while (!channel.isClosedForRead) {
                        var line = channel.readUTF8Line() ?: break
                        Log.info("OpenAI Response: $line")
                        if (line.isBlank()) continue
                        if (line.startsWith(":")) continue
                        if (line.startsWith("data: ")) line = line.removePrefix("data: ")
                        if (line == "[DONE]") break

                        try {
                            val responseObj = Json { ignoreUnknownKeys = true }.decodeFromString<OpenAIResponse>(line)
                            val delta = responseObj.choices.firstOrNull()?.delta

                            val content: String? = delta?.getContentString()
                            if (!content.isNullOrEmpty()) onChunk(content)

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
                            logger.error("解析 OpenAI 响应失败: ${e.message}\n原始响应: $line", e)
                        }
                    }
                }
            } catch (e: Exception) {
                throw LLMException("流式调用 LLM API 失败: ${e.message}", e)
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

