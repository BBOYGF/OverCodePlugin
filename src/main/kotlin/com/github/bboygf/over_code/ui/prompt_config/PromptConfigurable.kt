package com.github.bboygf.over_code.ui.prompt_config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.bboygf.over_code.services.ChatDatabaseService
import com.github.bboygf.over_code.vo.PromptInfo
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import java.awt.Dimension
import javax.swing.JComponent

class PromptConfigurable(private val project: Project) : Configurable {
    
    private val dbService = ChatDatabaseService.getInstance(project)
    private var composePanel: ComposePanel? = null
    
    override fun getDisplayName(): String = "Over Code Prompt 模板"
    
    override fun createComponent(): JComponent {
        composePanel = ComposePanel().apply {
            preferredSize = Dimension(900, 700)
            setContent {
                MaterialTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color(0xFF1E1F22)
                    ) {
                        PromptConfigScreen(dbService)
                    }
                }
            }
        }
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
            Button(onClick = { viewModel.showAddDialog() }) {
                Text("添加模板")
            }
            
            Button(
                onClick = { viewModel.startEditPrompt() },
                enabled = viewModel.selectedIndex != null && 
                         !viewModel.prompts.getOrNull(viewModel.selectedIndex ?: -1)?.isBuiltIn!!
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
                modifier = Modifier.width(600.dp).heightIn(max = 500.dp).padding(8.dp),
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
