package com.example.course_schedule_for_chd_v002.service.checkin.polling

import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.model.DayOfWeek
import java.time.LocalTime

/**
 * [v129] 课时有窗判定（B5 轮询通道闸门之一，L2-build B5 规格）：
 * 轮询仅在「当天有课的课前 N 分钟至课后 M 分钟」窗口内进行，无课时段不请求畅课服务器。
 *
 * 纯函数（无 Android 依赖），节次时间表与 [CalendarSyncService.CLASS_TIMES_WEISHUI] 同源
 * （渭水校区，第 1 节 08:30 起）；校验时注意两表同步维护。
 */
object ClassWindowScheduler {

    /** 渭水校区节次时间（LocalTime 版；字符串版见 CalendarSyncService.CLASS_TIMES_WEISHUI） */
    val WEISHUI_CLASS_TIMES: Map<Int, Pair<LocalTime, LocalTime>> = mapOf(
        1 to Pair(LocalTime.of(8, 30), LocalTime.of(9, 15)),
        2 to Pair(LocalTime.of(9, 20), LocalTime.of(10, 5)),
        3 to Pair(LocalTime.of(10, 25), LocalTime.of(11, 10)),
        4 to Pair(LocalTime.of(11, 15), LocalTime.of(12, 0)),
        5 to Pair(LocalTime.of(14, 0), LocalTime.of(14, 45)),
        6 to Pair(LocalTime.of(14, 50), LocalTime.of(15, 35)),
        7 to Pair(LocalTime.of(15, 55), LocalTime.of(16, 40)),
        8 to Pair(LocalTime.of(16, 45), LocalTime.of(17, 30)),
        9 to Pair(LocalTime.of(19, 0), LocalTime.of(19, 45)),
        10 to Pair(LocalTime.of(19, 50), LocalTime.of(20, 35)),
        11 to Pair(LocalTime.of(20, 40), LocalTime.of(21, 25))
    )

    const val DEFAULT_LEAD_MINUTES: Long = 10
    const val DEFAULT_LAG_MINUTES: Long = 5

    /**
     * now 是否落在任一「当日且本周」课程的窗口内。
     * 越界节次（classTimes 无对应 key，如课表解析出第 12 节）按 null 跳过该课程。
     */
    fun inAnyClassWindow(
        courses: List<Course>,
        currentWeek: Int,
        today: DayOfWeek,
        now: LocalTime,
        classTimes: Map<Int, Pair<LocalTime, LocalTime>> = WEISHUI_CLASS_TIMES,
        leadMinutes: Long = DEFAULT_LEAD_MINUTES,
        lagMinutes: Long = DEFAULT_LAG_MINUTES
    ): Boolean {
        return courses.any { course ->
            course.dayOfWeek == today &&
                course.isWeekInRange(currentWeek) &&
                windowFor(course, classTimes, leadMinutes, lagMinutes)?.contains(now) == true
        }
    }

    /** 单门课的轮询窗口（开始节起始时间减 lead 至结束节结束时间加 lag）；节次缺失返回 null */
    fun windowFor(
        course: Course,
        classTimes: Map<Int, Pair<LocalTime, LocalTime>> = WEISHUI_CLASS_TIMES,
        leadMinutes: Long = DEFAULT_LEAD_MINUTES,
        lagMinutes: Long = DEFAULT_LAG_MINUTES
    ): ClosedRange<LocalTime>? {
        val start = classTimes[course.startNode]?.first ?: return null
        val end = classTimes[course.endNode]?.second ?: return null
        // 跨午夜（如晚课 lag 超过 24:00）按 LocalTime 回绕，contains 仍按当日时刻比较
        return start.minusMinutes(leadMinutes)..end.plusMinutes(lagMinutes)
    }
}
