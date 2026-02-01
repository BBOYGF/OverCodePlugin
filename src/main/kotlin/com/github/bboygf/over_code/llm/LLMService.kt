package com.github.bboygf.over_code.llm

import com.github.bboygf.over_code.po.GeminiFunctionCall
import com.github.bboygf.over_code.po.GeminiPart
import com.github.bboygf.over_code.po.GeminiTool
import com.github.bboygf.over_code.po.LLMMessage
import com.github.bboygf.over_code.services.ChatDatabaseService
import com.github.bboygf.over_code.vo.ModelConfigInfo
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project

/**
 * LLM 服务 - 根据配置创建和管理 LLM Provider
 */
class LLMService(private val project: Project) {

    private val logger = thisLogger()
    private val dbService = ChatDatabaseService.getInstance(project)

    // 缓存当前的 Provider 实例，避免每次对话都重新创建 HttpClient
    private var cachedProvider: LLMProvider? = null

    // 用于记录缓存时的配置指纹（ID 或 Hash），判断配置是否变更
    private var cachedConfigSignature: String? = null

    /**
     * 获取当前激活的 LLM Provider
     */
    fun getProvider(): LLMProvider? {
        val config = dbService.getActiveModelConfig() ?: return null
        val host = dbService.getValue("host") ?: "127.0.0.1"
        val port = dbService.getValue("port") ?: "10808"

        // 生成当前配置的“指纹”，用于检测配置是否被用户修改了
        val currentSignature =
            "${config.name}-${config.provider}-${config.modelName}-${config.apiKey}-${config.baseUrl}-${config.useProxy}-${host}-${port}"

        // 如果配置没有变且缓存存在，直接返回（性能优化）
        if (cachedProvider != null && cachedConfigSignature == currentSignature) {
            return cachedProvider
        }

        logger.info("配置已变更或初始化，正在创建新的 LLM Provider: ${config.provider}")

        // 创建新的 Provider
        val newProvider = try {
            createProvider(config, host, port)
        } catch (e: Exception) {
            logger.error("创建 LLM Provider 失败", e)
            throw LLMException("创建模型服务失败: ${e.message}")
        }

        // 更新缓存
        cachedProvider = newProvider
        cachedConfigSignature = currentSignature

        return newProvider
    }

    /**
     * 工厂方法：根据配置类型实例化具体的 Provider
     */
    private fun createProvider(config: ModelConfigInfo, host: String, port: String): LLMProvider {
        val providerType = config.provider.lowercase().trim()

        // 处理 Base URL，如果用户没填，某些 Provider 可能需要默认值，
        // 但通常 Provider 内部构造函数参数默认值会处理，或者在这里处理
        val safeBaseUrl = config.baseUrl.trim().ifEmpty {
            when (providerType) {
                "ollama" -> "http://localhost:11434"
                else -> "" // OpenAI 等通常由 Provider 内部处理默认值
            }
        }

        return when (providerType) {
            "ollama" -> {
                OllamaProvider(
                    baseUrl = safeBaseUrl,
                    model = config.modelName
                )
            }

            // Google Gemini (需要适配之前生成的 GeminiProvider)
            "gemini", "google" -> {
                require(config.apiKey.isNotBlank()) { "使用 Gemini 必须配置 API Key" }
                GeminiProvider(
                    baseUrl = safeBaseUrl,
                    apiKey = config.apiKey,
                    model = config.modelName.ifBlank { "gemini-3-flash-preview" },// 默认模型
                    useProxy = config.useProxy,
                    host = host,
                    port = port
                )
            }

            // OpenAI 标准兼容厂商 (DeepSeek, Moonshot, Qwen 等)
            "openai", "zhipu", "qwen", "deepseek", "moonshot", "custom" -> {
                // 对于云端模型，API Key 通常是必填的（除了本地搭建的兼容接口）
                if (providerType != "custom" && config.apiKey.isBlank()) {
                    throw LLMException("$providerType 需要配置 API Key")
                }

                OpenAICompatibleProvider(
                    baseUrl = safeBaseUrl,
                    apiKey = config.apiKey,
                    model = config.modelName,
                    useProxy = config.useProxy,
                    host = host,
                    port = port
                )
            }

            else -> {
                // 默认兜底策略：尝试用 OpenAI 格式
                logger.warn("未知的 Provider 类型 [$providerType]，尝试使用 OpenAI 兼容模式")
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


    /**
     * 发送消息并获取回复（同步调用）
     */
    suspend fun chat(messages: List<LLMMessage>): String {
        val provider = getProvider()
            ?: throw LLMException("未配置模型，请先在设置中配置并激活一个模型")

        // Provider 内部已经使用线程池，这里直接调用 chatSync
        return provider.chat(messages)
    }

    /**
     * 流式发送消息，逐块输出
     */
    suspend fun chatStream(
        messages: List<LLMMessage>,
        onChunk: (String) -> Unit,
        onToolCall: ((GeminiPart) -> Unit)?,
        tools: List<GeminiTool>?,
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
