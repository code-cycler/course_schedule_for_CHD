package com.example.course_schedule_for_chd_v002.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * [v128→v130] 各厂商「自启动/应用启动管理」深链引导（原 HonorAutoStart 全厂商化）。
 *
 * 背景：真机实证（假设 E）荣耀 MagicOS 的 BootProxy 会扣留 BOOT_COMPLETED
 * （"don't meet cpuload"），开机重绑监听依赖用户在系统里允许本应用自启动；
 * 各厂商该开关均为私有设置，无标准 API 查询状态，只能深链引导 + 用户回报确认。
 *
 * 候选按 Build.MANUFACTURER 匹配优先排序（本机厂商组件排最前），逐个 try，
 * 全失败返回 false（调用方回退应用详情页）。组件名随厂商系统版本演进可能失效，
 * 失效即静默跳过尝试下一个。
 */
object AutoStartLauncher {

    /** 厂商 → 深链候选（类名前缀省略包名时自动补全） */
    private val VENDOR_CANDIDATES: Map<String, List<ComponentName>> = mapOf(
        "HONOR" to listOf(
            // 荣耀 MagicOS「应用启动管理」（真机 SER-AN00 实测直达，2026-10-14）
            cn("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            cn("com.hihonor.systemmanager", "com.hihonor.systemmanager.appcontrol.activity.StartupAppControlActivity")
        ),
        "HUAWEI" to listOf(
            // 华为 EMUI「应用启动管理」（与荣耀同源组件）
            cn("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            cn("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")
        ),
        "XIAOMI" to listOf(
            // MIUI/ HyperOS「自启动管理」
            cn("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            cn("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")
        ),
        "REDMI" to listOf(),  // 与 XIAOMI 共表（见 manufacturerKey 映射）
        "POCO" to listOf(),
        "OPPO" to listOf(
            // ColorOS「应用速冻/自启动管理」
            cn("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            cn("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            cn("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")
        ),
        "ONEPLUS" to listOf(),  // ColorOS 同 OPPO
        "REALME" to listOf(),
        "VIVO" to listOf(
            // OriginOS/Funtouch「后台弹出式/关联启动」
            cn("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            cn("com.iqoo.secure", "com.iqoo.secure.ui.phoneaddon.BgStartUpManager")
        ),
        "IQOO" to listOf(),
        "MEIZU" to listOf(
            // Flyme「后台管理」
            cn("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity")
        )
        // SAMSUNG 及其它：无独立自启动管理（Device care 体系），直接回退应用详情页
    )

    /** 子品牌映射到主厂商（REDMI/POCO→XIAOMI 等，共享候选表） */
    private val VENDOR_ALIAS = mapOf(
        "REDMI" to "XIAOMI", "POCO" to "XIAOMI",
        "ONEPLUS" to "OPPO", "REALME" to "OPPO",
        "IQOO" to "VIVO"
    )

    private fun cn(pkg: String, cls: String) = ComponentName(pkg, cls)

    /** 当前设备的候选列表：本机厂商在前，其余厂商的通用候选殿后兜底 */
    fun candidatesFor(manufacturer: String = Build.MANUFACTURER?.uppercase() ?: ""): List<ComponentName> {
        val key = VENDOR_ALIAS[manufacturer] ?: manufacturer
        val own = VENDOR_CANDIDATES[key].orEmpty()
        val rest = VENDOR_CANDIDATES.entries
            .filter { it.key != key && it.value.isNotEmpty() }
            .flatMap { it.value }
        return own + rest
    }

    /**
     * 依次尝试启动厂商自启动管理页；全部失败返回 false（调用方回退应用详情页）。
     */
    fun openSettings(context: Context): Boolean {
        for (component in candidatesFor()) {
            val intent = Intent(Intent.ACTION_MAIN)
                .setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return true
        }
        return false
    }
}
