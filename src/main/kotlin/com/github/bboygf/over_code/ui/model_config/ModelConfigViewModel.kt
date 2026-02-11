package com.github.bboygf.over_code.ui.model_config

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.bboygf.over_code.services.ChatDatabaseService
import com.github.bboygf.over_code.vo.ModelConfigInfo

/**
 * 模型配置界面的ViewModel
 * 负责管理模型配置的增删改查操作
 */
class ModelConfigViewModel(
    private val dbService: ChatDatabaseService
) {
    // UI状态
    var modelConfigs by mutableStateOf(listOf<ModelConfigInfo>())
        private set
    
    var selectedIndex by mutableStateOf<Int?>(null)
        private set
    
    var showAddDialog by mutableStateOf(false)
        private set
    
    var editingConfig by mutableStateOf<ModelConfigInfo?>(null)
        private set
    
    init {
        loadModelConfigs()
    }
    
    /**
     * 加载所有模型配置
     */
    fun loadModelConfigs() {
        modelConfigs = dbService.getAllModelConfigs()
    }
    
    /**
     * 选中某个模型配置
     */
    fun selectConfig(index: Int) {
        selectedIndex = index
    }
    
    /**
     * 显示添加对话框
     */
    fun showAddDialog() {
        showAddDialog = true
    }
    
    /**
     * 隐藏添加对话框
     */
    fun hideAddDialog() {
        showAddDialog = false
    }
    
    /**
     * 开始编辑配置
     */
    fun startEditConfig() {
        selectedIndex?.let { index ->
            if (index < modelConfigs.size) {
                editingConfig = modelConfigs[index]
            }
        }
    }
    
    /**
     * 取消编辑
     */
    fun cancelEdit() {
        editingConfig = null
    }
    
    /**
     * 添加新的模型配置
     */
    fun addModelConfig(config: ModelConfigInfo) {
        dbService.addModelConfig(config)
        loadModelConfigs()

        hideAddDialog()
    }
    
    /**
     * 更新模型配置
     */
    fun updateModelConfig(oldModelId: String, newConfig: ModelConfigInfo) {
        dbService.updateModelConfig(oldModelId, newConfig)
        loadModelConfigs()
        cancelEdit()
    }
    
    /**
     * 删除选中的模型配置
     */
    fun deleteSelectedConfig() {
        selectedIndex?.let { index ->
            if (index < modelConfigs.size) {
                dbService.deleteModelConfig(modelConfigs[index].modelId)
                loadModelConfigs()
                selectedIndex = null
            }
        }
    }
    
    /**
     * 设置选中的模型为当前激活模型
     */
    fun setSelectedAsActive() {
        selectedIndex?.let { index ->
            if (index < modelConfigs.size) {
                dbService.setActiveModel(modelConfigs[index].modelId)
                loadModelConfigs()
            }
        }
    }
    
    /**
     * 获取提供商预设配置
     */
    fun getProviderPreset(provider: String): Pair<String, String> {
        return when (provider) {
            "openai" -> "https://api.openai.com/v1" to "gpt-3.5-turbo"
            "gemini" -> "https://generativelanguage.googleapis.com/v1beta/models" to "gemini-3-flash-preview"
            "minmax" -> "https://api.minimaxi.com/v1" to "MiniMax-M2.1"
            "ollama" -> "http://localhost:11434" to "llama2"
            "zhipu" -> "https://open.bigmodel.cn/api/paas/v4" to "glm-4"
            "qwen" -> "https://dashscope.aliyuncs.com/compatible-mode/v1" to "qwen-plus"
            "deepseek" -> "https://api.deepseek.com/v1" to "deepseek-chat"
            "moonshot" -> "https://api.moonshot.cn/v1" to "moonshot-v1-8k"
            "anthropic" -> "https://api.anthropic.com/v1/messages" to "claude-opus-4-6"
            else -> "" to ""
        }
    }
}
