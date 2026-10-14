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
import androidx.compose.material.icons.filled.ArrowDropDown
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
import kotlinx.coroutines.launch

/**
 * [v114] 签到位置新增/编辑底部弹层
 * [v126] B4 重构（checkin-assist-v2）：
 * - 关联课程 = 可编辑下拉（当前学期课程；从菜单选中时自动预填该课**全部**教室为 tag，v120/v121 多教室课全带出）；
 *   自由输入仍允许（历史学期关联原样保留显示编辑）。
 * - 关联教室 = tag chip 增删（预填可删可加，手输自定义教室允许）。
 *
 * - 主要方式：点「采集当前位置」读取设备真实 GPS（WGS-84，零转换）。
 * - Fallback：手动输入经纬度（WGS-84）。
 *
 * @param editing 编辑模式传入已有位置；新增传 null
 * @param courseRoomMap 当前学期「课程名 → 教室并集」（下拉数据源 + 预填来源）
 * @param onSave 保存回调（WGS-84 坐标的 CheckInLocation）
 * @param onDismiss 关闭回调
 */
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
    var linkedCourse by remember { mutableStateOf(editing?.linkedCourseId ?: "") }
    // [v126] 教室 tag 集合（编辑模式带出已有 tag）
    var roomTags by remember { mutableStateOf(editing?.linkedRoomNames ?: emptyList()) }
    var newRoomInput by remember { mutableStateOf("") }
    var courseMenuExpanded by remember { mutableStateOf(false) }

    var collecting by remember { mutableStateOf(false) }
    var collectedAccuracy by remember { mutableStateOf<Float?>(editing?.accuracyRadiusMeters) }
    var collectMessage by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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

            // ===== [v126] 关联课程：可编辑下拉（当前学期）=====
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.ExposedDropdownMenuBox(
                expanded = courseMenuExpanded && courseRoomMap.isNotEmpty(),
                onExpandedChange = { if (courseRoomMap.isNotEmpty()) courseMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = linkedCourse,
                    onValueChange = { linkedCourse = it },
                    label = { Text("关联课程（可选，下拉选当前学期课程）") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    trailingIcon = {
                        androidx.compose.material3.IconButton(onClick = { courseMenuExpanded = !courseMenuExpanded }) {
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = "选择课程")
                        }
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
                            onClick = {
                                linkedCourse = courseName
                                // 选课 → 预填该课全部教室为 tag（可删可改）
                                courseRoomMap[courseName]?.let { rooms ->
                                    roomTags = rooms.sorted()
                                }
                                courseMenuExpanded = false
                            }
                        )
                    }
                }
            }

            // ===== [v126] 关联教室：tag chip 增删 =====
            Spacer(Modifier.height(8.dp))
            Text("关联教室 tag（选课后自动带出该课全部教室，可增删）", fontWeight = FontWeight.Medium)
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
                            onClick = { roomTags = roomTags - room },
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
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) { Text("取消") }
                Button(
                    onClick = {
                        val lat = latText.trim().toDoubleOrNull() ?: return@Button
                        val lng = lngText.trim().toDoubleOrNull() ?: return@Button
                        onSave(
                            CheckInLocation(
                                id = editing?.id ?: 0,
                                name = name.trim().ifBlank { "未命名位置" },
                                latitude = lat,
                                longitude = lng,
                                accuracyRadiusMeters = collectedAccuracy,
                                note = note.trim().ifBlank { null },
                                linkedCourseId = linkedCourse.trim().ifBlank { null },
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
