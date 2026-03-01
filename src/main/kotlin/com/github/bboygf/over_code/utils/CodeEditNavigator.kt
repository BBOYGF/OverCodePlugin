package com.github.bboygf.over_code.utils

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem

/**
 * 代码修改导航器 - 提供跳转高亮和差异对比功能
 */
object CodeEditNavigator {

    private val LOG = Log

    /**
     * 跳转到修改位置并高亮显示
     *
     * @param project 项目
     * @param filePath 文件路径
     * @param startLine 起始行号 (从1开始)
     * @param endLine 结束行号 (从1开始)
     */
    fun navigateAndHighlight(
        project: Project,
        filePath: String,
        startLine: Int,
        endLine: Int
    ) {
        ApplicationManager.getApplication().invokeLater {
            try {
                // 1. 找到文件
                val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath)
                if (virtualFile == null) {
                    LOG.warn("未找到文件: $filePath")
                    return@invokeLater
                }

                // 2. 打开文件
                val fileEditorManager = FileEditorManager.getInstance(project)
                val fileEditors = fileEditorManager.openFile(virtualFile, true)

                // 找到 TextEditor 并获取 Editor
                val textEditor = fileEditors.filterIsInstance<TextEditor>().firstOrNull()
                val editor = textEditor?.editor ?: run {
                    LOG.warn("无法打开文本编辑器")
                    return@invokeLater
                }

                // 3. 计算偏移量
                val document = editor.document
                val lineCount = document.lineCount
                val startOffset = if (startLine > 0 && startLine <= lineCount) {
                    document.getLineStartOffset(startLine - 1)
                } else {
                    0
                }
                val endOffset = if (endLine > 0 && endLine <= lineCount) {
                    document.getLineEndOffset(endLine - 1)
                } else {
                    document.textLength
                }

                // 4. 滚动到修改位置并选中
                editor.scrollingModel.scrollTo(editor.offsetToLogicalPosition(startOffset), ScrollType.CENTER)
                editor.caretModel.moveToOffset(startOffset)
                editor.selectionModel.setSelection(startOffset, endOffset)

                // 5. 高亮显示
                highlightRange(editor, startOffset, endOffset)

                LOG.info("已跳转到修改位置: $filePath, 行号: $startLine-$endLine")
            } catch (e: Exception) {
                LOG.error("跳转高亮失败: ${e.message}", e)
            }
        }
    }

    /**
     * 高亮显示指定范围 - 使用行高亮
     */
    private fun highlightRange(editor: Editor, startOffset: Int, endOffset: Int) {
        try {
            // 获取起始行号
            val startLine = editor.document.getLineNumber(startOffset)

            // 使用行高亮
            val highlighter = editor.markupModel.addLineHighlighter(
                com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey("EDIT_HIGHLIGHT"),
                startLine,
                HighlighterLayer.LAST
            )

            // 5秒后自动移除高亮
            ApplicationManager.getApplication().executeOnPooledThread {
                Thread.sleep(5000)
                ApplicationManager.getApplication().invokeLater {
                    editor.markupModel.removeHighlighter(highlighter)
                }
            }
        } catch (e: Exception) {
            LOG.error("高亮显示失败: ${e.message}", e)
        }
    }

    /**
     * 显示修改前后的差异对比
     *
     * @param project 项目
     * @param oldContent 修改前内容
     * @param newContent 修改后内容
     * @param title 对比标题
     */
    fun showDiff(
        project: Project,
        oldContent: String,
        newContent: String,
        title: String = "代码修改对比"
    ) {
        ApplicationManager.getApplication().invokeLater {
            try {
                val diffContentFactory = DiffContentFactory.getInstance()
                val oldDiffContent = diffContentFactory.create(oldContent)
                val newDiffContent = diffContentFactory.create(newContent)

                val diffRequest = SimpleDiffRequest(
                    title,
                    oldDiffContent,
                    newDiffContent,
                    "修改前",
                    "修改后"
                )

                DiffManager.getInstance().showDiff(project, diffRequest)
                LOG.info("已显示差异对比")
            } catch (e: Exception) {
                LOG.error("显示差异对比失败: ${e.message}", e)
            }
        }
    }

    /**
     * 跳转到文件并显示差异对比
     *
     * @param project 项目
     * @param filePath 文件路径
     * @param oldContent 修改前内容
     * @param newContent 修改后内容
     * @param startLine 起始行号
     * @param endLine 结束行号
     */
    fun navigateAndShowDiff(
        project: Project,
        filePath: String,
        oldContent: String,
        newContent: String,
        startLine: Int,
        endLine: Int
    ) {
        // 先跳转高亮
        navigateAndHighlight(project, filePath, startLine, endLine)

        // 延迟显示差异对比（等待文件打开完成）
        ApplicationManager.getApplication().invokeLater {
            Thread.sleep(300)
            showDiff(project, oldContent, newContent)
        }
    }
}