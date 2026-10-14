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

    // [v126] 删除 getByLinkedCourseOrRoom（零调用预留；匹配统一走 LocationSelection 内存过滤）
}
