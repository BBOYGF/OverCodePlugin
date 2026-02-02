package com.github.bboygf.over_code.utils

import com.github.bboygf.over_code.utils.ProjectFileUtils.findMethodsByName
import com.github.bboygf.over_code.utils.ProjectFileUtils.getFileFunInfo
import com.github.bboygf.over_code.utils.ProjectFileUtils.getMethodDetail
import com.github.bboygf.over_code.utils.ProjectFileUtils.replaceCodeByLine
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ProjectFileUtilsTest : BasePlatformTestCase() {
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

//        val methodDetail = getMethodDetail(project, "ProjectFileUtils.kt", "createFileOrDir")
//        println(methodDetail)

        val replaceMethodContent = replaceCodeByLine(
            project,
            "E:\\JavaProject\\OverCode\\src\\test\\testData\\myClasses\\ProjectFileUtils.kt",
            14,
            50,
            "fun abc() {\n" +
                    "        println(\"Hello, world!\") \n" +
                    "    }"
        )
        println(replaceMethodContent)
        val funInfo = getFileFunInfo(project, "E:\\JavaProject\\OverCode\\src\\test\\testData\\myClasses\\ProjectFileUtils.kt")
        println(funInfo)
    }

    /**
     * 根据方法名查询文件
     */
    fun testFindMethodsByName() {
        myFixture.copyDirectoryToProject("myClasses", "src")
        // 1. 获取 VirtualFile 集合 (最新推荐 API)
        val findMethodsByName = findMethodsByName(project, "findMethodsByName")
        println(findMethodsByName)
        val methodDetail = getMethodDetail(project, "ProjectFileUtils.kt", "findMethodsByName")
        println(methodDetail)
    }


    /**
     * 测试 替换代码功能
     */
    fun testReplaceCodeByOffset() {
        // 1. 准备：添加测试类到虚拟项目
        myFixture.copyDirectoryToProject("myClasses", "src")


        val replaceCodeByOffset = replaceCodeByLine(
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
     * 测试导出项目下所有文件功能
     */
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
     * 测试获取目录下内容功能
     */
    fun testListDirectoryContents() {
        // 1. 准备：在项目中创建测试目录结构 (addFileToProject 会返回 PsiFile)
        val file1 = myFixture.addFileToProject("testDir/subDir/file1.txt", "content1")
        myFixture.addFileToProject("testDir/file2.txt", "content2")
        myFixture.addFileToProject("testDir/fileA.txt", "contentA")

        // 关键点：使用 URL 而不是 path，这能包含协议头 (如 temp://)，彻底消除测试环境下的路径歧义
        val targetDirFile = file1.virtualFile.parent.parent
        val targetDirUrl = targetDirFile.url

        // 2. 测试正常目录
        val result = ProjectFileUtils.listDirectoryContents(targetDirUrl)

        println("Directory Content Markdown:\n$result")

        // 3. 验证断言
        assertTrue("标题应包含目录名", result.contains("### 目录内容: `testDir`"))
        assertTrue("应包含子目录", result.contains("| subDir | 📁 目录 |"))
        assertTrue("应包含文件2", result.contains("| file2.txt | 📄 文件 |"))

        // 验证排序：subDir 应该在文件之前
        val subDirIndex = result.indexOf("subDir")
        val file2Index = result.indexOf("file2.txt")
        assertTrue("目录应该在文件之前", subDirIndex < file2Index)

        // 4. 测试路径不存在的情况
        val errorResult = ProjectFileUtils.listDirectoryContents("/non/existent/path")
        assertTrue(errorResult.contains("### ❌ 失败：路径不存在"))

        // 5. 测试路径是文件的情况
        val filePath = file1.virtualFile.path
        val fileErrorResult = ProjectFileUtils.listDirectoryContents(filePath)
        assertTrue(fileErrorResult.contains("### ❌ 失败：该路径不是一个目录"))
    }
}
