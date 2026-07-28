package com.example.course_schedule_for_chd_v002.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.course_schedule_for_chd_v002.domain.model.CheckInLocation

/**
 * [v114] 签到位置数据库实体
 * 用于 Room 持久化存储。坐标统一为 WGS-84。
 */
@Entity(tableName = "checkin_locations")
data class CheckInLocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyRadiusMeters: Float? = null,
    val note: String? = null,
    val linkedCourseId: String? = null,
    val linkedRoomName: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** 转换为领域模型 */
    fun toDomainModel(): CheckInLocation = CheckInLocation(
        id = id,
        name = name,
        latitude = latitude,
        longitude = longitude,
        accuracyRadiusMeters = accuracyRadiusMeters,
        note = note,
        linkedCourseId = linkedCourseId,
        linkedRoomName = linkedRoomName,
        createdAt = createdAt
    )

    companion object {
        /** 从领域模型创建实体 */
        fun fromDomainModel(location: CheckInLocation): CheckInLocationEntity = CheckInLocationEntity(
            id = location.id,
            name = location.name,
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyRadiusMeters = location.accuracyRadiusMeters,
            note = location.note,
            linkedCourseId = location.linkedCourseId,
            linkedRoomName = location.linkedRoomName,
            createdAt = location.createdAt
        )
    }
}
