package com.github.bboygf.over_code

import com.github.bboygf.over_code.utils.ProjectFileUtils
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AllClassesSearch
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PsiErrorElementUtil
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.j2k.convertToKotlin
import org.jetbrains.kotlin.idea.refactoring.isInterfaceClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.uast.kotlin.desc


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
    fun testGetAllClass() {
        // 1. 准备：添加测试类到虚拟项目
//        myFixture.addFileToProject("com/example/TestClass.kt", "package com.example\nclass TestClass{ fun myTest(){ println(\"ee\")}}")
        myFixture.copyDirectoryToProject("myClasses", "src")

        // 2. 执行逻辑
//        val foundClasses = mutableListOf<String?>()
//        val query = AllClassesSearch.search(GlobalSearchScope.projectScope(project), project)
//        query.forEach { psiClass ->
//            println("类名: " + psiClass.qualifiedName)
//            foundClasses.add(psiClass.qualifiedName)
//            val methods: Array<PsiMethod?> = psiClass.methods
//
//            methods.forEach { method ->
//                if (method != null && method.isConstructor) {
//                    return@forEach
//                }
//                println("是否是Class：" + method?.isInterfaceClass())
//                println("方法名：${method?.name}")
//                println("开始行：${method?.startOffset} 结束行：${method?.endOffset}")
//                println("参数：${method?.parameterList?.text}")
//                println("完整：${method?.text}")
//            }
//        }

        // 1. 获取 VirtualFile 集合 (最新推荐 API)
        val virtualFiles = FilenameIndex.getVirtualFilesByName(
            "ProjectFileUtils.kt",
            GlobalSearchScope.projectScope(project)
        )
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
                        println("方法名：${method?.name}")
                        println("备注：${(method?.navigationElement as KtNamedFunction).docComment?.text}")
                        println("开始行：${method?.startOffset} 结束行：${method?.endOffset}")
                        println("参数：${method?.parameterList?.text}")
                        println("完整：${method?.text}")
                    }
                }
            }
        }
    }


    override fun getTestDataPath() = "src/test/testData"

}
