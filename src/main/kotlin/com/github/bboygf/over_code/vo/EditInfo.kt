package com.github.bboygf.over_code.vo

/**
 * 代码修改信息 - 用于存储修改前后的内容和位置
 */
data class EditInfo(
    val filePath: String,      // 文件绝对路径
    val oldContent: String,    // 修改前的内容
    val newContent: String,    // 修改后的内容
    val startLine: Int,        // 修改起始行号 (从1开始)
    val endLine: Int          // 修改结束行号 (从1开始)
) {
    /**
     * 序列化为 JSON 字符串 - 用于嵌入到工具返回结果中
     */
    fun toJson(): String {
        return """
            |[EDIT_INFO]
            |filePath=$filePath
            |oldContent=${oldContent.replace("\n", "\\n").replace("|", "\\|")}
            |newContent=${newContent.replace("\n", "\\n").replace("|", "\\|")}
            |startLine=$startLine
            |endLine=$endLine
            |[/EDIT_INFO]
        """.trimMargin()
    }

    companion object {
        /**
         * 从工具返回结果中解析 EditInfo
         */
        fun parseFromResult(result: String): EditInfo? {
            if (!result.contains("[EDIT_INFO]")) return null

            return try {
                val start = result.indexOf("[EDIT_INFO]")
                val end = result.indexOf("[/EDIT_INFO]")
                if (start == -1 || end == -1) return null

                val editBlock = result.substring(start + "[EDIT_INFO]".length, end)
                val map = editBlock.lines()
                    .filter { it.contains("=") }
                    .associate {
                        val idx = it.indexOf("=")
                        it.substring(0, idx).trim() to it.substring(idx + 1).trim()
                    }

                EditInfo(
                    filePath = map["filePath"] ?: return null,
                    oldContent = map["oldContent"]?.replace("\\n", "\n")?.replace("\\|", "|") ?: return null,
                    newContent = map["newContent"]?.replace("\\n", "\n")?.replace("\\|", "|") ?: return null,
                    startLine = map["startLine"]?.toIntOrNull() ?: 1,
                    endLine = map["endLine"]?.toIntOrNull() ?: 1
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}