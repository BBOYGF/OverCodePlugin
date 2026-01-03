package com.github.bboygf.over_code.actions

import com.github.bboygf.over_code.ui.toolWindow.ChatViewModel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.wm.ToolWindowManager

/**
 * 将选中的代码发送到聊天窗口询问AI
 */
class AskAIAction : AnAction() {
    private val LOG = Logger.getInstance(AskAIAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        
        // 获取选中的文本
        val selectedText = editor.selectionModel.selectedText
        
        if (selectedText.isNullOrBlank()) {
            LOG.info("没有选中任何代码")
            return
        }
        
        // 获取文件信息（可选，用于提供上下文）
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        val fileName = psiFile?.name ?: "未知文件"
        val language = psiFile?.language?.displayName ?: "未知语言"
        
        // 构建发送给AI的消息，添加"解释代码"前缀
        val messageToAI = buildString {
            appendLine("请帮我解释一下这段代码的意思：")
            appendLine()
            appendLine("文件名：$fileName")
            appendLine("语言：$language")
            appendLine()
            appendLine("```$language")
            appendLine(selectedText)
            appendLine("```")
        }
        
        LOG.info("准备发送代码到聊天窗口，长度: ${selectedText.length}")
        
        // 打开或激活 Over Code 工具窗口
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("Over Code")
        
        if (toolWindow != null) {
            // 激活工具窗口
            toolWindow.activate {
                // 工具窗口激活后，发送消息
                project.getService(ChatViewModelHolder::class.java)
                    ?.sendMessageToChat(messageToAI)
            }
        }
    }
    
    override fun update(e: AnActionEvent) {
        // 只有在编辑器中选中了文本时，才启用此操作
        val editor = e.getData(CommonDataKeys.EDITOR)
        val hasSelection = editor?.selectionModel?.hasSelection() ?: false
        e.presentation.isEnabled = hasSelection
    }
}

/**
 * 用于在 Project 范围内持有 ChatViewModel 实例的服务
 */
@Service(Service.Level.PROJECT)
class ChatViewModelHolder {
    var viewModel: ChatViewModel? = null
    
    fun sendMessageToChat(message: String) {
        viewModel?.sendMessageFromExternal(message)
    }
}
