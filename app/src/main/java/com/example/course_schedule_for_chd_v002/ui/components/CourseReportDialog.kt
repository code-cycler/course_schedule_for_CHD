package com.example.course_schedule_for_chd_v002.ui.components

import android.content.Intent
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.ui.screens.schedule.ReportState
import com.example.course_schedule_for_chd_v002.util.HtmlCache
import com.example.course_schedule_for_chd_v002.util.ReportGenerator
import java.io.File

/**
 * 课程识别错误报告对话框
 *
 * 多步骤流程:
 * 1. INPUT - 用户输入描述、选择要包含的数据
 * 2. PREVIEW - 预览将生成的内容
 * 3. GENERATING - 生成中
 * 4. RESULT - 成功（分享按钮）/ 失败（重试）
 */
@Composable
fun CourseReportDialog(
    targetCourse: Course?,
    semester: String,
    courseCount: Int,
    reportState: ReportState,
    onGenerate: (description: String, targetCourse: Course?, includeCourses: Boolean, includeHtml: Boolean, includeLogs: Boolean) -> Unit,
    onShare: (File) -> Unit,
    onSave: (File) -> Unit,
    onDismiss: () -> Unit
) {
    var description by remember { mutableStateOf("") }
    var includeCourses by remember { mutableStateOf(true) }
    var includeHtml by remember { mutableStateOf(HtmlCache.hasHtml(semester)) }
    var includeLogs by remember { mutableStateOf(true) }
    var step by remember { mutableStateOf(0) } // 0=input, 1=preview

    val context = LocalContext.current

    // 当外部状态变为 Generating/Success/Error 时自动跳到结果步骤
    val currentStep = when (reportState) {
        is ReportState.Idle -> step
        is ReportState.Generating -> 2
        is ReportState.Success, is ReportState.Error -> 3
    }

    AlertDialog(
        onDismissRequest = {
            if (reportState !is ReportState.Generating) onDismiss()
        },
        icon = {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("课程识别错误报告")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (currentStep) {
                    0 -> {
                        // 自动脱敏提示
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "已自动脱敏：学号、手机号等敏感信息将被替换",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }

                        // 问题课程信息
                        targetCourse?.let { course ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "问题课程:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = course.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${course.teacher} | ${course.location} | ${course.getWeeksDisplayText()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        // 用户描述输入
                        OutlinedTextField(
                            value = description,
                            onValueChange = { if (it.length <= 500) description = it },
                            label = { Text("问题描述") },
                            placeholder = { Text("描述识别错误的详情，如：前后期周次未正确区分") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 5,
                            supportingText = { Text("${description.length}/500") }
                        )

                        Divider()

                        // 数据选择
                        Text(
                            text = "包含的数据:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = includeCourses,
                                onCheckedChange = { includeCourses = it }
                            )
                            Text("已解析课程 ($courseCount 门)", style = MaterialTheme.typography.bodySmall)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = includeHtml,
                                onCheckedChange = { includeHtml = it },
                                enabled = HtmlCache.hasHtml(semester)
                            )
                            Text(
                                text = if (HtmlCache.hasHtml(semester)) "原始 HTML (${HtmlCache.getHtmlSize(semester) / 1024}KB)"
                                else "原始 HTML (未缓存，需重新登录)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (HtmlCache.hasHtml(semester)) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = includeLogs,
                                onCheckedChange = { includeLogs = it }
                            )
                            Text("解析日志", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    1 -> {
                        // 预览
                        Text("报告将包含以下内容:", style = MaterialTheme.typography.titleSmall)

                        if (description.isNotBlank()) {
                            ReportPreviewRow("用户描述", description.take(100) + if (description.length > 100) "..." else "")
                        }

                        targetCourse?.let {
                            ReportPreviewRow("问题课程", it.name)
                        }

                        if (includeCourses) {
                            ReportPreviewRow("已解析课程", "$courseCount 门课程的完整 JSON 数据")
                        }

                        if (includeHtml && HtmlCache.hasHtml(semester)) {
                            val size = HtmlCache.getHtmlSize(semester)
                            ReportPreviewRow("原始 HTML", "${size / 1024}KB (脱敏后)")
                        } else {
                            ReportPreviewRow("原始 HTML", if (HtmlCache.hasHtml(semester)) "未选择" else "未缓存")
                        }

                        if (includeLogs) {
                            ReportPreviewRow("解析日志", "包含周次识别、课程解析相关日志")
                        }

                        val estimatedSize = estimateReportSize(courseCount, includeHtml, HtmlCache.hasHtml(semester) && includeHtml, includeLogs)
                        Divider()
                        Text(
                            text = "预估报告大小: ~${estimatedSize}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    2 -> {
                        // 生成中
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Text("正在生成报告...")
                        }
                    }

                    3 -> {
                        when (reportState) {
                            is ReportState.Success -> {
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
                                            text = "报告生成成功",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Divider(color = MaterialTheme.colorScheme.outlineVariant)
                                        ReportPreviewRow("文件", reportState.file.name)
                                        ReportPreviewRow("大小", formatFileSize(reportState.file.length()))
                                        Text(
                                            text = "点击下方\"分享报告\"按钮，选择分享方式发送给开发者",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            is ReportState.Error -> {
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
                                            text = "生成失败",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = reportState.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (currentStep) {
                0 -> {
                    Button(
                        onClick = { step = 1 },
                        enabled = description.isNotBlank() || targetCourse != null
                    ) {
                        Text("预览报告")
                    }
                }
                1 -> {
                    Button(
                        onClick = {
                            onGenerate(description, targetCourse, includeCourses, includeHtml, includeLogs)
                        }
                    ) {
                        Text("生成报告")
                    }
                }
                2 -> {
                    // 生成中不显示按钮
                }
                3 -> {
                    when (reportState) {
                        is ReportState.Success -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onSave(reportState.file) }
                                ) {
                                    Text("保存到...")
                                }
                                Button(
                                    onClick = { onShare(reportState.file) }
                                ) {
                                    Icon(
                                        Icons.Filled.Share,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("分享报告")
                                }
                            }
                        }
                        is ReportState.Error -> {
                            Button(onClick = {
                                step = 0
                                onGenerate(description, targetCourse, includeCourses, includeHtml, includeLogs)
                            }) {
                                Text("重试")
                            }
                        }
                        else -> {}
                    }
                }
            }
        },
        dismissButton = {
            if (reportState !is ReportState.Generating) {
                TextButton(onClick = onDismiss) {
                    Text(if (currentStep == 1) "返回" else "关闭")
                }
            }
        }
    )
}

@Composable
private fun ReportPreviewRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun estimateReportSize(courseCount: Int, includeHtml: Boolean, hasHtml: Boolean, includeLogs: Boolean): String {
    var size = 2 // 2KB base
    size += courseCount * 1 // ~1KB per course
    if (includeHtml && hasHtml) size += 100 // HTML excerpt up to 100KB
    if (includeLogs) size += 10 // logs ~10KB
    return if (size > 1024) "${size / 1024}MB" else "${size}KB"
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
