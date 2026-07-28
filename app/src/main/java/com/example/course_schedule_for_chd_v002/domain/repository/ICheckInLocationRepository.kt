package com.example.course_schedule_for_chd_v002.domain.repository

import com.example.course_schedule_for_chd_v002.domain.model.CheckInLocation
import kotlinx.coroutines.flow.Flow

/**
 * [v114] 签到位置仓库接口
 */
interface ICheckInLocationRepository {

    /** 监听所有签到位置 */
    fun observeAll(): Flow<List<CheckInLocation>>

    /** 一次性获取所有签到位置 */
    suspend fun getAll(): List<CheckInLocation>

    /** 按 ID 查询 */
    suspend fun getById(id: Long): CheckInLocation?

    /** 新增位置，返回主键 ID */
    suspend fun insert(location: CheckInLocation): Long

    /** 更新位置 */
    suspend fun update(location: CheckInLocation)

    /** 按 ID 删除 */
    suspend fun deleteById(id: Long)

    /**
     * 按关联课程/教室查询（触发时自动匹配用）
     * @param courseName 课程名，可为空
     * @param roomName 教室名，可为空
     */
    suspend fun getByLinkedCourseOrRoom(courseName: String?, roomName: String?): List<CheckInLocation>
}
