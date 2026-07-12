package com.example.course_schedule_for_chd_v002.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.course_schedule_for_chd_v002.util.LogExporter
import java.io.File

/**
 * 日志导出对话框
 *
 * 功能：
 * - 显示导出进度
 * - 显示导出结果（文件名、大小、行数）
 * - 提供分享按钮
 */
@Composable
fun LogExportDialog(
    isExporting: Boolean,
    result: LogExporter.ExportResult?,
    onExport: () -> Unit,
    onShare: (File) -> Unit,
    onSave: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = {
            // 导出中不允许关闭
            if (!isExporting) onDismiss()
        },
        icon = {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("导出日志")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                                text = "正在导出日志...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    // 导出完成状态
                    result != null -> {
                        if (result.success && result.file != null) {
                            // 成功
                            ResultCard(
                                result = result
                            )
                        } else {
                            // 失败
                            ErrorCard(
                                error = result.error ?: "未知错误"
                            )
                        }
                    }

                    // 初始状态
                    else -> {
                        Text(
                            text = "点击下方按钮导出本次启动后的应用日志，导出后可通过分享功能发送给开发者。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                    // 成功后显示保存和分享按钮
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onSave(result.file!!) }) {
                            Text("保存到...")
                        }
                        TextButton(onClick = { onShare(result.file!!) }) {
                            Text("分享")
                        }
                    }
                }
                else -> {
                    // 初始或失败状态，显示导出按钮
                    TextButton(onClick = onExport) {
                        Text("导出日志")
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
 * 成功结果卡片
 */
@Composable
private fun ResultCard(result: LogExporter.ExportResult) {
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
            // 成功图标和文字
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "[OK] 导出成功",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // 文件信息
            FileInfoRow(
                label = "文件名",
                value = result.file?.name ?: "-"
            )

            FileInfoRow(
                label = "大小",
                value = "${result.formattedSize} (${result.lineCount} 行)"
            )

            // 文件路径
            val filePath = result.file?.absolutePath ?: "-"
            FileInfoRow(
                label = "路径",
                value = filePath,
                maxLines = 3
            )
        }
    }
}

/**
 * 文件信息行
 */
@Composable
private fun FileInfoRow(
    label: String,
    value: String,
    maxLines: Int = 1
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 错误卡片
 */
@Composable
private fun ErrorCard(error: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "[X] 导出失败",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        Text(
            text = error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp)
        )
    }
}
