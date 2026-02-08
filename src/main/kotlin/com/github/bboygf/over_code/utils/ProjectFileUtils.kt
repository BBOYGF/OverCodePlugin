package com.github.bboygf.over_code.utils

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
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
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.problems.WolfTheProblemSolver
import kotlinx.io.IOException
import java.io.File

object ProjectFileUtils {

    /**
     * 清理路径
     */
    private fun sanitizePath(path: String): String {
        val clean = path.replace("`", "")// 移除反引号
            .replace("\"", "")  // 移除双引号
            .replace(Regex("<ctrl\\d+>"), "")  // 修正后的正则，移除控制序列
            .trim()
        return FileUtil.toSystemIndependentName(clean)
    }

    /**
     * 内部辅助方法：统一查找 VirtualFile 的逻辑，支持普通路径、URL 和测试环境路径
     */
    private fun findVirtualFile(pathOrUrl: String): VirtualFile? {
        val path = sanitizePath(pathOrUrl)
        if (path.isEmpty()) return null

        val file = when {
            path.contains("://") -> VirtualFileManager.getInstance().findFileByUrl(path)
            else -> {
                LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
                    ?: VirtualFileManager.getInstance().findFileByUrl("temp://$path")
                    ?: VirtualFileManager.getInstance().findFileByUrl(VfsUtilCore.pathToUrl(path))
            }
        }
        if (file == null) {
            Log.error("未找到文件，原始路径: $pathOrUrl, 清理后路径: $path")
        }
        return file
    }


