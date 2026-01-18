package com.github.bboygf.over_code

import com.github.bboygf.over_code.utils.ProjectFileUtils
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PsiErrorElementUtil

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class MyPluginTest : BasePlatformTestCase() {

    fun testXMLFile() {
        val psiFile = myFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")
        val xmlFile = assertInstanceOf(psiFile, XmlFile::class.java)

        assertFalse(PsiErrorElementUtil.hasErrors(project, xmlFile.virtualFile))

        assertNotNull(xmlFile.rootTag)

        xmlFile.rootTag?.let {
            assertEquals("foo", it.name)
            assertEquals("bar", it.value.text)
        }
    }

    fun testRename() {
        myFixture.testRename("foo.xml", "foo_after.xml", "a2")
    }

    fun testProjectService() {
        // --- 1. 准备环境 (Setup) ---
        // 在虚拟项目中创建文件结构。
        // myFixture.addFileToProject 会自动将文件注册到 ProjectRootManager 的索引中。

        // 创建一个位于 src 目录下的 Kotlin 文件
        myFixture.addFileToProject("src/utils/Helper.kt", "package utils")
        // 创建一个位于根目录的 Markdown 文件
        myFixture.addFileToProject("README.md", "# Project Info")
        // 创建一个配置文件
        myFixture.addFileToProject("config/app.properties", "version=1.0")

        // 这一步是为了验证你的 shouldInclude 逻辑：
        // 虽然 createDirectory 创建了目录，但你的代码里写了 if (file.isDirectory) return false
        // 所以我们预期结果里不应该包含 "src" 或 "utils" 这些纯目录名
        val virtualDir = myFixture.tempDirFixture.findOrCreateDir("src/ignoredDir")

        // --- 2. 执行逻辑 (Act) ---
        // 传入测试环境的 project 对象
        val resultMarkdown = ProjectFileUtils.exportToMarkdown(project)

        // 打印出来方便你在控制台调试查看（实际发布时可去掉）
        println("Generated Markdown:\n$resultMarkdown")

        // --- 3. 验证断言 (Assert) ---

        // 验证表头是否存在
        assertTrue(resultMarkdown.contains("# Project Files Report"))
        assertTrue(resultMarkdown.contains("| File Name | Absolute Path |"))

        // 验证具体文件是否被扫描到了 (检查文件名)
        assertTrue("应该包含 Helper.kt", resultMarkdown.contains("| Helper.kt |"))
        assertTrue("应该包含 README.md", resultMarkdown.contains("| README.md |"))
        assertTrue("应该包含 app.properties", resultMarkdown.contains("| app.properties |"))

        // 验证路径是否包含 (注意：测试环境的绝对路径通常以 /temp 开头，我们只验证部分路径)
        assertTrue("应该包含 src/utils 路径", resultMarkdown.contains("src/utils/Helper.kt"))

        // 验证过滤逻辑：确保目录本身没有被当做文件列出来
        // 你的代码过滤掉了 directory，所以结果里不该有单独的目录行
        assertFalse("不应该包含纯目录 src", resultMarkdown.contains("| src |"))
        assertFalse("不应该包含纯目录 config", resultMarkdown.contains("| config |"))
    }

    override fun getTestDataPath() = "src/test/testData/rename"
}
