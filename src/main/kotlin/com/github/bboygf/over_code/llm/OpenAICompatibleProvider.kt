package com.github.bboygf.over_code.llm

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.io.HttpRequests
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable

/**
 * OpenAI 兼容的 LLM Provider
 * 支持：OpenAI, 智谱GLM, 通义千问, Deepseek, Moonshot 等
 */
class OpenAICompatibleProvider(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String
) : LLMProvider {
    
    override suspend fun chat(messages: List<LLMMessage>): String {
        return chatSync(messages)
    }
    
    override fun chatSync(messages: List<LLMMessage>): String {
        return ApplicationManager.getApplication().executeOnPooledThread(Callable {
            try {
                val requestBody = buildRequestBody(messages)
                
                val response = HttpRequests.post("$baseUrl/chat/completions", "application/json")
                    .tuner { connection ->
                        connection.setRequestProperty("Authorization", "Bearer $apiKey")
                    }
                    .connect { request ->
                        val output = request.connection.outputStream
                        output.write(requestBody.toByteArray(Charsets.UTF_8))
                        output.flush()
                        request.readString()
                    }
                
                parseResponse(response)
            } catch (e: Exception) {
                throw LLMException("调用 LLM API 失败: ${e.message}", e)
            }
        }).get()
    }
    
    /**
     * 流式聊天 - 逐字输出
     */
    override suspend fun chatStream(messages: List<LLMMessage>, onChunk: (String) -> Unit) {
        try {
            val requestBody = buildRequestBody(messages, stream = true)
            val url = URL("$baseUrl/chat/completions")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
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
                    if (chunk.startsWith("data: ")) {
                        val data = chunk.substring(6).trim()
                        if (data == "[DONE]") break
                        
                        try {
                            val json = JSONObject(data)
                            val choices = json.getJSONArray("choices")
                            if (choices.length() > 0) {
                                val delta = choices.getJSONObject(0).optJSONObject("delta")
                                val content = delta?.optString("content", "")
                                if (!content.isNullOrEmpty()) {
                                    onChunk(content)
                                }
                            }
                        } catch (e: Exception) {
                            // 忽略解析错误的行
                        }
                    }
                }
            }
        } catch (e: Exception) {
            throw LLMException("流式调用 LLM API 失败: ${e.message}", e)
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
            val choices = json.getJSONArray("choices")
            if (choices.length() == 0) {
                throw LLMException("API 返回结果为空")
            }
            
            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.getJSONObject("message")
            return message.getString("content")
        } catch (e: Exception) {
            throw LLMException("解析 API 响应失败: ${e.message}\n原始响应: $response", e)
        }
    }
}