    /**
     * 获取项目下所有文件的列表，并生成 Markdown 格式字符串
     * 格式：Markdown 表格
     */
    fun exportToMarkdown(project: Project): String {
        Log.info("调用工具，获取项目下的所有文件列表")
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

        // 2. 检查是否属于库文件或编译后的 class 文件（排除 jar 包 and 依赖库源码）
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
    fun readFileContent(absolutePath: String): String {
        return try {
            Log.info("调用工具，根据文件路径读取文件内容: $absolutePath")
            val virtualFile = findVirtualFile(absolutePath)

            if (virtualFile == null || virtualFile.isDirectory) {
                return "读取失败：文件不存在或路径是目录！"
            }

            // 1. 安全检查：限制读取大小（例如超过 1MB 就不读了，防止 OOM 和 Token 溢出）
            val maxSizeBytes = 1024 * 1024 // 1MB
            if (virtualFile.length > maxSizeBytes) {
                return "读取失败：文件过大 (${virtualFile.length / 1024} KB)，为了安全起见已跳过。请尝试缩小范围。"
            }

            runReadAction {
                val document = FileDocumentManager.getInstance().getDocument(virtualFile)
                val rawContent = document?.text ?: VfsUtil.loadText(virtualFile)
                val lines = rawContent.lines()

                // 2. 使用 StringBuilder 减少内存碎片的产生
                val result = StringBuilder()
                lines.forEachIndexed { index, lineText ->
                    result.append(index + 1).append(" | ").append(lineText).append("\n")
                }
                result.toString()
            }

        } catch (e: Throwable) {
            // 3. 捕获所有异常，确保工具调用流程不会中断
            val errorMsg = "读取文件时发生意外错误: ${e.message}"
            Log.error(errorMsg, e)
            errorMsg
        }
    }

    /**
     * 根据文件路径、起始行号、终止行号读取文件内容（带行号）
     *
     * @param absolutePath 文件的绝对路径
     * @param startLine 起始行号 (从 1 开始)
     * @param endLine 终止行号 (从 1 开始)
     * @return 带行号的文件内容字符串
     */
    fun readFileRange(absolutePath: String, startLine: Int, endLine: Int): String {
        return try {
            Log.info("调用工具，根据范围读取文件内容: $absolutePath ($startLine - $endLine)")
            val virtualFile = findVirtualFile(absolutePath)

            if (virtualFile == null || virtualFile.isDirectory) {
                return "读取失败：文件不存在或路径是目录！"
            }

            runReadAction {
                val document = FileDocumentManager.getInstance().getDocument(virtualFile)
                val rawContent = document?.text ?: VfsUtil.loadText(virtualFile)
                val lines = rawContent.lines()

                val result = StringBuilder()
                val actualStart = (startLine - 1).coerceAtLeast(0)
                val actualEnd = (endLine - 1).coerceAtMost(lines.size - 1)

                if (actualStart > actualEnd) {
                    return@runReadAction "读取失败：起始行号 $startLine 大于终止行号 $endLine 或超出范围 (当前文件共 ${lines.size} 行)"
                }

                for (i in actualStart..actualEnd) {
                    result.append(i + 1).append(" | ").append(lines[i]).append("\n")
                }
                result.toString()
            }

        } catch (e: Throwable) {
            val errorMsg = "范围读取文件时发生意外错误: ${e.message}"
            Log.error(errorMsg, e)
            errorMsg
        }
    }


    /**
     * 根据文件获取文件内所有方法详情（返回行号信息）
     * @param project 项目
     * @param absolutePath 文件名
     */
    fun getFileFunInfo(project: Project, absolutePath: String): String {
        Log.info("调用工具，根据文件获取文件内所有方法详情（返回行号信息）")
        val virtualFile = findVirtualFile(absolutePath)
            ?: return "### ❌ 失败：未找到文件\n路径: `$absolutePath`"

        return runReadAction {
            val stringBuilder = StringBuilder()
            val psiManager = PsiManager.getInstance(project)
            val documentManager = PsiDocumentManager.getInstance(project) // 1. 获取文档管理器

            val psiFile = psiManager.findFile(virtualFile) ?: return@runReadAction "文件路径不存在$absolutePath 请重试！"
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
            if (stringBuilder.isEmpty()) "未找到相关方法" else stringBuilder.toString()
        }
    }

    /**
     * 根据文件名和方法名获取特定方法的详情
     * @param project 项目对象
     * @param absolutePath 文件的绝对路径
     * @param methodName 要查找的方法名
     */
    fun getMethodDetail(project: Project, absolutePath: String, methodName: String): String {
        Log.info("调用工具，根据文件名和方法名获取特定方法的详情")
        val virtualFile = findVirtualFile(absolutePath)
            ?: return "### ❌ 失败：未找到文件\n路径: `$absolutePath`"
        return runReadAction {

            val stringBuilder = StringBuilder()
            val psiManager = PsiManager.getInstance(project)
            val documentManager = PsiDocumentManager.getInstance(project) // 1. 获取 Document 管理器
            val psiFile =
                psiManager.findFile(virtualFile) ?: return@runReadAction "文件路径不存在：$absolutePath 请重试！"
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
    fun createFileOrDir(project: Project, absolutePath: String, isDirectory: Boolean): String {
        Log.info("调用工具，创建文件或目录")
        val cleanPath = sanitizePath(absolutePath)
        // 将路径转换为系统无关路径 (处理 Windows 反斜杠问题)
        val systemIndependentPath = FileUtil.toSystemIndependentName(cleanPath)

        var result: VirtualFile? = null

        // 所有写操作必须在 WriteCommandAction 中执行，以支持 Undo 并确保线程安全
        return WriteCommandAction.runWriteCommandAction<String>(project) {
            try {
                if (isDirectory) {
                    // 创建目录（如果父目录不存在会自动创建）
                    result = VfsUtil.createDirectoryIfMissing(systemIndependentPath)
                    return@runWriteCommandAction "创建文件成功！"
                } else {
                    // 创建文件
                    val file = File(systemIndependentPath)
                    // 确保父目录存在
                    val parentDir = VfsUtil.createDirectoryIfMissing(file.parent)
                    if (parentDir != null) {
                        // 在父目录下查找或创建子文件
                        val existingFile = parentDir.findChild(file.name)
                        existingFile ?: parentDir.createChildData(this, file.name)
                        return@runWriteCommandAction "创建文件成功！"
                    } else {
                        return@runWriteCommandAction "创建文件成功！"
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                return@runWriteCommandAction "创建目录异常：: $absolutePath"
            }
        }
    }

    /**
     * 2. 根据绝对路径删除文件或目录
     *
     * @param project 当前项目
     * @param absolutePath 要删除的绝对路径
     */
    fun deleteFile(project: Project, absolutePath: String): String {
        Log.info("调用工具，根据绝对路径删除文件或目录")
        val path = sanitizePath(absolutePath)
        // 先尝试在 VFS 中找到这个文件 (需要刷新以确保同步)
        val virtualFile = findVirtualFile(path)
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
     * 根据行号替换文件内容
     * @param project 项目
     * @param absolutePath 文件绝对路径
     * @param startLine 起始行号 (从 1 开始)
     * @param endLine 结束行号 (从 1 开始)
     * @param newCodeString 新的代码
     */
    fun replaceCodeByLine(
        project: Project,
        absolutePath: String,
        startLine: Int,
        endLine: Int,
        newCodeString: String
    ): String {
        Log.info("调用工具，根据行号替换文件内容")
        // 1. 通过绝对路径加载 VirtualFile
        val virtualFile = findVirtualFile(absolutePath)
            ?: return "### ❌ 失败：未找到文件\n路径: `$absolutePath`"

        // 2. 自动定位该文件所属的项目
        var resultMessage = ""

        // 3. 在写入操作中执行修改
        WriteCommandAction.runWriteCommandAction(project) {
            try {
                val document = FileDocumentManager.getInstance().getDocument(virtualFile)
                if (document == null) {
                    resultMessage = "### ❌ 失败：无法加载文件文档内容 [${virtualFile.name}]"
                    return@runWriteCommandAction
                }

                val totalLines = document.lineCount
                val textLength = document.textLength

                // --- 情况 A: 处理空文件 ---
                if (textLength == 0) {
                    if (startLine == 1) {
                        document.setText(newCodeString)
                        commitAndFormat(project, document, 0, newCodeString.length)
                        resultMessage = "### ✅ 成功：文件初始化成功 [${virtualFile.name}]"
                    } else {
                        resultMessage = "### ❌ 失败：空文件必须从第 1 行开始写入"
                    }
                    return@runWriteCommandAction
                }

                // --- 情况 B: 严格边界检查 ---
                if (startLine < 1 || endLine > totalLines || startLine > endLine) {
                    resultMessage = "### ❌ 失败：行号越界\n请求范围: $startLine-$endLine, 当前总行数: $totalLines"
                    return@runWriteCommandAction
                }
                // 4. 计算 Offset (IDE 内部 offset 从 0 开始)
                val sLine = startLine - 1
                val eLine = endLine - 1
                val startOffset = document.getLineStartOffset(sLine)
                val endOffset = document.getLineEndOffset(eLine)

                // 6. 执行替换
                document.replaceString(startOffset, endOffset, newCodeString)

                // 7. 提交更改并触发格式化（假设已有 commitAndFormat 工具方法）
                commitAndFormat(project, document, startOffset, startOffset + newCodeString.length)

                resultMessage = "### ✅ 成功：已更新文件 [${virtualFile.name}]\n" +
                        "- 修改范围: 行 $startLine 到 $endLine\n" +
                        "- 当前总行数: ${document.lineCount}\n" +
                        "\n⚠️ 注意：修改后请务必使用相关工具检查代码是否报错。"

            } catch (e: Exception) {
                resultMessage = "### 💥 异常：修改过程中发生错误\n内容: ${e.message}"
            }
        }

        return resultMessage
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
     * 根据方法名在项目中查找其所属的类、文件路径和行号范围。
     * @param project 项目对象
     * @param methodName 方法名
     * @return Markdown 格式的查找结果
     */
    fun findMethodsByName(project: Project, methodName: String): String {
        Log.info("调用工具，根据方法名在项目中查找其所属的类、文件路径和行号范围。")
        return runReadAction {
            val scope = GlobalSearchScope.projectScope(project)
            val methods = PsiShortNamesCache.getInstance(project).getMethodsByName(methodName, scope)

            if (methods.isEmpty()) {
                return@runReadAction "未在项目中找到名为 `$methodName` 的方法。"
            }

            val sb = StringBuilder()
            sb.append("### 查找结果: `$methodName` \n\n")
            sb.append("| 类名 | 文件名 | 绝对路径 | 行号范围 |\n")
            sb.append("| :--- | :--- | :--- | :--- |\n")

            methods.forEach { method ->
                val psiClass = method.containingClass
                val psiFile = method.containingFile
                val virtualFile = psiFile.virtualFile
                val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)

                val lineRange = if (document != null) {
                    val start = document.getLineNumber(method.textRange.startOffset) + 1
                    val end = document.getLineNumber(method.textRange.endOffset) + 1
                    "$start - $end"
                } else {
                    "未知"
                }

                val className = psiClass?.qualifiedName ?: "顶层函数"
                val fileName = virtualFile.name
                val filePath = virtualFile.path

                sb.append("| $className | $fileName | $filePath | $lineRange |\n")
            }

            sb.toString()
        }
    }

    /**
     * 根据类名在项目中查找其所属的文件、文件路径和行号范围。
     * @param project 项目对象
     * @param className 类名
     * @return Markdown 格式的查找结果
     */
    fun findClassesByName(project: Project, className: String): String {
        Log.info("调用工具，根据类名在项目中查找其所属的文件、文件路径和行号范围。")
        return runReadAction {
            val scope = GlobalSearchScope.projectScope(project)
            val classes = PsiShortNamesCache.getInstance(project).getClassesByName(className, scope)

            if (classes.isEmpty()) {
                return@runReadAction "未在项目中找到名为 `$className` 的类。"
            }

            val sb = StringBuilder()
            sb.append("### 查找结果: `$className` \n\n")
            sb.append("| 全类名 | 文件名 | 绝对路径 | 行号范围 |\n")
            sb.append("| :--- | :--- | :--- | :--- |\n")

            classes.forEach { psiClass ->
                val psiFile = psiClass.containingFile
                val virtualFile = psiFile.virtualFile
                val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)

                val lineRange = if (document != null) {
                    val start = document.getLineNumber(psiClass.textRange.startOffset) + 1
                    val end = document.getLineNumber(psiClass.textRange.endOffset) + 1
                    "$start - $end"
                } else {
                    "未知"
                }

                val fullClassName = psiClass.qualifiedName ?: psiClass.name ?: "未知"
                val fileName = virtualFile.name
                val filePath = virtualFile.path

                sb.append("| $fullClassName | $fileName | $filePath | $lineRange |\n")
            }

            sb.toString()
        }
    }


    /**
     * 根据目录的绝对路径获取当前目录下的所有目录 and 文件，使用md格式输出字符串。
     * @param absolutePath 目录的绝对路径
     */
    fun listDirectoryContents(absolutePath: String): String {
        Log.info("调用工具，根据目录的绝对路径获取当前目录下的所有目录 and 文件，使用md格式输出字符串。")
        return runReadAction {
            val virtualFile = findVirtualFile(absolutePath)

            if (virtualFile == null || !virtualFile.exists()) {
                return@runReadAction "### ❌ 失败：路径不存在\n路径: `$absolutePath`"
            }

            if (!virtualFile.isDirectory) {
                return@runReadAction "### ❌ 失败：该路径不是一个目录\n路径: `$absolutePath`"
            }

            val sb = StringBuilder()
            sb.append("### 目录内容: `${virtualFile.name}`\n\n")
            sb.append("- 路径: `$absolutePath`\n\n")
            sb.append("| 名称 | 类型 | 绝对路径 |\n")
            sb.append("| :--- | :--- | :--- |\n")

            val children = virtualFile.children ?: emptyArray()

            if (children.isEmpty()) {
                return@runReadAction "### 目录内容: `${virtualFile.name}`\n\n该目录为空。"
            }

            // 排序：目录在前，文件在后，按名称排序
            val sortedChildren = children.sortedWith(compareBy({ !it.isDirectory }, { it.name }))

            for (child in sortedChildren) {
                val type = if (child.isDirectory) "📁 目录" else "📄 文件"
                sb.append("| ${child.name} | $type | ${child.path} |\n")
            }

            sb.toString()
        }
    }

    /**
     * 检查整个项目是否有爆红，并返回 Markdown 格式的报告
     */
    fun inspectProjectErrors(project: Project): String {
        val sb = StringBuilder()
        sb.append("# 🚀 项目代码质量扫描报告\n\n")

        // 确保在 Read Action 中执行，防止 AccessDeniedException
        return ApplicationManager.getApplication().runReadAction<String> {
            val wolf = WolfTheProblemSolver.getInstance(project)
            val errorFiles = mutableListOf<VirtualFile>()

            ProjectFileIndex.getInstance(project).iterateContent { virtualFile ->
                // 1. 过滤逻辑：只检查源码，排除 library 和忽略的文件
                if (!virtualFile.isDirectory && shouldInclude(virtualFile, project)) {

                    // 2. 综合判断：Wolf 标记或 PSI 语法错误
                    val hasError = wolf.isProblemFile(virtualFile) || run {
                        val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
                        psiFile != null && com.intellij.psi.util.PsiTreeUtil.hasErrorElements(psiFile)
                    }

                    if (hasError) errorFiles.add(virtualFile)
                }
                true
            }

            if (errorFiles.isEmpty()) {
                sb.append("### ✅ 完美！\n项目内未发现任何爆红文件 (ERROR 级别)。\n")
            } else {
                sb.append("### 📊 概览\n")
                sb.append("- 异常文件总数: **${errorFiles.size}**\n\n---\n\n")

                errorFiles.forEach { file ->
                    // 注意：reviewSingleFileInternal 内部也必须处理好读锁
                    val fileReport = reviewSingleFileInternal(project, file)
                    sb.append(fileReport).append("\n\n---\n\n")
                }
            }
            sb.toString()
        }
    }

    /**
     * 内部方法：解析单个文件的错误详情
     */
    private fun reviewSingleFileInternal(project: Project, virtualFile: VirtualFile): String {
        return runReadAction {
            val sb = StringBuilder()
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
            val document = psiFile?.let { PsiDocumentManager.getInstance(project).getDocument(it) }

            if (psiFile == null || document == null) {
                return@runReadAction "#### ❌ 无法解析文件: `${virtualFile.name}`"
            }

            val errorList = mutableListOf<TempErrorInfo>()

            // A. 检查语法错误 (PsiErrorElement)
            PsiTreeUtil.collectElementsOfType(psiFile, PsiErrorElement::class.java).forEach { error ->
                val line = try {
                    document.getLineNumber(error.textOffset) + 1
                } catch (e: Exception) {
                    0
                }
                errorList.add(
                    TempErrorInfo(
                        line = line,
                        type = "语法错误",
                        message = error.errorDescription,
                        codeContent = error.text.replace("\n", " ").replace("|", "\\|")
                    )
                )
            }

            // B. 检查语义错误 (已生成的高亮)
            DaemonCodeAnalyzerEx.processHighlights(
                document, project, HighlightSeverity.ERROR, 0, document.textLength
            ) { info ->
                val line = document.getLineNumber(info.startOffset) + 1
                val content = document.getText(TextRange(info.startOffset, info.endOffset))
                    .replace("\n", " ")
                    .replace("|", "\\|")
                val msg = (info.description ?: "未知错误").replace("|", "\\|")

                if (errorList.none { it.line == line && it.message == msg }) {
                    errorList.add(TempErrorInfo(line, "语义错误", msg, content))
                }
                true
            }

            // 构造该文件的表格
            val relativePath = virtualFile.path.removePrefix(project.basePath ?: "")
            sb.append("#### 📄 文件: `$relativePath`\n")
            sb.append("| 行号 | 类型 | 错误描述 | 问题代码 |\n")
            sb.append("| :--- | :--- | :--- | :--- |\n")

            errorList.sortedBy { it.line }.forEach { err ->
                val cleanCode = if (err.codeContent.length > 40) err.codeContent.take(40) + "..." else err.codeContent
                sb.append("| ${err.line} | ${err.type} | ${err.message} | `${cleanCode.ifBlank { "N/A" }}` |\n")
            }

            sb.toString()
        }
    }

    private data class TempErrorInfo(
        val line: Int,
        val type: String,
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
        val virtualFile = findVirtualFile(filePath)
            ?: return "### ❌ 文件未找到\n路径: `$filePath`"
        return reviewSingleFileInternal(project, virtualFile)
    }
}
