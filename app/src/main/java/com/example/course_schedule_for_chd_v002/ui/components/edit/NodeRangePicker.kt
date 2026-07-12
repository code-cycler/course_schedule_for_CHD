package com.example.course_schedule_for_chd_v002.ui.components.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 节次范围选择器
 * 11 个 FilterChip，点击两次形成范围
 */
@Composable
fun NodeRangePicker(
    startNode: Int,
    endNode: Int,
    maxNodes: Int = 11,
    onRangeChanged: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectionStart by remember { mutableIntStateOf(startNode) }
    var selectionEnd by remember { mutableIntStateOf(endNode) }
    var isSelectingEnd by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = if (isSelectingEnd) "点击选择结束节次" else "当前: 第${selectionStart}-${selectionEnd}节",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            for (node in 1..maxNodes) {
                val isSelected = node in selectionStart..selectionEnd
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (!isSelectingEnd) {
                            selectionStart = node
                            selectionEnd = node
                            isSelectingEnd = true
                        } else {
                            if (node >= selectionStart) {
                                selectionEnd = node
                            } else {
                                selectionStart = node
                                selectionEnd = selectionStart.also { selectionStart = node }
                            }
                            isSelectingEnd = false
                            onRangeChanged(selectionStart, selectionEnd)
                        }
                    },
                    label = { Text("${node}节") },
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}
