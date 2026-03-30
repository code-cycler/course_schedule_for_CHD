package com.example.course_schedule_for_chd_v002.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.course_schedule_for_chd_v002.util.LogExporter
import com.example.course_schedule_for_chd_v002.util.LogExporter.CrashLogSummary
import java.io.File

/**
 * 崩溃报告弹窗
 *
 * 当检测到上次非正常退出时自动弹出，提供:
 * - 崩溃堆栈预览（可展开）
 * - 一键导出完整崩溃日志
 * - 分享功能
 */
@Composable
fun CrashReportDialog(
    crashSummary: CrashLogSummary?,
    isExporting: Boolean,
    result: LogExporter.ExportResult?,
    onExport: () -> Unit,
    onShare: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            // 导出中不允许关闭
            if (!isExporting) onDismiss()
        },
        icon = {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text("检测到上次异常退出")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 说明文字
                Text(
                    text = "应用上次未能正常关闭，已自动收集日志以帮助排查问题。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when {
                    // 导出中状态
                    isExporting -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "正在导出崩溃日志...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    // 导出完成状态
                    result != null -> {
                        if (result.success && result.file != null) {
                            CrashExportSuccessCard(result = result)
                        } else {
                            CrashExportErrorCard(error = result.error ?: "未知错误")
                        }
                    }

                    // 初始状态 - 显示崩溃预览
                    else -> {
                        // 会话信息
                        if (crashSummary != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "崩溃信息",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    crashSummary.crashTimestamp?.let {
                                        CrashInfoRow("时间", it)
                                    }
                                    CrashInfoRow("可用会话", "${crashSummary.availableSessions} 个")
                                    crashSummary.previousSessionLog?.let {
                                        CrashInfoRow(
                                            "上次日志",
                                            "${it.count { c -> c == '\n' }} 行"
                                        )
                                    }
                                }
                            }
                        }

                        // 崩溃堆栈预览（可展开）
                        crashSummary?.crashStackTrace?.let { stackTrace ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = if (expanded) "收起堆栈" else "查看崩溃堆栈",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )

                                    val displayText = if (expanded) {
                                        stackTrace.take(3000)
                                    } else {
                                        stackTrace.lines().take(8).joinToString("\n")
                                    }

                                    Text(
                                        text = displayText,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        maxLines = if (expanded) Int.MAX_VALUE else 8,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (!expanded && stackTrace.lines().size > 8) {
                                        Text(
                                            text = "... (共 ${stackTrace.lines().size} 行)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    TextButton(
                                        onClick = { expanded = !expanded }
                                    ) {
                                        Text(if (expanded) "收起" else "展开全部")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                isExporting -> {
                    // 导出中，不显示按钮
                }
                result != null && result.success && result.file != null -> {
                    // 成功后显示分享按钮
                    Button(
                        onClick = { onShare(result.file!!) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 16.dp, vertical = 8.dp
                        )
                    ) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("分享日志")
                    }
                }
                else -> {
                    // 初始或失败状态，显示导出按钮
                    Button(onClick = onExport) {
                        Text("导出崩溃日志")
                    }
                }
            }
        },
        dismissButton = {
            if (!isExporting) {
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        }
    )
}

/**
 * 崩溃导出成功卡片
 */
@Composable
private fun CrashExportSuccessCard(result: LogExporter.ExportResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "[OK] 导出成功",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant
            )

            CrashInfoRow("文件名", result.file?.name ?: "-")
            CrashInfoRow("大小", "${result.formattedSize} (${result.lineCount} 行)")
        }
    }
}

/**
 * 崩溃导出失败卡片
 */
@Composable
private fun CrashExportErrorCard(error: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = "[X] 导出失败",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

/**
 * 信息行
 */
@Composable
private fun CrashInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
