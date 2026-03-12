package com.github.bboygf.over_code.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.ide.BrowserUtil
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId

/**
 * 关于对话框 - 带章鱼设计
 */
@Composable
fun AboutDialog(
    onDismissRequest: () -> Unit,
    backgroundColor: Color,
    surfaceColor: Color,
    primaryColor: Color,
    textPrimaryColor: Color,
    textSecondaryColor: Color
) {
    // 动态获取插件版本号
    val pluginVersion = remember {
        PluginManagerCore.getPlugin(PluginId.getId("com.guofan.overcode"))?.version?.toString() ?: "未知"
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "关于 OverCode",
                    color = textPrimaryColor,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 章鱼 Logo 区域 - 带渐变光晕
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        modifier = Modifier
                            .size(150.dp)
                            .padding(8.dp),
                        painter = painterResource("/image/o.png"),
                        contentDescription = "Octopus Logo"
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 插件名称
                Text(
                    text = "OverCode",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor
                )

                Text(
                    text = "IntelliJ IDEA AI 编程助手",
                    fontSize = 12.sp,
                    color = textSecondaryColor,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // 版本号
                Surface(
                    color = primaryColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Version $pluginVersion",
                        fontSize = 11.sp,
                        color = primaryColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = textSecondaryColor.copy(alpha = 0.3f)
                )

                // 功能介绍
                Text(
                    text = "功能介绍",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimaryColor,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // 功能卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = backgroundColor
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        FeatureRow(
                            icon = Icons.Default.Star,
                            text = "AI 智能对话编程助手",
                            primaryColor = primaryColor,
                            textSecondaryColor = textSecondaryColor
                        )
                        FeatureRow(
                            icon = Icons.Default.Settings,
                            text = "支持多种大语言模型",
                            primaryColor = primaryColor,
                            textSecondaryColor = textSecondaryColor
                        )
                        FeatureRow(
                            icon = Icons.Default.Add,
                            text = "代码解释、生成、优化",
                            primaryColor = primaryColor,
                            textSecondaryColor = textSecondaryColor
                        )
                        FeatureRow(
                            icon = Icons.Default.Edit,
                            text = "项目上下文理解",
                            primaryColor = primaryColor,
                            textSecondaryColor = textSecondaryColor
                        )
                        FeatureRow(
                            icon = Icons.Default.Star,
                            text = "记忆库功能",
                            primaryColor = primaryColor,
                            textSecondaryColor = textSecondaryColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = textSecondaryColor.copy(alpha = 0.3f)
                )

                // 点赞区域
                Text(
                    text = "喜欢这个插件？",
                    fontSize = 13.sp,
                    color = textPrimaryColor,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "给我们点赞支持一下！",
                    fontSize = 11.sp,
                    color = textSecondaryColor,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // 链接按钮组
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    LinkButton(
                        text = "👍 点赞",
                        url = "https://plugins.jetbrains.com/plugin/30117-overcode",
                        primaryColor = primaryColor
                    )
                    LinkButton(
                        text = "🌐 网站",
                        url = "https://felinetech.cn/",
                        primaryColor = primaryColor
                    )
                    LinkButton(
                        text = "📧 邮箱",
                        url = "mailto:fanguo922@gmail.com",
                        primaryColor = primaryColor
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "感谢您的支持！",
                    fontSize = 11.sp,
                    color = primaryColor,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("知道了", color = primaryColor)
            }
        },
        containerColor = surfaceColor,
        titleContentColor = textPrimaryColor
    )
}

/**
 * 功能行组件
 */
@Composable
private fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    primaryColor: Color,
    textSecondaryColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = primaryColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = textSecondaryColor
        )
    }
}

/**
 * 链接按钮组件
 */
@Composable
private fun LinkButton(
    text: String,
    url: String,
    primaryColor: Color
) {
    Surface(
        modifier = Modifier
            .clickable { BrowserUtil.browse(url) },
        color = primaryColor.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = primaryColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
