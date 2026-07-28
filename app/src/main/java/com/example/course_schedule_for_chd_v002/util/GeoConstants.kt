package com.example.course_schedule_for_chd_v002.util

import android.location.LocationManager

/**
 * [v114] 地理定位相关常量
 */
object GeoConstants {
    /** Mock 定位精度（米），全局统一，对应 ADR-0003 当前阶段全局固定精度 */
    const val MOCK_ACCURACY_METERS = 10f

    /** Mock 定位刷新间隔（毫秒），防止被真实定位覆盖 */
    const val MOCK_REFRESH_INTERVAL_MS = 1000L

    /** Mock 定位使用的测试提供者 */
    const val MOCK_PROVIDER = LocationManager.GPS_PROVIDER
}
