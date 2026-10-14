package com.example.course_schedule_for_chd_v002.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.course_schedule_for_chd_v002.domain.model.CheckInLocation

/**
 * [v114] 签到位置数据库实体
 * 用于 Room 持久化存储。坐标统一为 WGS-84。
 * [v126] linkedRoomName 单值列 → linkedRoomNames 集合列（List<String> 经 TypeConverter 落 TEXT JSON 数组）。
 * [v134] linkedCourseId 单值列 → linkedCourseIds 集合列（多课程关联 tag 化，同 TypeConverter 范式）。
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
    val linkedCourseIds: List<String> = emptyList(),
    val linkedRoomNames: List<String> = emptyList(),
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
        linkedCourseIds = linkedCourseIds,
        linkedRoomNames = linkedRoomNames,
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
            linkedCourseIds = location.linkedCourseIds,
            linkedRoomNames = location.linkedRoomNames,
            createdAt = location.createdAt
        )
    }
}
