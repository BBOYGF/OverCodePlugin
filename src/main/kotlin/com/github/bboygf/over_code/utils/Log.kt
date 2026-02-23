package com.github.bboygf.over_code.utils

import com.intellij.openapi.project.ProjectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object Log {

    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd")
    val ioScope = CoroutineScope(Dispatchers.IO)

    /**
     * 打印普通信息
     */
    fun info(message: String?) {
        ioScope.launch {
            writeLog("INFO", message)
        }
    }

    /**
     * 打印警告信息
     */
    fun warn(message: String?) {
        ioScope.launch {
            writeLog("WARN", message)
        }
    }

    /**
     * 打印错误信息，支持传入异常类解析堆栈
     */
    fun error(message: String?, throwable: Throwable? = null) {
        ioScope.launch {
            val fullMessage = buildString {
                if (message != null) append(message)
                if (throwable != null) {
                    if (isNotEmpty()) append("\n")
                    append(throwable.stackTraceToString())
                }
            }
            writeLog("ERROR", fullMessage)
        }
    }

    private fun writeLog(level: String, message: String?) {
        if (message.isNullOrBlank()) return

        val openProjects = ProjectManager.getInstance().openProjects
        val project = openProjects.firstOrNull() ?: return
        val basePath = project.basePath ?: return

        val logDir = File(basePath, ".idea/log")
        if (!logDir.exists()) logDir.mkdirs()

        // --- 关键修改点：将日期格式化移入同步块 ---
        synchronized(this) {
            try {
                val currDate = Date()
                // 现在在锁内部，SimpleDateFormat 是安全的
                val dataStr = dateFormat.format(currDate)
                val timestamp = dateTimeFormat.format(currDate)

                val logFile = File(logDir, "$dataStr.log")
                val logEntry = "[$timestamp] [$level] $message\n"
                print(logEntry)
                logFile.appendText(logEntry)
            } catch (e: Exception) {
                println("Log write failed: ${e.message}")
            }
        }
    }
}