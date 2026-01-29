package com.github.bboygf.over_code.utils

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.PsiTreeUtil
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
        val ignoredExtensions = setOf("class", "jar", "exe", "dll", "pyc", "png", "jpg", "jpeg", "gif", "bmp")
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

                                // 将方法内容按行拆分，并添加行号前缀（类似 readFileContent）
                                val methodLines = method.text.lines()
                                val methodWithLineNumbers = methodLines.mapIndexed { index, lineText ->
                                    "${startLine + index} | $lineText"
                                }.joinToString("\n")
                                stringBuilder.append(methodWithLineNumbers).append("\n")
                            } else {
                                stringBuilder.append("${method.text}\n")
                            }
                            stringBuilder.append("\n")
                        }
                    }
                }
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
     *  @param expectedOldContent 可选：AI 预期该行号区间内的旧代码。用于校验行号是否过期
     */
    fun replaceCodeByLine(
        project: Project,
        fileName: String,
        startLine: Int,
        endLine: Int,
        newCodeString: String,
        expectedOldContent: String? = null
    ): String {
        val targetFiles = mutableListOf<VirtualFile>()
        val fileByPath = LocalFileSystem.getInstance().findFileByPath(fileName)

        if (fileByPath != null) {
            targetFiles.add(fileByPath)
        } else {
            val shortName = if (fileName.contains("/") || fileName.contains("\\")) File(fileName).name else fileName
            val foundFiles = runReadAction {
                FilenameIndex.getVirtualFilesByName(shortName, GlobalSearchScope.projectScope(project))
            }
            targetFiles.addAll(foundFiles)
        }

        if (targetFiles.isEmpty()) return "失败：未找到文件 $fileName"

        val resultSummary = StringBuilder()
        var successCount = 0

        WriteCommandAction.runWriteCommandAction(project) {
            targetFiles.forEach { virtualFile ->
                try {
                    val document = FileDocumentManager.getInstance().getDocument(virtualFile)
                    if (document == null) {
                        resultSummary.append("跳过：无法加载 [${virtualFile.name}]; ")
                        return@forEach
                    }

                    val totalLines = document.lineCount
                    val textLength = document.textLength

                    // --- 修复：处理空文件 (0字节) 的情况 ---
                    // 如果文件完全为空，逻辑上我们允许对第 1 行进行“替换”（即插入）
                    if (textLength == 0) {
                        if (startLine == 1) {
                            document.setText(newCodeString) // 直接设置内容
                            commitAndFormat(project, document, 0, newCodeString.length)
                            successCount++
                            resultSummary.append("成功(初始化)：[${virtualFile.name}] (当前总行数: ${document.lineCount}); ")
                        } else {
                            resultSummary.append("失败 [${virtualFile.name}]: 空文件只能从第1行开始写入; ")
                        }
                        return@forEach
                    }

                    // --- 严格边界检查 (针对非空文件) ---
                    if (startLine < 1 || endLine > totalLines || startLine > endLine) {
                        resultSummary.append("失败 [${virtualFile.name}]: 行号范围越界 (请求: $startLine-$endLine, 文件总行数: $totalLines); ")
                        return@forEach
                    }

                    // 计算 Offset
                    val sLine = startLine - 1
                    val eLine = endLine - 1
                    val startOffset = document.getLineStartOffset(sLine)
                    // 注意：getLineEndOffset 包含行尾换行符的逻辑处理
                    val endOffset = document.getLineEndOffset(eLine)

                    // --- 内容校验 (乐观锁) ---
                    if (expectedOldContent != null) {
                        val actualContent = document.getText(TextRange(startOffset, endOffset))
                        // 使用 trim() 增加对换行符不一致的容错性
                        if (actualContent.trim() != expectedOldContent.trim()) {
                            resultSummary.append("失败 [${virtualFile.name}]: 内容不匹配。预期: [${expectedOldContent.trim()}], 实际: [${actualContent.trim()}]; ")
                            return@forEach
                        }
                    }

                    // 执行替换
                    document.replaceString(startOffset, endOffset, newCodeString)

                    // 提交并格式化
                    commitAndFormat(project, document, startOffset, startOffset + newCodeString.length)

                    successCount++
                    resultSummary.append("成功：[${virtualFile.name}] (当前总行数: ${document.lineCount}); ")

                } catch (e: Exception) {
                    resultSummary.append("异常 [${virtualFile.name}]: ${e.message}; ")
                }
            }
        }

        val prefix = if (successCount > 0) "操作完成 ($successCount/${targetFiles.size}): " else "全部失败: "
        val warning =
            if (successCount > 0) "\n\n⚠️ 注意：由于文件内容已更改，建议调用 read_file_content 获取最新行号后再进行下一次修改。" else ""

        return "$prefix$resultSummary$warning"
    }

    /**
     * 提交文档修改并执行局部代码格式化
     */
    private fun commitAndFormat(project: Project, document: Document, startOffset: Int, endOffset: Int) {
        val psiDocumentManager = PsiDocumentManager.getInstance(project)
        psiDocumentManager.commitDocument(document)
        val psiFile = psiDocumentManager.getPsiFile(document)
        if (psiFile != null) {
            CodeStyleManager.getInstance(project).reformatRange(psiFile, startOffset, endOffset)
        }
        // 强制保存到磁盘，确保后续 read_file_content 能读到最新内容
        FileDocumentManager.getInstance().saveDocument(document)
    }

    /**
     * 根据方法名在项目中查找其所属的类、文件路径和行号。
     * @param project 项目对象
     * @param methodName 方法名
     * @return Markdown 格式的查找结果
     */
    fun findMethodsByName(project: Project, methodName: String): String {
        return runReadAction {
            val scope = GlobalSearchScope.projectScope(project)
            val methods = PsiShortNamesCache.getInstance(project).getMethodsByName(methodName, scope)

            if (methods.isEmpty()) {
                return@runReadAction "未在项目中找到名为 `$methodName` 的方法。"
            }

            val sb = StringBuilder()
            sb.append("### 查找结果: `$methodName` \n\n")
            sb.append("| 类名 | 文件名 | 绝对路径 | 行号 |\n")
            sb.append("| :--- | :--- | :--- | :--- |\n")

            methods.forEach { method ->
                val psiClass = method.containingClass
                val psiFile = method.containingFile
                val virtualFile = psiFile.virtualFile
                val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                val lineNumber = document?.getLineNumber(method.textOffset)?.plus(1) ?: -1

                val className = psiClass?.qualifiedName ?: "顶层函数"
                val fileName = virtualFile.name
                val filePath = virtualFile.path

                sb.append("| $className | $fileName | $filePath | $lineNumber |\n")
            }

            sb.toString()
        }
    }


    /**
     * 内部简单数据类，用于暂存错误信息以便排序
     */
    private data class TempErrorInfo(
        val line: Int,
        val type: String,   // "语法错误" 或 "语义错误"
        val message: String,
        val codeContent: String
    )

    /**
     * 检查文件是否有爆红，并直接返回 Markdown 格式的报告
     *
     * @param project 当前项目
     * @param filePath 文件绝对路径
     * @return Markdown 格式的字符串报告
     */
    fun reviewCodeByFile(project: Project, filePath: String): String {
        return runReadAction {
            val sb = StringBuilder()
            val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath)

            if (virtualFile == null || !virtualFile.exists()) {
                return@runReadAction "### ❌ 文件未找到\n路径: `$filePath`"
            }

            val fileTitle = virtualFile.name
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
            val document = psiFile?.let { PsiDocumentManager.getInstance(project).getDocument(it) }

            if (psiFile == null || document == null) {
                return@runReadAction "### ❌ 无法解析文件内容: $fileTitle"
            }

            val errorList = mutableListOf<TempErrorInfo>()

            // 1. 检查语法错误 (PsiErrorElement) - 最直接的红线
            PsiTreeUtil.collectElementsOfType(psiFile, PsiErrorElement::class.java).forEach { error ->
                val line = document.getLineNumber(error.textOffset) + 1
                errorList.add(
                    TempErrorInfo(
                        line = line,
                        type = "Syntax Error",
                        message = error.errorDescription,
                        codeContent = error.text.replace("\n", " ").replace("|", "\\|")
                    )
                )
            }

            // 2. 检查语义错误 (使用通用的 processHighlights 方法)
            // 该方法会遍历文档中已经生成的高亮信息
            DaemonCodeAnalyzerEx.processHighlights(
                document,
                project,
                HighlightSeverity.ERROR, // 只获取 ERROR 级别
                0,
                document.textLength
            ) { info ->
                val line = document.getLineNumber(info.startOffset) + 1
                val content = document.getText(TextRange(info.startOffset, info.endOffset))
                    .replace("\n", " ")
                    .replace("|", "\\|")

                val msg = (info.description ?: "Unknown Error").replace("|", "\\|")

                // 去重逻辑：如果同一个位置已经有了语法错误，就不再重复添加语义错误
                if (errorList.none { it.line == line && it.message == msg }) {
                    errorList.add(
                        TempErrorInfo(
                            line = line,
                            type = "Semantic Error",
                            message = msg,
                            codeContent = content
                        )
                    )
                    true // 继续处理下一个
                } else true
            }

            // 3. 构造 MD 字符串
            if (errorList.isEmpty()) {
                sb.append("### ✅ 代码检查通过: $fileTitle\n\n")
                sb.append("- 路径: `$filePath`\n")
                sb.append("- 结果: 未发现任何报错 (ERROR)。\n")
            } else {
                sb.append("### 🔴 代码发现爆红: $fileTitle\n\n")
                sb.append("- 路径: `$filePath`\n")
                sb.append("- 错误总数: **${errorList.size}**\n\n")
                sb.append("| 行号 | 类型 | 错误描述 | 问题代码 |\n")
                sb.append("| :--- | :--- | :--- | :--- |\n")

                errorList.sortedBy { it.line }.forEach { err ->
                    val cleanCode =
                        if (err.codeContent.length > 50) err.codeContent.take(50) + "..." else err.codeContent
                    sb.append("| ${err.line} | ${err.type} | ${err.message} | `${cleanCode.ifBlank { "N/A" }}` |\n")
                }
            }

            sb.toString()
        }
    }
}

