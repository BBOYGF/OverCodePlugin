package com.github.bboygf.over_code.llm

import com.github.bboygf.over_code.po.LLMMessage
import com.github.bboygf.over_code.po.LlmToolCall
import com.github.bboygf.over_code.po.LlmToolDefinition
import com.github.bboygf.over_code.services.ChatDatabaseService
import com.github.bboygf.over_code.vo.ModelConfigInfo
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project

class LLMService(private val project: Project) {

    private val logger = thisLogger()
    private val dbService = ChatDatabaseService.getInstance(project)

    private var cachedProvider: LLMProvider? = null
    private var cachedConfigSignature: String? = null

    fun getProvider(): LLMProvider? {
        val config = dbService.getActiveModelConfig() ?: return null
        val host = dbService.getValue("host") ?: "127.0.0.1"
        val port = dbService.getValue("port") ?: "10808"

        val currentSignature =
            "${config.name}-${config.provider}-${config.modelName}-${config.apiKey}-${config.baseUrl}-${config.useProxy}-${host}-${port}"

        if (cachedProvider != null && cachedConfigSignature == currentSignature) {
            return cachedProvider
        }

        logger.info("配置已变更或初始化，正在创建新的 LLM Provider: ${config.provider}")

        val newProvider = try {
            createProvider(config, host, port)
        } catch (e: Exception) {
            logger.error("创建 LLM Provider 失败", e)
            throw LLMException("创建模型服务失败: ${e.message}")
        }

        cachedProvider = newProvider
        cachedConfigSignature = currentSignature

        return newProvider
    }

    private fun createProvider(config: ModelConfigInfo, host: String, port: String): LLMProvider {
        val providerType = config.provider.lowercase().trim()

        val safeBaseUrl = config.baseUrl.trim().ifEmpty {
            when (providerType) {
                "ollama" -> "http://localhost:11434"
                else -> ""
            }
        }

        return when (providerType) {
            "ollama" -> {
                OllamaProvider(
                    baseUrl = safeBaseUrl,
                    model = config.modelName
                )
            }

            "claude", "anthropic" -> {
                ClaudeProvider(
                    apiKey = config.apiKey,
                    model = config.modelName.ifBlank { "claude-3-5-sonnet-20240620" },
                    baseUrl = safeBaseUrl.ifBlank { "https://api.anthropic.com/v1/messages" },
                    useProxy = config.useProxy,
                    host = host,
                    port = port
                )
            }

            "gemini", "google" -> {
                require(config.apiKey.isNotBlank()) { "使用 Gemini 必须配置 API Key" }
                val defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/models"
                GeminiProvider(
                    baseUrl = safeBaseUrl.ifBlank { defaultBaseUrl },
                    apiKey = config.apiKey,
                    model = config.modelName.ifBlank { "gemini-2.0-flash" },
                    useProxy = config.useProxy,
                    host = host,
                    port = port
                )
            }

            else -> {
                OpenAICompatibleProvider(
                    baseUrl = safeBaseUrl,
                    apiKey = config.apiKey,
                    model = config.modelName,
                    useProxy = config.useProxy,
                    host = host,
                    port = port
                )
            }
        }
    }

    suspend fun chat(messages: List<LLMMessage>): String {
        val provider = getProvider()
            ?: throw LLMException("未配置模型，请先在设置中配置并激活一个模型")

        return provider.chat(messages)
    }

    suspend fun chatStream(
        messages: List<LLMMessage>,
        onChunk: (String) -> Unit,
        onToolCall: ((LlmToolCall) -> Unit)?,
        tools: List<LlmToolDefinition>?,
        onThought: ((String) -> Unit)? = null
    ) {
        val provider = getProvider()
            ?: throw LLMException("未配置模型，请先在设置中配置并激活一个模型")

        provider.chatStream(messages, onChunk, onToolCall, tools, onThought)
    }

    companion object {
        fun getInstance(project: Project): LLMService {
            return LLMService(project)
        }
    }
}
