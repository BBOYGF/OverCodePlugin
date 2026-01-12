package com.github.bboygf.over_code.llm

import com.github.bboygf.over_code.services.ChatDatabaseService
import com.intellij.openapi.project.Project

/**
 * LLM 服务 - 根据配置创建和管理 LLM Provider
 */
class LLMService(private val project: Project) {

    private val dbService = ChatDatabaseService.getInstance(project)

    /**
     * 获取当前激活的 LLM Provider
     */
    fun getProvider(): LLMProvider? {
        val config = dbService.getActiveModelConfig() ?: return null

        return when (config.provider.lowercase()) {
            "ollama" -> {
                OllamaProvider(
                    baseUrl = config.baseUrl,
                    model = config.modelName
                )
            }

            "openai", "zhipu", "qwen", "deepseek", "moonshot", "custom" -> {
                OpenAICompatibleProvider(
                    baseUrl = config.baseUrl,
                    apiKey = config.apiKey,
                    model = config.modelName
                )
            }

            else -> {
                // 默认使用 OpenAI 兼容模式
                OpenAICompatibleProvider(
                    baseUrl = config.baseUrl,
                    apiKey = config.apiKey,
                    model = config.modelName
                )
            }
        }
    }

    /**
     * 发送消息并获取回复（同步调用）
     */
    suspend fun chatSync(messages: List<LLMMessage>): String {
        val provider = getProvider()
            ?: throw LLMException("未配置模型，请先在设置中配置并激活一个模型")

        // Provider 内部已经使用线程池，这里直接调用 chatSync
        return provider.chatAsync(messages)
    }

    /**
     * 流式发送消息，逐块输出
     */
    suspend fun chatStream(messages: List<LLMMessage>, onChunk: (String) -> Unit) {
        val provider = getProvider()
            ?: throw LLMException("未配置模型，请先在设置中配置并激活一个模型")

        provider.chatStream(messages, onChunk)
    }

    companion object {
        fun getInstance(project: Project): LLMService {
            return LLMService(project)
        }
    }
}
