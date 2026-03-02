package com.github.bboygf.over_code.actions

import com.github.bboygf.over_code.services.HomeViewModelService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.wm.ToolWindowManager

/**
 * 将选中的异常信息插入到聊天窗口，辅助 AI 分析异常
 */
class InsertExceptionToAIAction : AnAction() {
    private val LOG = Logger.getInstance(InsertExceptionToAIAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return

        // 获取选中的文本
        val selectedText = editor.selectionModel.selectedText
        if (selectedText.isNullOrBlank()) {
            LOG.info("没有选中任何异常信息")
            return
        }

        // 尝试检测异常类型
        val exceptionType = detectExceptionType(selectedText)
        val exceptionMessage = extractExceptionMessage(selectedText)

        // 构建发送给 AI 的消息
        val messageToAI = buildString {
            appendLine("## 异常信息")
            appendLine()
            if (exceptionType != null) {
                appendLine("**异常类型**: `$exceptionType`")
            }
            if (exceptionMessage != null) {
                appendLine("**异常消息**: $exceptionMessage")
            }
            appendLine()
            appendLine("### 堆栈跟踪:")
            appendLine("```")
            appendLine(selectedText.trim())
            appendLine("```")
            appendLine()
            appendLine("请帮我分析这个异常的原因，并提供解决方案。")
        }

        LOG.info("准备插入异常到聊天输入框，异常类型: $exceptionType")

        // 打开或激活 Over Code 工具窗口
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("Over Code")

        if (toolWindow != null) {
            toolWindow.activate {
                project.getService(HomeViewModelService::class.java)
                    ?.insertTextToChat(messageToAI)
            }
        }
    }

    private fun detectExceptionType(text: String): String? {
        // 匹配常见的异常类型: java.lang.NullPointerException, kotlin.NullPointerException 等
        val pattern = Regex("(?:java\\.lang\\.|kotlin\\.|[a-zA-Z_]+\\.)(\\w+Exception|\\w+Error):")
        return pattern.find(text)?.let { it.groupValues.getOrNull(1) }
    }

    private fun extractExceptionMessage(text: String): String? {
        // 提取异常消息（冒号后面的内容）
        val pattern = Regex("(?:java\\.lang\\.|kotlin\\.|[a-zA-Z_]+\\.)(\\w+Exception|\\w+Error):\\s*(.+)")
        return pattern.find(text)?.groupValues?.getOrNull(2)?.trim()
    }

    override fun update(e: AnActionEvent) {
        // 检查是否有选中文本
        val editor = e.getData(CommonDataKeys.EDITOR)
        val hasSelection = editor?.selectionModel?.hasSelection() ?: false
        e.presentation.isEnabled = hasSelection
    }
}