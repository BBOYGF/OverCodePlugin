package com.github.bboygf.over_code.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bboygf.over_code.vo.MessagePart
import com.github.bboygf.over_code.vo.ModelConfigInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


/**
 * 解析 Markdown 代码块
 */
fun parseMessage(content: String): List<MessagePart> {
    val parts = mutableListOf<MessagePart>()
    val regex = """```(\w*)\n?([\s\S]*?)```""".toRegex()
    var lastIndex = 0

    regex.findAll(content).forEach { matchResult ->
        val textBefore = content.substring(lastIndex, matchResult.range.first)
        if (textBefore.isNotEmpty()) {
            parts.add(MessagePart.Text(textBefore))
        }
        val language = matchResult.groups[1]?.value?.takeIf { it.isNotEmpty() }
        val code = matchResult.groups[2]?.value ?: ""
        parts.add(MessagePart.Code(code, language))
        lastIndex = matchResult.range.last + 1
    }

    if (lastIndex < content.length) {
        parts.add(MessagePart.Text(content.substring(lastIndex)))
    }

    return if (parts.isEmpty() && content.isNotEmpty()) listOf(MessagePart.Text(content)) else parts
}

/**
 * 底部输入区域
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomInputArea(
    inputText: TextFieldValue,
    onInputChange: (TextFieldValue) -> Unit,
    isLoading: Boolean,
    onSend: () -> Unit,
    activeModelName: String,
    chatMode: String,
    onModeChange: (String) -> Unit,
    modelConfigs: List<ModelConfigInfo>,
    onModelSelect: (String) -> Unit,
    backgroundColor: Color,
    textColor: Color,
    isChecked: Boolean,
    onLoadHistoryChange: (Boolean) -> Unit,
) {
    var modeMenuExpanded by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor.copy(alpha = 0.5f))
            .padding(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E)
            ),
            border = BorderStroke(1.dp, Color(0xFF333333))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()

                    .border(
                        3.dp, if (focused) {
                            Color(0xff3574F0)
                        } else {
                            Color(0xff393B40)
                        }, RoundedCornerShape(12.dp)
                    )
                    .padding(8.dp)
            ) {
                // 顶部：添加上下文按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add",
                            tint = Color(0xFFBBBBBB),
                            modifier = Modifier.size(22.dp)
                                .background(color = Color(0xFF2D2D2D))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "添加上下文",
                            color = Color(0xFF888888),
                            fontSize = 12.sp
                        )
                    }
                }

                // 中间：输入框
                TextField(
                    value = inputText,
                    onValueChange = { newValue ->
                        // 这里直接更新状态
                        onInputChange(newValue)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent {
                            println("key:${it.key} type:${it.type} isShiftPressed:${it.isShiftPressed}")
                            if (it.key == Key.Enter && it.type == KeyEventType.KeyUp) {
                                if (it.isShiftPressed) {
                                    val currentText = inputText.text
                                    val currentSelection = inputText.selection
                                    val newText = StringBuilder(currentText)
                                        .insert(currentSelection.start, "\n")
                                        .toString()
                                    val newCursorPos = currentSelection.start + 1
                                    onInputChange(
                                        TextFieldValue(
                                            text = newText,
                                            selection = TextRange(newCursorPos)
                                        )
                                    )
                                    return@onPreviewKeyEvent true
                                } else {
                                    onSend()
                                    return@onPreviewKeyEvent true
                                }

                            } else {
                                return@onPreviewKeyEvent false
                            }
                        }
                        .onFocusChanged {
                            if (it.isFocused && !focused) { // 只有在从没焦点变为有焦点时触发
                                scope.launch {
                                    delay(50) // 很短的延迟，用户无感知
                                    onInputChange(inputText.copy(selection = TextRange(inputText.text.length)))
                                }
                            }
                            focused = it.isFocused
                        },
                    placeholder = {
                        Text(
                            text = "使用Over Code解放程序员！",
                            fontSize = 14.sp,
                            color = Color(0xFF666666)
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor
                    ),
                    maxLines = 10,
                    minLines = 1
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 底部：下拉列表和发送按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 模式切换
                        Box {
                            Row(
                                modifier = Modifier
                                    .clickable { modeMenuExpanded = true }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = chatMode,
                                    color = Color(0xFF888888),
                                    fontSize = 12.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Mode DropDown",
                                    tint = Color(0xFF888888),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = modeMenuExpanded,
                                onDismissRequest = { modeMenuExpanded = false },
                                modifier = Modifier.background(Color(0xFF2D2D2D))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("计划模式", color = Color.White, fontSize = 12.sp) },
                                    onClick = {
                                        onModeChange("计划模式")
                                        modeMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("执行模式", color = Color.White, fontSize = 12.sp) },
                                    onClick = {
                                        onModeChange("执行模式")
                                        modeMenuExpanded = false
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // 模型切换
                        Box {
                            Row(
                                modifier = Modifier
                                    .clickable { modelMenuExpanded = true }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = activeModelName,
                                    color = Color(0xFF888888),
                                    fontSize = 12.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Model DropDown",
                                    tint = Color(0xFF888888),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = modelMenuExpanded,
                                onDismissRequest = { modelMenuExpanded = false },
                                modifier = Modifier.background(Color(0xFF2D2D2D))
                            ) {
                                if (modelConfigs.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("无可用模型", color = Color.White) },
                                        onClick = { modelMenuExpanded = false }
                                    )
                                } else {
                                    modelConfigs.forEach { config ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    config.name,
                                                    color = if (config.isActive) Color(0xFF4A9D5F) else Color.White
                                                )
                                            },
                                            onClick = {
                                                onModelSelect(config.modelId)
                                                modelMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        // 是否携带历史消息
                        Box {
                            Row(
                                modifier = Modifier
                                    .clickable { }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(isChecked, onCheckedChange = {
                                    onLoadHistoryChange(it)
                                })
                                Text(
                                    text = "历史消息",
                                    color = Color(0xFF888888),
                                    fontSize = 12.sp
                                )
                            }

                        }

                    }

                    // 发送按钮
                    IconButton(
                        onClick = onSend,
                        modifier = Modifier.size(32.dp),
                        enabled = inputText.text.isNotBlank() && !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF4A9D5F)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Send",
                                tint = if (inputText.text.isNotBlank()) Color(0xFFBBBBBB) else Color(0xFF444444)
                            )
                        }
                    }
                }
            }
        }
    }
}
