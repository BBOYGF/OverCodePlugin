package com.github.bboygf.over_code.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * 智能文本替换工具
 * 实现多种文本匹配策略，支持模糊匹配和智能定位
 */
object SmartReplacer {

    /**
     * Levenshtein 距离算法 - 计算两个字符串的编辑距离
     */
    private fun levenshtein(a: String, b: String): Int {
        if (a.isEmpty() || b.isEmpty()) {
            return maxOf(a.length, b.length)
        }

        val matrix = Array(a.length + 1) { IntArray(b.length + 1) }

        for (i in 0..a.length) {
            matrix[i][0] = i
        }
        for (j in 0..b.length) {
            matrix[0][j] = j
        }

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                matrix[i][j] = minOf(
                    matrix[i - 1][j] + 1,
                    matrix[i][j - 1] + 1,
                    matrix[i - 1][j - 1] + cost
                )
            }
        }

        return matrix[a.length][b.length]
    }

    // 相似度阈值
    private const val SINGLE_CANDIDATE_SIMILARITY_THRESHOLD = 0.0
    private const val MULTIPLE_CANDIDATES_SIMILARITY_THRESHOLD = 0.3

    /**
     * 从原始行中提取块
     */
    private fun extractBlock(content: String, lines: List<String>, startLine: Int, endLine: Int): String {
        var matchStartIndex = 0
        for (k in 0 until startLine) {
            matchStartIndex += lines[k].length + 1
        }
        var matchEndIndex = matchStartIndex
        for (k in startLine..endLine) {
            matchEndIndex += lines[k].length
            if (k < endLine) {
                matchEndIndex += 1
            }
        }
        return content.substring(matchStartIndex, matchEndIndex)
    }

    /**
     * SimpleReplacer: 精确匹配
     */
    private fun simpleReplace(content: String, find: String): Sequence<String> {
        return if (content.contains(find)) {
            sequenceOf(find)
        } else {
            emptySequence()
        }
    }

    /**
     * LineTrimmedReplacer: 行级别 trim 后匹配
     */
    private fun lineTrimmedReplace(content: String, find: String): Sequence<String> {
        val originalLines = content.lines()
        val searchLines = find.lines().toMutableList()

        // 移除末尾空行
        if (searchLines.isNotEmpty() && searchLines.last().isEmpty()) {
            searchLines.removeAt(searchLines.size - 1)
        }

        return sequence {
            for (i in 0..originalLines.size - searchLines.size) {
                var matches = true

                for (j in searchLines.indices) {
                    if (originalLines[i + j].trim() != searchLines[j].trim()) {
                        matches = false
                        break
                    }
                }

                if (matches) {
                    var matchStartIndex = 0
                    for (k in 0 until i) {
                        matchStartIndex += originalLines[k].length + 1
                    }

                    var matchEndIndex = matchStartIndex
                    for (k in searchLines.indices) {
                        matchEndIndex += originalLines[i + k].length
                        if (k < searchLines.size - 1) {
                            matchEndIndex += 1
                        }
                    }

                    yield(content.substring(matchStartIndex, matchEndIndex))
                }
            }
        }
    }

    /**
     * BlockAnchorReplacer: 基于首尾行锚点的块匹配，使用 Levenshtein 距离
     */
    private fun blockAnchorReplace(content: String, find: String): Sequence<String> {
        val originalLines = content.lines()
        val searchLines = find.lines().toMutableList()

        if (searchLines.size < 3) {
            return emptySequence()
        }

        // 移除末尾空行
        if (searchLines.last().isEmpty()) {
            searchLines.removeAt(searchLines.size - 1)
        }

        val firstLineSearch = searchLines.first().trim()
        val lastLineSearch = searchLines.last().trim()
        val searchBlockSize = searchLines.size

        // 收集所有候选位置
        val candidates = mutableListOf<Pair<Int, Int>>()
        for (i in originalLines.indices) {
            if (originalLines[i].trim() != firstLineSearch) continue

            for (j in i + 2 until originalLines.size) {
                if (originalLines[j].trim() == lastLineSearch) {
                    candidates.add(i to j)
                    break
                }
            }
        }

        if (candidates.isEmpty()) {
            return emptySequence()
        }

        return sequence {
            // 单候选情况
            if (candidates.size == 1) {
                val (startLine, endLine) = candidates[0]
                val actualBlockSize = endLine - startLine + 1

                var similarity = 0.0
                val linesToCheck = minOf(searchBlockSize - 2, actualBlockSize - 2)

                if (linesToCheck > 0) {
                    for (j in 1 until minOf(searchBlockSize - 1, actualBlockSize - 1)) {
                        val originalLine = originalLines[startLine + j].trim()
                        val searchLine = searchLines[j].trim()
                        val maxLen = maxOf(originalLine.length, searchLine.length)
                        if (maxLen == 0) continue

                        val distance = levenshtein(originalLine, searchLine)
                        similarity += (1.0 - distance.toDouble() / maxLen) / linesToCheck

                        if (similarity >= SINGLE_CANDIDATE_SIMILARITY_THRESHOLD) {
                            break
                        }
                    }
                } else {
                    similarity = 1.0
                }

                if (similarity >= SINGLE_CANDIDATE_SIMILARITY_THRESHOLD) {
                    yield(extractBlock(content, originalLines, startLine, endLine))
                }
            } else {
                // 多候选情况
                var bestMatch: Pair<Int, Int>? = null
                var maxSimilarity = -1.0

                for ((startLine, endLine) in candidates) {
                    val actualBlockSize = endLine - startLine + 1
                    var similarity = 0.0
                    val linesToCheck = minOf(searchBlockSize - 2, actualBlockSize - 2)

                    if (linesToCheck > 0) {
                        for (j in 1 until minOf(searchBlockSize - 1, actualBlockSize - 1)) {
                            val originalLine = originalLines[startLine + j].trim()
                            val searchLine = searchLines[j].trim()
                            val maxLen = maxOf(originalLine.length, searchLine.length)
                            if (maxLen == 0) continue

                            val distance = levenshtein(originalLine, searchLine)
                            similarity += 1.0 - distance.toDouble() / maxLen
                        }
                        similarity /= linesToCheck
                    } else {
                        similarity = 1.0
                    }

                    if (similarity > maxSimilarity) {
                        maxSimilarity = similarity
                        bestMatch = startLine to endLine
                    }
                }

                if (maxSimilarity >= MULTIPLE_CANDIDATES_SIMILARITY_THRESHOLD && bestMatch != null) {
                    yield(extractBlock(content, originalLines, bestMatch.first, bestMatch.second))
                }
            }
        }
    }

    /**
     * WhitespaceNormalizedReplacer: 空白字符规范化匹配
     */
    private fun whitespaceNormalizedReplace(content: String, find: String): Sequence<String> {
        val normalizeWhitespace: (String) -> String = { text ->
            text.replace(Regex("\\s+"), " ").trim()
        }
        val normalizedFind = normalizeWhitespace(find)
        val lines = content.lines()

        return sequence {
            // 单行匹配
            for (line in lines) {
                if (normalizeWhitespace(line) == normalizedFind) {
                    yield(line)
                }
            }

            // 多行块匹配
            val findLines = find.lines()
            if (findLines.size > 1) {
                for (i in 0..lines.size - findLines.size) {
                    val block = lines.subList(i, i + findLines.size).joinToString("\n")
                    if (normalizeWhitespace(block) == normalizedFind) {
                        yield(block)
                    }
                }
            }
        }
    }

    /**
     * IndentationFlexibleReplacer: 忽略缩进差异的匹配
     */
    private fun indentationFlexibleReplace(content: String, find: String): Sequence<String> {
        fun removeIndentation(text: String): String {
            val lines = text.lines()
            val nonEmptyLines = lines.filter { it.trim().isNotEmpty() }
            if (nonEmptyLines.isEmpty()) return text

            val minIndent = nonEmptyLines.minOf { line ->
                val match = Regex("^(\\s*)").find(line)
                match?.groupValues?.get(1)?.length ?: 0
            }

            return lines.joinToString("\n") { line ->
                if (line.trim().isEmpty()) line else line.drop(minIndent)
            }
        }

        val normalizedFind = removeIndentation(find)
        val contentLines = content.lines()
        val findLines = find.lines()

        return sequence {
            for (i in 0..contentLines.size - findLines.size) {
                val block = contentLines.subList(i, i + findLines.size).joinToString("\n")
                if (removeIndentation(block) == normalizedFind) {
                    yield(block)
                }
            }
        }
    }

    /**
     * EscapeNormalizedReplacer: 处理转义字符的匹配
     */
    private fun escapeNormalizedReplace(content: String, find: String): Sequence<String> {
        fun unescapeString(str: String): String {
            return str.replace(Regex("\\\\(n|t|r|'|\"|`|\\\\|\$)")) { match ->
                when (match.groupValues[1]) {
                    "n" -> "\n"
                    "t" -> "\t"
                    "r" -> "\r"
                    "'" -> "'"
                    "\"" -> "\""
                    "`" -> "`"
                    "\\" -> "\\"
                    "$" -> "$"
                    else -> match.value
                }
            }
        }

        val unescapedFind = unescapeString(find)
        val lines = content.lines()
        val findLines = unescapedFind.lines()

        return sequence {
            // 直接匹配
            if (content.contains(unescapedFind)) {
                yield(unescapedFind)
            }

            // 在内容中查找转义版本
            for (i in 0..lines.size - findLines.size) {
                val block = lines.subList(i, i + findLines.size).joinToString("\n")
                if (unescapeString(block) == unescapedFind) {
                    yield(block)
                }
            }
        }
    }

    /**
     * TrimmedBoundaryReplacer: 边界 trim 匹配
     */
    private fun trimmedBoundaryReplace(content: String, find: String): Sequence<String> {
        val trimmedFind = find.trim()

        if (trimmedFind == find) {
            return emptySequence()
        }

        val lines = content.lines()
        val findLines = find.lines()

        return sequence {
            if (content.contains(trimmedFind)) {
                yield(trimmedFind)
            }

            for (i in 0..lines.size - findLines.size) {
                val block = lines.subList(i, i + findLines.size).joinToString("\n")
                if (block.trim() == trimmedFind) {
                    yield(block)
                }
            }
        }
    }

    /**
     * ContextAwareReplacer: 上下文感知匹配
     */
    private fun contextAwareReplace(content: String, find: String): Sequence<String> {
        val findLines = find.lines().toMutableList()

        if (findLines.size < 3) {
            return emptySequence()
        }

        // 移除末尾空行
        if (findLines.last().isEmpty()) {
            findLines.removeAt(findLines.size - 1)
        }

        val contentLines = content.lines()
        val firstLine = findLines.first().trim()
        val lastLine = findLines.last().trim()

        return sequence {
            for (i in contentLines.indices) {
                if (contentLines[i].trim() != firstLine) continue

                for (j in i + 2 until contentLines.size) {
                    if (contentLines[j].trim() == lastLine) {
                        val blockLines = contentLines.subList(i, j + 1)

                        if (blockLines.size == findLines.size) {
                            var matchingLines = 0
                            var totalNonEmptyLines = 0

                            for (k in 1 until blockLines.size - 1) {
                                val blockLine = blockLines[k].trim()
                                val findLine = findLines[k].trim()

                                if (blockLine.isNotEmpty() || findLine.isNotEmpty()) {
                                    totalNonEmptyLines++
                                    if (blockLine == findLine) {
                                        matchingLines++
                                    }
                                }
                            }

                            if (totalNonEmptyLines == 0 || matchingLines.toDouble() / totalNonEmptyLines >= 0.5) {
                                yield(blockLines.joinToString("\n"))
                                break
                            }
                        }
                        break
                    }
                }
            }
        }
    }

    /**
     * MultiOccurrenceReplacer: 多出现匹配（返回所有精确匹配）
     */
    private fun multiOccurrenceReplace(content: String, find: String): Sequence<String> {
        return sequence {
            var startIndex = 0
            while (true) {
                val index = content.indexOf(find, startIndex)
                if (index == -1) break
                yield(find)
                startIndex = index + find.length
            }
        }
    }

    /**
     * 智能文本替换核心方法
     *
     * @param content 原始内容
     * @param oldString 要替换的文本
     * @param newString 新文本
     * @param replaceAll 是否替换所有匹配项
     * @return 替换后的内容
     * @throws IllegalArgumentException 如果 oldString 未找到或有多个匹配（非 replaceAll 模式）
     */
    fun smartReplace(content: String, oldString: String, newString: String, replaceAll: Boolean = false): String {
        require(oldString != newString) { "oldString 和 newString 必须不同" }

        val replacers = listOf(
            ::simpleReplace,
            ::lineTrimmedReplace,
            ::blockAnchorReplace,
            ::whitespaceNormalizedReplace,
            ::indentationFlexibleReplace,
            ::escapeNormalizedReplace,
            ::trimmedBoundaryReplace,
            ::contextAwareReplace,
            ::multiOccurrenceReplace
        )

        var notFound = true

        for (replacer in replacers) {
            for (search in replacer(content, oldString)) {
                val index = content.indexOf(search)
                if (index == -1) continue

                notFound = false

                if (replaceAll) {
                    Log.info("SmartReplace: 使用 replaceAll 模式替换所有匹配项")
                    return content.replace(search, newString)
                }

                val lastIndex = content.lastIndexOf(search)
                if (index != lastIndex && content.isNotEmpty()) {
                    throw IllegalArgumentException(
                        "找到多个匹配项。请提供更多上下文来定位正确的匹配。"
                    )
                }

                Log.info("SmartReplace: 成功找到唯一匹配项，执行替换")
                return content.substring(0, index) + newString + content.substring(index + search.length)
            }
        }

        if (notFound) {
            throw IllegalArgumentException("未在内容中找到 oldString")
        }

        throw IllegalArgumentException(
            "找到多个匹配项。请提供更多上下文来定位正确的匹配。"
        )
    }

    /**
     * 根据搜索文本替换文件内容
     *
     * @param project 项目对象
     * @param filePath 文件绝对路径
     * @param oldString 要替换的文本
     * @param newString 新文本
     * @param replaceAll 是否替换所有匹配项
     * @param virtualFileFinder 用于查找 VirtualFile 的函数
     * @param commitAndFormat 用于提交和格式化的函数
     * @return 操作结果信息（包含修改位置信息）
     */
    fun editFileBySearch(
        project: Project,
        filePath: String,
        oldString: String = "",
        newString: String,
        replaceAll: Boolean = false,
        virtualFileFinder: (String) -> VirtualFile?,
        commitAndFormat: (Project, com.intellij.openapi.editor.Document, Int, Int) -> Unit
    ): String {
        Log.info("调用智能文本替换工具: $filePath")
        if (oldString == newString) {
            return "### ❌ 失败: oldString 和 newString 必须不同"
        }
        val virtualFile = virtualFileFinder(filePath)
            ?: return "### ❌ 失败: 未找到文件\n路径: `$filePath`"
        if (virtualFile.isDirectory) {
            return "### ❌ 失败: 路径是目录而非文件\n路径: `$filePath`"
        }
        var newContent: String? = null
        var originalContentLength = 0
        var oldContentStartLine = 1
        var oldContentEndLine = 1
        val originalContentBackup = StringBuilder()

        // 1. 读取阶段：在 Read Action 中计算新内容
        val readError = com.intellij.openapi.application.runReadAction {
            val document = FileDocumentManager.getInstance().getDocument(virtualFile)
                ?: return@runReadAction "### ❌ 失败: 无法加载文件文档内容 [${virtualFile.name}]"

            val originalContent = document.text
            originalContentLength = originalContent.length
            // 备份原始内容
            originalContentBackup.append(originalContent)

            // 计算 oldString 在文档中的行号范围
            val oldStringIndex = originalContent.indexOf(oldString)
            if (oldStringIndex >= 0) {
                oldContentStartLine = document.getLineNumber(oldStringIndex) + 1
                oldContentEndLine = document.getLineNumber(oldStringIndex + oldString.length) + 1
            }

            try {
                newContent = smartReplace(originalContent, oldString, newString, replaceAll)
                null // 无错误
            } catch (e: IllegalArgumentException) {
                return@runReadAction "### ❌ 失败: ${e.message}"
            }
        }

        if (readError != null) return readError
        val contentToWrite = newContent ?: return "### 💥 异常: 未能生成新内容"

        // 2. 写入阶段：在 EDT 中执行 WriteCommandAction
        var resultMessage = ""
        val finalStartLine = oldContentStartLine
        val finalEndLine = oldContentEndLine
        val finalFilePath = filePath
        val finalOldContent = oldString
        val finalNewContent = newString

        val runnable = Runnable {
            WriteCommandAction.runWriteCommandAction(project) {
                try {
                    val document = FileDocumentManager.getInstance().getDocument(virtualFile)
                    if (document != null) {
                        document.setText(contentToWrite)
                        commitAndFormat(project, document, 0, contentToWrite.length)
                        resultMessage = "### ✅ 成功: 文件已更新 [${virtualFile.name}]\n" +
                                "- 原始内容长度: $originalContentLength 字符\n" +
                                "- 新内容长度: ${contentToWrite.length} 字符\n" +
                                "- 替换模式: ${if (replaceAll) "全部替换" else "单处替换"}\n" +
                                "- 修改行号: 第 $finalStartLine - $finalEndLine 行\n" +
                                "请调用相关工具检查代码是否报错或者爆红。\n\n" +
                                // 附加 EditInfo 用于前端解析
                                "[EDIT_INFO]\n" +
                                "filePath=$finalFilePath\n" +
                                "oldContent=${finalOldContent.replace("\n", "\\n").replace("|", "\\|")}\n" +
                                "newContent=${finalNewContent.replace("\n", "\\n").replace("|", "\\|")}\n" +
                                "startLine=$finalStartLine\n" +
                                "endLine=$finalEndLine\n" +
                                "[/EDIT_INFO]"

                    } else {
                        resultMessage = "### ❌ 失败: 无法获取文档对象用于写入 [${virtualFile.name}]"
                    }
                } catch (e: Exception) {
                    resultMessage = "### 💥 异常: 写入文件时发生错误\n${e.message}"
                }
            }
        }

        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) {
            runnable.run()
        } else {
            application.invokeAndWait(runnable)
        }

        return resultMessage
    }
}
