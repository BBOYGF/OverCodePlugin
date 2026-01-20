package com.github.bboygf.over_code.llm

import com.github.bboygf.over_code.po.*
import com.github.bboygf.over_code.po.GeminiBlob
import com.github.bboygf.over_code.po.GeminiContent
import com.github.bboygf.over_code.po.GeminiPart
import com.github.bboygf.over_code.po.GeminiRequest
import com.github.bboygf.over_code.po.GeminiResponse
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Google Gemini LLM Provider
 * 需要申请 API Key: https://aistudio.google.com/
 */
class GeminiProvider(
    private val apiKey: String,
    private val model: String = "gemini-2.5-flash",
    private val useProxy: Boolean,
    private val baseUrl: String,
    private val host: String,
    private val port: String
) : LLMProvider {

    private val logger = thisLogger()


    private val client = HttpClient(CIO) {
        // 如果使用代理那么启动代理
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

    /**
     * 使用 Java 标准 ProxySelector 获取代理
     * IntelliJ 平台已覆写了默认的 Selector，因此这会自动应用 IDE 的设置
     */
    private fun configureProxy(config: CIOEngineConfig) {
        try {
            // 1. 请求系统(IDE)选择适合该 URI 的代理列表
            // 2. 获取第一个有效代理
            val proxyUrl = "http://${host}:${port}"
            logger.info("GeminiProvider: 自动检测到 IDE 代理配置 -> $proxyUrl")

            config.proxy = ProxyBuilder.http(proxyUrl)

        } catch (e: Exception) {
            logger.warn("GeminiProvider: 自动获取代理设置失败，将尝试直连", e)
        }
    }

    override suspend fun chat(messages: List<LLMMessage>): String {
        return withContext(Dispatchers.IO) {
            try {
                // 1. 构建 Gemini 专用请求体
                val requestBody = buildGeminiRequest(messages)

                // 2. 发起请求 (注意 URL 格式和 API Key)
                // 格式: POST https://.../models/{model}:generateContent?key={apiKey}
                val response: GeminiResponse = client.post("$baseUrl/$model:generateContent") {
                    header("x-goog-api-key", apiKey)
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }.body()

                // 3. 解析响应
                val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: ""

                return@withContext text

            } catch (e: Exception) {
                throw LLMException("调用 Gemini API 失败: ${e.message}", e)
            }
        }
    }

    override suspend fun chatStream(
        messages: List<LLMMessage>,
        onChunk: (String) -> Unit,
        onToolCall: ((GeminiPart) -> Unit)?,
        tools: List<GeminiTool>?
    ) {
        withContext(Dispatchers.IO) {
            var line = ""
            try {
                val requestBody = buildGeminiRequest(messages, tools)
                // 2. 发起流式请求
                // 注意：Gemini 默认返回 JSON 数组流，加上 &alt=sse 可以强制返回类似 OpenAI 的 Server-Sent Events 格式
                // 这样解析逻辑就和 Ollama 那个很像了
                client.preparePost("$baseUrl/$model:streamGenerateContent") {
                    header("x-goog-api-key", apiKey)
                    parameter("alt", "sse") // 关键：强制使用 SSE 格式
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }.execute { response ->
                    val channel: ByteReadChannel = response.bodyAsChannel()

                    // 在 chatStream 的 execute 块中
                    while (!channel.isClosedForRead) {
                        line = channel.readUTF8Line() ?: break
                        println("line: $line")
                        // ... 解析 JSON ...
                        if (line.isEmpty()) continue // 忽略空行

                        if (line.startsWith("data:")) {
                            // 使用 substringAfter 确保无论是否有空格都能正确提取内容
                            line = line.substringAfter("data:").trim()
                        } else {
                            // 如果不是以 data: 开头的行（可能是注释或心跳），直接跳过
                            continue
                        }
                        if (line == "[DONE]") break

                        val responseObj = Json { ignoreUnknownKeys = true }.decodeFromString<GeminiResponse>(line)

                        // 关键：遍历所有的 Part，而不是只取第一个
                        responseObj.candidates?.forEach { candidate ->
                            candidate.content?.parts?.forEach { part ->
                                // 1. 处理文本片段
                                part.text?.let { onChunk(it) }

                                // 2. 处理函数调用（可能有多个）
                                part.functionCall?.let { call ->
                                    onToolCall?.invoke(part) // 这里会被触发多次
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                println("调用模型产生异常：${e.message} line:$line")
                if (e is CancellationException) throw e
                throw LLMException("流式调用 Gemini API 失败: ${e.message}", e)
            }
        }
    }

    /**
     * 辅助方法：将通用 LLMMessage 转换为 Gemini 格式
     */
    private fun buildGeminiRequest(
        messages: List<LLMMessage>,
        tools: List<GeminiTool>? = null,
        toolConfig: GeminiToolConfig? = null
    ): GeminiRequest {
        val geminiContents = messages.map { msg ->
            val parts = mutableListOf<GeminiPart>()
            when (msg.role) {
                // 情况 1: 用户发送的消息
                "user" -> {
                    if (msg.content.isNotEmpty()) {
                        parts.add(GeminiPart(text = msg.content))
                    }
                    // 图片处理...
                    msg.images.forEach { imgData ->
                        val (mimeType, rawBase64) = parseBase64Image(imgData)
                        parts.add(GeminiPart(inlineData = GeminiBlob(mimeType, rawBase64)))
                    }
                    GeminiContent(role = "user", parts = parts)
                }

                // 情况 2: 模型之前的回复（可能是文本，也可能是它发起的函数调用）
                "assistant" -> {
                    // 如果模型之前说过话
                    if (msg.content.isNotEmpty()) {
                        parts.add(GeminiPart(text = msg.content))
                    }
                    // 重点：如果模型之前发起了函数调用，必须放进历史里！
                    if (msg.functionCall != null) {
                        parts.add(GeminiPart(functionCall = msg.functionCall, thoughtSignature = msg.thoughtSignature))
                    }
                    GeminiContent(role = "model", parts = parts)
                }

                // 情况 3: 工具执行的结果（我们要发给模型的）
                // 对应前面说的 Msg 3
                "function", "tool" -> {
                    val funcName = msg.name ?: "unknown"
                    // 将内容转为 JSON 对象，Gemini 要求 response 是 JSON
                    val jsonResponse = try {
                        Json.parseToJsonElement(msg.content) as JsonObject
                    } catch (e: Exception) {
                        // 如果内容是纯文本（比如文件列表字符串），包装成 JSON
                        buildJsonObject { put("result", msg.content) }
                    }

                    parts.add(
                        GeminiPart(
                            functionResponse = GeminiFunctionResponse(
                                name = funcName,
                                response = jsonResponse
                            )
                        )
                    )
                    // Gemini API 要求 function response 的 role 必须是 "function"
                    GeminiContent(role = "function", parts = parts)
                }

                else -> GeminiContent(role = "user", parts = parts) // 默认回退
            }
        }

        return GeminiRequest(
            contents = geminiContents,
            tools = tools, // 只有第一条消息需要带 tools，或者每次带都行
            toolConfig = toolConfig
        )
    }

    /**
     * 简单的 Base64 解析，分离 mimeType 和 数据
     */
    private fun parseBase64Image(base64String: String): Pair<String, String> {
        return if (base64String.contains(",")) {
            val split = base64String.split(",", limit = 2)
            val header = split[0] // e.g., "data:image/png;base64"
            val data = split[1]
            val mime = header.substringAfter("data:").substringBefore(";")
            mime to data
        } else {
            "image/jpeg" to base64String // 默认回退
        }
    }
}