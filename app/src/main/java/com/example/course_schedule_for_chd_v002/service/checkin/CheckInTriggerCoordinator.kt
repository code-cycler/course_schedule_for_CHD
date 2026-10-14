package com.example.course_schedule_for_chd_v002.service.checkin

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.course_schedule_for_chd_v002.data.local.preferences.UserPreferences
import com.example.course_schedule_for_chd_v002.domain.model.Campus
import com.example.course_schedule_for_chd_v002.domain.model.CheckInLocation
import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.repository.ICheckInLocationRepository
import com.example.course_schedule_for_chd_v002.domain.repository.ICourseRepository
import com.example.course_schedule_for_chd_v002.service.calendar.CalendarSyncService
import com.example.course_schedule_for_chd_v002.service.mocklocation.LocationSelection
import com.example.course_schedule_for_chd_v002.service.mocklocation.MockLocationController
import com.example.course_schedule_for_chd_v002.util.AppLogger
import com.example.course_schedule_for_chd_v002.util.TimeUtils
import java.time.LocalTime

/**
 * [v114] 签到触发协调器（阶段二）
 *
 * 把「权限门禁 → 选位置 → 启动 Mock 定位 → 可选打开畅课」的触发逻辑从 ViewModel 抽出，
 * 供两条路径复用：
 * - UI 路径：CheckInAssistViewModel 的模拟触发按钮 / 手动选位置
 * - 后台路径：[CheckInNotificationListener] 监听真实畅课签到通知
 *
 * 不持有 UiState，返回 [TriggerResult] 由调用方映射到 UI 或通知。
 */
