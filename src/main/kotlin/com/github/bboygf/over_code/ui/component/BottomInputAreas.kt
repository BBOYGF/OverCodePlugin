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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bboygf.over_code.enums.ChatPattern
import com.github.bboygf.over_code.utils.Log
import com.github.bboygf.over_code.vo.MessagePart
import com.github.bboygf.over_code.vo.ModelConfigInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image
import java.awt.Cursor
import java.util.*


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
    chatMode: ChatPattern,
    onModeChange: (ChatPattern) -> Unit,
    modelConfigs: List<ModelConfigInfo>,
    onModelSelect: (String) -> Unit,
    backgroundColor: Color,
    textColor: Color,
    isChecked: Boolean,
    onLoadHistoryChange: (Boolean) -> Unit,
    selectedImageBase64List: MutableList<String>,
    onDeleteImage: (String) -> Unit = {},
    onPasteImage: () -> Boolean = { false },
    onSelectImage: () -> Unit = {},
    addProjectIndex: () -> Unit = {},
    isCancelling: Boolean = false,
    onCancel: () -> Unit = {},
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
                // 预览图片
                if (selectedImageBase64List.isNotEmpty()) {
                    Row {
                        selectedImageBase64List.forEach { base64Str ->
                            Box(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(60.dp)
                            ) {
                                val imageBitmap = remember(base64Str) {
                                    try {
                                        val bytes = Base64.getDecoder().decode(base64Str)
                                        Image.makeFromEncoded(bytes).toComposeImageBitmap()
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                                if (imageBitmap != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = imageBitmap,
                                        contentDescription = "Preview",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .border(1.dp, Color(0xFF444444), RoundedCornerShape(4.dp))
                                    )
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 8.dp, y = (-8).dp)
                                            .size(20.dp)
                                            .clickable { onDeleteImage(base64Str) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = Color.White,
                                            modifier = Modifier.padding(4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }


                // 顶部：添加上下文按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clickable { onSelectImage() }
                            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
                            .padding(horizontal = 8.dp, vertical = 4.dp),

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
                            if (it.key == Key.V && (it.isCtrlPressed || it.isMetaPressed) && it.type == KeyEventType.KeyDown) {
                                if (onPasteImage()) {
                                    return@onPreviewKeyEvent true
                                }
                            }

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
//                                    delay(400) // 很短的延迟，用户无感知
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
                                    text = chatMode.name,
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
                                    text = { Text(ChatPattern.计划模式.name, color = Color.White, fontSize = 12.sp) },
                                    onClick = {
                                        onModeChange(ChatPattern.计划模式)
                                        modeMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(ChatPattern.执行模式.name, color = Color.White, fontSize = 12.sp) },
                                    onClick = {
                                        onModeChange(ChatPattern.执行模式)
                                        modeMenuExpanded = false
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
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
                                                    color = if (config.isActive) Color(0xFFFFA726) else Color.White
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
                        Spacer(modifier = Modifier.width(8.dp))
                        // 是否携带历史消息
                        Checkbox(isChecked, onCheckedChange = {
                            onLoadHistoryChange(it)
                        },colors = CheckboxDefaults.colors(checkedColor = Color(0xFF5D44A1)))
                        Text(
                            text = "历史消息",
                            color = Color(0xFF888888),
                            fontSize = 12.sp
                        )

                    }

                    // 发送和停止按钮
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 圆形精度条（加载时显示）
                        if (isLoading) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable { onCancel() },
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color(0xFF4A9D5F),
                                    strokeWidth = 2.dp
                                )
                            }
                        } else {
                            // 发送按钮
                            IconButton(
                                onClick = onSend,
                                modifier = Modifier.size(32.dp),
                                enabled = !isLoading && (inputText.text.isNotBlank() || selectedImageBase64List != null)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = "发送",
                                    tint = if (inputText.text.isNotBlank() || selectedImageBase64List != null)
                                        Color(0xFFBBBBBB) else Color(0xFF444444)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}