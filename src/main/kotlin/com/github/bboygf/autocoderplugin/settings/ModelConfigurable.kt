package com.github.bboygf.autocoderplugin.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.bboygf.autocoderplugin.services.ChatDatabaseService
import com.github.bboygf.autocoderplugin.services.ModelConfigInfo
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import javax.swing.JComponent

/**
 * 模型配置页面 - Compose 版本
 */
class ModelConfigurable(private val project: Project) : Configurable {
    
    private val dbService = ChatDatabaseService.getInstance(project)
    private var composePanel: ComposePanel? = null
    
    override fun getDisplayName(): String = "Qoder 模型配置"
    
    override fun createComponent(): JComponent {
        println("[ModelConfigurable] Creating component...")
        composePanel = ComposePanel().apply {
            preferredSize = java.awt.Dimension(800, 600)
            setContent {
                MaterialTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color(0xFF2B2B2B)
                    ) {
                        ModelConfigScreen(dbService)
                    }
                }
            }
        }
        println("[ModelConfigurable] Component created successfully")
        return composePanel!!
    }
    
    override fun isModified(): Boolean = false
    
    override fun apply() {}
    
    override fun reset() {}
    
    override fun disposeUIResources() {
        composePanel = null
    }
}

@Composable
fun ModelConfigScreen(dbService: ChatDatabaseService) {
    println("[ModelConfigScreen] Rendering...")
    var modelConfigs by remember { mutableStateOf(dbService.getAllModelConfigs()) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingConfig by remember { mutableStateOf<ModelConfigInfo?>(null) }
    
    println("[ModelConfigScreen] Model configs count: ${modelConfigs.size}")
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 标题
        Text(
            text = "模型配置管理",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // 按钮栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { showAddDialog = true }) {
                Text("添加模型")
            }
            
            Button(
                onClick = {
                    selectedIndex?.let { index ->
                        if (index < modelConfigs.size) {
                            editingConfig = modelConfigs[index]
                        }
                    }
                },
                enabled = selectedIndex != null
            ) {
                Text("编辑")
            }
            
            Button(
                onClick = {
                    selectedIndex?.let { index ->
                        if (index < modelConfigs.size) {
                            dbService.deleteModelConfig(modelConfigs[index].modelId)
                            modelConfigs = dbService.getAllModelConfigs()
                            selectedIndex = null
                        }
                    }
                },
                enabled = selectedIndex != null,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB00020))
            ) {
                Text("删除")
            }
            
            Button(
                onClick = {
                    selectedIndex?.let { index ->
                        if (index < modelConfigs.size) {
                            dbService.setActiveModel(modelConfigs[index].modelId)
                            modelConfigs = dbService.getAllModelConfigs()
                        }
                    }
                },
                enabled = selectedIndex != null
            ) {
                Text("设为当前模型")
            }
        }
        
        // 模型列表
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF3C3F41))
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(8.dp)
            ) {
                // 表头
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF4E5254))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("名称", color = Color.White, modifier = Modifier.weight(1.5f))
                        Text("提供商", color = Color.White, modifier = Modifier.weight(1f))
                        Text("模型", color = Color.White, modifier = Modifier.weight(1.5f))
                        Text("Base URL", color = Color.White, modifier = Modifier.weight(2f))
                        Text("当前使用", color = Color.White, modifier = Modifier.weight(0.8f))
                    }
                }
                
                // 数据行
                itemsIndexed(modelConfigs) { index, config ->
                    ModelConfigRow(
                        config = config,
                        isSelected = selectedIndex == index,
                        onClick = { selectedIndex = index }
                    )
                }
            }
        }
    }
    
    // 添加模型对话框
    if (showAddDialog) {
        ModelConfigDialog(
            config = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { newConfig ->
                dbService.addModelConfig(newConfig)
                modelConfigs = dbService.getAllModelConfigs()
                showAddDialog = false
            }
        )
    }
    
    // 编辑模型对话框
    editingConfig?.let { config ->
        ModelConfigDialog(
            config = config,
            onDismiss = { editingConfig = null },
            onConfirm = { updatedConfig ->
                dbService.updateModelConfig(config.modelId, updatedConfig)
                modelConfigs = dbService.getAllModelConfigs()
                editingConfig = null
            }
        )
    }
}

