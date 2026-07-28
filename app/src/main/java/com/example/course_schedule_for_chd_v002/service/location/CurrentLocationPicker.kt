package com.example.course_schedule_for_chd_v002.service.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.example.course_schedule_for_chd_v002.util.AppLogger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * [v114] 当前真实 GPS 采集器
 *
 * 同时请求 GPS 与 NETWORK（Wi-Fi/基站）两个定位源，**谁先返回带精度的有效定位就用谁**，
 * 10 秒超时。坐标天然为 WGS-84，直接入库 / Mock，无需任何坐标系转换。
 *
 * 室内 GPS 信号弱时由 NETWORK_PROVIDER 兜底（与百度等定位 SDK 室内定位同源）。
 *
 * 前置权限：ACCESS_FINE_LOCATION（GPS）+ ACCESS_COARSE_LOCATION（NETWORK）。
 */
class CurrentLocationPicker(private val context: Context) {

    /** 采集到的位置（WGS-84） */
    data class Position(
        val latitude: Double,
        val longitude: Double,
        /** 精度半径（米），由系统给出；可能为 null（极少） */
        val accuracyMeters: Float?
    )

    sealed class Outcome {
        data class Success(val position: Position) : Outcome()
        /** 超时未拿到任何有效定位 */
        object Timeout : Outcome()
        /** 缺权限 / 无可用 provider */
        data class Error(val message: String) : Outcome()
    }

    private val lm: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /**
     * 采集一次当前位置。
     * @param timeoutMs 超时毫秒，默认 10s
     */
    @SuppressLint("MissingPermission")
    suspend fun collect(timeoutMs: Long = DEFAULT_TIMEOUT_MS): Outcome {
        val hasGps = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val hasNetwork = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (!hasGps && !hasNetwork) {
            return Outcome.Error("定位服务未开启，请在系统设置中打开位置")
        }

        val result = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Position> { cont ->
                val settled = AtomicReference<Position?>(null)
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (!location.hasAccuracy()) return
                        val pos = Position(location.latitude, location.longitude, location.accuracy)
                        // CAS 保证只 resume 一次
                        if (settled.compareAndSet(null, pos) && cont.isActive) {
                            AppLogger.d(TAG, "[v114] 采集到定位: ${pos.latitude},${pos.longitude} ±${pos.accuracyMeters}m (${location.provider})")
                            cont.resume(pos)
                        }
                    }
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                    @Deprecated("已废弃，空实现")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                }

                cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }

                try {
                    val mainLooper = Looper.getMainLooper()
                    if (hasGps) {
                        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener, mainLooper)
                    }
                    if (hasNetwork) {
                        lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener, mainLooper)
                    }
                } catch (e: SecurityException) {
                    if (cont.isActive) cont.resume(
                        Position(0.0, 0.0, null) // 占位，外层判 Error
                    )
                }
            }
        }

        return when {
            result == null -> Outcome.Timeout
            result.latitude == 0.0 && result.longitude == 0.0 -> Outcome.Error("缺少定位权限")
            else -> Outcome.Success(result)
        }
    }

    companion object {
        private const val TAG = "CurrentLocationPicker"
        const val DEFAULT_TIMEOUT_MS = 10_000L
    }
}
