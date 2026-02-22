package com.github.bboygf.over_code.ui.prompt_config

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bboygf.over_code.services.ChatDatabaseService
import com.github.bboygf.over_code.vo.PromptInfo


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
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isSelected) {
                    expanded = !expanded
                } else {
                    onClick()
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF4A7BA7) else Color(0xFF2B2D30)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 头部标题行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 展开/收起图标
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "展开/收起",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))

                    // 模板名称
                    Text(
                        text = prompt.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(Modifier.width(8.dp))

                    // Key 标识
                    Text(
                        text = "[${prompt.key}]",
                        color = Color(0xFFBBBBBB),
                        fontSize = 12.sp
                    )
                }

                // 类型标签
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (prompt.isBuiltIn) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "内置",
                            tint = Color(0xFFFFB74D),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "内置",
                            color = Color(0xFFFFB74D),
                            fontSize = 12.sp
                        )
                    } else {
                        Text(
                            text = "自定义",
                            color = Color(0xFF4CAF50),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // 描述摘要（收起时显示）
            if (!expanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 44.dp, end = 12.dp, bottom = 12.dp)
                ) {
                    Text(
                        text = prompt.description.ifEmpty { "无描述" },
                        color = Color.Gray,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 展开后的详细内容
            if (expanded) {
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        // 详细内容区域
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                        ) {
                            // 描述
                            Text(
                                text = "描述:",
                                color = Color(0xFFFFA726),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = prompt.description.ifEmpty { "无描述" },
                                color = Color(0xFFBBBBBB),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                            )

                            // 模板内容
                            Text(
                                text = "模板内容:",
                                color = Color(0xFFFFA726),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1E1E1E), RoundedCornerShape(4.dp))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = prompt.template,
                                    color = Color(0xFFD4D4D4),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // 右侧收起区域
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(48.dp)
                                .background(Color.Black.copy(alpha = 0.1f))
                                .clickable { expanded = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "收起",
                                    tint = Color.Gray.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "收起",
                                    fontSize = 10.sp,
                                    color = Color.Gray.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
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

    val dialogShape = RoundedCornerShape(12.dp)
    AlertDialog(
        modifier = Modifier.background(Color(0xFF2B2B2B))
            .border(
                width = 1.dp,
                color = Color(0xFF3C3D3E),
                shape = dialogShape
            ),
        shape = dialogShape,
        containerColor = Color(0xFF18191A),
        onDismissRequest = onDismiss,
        title = { Text(if (prompt == null) "添加 Prompt 模板" else "编辑 Prompt 模板", color = Color.White) },
        text = {
            Column(
                modifier = Modifier.width(600.dp).heightIn(max = 600.dp).padding(8.dp)
                    .background(Color(0xFF18191A)),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 模板名称 - 使用类似代码块的样式
                Text(
                    text = "模板名称:",
                    color = Color(0xFFFFA726),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E1E), RoundedCornerShape(4.dp))
                        .padding(12.dp)
                ) {
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        placeholder = {
                            Text("输入模板名称", color = Color.Gray)
                        }
                    )
                }

                // Key - 使用类似代码块的样式
                Text(
                    text = "Key (唯一标识):",
                    color = Color(0xFFFFA726),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E1E), RoundedCornerShape(4.dp))
                        .padding(12.dp)
                ) {
                    TextField(
                        value = key,
                        onValueChange = { key = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = prompt == null,
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            disabledTextColor = Color.Gray,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            cursorColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        placeholder = {
                            Text("输入唯一标识Key", color = Color.Gray)
                        }
                    )
                }

                // 描述 - 使用大文本框
                Text(
                    text = "描述:",
                    color = Color(0xFFFFA726),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(Color(0xFF1E1E1E), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    TextField(
                        value = description,
                        onValueChange = { description = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        placeholder = {
                            Text("输入描述", color = Color.Gray)
                        }
                    )
                }

                // Prompt模板 - 使用更大的文本框
                Text(
                    text = "Prompt 模板:",
                    color = Color(0xFFFFA726),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color(0xFF1E1E1E), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    TextField(
                        value = template,
                        onValueChange = { template = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        placeholder = {
                            Text("输入Prompt模板内容", color = Color.Gray)
                        }
                    )
                }

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
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3574F0),
                    contentColor = Color.White
                ),
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
