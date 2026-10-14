package com.example.course_schedule_for_chd_v002.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.course_schedule_for_chd_v002.data.local.database.entity.CheckInLocationEntity
import kotlinx.coroutines.flow.Flow

/**
 * [v114] 签到位置数据访问对象
 */
@Dao
interface CheckInLocationDao {

    /** 监听所有签到位置（Flow，UI 订阅用） */
    @Query("SELECT * FROM checkin_locations ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<CheckInLocationEntity>>

    /** 一次性获取所有签到位置 */
    @Query("SELECT * FROM checkin_locations ORDER BY createdAt DESC")
    suspend fun getAll(): List<CheckInLocationEntity>

    /** 按 ID 查询 */
    @Query("SELECT * FROM checkin_locations WHERE id = :id")
    suspend fun getById(id: Long): CheckInLocationEntity?

    // [v126] 删除 getByLinkedCourseOrRoom：v114 预留但零调用；教室 tag 集合化后
    // JSON 数组列不适合 SQL 等值查询，课程/教室匹配统一走 LocationSelection 内存过滤（本来就是全量内存路径）。

    /** 插入并返回生成的主键 ID */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAndGetId(location: CheckInLocationEntity): Long

    /** 更新单条 */
    @Update
    suspend fun update(location: CheckInLocationEntity)

    /** 按 ID 删除 */
    @Query("DELETE FROM checkin_locations WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 总数（设置页状态用） */
    @Query("SELECT COUNT(*) FROM checkin_locations")
    suspend fun count(): Int
}
