package com.example.course_schedule_for_chd_v002.service.reminder

import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.model.DayOfWeek
import java.time.DayOfWeek as JavaDayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * 课程提醒工具类
 *
 * 提供课程时间计算、早八检测等工具方法
 */
object CourseReminderUtils {

    /**
     * 每节课的开始时间配置
     * 根据长安大学课程表时间安排
     */
    val CLASS_START_TIMES: Map<Int, LocalTime> = mapOf(
        1 to LocalTime.of(8, 0),    // 第1节 08:00
        2 to LocalTime.of(8, 55),   // 第2节 08:55
        3 to LocalTime.of(10, 0),   // 第3节 10:00
        4 to LocalTime.of(10, 55),  // 第4节 10:55
        5 to LocalTime.of(14, 0),   // 第5节 14:00
        6 to LocalTime.of(14, 55),  // 第6节 14:55
        7 to LocalTime.of(16, 0),   // 第7节 16:00
        8 to LocalTime.of(16, 55),  // 第8节 16:55
        9 to LocalTime.of(19, 0),   // 第9节 19:00
        10 to LocalTime.of(19, 55), // 第10节 19:55
        11 to LocalTime.of(20, 50)  // 第11节 20:50
    )

    /**
     * 每节课的结束时间配置
     */
    val CLASS_END_TIMES: Map<Int, LocalTime> = mapOf(
        1 to LocalTime.of(8, 45),   // 第1节 08:45
        2 to LocalTime.of(9, 40),   // 第2节 09:40
        3 to LocalTime.of(10, 45),  // 第3节 10:45
        4 to LocalTime.of(11, 40),  // 第4节 11:40
        5 to LocalTime.of(14, 45),  // 第5节 14:45
        6 to LocalTime.of(15, 40),  // 第6节 15:40
        7 to LocalTime.of(16, 45),  // 第7节 16:45
        8 to LocalTime.of(17, 40),  // 第8节 17:40
        9 to LocalTime.of(19, 45),  // 第9节 19:45
        10 to LocalTime.of(20, 40), // 第10节 20:40
        11 to LocalTime.of(21, 35)  // 第11节 21:35
    )

    /**
     * 早八课程定义：第1-2节
     */
    const val EARLY_MORNING_NODE_START = 1
    const val EARLY_MORNING_NODE_END = 2

    /**
     * 检查明天是否有早八课程
     *
     * @param courses 课程列表
     * @param currentWeek 当前教学周
     * @param today 今天的日期
     * @return 早八课程列表，如果没有返回空列表
     */
    fun checkEarlyMorningCourses(
        courses: List<Course>,
        currentWeek: Int,
        today: LocalDate = LocalDate.now()
    ): List<Course> {
        val tomorrow = today.plusDays(1)
        val tomorrowDayOfWeek = convertToAppDayOfWeek(tomorrow.dayOfWeek)

        // 计算明天的教学周
        // 如果今天是周日，明天是周一，教学周+1
        val tomorrowWeek = if (today.dayOfWeek == JavaDayOfWeek.SUNDAY) {
            currentWeek + 1
        } else {
            currentWeek
        }

        return courses.filter { course ->
            // 检查是否是明天的课
            course.dayOfWeek == tomorrowDayOfWeek &&
            // 检查周次是否匹配
            course.isWeekInRange(tomorrowWeek) &&
            // 检查是否是早八 (第1-2节)
            course.startNode <= EARLY_MORNING_NODE_END &&
            course.endNode >= EARLY_MORNING_NODE_START
        }
    }

    /**
     * 获取今天即将开始的课程
     *
     * @param courses 课程列表
     * @param currentWeek 当前教学周
     * @param reminderMinutes 提前多少分钟提醒
     * @param now 当前时间
     * @param today 今天的日期
     * @return 即将开始的课程列表
     */
    fun getUpcomingCourses(
        courses: List<Course>,
        currentWeek: Int,
        reminderMinutes: Int,
        now: LocalTime = LocalTime.now(),
        today: LocalDate = LocalDate.now()
    ): List<UpcomingCourse> {
        val todayDayOfWeek = convertToAppDayOfWeek(today.dayOfWeek)

        return courses
            .filter { course ->
                course.dayOfWeek == todayDayOfWeek &&
                course.isWeekInRange(currentWeek)
            }
            .mapNotNull { course ->
                val classStartTime = CLASS_START_TIMES[course.startNode] ?: return@mapNotNull null
                val reminderTime = classStartTime.minusMinutes(reminderMinutes.toLong())

                // 只有当提醒时间还没过时才返回
                if (now.isBefore(reminderTime)) {
                    UpcomingCourse(
                        course = course,
                        classStartTime = classStartTime,
                        reminderTime = reminderTime
                    )
                } else {
                    null
                }
            }
            .sortedBy { it.reminderTime }
    }

