package com.github.bboygf.over_code.utils

import com.github.bboygf.over_code.po.LlmToolDefinition
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.*
import com.github.bboygf.over_code.enums.ChatPattern
import com.github.bboygf.over_code.services.ChatDatabaseService
import com.github.bboygf.over_code.vo.MemoryVo
import kotlinx.serialization.json.buildJsonObject

/**
 * LLM 工具接口，统一管理元数据和执行逻辑
 */
interface LlmTool {
    val name: String
    val description: String
    val parameters: JsonObject
    val isWriteTool: Boolean

    fun execute(project: Project, args: Map<String, JsonElement>, chatMode: ChatPattern): String

    fun toGenericDefinition(): LlmToolDefinition {
        return LlmToolDefinition(name, description, parameters)
    }
}


//object GetProjectInfoTool : LlmTool {
//    override val name = "get_project_files"
//    override val description = "列出当前项目下所有的文件的完整路径。"
//    override val parameters = buildJsonObject {
//        put("type", "object")
//        put("properties", buildJsonObject {})
//    }
//    override val isWriteTool = false
//
//    override fun execute(project: Project, args: Map<String, JsonElement>, chatMode: ChatPattern): String {
//        return try {
//            ProjectFileUtils.exportToMarkdown(project)
//        } catch (e: Exception) {
//            "读取项目失败: ${e.message}"
//        }
//    }
//}

/**
 * 获取目录树形结构工具 - 只列出目录，使用 MD 树形图格式
 */
object GetDirectoryTreeTool : LlmTool {
    override val name = "get_directory_tree"
    override val description = "获取当前项目的所有目录树形结构，只列出目录（不包含文件），使用 MD 树形图格式输出"
    override val parameters = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject { })
    }
    override val isWriteTool = false

    override fun execute(project: Project, args: Map<String, JsonElement>, chatMode: ChatPattern): String {
        val basePath = project.basePath ?: return "### ❌ 失败：无法获取项目路径"
        return ProjectFileUtils.getDirectoryTree(project, basePath)
    }
}


object ListDirectoryContentsTool : LlmTool {
    override val name = "list_directory_contents"
    override val description = "根据目录的绝对路径获取当前目录下的所有目录和文件。"
    override val parameters = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            putJsonObject("absolutePath") {
                put("type", "string")
                put("description", "文件的绝对路径")
            }
        })
    }
    override val isWriteTool = false

    override fun execute(project: Project, args: Map<String, JsonElement>, chatMode: ChatPattern): String {
        val path = args["absolutePath"]?.jsonPrimitive?.content ?: ""
        return ProjectFileUtils.listDirectoryContents(path)
    }
}

object ReadFileContentTool : LlmTool {
    override val name = "read_file_content"
    override val description = "根据文件的绝对路径读取文件内容"
    override val parameters = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            putJsonObject("absolutePath") {
                put("type", "string")
                put("description", "文件的绝对路径")
            }
        })
    }
    override val isWriteTool = false

    override fun execute(project: Project, args: Map<String, JsonElement>, chatMode: ChatPattern): String {
        val path = args["absolutePath"]?.jsonPrimitive?.content ?: ""
        return ProjectFileUtils.readFileContent(path)
    }
}

object GetFileFunInfoTool : LlmTool {
    override val name = "get_file_fun_info"
    override val description = "根据文件的绝对路径获取文件内所有方法详情并返回方法的行号信息"
    override val parameters = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            putJsonObject("absolutePath") {
                put("type", "string")
                put("description", "文件的绝对路径")
            }
        })
    }
    override val isWriteTool = false

    override fun execute(project: Project, args: Map<String, JsonElement>, chatMode: ChatPattern): String {
        val filePath = args["absolutePath"]?.jsonPrimitive?.content ?: ""
        return ProjectFileUtils.getFileFunInfo(project, filePath)
    }
}

object GetMethodDetailTool : LlmTool {
    override val name = "get_method_detail"
    override val description = "根据文件的绝对路径和方法名获取该方法的内容及起始终止行号"
    override val parameters = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            putJsonObject("absolutePath") {
                put("type", "string")
                put("description", "文件的绝对路径")
            }
            putJsonObject("methodName") {
                put("type", "string")
                put("description", "要查找的方法名")
            }
        })
    }
    override val isWriteTool = false

    override fun execute(project: Project, args: Map<String, JsonElement>, chatMode: ChatPattern): String {
        val fileName = args["absolutePath"]?.jsonPrimitive?.content ?: ""
        val methodName = args["methodName"]?.jsonPrimitive?.content ?: ""
        return ProjectFileUtils.getMethodDetail(project, fileName, methodName)
    }
}

