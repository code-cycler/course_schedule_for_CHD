package com.example.course_schedule_for_chd_v002.service.mocklocation

import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import com.example.course_schedule_for_chd_v002.domain.model.CheckInLocation
import com.example.course_schedule_for_chd_v002.util.AppLogger
import com.example.course_schedule_for_chd_v002.util.GeoConstants
import java.lang.reflect.InvocationTargetException

/**
 * [v114] Mock 定位控制器
 *
 * 封装 Android 测试定位提供者（Test Provider）的设置/清除，以及前台服务的启停。
 * 坐标统一为 WGS-84。
 *
 * 前置条件：用户需在「开发者选项 → 选择模拟位置信息应用」中选本 App，
 * 否则 [applyMock] 会抛 [SecurityException]，由 UI 层捕获并引导。
 *
 * 兼容性：addTestProvider 在 API 34 起改为 (String, ProviderProperties) 重载，
 * 旧 (String, boolean×7, int, int) 重载已从 SDK 36 移除。本类用反射按运行时
 * 实际存在的重载调用，同时支持 API 31–33 与 34+。
 */
object MockLocationController {

    private const val TAG = "MockLocationCtrl"

    const val EXTRA_NAME = "extra.mock.name"
    const val EXTRA_LAT = "extra.mock.lat"
    const val EXTRA_LNG = "extra.mock.lng"
    const val EXTRA_DURATION_MIN = "extra.mock.duration_min"
    const val ACTION_STOP = "com.example.course_schedule_for_chd_v002.action.MOCK_STOP"

    /** 启动一次 Mock 定位会话（前台服务），超时自动恢复真实定位。已有会话先停止。 */
    fun start(context: Context, location: CheckInLocation, durationMinutes: Int) {
        stop(context)
        val intent = Intent(context, MockLocationService::class.java).apply {
            putExtra(EXTRA_NAME, location.name)
            putExtra(EXTRA_LAT, location.latitude)
            putExtra(EXTRA_LNG, location.longitude)
            putExtra(EXTRA_DURATION_MIN, durationMinutes)
        }
        context.startForegroundService(intent)
        AppLogger.i(
            TAG,
            "[v114] 启动 Mock 会话: ${location.name} (${location.latitude},${location.longitude}) ${durationMinutes}分钟"
        )
    }

    /** 手动停止 Mock 定位会话 */
    fun stop(context: Context) {
        context.stopService(Intent(context, MockLocationService::class.java))
    }

    /**
     * 检测本 App 是否被设为「模拟位置信息应用」。
     * 通过尝试 addTestProvider 捕获 [SecurityException] 判定。
     */
    fun isMockLocationAppEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return try {
            addTestProvider(lm)
            // 添加成功 → 本应用是模拟定位应用；移除刚加的测试提供者以还原
            try { lm.removeTestProvider(GeoConstants.MOCK_PROVIDER) } catch (_: Exception) {}
            AppLogger.i(TAG, "[v114] 模拟定位检测: addTestProvider 成功 → 已授权")
            true
        } catch (e: SecurityException) {
            // 未被设为「模拟位置信息应用」
            AppLogger.i(TAG, "[v114] 模拟定位检测: SecurityException → 未授权")
            false
        } catch (e: IllegalArgumentException) {
            // 测试提供者已存在（活动 Mock 会话已添加）→ 已是模拟定位应用
            AppLogger.i(TAG, "[v114] 模拟定位检测: provider 已存在 → 已授权")
            true
        } catch (e: Exception) {
            // 其它异常，保守判定为未启用，避免误报「已通过」
            AppLogger.e(TAG, "[v114] 模拟定位检测异常: ${e.javaClass.simpleName}: ${e.message}", e)
            false
        }
    }

    /**
     * 设置一次测试定位（WGS-84）。每秒调用一次可防止被真实定位覆盖。
     * @throws SecurityException 未设为模拟定位应用时抛出
     */
    @Throws(SecurityException::class)
    fun applyMock(context: Context, lat: Double, lng: Double) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: throw IllegalStateException("LocationManager 不可用")
        addTestProvider(lm)

        val location = Location(GeoConstants.MOCK_PROVIDER).apply {
            latitude = lat
            longitude = lng
            altitude = 0.0
            accuracy = GeoConstants.MOCK_ACCURACY_METERS
            speed = 0.0f
            bearing = 0.0f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        lm.setTestProviderLocation(GeoConstants.MOCK_PROVIDER, location)
    }

    /** 清除测试定位（恢复真实定位） */
    fun clearMock(context: Context) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        try {
            lm.removeTestProvider(GeoConstants.MOCK_PROVIDER)
            AppLogger.i(TAG, "[v114] 已清除测试定位")
        } catch (e: Exception) {
            AppLogger.w(TAG, "[v114] 清除测试定位失败: ${e.message}")
        }
    }

    /**
     * 调用 addTestProvider。
     * - API 34+：直接用 [ProviderProperties]（compileSdk 36 支持，不走反射，最可靠）
     * - API 31–33：反射调用旧重载 addTestProvider(String, boolean×7, int, int)
     *   （旧重载已从 SDK 36 移除，无法直接调用）
     *
     * API 34+ 的直接调用放在独立方法 [addTestProviderApi34Plus] 里，避免在低版本设备上
     * 因引用不存在的 [ProviderProperties] 而触发 NoClassDefFoundError。
     */
    @Throws(SecurityException::class, Exception::class)
    private fun addTestProvider(lm: LocationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            addTestProviderApi34Plus(lm)
        } else {
            addTestProviderLegacy(lm)
        }
    }

    /** API 34+：直接构造 ProviderProperties 调用（方法名以 compileSdk 36 的 Builder 为准） */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Throws(SecurityException::class, Exception::class)
    private fun addTestProviderApi34Plus(lm: LocationManager) {
        val props = ProviderProperties.Builder()
            .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
            .setAccuracy(ProviderProperties.ACCURACY_FINE)
            .setHasMonetaryCost(false)
            .setHasAltitudeSupport(false)
            .setHasSpeedSupport(false)
            .setHasBearingSupport(false)
            .setHasNetworkRequirement(false)
            .setHasSatelliteRequirement(false)
            .setHasCellRequirement(false)
            .build()
        lm.addTestProvider(GeoConstants.MOCK_PROVIDER, props)
    }

    /** API 31–33：反射调用旧重载（已从 SDK 36 移除）。解包 InvocationTargetException。 */
    @Throws(SecurityException::class, Exception::class)
    private fun addTestProviderLegacy(lm: LocationManager) {
        try {
            val legacy = LocationManager::class.java.getMethod(
                "addTestProvider",
                String::class.java,
                java.lang.Boolean.TYPE, java.lang.Boolean.TYPE, java.lang.Boolean.TYPE,
                java.lang.Boolean.TYPE, java.lang.Boolean.TYPE, java.lang.Boolean.TYPE,
                java.lang.Boolean.TYPE, java.lang.Integer.TYPE, java.lang.Integer.TYPE
            )
            // requiresNetwork=F, requiresSatellite=F, requiresCell=F, hasMonetaryCost=F,
            // supportsAltitude=F, supportsSpeed=F, supportsBearing=F, power=LOW(0), accuracy=FINE(1)
            legacy.invoke(
                lm, GeoConstants.MOCK_PROVIDER,
                false, false, false, false, false, false, false, 0, 1
            )
        } catch (e: InvocationTargetException) {
            throw (e.cause as? Exception) ?: e
        }
    }
}
