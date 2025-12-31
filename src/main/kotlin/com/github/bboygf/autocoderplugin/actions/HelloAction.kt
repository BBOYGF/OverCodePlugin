package com.github.bboygf.autocoderplugin.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages

class HelloAction : AnAction() {
    // 1. 增加日志输出：获取 Logger 实例
    private val LOG = Logger.getInstance(HelloAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        // 当用户点击菜单项时执行此操作
        
        val projectName = e.project?.name ?: "Unknown Project"
        val message = "Hello from MyCoolPlugin in $projectName!"

        // 2. 日志输出
        LOG.info("用户点击了 HelloAction。项目名称: $projectName")

        // 3. 增加一个弹窗（作为按钮点击的反馈）
        Messages.showMessageDialog(
            e.project,
            message,
            "Greeting",
            Messages.getInformationIcon()
        )
    }
}
