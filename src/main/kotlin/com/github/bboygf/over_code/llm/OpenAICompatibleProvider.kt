package com.github.bboygf.over_code.llm

import com.github.bboygf.over_code.po.LLMMessage
import com.github.bboygf.over_code.po.OpenAIContentPart
import com.github.bboygf.over_code.po.OpenAIImageUrl
import com.github.bboygf.over_code.po.OpenAIMessage
import com.github.bboygf.over_code.po.OpenAIRequest
import com.github.bboygf.over_code.po.OpenAIResponse
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
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

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
                ignoreUnknownKeys = true // 忽略响应中多余的字段（重要！）
                encodeDefaults = true    // 编码默认值
                isLenient = true
            })
        }
        install(HttpTimeout) { requestTimeoutMillis = 100000 }
    }

    /**
     * 使用 Java 标准 ProxySelector 获取代理
     * IntelliJ 平台已覆写了默认的 Selector，因此这会自动应用 IDE 的设置
     */
    private fun configureProxy(config: CIOEngineConfig) {
        try {
            // 后期改为从配置文件获取
            val proxyUrl = "http://${host}:${port}"
            logger.info("GeminiProvider: 自动检测到 IDE 代理配置 -> $proxyUrl")
            config.proxy = ProxyBuilder.http(proxyUrl)
        } catch (e: Exception) {
            logger.warn("GeminiProvider: 自动获取代理设置失败，将尝试直连", e)
        }
    }

    override suspend fun chat(messages: List<LLMMessage>): String {
        return withContext(Dispatchers.IO) {
            val messageDtos = messages.map { msg ->
                if (msg.images.isEmpty()) {
                    OpenAIMessage(
                        role = msg.role,
                        content = JsonPrimitive(msg.content),
                    )
                } else {
                    val contentList = mutableListOf<JsonElement>()
                    if (msg.content.isNotEmpty()) {
                        contentList.add(
                            Json.encodeToJsonElement(
                                OpenAIContentPart(type = "text", text = msg.content)
                            )
                        )
                    }
                    msg.images.forEach { imgData ->
                        // 自动判断是否需要加 base64 前缀 (如果已经是 URL 则不用)
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
                    OpenAIMessage(
                        role = msg.role,
                        content = JsonArray(contentList) // 包装成 JsonArray
                    )
                }
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
                val content: String = when (val c = response.choices.first().message?.content) {
                    is JsonPrimitive -> c.content // 如果是简单字符串
                    is JsonArray -> "" // 响应流中通常不包含图片数组，暂忽略
                    else -> "" // 如果是 JsonNull 或不存在
                }
                return@withContext content
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
            val messageDtos = messages.map { msg ->
                if (msg.images.isEmpty()) {
                    OpenAIMessage(
                        role = msg.role,
                        content = JsonPrimitive(msg.content),
                    )
                } else {
                    val contentList = mutableListOf<JsonElement>()
                    if (msg.content.isNotEmpty()) {
                        contentList.add(
                            Json.encodeToJsonElement(
                                OpenAIContentPart(type = "text", text = msg.content)
                            )
                        )
                    }
                    msg.images.forEach { imgData ->
                        // 自动判断是否需要加 base64 前缀 (如果已经是 URL 则不用)
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
                    OpenAIMessage(
                        role = msg.role,
                        content = JsonArray(contentList) // 包装成 JsonArray
                    )
                }
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
                            val content: String? = when (val c = delta?.content) {
                                is JsonPrimitive -> c.content // 如果是简单字符串
                                is JsonArray -> "" // 响应流中通常不包含图片数组，暂忽略
                                else -> null // 如果是 JsonNull 或不存在
                            }
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
