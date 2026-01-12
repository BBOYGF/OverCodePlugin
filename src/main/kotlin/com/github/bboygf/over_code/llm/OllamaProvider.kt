package com.github.bboygf.over_code.llm

import com.intellij.util.io.HttpRequests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Ollama LLM Provider
 */
class OllamaProvider(
    private val baseUrl: String = "http://localhost:11434",
    private val model: String = "llama2"
) : LLMProvider {
    
    override suspend fun chat(messages: List<LLMMessage>): String {
        return chatAsync(messages)
    }
    
    override suspend fun chatAsync(messages: List<LLMMessage>): String {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = buildRequestBody(messages)

                // HttpRequests 是同步的，但在 Dispatchers.IO 中运行是安全的
                val response = HttpRequests.post("$baseUrl/api/chat", "application/json")
                    .connect { request ->
                        val output = request.connection.outputStream
                        output.write(requestBody.toByteArray(Charsets.UTF_8))
                        output.flush()
                        request.readString()
                    }

                parseResponse(response)
            } catch (e: Exception) {
                // 建议使用插件专用的异常处理
                throw LLMException("调用 Ollama API 失败: ${e.message}", e)
            }
        }
    }
    
    /**
     * 流式聊天 - 逐字输出
     */
    override suspend fun chatStream(messages: List<LLMMessage>, onChunk: (String) -> Unit) {
        try {
            val requestBody = buildRequestBody(messages, stream = true)
            val url = URL("$baseUrl/api/chat")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.doInput = true
            
            // 发送请求
            connection.outputStream.use { output ->
                output.write(requestBody.toByteArray(Charsets.UTF_8))
                output.flush()
            }
            
            // 读取流式响应
            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val chunk = line ?: continue
                    try {
                        val json = JSONObject(chunk)
                        val done = json.optBoolean("done", false)
                        
                        if (!done) {
                            val message = json.optJSONObject("message")
                            val content = message?.optString("content", "")
                            if (!content.isNullOrEmpty()) {
                                onChunk(content)
                            }
                        } else {
                            break
                        }
                    } catch (e: Exception) {
                        // 忽略解析错误的行
                    }
                }
            }
        } catch (e: Exception) {
            throw LLMException("流式调用 Ollama API 失败: ${e.message}", e)
        }
    }
    
    private fun buildRequestBody(messages: List<LLMMessage>, stream: Boolean = false): String {
        val json = JSONObject()
        json.put("model", model)
        json.put("stream", stream)
        
        val messagesArray = JSONArray()
        messages.forEach { msg ->
            val messageObj = JSONObject()
            messageObj.put("role", msg.role)
            messageObj.put("content", msg.content)
            messagesArray.put(messageObj)
        }
        json.put("messages", messagesArray)
        
        return json.toString()
    }
    
    private fun parseResponse(response: String): String {
        try {
            val json = JSONObject(response)
            val message = json.getJSONObject("message")
            return message.getString("content")
        } catch (e: Exception) {
            throw LLMException("解析 Ollama 响应失败: ${e.message}\n原始响应: $response", e)
        }
    }
}
