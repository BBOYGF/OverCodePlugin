package com.github.bboygf.over_code.utils

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SmartReplacerTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String {
        return "src/test/testData"
    }

    /**
     * 测试场景1: 空文件 + 非空 oldString（预期失败）
     * 这是用户报告的问题场景
     */
    fun testEmptyFileWithNonEmptyOldString() {
        // 1. 准备：创建一个空文件
        val emptyFile = myFixture.addFileToProject("empty.txt", "")
        val filePath = emptyFile.virtualFile.path

        // 2. 执行：尝试替换空文件中的非空内容
        val result = ProjectFileUtils.editFileBySearch(
            project = project,
            filePath = filePath,
            oldString = "someContent",
            newString = "newContent"
        )

        println("=== 测试1: 空文件 + 非空 oldString ===")
        println("结果: $result")

        // 3. 验证：应该返回失败信息
        assertTrue("应该提示失败", result.contains("❌") || result.contains("失败"))
    }

    /**
     * 测试场景2: 空文件 + 空 oldString + 非空 newString（插入模式）
     */
    fun testEmptyFileWithEmptyOldString() {
        // 1. 准备：创建一个空文件
        val emptyFile = myFixture.addFileToProject("empty2.txt", "")
        val filePath = emptyFile.virtualFile.path

        // 2. 执行：尝试用空 oldString 插入新内容
        val result = ProjectFileUtils.editFileBySearch(
            project = project,
            filePath = filePath,
            oldString = "",
            newString = "package com.example\n\nfun main() {}"
        )

        println("=== 测试2: 空文件 + 空 oldString ===")
        println("结果: $result")

        // 3. 验证
        // 注意：当前可能失败，这是需要修复的问题
    }

    /**
     * 测试场景3: 非空文件 + 正常替换（基线测试）
     */
    fun testNormalFileReplace() {
        // 1. 准备：创建一个有内容的文件
        val file =
            myFixture.addFileToProject("Sample.kt", "package com.example\n\nfun hello() {\n    println(\"world\")\n}")
        val filePath = file.virtualFile.path

        // 2. 执行：替换内容
        val result = ProjectFileUtils.editFileBySearch(
            project = project,
            filePath = filePath,
            oldString = "println(\"world\")",
            newString = "println(\"Hello, World!\")"
        )

        println("=== 测试3: 正常文件替换 ===")
        println("结果: $result")

        // 3. 验证：应该成功
        assertTrue("应该包含成功标识", result.contains("✅"))
    }

    /**
     * 测试场景4: 非空文件 + 空 oldString（插入到开头）
     */
    fun testNonEmptyFileWithEmptyOldString() {
        // 1. 准备：创建一个有内容的文件
        val file = myFixture.addFileToProject("Sample2.kt", "fun main() {}")
        val filePath = file.virtualFile.path

        // 2. 执行：尝试在文件开头插入内容
        val result = ProjectFileUtils.editFileBySearch(
            project = project,
            filePath = filePath,
            oldString = "",
            newString = "package com.example\n\n"
        )

        println("=== 测试4: 非空文件 + 空 oldString ===")
        println("结果: $result")
    }

    /**
     * 测试场景5: 测试 createFileOrDir 功能 - 创建新文件
     */
    fun testCreateNewFileWithContent() {
        // 使用 createFileOrDir 方法创建新文件并写入内容
        val newFilePath = "src/test_new.kt"
        val content = "package com.test\n\nclass TestClass"

        // 先确保 src 目录存在
        val testDir = myFixture.tempDirFixture.getFile("src")
        if (testDir == null) {
            myFixture.addFileToProject("src/.gitkeep", "")
        }

        // 创建新文件 (先创建空文件)
        val createResult = ProjectFileUtils.createFileOrDir(
            project = project,
            absolutePath = "${project.basePath}/src/test_new.kt",
            isDirectory = false
        )
        println("=== 测试5a: 创建新文件 ===")
        println("创建结果: $createResult")

        // 然后用 editFileBySearch 写入内容
        val editResult = ProjectFileUtils.editFileBySearch(
            project = project,
            filePath = "${project.basePath}/src/test_new.kt",
            oldString = "",
            newString = content
        )
        println("=== 测试5b: 写入内容 ===")
        println("写入结果: $editResult")
    }
}