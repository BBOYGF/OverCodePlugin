package com.github.bboygf.over_code.utils

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
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
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.problems.WolfTheProblemSolver
import kotlinx.io.IOException
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

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

    /**
     * 不包含的目录或文件
     */
    private fun shouldInclude(file: VirtualFile, project: Project): Boolean {
        if (file.isDirectory) return false

        val fileIndex = ProjectFileIndex.getInstance(project)

        // 1. 检查文件是否在"排除列表"中（如 build, target 等目录下的文件）
        if (fileIndex.isExcluded(file)) return false

        // 2. 检查是否属于库文件或编译后的 class 文件（排除 jar 包 and 依赖库源码）
        if (fileIndex.isInLibraryClasses(file) || fileIndex.isInLibrarySource(file)) return false

        // 3. 基础过滤：排除特定后缀
        val ignoredExtensions =
            setOf("class", "jar", "exe", "dll", "pyc", "png", "jpg", "jpeg", "gif", "bmp", "sql", "log")
        if (ignoredExtensions.contains(file.extension?.lowercase())) return false

        return true
    }

    /**
     * 判断目录是否应该包含在目录树中
     */
    private fun shouldIncludeDirectory(dir: VirtualFile, project: Project): Boolean {
        val fileIndex = ProjectFileIndex.getInstance(project)

        // 1. 检查目录是否在"排除列表"中（如 build, target, .gradle 等目录）
        if (fileIndex.isExcluded(dir)) return false

        // 2. 检查是否属于库目录
        if (fileIndex.isInLibraryClasses(dir) || fileIndex.isInLibrarySource(dir)) return false

        // 3. 排除隐藏目录（以 . 开头）
        if (dir.name.startsWith(".")) return false

        // 4. 排除常见的构建输出目录
        val excludedDirs = setOf(
            "build", "target", "out", "dist", "lib", "bin", "obj",
            ".gradle", ".idea", "node_modules", "__pycache__", "venv"
        )
        if (excludedDirs.contains(dir.name)) return false

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
        // 所有写操作必须在 WriteCommandAction 中执行，以支持 Undo 并确保线程安全
        return WriteCommandAction.runWriteCommandAction<String>(project) {
            try {
                if (isDirectory) {
                    // 创建目录（如果父目录不存在会自动创建）
                    VfsUtil.createDirectoryIfMissing(systemIndependentPath)
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
    internal fun commitAndFormat(project: Project, document: Document, startOffset: Int, endOffset: Int) {
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

        // 1. 先触发代码分析并等待完成，确保问题文件索引是最新的
        waitForCodeAnalysis(project)

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
                        psiFile != null && PsiTreeUtil.hasErrorElements(psiFile)
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
     * 触发项目代码分析并等待完成，确保问题文件索引是最新的
     */
    private fun waitForCodeAnalysis(project: Project, timeoutSeconds: Long = 30) {
        val daemon = DaemonCodeAnalyzerImpl.getInstance(project)

        // 触发全项目代码分析
        daemon.restart()

        // 等待分析完成（带超时保护）
        // 使用反射调用内部方法，避免版本兼容性问题
        try {
            val method = DaemonCodeAnalyzerImpl::class.java.getMethod(
                "waitForDAForProjectInTests",
                Project::class.java,
                Long::class.javaPrimitiveType,
                TimeUnit::class.java
            )
            method.invoke(daemon, project, timeoutSeconds, TimeUnit.SECONDS)
        } catch (e: NoSuchMethodException) {
            // 如果方法不存在，尝试使用替代方案
            waitForAnalysisAlternative(project, timeoutSeconds)
        } catch (e: Exception) {
            // 其他异常也忽略，继续执行
            println("代码分析等待异常: ${e.message}")
        }
    }

    /**
     * 等待代码分析的替代方案 - 使用简单延迟
     */
    private fun waitForAnalysisAlternative(project: Project, timeoutSeconds: Long) {
        try {
            // 简单等待一段时间，让代码分析有机会完成
            // 注意：这是一种简化的方案，不检查实际分析状态
            Thread.sleep(minOf(timeoutSeconds * 1000, 5000)) // 最多等待 5 秒
        } catch (e: Exception) {
            println("等待代码分析替代方案异常: ${e.message}")
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
            val relativePath = virtualFile.path
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

    /**
     * 智能文本替换核心方法 - 委托给 SmartReplacer
     *
     * @param content 原始内容
     * @param oldString 要替换的文本
     * @param newString 新文本
     * @param replaceAll 是否替换所有匹配项
     * @return 替换后的内容
     * @throws IllegalArgumentException 如果 oldString 未找到或有多个匹配（非 replaceAll 模式）
     */
    fun smartReplace(content: String, oldString: String, newString: String, replaceAll: Boolean = false): String {
        return SmartReplacer.smartReplace(content, oldString, newString, replaceAll)
    }

    /**
     * 根据搜索文本替换文件内容 - 委托给 SmartReplacer
     *
     * @param project 项目对象
     * @param filePath 文件绝对路径
     * @param oldString 要替换的文本
     * @param newString 新文本
     * @param replaceAll 是否替换所有匹配项
     * @return 操作结果信息
     */
    fun editFileBySearch(
        project: Project,
        filePath: String,
        oldString: String,
        newString: String,
        replaceAll: Boolean = false
    ): String {
        return SmartReplacer.editFileBySearch(
            project = project,
            filePath = sanitizePath(filePath),
            oldString = oldString,
            newString = newString,
            replaceAll = replaceAll,
            virtualFileFinder = ::findVirtualFile,
            commitAndFormat = ::commitAndFormat
        )
    }

    /**
     * 获取指定目录的树形结构，只列出目录（不包含文件），使用 MD 树形图格式输出
     * @param project 项目实例
     * @param absolutePath 目录的绝对路径
     * @return MD 树形图格式的目录结构
     */
    fun getDirectoryTree(project: Project, absolutePath: String): String {
        val projectPath = project.basePath ?: ""
        Log.info("调用工具，获取目录树形结构，项目路径: $projectPath, 目标路径: $absolutePath")
        return runReadAction {
            val virtualFile = findVirtualFile(absolutePath)

            if (virtualFile == null || !virtualFile.exists()) {
                return@runReadAction "### ❌ 失败：路径不存在\n路径: `$absolutePath`"
            }

            if (!virtualFile.isDirectory) {
                return@runReadAction "### ❌ 失败：该路径不是一个目录\n路径: `$absolutePath`"
            }

            val sb = StringBuilder()
            sb.append("# 目录树形结构\n\n")
            sb.append("📁 项目根路径: $projectPath\n\n")

            // 递归构建树形结构
            fun buildTree(parentFile: VirtualFile, prefix: String, isLast: Boolean) {
                val children = parentFile.children
                    ?.filter { it.isDirectory && shouldIncludeDirectory(it, project) }
                    ?.sortedBy { it.name }
                    ?: return

                children.forEachIndexed { index, child ->
                    val isChildLast = index == children.size - 1
                    val connector = if (isChildLast) "└── " else "├── "
                    val nextPrefix = if (isChildLast) "$prefix    " else "$prefix│   "

                    sb.append("$prefix$connector📁 ${child.name}\n")
                    buildTree(child, nextPrefix, isChildLast)
                }
            }

            buildTree(virtualFile, "", true)
            sb.append("\n路径: `$absolutePath`")
            sb.toString()
        }
    }

    /**
     * 获取指定目录下所有文件的完整路径（递归遍历，包含所有子目录）
     * @param absolutePath 目录的绝对路径
     * @return 所有文件的绝对路径列表
     */
    fun getAllFilesInDirectory(absolutePath: String): String {
        Log.info("调用工具，获取目录下所有文件: $absolutePath")
        return runReadAction {
            val virtualFile = findVirtualFile(absolutePath)

            if (virtualFile == null || !virtualFile.exists()) {
                return@runReadAction "### ❌ 失败：路径不存在\n路径: `$absolutePath`"
            }

            if (!virtualFile.isDirectory) {
                return@runReadAction "### ❌ 失败：该路径不是一个目录\n路径: `$absolutePath`"
            }

            val sb = StringBuilder()
            sb.append("# 目录下的所有文件\n\n")
            sb.append("📁 目录: `${virtualFile.name}`\n\n")

            val allFiles = mutableListOf<String>()

            // 递归收集所有文件
            fun collectFiles(dir: VirtualFile) {
                val children = dir.children ?: return
                for (child in children) {
                    if (child.isDirectory) {
                        collectFiles(child)
                    } else {
                        allFiles.add(child.path)
                    }
                }
            }

            collectFiles(virtualFile)

            if (allFiles.isEmpty()) {
                return@runReadAction "# 目录下的所有文件\n\n📁 目录: `${virtualFile.name}`\n\n该目录下没有文件。"
            }

            // 按路径排序
            allFiles.sort()

            sb.append("| 序号 | 文件名 | 绝对路径 |\n")
            sb.append("| :--- | :--- | :--- |\n")

            allFiles.forEachIndexed { index, filePath ->
                val fileName = filePath.substringAfterLast("/").substringAfterLast("\\")
                sb.append("| ${index + 1} | $fileName | $filePath |\n")
            }

            sb.append("\n共 ${allFiles.size} 个文件")
            sb.toString()
        }
    }

    /**
     * 根据变量名在项目中查找其定义位置
     * @param project 项目对象
     * @param variableName 变量名
     * @return Markdown 格式的查找结果
     */
    fun findVariablesByName(project: Project, variableName: String): String {
        Log.info("调用工具，根据变量名在项目中查找其定义位置。")
        return runReadAction {
            val scope = GlobalSearchScope.projectScope(project)
            val results = mutableListOf<VariableSearchResult>()

            // 1. 搜索字段（Field）- 类的成员变量
            val fields = PsiShortNamesCache.getInstance(project).getFieldsByName(variableName, scope)
            fields.forEach { field ->
                val psiClass = field.containingClass
                val psiFile = field.containingFile
                val virtualFile = psiFile?.virtualFile

                if (virtualFile != null) {
                    val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                    val lineRange = if (document != null) {
                        val start = document.getLineNumber(field.textRange.startOffset) + 1
                        val end = document.getLineNumber(field.textRange.endOffset) + 1
                        "$start - $end"
                    } else {
                        "未知"
                    }

                    results.add(
                        VariableSearchResult(
                            variableName = field.name,
                            type = "字段 (Field)",
                            declaringClass = psiClass?.qualifiedName ?: "顶层",
                            fileName = virtualFile.name,
                            filePath = virtualFile.path,
                            lineRange = lineRange,
                            codeSnippet = field.text.replace("\n", " ").take(80)
                        )
                    )
                }
            }

            // 2. 搜索局部变量（LocalVariable）- 方法内的变量
            // 使用 PsiSearchHelper 搜索
            val fileIndex = ProjectRootManager.getInstance(project).fileIndex
            runReadAction {
                fileIndex.iterateContent { virtualFile ->
                    if (!shouldInclude(virtualFile, project)) return@iterateContent true

                    val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@iterateContent true

                    // 遍历 PSI 树查找局部变量
                    psiFile.accept(object : PsiRecursiveElementVisitor() {
                        override fun visitElement(element: PsiElement) {
                            super.visitElement(element)

                            // 检查是否是变量声明 (val 或 var)
                            if (element is PsiVariable) {
                                if (element.name == variableName) {
                                    val psiClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
                                    val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)

                                    val lineRange = if (document != null) {
                                        val start = document.getLineNumber(element.textRange.startOffset) + 1
                                        val end = document.getLineNumber(element.textRange.endOffset) + 1
                                        "$start - $end"
                                    } else {
                                        "未知"
                                    }

                                    // 检查是否是局部变量（不是字段）
                                    if (psiClass == null || !element.hasModifierProperty(PsiModifier.STATIC)) {
                                        results.add(
                                            VariableSearchResult(
                                                variableName = element.name ?: variableName,
                                                type = "局部变量 (Local Variable)",
                                                declaringClass = "方法内",
                                                fileName = virtualFile.name,
                                                filePath = virtualFile.path,
                                                lineRange = lineRange,
                                                codeSnippet = element.text.replace("\n", " ").take(80)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    })
                    true // 继续遍历
                }
            }

            if (results.isEmpty()) {
                return@runReadAction "未在项目中找到名为 `$variableName` 的变量。"
            }

            val sb = StringBuilder()
            sb.append("### 查找结果: `$variableName` \n\n")
            sb.append("| 类型 | 变量名 | 所属类/方法 | 文件名 | 绝对路径 | 行号范围 | 代码片段 |\n")
            sb.append("| :--- | :--- | :--- | :--- | :--- | :--- | :--- |\n")

            results.forEach { result ->
                sb.append("| ${result.type} | ${result.variableName} | ${result.declaringClass} | ${result.fileName} | ${result.filePath} | ${result.lineRange} | `${result.codeSnippet}` |\n")
            }

            sb.append("\n共找到 ${results.size} 处定义")
            sb.toString()
        }
    }

    private data class VariableSearchResult(
        val variableName: String,
        val type: String,
        val declaringClass: String,
        val fileName: String,
        val filePath: String,
        val lineRange: String,
        val codeSnippet: String
    )

    /**
     * 根据变量名或方法名查找其在项目中的引用位置
     * @param project 项目对象
     * @param name 变量名或方法名
     * @return Markdown 格式的查找结果
     */
    fun findReferencesByName(project: Project, name: String): String {
        Log.info("调用工具，根据变量名或方法名查找引用位置。")
        return runReadAction {
            val results = mutableListOf<ReferenceSearchResult>()
            val fileIndex = ProjectRootManager.getInstance(project).fileIndex
            val scope = GlobalSearchScope.projectScope(project)

            // 1. 收集方法定义文件
            val methodDefinitionFiles = mutableSetOf<VirtualFile>()
            val methods = PsiShortNamesCache.getInstance(project).getMethodsByName(name, scope)
            for (method in methods) {
                method.containingFile?.virtualFile?.let { methodDefinitionFiles.add(it) }
            }

            // 2. 收集字段定义文件
            val fieldDefinitionFiles = mutableSetOf<VirtualFile>()
            val fields = PsiShortNamesCache.getInstance(project).getFieldsByName(name, scope)
            for (field in fields) {
                field.containingFile?.virtualFile?.let { fieldDefinitionFiles.add(it) }
            }

            // 3. 遍历项目文件，查找引用（最多30个）
            val maxResults = 30
            fileIndex.iterateContent { virtualFile ->
                if (results.size >= maxResults) return@iterateContent false // 达到上限，终止遍历
                if (!shouldInclude(virtualFile, project)) return@iterateContent true
                // 跳过定义文件
                if (methodDefinitionFiles.contains(virtualFile) || fieldDefinitionFiles.contains(virtualFile)) {
                    return@iterateContent true
                }

                val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@iterateContent true

                // 遍历文件中的元素，查找匹配的引用
                psiFile.accept(object : PsiRecursiveElementVisitor() {
                    override fun visitElement(element: PsiElement) {
                        super.visitElement(element)

                        // 检查元素文本是否匹配名称
                        if (element.text == name) {
                            val containingFile = element.containingFile ?: return
                            val containingVirtualFile = containingFile.virtualFile ?: return

                            val document = PsiDocumentManager.getInstance(project).getDocument(containingFile)
                            val lineRange = if (document != null) {
                                val start = document.getLineNumber(element.textRange.startOffset) + 1
                                val end = document.getLineNumber(element.textRange.endOffset) + 1
                                "$start - $end"
                            } else {
                                "未知"
                            }

                            // 判断引用类型
                            val type: String
                            val declaringClass: String

                            val parent = element.parent
                            when (parent) {
                                is PsiMethodCallExpression -> {
                                    type = "方法引用"
                                    val resolvedMethod = parent.resolveMethod()
                                    declaringClass = resolvedMethod?.containingClass?.qualifiedName ?: "未知"
                                }

                                is PsiReferenceExpression -> {
                                    val resolved = parent.resolve()
                                    when (resolved) {
                                        is PsiMethod -> {
                                            type = "方法引用"
                                            declaringClass = resolved.containingClass?.qualifiedName ?: "未知"
                                        }

                                        is PsiField -> {
                                            type = "字段引用"
                                            declaringClass = resolved.containingClass?.qualifiedName ?: "未知"
                                        }

                                        else -> {
                                            type = "变量引用"
                                            declaringClass = "局部"
                                        }
                                    }
                                }

                                else -> {
                                    type = "变量引用"
                                    declaringClass = "局部"
                                }
                            }

                            results.add(
                                ReferenceSearchResult(
                                    name = name,
                                    type = type,
                                    declaringClass = declaringClass,
                                    fileName = containingVirtualFile.name,
                                    filePath = containingVirtualFile.path,
                                    lineRange = lineRange,
                                    codeSnippet = element.text.replace("\n", " ").take(80)
                                )
                            )
                        }
                    }
                })
                true
            }

            if (results.isEmpty()) {
                return@runReadAction "未在项目中找到 `$name` 的引用位置。"
            }

            // 去重并限制数量
            val uniqueResults = results.distinctBy { "${it.filePath}-${it.lineRange}-${it.codeSnippet}" }
            val totalCount = uniqueResults.size
            val displayResults = uniqueResults.take(maxResults)

            val sb = StringBuilder()
            sb.append("### 引用查找结果: `$name` \n\n")
            sb.append("| 类型 | 名称 | 定义位置 | 文件名 | 绝对路径 | 行号范围 | 代码片段 |\n")
            sb.append("| :--- | :--- | :--- | :--- | :--- | :--- | :--- |\n")

            displayResults.forEach { result ->
                sb.append("| ${result.type} | ${result.name} | ${result.declaringClass} | ${result.fileName} | ${result.filePath} | ${result.lineRange} | `${result.codeSnippet}` |\n")
            }

            val extraInfo =
                if (totalCount > maxResults) "（显示前 $maxResults 个结果，另有 ${totalCount - maxResults} 个结果未显示）" else ""
            sb.append("\n共找到 $totalCount 处引用$extraInfo")
            sb.toString()
        }
    }

    private data class ReferenceSearchResult(
        val name: String,
        val type: String,
        val declaringClass: String,
        val fileName: String,
        val filePath: String,
        val lineRange: String,
        val codeSnippet: String
    )

    /**
     * 全局搜索 - 搜索项目中的任何内容
     * 包括：类、方法、变量、字符串常量等
     */
    fun globalSearch(project: Project, keyword: String): String {
        Log.info("调用工具，全局搜索: $keyword")
        if (keyword.isBlank()) {
            return "搜索关键词不能为空"
        }

        return runReadAction {
            val results = mutableListOf<GlobalSearchResult>()
            val maxResults = 50

            // 遍历项目文件搜索所有匹配内容
            val fileIndex = ProjectRootManager.getInstance(project).fileIndex
            val seen = mutableSetOf<String>() // 用于去重
            fileIndex.iterateContent { virtualFile ->
                if (results.size >= maxResults) return@iterateContent false
                if (!shouldInclude(virtualFile, project)) return@iterateContent true

                val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@iterateContent true

                psiFile.accept(object : PsiRecursiveElementVisitor() {
                    override fun visitElement(element: PsiElement) {
                        super.visitElement(element)
                        if (results.size >= maxResults) return

                        val text = element.text
                        if (text.contains(keyword) && text.length < 200) {
                            val doc = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                            val line = doc?.getLineNumber(element.textRange.startOffset)?.plus(1) ?: 1

                            // 去重：文件路径+行号+类型
                            val key = "${virtualFile.path}:$line:${element.javaClass.simpleName}"
                            if (seen.contains(key)) return
                            seen.add(key)

                            // 判断类型
                            val type = when (element) {
                                is PsiComment -> "注释"
                                is PsiLiteralExpression -> "字符串"
                                is PsiClass -> "类"
                                is PsiMethod -> "方法"
                                is PsiVariable -> "变量"
                                else -> "字符串"
                            }
                            results.add(
                                GlobalSearchResult(
                                    type,
                                    text.take(50),
                                    virtualFile.name,
                                    virtualFile.path,
                                    "第${line}行"
                                )
                            )
                        }
                    }
                })
                true
            }

            if (results.isEmpty()) {
                return@runReadAction "未在项目中找到与 `$keyword` 相关的内容。"
            }

            // 构建结果
            val sb = StringBuilder()
            sb.appendLine("### 全局搜索结果: `$keyword`")
            sb.appendLine()
            sb.appendLine("| 类型 | 名称 | 文件名 | 路径 | 位置 |")
            sb.appendLine("| :--- | :--- | :--- | :--- | :--- |")

            results.take(maxResults).forEach { r ->
                sb.appendLine("| ${r.type} | ${r.name} | ${r.fileName} | ${r.filePath} | ${r.location} |")
            }

            sb.appendLine()
            sb.appendLine("共找到 ${results.size} 个结果")

            sb.toString()
        }
    }

    private data class GlobalSearchResult(
        val type: String,
        val name: String,
        val fileName: String,
        val filePath: String,
        val location: String
    )
}