class CheckInTriggerCoordinator(
    private val app: Application,
    private val locationRepository: ICheckInLocationRepository,
    private val userPreferences: UserPreferences,
    private val courseRepository: ICourseRepository
) {

    sealed class TriggerResult {
        /** 已启动 Mock 定位 */
        data class MockStarted(
            val locationName: String,
            val durationMin: Int,
            val autoOpenedChaoqing: Boolean
        ) : TriggerResult()

        /** 没有保存的签到位置 */
        object NoLocation : TriggerResult()

        /** 前置权限缺失（模拟定位应用未设置 / 定位权限缺失） */
        data class PermissionMissing(val reason: String) : TriggerResult()

        /** 多个候选位置，需用户手动选择 */
        data class NeedsManualSelection(val candidates: List<CheckInLocation>) : TriggerResult()

        /** [v126] 会话级去重命中：同一签到（同课程/同 ID）近期已响应过，本次跳过 */
        object DebouncedBySession : TriggerResult()
    }

    /** 主入口：权限门禁 → 选位置 → 启动 Mock。供 UI 与后台服务复用（= 手动触发事件，不走去重）。 */
    suspend fun trigger(): TriggerResult =
        onEvent(CheckInTriggerEvent(source = CheckInTriggerSource.Manual))

    /**
     * [v126] 管线主入口（ADR-0006）：收事件 → courseHint 补全 → 会话级去重 → 响应动作序列。
     * Manual 源跳过去重（用户主动动作）；去重只针对 Notification / Polling / ReservedAuto。
     */
    suspend fun onEvent(rawEvent: CheckInTriggerEvent): TriggerResult {
        // ① courseHint 补全：事件未携带（通知不含课程名/未提取到）→ 用当前时段课推断（grill W01 Q2 兜底）
        val course = computeCurrentCourse()
        val effectiveHint = rawEvent.courseHint?.takeIf { it.isNotBlank() } ?: course?.name
        val event = if (effectiveHint != rawEvent.courseHint) rawEvent.copy(courseHint = effectiveHint) else rawEvent

        // ② 会话级去重（替换 v114 的 15s 纯时间防抖）
        if (event.source != CheckInTriggerSource.Manual && CheckInDeduper.shouldSuppress(event)) {
            return TriggerResult.DebouncedBySession
        }

        // ③ 权限门禁
        if (!MockLocationController.isMockLocationAppEnabled(app)) {
            return TriggerResult.PermissionMissing("请先在「开发者选项 → 模拟位置信息应用」中选择本应用")
        }
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return TriggerResult.PermissionMissing("请授予定位权限")
        }

        // ④ 选位置 + 启动（课程名匹配用补全后的 courseHint；教室匹配用活跃卡/全并集）
        val locations = locationRepository.getAll()
        val semester = courseRepository.getCurrentSemester()
        val fallbackRooms = if (effectiveHint != null && semester != null) {
            courseRepository.getCourseRoomUnion(semester)[effectiveHint] ?: emptySet()
        } else emptySet()
        val activeRooms = course?.rooms ?: emptySet()
        val remembered = effectiveHint?.let { userPreferences.getRememberedLocationId(it) }
        return when (val sel = LocationSelection.select(locations, effectiveHint, activeRooms, fallbackRooms, remembered)) {
            is LocationSelection.Result.Empty -> TriggerResult.NoLocation
            is LocationSelection.Result.Auto -> startMock(sel.location)
            is LocationSelection.Result.NeedManual -> TriggerResult.NeedsManualSelection(sel.candidates)
        }
    }

    /** 用户手动选定某位置后启动（记忆该选择 + 启动 Mock） */
    suspend fun startForLocation(location: CheckInLocation): TriggerResult {
        val course = computeCurrentCourse()
        course?.let { userPreferences.rememberLocation(it.name, location.id) }
        return startMock(location)
    }

    private suspend fun startMock(location: CheckInLocation): TriggerResult {
        val settings = userPreferences.getCheckInAssistSettingsOnce()
        MockLocationController.start(app, location, settings.mockDurationMinutes)
        val autoOpened = settings.autoOpenChaoqing && openChaoqing()
        AppLogger.i(
            TAG,
            "[v114] 触发 Mock: ${location.name}, ${settings.mockDurationMinutes}分钟, 自动开畅课=$autoOpened"
        )
        return TriggerResult.MockStarted(location.name, settings.mockDurationMinutes, autoOpened)
    }

    /** 启动畅课（TronClass）；返回是否成功拉起 */
    private fun openChaoqing(): Boolean {
        val intent = app.packageManager.getLaunchIntentForPackage(CheckInNotificationMatcher.TRONCLASS_PACKAGE)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { app.startActivity(intent) }.isSuccess
    }

    /** 计算当前正在上的课程（用于位置自动匹配） */
    private suspend fun computeCurrentCourse(): Course? {
        return try {
            val semester = courseRepository.getCurrentSemester() ?: return null
            val week = userPreferences.getCurrentWeekOnce()
            val todayDow = TimeUtils.getTodayDayOfWeek()
            val campus = Campus.fromName(userPreferences.getCampusOnce())
            val classTimes = CalendarSyncService.getClassTimes(campus)
            val now = LocalTime.now()

            courseRepository.getLocalSchedule(semester).firstOrNull { c ->
                c.dayOfWeek == todayDow &&
                    c.isWeekInRange(week) &&
                    isInClassNow(c.startNode, c.endNode, classTimes, now)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "[v114] 计算当前课程失败: ${e.message}")
            null
        }
    }

    private fun isInClassNow(
        startNode: Int,
        endNode: Int,
        classTimes: Map<Int, Pair<String, String>>,
        now: LocalTime
    ): Boolean {
        val startStr = classTimes[startNode]?.first ?: return false
        val endStr = classTimes[endNode]?.second ?: return false
        val s = LocalTime.parse(startStr)
        val e = LocalTime.parse(endStr)
        // 含下课前 10 分钟缓冲，仍视为在上课
        return !now.isBefore(s) && now.isBefore(e.plusMinutes(10))
    }

    companion object {
        private const val TAG = "CheckInTrigger"
    }
}
