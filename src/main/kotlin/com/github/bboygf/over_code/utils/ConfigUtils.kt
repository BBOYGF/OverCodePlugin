package com.github.bboygf.over_code.utils

import kotlinx.coroutines.*
import java.io.File
import java.util.Properties

/**
 * 配置保存工具类
 */
class ConfigUtils(private val filePath: String) {
    private val props = Properties()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        load()
    }

    private fun load() {
        val file = File(filePath)
        if (file.exists()) {
            file.inputStream().use { props.load(it) }
        }
    }

    // 异步保存：由协程处理文件写入，不阻塞 UI
    private fun saveAsync() {
        scope.launch {
            File(filePath).outputStream().use {
                props.store(it, "UI State")
            }
        }
    }

    // --- 存入方法 ---
    fun set(key: String, value: Any?) {
        props.setProperty(key, value?.toString() ?: "")
        saveAsync()
    }

    // --- 获取方法 (提供默认值) ---
    fun getString(key: String, default: String = ""): String = props.getProperty(key, default)

    fun getInt(key: String, default: Int = 0): Int = props.getProperty(key)?.toIntOrNull() ?: default

    fun getBoolean(key: String, default: Boolean = false): Boolean = props.getProperty(key)?.toBoolean() ?: default

    fun getDouble(key: String, default: Double = 0.0): Double = props.getProperty(key)?.toDoubleOrNull() ?: default

    // 异步获取示例：模拟耗时操作或单纯为了语义一致
    suspend fun getStringAsync(key: String): String = withContext(Dispatchers.IO) {
        getString(key)
    }
}
