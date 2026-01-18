package com.github.bboygf.over_code.utils

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile

object ProjectFileUtils {
    /**
     * 获取项目下所有文件的列表，并生成 Markdown 格式字符串
     * 格式：Markdown 表格
     */
    fun exportToMarkdown(project: Project): String {
        val sb = StringBuilder()

        // 1. 写入 Markdown 表头
        sb.append("# Project Files Report\n\n")
        sb.append("Project Name: **${project.name}**\n\n")
        sb.append("| File Name | Absolute Path |\n")
        sb.append("| :--- | :--- |\n")

        val fileIndex = ProjectRootManager.getInstance(project).fileIndex
        val fileTypeManager = FileTypeManager.getInstance()

        // 2. 使用读操作遍历文件系统
        // iterateContent 会遍历项目 Content Root 下的所有文件（包含子模块）
        runReadAction {
            fileIndex.iterateContent { virtualFile ->
                if (shouldInclude(virtualFile, fileTypeManager)) {
                    // 获取文件名和路径
                    val name = virtualFile.name
                    val path = virtualFile.path // VirtualFile 的 path 通常就是绝对路径

                    // 3. 拼接到 Markdown 表格中
                    sb.append("| $name | $path |\n")
                }
                true // 返回 true 继续遍历，false 停止遍历
            }
        }

        return sb.toString()
    }

    /**
     * 过滤逻辑：决定哪些文件需要被包含
     */
    private fun shouldInclude(file: VirtualFile, fileTypeManager: FileTypeManager): Boolean {
        // 排除目录，只列出文件
        if (file.isDirectory) return false

        // 排除被 IDEA 忽略的文件（如 .DS_Store, .git 等）
        if (fileTypeManager.isFileIgnored(file)) return false

        // (可选) 可以在这里添加自定义过滤，例如排除 .class 文件或 .jar 文件
        // if (file.extension == "class" || file.extension == "jar") return false

        return true
    }
}