object FindMethodsByNameTool : LlmTool {
    override val name = "find_methods_by_name"
    override val description = "根据方法名在项目中查找其所属的类、文件路径和行号范围"
    override val parameters = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            putJsonObject("methodName") {
                put("type", "string")
                put("description", "方法名")
            }
        })
    }
    override val isWriteTool = false

    override fun execute(project: Project, args: Map<String, JsonElement>, chatMode: ChatPattern): String {
        val methodName = args["methodName"]?.jsonPrimitive?.content ?: ""
        return ProjectFileUtils.findMethodsByName(project, methodName)
    }
}

object InspectProjectErrorsTool : LlmTool {
    override val name = "inspect_project_errors"
    override val description = "检查整个项目是否有爆红，并返回 Markdown 格式的报告"
    override val parameters = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject { })
    }
    override val isWriteTool = false

    override fun execute(project: Project, args: Map<String, JsonElement>, chatMode: ChatPattern): String {
        return ProjectFileUtils.inspectProjectErrors(project)
    }
}

object CreateFileOrDirTool : LlmTool {
    override val name = "create_file_or_dir"
    override val description = "根据文件绝对路径创建文件或者目录"
    override val parameters = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            putJsonObject("absolutePath") {
                put("type", "string")
                put("description", "文件的绝对路径")
            }
            putJsonObject("isDirectory") {
                put("type", "boolean")
                put("description", "是否为目录，true表示目录，false表示文件")
            }
        })
    }
    override val isWriteTool = true

    override fun execute(project: Project, args: Map<String, JsonElement>, chatMode: ChatPattern): String {
        if (chatMode != ChatPattern.执行模式) {
            return "当前为计划模式，请切换到执行模式！"
        }
        val path = args["absolutePath"]?.jsonPrimitive?.content ?: ""
        val isDir = args["isDirectory"]?.jsonPrimitive?.booleanOrNull ?: false
        val result = ProjectFileUtils.createFileOrDir(project, path, isDir)
        return result
    }
}

object ReplaceCodeByLineTool : LlmTool {
    override val name = "replace_code_by_line"
    override val description =
        "根据文件名、起始行号、终止行号替换文件内容，（如果不确定行号请先获取行）请不要并行修改，防止修改同一个文件导致行号不读。"
    override val parameters = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            putJsonObject("absolutePath") {
                put("type", "string")
                put("description", "文件的绝对路径")
            }
            putJsonObject("startLine") {
                put("type", "integer")
                put("description", "起始行号 (从 1 开始)")
            }
            putJsonObject("endLine") {
                put("type", "integer")
                put("description", "结束行号 (从 1 开始)")
            }
            putJsonObject("newCodeString") {
                put("type", "string")
                put("description", "新的代码")
            }
        })
    }
    override val isWriteTool = true

    override fun execute(project: Project, args: Map<String, JsonElement>, chatMode: ChatPattern): String {
        val absolutePath = args["absolutePath"]?.jsonPrimitive?.content ?: ""
        val startLine = args["startLine"]?.jsonPrimitive?.let {
            it.intOrNull ?: it.content.toIntOrNull()
        } ?: 0
        val endLine = args["endLine"]?.jsonPrimitive?.let {
            it.intOrNull ?: it.content.toIntOrNull()
        } ?: 0
        val newCodeString = args["newCodeString"]?.jsonPrimitive?.content ?: ""
        return ProjectFileUtils.replaceCodeByLine(project, absolutePath, startLine, endLine, newCodeString)
    }

}

object DeleteFileTool : LlmTool {
    override val name = "delete_file"
    override val description = "根据绝对路径删除文件或目录"
    override val parameters = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            putJsonObject("absolutePath") {
                put("type", "string")
                put("description", "文件的绝对路径")
            }
        })
    }
    override val isWriteTool = true

    override fun execute(project: Project, args: Map<String, JsonElement>, chatMode: ChatPattern): String {
        if (chatMode != ChatPattern.执行模式) {
            return "当前为计划模式，请切换到执行模式！"
        }
        val path = args["absolutePath"]?.jsonPrimitive?.content ?: ""
        return ProjectFileUtils.deleteFile(project, path)
    }
}

