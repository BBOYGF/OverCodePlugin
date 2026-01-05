package com.github.bboygf.over_code.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bboygf.over_code.po.ChatMessage

/**
 * 消息部分定义
 */
sealed class MessagePart {
    data class Text(val content: String) : MessagePart()
    data class Code(val content: String, val language: String? = null) : MessagePart()
}

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
 * 欢迎屏幕
 */
@Composable
fun WelcomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Over Code Logo (使用简单的圆形图标代替)
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    color = Color(0xFF4A4A4A),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Q",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF888888)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Over Code 标题
        Text(
            text = "OverCode",
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF888888)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 欢迎文字
        Text(
            text = "打开关闭窗口",
            fontSize = 13.sp,
            color = Color(0xFF606060)
        )

        Spacer(modifier = Modifier.height(40.dp))

        // 底部介绍卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF3C3F41)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Logo
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = Color(0xFF4A9D5F),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Q",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 介绍文字
                Text(
                    text = "嗨！我是Over Code。我可以帮你一起编程，或者为你解答编程相关问题。",
                    fontSize = 13.sp,
                    color = Color(0xFFBBBBBB),
                    lineHeight = 20.sp
                )
            }
        }
    }
}

/**
 * 消息气泡
 */
@Composable
fun MessageBubble(
    message: ChatMessage,
    onCopyCode: (String) -> Unit = {},
    onInsertCode: (String) -> Unit = {}
) {
    // 消息内容
    Card(
        modifier = Modifier.fillMaxWidth().padding(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (message.isUser) Color(0xFF3C3F41) else Color(0xFF3C3F41)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            // 头部信息
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .padding(horizontal = 8.dp)
                    .background(
                        color = Color(0xFF3C3F41),
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                if (message.isUser) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "用户",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = "我", color = Color.White, fontSize = 12.sp)
                    }
                } else {
                    Text(
                        text = "OverCode:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4A9D5F),
                    )
                }
            }

            // 内容区域
            val parts = remember(message.content) { parseMessage(message.content) }

            Column(modifier = Modifier.padding(8.dp)) {
                parts.forEach { part ->
                    when (part) {
                        is MessagePart.Text -> {
                            SelectionContainer {
                                Text(
                                    text = part.content,
                                    fontSize = 13.sp,
                                    color = if (message.isUser) Color.White else Color(0xFFBBBBBB),
                                    lineHeight = 20.sp
                                )
                            }
                        }

                        is MessagePart.Code -> {
                            CodeBlock(
                                code = part.content,
                                language = part.language,
                                onCopy = { onCopyCode(part.content) },
                                onInsert = { onInsertCode(part.content) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 代码块组件
 */
@Composable
fun CodeBlock(
    code: String,
    language: String?,
    onCopy: () -> Unit,
    onInsert: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(Color(0xFF1E1E1E), RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!language.isNullOrBlank()) {
                Text(
                    text = language,
                    color = Color(0xFF4A9D5F),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            Row {
                IconButton(
                    onClick = onInsert,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "插入到光标位置",
                        tint = Color(0xFF808080),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DateRange,
                        contentDescription = "复制",
                        tint = Color(0xFF808080),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        SelectionContainer {
            Text(
                text = code.trim(),
                color = Color(0xFFD4D4D4),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}

/**
 * 底部输入区域
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomInputArea(
    inputText: String,
    onInputChange: (String) -> Unit,
    isLoading: Boolean,
    onSend: () -> Unit,
    activeModelName: String,
    chatMode: String,
    onModeChange: (String) -> Unit,
    modelConfigs: List<com.github.bboygf.over_code.services.ModelConfigInfo>,
    onModelSelect: (String) -> Unit,
    backgroundColor: Color,
    textColor: Color
) {
    var modeMenuExpanded by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }

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
                    .padding(8.dp)
            ) {
                // 顶部：添加上下文按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { /* 添加上下文逻辑 */ },
                        color = Color(0xFF2D2D2D),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add",
                                tint = Color(0xFF888888),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "添加上下文",
                                color = Color(0xFF888888),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // 中间：输入框
                TextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onKeyEvent {
                            if (it.key == Key.Enter) {
                                onSend()
                                return@onKeyEvent true
                            } else {
                                return@onKeyEvent false
                            }
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
                    }

                    // 发送按钮
                    IconButton(
                        onClick = onSend,
                        modifier = Modifier.size(32.dp),
                        enabled = inputText.isNotBlank() && !isLoading
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
                                tint = if (inputText.isNotBlank()) Color(0xFFBBBBBB) else Color(0xFF444444)
                            )
                        }
                    }
                }
            }
        }
    }
}
