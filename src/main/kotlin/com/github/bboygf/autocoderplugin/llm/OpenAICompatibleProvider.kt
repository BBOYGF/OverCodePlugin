package com.github.bboygf.autocoderplugin.llm

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.io.HttpRequests
import org.json.JSONArray
import org.json.JSONObject
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
    
    private fun buildRequestBody(messages: List<LLMMessage>): String {
        val json = JSONObject()
        json.put("model", model)
        
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
