package com.github.bboygf.over_code.actions

import com.github.bboygf.over_code.services.ChatDatabaseService
import com.github.bboygf.over_code.services.HomeViewModelService
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.wm.ToolWindowManager

/**
 * AI 助手动态菜单组 - 根据 Prompt 模板动态生成子菜单
 */
class AIAssistantActionGroup : ActionGroup() {
    
    private val LOG = Logger.getInstance(AIAssistantActionGroup::class.java)
    
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val project = e?.project ?: return emptyArray()
        val editor = e.getData(CommonDataKeys.EDITOR)
        val hasSelection = editor?.selectionModel?.hasSelection() ?: false
        
        if (!hasSelection) return emptyArray()
        
        return try {
            val dbService = ChatDatabaseService.getInstance(project)
            val prompts = dbService.getAllPromptTemplates()
            
            LOG.info("加载了 ${prompts.size} 个 Prompt 模板")
            
            // 为每个 Prompt 模板创建一个 Action
            // 注意：InsertToAIAction 已经在 plugin.xml 中静态注册了，会自动显示
            // 这里只返回动态生成的 Prompt Actions
            prompts.map { prompt ->
                DynamicPromptAction(prompt.key, prompt.name)
            }.toTypedArray()
        } catch (e: Exception) {
            LOG.error("加载 Prompt 模板失败", e)
            emptyArray()
        }
    }
    
    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val hasSelection = editor?.selectionModel?.hasSelection() ?: false
        // 只要有选中的文本，就显示菜单组
        e.presentation.isEnabledAndVisible = hasSelection
        
        if (hasSelection) {
            LOG.info("菜单组应该显示：有选中文本")
        }
    }
}

/**
 * 动态 Prompt Action
 */
class DynamicPromptAction(
    private val promptKey: String,
    private val promptName: String
) : AnAction(promptName) {
    
    private val LOG = Logger.getInstance(DynamicPromptAction::class.java)
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selectedText = editor.selectionModel.selectedText
        
        if (selectedText.isNullOrBlank()) {
            LOG.info("没有选中任何内容")
            return
        }
        
        // 获取文件信息
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        val fileName = psiFile?.name ?: "未知文件"
        val language = psiFile?.language?.displayName ?: "未知语言"
        
        // 使用 Prompt 模板渲染消息
        val dbService = ChatDatabaseService.getInstance(project)
        val messageToAI = try {
            dbService.renderPrompt(promptKey, mapOf(
                "code" to selectedText,
                "text" to selectedText,
                "fileName" to fileName,
                "language" to language
            ))
        } catch (ex: Exception) {
            LOG.warn("渲染 Prompt 失败: ${ex.message}")
            "请处理以下内容：\n\n$selectedText"
        }
        
        LOG.info("使用 Prompt: $promptKey, 发送内容长度: ${selectedText.length}")
        
        // 打开聊天窗口并发送消息
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("Over Code")
        
        toolWindow?.activate {
            project.getService(HomeViewModelService::class.java)
                ?.sendMessageToChat(messageToAI)
        }
    }
    
    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val hasSelection = editor?.selectionModel?.hasSelection() ?: false
        e.presentation.isEnabled = hasSelection
    }
}
