package com.example.course_schedule_for_chd_v002.ui.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.unit.Constraints
import androidx.core.content.FileProvider
import java.io.File

/**
 * [导出图片] 持有 [GraphicsLayer]，提供 captureToFile：把 layer 捕获为 Bitmap 写临时文件、返回 FileProvider Uri。
 * graphicsLayer 尚未绘制（首帧前）或捕获失败时返回 null。
 */
class ScheduleCaptureState internal constructor(val graphicsLayer: GraphicsLayer) {
    suspend fun captureToFile(context: Context, fileName: String = "schedule.png"): Uri? {
        return try {
            val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
            if (bitmap.width <= 0 || bitmap.height <= 0) return null
            ScheduleImageExporter.writeBitmap(context, bitmap, fileName)
        } catch (e: Exception) {
            android.util.Log.e("ScheduleCaptureState", "captureToFile failed", e)
            null
        }
    }
}

/** [导出图片] 在 Composable 侧持有捕获状态（rememberGraphicsLayer） */
@Composable
fun rememberScheduleCaptureState(): ScheduleCaptureState {
    val graphicsLayer = rememberGraphicsLayer()
    return remember(graphicsLayer) { ScheduleCaptureState(graphicsLayer) }
}

/**
 * [导出图片] 离屏捕获容器。
 *
 * 用自定义 [Layout] 给 content 传「固定宽度 + Infinity 高度」的约束测量，
 * 让 [ExportableScheduleGrid] 这类完整高度 Composable 被 measure 到固有高度（不被屏幕裁剪），
 * 再 record 到 [GraphicsLayer]；自身 `layout(0, 0)` 不占父布局空间，且 drawWithContent 只 record 不上屏（不可见）。
 *
 * 注意：record 在该 Composable 被绘制后才完成，调用方需在组合后等一帧再 captureToFile。
 *
 * @param widthPx 离屏渲染宽度（像素）
 */
@Composable
fun OffscreenScheduleCapture(
    captureState: ScheduleCaptureState,
    widthPx: Int,
    content: @Composable () -> Unit
) {
    val measurePolicy = MeasurePolicy { measurables, _ ->
        val placeable = measurables.first().measure(
            Constraints(
                minWidth = widthPx, maxWidth = widthPx,
                minHeight = 0, maxHeight = Constraints.Infinity
            )
        )
        layout(0, 0) {
            placeable.place(0, 0)
        }
    }
    Layout(
        content = {
            Box(
                Modifier.drawWithContent {
                    captureState.graphicsLayer.record { this@drawWithContent.drawContent() }
                    // 故意不 drawLayer / drawContent 到 canvas —— 内容不上屏，只 record 到 layer
                }
            ) { content() }
        },
        measurePolicy = measurePolicy
    )
}

/**
 * [导出图片] Bitmap → 临时文件 → FileProvider Uri；以及构造分享 Intent。
 * authority = `${packageName}.fileprovider`，与 AndroidManifest 一致。
 */
object ScheduleImageExporter {
    private const val AUTHORITY_SUFFIX = ".fileprovider"
    private const val SHARED_DIR = "shared_images"

    fun writeBitmap(context: Context, bitmap: Bitmap, fileName: String): Uri? {
        return try {
            val dir = File(context.cacheDir, SHARED_DIR).apply { mkdirs() }
            val file = File(dir, fileName)
            file.outputStream().use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
        } catch (e: Exception) {
            android.util.Log.e("ScheduleImageExporter", "writeBitmap failed", e)
            null
        }
    }

    fun buildShareIntent(uri: Uri, subject: String = "我的课表"): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
