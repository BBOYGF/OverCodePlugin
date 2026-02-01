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
    fun abc() {
        println("Hello, world!")
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

                                stringBuilder.append("字符下标：${method.textRange.startOffset} - ${method.textRange.endOffset}\n")
                                stringBuilder.append("\n")
                            }
                        }
                    }
                }

                if (stringBuilder.isEmpty()) "未找到方法: $methodName" else stringBuilder.toString()
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
            // 1. 行号安全检查及转换 (将 1-based 转换为 0-based)
            val totalLines = document.lineCount

            if (totalLines == 0) {
                document.setText(newCodeString)
            } else {
                // 限制范围在合法行数内 (0 到 lineCount-1)
                val sLine = (startLine - 1).coerceIn(0, totalLines - 1)
                val eLine = (endLine - 1).coerceIn(0, totalLines - 1)

                if (sLine > eLine) {
                    return@Computable "失败：起始行 $startLine 大于结束行 $endLine"
                }
                // 1. 行号安全检查及转换 (将 1-based 转换为 0-based)
                val totalLines = document.lineCount

                if (totalLines == 0) {
                    document.setText(newCodeString)
                } else {
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
                }

                successCount++
                "成功：已替换 [${virtualFile.name}] 第 $startLine-$endLine 行"
            } catch (e: Exception) {
                "异常：[${virtualFile.name}] 处理时错误: ${e.message}"
            }
        })
        resultSummary.append(fileResult).append("; ")
    }

    return if (successCount > 0)
    {
        "修改文件 $finalFileName 成功: $resultSummary"
    } else
    {
        "全部操作失败: $resultSummary"
    }
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
}
