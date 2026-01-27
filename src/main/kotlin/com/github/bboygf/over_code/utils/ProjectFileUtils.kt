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
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
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
        val ignoredExtensions = setOf("class", "jar", "exe", "dll", "pyc")
        if (ignoredExtensions.contains(file.extension?.lowercase())) return false

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

    /**
     * 根据文件获取文件内所有方法详情
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
            // 1. 获取 VirtualFile 集合 (最新推荐 API)
            val virtualFiles = FilenameIndex.getVirtualFilesByName(
                finalFileName,
                GlobalSearchScope.projectScope(project)
            )
            val stringBuilder = StringBuilder()
            val psiManager = PsiManager.getInstance(project)
            virtualFiles.forEach { virtualFile ->
                // 2. 将 VirtualFile 转换为 PsiFile
                val psiFile = psiManager.findFile(virtualFile)
                // 3. 提取类
                if (psiFile is PsiClassOwner) {
                    val classes = psiFile.classes
                    classes.forEach { psiClass ->
//                    println("找到类: ${psiClass.qualifiedName}")
                        val methods: Array<PsiMethod?> = psiClass.methods
                        methods.forEach { method ->
                            if (method != null && method.isConstructor) {
                                return@forEach
                            }
                            val commentText = method?.let { m ->
                                // navigationElement 会自动处理 Kotlin/Java 的源码定位
                                val originalElement = m.navigationElement
                                // PsiDocCommentOwner 是 Java 和 Kotlin 共同实现的接口
//                                when (originalElement) {
//                                    // Java 方法或已生成的轻量级类成员
//                                    is PsiDocCommentOwner -> originalElement.docComment?.text
//
//                                    // Kotlin 源码中的函数
//                                    is KtNamedFunction -> originalElement.docComment?.text
//
//                                    else -> ""
//                                }
                                // PsiDocCommentOwner 是 Java 和 Kotlin 共同实现的接口
                                if (originalElement is PsiDocCommentOwner) {
                                    originalElement.docComment?.text
                                } else {
                                    null
                                }
                            }
                            stringBuilder.append("备注：${commentText ?: ""}\r\n")
                            stringBuilder.append("方法名：${method?.name}\r\n")
                            stringBuilder.append("参数：${method?.parameterList?.text}}\r\n")
                            stringBuilder.append("方法(index)下标：${method?.startOffset} - ${method?.endOffset}\n")
                        }
                    }
                }
            }
            stringBuilder.toString()
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

            virtualFiles.forEach { virtualFile ->
                val psiFile = psiManager.findFile(virtualFile)

                // 处理包含类定义的文件 (Java 或 Kotlin 类)
                if (psiFile is PsiClassOwner) {
                    psiFile.classes.forEach { psiClass ->
                        // 过滤出指定名称的方法
                        val methods = psiClass.findMethodsByName(methodName, false)

                        methods.forEach { method ->
                            // 排除构造函数
                            if (method.isConstructor) return@forEach
                            stringBuilder.append("--- 方法详情 ---\n")
                            stringBuilder.append("所属类：${psiClass.qualifiedName}\n")
                            stringBuilder.append("${method.text}\n")
                            stringBuilder.append("方法(index)下标：${method.startOffset} - ${method.endOffset}\n")
                        }
                    }
                }

                // 特殊处理：Kotlin 顶层函数 (Top-level functions)
                // 如果是 Kotlin 文件，顶层函数不在 psiFile.classes 里
//                if (psiFile is KtFile) {
//                    psiFile.declarations.filterIsInstance<KtNamedFunction>().forEach { ktFunction ->
//                        if (ktFunction.name == methodName) {
//                            stringBuilder.append("--- 顶层方法详情 ---\n")
//                            stringBuilder.append("${ktFunction.text}\n")
//                            stringBuilder.append("方法位置：${ktFunction.startOffset} - ${ktFunction.endOffset}\n")
//                        }
//                    }
//                }
            }

            if (stringBuilder.isEmpty()) "未找到方法: $methodName" else stringBuilder.toString()
        }
    }

//    /**
//     * 替换指定文件中的方法
//     * @param project 项目
//     * @param fileName 文件名
//     * @param methodName 要替换的方法名
//     * @param newMethodCode 新的方法完整代码字符串 (例如: "fun hello() { println(\"hi\") }")
//     */
//    fun replaceMethodContent(project: Project, fileName: String, methodName: String, newMethodCode: String): String {
//        val finalFileName = File(fileName).name
//
//        // 1. 获取 VirtualFiles (需要 ReadAction)
//        val virtualFiles = runReadAction {
//            FilenameIndex.getVirtualFilesByName(
//                finalFileName,
//                GlobalSearchScope.projectScope(project)
//            )
//        }
//
//        if (virtualFiles.isEmpty()) return "失败：未找到文件 $finalFileName"
//
//        val resultSummary = StringBuilder()
//        var totalReplacedCount = 0
//
//        virtualFiles.forEach { virtualFile ->
//            // 2. 获取 PSI (需要 ReadAction)
//            val psiFile = runReadAction {
//                PsiManager.getInstance(project).findFile(virtualFile)
//            } ?: return@forEach
//
//            // 3. 执行修改 (WriteCommandAction 会自动处理写锁)
//            try {
//                val countInFile = WriteCommandAction.writeCommandAction(project, psiFile)
//                    .withName("AI Replace Method")
//                    .withGroupId("OverCodeGroup")
//                    .compute<Int, Throwable> {
//                        var replacedCount = 0
//
//                        // 优化：使用更精确的深度优先遍历，只找方法和函数
//                        psiFile.accept(object : PsiRecursiveElementWalkingVisitor() {
//                            override fun visitElement(element: PsiElement) {
//                                val isMatch = when (element) {
//                                    is PsiMethod -> element.name == methodName && !element.isConstructor
//                                    is KtNamedFunction -> element.name == methodName
//                                    else -> false
//                                }
//
//                                if (isMatch) {
//                                    // 找到匹配项，执行替换
//                                    replacePsiElement(project, element.navigationElement, newMethodCode)
//                                    replacedCount++
//                                    // 如果一个文件里只打算替换一个同名方法，可以调用 stopWalking()
//                                }
//                                super.visitElement(element)
//                            }
//                        })
//                        replacedCount
//                    }
//
//                if (countInFile > 0) {
//                    resultSummary.append("[${virtualFile.name}] 替换 $countInFile 处;")
//                    totalReplacedCount += countInFile
//                }
//            } catch (e: Exception) {
//                resultSummary.append("[${virtualFile.name}] 出错: ${e.message};")
//            }
//        }
//
//        return if (totalReplacedCount > 0) "成功：$resultSummary" else "失败：未匹配到方法名"
//    }

    /**
     * 执行底层的 PSI 替换逻辑
     */
    private fun replacePsiElement(project: Project, oldElement: PsiElement, newCode: String) {
        val isKotlin = oldElement.language.id.equals("kotlin", ignoreCase = true)

        val newElement: PsiElement = if (isKotlin) {
            // 创建 Kotlin 方法节点
            KtPsiFactory(project).createFunction(newCode)
        } else {
            // 创建 Java 方法节点
            PsiElementFactory.getInstance(project).createMethodFromText(newCode, oldElement.context)
        }

        // 在真实的物理节点上执行替换
        val replacedElement = oldElement.replace(newElement)

        // 格式化新代码
        CodeStyleManager.getInstance(project).reformat(replacedElement)
    }

    /**
     * 根据字符偏移量（Index）替换文件内容
     * @param project 项目
     * @param fileName 文件名
     * @param startOffset 起始偏移量 (method.startOffset)
     * @param endOffset 结束偏移量 (method.endOffset)
     * @param newCodeString 新的代码
     */
    fun replaceCodeByOffset(
        project: Project,
        fileName: String,
        startOffset: Int,
        endOffset: Int,
        newCodeString: String
    ): String {
        var finalFileName = fileName
        val file = File(fileName)
        if (file.exists()) {
            finalFileName = file.name
        }

        // 修复点 1: 在 ReadAction 中获取文件列表
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
            // 使用 Computable 来获取写操作块内部返回的字符串信息
            val fileResult = WriteCommandAction.runWriteCommandAction<String>(project, Computable {
                try {
                    // 1. 获取 Document 对象
                    val document = FileDocumentManager.getInstance().getDocument(virtualFile)
                        ?: return@Computable "跳过：无法加载文件 [${virtualFile.name}] 的内容"

                    // 2. 安全检查：确保偏移量在文件范围内
                    val safeStart = startOffset.coerceIn(0, document.textLength)
                    val safeEnd = endOffset.coerceIn(0, document.textLength)

                    if (safeStart > safeEnd) {
                        return@Computable "失败：起始偏移量 $safeStart 大于结束偏移量 $safeEnd"
                    }

                    // 3. 直接进行文本替换
                    document.replaceString(safeStart, safeEnd, newCodeString)

                    // 4. 同步到 PSI 树
                    val psiDocumentManager = PsiDocumentManager.getInstance(project)
                    psiDocumentManager.commitDocument(document)

                    // 5. 自动格式化替换后的区域
                    val psiFile = psiDocumentManager.getPsiFile(document)
                    if (psiFile != null) {
                        CodeStyleManager.getInstance(project).reformatRange(
                            psiFile,
                            safeStart,
                            safeStart + newCodeString.length
                        )
                    }

                    successCount++
                    "成功：已替换 [${virtualFile.name}] 偏移量 $safeStart-$safeEnd"
                } catch (e: Exception) {
                    "异常：[${virtualFile.name}] 处理时发生错误: ${e.message}"
                }
            })
            resultSummary.append(fileResult).append("; ")
        }

        return if (successCount > 0) {
            "成功 (共 $successCount 处): $resultSummary"
        } else {
            "全部操作失败: $resultSummary"
        }
    }
}