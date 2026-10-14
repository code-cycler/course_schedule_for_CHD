package com.example.course_schedule_for_chd_v002.data.local.database

import androidx.room.TypeConverter
import com.example.course_schedule_for_chd_v002.util.RoomsJson

/**
 * [v126] Room 类型转换器：教室 tag 集合 ↔ JSON 数组字符串。
 * 序列化逻辑在纯 Kotlin 的 [RoomsJson]（JVM 单测可测）。
 * [v134] 注：Room 按「类型」匹配 converter，本对 String ↔ List<String> 同时服务
 * linkedRoomNames 与 linkedCourseIds 两列（同 codec，不得再注册同类型第二对）。
 */
class Converters {
    @TypeConverter
    fun fromRoomNames(value: String): List<String> = RoomsJson.decode(value)

    @TypeConverter
    fun toRoomNames(rooms: List<String>): String = RoomsJson.encode(rooms)
}
