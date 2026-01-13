package com.github.bboygf.over_code.actions

import com.github.bboygf.over_code.llm.LLMService
import com.github.bboygf.over_code.po.LLMMessage
import com.github.bboygf.over_code.services.ChatDatabaseService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 在选中处下方弹出气泡显示翻译结果
 */
class QuickTranslateAction : AnAction() {
    private val LOG = Logger.getInstance(QuickTranslateAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selectedText = editor.selectionModel.selectedText ?: return

        if (selectedText.isBlank()) return

        // 1. 显示加载中的气泡
        val factory = JBPopupFactory.getInstance()
        val balloonBuilder = factory.createHtmlTextBalloonBuilder("正在翻译...", MessageType.INFO, null)
        val loadingBalloon = balloonBuilder.createBalloon()

        val popupLocation = factory.guessBestPopupLocation(editor)
        loadingBalloon.show(popupLocation, Balloon.Position.below)
        currentThreadCoroutineScope().launch {
            // 2. 异步调用 LLM
            try {
                val llmService = LLMService.getInstance(project)
                val dbService = ChatDatabaseService.getInstance(project)

                val prompt = try {
                    dbService.renderPrompt("translate_quick", mapOf("text" to selectedText))
                } catch (ex: Exception) {
                    "你是一个专业的翻译助手。请直接输出以下内容的中文翻译结果，不需要任何开场白或解释：\n\n$selectedText"
                }

                val result = llmService.chat(listOf(LLMMessage("user", prompt)))

                // 3. 回到 UI 线程更新结果
                withContext(Dispatchers.EDT) {
                    loadingBalloon.hide()

                    val resultBalloon = factory.createHtmlTextBalloonBuilder(
                        result.replace("\n", "<br/>"),
                        MessageType.INFO,
                        null
                    )
                        .setFadeoutTime(10000) // 10秒后自动消失
                        .setHideOnAction(true)
                        .setHideOnClickOutside(true)
                        .setHideOnKeyOutside(true)
                        .createBalloon()
                    resultBalloon.show(popupLocation, Balloon.Position.below)
                }
            } catch (ex: Exception) {
                LOG.error("翻译失败", ex)
                ApplicationManager.getApplication().invokeLater {
                    loadingBalloon.hide()
                    factory.createHtmlTextBalloonBuilder("翻译失败: ${ex.message}", MessageType.ERROR, null)
                        .createBalloon()
                        .show(popupLocation, Balloon.Position.below)
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = editor?.selectionModel?.hasSelection() ?: false
    }
}
