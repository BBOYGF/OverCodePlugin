package com.github.bboygf.over_code.ui.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bboygf.over_code.vo.MemoryVo

/**
 * 记忆库管理界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryPanel(
    memories: List<MemoryVo>,
    onAddMemory: (MemoryVo) -> Unit,
    onUpdateMemory: (String, MemoryVo) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onGenerateMemory: () -> Unit,
    isGenerating: Boolean,
    backgroundColor: Color,
    surfaceColor: Color,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    primaryColor: Color
) {
    var selectedMemoryId by remember { mutableStateOf<String?>(null) }
    var showAddMemoryDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(12.dp)
    ) {
        // 标题栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "记忆库",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimaryColor
            )

            Row {
                // AI生成记忆按钮
                IconButton(
                    onClick = onGenerateMemory,
                    enabled = !isGenerating
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = primaryColor,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "AI生成记忆",
                            tint = primaryColor
                        )
                    }
                }
                // 添加记忆按钮
                IconButton(onClick = { showAddMemoryDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加记忆",
                        tint = textSecondaryColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (memories.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = textSecondaryColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "暂无记忆",
                        color = textSecondaryColor,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "点击右上角按钮生成记忆或手动添加",
                        color = textSecondaryColor,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(memories) { memory ->
                    MemoryCard(
                        memory = memory,
                        isSelected = selectedMemoryId == memory.memoryId,
                        onSelect = {
                            selectedMemoryId = if (selectedMemoryId == memory.memoryId) null else memory.memoryId
                        },
                        onDeleteMemory = { onDeleteMemory(memory.memoryId) },
                        onUpdateMemory = { id, updatedMemory -> onUpdateMemory(id, updatedMemory) },
                        surfaceColor = surfaceColor,
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor,
                        primaryColor = primaryColor
                    )
                }
            }
        }
    }

    // 添加记忆对话框
    if (showAddMemoryDialog) {
        AddMemoryDialog(
            onDismiss = { showAddMemoryDialog = false },
            onConfirm = { summary, content ->
                onAddMemory(MemoryVo(summary = summary, content = content))
                showAddMemoryDialog = false
            },
            surfaceColor = surfaceColor,
            textPrimaryColor = textPrimaryColor,
            textSecondaryColor = textSecondaryColor,
            primaryColor = primaryColor
        )
    }
}

/**
 * 记忆卡片
 */
@Composable
fun MemoryCard(
    memory: MemoryVo,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDeleteMemory: () -> Unit,
    onUpdateMemory: (String, MemoryVo) -> Unit,
    surfaceColor: Color,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    primaryColor: Color
) {
    var isEditing by remember { mutableStateOf(false) }
    var editSummary by remember { mutableStateOf(memory.summary) }
    var editContent by remember { mutableStateOf(memory.content) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = surfaceColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 头部
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = textSecondaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))

                    if (isEditing) {
                        OutlinedTextField(
                            value = editSummary,
                            onValueChange = { editSummary = it },
                            modifier = Modifier.weight(1f),
                            textStyle = LocalTextStyle.current.copy(color = textPrimaryColor),
                            singleLine = true,
                            label = { Text("摘要", color = textSecondaryColor) }
                        )
                    } else {
                        Text(
                            text = memory.summary,
                            color = textPrimaryColor,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row {
                    if (isEditing) {
                        val memoryId = memory.memoryId
                        val updatedMemory = memory.copy(summary = editSummary, content = editContent)
                        IconButton(onClick = {
                            onUpdateMemory(memoryId, updatedMemory)
                            isEditing = false
                        }) {
                            Icon(Icons.Default.Check, "保存", tint = primaryColor)
                        }
                        IconButton(onClick = {
                            editSummary = memory.summary
                            editContent = memory.content
                            isEditing = false
                        }) {
                            Icon(Icons.Default.Close, "取消", tint = textSecondaryColor)
                        }
                    } else {
                        IconButton(onClick = { isEditing = true }) {
                            Icon(Icons.Default.Edit, "编辑", tint = textSecondaryColor, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Default.Delete,
                                "删除",
                                tint = textSecondaryColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // 详情内容（展开时显示）
            if (isSelected) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = textSecondaryColor.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(8.dp))

                if (isEditing) {
                    OutlinedTextField(
                        value = editContent,
                        onValueChange = { editContent = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(color = textPrimaryColor),
                        minLines = 3,
                        maxLines = 10,
                        label = { Text("详情内容", color = textSecondaryColor) }
                    )
                } else {
                    Text(
                        text = memory.content,
                        color = textSecondaryColor,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 24.dp)
                    )
                }
            }
        }
    }

    // 删除确认对话框
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除", color = textPrimaryColor) },
            text = { Text("确定要删除这条记忆吗？", color = textSecondaryColor) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteMemory()
                    showDeleteConfirm = false
                }) {
                    Text("删除", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消", color = textSecondaryColor)
                }
            },
            containerColor = surfaceColor
        )
    }
}

/**
 * 添加记忆对话框
 */
@Composable
fun AddMemoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
    surfaceColor: Color,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    primaryColor: Color
) {
    var summary by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加记忆", color = textPrimaryColor) },
        text = {
            Column {
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = { Text("摘要", color = textSecondaryColor) },
                    textStyle = LocalTextStyle.current.copy(color = textPrimaryColor),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("详情内容", color = textSecondaryColor) },
                    textStyle = LocalTextStyle.current.copy(color = textPrimaryColor),
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(summary, content) },
                enabled = summary.isNotBlank() && content.isNotBlank()
            ) {
                Text(
                    "添加",
                    color = if (summary.isNotBlank() && content.isNotBlank()) primaryColor else textSecondaryColor
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = textSecondaryColor)
            }
        },
        containerColor = surfaceColor
    )
}