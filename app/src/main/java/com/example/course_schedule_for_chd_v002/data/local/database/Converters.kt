package com.example.course_schedule_for_chd_v002.data.local.database

import androidx.room.TypeConverter
import com.example.course_schedule_for_chd_v002.util.RoomsJson

/**
 * [v126] Room 类型转换器：教室 tag 集合 ↔ JSON 数组字符串。
 * 序列化逻辑在纯 Kotlin 的 [RoomsJson]（JVM 单测可测）。
 */
class Converters {
    @TypeConverter
    fun fromRoomNames(value: String): List<String> = RoomsJson.decode(value)

    @TypeConverter
    fun toRoomNames(rooms: List<String>): String = RoomsJson.encode(rooms)
}
