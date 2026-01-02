package com.github.bboygf.autocoderplugin.llm

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.io.HttpRequests
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Callable

/**
 * Ollama LLM Provider
 */
class OllamaProvider(
    private val baseUrl: String = "http://localhost:11434",
    private val model: String = "llama2"
) : LLMProvider {
    
    override suspend fun chat(messages: List<LLMMessage>): String {
        return chatSync(messages)
    }
    
    override fun chatSync(messages: List<LLMMessage>): String {
        return ApplicationManager.getApplication().executeOnPooledThread(Callable {
            try {
                val requestBody = buildRequestBody(messages)
                
                val response = HttpRequests.post("$baseUrl/api/chat", "application/json")
                    .connect { request ->
                        val output = request.connection.outputStream
                        output.write(requestBody.toByteArray(Charsets.UTF_8))
                        output.flush()
                        request.readString()
                    }
                
                parseResponse(response)
            } catch (e: Exception) {
                throw LLMException("调用 Ollama API 失败: ${e.message}", e)
            }
        }).get()
    }
    
    private fun buildRequestBody(messages: List<LLMMessage>): String {
        val json = JSONObject()
        json.put("model", model)
        json.put("stream", false)
        
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
