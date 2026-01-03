package com.github.bboygf.over_code.startup

import com.github.bboygf.over_code.services.ChatDatabaseService
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Over Code 插件启动活动
 * 在项目打开时执行初始化操作
 */
class InitProjectActivity : ProjectActivity {

    private val logger = thisLogger()

    override suspend fun execute(project: Project) {
        logger.info("Over Coder 插件正在初始化...")
        
        try {
            // 在 IO 线程中初始化数据库服务
            withContext(Dispatchers.IO) {
                initializeDatabaseService(project)
            }
            // 检查模型配置状态
            checkModelConfiguration(project)
            logger.info("Over Code 插件初始化完成")
        } catch (e: Exception) {
            logger.error("Over Code 插件初始化失败", e)
        }
    }
    
    /**
     * 初始化数据库服务
     * 确保数据库连接正常，表结构已创建
     */
    private fun initializeDatabaseService(project: Project) {
        try {
            val dbService = ChatDatabaseService.getInstance(project)
            logger.info("数据库服务初始化成功")
            
            // 获取现有配置数量
            val configCount = dbService.getAllModelConfigs().size
            val sessionCount = dbService.loadSessions().size
            
            logger.info("已加载 $configCount 个模型配置，$sessionCount 个历史会话")
        } catch (e: Exception) {
            logger.error("数据库服务初始化失败", e)
            throw e
        }
    }
    
    /**
     * 检查模型配置状态
     * 如果没有激活的模型，提示用户配置
     */
    private fun checkModelConfiguration(project: Project) {
        try {
            val dbService = ChatDatabaseService.getInstance(project)
            val activeModel = dbService.getActiveModelConfig()
            
            if (activeModel == null) {
                logger.warn("未配置活动模型，请在设置中配置 LLM 模型")
            } else {
                logger.info("当前使用模型: ${activeModel.name} (${activeModel.provider})")
            }
        } catch (e: Exception) {
            logger.error("检查模型配置失败", e)
        }
    }
}