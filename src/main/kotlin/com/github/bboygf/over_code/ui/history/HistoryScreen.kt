package com.github.bboygf.over_code.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bboygf.over_code.ui.home.HomeViewModel
import com.github.bboygf.over_code.vo.SessionInfo
import java.text.SimpleDateFormat
import java.util.*

/**
 * 历史会话列表界面
 */
@Composable
fun HistoryScreen(
    viewModel: HomeViewModel,
    backgroundColor: Color,
    surfaceColor: Color,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    primaryColor: Color
) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(8.dp)
    ) {
        Text(
            text = "历史会话",
            fontSize = 16.sp,
            color = textPrimaryColor,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (viewModel.sessions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "暂无历史记录", color = textSecondaryColor)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(viewModel.sessions) { session ->
                    HistoryItem(
                        session = session,
                        isSelected = session.sessionId == viewModel.currentSessionId,
                        dateFormat = dateFormat,
                        surfaceColor = surfaceColor,
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor,
                        primaryColor = primaryColor,
                        onClick = {
                            viewModel.loadMessages(session.sessionId)
                            viewModel.showHistory = false
                        },
                        onDelete = {
                            viewModel.deleteSession(session.sessionId)
                        }
                    )
                }
            }
        }
    }
}


@Composable
fun HistoryItem(
    session: SessionInfo,
    isSelected: Boolean,
    dateFormat: SimpleDateFormat,
    surfaceColor: Color,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    primaryColor: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) surfaceColor.copy(alpha = 0.8f) else surfaceColor
        ),
        border = if (isSelected) CardDefaults.outlinedCardBorder()
            .copy(brush = androidx.compose.ui.graphics.SolidColor(primaryColor)) else null
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    fontSize = 14.sp,
                    color = textPrimaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateFormat.format(Date(session.updatedAt)),
                    fontSize = 12.sp,
                    color = textSecondaryColor
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除会话",
                    tint = textSecondaryColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
