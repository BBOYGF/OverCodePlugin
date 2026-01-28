package com.github.bboygf.over_code.utils

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
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
        // 2. 使用读操作遍历文件系统
        // iterateContent 会遍历项目 Content Root 下的所有文件（包含子模块）
        runReadAction {
            fileIndex.iterateContent { virtualFile ->
                if (shouldInclude(virtualFile, project)) {
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

    private fun shouldInclude(file: VirtualFile, project: Project): Boolean {
        if (file.isDirectory) return false

        val fileIndex = ProjectFileIndex.getInstance(project)

        // 1. 检查文件是否在“排除列表”中（如 build, target 等目录下的文件）
        if (fileIndex.isExcluded(file)) return false

        // 2. 检查是否属于库文件或编译后的 class 文件（排除 jar 包和依赖库源码）
        if (fileIndex.isInLibraryClasses(file) || fileIndex.isInLibrarySource(file)) return false

        // 3. 基础过滤：排除特定后缀
        val ignoredExtensions = setOf("class", "jar", "exe", "dll", "pyc", "png", "jpg", "jpeg", "gif")
        if (ignoredExtensions.contains(file.extension?.lowercase())) return false

        return true
    }

    /**
     * 4. 根据文件路径读取文件内容（带行号）
     *
     * @param absolutePath 文件的绝对路径
     * @return 带行号的文件内容字符串，如果文件不存在则返回 null
     */
    fun readFileContent(absolutePath: String): String? {
        val path = FileUtil.toSystemIndependentName(absolutePath)
        // 建议先刷新，确保获取最新内容
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path)

        if (virtualFile == null || virtualFile.isDirectory) {
            return "读取失败文件不存在！"
        }

        return runReadAction {
            try {
                val rawContent = VfsUtil.loadText(virtualFile)

                // 将内容按行拆分，并添加行号前缀
                val lines = rawContent.lines() // 自动处理 \n, \r\n
                val contentWithLineNumbers = lines.mapIndexed { index, lineText ->
                    // index 从 0 开始，所以行号需要 + 1
                    "${index + 1} | $lineText"
                }.joinToString("\n")

                contentWithLineNumbers
            } catch (e: IOException) {
                e.printStackTrace()
                Log.error("读取文件失败", e)
                "读取文件失败：$absolutePath ${e.message}"
            }
        }
    }

    /**
     * 根据文件获取文件内所有方法详情（返回行号信息）
     * @param project 项目
     * @param fileName 文件名
     */
    fun getFileFunInfo(project: Project, fileName: String): String {
        var finalFileName = fileName
        val file = File(fileName)
        if (file.exists() && file.isFile) {
            finalFileName = file.name
        }

        return runReadAction {
            val virtualFiles = FilenameIndex.getVirtualFilesByName(
                finalFileName,
                GlobalSearchScope.projectScope(project)
            )

            val stringBuilder = StringBuilder()
            val psiManager = PsiManager.getInstance(project)
            val documentManager = PsiDocumentManager.getInstance(project) // 1. 获取文档管理器

            virtualFiles.forEach { virtualFile ->
                val psiFile = psiManager.findFile(virtualFile) ?: return@forEach
                val document = documentManager.getDocument(psiFile) // 2. 获取该文件的 Document 对象

                if (psiFile is PsiClassOwner) {
                    val classes = psiFile.classes
                    classes.forEach { psiClass ->
                        val methods = psiClass.methods
                        methods.forEach { method ->
                            if (method.isConstructor) return@forEach

                            val commentText = method.navigationElement.let { original ->
                                if (original is PsiDocCommentOwner) original.docComment?.text else null
                            }

                            // 3. 计算行号
                            val lineInfo = if (document != null) {
                                val startLine = document.getLineNumber(method.textRange.startOffset) + 1
                                val endLine = document.getLineNumber(method.textRange.endOffset) + 1
                                "第 $startLine 行 - 第 $endLine 行"
                            } else {
                                "无法获取行号"
                            }

                            stringBuilder.append("备注：${commentText?.trim() ?: "无"}\r\n")
                            stringBuilder.append("方法名：${method.name}\r\n")
                            stringBuilder.append("参数：${method.parameterList.text}\r\n")
                            stringBuilder.append("方法范围：$lineInfo\r\n")
                            stringBuilder.append("--------------------------\r\n")
                        }
                    }
                }
            }
            if (stringBuilder.isEmpty()) "未找到相关方法" else stringBuilder.toString()
        }
    }

    /**
     * 根据文件名和方法名获取特定方法的详情
     * @param project 项目对象
     * @param fileName 文件名 (如 "TestService.kt")
     * @param methodName 要查找的方法名
     */
    fun getMethodDetail(project: Project, fileName: String, methodName: String): String {
        var finalFileName = fileName
        val file = File(fileName)
        if (file.exists() && file.isFile) {
            finalFileName = file.name
        }

        return runReadAction {
            val virtualFiles = FilenameIndex.getVirtualFilesByName(
                finalFileName,
                GlobalSearchScope.projectScope(project)
            )

            val stringBuilder = StringBuilder()
            val psiManager = PsiManager.getInstance(project)
            val documentManager = PsiDocumentManager.getInstance(project) // 1. 获取 Document 管理器

            virtualFiles.forEach { virtualFile ->
                val psiFile = psiManager.findFile(virtualFile) ?: return@forEach
                val document = documentManager.getDocument(psiFile) // 2. 获取文件的 Document 对象

                // 处理包含类定义的文件 (Java 或 Kotlin 类)
                if (psiFile is PsiClassOwner) {
                    psiFile.classes.forEach { psiClass ->
                        val methods = psiClass.findMethodsByName(methodName, false)
                        methods.forEach { method ->
                            if (method.isConstructor) return@forEach

                            stringBuilder.append("--- 方法详情 ---\n")
                            stringBuilder.append("所属类：${psiClass.qualifiedName}\n")

                            // 3. 计算并添加行数信息
                            if (document != null) {
                                val startLine = document.getLineNumber(method.textRange.startOffset) + 1
                                val endLine = document.getLineNumber(method.textRange.endOffset) + 1
                                stringBuilder.append("行数范围（包括备注）：第 $startLine 行 到 第 $endLine 行\n")
                            }

                            stringBuilder.append("${method.text}\n")
                            stringBuilder.append("字符下标：${method.textRange.startOffset} - ${method.textRange.endOffset}\n")
                            stringBuilder.append("\n")
                        }
                    }
                }

                // 特殊处理：Kotlin 顶层函数
//                if (psiFile is org.jetbrains.kotlin.psi.KtFile) {
//                    psiFile.declarations.filterIsInstance<org.jetbrains.kotlin.psi.KtNamedFunction>().forEach { ktFunction ->
//                        if (ktFunction.name == methodName) {
//                            stringBuilder.append("--- 顶层方法详情 ---\n")
//
//                            // 3. 计算并添加行数信息
//                            if (document != null) {
//                                val startLine = document.getLineNumber(ktFunction.textRange.startOffset) + 1
//                                val endLine = document.getLineNumber(ktFunction.textRange.endOffset) + 1
//                                stringBuilder.append("行数范围：第 $startLine 行 到 第 $endLine 行\n")
//                            }
//
//                            stringBuilder.append("${ktFunction.text}\n")
//                            stringBuilder.append("字符下标：${ktFunction.textRange.startOffset} - ${ktFunction.textRange.endOffset}\n")
//                            stringBuilder.append("\n")
//                        }
//                    }
//                }
            }

            if (stringBuilder.isEmpty()) "未找到方法: $methodName" else stringBuilder.toString()
        }
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
    fun deleteFile(project: Project, absolutePath: String): String {
        val path = FileUtil.toSystemIndependentName(absolutePath)
        // 先尝试在 VFS 中找到这个文件 (需要刷新以确保同步)
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
        if (virtualFile == null || !virtualFile.isValid) {
            return "没有找到这个文件：$absolutePath"
        }

        return WriteCommandAction.runWriteCommandAction<String>(project) {
            try {
                // 删除文件
                virtualFile.delete(this)
                "删除文件：$absolutePath 成功！"
            } catch (e: IOException) {
                Log.error("删除文件异常！", e)
                "删除文件：$absolutePath 失败！原因是：${e.message}"
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
    fun updateFileContent(project: Project, absolutePath: String, newContent: String): String {
        val path = FileUtil.toSystemIndependentName(absolutePath)
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path)

        if (virtualFile == null || virtualFile.isDirectory) {
            println("File not found or is a directory: $absolutePath")
            return "文件不存在，或者是目录"
        }

        return WriteCommandAction.runWriteCommandAction<String>(project) {
            try {
                // VfsUtil.saveText 处理了编码和换行符问题
                VfsUtil.saveText(virtualFile, newContent)
                "修改成功！"
            } catch (e: IOException) {
                Log.error("根据绝对路径修改文件内容产生异常", e)
                "修改失败：${e.message}"
            }
        }
    }

    /**
     * 根据行号替换文件内容
     * @param project 项目
     * @param fileName 文件名
     * @param startLine 起始行号 (从 1 开始)
     * @param endLine 结束行号 (从 1 开始)
     * @param newCodeString 新的代码
     */
    fun replaceCodeByLine(
        project: Project,
        fileName: String,
        startLine: Int,
        endLine: Int,
        newCodeString: String
    ): String {
        var finalFileName = fileName
        val file = File(fileName)
        if (file.exists()) {
            finalFileName = file.name
        }

        val virtualFiles = runReadAction {
            FilenameIndex.getVirtualFilesByName(
                finalFileName,
                GlobalSearchScope.projectScope(project)
            )
        }

        if (virtualFiles.isEmpty()) {
            return "失败：未找到文件 $finalFileName"
        }

        val resultSummary = StringBuilder()
        var successCount = 0

        virtualFiles.forEach { virtualFile ->
            val fileResult = WriteCommandAction.runWriteCommandAction<String>(project, Computable {
                try {
                    val document = FileDocumentManager.getInstance().getDocument(virtualFile)
                        ?: return@Computable "跳过：无法加载文件 [${virtualFile.name}] 的内容"

                    // 1. 行号安全检查及转换 (将 1-based 转换为 0-based)
                    val totalLines = document.lineCount
                    if (totalLines == 0) return@Computable "失败：文件为空"

                    // 限制范围在合法行数内 (0 到 lineCount-1)
                    val sLine = (startLine - 1).coerceIn(0, totalLines - 1)
                    val eLine = (endLine - 1).coerceIn(0, totalLines - 1)

                    if (sLine > eLine) {
                        return@Computable "失败：起始行 $startLine 大于结束行 $endLine"
                    }

                    // 2. 将行号转换为偏移量
                    // getLineStartOffset: 获取该行第一个字符的下标
                    // getLineEndOffset: 获取该行最后一个字符（含换行符）的下标
                    val startOffset = document.getLineStartOffset(sLine)
                    val endOffset = document.getLineEndOffset(eLine)

                    // 3. 执行替换
                    document.replaceString(startOffset, endOffset, newCodeString)

                    // 4. 同步到 PSI 树
                    val psiDocumentManager = PsiDocumentManager.getInstance(project)
                    psiDocumentManager.commitDocument(document)

                    // 5. 格式化
                    val psiFile = psiDocumentManager.getPsiFile(document)
                    if (psiFile != null) {
                        CodeStyleManager.getInstance(project).reformatRange(
                            psiFile,
                            startOffset,
                            startOffset + newCodeString.length
                        )
                    }

                    successCount++
                    "成功：已替换 [${virtualFile.name}] 第 $startLine-$endLine 行"
                } catch (e: Exception) {
                    "异常：[${virtualFile.name}] 处理时错误: ${e.message}"
                }
            })
            resultSummary.append(fileResult).append("; ")
        }

        return if (successCount > 0) {
            "修改文件 $finalFileName 成功: $resultSummary"
        } else {
            "全部操作失败: $resultSummary"
        }
    }
}