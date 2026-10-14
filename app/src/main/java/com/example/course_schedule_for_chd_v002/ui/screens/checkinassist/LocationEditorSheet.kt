package com.example.course_schedule_for_chd_v002.ui.screens.checkinassist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.course_schedule_for_chd_v002.domain.model.CheckInLocation
import com.example.course_schedule_for_chd_v002.service.location.CurrentLocationPicker
import com.example.course_schedule_for_chd_v002.util.AppLogger
import kotlinx.coroutines.launch

/**
 * [v114] 签到位置新增/编辑底部弹层
 * [v126] B4 重构（checkin-assist-v2）：
 * - 关联教室 = tag chip 增删（预填可删可加，手输自定义教室允许）。
 * [v134] 关联课程 tag 化（支持多门课共用同一位置）：
 * - 下拉选当前学期课程（v120/v121 多教室课全带出）或手输（跨学期课程名）；
 * - 每选一门课，该课**全部**教室**并入**教室 tag（多课取并集，可删可改；删课程不回收教室）；
 * - 触发时任一课程名命中或任一教室命中即选中该位置（LocationSelection v134 集合语义）。
 * - 关键操作 AppLogger 留痕（TAG=CheckInEditor，崩溃报告自动带出，定位「点了什么才崩」）。
 *
 * - 主要方式：点「采集当前位置」读取设备真实 GPS（WGS-84，零转换）。
 * - Fallback：手动输入经纬度（WGS-84）。
 *
 * @param editing 编辑模式传入已有位置；新增传 null
 * @param courseRoomMap 当前学期「课程名 → 教室并集」（下拉数据源 + 教室并入来源）
 * @param onSave 保存回调（WGS-84 坐标的 CheckInLocation）
 * @param onDismiss 关闭回调
 */
