package com.github.bboygf.over_code.ui.model_config

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.bboygf.over_code.services.ChatDatabaseService
import com.github.bboygf.over_code.ui.prompt_config.PromptConfigViewModel
import com.github.bboygf.over_code.vo.ModelConfigInfo
import com.github.bboygf.over_code.vo.PromptInfo
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.utils.addToStdlib.constant
import java.awt.Dimension
import javax.swing.JComponent

/**
 * 统一配置页面 - 包含模型配置和 Prompt 模板
 */
class ModelConfigurable(private val project: Project) : Configurable {

    private val dbService = ChatDatabaseService.getInstance(project)
    private var composePanel: ComposePanel? = null

    override fun getDisplayName(): String = "Over Code 配置"

    override fun createComponent(): JComponent {
        println("[ModelConfigurable] Creating component...")
        composePanel = ComposePanel().apply {
            preferredSize = Dimension(-1, -1)
            setContent {
                MaterialTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color(0xFF1E1F22)
                    ) {
                        UnifiedConfigScreen(dbService)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedConfigScreen(dbService: ChatDatabaseService) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("模型配置", "Prompt 模板")

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab 切换
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color(0xFF2B2B2B),
            contentColor = Color.White,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    // 使用 tabIndicatorOffset 让指示器自动移动到选中的 Tab 位置
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = Color.White // 将颜色修改为白色
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) },

                    )
            }
        }

        // Tab 内容
        when (selectedTabIndex) {
            0 -> ModelConfigScreen(dbService)
            1 -> PromptConfigScreen(dbService)
        }
    }
}

@Composable
fun PromptConfigScreen(dbService: ChatDatabaseService) {
    val viewModel = remember { PromptConfigViewModel(dbService) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Prompt 模板管理",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "管理 AI 操作使用的提示词模板，支持变量占位符：{{code}}, {{language}}, {{fileName}}, {{text}}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.showAddDialog() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3574F0))
            ) {
                Text("添加模板")
            }

            Button(
                onClick = { viewModel.startEditPrompt() },
                enabled = viewModel.selectedIndex != null &&
                        !viewModel.prompts.getOrNull(viewModel.selectedIndex ?: -1)?.isBuiltIn!!,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3574F0))
            ) {
                Text("编辑")
            }

            Button(
                onClick = { viewModel.deleteSelectedPrompt() },
                enabled = viewModel.selectedIndex != null &&
                        !viewModel.prompts.getOrNull(viewModel.selectedIndex ?: -1)?.isBuiltIn!!,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB00020))
            ) {
                Text("删除")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF3C3F41))
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(8.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF4E5254))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("模板名称", color = Color.White, modifier = Modifier.weight(1.5f))
                        Text("Key", color = Color.White, modifier = Modifier.weight(1f))
                        Text("描述", color = Color.White, modifier = Modifier.weight(2f))
                        Text("类型", color = Color.White, modifier = Modifier.weight(0.8f))
                    }
                }

                itemsIndexed(viewModel.prompts) { index, prompt ->
                    PromptRow(
                        prompt = prompt,
                        isSelected = viewModel.selectedIndex == index,
                        onClick = { viewModel.selectPrompt(index) }
                    )
                }
            }
        }
    }

    if (viewModel.showAddDialog) {
        PromptEditDialog(
            prompt = null,
            onDismiss = { viewModel.hideAddDialog() },
            onConfirm = { newPrompt ->
                viewModel.addPrompt(newPrompt)
            }
        )
    }

    viewModel.editingPrompt?.let { prompt ->
        PromptEditDialog(
            prompt = prompt,
            onDismiss = { viewModel.cancelEdit() },
            onConfirm = { updatedPrompt ->
                viewModel.updatePrompt(prompt.promptId, updatedPrompt)
            }
        )
    }
}

