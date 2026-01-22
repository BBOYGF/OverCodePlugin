package com.github.bboygf.over_code.utils

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.io.IOException
import java.io.File

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

    /**
     * 1. 创建文件或目录
     *
     * @param project 当前项目对象
     * @param absolutePath 文件的绝对路径
     * @param isDirectory true为创建目录，false为创建文件
     * @return 创建成功的 VirtualFile，如果失败则返回 null
     */
    fun createFileOrDir(project: Project, absolutePath: String, isDirectory: Boolean): VirtualFile? {
        // 将路径转换为系统无关路径 (处理 Windows 反斜杠问题)
        val systemIndependentPath = FileUtil.toSystemIndependentName(absolutePath)

        var result: VirtualFile? = null

        // 所有写操作必须在 WriteCommandAction 中执行，以支持 Undo 并确保线程安全
        WriteCommandAction.runWriteCommandAction(project) {
            try {
                if (isDirectory) {
                    // 创建目录（如果父目录不存在会自动创建）
                    result = VfsUtil.createDirectoryIfMissing(systemIndependentPath)
                } else {
                    // 创建文件
                    val file = File(systemIndependentPath)
                    // 确保父目录存在
                    val parentDir = VfsUtil.createDirectoryIfMissing(file.parent)
                    if (parentDir != null) {
                        // 在父目录下查找或创建子文件
                        val existingFile = parentDir.findChild(file.name)
                        result = existingFile ?: parentDir.createChildData(this, file.name)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return result
    }

    /**
     * 2. 根据绝对路径删除文件或目录
     *
     * @param project 当前项目
     * @param absolutePath 要删除的绝对路径
     */
    fun deleteFile(project: Project, absolutePath: String) {
        val path = FileUtil.toSystemIndependentName(absolutePath)

        // 先尝试在 VFS 中找到这个文件 (需要刷新以确保同步)
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path) ?: return

        WriteCommandAction.runWriteCommandAction(project) {
            try {
                // 删除文件
                virtualFile.delete(this)
            } catch (e: IOException) {
                println(e.message)
                e.printStackTrace()
            }
        }
    }

    /**
     * 3. 根据绝对路径修改文件内容
     *
     * @param project 当前项目
     * @param absolutePath 文件的绝对路径
     * @param newContent 要写入的新文本内容
     */
    fun updateFileContent(project: Project, absolutePath: String, newContent: String) {
        val path = FileUtil.toSystemIndependentName(absolutePath)
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path)

        if (virtualFile == null || virtualFile.isDirectory) {
            println("File not found or is a directory: $absolutePath")
            return
        }

        WriteCommandAction.runWriteCommandAction(project) {
            try {
                // VfsUtil.saveText 处理了编码和换行符问题
                VfsUtil.saveText(virtualFile, newContent)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 4. 根据文件路径读取文件内容
     *
     * @param absolutePath 文件的绝对路径
     * @return 文件内容字符串，如果文件不存在则返回 null
     */
    fun readFileContent(absolutePath: String): String? {
        val path = FileUtil.toSystemIndependentName(absolutePath)
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path)

        if (virtualFile == null || virtualFile.isDirectory) {
            return null
        }

        // 读取操作建议在 ReadAction 中进行，或者是 UI 线程
        return runReadAction {
            try {
                VfsUtil.loadText(virtualFile)
            } catch (e: IOException) {
                e.printStackTrace()
                null
            }
        }
    }

}