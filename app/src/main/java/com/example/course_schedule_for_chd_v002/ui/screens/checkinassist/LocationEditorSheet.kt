package com.example.course_schedule_for_chd_v002.ui.screens.checkinassist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
 *
 * - 主要方式：点「采集当前位置」读取设备真实 GPS（WGS-84，零转换）。
 * - Fallback：手动输入经纬度（WGS-84）。
 * - 关联课程名/教室名可选。
 *
 * @param editing 编辑模式传入已有位置；新增传 null
 * @param onSave 保存回调（WGS-84 坐标的 CheckInLocation）
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationEditorSheet(
    editing: CheckInLocation?,
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
    var linkedRoom by remember { mutableStateOf(editing?.linkedRoomName ?: "") }

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

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = linkedCourse,
                    onValueChange = { linkedCourse = it },
                    label = { Text("关联课程（可选）") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = linkedRoom,
                    onValueChange = { linkedRoom = it },
                    label = { Text("关联教室（可选）") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
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
                                linkedRoomName = linkedRoom.trim().ifBlank { null },
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