@Composable
fun PromptRow(
    prompt: PromptInfo,
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
        Text(prompt.name, color = Color.White, modifier = Modifier.weight(1.5f))
        Text(prompt.key, color = Color(0xFFBBBBBB), modifier = Modifier.weight(1f))
        Text(
            prompt.description,
            color = Color.Gray,
            modifier = Modifier.weight(2f),
            maxLines = 1
        )
        Row(modifier = Modifier.weight(0.8f), verticalAlignment = Alignment.CenterVertically) {
            if (prompt.isBuiltIn) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "内置",
                    tint = Color(0xFFFFB74D),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                if (prompt.isBuiltIn) "内置" else "自定义",
                color = if (prompt.isBuiltIn) Color(0xFFFFB74D) else Color(0xFF4CAF50)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptEditDialog(
    prompt: PromptInfo?,
    onDismiss: () -> Unit,
    onConfirm: (PromptInfo) -> Unit
) {
    var name by remember { mutableStateOf(prompt?.name ?: "") }
    var key by remember { mutableStateOf(prompt?.key ?: "") }
    var template by remember { mutableStateOf(prompt?.template ?: "") }
    var description by remember { mutableStateOf(prompt?.description ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (prompt == null) "添加 Prompt 模板" else "编辑 Prompt 模板") },
        text = {
            Column(
                modifier = Modifier.width(600.dp).heightIn(max = 500.dp).padding(8.dp)
                    .background(Color(0xFF2B2B2B)),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("模板名称") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Key (唯一标识)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = prompt == null
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it },
                    label = { Text("Prompt 模板") },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    maxLines = 10,
                    minLines = 8
                )

                Text(
                    "可用变量: {{code}}, {{language}}, {{fileName}}, {{text}}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && key.isNotBlank() && template.isNotBlank()) {
                        onConfirm(
                            PromptInfo(
                                promptId = prompt?.promptId ?: "",
                                name = name,
                                key = key,
                                template = template,
                                description = description,
                                isBuiltIn = false
                            )
                        )
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3574F0))
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

@Composable
fun ModelConfigScreen(dbService: ChatDatabaseService) {
    println("[ModelConfigScreen] Rendering...")

    // ViewModel
    val viewModel = remember { ModelConfigViewModel(dbService) }

    println("[ModelConfigScreen] Model configs count: ${viewModel.modelConfigs.size}")

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
            Button(
                onClick = { viewModel.showAddDialog() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3574F0))
            ) {
                Text("添加模型")
            }

            Button(
                onClick = { viewModel.startEditConfig() },
                enabled = viewModel.selectedIndex != null,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3574F0))
            ) {
                Text("编辑")
            }

            Button(
                onClick = { viewModel.deleteSelectedConfig() },
                enabled = viewModel.selectedIndex != null,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB00020))
            ) {
                Text("删除")
            }

            Button(
                onClick = { viewModel.setSelectedAsActive() },
                enabled = viewModel.selectedIndex != null,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3574F0))
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
                        Text("代理", color = Color.White, modifier = Modifier.weight(0.8f))
                    }
                }

                // 数据行
                itemsIndexed(viewModel.modelConfigs) { index, config ->
                    ModelConfigRow(
                        config = config,
                        isSelected = viewModel.selectedIndex == index,
                        onClick = { viewModel.selectConfig(index) }
                    )
                }
            }
        }
    }

    // 添加模型对话框
    if (viewModel.showAddDialog) {
        ModelConfigDialog(
            config = null,
            viewModel = viewModel,
            onDismiss = { viewModel.hideAddDialog() },
            onConfirm = { newConfig ->
                viewModel.addModelConfig(newConfig)
            }
        )
    }

    // 编辑模型对话框
    viewModel.editingConfig?.let { config ->
        ModelConfigDialog(
            config = config,
            viewModel = viewModel,
            onDismiss = { viewModel.cancelEdit() },
            onConfirm = { updatedConfig ->
                viewModel.updateModelConfig(config.modelId, updatedConfig)
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
        Text(
            if (config.useProxy) "✓" else "",
            color = Color(0xFF4CAF50),
            modifier = Modifier.weight(0.8f)
        )
    }
}

@Composable
fun ModelConfigDialog(
    config: ModelConfigInfo?,
    viewModel: ModelConfigViewModel,
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
    var useProxy by remember { mutableStateOf(config?.useProxy ?: false) }

    val providers = listOf("openai", "gemini", "ollama", "zhipu", "qwen", "deepseek", "moonshot", "custom")

    // 预设配置
    LaunchedEffect(provider) {
        if (config == null) {
            val (presetBaseUrl, presetModelName) = viewModel.getProviderPreset(provider)
            if (presetBaseUrl.isNotEmpty()) {
                baseUrl = presetBaseUrl
                modelName = presetModelName
                if (provider == "ollama") {
                    apiKey = ""
                }
            }
        }
    }
    val dialogShape = RoundedCornerShape(12.dp)
    AlertDialog(
        modifier = Modifier.padding(8.dp)
            .border(
                width = 1.dp,
                color = Color(0xFF3C3D3E),
                shape = dialogShape // 必须与下面的 shape 一致
            ),
        shape = dialogShape,
        containerColor = Color(0xFF18191A),
        onDismissRequest = onDismiss,
        title = { Text(if (config == null) "添加模型" else "编辑模型", color = Color.White) },
        text = {
            Column(
                modifier = Modifier.width(500.dp).padding(8.dp)
                    .background(Color(0xFF18191A)),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("配置名称") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White.copy(0.8f),
                        focusedBorderColor = Color.White,
                        focusedLabelColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.8f),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.8f),
                        selectionColors = TextSelectionColors(
                            handleColor = Color.Black,
                            backgroundColor = Color.White.copy(alpha = 0.4f)
                        ),
                        cursorColor = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // 提供商选择（使用简单按钮）
                Column {
                    Text("提供商:", style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = { showProviderMenu = !showProviderMenu },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3574F0),
                            contentColor = Color.White
                        ),
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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White.copy(0.8f),
                        focusedBorderColor = Color.White,
                        focusedLabelColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.8f),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.8f),
                        selectionColors = TextSelectionColors(
                            handleColor = Color.Black,
                            backgroundColor = Color.White.copy(alpha = 0.4f)
                        ),
                        cursorColor = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key (可选)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White.copy(0.8f),
                        focusedBorderColor = Color.White,
                        focusedLabelColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.8f),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.8f),
                        selectionColors = TextSelectionColors(
                            handleColor = Color.Black,
                            backgroundColor = Color.White.copy(alpha = 0.4f)
                        ),
                        cursorColor = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("模型名称") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White.copy(0.8f),
                        focusedBorderColor = Color.White,
                        focusedLabelColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.8f),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.8f),
                        selectionColors = TextSelectionColors(
                            handleColor = Color.Black,
                            backgroundColor = Color.White.copy(alpha = 0.4f)
                        ),
                        cursorColor = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isActive,
                        onCheckedChange = { isActive = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF3574F0),
                            uncheckedColor = Color.White.copy(alpha = 0.8f)
                        )
                    )
                    Text("设为当前使用的模型", color = Color.White)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = useProxy,
                        onCheckedChange = { useProxy = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF3574F0),
                            uncheckedColor = Color.White.copy(alpha = 0.8f)
                        )
                    )
                    Text("使用代理", color = Color.White)
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
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3574F0),
                    contentColor = Color.White
                ),
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
                                isActive = isActive,
                                useProxy = useProxy
                            )
                        )
                    }
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB00020),
                    contentColor = Color.White
                ),
            ) {
                Text("取消")
            }
        }
    )
}
