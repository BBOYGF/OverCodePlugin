package com.github.bboygf.over_code.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bboygf.over_code.po.ChatMessage

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
            text = "Over Code",
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
fun MessageBubble(message: ChatMessage) {
    // 消息内容
    Card(
        modifier = Modifier.fillMaxWidth().padding(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (message.isUser) Color(0xFF3C3F41) else Color(0xFF3C3F41)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            // 用户头像
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .padding(3.dp)
                    .background(
                        color = Color(0xFF3C3F41),
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                if (message.isUser) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "用户",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = "OverCode:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
            SelectionContainer {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(5.dp),
                    fontSize = 13.sp,
                    color = if (message.isUser) Color.White else Color(0xFFBBBBBB),
                    lineHeight = 20.sp
                )
            }
        }
    }
}

/**
 * 底部输入区域
 */
@Composable
fun BottomInputArea(
    inputText: String,
    onInputChange: (String) -> Unit,
    isLoading: Boolean,
    onSend: () -> Unit,
    backgroundColor: Color,
    textColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
    ) {
        Divider(color = Color(0xFF2B2B2B), thickness = 1.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // 添加按钮
            IconButton(
                onClick = { /* 添加附件 */ },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "添加",
                    tint = Color(0xFF808080)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 输入框
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f)
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
                        text = "添加上下文\n自主规则及编程\n智能性 Auto",
                        fontSize = 12.sp,
                        color = Color(0xFF606060),
                        lineHeight = 16.sp
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    focusedContainerColor = Color(0xFF2B2B2B),
                    unfocusedContainerColor = Color(0xFF2B2B2B),
                    focusedBorderColor = Color(0xFF4A4A4A),
                    unfocusedBorderColor = Color(0xFF3C3F41)
                ),
                shape = RoundedCornerShape(8.dp),
                minLines = 2,
                maxLines = 6
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 发送按钮
            IconButton(
                onClick = onSend,
                modifier = Modifier.size(36.dp),
                enabled = inputText.isNotBlank() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF4A9D5F)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "发送",
                        tint = if (inputText.isNotBlank()) Color(0xFF4A9D5F) else Color(0xFF606060)
                    )
                }
            }
        }
    }
}
