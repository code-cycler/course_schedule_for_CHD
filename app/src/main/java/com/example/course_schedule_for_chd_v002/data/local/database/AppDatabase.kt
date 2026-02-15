package com.example.course_schedule_for_chd_v002.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.course_schedule_for_chd_v002.data.local.database.entity.CourseEntity

/**
 * 应用数据库
 * 使用 Room 持久化库
 */
@Database(
    entities = [CourseEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    /**
     * 获取课程数据访问对象
     */
    abstract fun courseDao(): CourseDao
}
