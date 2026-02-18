package com.github.bboygf.over_code.llm

import com.github.bboygf.over_code.po.*
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
import kotlinx.serialization.encodeToString
import kotlin.coroutines.cancellation.CancellationException

class GeminiProvider(
    private val apiKey: String,
    private val model: String = "gemini-2.0-flash",
    private val useProxy: Boolean,
    private val baseUrl: String,
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
            Log.error("GeminiProvider: 自动获取代理设置失败，将尝试直连", e)
        }
    }

    private fun convertToGeminiTool(tool: LlmToolDefinition): GeminiTool {
        return GeminiTool(
            functionDeclarations = listOf(
                GeminiFunctionDeclaration(
                    name = tool.name,
                    description = tool.description,
                    parameters = tool.parameters
                )
            )
        )
    }

    private fun convertToLlmToolCall(part: GeminiPart, index: Int): LlmToolCall? {
        val fc = part.functionCall ?: return null
        return LlmToolCall(
            id = part.thoughtSignature,
            functionName = fc.name,
            arguments = fc.args.toString()
        )
    }

    override suspend fun chat(messages: List<LLMMessage>): String {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = buildGeminiRequest(messages, null)
                val response: GeminiResponse = client.post("$baseUrl/$model:generateContent") {
                    header("x-goog-api-key", apiKey)
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }.body()

                return@withContext response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            } catch (e: Exception) {
                Log.error("GeminiProvider chat方法调用失败", e)
                throw LLMException(parseGeminiError(e), e)
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
            var line = ""
            try {
                val geminiTools = tools?.map { convertToGeminiTool(it) }
                val requestBody = buildGeminiRequest(messages, geminiTools)

                client.preparePost("$baseUrl/$model:streamGenerateContent") {
                    header("x-goog-api-key", apiKey)
                    parameter("alt", "sse")
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }.execute { response ->
                    if (!response.status.isSuccess()) {
                        throw LLMException("Gemini API 响应错误: ${response.status}")
                    }

                    val channel = response.bodyAsChannel()
                    var callIndex = 0

                    try {
                        while (!channel.isClosedForRead) {
                            line = channel.readUTF8Line() ?: break
                            Log.info("Gemini Response: $line")
                            if (line.isEmpty() || !line.startsWith("data:")) continue

                            val data = line.substringAfter("data:").trim()
                            if (data == "[DONE]") break

                            try {
                                val responseObj =
                                    Json { ignoreUnknownKeys = true }.decodeFromString<GeminiResponse>(data)

                                val textChunks = mutableListOf<String>()
                                val thoughtChunks = mutableListOf<String>()
                                val geminiParts = mutableListOf<GeminiPart>()

                                responseObj.candidates?.forEach { candidate ->
                                    candidate.content?.parts?.forEach { part ->
                                        val thoughtValue = part.thought
                                        var isThoughtPart = false

                                        if (thoughtValue is JsonPrimitive) {
                                            if (thoughtValue.isString) {
                                                thoughtChunks.add(thoughtValue.content)
                                                isThoughtPart = true
                                            } else if (thoughtValue.content == "true") {
                                                isThoughtPart = true
                                                part.text?.let { thoughtChunks.add(it) }
                                            }
                                        }

                                        if (!isThoughtPart) {
                                            part.text?.let { textChunks.add(it) }
                                        }

                                        if (part.functionCall != null) {
                                            geminiParts.add(part)
                                        }
                                    }
                                }

                                if (textChunks.isEmpty() && geminiParts.isEmpty() && thoughtChunks.isEmpty()) {
                                    return@execute
                                }

                                withContext(Dispatchers.Main) {
                                    thoughtChunks.forEach { onThought?.invoke(it) }
                                    textChunks.forEach { onChunk(it) }
                                    geminiParts.forEach { part ->
                                        convertToLlmToolCall(part, callIndex++)?.let { call ->
                                            onToolCall?.invoke(call)
                                        }
                                    }
                                }

                            } catch (parseError: Exception) {
                                Log.info("解析 Gemini 响应块失败: ${parseError.message}")
                            }
                        }
                    } catch (eof: java.io.EOFException) {
                        Log.error("Gemini 流式响应正常结束")
                    } catch (e: Exception) {
                        Log.error("读取 Gemini 流式响应失败: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                throw LLMException(parseGeminiError(e), e)
            }
        }
    }

    private fun buildGeminiRequest(
        messages: List<LLMMessage>,
        tools: List<GeminiTool>? = null
    ): GeminiRequest {
        val systemMessage = messages.find { it.role == "system" }
        val systemInstruction = systemMessage?.let {
            GeminiContent(role = "system", parts = listOf(GeminiPart(text = it.content)))
        }

        val convertedMessages = messages.filter { it.role != "system" }.map { msg ->
            val parts = mutableListOf<GeminiPart>()
            val geminiRole = when (msg.role) {
                "assistant" -> "model"
                "tool", "function" -> "user"
                else -> "user"
            }
            // 为了节省 token 可以注销掉折行代码，也就是不上传思考过程到llm
            if (!msg.thought.isNullOrEmpty()) {
                parts.add(
                    GeminiPart(
                        thought = JsonPrimitive(true),
                        text = msg.thought
                    )
                )
            }

            if (msg.content.isNotEmpty() && msg.role != "tool" && msg.role != "function") {
                parts.add(GeminiPart(text = msg.content))
            }

            msg.images.forEach { imgData ->
                val (mimeType, rawBase64) = parseBase64Image(imgData)
                parts.add(GeminiPart(inlineData = GeminiBlob(mimeType, rawBase64)))
            }

            msg.toolCalls?.forEachIndexed { index, call ->
                parts.add(
                    GeminiPart(
                        functionCall = GeminiFunctionCall(
                            name = call.functionName,
                            args = try {
                                Json.parseToJsonElement(call.arguments) as JsonObject
                            } catch (e: Exception) {
                                buildJsonObject { }
                            }
                        ),
                        thoughtSignature = if (index == 0) msg.thoughtSignature else null
                    )
                )
            }

            if (msg.role == "tool" || msg.role == "function") {
                val funcName = msg.name ?: msg.toolCallId ?: "unknown"
                val jsonResponse = try {
                    Json.parseToJsonElement(msg.content) as JsonObject
                } catch (e: Exception) {
                    buildJsonObject { put("result", msg.content) }
                }
                parts.add(GeminiPart(functionResponse = GeminiFunctionResponse(funcName, jsonResponse)))
            }

            geminiRole to parts
        }.filter { it.second.isNotEmpty() }

        val finalContents = mutableListOf<GeminiContent>()
        if (convertedMessages.isNotEmpty()) {
            for (i in convertedMessages.indices) {
                val (role, parts) = convertedMessages[i]
                val prevContent = finalContents.lastOrNull()

                if (prevContent != null && prevContent.role == role) {
                    val mergedParts = prevContent.parts?.toMutableList() ?: mutableListOf()
                    mergedParts.addAll(parts)
                    finalContents[finalContents.lastIndex] = GeminiContent(role = role, parts = mergedParts)
                } else {
                    finalContents.add(GeminiContent(role = role, parts = parts))
                }
            }
        }

        return GeminiRequest(
            contents = finalContents,
            systemInstruction = systemInstruction,
            tools = tools,
            generationConfig = GeminiGenerationConfig(thinkingConfig = GeminiThinkingConfig(includeThoughts = true))
        ).also {
            Log.info("Gemini Request: ${Json.encodeToString(it)}")
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
     * 解析 Gemini API 错误，返回友好的错误消息
     * 根据 HTTP 状态码返回对应的中文提示
     */
    private fun parseGeminiError(e: Exception): String {
        // 尝试从 Ktor 异常中提取 HTTP 状态码
        val statusCode = extractHttpStatusCode(e)

        return when (statusCode) {
            400 -> "请求参数无效，请检查输入格式是否正确"
            403 -> "API权限不足，请检查API Key是否正确"
            404 -> "请求的资源未找到，请检查模型名称是否正确"
            429 -> "请求过于频繁，请稍后再试（已超出速率限制）"
            500 -> "Google服务器内部错误，请稍后重试"
            503 -> "服务暂时过载，请稍后重试"
            504 -> "请求处理超时，请减少输入内容或增加超时时间"
            else -> "调用Gemini API失败: ${e.message}"
        }
    }

    /**
     * 从异常中提取 HTTP 状态码
     */
    private fun extractHttpStatusCode(e: Exception): Int? {
        // 尝试从异常消息中提取状态码
        val message = e.message ?: return null

        // 常见 Ktor 异常模式: "Server returned(400 - Bad Request)"
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
}