    /**
     * 获取课程的时间显示文本
     *
     * @param startNode 开始节次
     * @param endNode 结束节次
     * @return 如 "第1-2节 (08:00-09:40)"
     */
    fun getCourseTimeDisplay(startNode: Int, endNode: Int): String {
        val startTime = CLASS_START_TIMES[startNode] ?: return ""
        val endTime = CLASS_END_TIMES[endNode] ?: return ""
        return "第${startNode}-${endNode}节 (${startTime}-${endTime})"
    }

    /**
     * 获取课程简短时间显示
     *
     * @param startNode 开始节次
     * @return 如 "08:00"
     */
    fun getCourseStartTimeDisplay(startNode: Int): String {
        val startTime = CLASS_START_TIMES[startNode] ?: return ""
        return String.format("%02d:%02d", startTime.hour, startTime.minute)
    }

    /**
     * 转换 Java DayOfWeek 到应用内的 DayOfWeek
     */
    fun convertToAppDayOfWeek(dayOfWeek: JavaDayOfWeek): DayOfWeek {
        return when (dayOfWeek) {
            JavaDayOfWeek.MONDAY -> DayOfWeek.MONDAY
            JavaDayOfWeek.TUESDAY -> DayOfWeek.TUESDAY
            JavaDayOfWeek.WEDNESDAY -> DayOfWeek.WEDNESDAY
            JavaDayOfWeek.THURSDAY -> DayOfWeek.THURSDAY
            JavaDayOfWeek.FRIDAY -> DayOfWeek.FRIDAY
            JavaDayOfWeek.SATURDAY -> DayOfWeek.SATURDAY
            JavaDayOfWeek.SUNDAY -> DayOfWeek.SUNDAY
        }
    }

    /**
     * 获取星期几的显示名称
     */
    fun getDayOfWeekDisplayName(dayOfWeek: DayOfWeek): String {
        return when (dayOfWeek) {
            DayOfWeek.MONDAY -> "周一"
            DayOfWeek.TUESDAY -> "周二"
            DayOfWeek.WEDNESDAY -> "周三"
            DayOfWeek.THURSDAY -> "周四"
            DayOfWeek.FRIDAY -> "周五"
            DayOfWeek.SATURDAY -> "周六"
            DayOfWeek.SUNDAY -> "周日"
        }
    }

    /**
     * 格式化早八提醒通知内容
     *
     * @param courses 早八课程列表
     * @return 通知内容
     */
    fun formatEarlyMorningNotificationContent(courses: List<Course>): String {
        if (courses.isEmpty()) {
            return "明天没有早八课程"
        }

        return if (courses.size == 1) {
            val course = courses[0]
            "明天早八: ${course.name} @ ${course.location}"
        } else {
            val courseNames = courses.joinToString(", ") { it.name }
            "明天早八有${courses.size}节课: $courseNames"
        }
    }

    /**
     * 格式化上课前提醒通知内容
     *
     * @param course 课程
     * @return 通知内容
     */
    fun formatBeforeClassNotificationContent(course: Course): String {
        val timeDisplay = getCourseStartTimeDisplay(course.startNode)
        return "${course.name} @ ${course.location} ($timeDisplay)"
    }
}

/**
 * 即将开始的课程
 *
 * @property course 课程信息
 * @property classStartTime 上课开始时间
 * @property reminderTime 提醒时间
 */
data class UpcomingCourse(
    val course: Course,
    val classStartTime: LocalTime,
    val reminderTime: LocalTime
) {
    /**
     * 距离上课还有多少分钟
     */
    fun getMinutesUntilClass(now: LocalTime = LocalTime.now()): Long {
        return java.time.Duration.between(now, classStartTime).toMinutes()
    }
}
