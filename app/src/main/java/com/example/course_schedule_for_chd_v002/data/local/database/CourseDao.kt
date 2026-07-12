package com.example.course_schedule_for_chd_v002.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.course_schedule_for_chd_v002.data.local.database.entity.CourseEntity
import kotlinx.coroutines.flow.Flow

/**
 * 课程数据访问对象
 * 定义数据库操作接口
 */
@Dao
interface CourseDao {

    /**
     * 按学期查询课程 (Flow)
     * @param semester 学期标识，如 "2024-2025-1"
     * @return 该学期的所有课程的Flow，按星期和节次排序
     */
    @Query("SELECT * FROM courses WHERE semester = :semester ORDER BY dayOfWeek, startNode")
    fun getCoursesBySemesterFlow(semester: String): Flow<List<CourseEntity>>

    /**
     * 获取当前学期 (Flow)
     * @return 最新的学期标识
     */
    @Query("SELECT DISTINCT semester FROM courses ORDER BY semester DESC LIMIT 1")
    fun getCurrentSemesterFlow(): Flow<String?>

    /**
     * 按学期查询课程 (同步)
     * @param semester 学期标识，如 "2024-2025-1"
     * @return 该学期的所有课程，按星期和节次排序
     */
    @Query("SELECT * FROM courses WHERE semester = :semester ORDER BY dayOfWeek, startNode")
    suspend fun getCoursesBySemester(semester: String): List<CourseEntity>

    /**
     * 按学期查询课程 (同步，非suspend版本，用于BroadcastReceiver)
     * @param semester 学期标识，如 "2024-2025-1"
     * @return 该学期的所有课程，按星期和节次排序
     */
    @Query("SELECT * FROM courses WHERE semester = :semester ORDER BY dayOfWeek, startNode")
    fun getCoursesBySemesterSync(semester: String): List<CourseEntity>

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
     * 插入单条课程并返回生成的主键ID
     * @param course 课程实体
     * @return 生成的主键ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAndGetId(course: CourseEntity): Long

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

    /**
     * 更新单条课程
     * @param course 课程实体
     */
    @Update
    suspend fun update(course: CourseEntity)

    /**
     * 按ID删除单条课程
     * @param id 课程ID
     */
    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 按课程名查询（同名课程的所有时段）
     * @param semester 学期标识
     * @param name 课程名称
     * @return 该名称下所有课程实例
     */
    @Query("SELECT * FROM courses WHERE semester = :semester AND name = :name ORDER BY dayOfWeek, startNode")
    suspend fun getCoursesByName(semester: String, name: String): List<CourseEntity>

    /**
     * 获取所有不重复的教师名（用于编辑建议）
     * @param semester 学期标识
     * @return 教师名列表
     */
    @Query("SELECT DISTINCT teacher FROM courses WHERE semester = :semester AND teacher != '' ORDER BY teacher")
    suspend fun getDistinctTeachers(semester: String): List<String>

    /**
     * 获取所有不重复的教室（用于编辑建议）
     * @param semester 学期标识
     * @return 教室列表
     */
    @Query("SELECT DISTINCT location FROM courses WHERE semester = :semester AND location != '' ORDER BY location")
    suspend fun getDistinctLocations(semester: String): List<String>
}
