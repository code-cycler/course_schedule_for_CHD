package com.example.course_schedule_for_chd_v002.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.domain.model.CourseType
import com.example.course_schedule_for_chd_v002.domain.model.DayOfWeek

/**
 * 课程数据库实体
 * 用于 Room 数据库持久化存储
 */
@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val teacher: String,
    val location: String,
    val dayOfWeek: Int,           // 1-7 对应周一到周日
    val startWeek: Int,
    val endWeek: Int,
    val startNode: Int,           // 开始节次 (1-12)
    val endNode: Int,             // 结束节次
    val courseType: String,       // 课程类型名称
    val credit: Double,
    val remark: String,
    val semester: String,         // 如 "2024-2025-1"
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * 转换为领域模型
     */
    fun toDomainModel(): Course {
        return Course(
            id = id,
            name = name,
            teacher = teacher,
            location = location,
            dayOfWeek = DayOfWeek.fromValue(dayOfWeek),
            startWeek = startWeek,
            endWeek = endWeek,
            startNode = startNode,
            endNode = endNode,
            courseType = CourseType.fromString(courseType),
            credit = credit,
            remark = remark,
            semester = semester
        )
    }

    companion object {
        /**
         * 从领域模型创建实体
         */
        fun fromDomainModel(course: Course): CourseEntity {
            return CourseEntity(
                id = course.id,
                name = course.name,
                teacher = course.teacher,
                location = course.location,
                dayOfWeek = course.dayOfWeek.value,
                startWeek = course.startWeek,
                endWeek = course.endWeek,
                startNode = course.startNode,
                endNode = course.endNode,
                courseType = course.courseType.displayName,
                credit = course.credit,
                remark = course.remark,
                semester = course.semester
            )
        }
    }
}
