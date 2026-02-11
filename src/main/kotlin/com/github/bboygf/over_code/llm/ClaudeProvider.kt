package com.github.bboygf.over_code.llm

import com.github.bboygf.over_code.po.LLMMessage
import com.github.bboygf.over_code.po.LlmToolCall
import com.github.bboygf.over_code.po.LlmToolDefinition
import com.github.bboygf.over_code.utils.Log
import com.intellij.openapi.diagnostic.thisLogger
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

    private val logger = thisLogger()

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
        install(HttpTimeout) { requestTimeoutMillis = 3 * 1000 }
    }

    private fun configureProxy(config: CIOEngineConfig) {
        try {
            val proxyUrl = "http://${host}:${port}"
            config.proxy = ProxyBuilder.http(proxyUrl)
        } catch (e: Exception) {
            logger.warn("ClaudeProvider: 自动获取代理设置失败，将尝试直连", e)
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
                throw LLMException("调用 Claude API 失败: ${e.message}", e)
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
                        Log.info("Claude Response: $line")
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

                                "content_block_stop" -> {
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
                        } catch (e: Exception) {
                            Log.error("解析 Claude 响应分片失败: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                throw LLMException("流式调用 Claude API 失败: ${e.message}", e)
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
                        put("role", if (firstMsg.role == "tool") "user" else firstMsg.role)
                        putJsonArray("content") {
                            group.forEach { msg ->
                                when (msg.role) {
                                    "tool" -> {
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
                                            addJsonObject {
                                                put("type", "tool_use")
                                                put("id", tc.id)
                                                put("name", tc.functionName)
                                                put("input", Json.parseToJsonElement(tc.arguments))
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
}
