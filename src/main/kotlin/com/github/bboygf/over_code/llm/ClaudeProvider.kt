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
        install(HttpTimeout) { requestTimeoutMillis = 60000 }
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

                client.preparePost(baseUrl) {
                    header("x-api-key", apiKey)
                    header("anthropic-version", "2023-06-01")
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }.execute { response ->
                    if (!response.status.isSuccess()) {
                        throw LLMException("Claude API 响应错误: ${response.status}")
                    }
                    val channel: ByteReadChannel = response.bodyAsChannel()
                    var currentToolId = ""
                    var currentToolName = ""
                    val toolArgumentsBuilder = StringBuilder()

                    while (!channel.isClosedForRead) {
                        val line = channel.readUTF8Line() ?: break
                        if (!line.startsWith("data:")) continue

                        val data = line.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        if (data.isEmpty()) continue

                        try {
                            val event = Json.parseToJsonElement(data) as JsonObject
                            when (event["type"]?.jsonPrimitive?.content) {
                                "content_block_start" -> {
                                    val block = event["content_block"]?.jsonObject
                                    if (block?.get("type")?.jsonPrimitive?.content == "tool_use") {
                                        currentToolId = block["id"]?.jsonPrimitive?.content ?: ""
                                        currentToolName = block["name"]?.jsonPrimitive?.content ?: ""
                                    }
                                }

                                "content_block_delta" -> {
                                    val delta = event["delta"]?.jsonObject
                                    when (delta?.get("type")?.jsonPrimitive?.content) {
                                        "text_delta" -> onChunk(delta["text"]?.jsonPrimitive?.content ?: "")
                                        "input_json_delta" -> {
                                            toolArgumentsBuilder.append(
                                                delta["partial_json"]?.jsonPrimitive?.content ?: ""
                                            )
                                        }
                                    }
                                }

                                "content_block_stop" -> {
                                    if (currentToolId.isNotEmpty()) {
                                        onToolCall?.invoke(
                                            LlmToolCall(
                                                currentToolId,
                                                currentToolName,
                                                toolArgumentsBuilder.toString()
                                            )
                                        )
                                        currentToolId = ""
                                        currentToolName = ""
                                        toolArgumentsBuilder.clear()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // 忽略解析错误
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
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
                messages.filter { it.role != "system" }.forEach { msg ->
                    addJsonObject {
                        put("role", if (msg.role == "tool") "user" else msg.role)
                        putJsonArray("content") {
                            if (msg.role == "tool") {
                                addJsonObject {
                                    put("type", "tool_result")
                                    put("tool_use_id", msg.toolCallId)
                                    put("content", msg.content)
                                }
                            } else {
                                if (msg.content.isNotEmpty()) {
                                    addJsonObject {
                                        put("type", "text")
                                        put("text", msg.content)
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
}
