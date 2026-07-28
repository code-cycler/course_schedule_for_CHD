package com.example.course_schedule_for_chd_v002.domain.model

/**
 * [v114] 签到位置领域模型
 *
 * 用户为签到场景标定的地理坐标。内部统一使用 WGS-84 坐标系
 * （Android 系统定位 / Mock 定位所用坐标系）。
 *
 * 可选关联到课程表中的某门课程或某个教室，用于在签到触发时优先自动匹配。
 * 未关联时，由用户手动选择使用哪个位置。
 *
 * @param id 主键，新增时为 0
 * @param name 显示名称，如「图书馆东侧」
 * @param latitude 纬度（WGS-84）
 * @param longitude 经度（WGS-84）
 * @param accuracyRadiusMeters 精度半径占位（当前 Mock 统一用全局常量，此字段预留扩展）
 * @param note 备注
 * @param linkedCourseId 可选：关联课程标识（取课程名，跨学期稳定）
 * @param linkedRoomName 可选：关联教室名（取课程的 location 字段）
 * @param createdAt 创建时间戳
 */
data class CheckInLocation(
    val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyRadiusMeters: Float? = null,
    val note: String? = null,
    val linkedCourseId: String? = null,
    val linkedRoomName: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
