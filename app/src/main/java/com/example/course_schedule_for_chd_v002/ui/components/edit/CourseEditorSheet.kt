package com.example.course_schedule_for_chd_v002.ui.components.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.model.CourseType
import com.example.course_schedule_for_chd_v002.domain.model.DayOfWeek
import com.example.course_schedule_for_chd_v002.ui.screens.schedule.CourseConflictInfo
import com.example.course_schedule_for_chd_v002.ui.screens.schedule.CourseEditGroup
import com.example.course_schedule_for_chd_v002.util.Constants

/**
 * 课程编辑主容器
 * BottomSheet 显示同名课程的所有时段
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEditorSheet(
    editGroup: CourseEditGroup,
    suggestedTeachers: List<String>,
    suggestedLocations: List<String>,
    editConflicts: List<CourseConflictInfo>,
    onUpdateInstance: (Course) -> Unit,
    onDeleteInstance: (Long) -> Unit,
    onAddInstance: (Course) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 编辑器状态
    var editingInstance by remember { mutableStateOf<Course?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<Long?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.75f)
        ) {
            // 可滚动内容区域
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 64.dp)
            ) {
                // 头部: 课程名 + 学分 + 类型
                Text(
                    text = editGroup.courseName,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "${editGroup.credit}学分 | ${editGroup.courseType.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 时段列表
                editGroup.instances.forEach { instance ->
                    val hasConflict = editConflicts.any {
                        it.course1.id == instance.id || it.course2.id == instance.id
                    }
                    CourseInstanceCard(
                        instance = instance,
                        hasConflict = hasConflict,
                        onEdit = { editingInstance = instance },
                        onDelete = { showDeleteConfirm = instance.id }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 冲突提示
                if (editConflicts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "存在 ${editConflicts.size} 处时间冲突",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // 固定底部添加按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = {
                        editingInstance = Course(
                            id = 0,
                            name = editGroup.courseName,
                            teacher = "",
                            location = "",
                            dayOfWeek = DayOfWeek.MONDAY,
                            startWeek = 1,
                            endWeek = 16,
                            startNode = 1,
                            endNode = 2,
                            courseType = editGroup.courseType,
                            credit = editGroup.credit,
                            remark = "weeksBitmap:${"0".repeat(Constants.Schedule.MAX_WEEKS + 1).let { b -> StringBuilder(b).also { sb -> for (i in 1..16) sb.setCharAt(i, '1') }.toString() }}",
                            semester = editGroup.semester
                        )
                    }
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text("添加上课时段")
                }
            }
        }
    }

    // 编辑表单
    editingInstance?.let { course ->
        CourseInstanceEditor(
            initialCourse = course,
            suggestedTeachers = suggestedTeachers,
            suggestedLocations = suggestedLocations,
            isNew = course.id == 0L,
            onSave = { updated ->
                if (course.id == 0L) {
                    onAddInstance(updated)
                } else {
                    onUpdateInstance(updated)
                }
                editingInstance = null
            },
            onDismiss = { editingInstance = null }
        )
    }

    // 删除确认对话框
    showDeleteConfirm?.let { courseId ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("确认删除") },
            text = { Text("确定删除该上课时段吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteInstance(courseId)
                        showDeleteConfirm = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("取消")
                }
            }
        )
    }
}
