package com.example.course_schedule_for_chd_v002.ui.components.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.util.Constants

/**
 * 课程时段编辑表单
 * 用于添加或编辑单个课程时段
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseInstanceEditor(
    initialCourse: Course,
    suggestedTeachers: List<String>,
    suggestedLocations: List<String>,
    onSave: (Course) -> Unit,
    onDismiss: () -> Unit,
    isNew: Boolean = false,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 编辑状态
    var dayOfWeek by remember { mutableStateOf(initialCourse.dayOfWeek) }
    var startNode by remember { mutableIntStateOf(initialCourse.startNode) }
    var endNode by remember { mutableIntStateOf(initialCourse.endNode) }
    var teacher by remember { mutableStateOf(initialCourse.teacher) }
    var location by remember { mutableStateOf(initialCourse.location) }

    // 从 remark 解析位图
    val initialBitmap = remember(initialCourse.remark) {
        val regex = """weeksBitmap:([01]+)""".toRegex()
        regex.find(initialCourse.remark)?.groupValues?.get(1)
    }
    var currentBitmap by remember { mutableStateOf(initialBitmap ?: "") }
    var currentStartWeek by remember { mutableIntStateOf(initialCourse.startWeek) }
    var currentEndWeek by remember { mutableIntStateOf(initialCourse.endWeek) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // 顶部操作栏: 取消 - 标题 - 保存
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (isNew) "添加上课时段" else "编辑上课时段",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        val remark = if (currentBitmap.isNotEmpty()) {
                            "weeksBitmap:$currentBitmap"
                        } else {
                            ""
                        }
                        val course = initialCourse.copy(
                            dayOfWeek = dayOfWeek,
                            startNode = startNode,
                            endNode = endNode,
                            startWeek = if (currentStartWeek > 0) currentStartWeek else 1,
                            endWeek = if (currentEndWeek > 0) currentEndWeek else 16,
                            teacher = teacher,
                            location = location,
                            remark = remark
                        )
                        onSave(course)
                    }
                ) {
                    Text("保存")
                }
            }

            HorizontalDivider()

            Spacer(modifier = Modifier.height(12.dp))

            // 星期选择
            SectionLabel(text = "星期")
            DayOfWeekPicker(
                selected = dayOfWeek,
                onSelected = { dayOfWeek = it }
            )

            SectionDivider()

            // 节次范围选择
            SectionLabel(text = "节次")
            NodeRangePicker(
                startNode = startNode,
                endNode = endNode,
                maxNodes = Constants.Schedule.MAX_NODES_PER_DAY,
                onRangeChanged = { s, e ->
                    startNode = s
                    endNode = e
                }
            )

            SectionDivider()

            // 周次位图选择
            SectionLabel(text = "周次")
            WeekBitmapPicker(
                maxWeeks = Constants.Schedule.MAX_WEEKS,
                initialBitmap = initialBitmap,
                onBitmapChanged = { bitmap, sWeek, eWeek ->
                    currentBitmap = bitmap
                    currentStartWeek = sWeek
                    currentEndWeek = eWeek
                }
            )

            SectionDivider()

            // 教师输入
            SuggestionField(
                value = teacher,
                suggestions = suggestedTeachers,
                label = "教师",
                onValueChange = { teacher = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 教室输入
            SuggestionField(
                value = location,
                suggestions = suggestedLocations,
                label = "教室",
                onValueChange = { location = it }
            )

            // 底部留白
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 统一的 section 标签
 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

/**
 * 统一的 section 分隔线
 */
@Composable
private fun SectionDivider() {
    Spacer(modifier = Modifier.height(12.dp))
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
    Spacer(modifier = Modifier.height(12.dp))
}
