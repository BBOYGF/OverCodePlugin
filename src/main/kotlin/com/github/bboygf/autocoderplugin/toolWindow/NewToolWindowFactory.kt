package com.github.bboygf.autocoderplugin.toolWindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import javax.swing.JButton

class NewToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = NewToolWindow(toolWindow)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), "My New Window", false)
        toolWindow.contentManager.addContent(content)
    }

    class NewToolWindow(toolWindow: ToolWindow) {
        private val panel = JBPanel<JBPanel<*>>()

        init {
            panel.add(JBLabel("这是一个新的自定义窗口！"))

            val button = JButton("点击我打印日志")
            button.addActionListener {
                println("窗口内的按钮被点击了！") // 简单的控制台输出，或者使用 Logger
            }
            panel.add(button)
        }

        fun getContent() = panel
    }
}