package com.github.bboygf.over_code.llm

import com.github.bboygf.over_code.po.*
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
    private val  port: String
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
                    parameter("key", apiKey)
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

    override suspend fun chatStream(messages: List<LLMMessage>, onChunk: (String) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                val requestBody = buildGeminiRequest(messages)

                // 2. 发起流式请求
                // 注意：Gemini 默认返回 JSON 数组流，加上 &alt=sse 可以强制返回类似 OpenAI 的 Server-Sent Events 格式
                // 这样解析逻辑就和 Ollama 那个很像了
                client.preparePost("$baseUrl/$model:streamGenerateContent") {
                    parameter("key", apiKey)
                    parameter("alt", "sse") // 关键：强制使用 SSE 格式
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }.execute { response ->
                    val channel: ByteReadChannel = response.bodyAsChannel()

                    while (!channel.isClosedForRead) {
                        var line = channel.readUTF8Line() ?: break

                        if (line.isBlank()) continue
                        if (line.startsWith("data: ")) {
                            line = line.removePrefix("data: ")
                        }
                        // Gemini SSE 结束时不一定发 [DONE]，但如果解析失败通常意味着结束或错误
                        try {
                            val responseObj = Json { ignoreUnknownKeys = true }.decodeFromString<GeminiResponse>(line)

                            val text = responseObj.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                            if (!text.isNullOrEmpty()) {
                                onChunk(text)
                            }
                        } catch (e: Exception) {
                            // 忽略非 JSON 行
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                throw LLMException("流式调用 Gemini API 失败: ${e.message}", e)
            }
        }
    }

    /**
     * 辅助方法：将通用 LLMMessage 转换为 Gemini 格式
     */
    private fun buildGeminiRequest(messages: List<LLMMessage>): GeminiRequest {
        val geminiContents = messages.map { msg ->
            val parts = mutableListOf<GeminiPart>()

            // 1. 处理文本
            if (msg.content.isNotEmpty()) {
                parts.add(GeminiPart(text = msg.content))
            }

            // 2. 处理图片
            msg.images.forEach { imgData ->
                // Gemini 需要原始 base64，不带 "data:image/..." 前缀
                // 且必须指定 mimeType (这里简单假设为 jpeg，实际最好根据 header 判断)
                val (mimeType, rawBase64) = parseBase64Image(imgData)

                parts.add(
                    GeminiPart(
                        inlineData = GeminiBlob(
                            mimeType = mimeType,
                            data = rawBase64
                        )
                    )
                )
            }

            // 3. 映射角色 (Gemini 只有 "user" 和 "model")
            val role = if (msg.role == "assistant") "model" else "user"
            GeminiContent(role = role, parts = parts)
        }
        return GeminiRequest(contents = geminiContents)
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