object ReadFileRangeTool : LlmTool {
    override val name = "read_file_range"
    override val description = "根据文件路径、起始行号、终止行号读取文件内容（带行号）"
    override val parameters = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            putJsonObject("absolutePath") {
                put("type", "string")
                put("description", "文件的绝对路径")
            }
            putJsonObject("startLine") {
                put("type", "integer")
                put("description", "起始行号 (从 1 开始)")
            }
            putJsonObject("endLine") {
                put("type", "integer")
                put("description", "终止行号 (从 1 开始)")
            }
        })
    }
    override val isWriteTool = false

    override fun execute(project: Project, args: Map<String, JsonElement>, chatMode: ChatPattern): String {
        val path = args["absolutePath"]?.jsonPrimitive?.content ?: ""
        val startLine = args["startLine"]?.jsonPrimitive?.let {
            it.intOrNull ?: it.content.toIntOrNull()
        } ?: 1
        val endLine = args["endLine"]?.jsonPrimitive?.let {
            it.intOrNull ?: it.content.toIntOrNull()
        } ?: 1
        return ProjectFileUtils.readFileRange(path, startLine, endLine)
    }
}


object FindClassByNameTool : LlmTool {
    override val name = "find_class_by_name"
    override val description = "根据类名在项目中查找其所属的文件、文件路径和行号范围"
    override val parameters = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            putJsonObject("className") {
                put("type", "string")
                put("description", "类名")
            }
        })
    }
    override val isWriteTool = false

    override fun execute(project: Project, args: Map<String, JsonElement>, chatMode: ChatPattern): String {
        val className = args["className"]?.jsonPrimitive?.content ?: ""
        return ProjectFileUtils.findClassesByName(project, className)
    }
}

object EditFileBySearchTool : LlmTool {

    override val name: String = "edit_file_by_search"
    override val description: String = """
        在文件中执行字符串的精确替换
        用法说明：
            先读后改： 在进行编辑之前，你必须在对话中至少使用过一次 Read（读取）工具。如果你在没有读取文件的情况下尝试编辑，该工具会报错。

            缩进匹配： 在编辑 Read 工具输出的文本时，请务必保留行号前缀之后的精确缩进（制表符/空格）。行号前缀的格式为：空格 + 行号 + 制表符。该制表符之后的所有内容才是匹配所需的实际文件内容。切勿在 oldString（旧字符串）或 newString（新字符串）中包含行号前缀的任何部分。

            优先修改： 始终优先编辑代码库中的现有文件。除非有明确要求，否则绝不创建新文件。

            表情符号限制： 仅在用户明确要求时才使用表情符号。除非被要求，否则避免在文件中添加表情符号。

            匹配失败： 如果在文件中找不到 oldString，编辑将失败并报错：“未在内容中找到 oldString”。

            唯一性要求： 如果 oldString 在文件中多次出现，编辑将失败并报错：“找到多个 oldString，需要更多代码上下文来唯一标识目标匹配项”。此时，请提供包含更多周边代码的更长字符串以确保唯一性，或者使用 replaceAll（替换全部）参数来修改每一个 oldString 实例。

            批量替换： 使用 replaceAll 在整个文件中替换或重命名字符串。例如，当你需要重构变量名时，此参数非常有用。
    """.trimIndent()

    override val parameters = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            putJsonObject("filePath") {
                put("type", "string")
                put("description", "文件的绝对路径")
            }
            putJsonObject("oldString") {
                put("type", "string")
                put("description", "要替换的内容也就是被覆盖的内容")
            }
            putJsonObject("newString") {
                put("type", "string")
                put("description", "新的内容")
            }
            putJsonObject("replaceAll") {
                put("type", "boolean")
                put("description", "是否替换所有匹配的内容，默认为false")
            }
        })

    }
    override val isWriteTool: Boolean = true

    override fun execute(
        project: Project,
        args: Map<String, JsonElement>,
        chatMode: ChatPattern
    ): String {
        if (chatMode != ChatPattern.执行模式) {
            return "当前为计划模式，请切换到执行模式！"
        }
        val filePath = args["filePath"]?.jsonPrimitive?.content ?: ""
        val oldString = args["oldString"]?.jsonPrimitive?.content ?: ""
        val newString = args["newString"]?.jsonPrimitive?.content ?: ""
        val replaceAll = args["replaceAll"]?.jsonPrimitive?.boolean ?: false
        return ProjectFileUtils.editFileBySearch(project, filePath, oldString, newString, replaceAll)
    }

}


