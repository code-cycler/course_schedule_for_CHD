package com.example.course_schedule_for_chd_v002.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.course_schedule_for_chd_v002.data.local.database.entity.CourseEntity

/**
 * 课程数据访问对象
 * 定义数据库操作接口
 */
@Dao
interface CourseDao {

    /**
     * 按学期查询课程
     * @param semester 学期标识，如 "2024-2025-1"
     * @return 该学期的所有课程，按星期和节次排序
     */
    @Query("SELECT * FROM courses WHERE semester = :semester ORDER BY dayOfWeek, startNode")
    suspend fun getBySemester(semester: String): List<CourseEntity>

    /**
     * 按ID查询课程
     * @param id 课程ID
     * @return 课程实体，不存在则返回null
     */
    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun getById(id: Long): CourseEntity?

    /**
     * 获取所有学期
     * @return 按降序排列的学期列表
     */
    @Query("SELECT DISTINCT semester FROM courses ORDER BY semester DESC")
    suspend fun getAllSemesters(): List<String>

    /**
     * 插入单条课程
     * @param course 课程实体
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(course: CourseEntity)

    /**
     * 批量插入课程
     * @param courses 课程实体列表
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(courses: List<CourseEntity>)

    /**
     * 按学期删除课程
     * @param semester 学期标识
     */
    @Query("DELETE FROM courses WHERE semester = :semester")
    suspend fun deleteBySemester(semester: String)

    /**
     * 删除所有课程
     */
    @Query("DELETE FROM courses")
    suspend fun deleteAll()
}
