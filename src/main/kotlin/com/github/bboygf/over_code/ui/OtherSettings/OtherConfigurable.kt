package com.github.bboygf.over_code.ui.OtherSettings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bboygf.over_code.services.ChatDatabaseService
import com.github.bboygf.over_code.vo.PromptInfo


@Composable
fun OtherConfigurable(dbService: ChatDatabaseService) {
    val viewModel = remember { OtherConfigViewModel(dbService) }

    /**
     * 代理设置界面 UI 逻辑
     *
     * 功能说明：
     * 1. 提供 IP 地址和端口号的输入框。
     * 2. 通过 ViewModel 进行双向数据绑定。
     * 3. 自定义深色主题样式的输入框和弹窗。
     * 4. 包含保存配置的交互逻辑及保存成功的弹窗反馈。
     */
    Column(
        // --- 根布局配置 ---
        modifier = Modifier
            .fillMaxSize() // 占满父容器的所有可用空间
            .padding(16.dp) // 设置整体外边距为 16dp
    ) {
        // ----------------------------------------------------------------
        // 1. 标题区域
        // ----------------------------------------------------------------

        // 主标题："其他设置"
        Text(
            text = "其他设置",
            style = MaterialTheme.typography.headlineMedium, // 使用大号标题样式
            color = Color.White, // 白色字体
            modifier = Modifier.padding(bottom = 8.dp) // 底部留白
        )

        // 副标题/说明："设置代理"
        Text(
            text = "设置代理",
            style = MaterialTheme.typography.bodySmall, // 使用小号正文样式
            color = Color.Gray, // 灰色字体，表示次要信息
            modifier = Modifier.padding(bottom = 16.dp) // 底部留白，与输入框隔开
        )

        // ----------------------------------------------------------------
        // 2. 输入区域 (IP 和 端口)
        // ----------------------------------------------------------------

        // 使用 Row 水平排列 "标签 + 输入框 + 标签 + 输入框"
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically // 子元素垂直居中对齐
        ) {
            // --- IP 地址输入部分 ---
            Text(
                text = "代理地址：",
                color = Color.White
            )

            TextField(
                // [关键逻辑] 数据绑定：显示 ViewModel 中的 ipAdded 状态
                value = viewModel.ipAdded,
                // [关键逻辑] 事件处理：当输入改变时，更新 ViewModel 中的状态
                onValueChange = {
                    viewModel.ipAdded = it
                },
                modifier = Modifier
                    .width(150.dp) // 固定宽度
                    .height(50.dp) // 固定高度
                    .background(Color.Gray, shape = RoundedCornerShape(5.dp)) // 背景色和圆角
                    .border(1.dp, Color.Gray, shape = RoundedCornerShape(5.dp)), // 边框

                // [样式定制] 自定义输入框各状态下的颜色（适配深色背景）
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.Black,          // 获取焦点时文字颜色
                    unfocusedTextColor = Color.White,        // 未获焦点时文字颜色
                    focusedContainerColor = Color.Gray,      // 获取焦点时背景色
                    unfocusedContainerColor = Color.Black.copy(0.8f), // 未获焦点时背景色(带透明度)
                    cursorColor = Color.White,               // 光标颜色
                    focusedLabelColor = Color(0xFF3574F0),   // 获取焦点时标签颜色
                    selectionColors = TextSelectionColors(   // 文本选中时的颜色配置
                        handleColor = Color.White,           // 拖拽手柄颜色
                        backgroundColor = Color(0xFF3574F0)  // 选中文本背景色
                    )
                )
            )

            Spacer(Modifier.width(8.dp)) // IP框和端口标签之间的间距

            // --- 端口输入部分 ---
            Text(text = "代理端口：", color = Color.White)

            TextField(
                // [关键逻辑] 数据绑定：显示 ViewModel 中的 port 状态
                value = viewModel.port,
                // [关键逻辑] 事件处理：更新端口状态
                onValueChange = {
                    viewModel.port = it
                },
                modifier = Modifier
                    .width(150.dp)
                    .height(50.dp)
                    .background(Color.Gray, shape = RoundedCornerShape(5.dp))
                    .border(1.dp, Color.Gray, shape = RoundedCornerShape(5.dp)),

                // [样式定制] 同上，保持风格一致
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color.Gray,
                    unfocusedContainerColor = Color.Black.copy(0.8f),
                    cursorColor = Color.White,
                    selectionColors = TextSelectionColors(
                        handleColor = Color.White,
                        backgroundColor = Color(0xFF3574F0)
                    )
                )
            )
        }

        // ----------------------------------------------------------------
        // 3. 操作按钮区域
        // ----------------------------------------------------------------

        // 使用 Row 并设置 horizontalArrangement = Arrangement.End 让按钮靠右显示
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                // [关键逻辑] 点击事件：调用 ViewModel 的更新配置方法
                onClick = {
                    viewModel.updateConfig()
                },
                shape = RoundedCornerShape(5.dp), // 按钮圆角
                // 按钮背景色：使用特定的蓝色 (0xFF3574F0)
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF3574F0))
            ) {
                Text("保存配置")
            }
        }

        // ----------------------------------------------------------------
        // 4. 弹窗反馈区域
        // ----------------------------------------------------------------

        // [关键逻辑] 状态驱动 UI：只有当 showMsg 为 true 时才渲染对话框
        if (viewModel.showMsg) {
            val dialogShape = RoundedCornerShape(12.dp) // 提取变量以确保边框和容器形状一致

            AlertDialog(
                // 修改 Dialog 的外观以适配深色主题
                modifier = Modifier
                    .background(Color(0xFF2B2B2B))
                    .border(
                        width = 1.dp,
                        color = Color(0xFF3C3D3E), // 边框颜色
                        shape = dialogShape
                    ),
                shape = dialogShape,
                containerColor = Color(0xFF18191A), // 对话框内部背景色

                // [关键逻辑] 点击对话框外部或返回键时，调用 dismiss 关闭弹窗
                onDismissRequest = { viewModel.onDismiss() },

                title = { Text("提示", color = Color.White) },
                text = {
                    Text(
                        "保存成功！",
                        color = Color.White,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(bottom = 8.dp).width(150.dp)
                    )
                },

                // 确认按钮区域
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.onDismiss() // 点击确定关闭弹窗
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3574F0), // 蓝色背景
                            contentColor = Color.White          // 白色文字
                        ),
                    ) {
                        Text("确定")
                    }
                },

                // 取消/关闭按钮区域
                dismissButton = {
                    TextButton(
                        onClick = {
                            viewModel.onDismiss() // 点击取消关闭弹窗
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFB00020), // 红色背景，表示警示或取消
                            contentColor = Color.White
                        ),
                    ) {
                        Text("取消")
                    }
                }
            )
        }
    }

}