/**
 * 获取记忆列表工具 - 获取所有记忆的 ID 和概要
 */
object GetMemoriesListTool : LlmTool {
    override val name = "get_memories_list"
    override val description =
        "获取所有项目记忆的列表（包含 ID 和概要）。当你想了解项目有哪些记忆、记忆的 ID 是什么时使用此工具。"
    override val parameters = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject { })
    }
    override val isWriteTool = false

    override fun execute(project: Project, args: Map<String, JsonElement>, chatMode: ChatPattern): String {
        return try {
            val dbService = ChatDatabaseService.getInstance(project)
            val memories = dbService.getAllMemorySummaries()

            if (memories.isEmpty()) {
                return "当前项目还没有记忆库内容。"
            }

            buildString {
                appendLine("【项目记忆列表】")
                appendLine()
                appendLine("| ID | 概要 |")
                appendLine("| --- | --- |")
                memories.forEach { mem ->
                    appendLine("| ${mem.memoryId} | ${mem.summary} |")
                }
                appendLine()
                appendLine("如需查看某条记忆的详细内容，请使用 get_memory_detail 工具并提供对应的 memoryId。")
            }
        } catch (e: Exception) {
            "读取记忆库失败: ${e.message}"
        }
    }
}

/**
 * 获取记忆详情工具 - 根据 ID 获取记忆的完整内容
 */
object GetMemoryDetailTool : LlmTool {
    override val name = "get_memory_detail"
    override val description = "根据记忆 ID（memoryId）获取记忆的详细内容，包括概要和详情"
    override val parameters = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            putJsonObject("memoryId") {
                put("type", "string")
                put("description", "记忆的唯一标识 ID")
            }
        })
    }
    override val isWriteTool = false

    override fun execute(project: Project, args: Map<String, JsonElement>, chatMode: ChatPattern): String {
        return try {
            val memoryId = args["memoryId"]?.jsonPrimitive?.content ?: ""
            if (memoryId.isBlank()) {
                return "获取失败：memoryId 不能为空"
            }

            val dbService = ChatDatabaseService.getInstance(project)
            val memory = dbService.getMemoryById(memoryId)

            if (memory == null) {
                return "未找到 ID 为 [$memoryId] 的记忆"
            }

            buildString {
                appendLine("【记忆详情】")
                appendLine()
                appendLine("**ID**: ${memory.memoryId}")
                appendLine()
                appendLine("**概要**: ${memory.summary}")
                appendLine()
                appendLine("**详情**:")
                appendLine()
                appendLine(memory.content)
            }
        } catch (e: Exception) {
            "获取记忆详情失败: ${e.message}"
        }
    }
}

/**
 * 修改记忆工具 - 根据 ID 修改记忆的概要和详情
 */
object UpdateMemoryTool : LlmTool {
    override val name = "update_memory"
    override val description = "根据记忆 ID（memoryId）修改记忆的概要和详情"
    override val parameters = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            putJsonObject("memoryId") {
                put("type", "string")
                put("description", "记忆的唯一标识 ID")
            }
            putJsonObject("summary") {
                put("type", "string")
                put("description", "新的经验概要")
            }
            putJsonObject("content") {
                put("type", "string")
                put("description", "新的经验详情")
            }
        })
    }
    override val isWriteTool = true

    override fun execute(project: Project, args: Map<String, JsonElement>, chatMode: ChatPattern): String {
        return try {
            val memoryId = args["memoryId"]?.jsonPrimitive?.content ?: ""
            val summary = args["summary"]?.jsonPrimitive?.content ?: ""
            val content = args["content"]?.jsonPrimitive?.content ?: ""

            if (memoryId.isBlank() || summary.isBlank() || content.isBlank()) {
                return "修改失败：memoryId、summary 和 content 都不能为空"
            }

            val dbService = ChatDatabaseService.getInstance(project)
            val existing = dbService.getMemoryById(memoryId)

            if (existing == null) {
                return "修改失败：未找到 ID 为 [$memoryId] 的记忆"
            }

            val success = dbService.updateMemory(memoryId, summary, content)
            if (success) {
                "记忆修改成功！\n- ID: $memoryId\n- 概要: $summary\n\n可以使用 get_memory_detail 工具查看修改后的内容。"
            } else {
                "记忆修改失败"
            }
        } catch (e: Exception) {
            "修改记忆失败: ${e.message}"
        }
    }
}

