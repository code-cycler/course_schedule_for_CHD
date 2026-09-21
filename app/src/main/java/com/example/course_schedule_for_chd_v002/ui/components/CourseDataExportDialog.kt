package com.example.course_schedule_for_chd_v002.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.course_schedule_for_chd_v002.util.CourseDataExporter

/**
 * 课程数据导出对话框 [v120]
 *
 * 功能：
 * - 显示导出进度
 * - 显示导出结果（文件名、大小、课程数、是否含原始 HTML）
 * - 提供分享/保存按钮
 */
@Composable
fun CourseDataExportDialog(
    semester: String,
    isExporting: Boolean,
    result: CourseDataExporter.ExportResult?,
    onExport: () -> Unit,
    onShare: (java.io.File) -> Unit,
    onSave: (java.io.File) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            // 导出中不允许关闭
            if (!isExporting) onDismiss()
        },
        icon = {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("导出课程数据")
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
                                text = "正在打包课程数据...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    // 导出完成状态
                    result != null -> {
                        if (result.success && result.file != null) {
                            ResultCard(result)
                        } else {
                            ErrorCard(error = result.error ?: "未知错误")
                        }
                    }

                    // 初始状态
                    else -> {
                        Text(
                            text = "导出当前学期（$semester）的课程数据 ZIP，内含：\n" +
                                "· 识别后课程数据（JSON，可恢复）\n" +
                                "· 教务系统原始页面（HTML）\n" +
                                "· 字段与周次位图说明（README）\n\n" +
                                "导出后可通过分享发送给开发者或自行备份。原始 HTML 含个人信息，请妥善保管。",
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
                    TextButton(onClick = onExport) {
                        Text("导出")
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
private fun ResultCard(result: CourseDataExporter.ExportResult) {
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
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            FileInfoRow(label = "文件名", value = result.file?.name ?: "-")
            FileInfoRow(label = "学期", value = result.semester)
            FileInfoRow(
                label = "内容",
                value = "课程 ${result.courseCount} 门" + (if (result.hasRawHtml) "，含原始 HTML" else "，无原始 HTML 缓存")
            )
            FileInfoRow(label = "大小", value = result.formattedSize)

            val filePath = result.file?.absolutePath ?: "-"
            FileInfoRow(label = "路径", value = filePath, maxLines = 3)
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
        Text(
            text = "[X] 导出失败",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(12.dp)
        )

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
