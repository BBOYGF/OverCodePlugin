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

import kotlin.coroutines.cancellation.CancellationException

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
    val json = Json { ignoreUnknownKeys = true }
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
        install(HttpTimeout) {
            requestTimeoutMillis = 5 * 60 * 1000
            socketTimeoutMillis = 3 * 60 * 1000
            connectTimeoutMillis = 10 * 1000
        }
    }

    private fun configureProxy(config: CIOEngineConfig) {
        try {
            val proxyUrl = "http://${host}:${port}"
            config.proxy = ProxyBuilder.http(proxyUrl)
        } catch (e: Exception) {
            Log.error("OpenAICompatibleProvider: 自动获取代理设置失败，将尝试直连", e)
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
                Log.error("OpenAICompatibleProvider chat方法调用失败", e)
                throw LLMException(parseOpenAIError(e), e)
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
//                Log.info("OpenAI Request: ${Json.encodeToString(it)}")
            }
            val toolCallsMap = mutableMapOf<Int, ToolCallAccumulator>()
            var line = ""
            try {
                client.preparePost("$baseUrl/chat/completions") {
                    header("Authorization", "Bearer $apiKey")
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }.execute { response ->
                    if (!response.status.isSuccess()) {
                        Log.error(response.toString())
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
                            val responseObj = json.decodeFromString<OpenAIResponse>(line)
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
                throw LLMException(parseOpenAIError(e), e)
            }
        }
    }

    /**
     * 解析 OpenAI 兼容 API 错误，返回友好的错误消息
     * 根据 HTTP 状态码返回对应的中文提示
     */
    private fun parseOpenAIError(e: Exception): String {
        // 尝试从 Ktor 异常中提取 HTTP 状态码
        val statusCode = extractHttpStatusCode(e)

        return when (statusCode) {
            401 -> "API认证失败，请检查API Key是否正确"
            403 -> "API权限不足，可能原因：IP未授权或账户不属于任何组织"
            404 -> "请求的资源未找到，请检查API端点是否正确"
            429 -> "请求过于频繁或超出配额，请稍后再试或检查您的套餐"
            500 -> "服务器内部错误，请稍后重试"
            503 -> "服务暂时过载，请稍后重试"
            else -> "调用OpenAI兼容API失败: ${e.message}"
        }
    }

    /**
     * 从异常中提取 HTTP 状态码
     */
    private fun extractHttpStatusCode(e: Exception): Int? {
        // 尝试从异常消息中提取状态码
        val message = e.message ?: return null

        // 常见 Ktor 异常模式: "Server returned(401 - Unauthorized)"
        val regex = "\\((\\d+)\\s*-".toRegex()
        regex.find(message)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }

        // 尝试从 cause 中获取
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

    private fun LLMMessage.toOpenAIMessage(): OpenAIMessage {
        return when (role) {
            "tool", "function" -> OpenAIMessage(
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

