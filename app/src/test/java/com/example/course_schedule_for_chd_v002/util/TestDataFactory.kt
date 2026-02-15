package com.example.course_schedule_for_chd_v002.util

import com.example.course_schedule_for_chd_v002.domain.model.*

/**
 * 测试数据工厂
 * 用于生成测试用的数据对象
 */
object TestDataFactory {
    /**
     * 创建课程对象
     */
    fun createCourse(
        id: Long = 1,
        name: String = "高等数学",
        teacher: String = "张老师",
        location: String = "A101",
        dayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
        startWeek: Int = 1,
        endWeek: Int = 16,
        startNode: Int = 1,
        endNode: Int = 2,
        courseType: CourseType = CourseType.REQUIRED,
        credit: Double = 4.0,
        remark: String = "",
        semester: String = "2024-2025-1"
    ): Course {
        return Course(
            id = id,
            name = name,
            teacher = teacher,
            location = location,
            dayOfWeek = dayOfWeek,
            startWeek = startWeek,
            endWeek = endWeek,
            startNode = startNode,
            endNode = endNode,
            courseType = courseType,
            credit = credit,
            remark = remark,
            semester = semester
        )
    }

    /**
     * 创建课程列表
     */
    fun createCourseList(count: Int): List<Course> {
        return (1..count).map { index ->
            createCourse(
                id = index.toLong(),
                name = "课程$index",
                dayOfWeek = DayOfWeek.entries[index % 7]
            )
        }
    }
}
