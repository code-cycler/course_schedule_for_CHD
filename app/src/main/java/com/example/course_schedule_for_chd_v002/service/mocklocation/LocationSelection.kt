package com.example.course_schedule_for_chd_v002.service.mocklocation

import com.example.course_schedule_for_chd_v002.domain.model.CheckInLocation
import com.example.course_schedule_for_chd_v002.domain.model.Course

/**
 * [v114] 触发签到辅助时，决定使用哪个已保存位置的规则。
 * [v126] 教室匹配升级为 tag 集合语义（checkin-assist-v2 B1 / ADR-0007）：
 * - 课程名命中：linkedCourseId == 课程名
 * - 教室命中：位置教室 tag 集合与「匹配教室集合」**任一交集即命中**
 * - 匹配教室集合 = 当前周活跃卡教室（activeRooms，由调用方按 isWeekInRange 过滤出活跃卡解析）；
 *   本周无活跃卡时回退该课全教室并集（fallbackRooms，S6 分周换教室课）
 *
 * 顺序：仅 1 条 → 直接用；否则按当前课程/教室匹配；命中多条且记忆在其中 → 用记忆；
 * 仍无法唯一确定 → 交由用户手动选择。
 *
 * 纯函数，便于单元测试。
 */
object LocationSelection {

    sealed class Result {
        /** 无可用位置 */
        object Empty : Result()
        /** 唯一确定使用某位置 */
        data class Auto(val location: CheckInLocation) : Result()
        /** 需用户手动选择，给出候选列表 */
        data class NeedManual(val candidates: List<CheckInLocation>) : Result()
    }

    /**
     * @param locations 全部已保存位置
     * @param currentCourse 当前正在上的课程（活跃卡；可空 = 无活跃卡或无法判定）
     * @param activeRooms 当前周活跃卡教室集合（空 = 无活跃卡）
     * @param fallbackRooms 该课全教室并集（activeRooms 为空时回退匹配用；课程未识别时为空）
     * @param rememberedLocationId 该课程上次手动选择的位置 ID（可空）
     */
    fun select(
        locations: List<CheckInLocation>,
        currentCourse: Course?,
        activeRooms: Set<String>,
        fallbackRooms: Set<String>,
        rememberedLocationId: Long?
    ): Result {
        if (locations.isEmpty()) return Result.Empty
        if (locations.size == 1) return Result.Auto(locations.first())

        val courseName = currentCourse?.name
        // 匹配教室集合：活跃卡优先，无活跃卡回退全并集
        val matchRooms = (if (activeRooms.isNotEmpty()) activeRooms else fallbackRooms)
            .map { normalizeRoom(it) }.filter { it.isNotEmpty() }.toSet()

        val matched = locations.filter { loc ->
            (courseName != null && loc.linkedCourseId == courseName) ||
                (matchRooms.isNotEmpty() && loc.linkedRoomNames.any { normalizeRoom(it) in matchRooms })
        }

        if (matched.size == 1) return Result.Auto(matched.first())
        if (matched.size > 1) {
            // 多条命中且记忆在其中 → 用记忆
            rememberedLocationId?.let { rid ->
                matched.firstOrNull { it.id == rid }?.let { return Result.Auto(it) }
            }
            return Result.NeedManual(matched)
        }

        // 未命中课程/教室：记忆兜底
        rememberedLocationId?.let { rid ->
            locations.firstOrNull { it.id == rid }?.let { return Result.Auto(it) }
        }

        return Result.NeedManual(locations)
    }

    /** [v126] 教室名归一化：去教务主教室标记 `*` 前缀与空白（课表 "*WM2301机房" 与手输 tag "WM2301机房" 等价匹配） */
    private fun normalizeRoom(room: String): String = room.trim().removePrefix("*").trim()
}