/**
 * 删除记忆工具 - 根据 ID 删除记忆
 */
object DeleteMemoryTool : LlmTool {
    override val name = "delete_memory"
    override val description = "根据记忆 ID（memoryId）删除指定的记忆"
    override val parameters = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            putJsonObject("memoryId") {
                put("type", "string")
                put("description", "记忆的唯一标识 ID")
            }
        })
    }
    override val isWriteTool = true

    override fun execute(project: Project, args: Map<String, JsonElement>, chatMode: ChatPattern): String {
        return try {
            val memoryId = args["memoryId"]?.jsonPrimitive?.content ?: ""
            if (memoryId.isBlank()) {
                return "删除失败：memoryId 不能为空"
            }

            val dbService = ChatDatabaseService.getInstance(project)
            val existing = dbService.getMemoryById(memoryId)

            if (existing == null) {
                return "删除失败：未找到 ID 为 [$memoryId] 的记忆"
            }

            dbService.deleteMemory(memoryId)
            "记忆删除成功！\n已删除记忆 ID: $memoryId (${existing.summary})"
        } catch (e: Exception) {
            "删除记忆失败: ${e.message}"
        }
    }
}

/**
 * 保存记忆工具 - 让 AI 可以主动保存重要信息到记忆库
 */
object SaveMemoryTool : LlmTool {
    override val name = "save_memory"
    override val description =
        "保存或更新记忆库。当发现项目特有的配置、架构约定、特殊的依赖库或工具版本、自定义代码风格等对后续开发有用的信息时，应该调用此工具保存。AI 在对话过程中发现重要信息时应主动保存，而不是等用户要求。"
    override val parameters = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            putJsonObject("summary") {
                put("type", "string")
                put(
                    "description",
                    "经验概要，简短描述这个记忆的核心内容，如：'项目使用XX框架的特定版本'、'代码规范要求使用XX风格' 等"
                )
            }
            putJsonObject("content") {
                put("type", "string")
                put("description", "经验详情，详细描述这个记忆的具体内容，包括配置值、代码示例等")
            }
        })
    }
    override val isWriteTool = false // 设置为 false 让计划模式也能使用

    override fun execute(project: Project, args: Map<String, JsonElement>, chatMode: ChatPattern): String {
        return try {
            val summary = args["summary"]?.jsonPrimitive?.content ?: ""
            val content = args["content"]?.jsonPrimitive?.content ?: ""

            if (summary.isBlank() || content.isBlank()) {
                return "保存失败：summary 和 content 不能为空"
            }

            val dbService = ChatDatabaseService.getInstance(project)

            // 查找是否已存在相同 summary 的记忆
            val existingMemory = dbService.getMemoryDetailBySummary(summary)

            if (existingMemory != null) {
                // 更新已存在的记忆
                dbService.updateMemory(existingMemory.memoryId, summary, content)
                "记忆已更新：\n- 概要：$summary\n\n在后续开发中，AI 会自动读取这些记忆来了解项目的特殊情况。"
            } else {
                // 添加新记忆
                dbService.addMemory(MemoryVo(summary = summary, content = content))
                "记忆已保存：\n- 概要：$summary\n\n在后续开发中，AI 会自动读取这些记忆来了解项目的特殊情况。"
            }
        } catch (e: Exception) {
            "保存记忆失败: ${e.message}"
        }
    }
}


object ToolRegistry {
    val allTools = listOf(
//        GetProjectInfoTool,
        ListDirectoryContentsTool,
        ReadFileContentTool,
        ReadFileRangeTool,
        GetFileFunInfoTool,
        GetMethodDetailTool,
        FindMethodsByNameTool,
        FindClassByNameTool,
        InspectProjectErrorsTool,
        CreateFileOrDirTool,
        DeleteFileTool,
        EditFileBySearchTool,
        GetMemoriesListTool,
        GetMemoryDetailTool,
        UpdateMemoryTool,
        DeleteMemoryTool,
        SaveMemoryTool,
        GetDirectoryTreeTool,
    )
}
