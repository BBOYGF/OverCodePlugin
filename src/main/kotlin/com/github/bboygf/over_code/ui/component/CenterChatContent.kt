package com.github.bboygf.over_code.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                            tint = Color(0xFFdfe1e5),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = "我", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                } else {
                    Text(
                        text = "Over Code:",
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
                                    color = Color.White,
                                    lineHeight = 20.sp,
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
