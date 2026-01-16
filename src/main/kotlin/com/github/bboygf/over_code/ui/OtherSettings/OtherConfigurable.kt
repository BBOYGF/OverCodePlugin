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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "其他设置",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "设置代理",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                text = "代理地址：",
                color = Color.White
            )
            TextField(
                value = viewModel.ipAdded,
                onValueChange = {
                    viewModel.ipAdded = it
                },
                modifier = Modifier.width(150.dp)
                    .height(50.dp)
                    .background(Color.Gray, shape = RoundedCornerShape(5.dp))
                    .border(1.dp, Color.Gray, shape = RoundedCornerShape(5.dp)),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color.Gray,
                    unfocusedContainerColor = Color.Black.copy(0.8f),
                    cursorColor = Color.White,
                    focusedLabelColor = Color(0xFF3574F0),
                    selectionColors = TextSelectionColors(
                        handleColor = Color.White,
                        backgroundColor = Color(0xFF3574F0)
                    )
                )
            )
            Spacer(Modifier.width(8.dp))
            Text(text = "代理端口：", color = Color.White)
            TextField(
                value = viewModel.port, onValueChange = {
                    viewModel.port = it
                },
                modifier = Modifier.width(150.dp)
                    .height(50.dp)
                    .background(Color.Gray, shape = RoundedCornerShape(5.dp))
                    .border(1.dp, Color.Gray, shape = RoundedCornerShape(5.dp)),
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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = {
                    viewModel.updateConfig()
                },
                shape = RoundedCornerShape(5.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF3574F0))
            ) {
                Text("保存配置")
            }
        }

        if (viewModel.showMsg) {
            val dialogShape = RoundedCornerShape(12.dp)
            AlertDialog(
                modifier = Modifier.background(Color(0xFF2B2B2B))
                    .border(
                        width = 1.dp,
                        color = Color(0xFF3C3D3E),
                        shape = dialogShape // 必须与下面的 shape 一致
                    ),
                shape = dialogShape,
                containerColor = Color(0xFF18191A),
                onDismissRequest = { viewModel.onDismiss() },
                title = { Text("提示", color = Color.White) },
                text = {
                    Text("保存成功！", color = Color.White,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(bottom = 8.dp).width(150.dp))
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.onDismiss()
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
                        onClick = {
                            viewModel.onDismiss()
                        },
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
    }


}