@Composable
fun ModelConfigRow(
    config: ModelConfigInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) Color(0xFF4A7BA7) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(config.name, color = Color.White, modifier = Modifier.weight(1.5f))
        Text(config.provider, color = Color.White, modifier = Modifier.weight(1f))
        Text(config.modelName, color = Color.White, modifier = Modifier.weight(1.5f))
        Text(
            config.baseUrl,
            color = Color.Gray,
            modifier = Modifier.weight(2f),
            maxLines = 1
        )
        Text(
            if (config.isActive) "✓" else "",
            color = Color(0xFF4CAF50),
            modifier = Modifier.weight(0.8f)
        )
    }
}

@Composable
fun ModelConfigDialog(
    config: ModelConfigInfo?,
    onDismiss: () -> Unit,
    onConfirm: (ModelConfigInfo) -> Unit
) {
    var name by remember { mutableStateOf(config?.name ?: "") }
    var provider by remember { mutableStateOf(config?.provider ?: "openai") }
    var baseUrl by remember { mutableStateOf(config?.baseUrl ?: "https://api.openai.com/v1") }
    var apiKey by remember { mutableStateOf(config?.apiKey ?: "") }
    var modelName by remember { mutableStateOf(config?.modelName ?: "gpt-3.5-turbo") }
    var isActive by remember { mutableStateOf(config?.isActive ?: false) }
    var showProviderMenu by remember { mutableStateOf(false) }
    
    val providers = listOf("openai", "ollama", "zhipu", "qwen", "deepseek", "moonshot", "custom")
    
    // 预设配置
    LaunchedEffect(provider) {
        if (config == null) {
            when (provider) {
                "openai" -> {
                    baseUrl = "https://api.openai.com/v1"
                    modelName = "gpt-3.5-turbo"
                }
                "ollama" -> {
                    baseUrl = "http://localhost:11434"
                    modelName = "llama2"
                    apiKey = ""
                }
                "zhipu" -> {
                    baseUrl = "https://open.bigmodel.cn/api/paas/v4"
                    modelName = "glm-4"
                }
                "qwen" -> {
                    baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1"
                    modelName = "qwen-plus"
                }
                "deepseek" -> {
                    baseUrl = "https://api.deepseek.com/v1"
                    modelName = "deepseek-chat"
                }
                "moonshot" -> {
                    baseUrl = "https://api.moonshot.cn/v1"
                    modelName = "moonshot-v1-8k"
                }
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (config == null) "添加模型" else "编辑模型") },
        text = {
            Column(
                modifier = Modifier.width(500.dp).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("配置名称") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // 提供商选择（使用简单按钮）
                Column {
                    Text("提供商:", style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = { showProviderMenu = !showProviderMenu },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(provider)
                    }
                    
                    if (showProviderMenu) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column {
                                providers.forEach { item ->
                                    TextButton(
                                        onClick = {
                                            provider = item
                                            showProviderMenu = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(item)
                                    }
                                }
                            }
                        }
                    }
                }
                
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key (可选)") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("模型名称") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isActive,
                        onCheckedChange = { isActive = it }
                    )
                    Text("设为当前使用的模型")
                }
                
                Text(
                    "提示: Ollama 本地模型无需 API Key",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && modelName.isNotBlank()) {
                        onConfirm(
                            ModelConfigInfo(
                                modelId = config?.modelId ?: "",
                                name = name,
                                provider = provider,
                                baseUrl = baseUrl,
                                apiKey = apiKey,
                                modelName = modelName,
                                isActive = isActive
                            )
                        )
                    }
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
