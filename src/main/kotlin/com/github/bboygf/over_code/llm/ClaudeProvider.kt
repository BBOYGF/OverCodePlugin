package com.github.bboygf.over_code.llm

import com.github.bboygf.over_code.po.LLMMessage
import com.github.bboygf.over_code.po.LlmToolCall
import com.github.bboygf.over_code.po.LlmToolDefinition
import com.github.bboygf.over_code.utils.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlin.coroutines.cancellation.CancellationException

/**
 * Anthropic Claude LLM Provider
 */
class ClaudeProvider(
    private val apiKey: String,
    private val model: String = "claude-3-5-sonnet-20240620",
    private val baseUrl: String = "https://api.anthropic.com/v1/messages",
    private val useProxy: Boolean,
    private val host: String,
    private val port: String
) : LLMProvider {


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
            connectTimeoutMillis = 60 * 1000
        }
    }

    private fun configureProxy(config: CIOEngineConfig) {
        try {
            val proxyUrl = "http://${host}:${port}"
            config.proxy = ProxyBuilder.http(proxyUrl)
        } catch (e: Exception) {
            Log.error("ClaudeProvider: 自动获取代理设置失败，将尝试直连", e)
        }
    }

    override suspend fun chat(messages: List<LLMMessage>): String {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = buildClaudeRequest(messages, null, stream = false)
                val response: JsonObject = client.post(baseUrl) {
                    header("x-api-key", apiKey)
                    header("anthropic-version", "2023-06-01")
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }.body()

                val content =
                    response["content"]?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
                return@withContext content
            } catch (e: Exception) {
                Log.error("ClaudeProvider chat方法调用失败", e)
                throw LLMException(parseClaudeError(e), e)
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
                val requestBody = buildClaudeRequest(messages, tools, stream = true)

                client.preparePost("$baseUrl/v1/messages") {
                    header("x-api-key", apiKey)
                    header("anthropic-version", "2023-06-01")
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }.execute { response ->
                    if (!response.status.isSuccess()) {
                        val errorBody = response.bodyAsText()
                        throw LLMException("Claude API 响应错误: ${response.status}, Body: $errorBody")
                    }
                    val channel: ByteReadChannel = response.bodyAsChannel()

                    // 用于累积多个并行工具调用的 Map
                    class ToolCallAccumulator(
                        var id: String = "",
                        var name: String = "",
                        val argsBuilder: StringBuilder = StringBuilder()
                    )

                    val toolCallsMap = mutableMapOf<Int, ToolCallAccumulator>()

                    while (!channel.isClosedForRead) {
                        val line = channel.readUTF8Line() ?: break
//                        Log.info("Claude Response: $line")
                        if (!line.startsWith("data:")) continue

                        val data = line.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        if (data.isEmpty()) continue

                        try {
                            val event = Json.parseToJsonElement(data).jsonObject
                            val eventType = event["type"]?.jsonPrimitive?.content
                            val index = event["index"]?.jsonPrimitive?.intOrNull ?: 0

                            when (eventType) {
                                "content_block_start" -> {
                                    val block = event["content_block"]?.jsonObject
                                    if (block?.get("type")?.jsonPrimitive?.content == "tool_use") {
                                        val acc = toolCallsMap.getOrPut(index) { ToolCallAccumulator() }
                                        acc.id = block["id"]?.jsonPrimitive?.content ?: ""
                                        acc.name = block["name"]?.jsonPrimitive?.content ?: ""
                                    }
                                }

                                "content_block_delta" -> {
                                    val delta = event["delta"]?.jsonObject
                                    withContext(Dispatchers.Main) {
                                        when (delta?.get("type")?.jsonPrimitive?.content) {
                                            "text_delta" -> onChunk(delta["text"]?.jsonPrimitive?.content ?: "")
                                            "input_json_delta" -> {
                                                toolCallsMap[index]?.argsBuilder?.append(
                                                    delta["partial_json"]?.jsonPrimitive?.content ?: ""
                                                )
                                            }

                                            "thinking_delta" -> {
                                                onThought?.invoke(delta["thinking"]?.jsonPrimitive?.content ?: "")
                                            }
                                        }
                                    }
                                }

                                "content_block_stop" -> {
                                    withContext(Dispatchers.Main) {
                                        toolCallsMap[index]?.let { acc ->
                                            if (acc.id.isNotEmpty() && acc.name.isNotEmpty()) {
                                                onToolCall?.invoke(
                                                    LlmToolCall(
                                                        acc.id,
                                                        acc.name,
                                                        acc.argsBuilder.toString()
                                                    )
                                                )
                                                toolCallsMap.remove(index)
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.error("解析 Claude 响应分片失败,请求：$requestBody 响应：$line", e)
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                throw LLMException(parseClaudeError(e), e)
            }
        }
    }

    private fun buildClaudeRequest(
        messages: List<LLMMessage>,
        tools: List<LlmToolDefinition>?,
        stream: Boolean
    ): JsonObject {
        return buildJsonObject {
            put("model", model)
            put("max_tokens", 4096)
            put("stream", stream)

            val systemPrompt = messages.find { it.role == "system" }?.content
            if (systemPrompt != null) {
                put("system", systemPrompt)
            }

            tools?.let {
                putJsonArray("tools") {
                    it.forEach { tool ->
                        addJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("input_schema", tool.parameters)
                        }
                    }
                }
            }

            putJsonArray("messages") {
                val convertedMessages = messages.filter { it.role != "system" }

                // 将连续相同角色的消息分组
                // 对于 Claude，工具调用结果 (role="tool") 必须作为一组 content 放入同一个 "user" 角色消息中
                val messageGroups = mutableListOf<MutableList<LLMMessage>>()
                convertedMessages.forEach { msg ->
                    if (messageGroups.isNotEmpty() && messageGroups.last().last().role == msg.role) {
                        messageGroups.last().add(msg)
                    } else {
                        messageGroups.add(mutableListOf(msg))
                    }
                }

                messageGroups.forEach { group ->
                    val firstMsg = group.first()
                    addJsonObject {
                        put(
                            "role",
                            if (firstMsg.role == "tool" || firstMsg.role == "function") "user" else firstMsg.role
                        )
                        putJsonArray("content") {
                            group.forEach { msg ->
                                when (msg.role) {
                                    "tool", "function" -> {
                                        addJsonObject {
                                            put("type", "tool_result")
                                            put("tool_use_id", msg.toolCallId)
                                            put("content", msg.content)
                                        }
                                    }

                                    else -> {
                                        if (msg.content.isNotEmpty()) {
                                            addJsonObject {
                                                put("type", "text")
                                                put("text", msg.content)
                                            }
                                        }
                                        msg.images.forEach { imgData ->
                                            val (mimeType, rawBase64) = parseBase64Image(imgData)
                                            addJsonObject {
                                                put("type", "image")
                                                putJsonObject("source") {
                                                    put("type", "base64")
                                                    put("media_type", mimeType)
                                                    put("data", rawBase64)
                                                }
                                            }
                                        }
                                        msg.toolCalls?.forEach { tc ->
                                            // 验证 JSON 参数是否有效，避免因流式响应不完整导致解析失败
                                            val validArguments = tryParseJsonOrDefault(tc.arguments)
                                            addJsonObject {
                                                put("type", "tool_use")
                                                put("id", tc.id)
                                                put("name", tc.functionName)
                                                put("input", Json.parseToJsonElement(validArguments))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.also {
            Log.info("Claude Request: ${it.toString()}")
        }
    }

    private fun parseBase64Image(base64String: String): Pair<String, String> {
        return if (base64String.contains(",")) {
            val split = base64String.split(",", limit = 2)
            val header = split[0]
            val data = split[1]
            val mime = header.substringAfter("data:").substringBefore(";")
            mime to data
        } else {
            "image/jpeg" to base64String
        }
    }

    /**
     * 尝试解析 JSON 字符串，如果失败则返回默认的空对象 JSON
     * 用于处理流式响应中 partial_json 可能不完整的情况
     *
     * @param jsonString 要解析的 JSON 字符串
     * @return 有效的 JSON 字符串，解析失败时返回 "{}"
     */
    private fun tryParseJsonOrDefault(jsonString: String): String {
        return try {
            Json.parseToJsonElement(jsonString)
            jsonString
        } catch (e: Exception) {
            Log.warn(
                "工具参数 JSON 解析失败，functionName=${extractFunctionName(jsonString)}, " +
                        "rawArgs=${jsonString.take(100)}, 原因: ${e.message}, 使用默认空对象"
            )
            "{}"
        }
    }

    /**
     * 从 JSON 字符串中提取函数名（用于日志）
     */
    private fun extractFunctionName(jsonString: String): String {
        return try {
            Json.parseToJsonElement(jsonString)
                .jsonObject["name"]?.jsonPrimitive?.content ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * 解析 Claude API 错误，返回友好的错误消息
     * 根据 HTTP 状态码返回对应的中文提示
     */
    private fun parseClaudeError(e: Exception): String {
        val statusCode = extractHttpStatusCode(e)

        return when (statusCode) {
            400 -> "请求参数无效，请检查输入格式是否正确"
            401 -> "API认证失败，请检查API Key是否正确"
            403 -> "API权限不足，请检查API Key权限或IP白名单"
            429 -> "请求过于频繁，请稍后再试（已超出速率限制）"
            500 -> "Anthropic服务器内部错误，请稍后重试"
            503 -> "服务暂时过载，请稍后重试"
            else -> "调用Claude API失败: ${e.message}"
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
