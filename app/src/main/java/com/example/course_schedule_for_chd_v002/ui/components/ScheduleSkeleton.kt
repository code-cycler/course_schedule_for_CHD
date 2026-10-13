package com.example.course_schedule_for_chd_v002.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface

/**
 * [v124] 课表加载骨架屏（L2-build C 批第 ① 步）：手写 Compose 占位，不引第三方库。
 * 形状与真实课表网格对应（节次列 + 7 列表头 + 若干行格子），整体 alpha 呼吸动画。
 * 仅首载 isLoading 时显示，切周不显示（切周有缓存、瞬时完成）。
 */
@Composable
fun ScheduleSkeleton(modifier: Modifier = Modifier) {
    // 呼吸动画：0.35–0.7 循环，克制不刺眼
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )

    val blockShape = RoundedCornerShape(6.dp)
    val blockColor = MaterialTheme.colorScheme.outlineVariant

    Surface(modifier = modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 表头行：节次列占位 + 7 个星期占位
            SkeletonRow {
                Box(Modifier.size(width = 36.dp, height = 28.dp).alpha(alpha).background(blockColor, blockShape))
                Spacer(Modifier.width(4.dp))
                repeat(7) {
                    Box(Modifier.weight(1f).height(28.dp).alpha(alpha).background(blockColor, blockShape))
                    Spacer(Modifier.width(4.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            // 网格行：节次列 + 7 列格子，行高错落模拟课程块
            val rowHeights = listOf(56, 88, 64, 96, 72, 56, 88, 64)
            rowHeights.forEach { rowHeight ->
                SkeletonRow {
                    Box(Modifier.size(width = 36.dp, height = rowHeight.dp).alpha(alpha * 0.6f).background(blockColor, blockShape))
                    Spacer(Modifier.width(4.dp))
                    repeat(7) { col ->
                        // 部分格子留空，更接近真实课表的稀疏感
                        val hasBlock = (col * 7 + rowHeight) % 3 != 0
                        if (hasBlock) {
                            Box(Modifier.weight(1f).height(rowHeight.dp).alpha(alpha).background(blockColor, blockShape))
                        } else {
                            Box(Modifier.weight(1f).height(rowHeight.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun SkeletonRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        content = content
    )
}
