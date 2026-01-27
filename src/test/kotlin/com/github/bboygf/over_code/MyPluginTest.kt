package com.github.bboygf.over_code

import com.github.bboygf.over_code.utils.ProjectFileUtils
import com.github.bboygf.over_code.utils.ProjectFileUtils.getFileFunInfo
import com.github.bboygf.over_code.utils.ProjectFileUtils.getMethodDetail
import com.github.bboygf.over_code.utils.ProjectFileUtils.replaceCodeByOffset
import com.github.bboygf.over_code.utils.ProjectFileUtils.replaceMethodContent
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
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
        myFixture.addFileToProject("src/utils/Helper.kt", "package utils")
        myFixture.addFileToProject("README.md", "# Project Info")
        myFixture.addFileToProject("config/app.properties", "version=1.0")

        // --- 2. 执行逻辑 (Act) ---
        val resultMarkdown = ProjectFileUtils.exportToMarkdown(project)
        println("Generated Markdown:\n$resultMarkdown")

        // --- 3. 验证断言 (Assert) ---
        assertTrue(resultMarkdown.contains("# Project Files Report"))
        assertTrue(resultMarkdown.contains("| File Name | Absolute Path |"))
        assertTrue("应该包含 Helper.kt", resultMarkdown.contains("| Helper.kt |"))
        assertTrue("应该包含 README.md", resultMarkdown.contains("| README.md |"))
        assertTrue("应该包含 app.properties", resultMarkdown.contains("| app.properties |"))
        assertTrue("应该包含 src/utils 路径", resultMarkdown.contains("src/utils/Helper.kt"))
        assertFalse("不应该包含纯目录 src", resultMarkdown.contains("| src |"))
    }

    /**
     * 测试 AllClassesSearch 功能
     */
    fun testReplaceCodeByOffset() {
        // 1. 准备：添加测试类到虚拟项目
        myFixture.copyDirectoryToProject("myClasses", "src")


        val replaceCodeByOffset = replaceCodeByOffset(
            project, "ProjectFileUtils.kt", 0, 1, """
    fun newMethod() {
        println("This is new code")
    }
    """.trimIndent()
        )
        println(replaceCodeByOffset)
        val methodDetail2 = getFileFunInfo(project, "ProjectFileUtils.kt")
        println(methodDetail2)
    }

    /**
     * 测试替换方法
     */
    fun testReplaceMethodContent() {
        myFixture.copyDirectoryToProject("myClasses", "src")
        // 1. 获取 VirtualFile 集合 (最新推荐 API)
        val virtualFiles = FilenameIndex.getVirtualFilesByName(
            "ProjectFileUtils.kt",
            GlobalSearchScope.projectScope(project)
        )

        val methodDetail = getMethodDetail(project, "ProjectFileUtils.kt", "createFileOrDir")
        println(methodDetail)

        val replaceMethodContent = replaceMethodContent(
            project,
            "ProjectFileUtils.kt",
            "createFileOrDir",
            "fun abc() {\n" +
                    "        println(\"Hello, world!\") \n" +
                    "    }"
        )
        println(replaceMethodContent)
        val funInfo = getFileFunInfo(project, "ProjectFileUtils.kt")
        println(funInfo)
    }

    override fun getTestDataPath() = "src/test/testData"


}
