package com.github.bboygf.over_code.utils

import com.github.bboygf.over_code.po.LlmToolDefinition
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.*
import com.github.bboygf.over_code.enums.ChatPattern

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


object GetProjectInfoTool : LlmTool {
    override val name = "get_project_info"
    override val description = "读取当前项目的完整文件结构和内容。"
    override val parameters = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
    }
    override val isWriteTool = false

    override fun execute(project: Project, args: Map<String, JsonElement>, chatMode: ChatPattern): String {
        return try {
            ProjectFileUtils.exportToMarkdown(project)
        } catch (e: Exception) {
            "读取项目失败: ${e.message}"
        }
    }
}

object ListDirectoryContentsTool : LlmTool {
    override val name = "list_directory_contents"
    override val description = "根据目录的绝对路径获取当前目录下的所有目录 and 文件，使用md格式输出字符串。"
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
        return ProjectFileUtils.readFileContent(path) ?: "null"
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
    override val description = "根据文件的绝对路径和方法名获取特定方法的详情及起始终止行号"
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
        val path = args["absolutePath"]?.jsonPrimitive?.content ?: ""
        val isDir = args["isDirectory"]?.jsonPrimitive?.booleanOrNull ?: false
        val createFileOrDir = ProjectFileUtils.createFileOrDir(project, path, isDir)
        return if (createFileOrDir == null || !createFileOrDir.exists()) {
            "创建失败！"
        } else {
            "创建成功！"
        }
    }
}

object ReplaceCodeByLineTool : LlmTool {
    override val name = "replace_code_by_line"
    override val description = "根据文件名、起始行号、终止行号替换文件内容，（如果不确定行号请先获取行）请不要并行修改，防止修改同一个文件导致行号不读。"
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

object OrganizeThoughtsTool : LlmTool {
    override val name = "organize_thoughts"
    override val description =
        "当用户有新的代码修改需求时或者用户需要按计划修改代码时调用"
    override val parameters = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {       })
    }
    override val isWriteTool = false

    override fun execute(project: Project, args: Map<String, JsonElement>, chatMode: ChatPattern): String {

        return if (chatMode == ChatPattern.计划模式) {
            """
            【当前为计划模式】
            1. 禁止在此模式下修改代码。
            2. 请利用现有工具（如获取项目结构、读取代码、查找方法等）深入理解业务逻辑。
            3. 你需要向用户列出详细的修改计划。
            4. 如果有任何不确定的地方或逻辑困惑，请在此向用户明确提问。
            5. 当所有疑点确认后，请汇总最终执行计划，并提示用户：
               "如果计划符合需求，请切换为执行模式，输入：按计划实施"
     
            """.trimIndent()
        } else {
            """
            【当前为执行模式】
            1. 请结合之前的计划和上下文，使用修改类工具（如 replace_code_by_line, create_file_or_dir 等）对代码进行实际修改。
            2. 每次修改完成后，必须使用 inspect_project_errors 或 review_code_by_file 工具检查代码是否报错。
            3. 如果出现报错，请继续分析并修复，直到所有相关逻辑修改完毕且无编译错误。
          
            """.trimIndent()
        }
    }
}

object ToolRegistry {
    val allTools = listOf(
        GetProjectInfoTool,
        ListDirectoryContentsTool,
        ReadFileContentTool,
        ReadFileRangeTool,
        GetFileFunInfoTool,
        GetMethodDetailTool,
        FindMethodsByNameTool,
        FindClassByNameTool,
        InspectProjectErrorsTool,
        CreateFileOrDirTool,
        ReplaceCodeByLineTool,
        DeleteFileTool,
        OrganizeThoughtsTool
    )
}
