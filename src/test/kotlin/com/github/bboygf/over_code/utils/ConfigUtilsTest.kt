package com.github.bboygf.over_code.utils


import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

class ConfigUtilsTest : BasePlatformTestCase() {

    private val testFilePath = "test_config.properties"
    private lateinit var config: ConfigUtils

    fun setup() {
        // 每个测试开始前删除旧文件，确保测试环境干净

    }

//     fun tearDown() {
//        // 测试结束后清理文件
//        File(testFilePath).delete()
//    }

    fun `test string saving and loading`() {
        File(testFilePath).delete()
        config = ConfigUtils(testFilePath)
        config.set("name", "KotlinUser")
        assertEquals("KotlinUser", config.getString("name"))
    }

    fun `test numeric and boolean types`() {
        config.set("age", 25)
        config.set("pi", 3.14)
        config.set("isAwesome", true)

        assertEquals(25, config.getInt("age"))
        assertEquals(3.14, config.getDouble("pi"), 0.001)
        assertTrue(config.getBoolean("isAwesome"))
    }

    fun `test default values when key missing`() {
        assertEquals("DefaultValue", config.getString("non_existent", "DefaultValue"))
        assertEquals(100, config.getInt("unknown_age", 100))
        assertEquals(0.5, config.getDouble("unknown_double", 0.5))
        assertFalse(config.getBoolean("unknown_bool", false))
    }


    fun `test file persistence`() {
        // 1. 设置值
        config.set("theme", "dark")

        // 2. 因为保存是异步的，稍微等待一下让协程完成文件写入
//        delay(100)

        // 3. 创建一个新的实例加载同一个文件，模拟应用重启
        val newConfig = ConfigUtils(testFilePath)

        // 4. 验证数据是否从文件恢复
        assertEquals("dark", newConfig.getString("theme"))
    }

    fun `test type safety on wrong format`() {
        // 存入一个不是数字的字符串
        config.set("error_num", "abc")

        // 获取 int 时应该返回默认值而不是崩溃
        assertEquals(99, config.getInt("error_num", 99))
    }

    fun `test async get`()  {
        config.set("async_key", "hello")

//        val value = config.getStringAsync("async_key")
//        assertEquals("hello", value)
    }
}
