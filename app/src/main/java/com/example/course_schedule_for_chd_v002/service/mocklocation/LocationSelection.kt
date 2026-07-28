package com.example.course_schedule_for_chd_v002.service.mocklocation

import com.example.course_schedule_for_chd_v002.domain.model.CheckInLocation
import com.example.course_schedule_for_chd_v002.domain.model.Course

/**
 * [v114] 触发签到辅助时，决定使用哪个已保存位置的规则。
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
     * @param currentCourse 当前正在上的课程（可空）
     * @param rememberedLocationId 该课程/教室上次手动选择的位置 ID（可空）
     */
    fun select(
        locations: List<CheckInLocation>,
        currentCourse: Course?,
        rememberedLocationId: Long?
    ): Result {
        if (locations.isEmpty()) return Result.Empty
        if (locations.size == 1) return Result.Auto(locations.first())

        // 按当前课程名 / 教室匹配
        val courseName = currentCourse?.name
        val roomName = currentCourse?.location
        val matched = locations.filter { loc ->
            (courseName != null && loc.linkedCourseId == courseName) ||
                (roomName != null && loc.linkedRoomName == roomName)
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
}
