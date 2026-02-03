package com.github.bboygf.over_code.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bboygf.over_code.enums.ChatRole
import com.github.bboygf.over_code.vo.ChatMessage
import com.github.bboygf.over_code.vo.MessagePart


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
                .size(200.dp)
                .background(
                    color = Color(0xFF4A4A4A),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(modifier = Modifier.width(200.dp).height(200.dp),painter = painterResource("/image/o.png"), contentDescription = "logo")
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
                            color = Color(0xFF5D44A1),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "O",
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
    // 是否是工具/函数调用角色
    val isTool = message.chatRole == ChatRole.工具
    // 控制展开状态，默认收起(false)
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isTool) Color(0xFF2B2D30) else Color(0xFF3C3F41)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            // 新增：思考内容显示区域
            if (message.thought != null) {
                var thoughtExpanded by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF2D2D2D)
                    ),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { thoughtExpanded = !thoughtExpanded }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (thoughtExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "展开/收起",
                                tint = Color(0xFFFFA726),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "思考",
                                tint = Color(0xFFFFA726),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "推理过程",
                                color = Color(0xFFFFA726),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (thoughtExpanded) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = message.thought,
                                        color = Color(0xFFAAAAAA),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 头部信息 - 如果是工具，点击整行可折叠
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(color = Color(0xFF3C3F41))
                    .clickable(enabled = isTool) { expanded = !expanded } // 仅工具角色可点击
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (message.chatRole) {
                            ChatRole.用户 -> {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "用户",
                                    tint = Color(0xFFdfe1e5),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(text = "我", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            ChatRole.工具 -> {
                                Icon(
                                    imageVector = Icons.Default.Build, // 工具图标
                                    contentDescription = "工具",
                                    tint = Color(0xFFFFA726),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Tool Execution",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFA726)
                                )
                            }
                            else -> {
                                Text(
                                    text = "Over Code:",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFA726),
                                )
                            }
                        }
                    }

                    // 如果是工具，显示折叠箭头
                    if (isTool) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Toggle",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 内容区域
            val contentBody = @Composable {
                val parts = remember(message.content) {
                    try {
                        parseMarkdown(message.content)
                    } catch (e: Exception) {
                        listOf(MessagePart.Text(AnnotatedString(message.content)))
                    }
                }

                Column(modifier = Modifier.padding(12.dp)) {
                    parts.forEach { part ->
                        when (part) {
                            is MessagePart.Header -> {
                                SelectionContainer {
                                    Text(
                                        text = part.content,
                                        style = TextStyle(
                                            fontSize = when (part.level) {
                                                1 -> 24.sp
                                                2 -> 20.sp
                                                else -> 18.sp
                                            },
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF4A9D5F)
                                        ),
                                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                    )
                                }
                            }
                            is MessagePart.ListItem -> {
                                Row(modifier = Modifier.padding(4.dp)) {
                                    SelectionContainer {
                                        Text(
                                            text = if (part.index != null) "${part.index}. " else "• ",
                                            color = Color(0xFFFFA726),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    SelectionContainer {
                                        Text(text = part.content, color = Color.White, lineHeight = 20.sp)
                                    }
                                }
                            }
                            is MessagePart.Text -> {
                                SelectionContainer {
                                    Text(
                                        text = part.content,
                                        color = Color.White,
                                        lineHeight = 22.sp,
                                        modifier = Modifier.padding(vertical = 4.dp)
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

            // 根据角色决定是否折叠
            if (isTool) {
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    // 使用 Row 包裹内容，右侧留出一个专门的折叠感应区
                    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        Box(modifier = Modifier.weight(1f)) {
                            contentBody()
                        }

                        // 右侧垂直折叠条：贯穿整个内容高度，点击即可收起
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(32.dp)
                                .background(Color.Black.copy(alpha = 0.05f))
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
                                // 添加一个垂直的文字提示，让用户知道这里可以点
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
            } else {
                contentBody()
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
 * 解析 Markdown 代码块
 */
fun parseMarkdown(text: String): List<MessagePart> {
    val parts = mutableListOf<MessagePart>()
    val lines = text.lines()
    var index = 0

    while (index < lines.size) {
        val line = lines[index]

        // 1. 处理代码块 (Code Block)
        if (line.trim().startsWith("```")) {
            val language = line.trim().removePrefix("```").trim()
            val codeContent = StringBuilder()
            index++
            while (index < lines.size && !lines[index].trim().startsWith("```")) {
                codeContent.append(lines[index]).append("\n")
                index++
            }
            parts.add(MessagePart.Code(codeContent.toString().trimEnd(), language))
            index++ // 跳过结束的 ```
            continue
        }

        // 2. 处理标题 (Headers: #, ##, ###)
        val headerMatch = Regex("^(#{1,6})\\s+(.*)$").find(line)
        if (headerMatch != null) {
            val level = headerMatch.groupValues[1].length
            val content = headerMatch.groupValues[2]
            parts.add(MessagePart.Header(content, level))
            index++
            continue
        }

        // 3. 处理有序列表 (Ordered List: 1. Item)
        val orderedListMatch = Regex("^(\\d+)\\.\\s+(.*)$").find(line)
        if (orderedListMatch != null) {
            val num = orderedListMatch.groupValues[1].toInt()
            val content = orderedListMatch.groupValues[2]
            parts.add(MessagePart.ListItem(parseInlineStyles(content), num))
            index++
            continue
        }

        // 4. 处理无序列表 (Unordered List: *, -, •)
        val unorderedListMatch = Regex("^[\\*\\-\\•]\\s+(.*)$").find(line)
        if (unorderedListMatch != null) {
            val content = unorderedListMatch.groupValues[1]
            parts.add(MessagePart.ListItem(parseInlineStyles(content), null))
            index++
            continue
        }

        // 5. 普通文本块
        if (line.isNotBlank()) {
            parts.add(MessagePart.Text(parseInlineStyles(line)))
        }
        index++
    }
    return parts
}

/**
 * 处理行内样式：加粗 **text** 和 行内代码 `code`
 */
fun parseInlineStyles(text: String): AnnotatedString {
    return buildAnnotatedString {
        val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
        val inlineCodeRegex = Regex("`(.*?)`")

        var currentIndex = 0

        // 合并所有匹配项并按起始位置排序
        val tokens = (boldRegex.findAll(text) + inlineCodeRegex.findAll(text))
            .sortedBy { it.range.first }
            .toList()

        for (token in tokens) {
            // 【核心修复】：如果当前匹配项的开始位置小于 currentIndex，
            // 说明它被之前的匹配项包含在内了（例如加粗里套了代码），直接跳过防止崩溃
            if (token.range.first < currentIndex) continue

            // 添加匹配项之前的普通文本
            if (token.range.first > currentIndex) {
                append(text.substring(currentIndex, token.range.first))
            }

            val isBold = token.value.startsWith("**")
            // 去掉标记符后的实际内容
            val content = token.groupValues[1]

            val start = length
            append(content)
            val end = length

            if (isBold) {
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            } else {
                addStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0xFF444444),
                        color = Color(0xFFF0F0F0)
                    ), start, end
                )
            }
            // 更新当前位置到匹配项的最后
            currentIndex = token.range.last + 1
        }

        // 添加最后剩余的文本
        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }
}