private const val TAG = "CheckInEditor"
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LocationEditorSheet(
    editing: CheckInLocation?,
    courseRoomMap: Map<String, Set<String>>,
    onSave: (CheckInLocation) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = remember { CurrentLocationPicker(context) }

    var name by remember { mutableStateOf(editing?.name ?: "") }
    var latText by remember { mutableStateOf(editing?.latitude?.let { "%.6f".format(it) } ?: "") }
    var lngText by remember { mutableStateOf(editing?.longitude?.let { "%.6f".format(it) } ?: "") }
    var note by remember { mutableStateOf(editing?.note ?: "") }
    // [v134] 关联课程 tag 集合（编辑模式带出；多门课共用同一位置）
    var linkedCourses by remember { mutableStateOf(editing?.linkedCourseIds ?: emptyList()) }
    var newCourseInput by remember { mutableStateOf("") }
    // [v126] 教室 tag 集合（编辑模式带出已有 tag）
    var roomTags by remember { mutableStateOf(editing?.linkedRoomNames ?: emptyList()) }
    var newRoomInput by remember { mutableStateOf("") }
    var courseMenuExpanded by remember { mutableStateOf(false) }

    // [v134] 面包屑：崩溃报告自动带出「打开编辑器时的初始状态」，定位问题入口
    LaunchedEffect(Unit) {
        AppLogger.i(
            TAG,
            if (editing == null) "打开新增位置编辑器"
            else "打开编辑『${editing.name}』（课程 ${editing.linkedCourseIds.size} 门 / 教室 ${editing.linkedRoomNames.size} 个）"
        )
    }

    // [v134] 加一门关联课程：去重追加 + 该课教室并入（并集）；下拉与手输共用
    val addCourse: (String) -> Unit = { input ->
        val c = input.trim()
        if (c.isNotEmpty() && linkedCourses.none { it.equals(c, ignoreCase = true) }) {
            linkedCourses = linkedCourses + c
            courseRoomMap[c]?.let { rooms ->
                roomTags = (roomTags + rooms).distinct().sorted()
            }
            AppLogger.i(TAG, "关联课程+『$c』（共 ${linkedCourses.size} 门，教室 tag 并集后 ${roomTags.size} 个）")
        }
        newCourseInput = ""
        courseMenuExpanded = false
    }

    var collecting by remember { mutableStateOf(false) }
    var collectedAccuracy by remember { mutableStateOf<Float?>(editing?.accuracyRadiusMeters) }
    var collectMessage by remember { mutableStateOf<String?>(null) }

    // [v134] 面包屑：关闭也留痕（区分「主动取消」与保存后关闭）
    val dismissWithLog: () -> Unit = {
        AppLogger.i(TAG, "关闭位置编辑（未点保存）")
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = dismissWithLog,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = if (editing == null) "新增签到位置" else "编辑签到位置",
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            // ===== 采集当前位置（主要方式）=====
            Text("采集当前位置（建议在签到现场点）", fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    if (collecting) return@Button
                    collecting = true
                    collectMessage = null
                    scope.launch {
                        when (val outcome = picker.collect()) {
                            is CurrentLocationPicker.Outcome.Success -> {
                                val p = outcome.position
                                latText = "%.6f".format(p.latitude)
                                lngText = "%.6f".format(p.longitude)
                                collectedAccuracy = p.accuracyMeters
                                collectMessage = "已采集" + (p.accuracyMeters?.let { "（精度 ±%.0fm）".format(it) } ?: "")
                            }
                            CurrentLocationPicker.Outcome.Timeout ->
                                collectMessage = "10 秒内未定位到，请到窗边或开阔处重试"
                            is CurrentLocationPicker.Outcome.Error ->
                                collectMessage = outcome.message
                        }
                        collecting = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !collecting
            ) {
                if (collecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("采集中…")
                } else {
                    Text("采集当前位置")
                }
            }
            collectMessage?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ===== 手动输入 fallback =====
            Spacer(Modifier.height(12.dp))
            Text("或手动输入经纬度（WGS-84）", fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = latText,
                    onValueChange = {
                        latText = it
                        collectedAccuracy = null
                    },
                    label = { Text("纬度") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = lngText,
                    onValueChange = {
                        lngText = it
                        collectedAccuracy = null
                    },
                    label = { Text("经度") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("位置名称（如：图书馆东侧）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // ===== [v134] 关联课程：tag 集合（下拉选课/手输追加，chip 可删）=====
            Spacer(Modifier.height(8.dp))
            Text("关联课程 tag（下拉选当前学期课程或手输，可关联多门）", fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            if (linkedCourses.isEmpty()) {
                Text(
                    "未关联课程（触发时按课程名/教室匹配）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    linkedCourses.forEach { course ->
                        AssistChip(
                            onClick = {
                                linkedCourses = linkedCourses - course
                                AppLogger.i(TAG, "关联课程-『$course』（剩 ${linkedCourses.size} 门）")
                            },
                            label = { Text(course) },
                            trailingIcon = {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "删除课程 $course",
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
                Text(
                    "点 tag 删除；删除课程不回收已带出的教室 tag",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.material3.ExposedDropdownMenuBox(
                    expanded = courseMenuExpanded && courseRoomMap.isNotEmpty(),
                    onExpandedChange = {
                        if (courseRoomMap.isNotEmpty()) {
                            courseMenuExpanded = it
                            if (it) AppLogger.i(TAG, "课程下拉展开（${courseRoomMap.size} 门当前学期课程可选）")
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = newCourseInput,
                        onValueChange = { newCourseInput = it },
                        label = { Text("添加课程（下拉选或手输）") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        // [v134] 官方 TrailingIcon：点击由 menuAnchor 统一 toggle（自制 IconButton 的
                        // onClick 会与 menuAnchor 双写 expanded，实测浮层闪现即收——「下拉按钮打不开」残余）
                        trailingIcon = {
                            androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = courseMenuExpanded
                            )
                        }
                    )
                    androidx.compose.material3.DropdownMenu(
                        expanded = courseMenuExpanded && courseRoomMap.isNotEmpty(),
                        onDismissRequest = { courseMenuExpanded = false },
                        modifier = Modifier.exposedDropdownSize()
                    ) {
                        courseRoomMap.keys.sorted().forEach { courseName ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(courseName) },
                                onClick = { addCourse(courseName) }
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = { addCourse(newCourseInput) },
                    enabled = newCourseInput.isNotBlank()
                ) { Text("添加") }
            }

            // ===== [v126] 关联教室：tag chip 增删（[v134] 选课改为并入式）=====
            Spacer(Modifier.height(8.dp))
            Text("关联教室 tag（每选一门课并入该课全部教室，可增删）", fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            if (roomTags.isEmpty()) {
                Text(
                    "未关联教室（触发时按课程名匹配）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    roomTags.forEach { room ->
                        AssistChip(
                            onClick = {
                                roomTags = roomTags - room
                                AppLogger.i(TAG, "教室 tag-『$room』（剩 ${roomTags.size} 个）")
                            },
                            label = { Text(room) },
                            trailingIcon = {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "删除教室 $room",
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
                Text(
                    "点 tag 删除",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newRoomInput,
                    onValueChange = { newRoomInput = it },
                    label = { Text("添加教室 tag") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = {
                        val r = newRoomInput.trim()
                        if (r.isNotEmpty() && roomTags.none { it.equals(r, ignoreCase = true) }) {
                            roomTags = roomTags + r
                            AppLogger.i(TAG, "教室 tag+『$r』（共 ${roomTags.size} 个）")
                        }
                        newRoomInput = ""
                    },
                    enabled = newRoomInput.isNotBlank()
                ) { Text("添加") }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("备注（可选）") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
            )

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = dismissWithLog,
                    modifier = Modifier.weight(1f)
                ) { Text("取消") }
                Button(
                    onClick = {
                        val lat = latText.trim().toDoubleOrNull() ?: return@Button
                        val lng = lngText.trim().toDoubleOrNull() ?: return@Button
                        val finalName = name.trim().ifBlank { "未命名位置" }
                        AppLogger.i(
                            TAG,
                            "保存位置『$finalName』：课程[${linkedCourses.joinToString("/")}] 教室[${roomTags.joinToString("/")}] ($lat,$lng)"
                        )
                        onSave(
                            CheckInLocation(
                                id = editing?.id ?: 0,
                                name = finalName,
                                latitude = lat,
                                longitude = lng,
                                accuracyRadiusMeters = collectedAccuracy,
                                note = note.trim().ifBlank { null },
                                linkedCourseIds = linkedCourses,
                                linkedRoomNames = roomTags,
                                createdAt = editing?.createdAt ?: System.currentTimeMillis()
                            )
                        )
                    },
                    enabled = name.isNotBlank() && latText.isNotBlank() && lngText.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text("保存") }
            }
        }
    }
}
