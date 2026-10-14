package com.example.course_schedule_for_chd_v002.di

import com.example.course_schedule_for_chd_v002.data.local.preferences.UserPreferences
import com.example.course_schedule_for_chd_v002.data.remote.parser.ScheduleHtmlParser
import com.example.course_schedule_for_chd_v002.data.repository.CheckInLocationRepositoryImpl
import com.example.course_schedule_for_chd_v002.data.repository.CourseRepositoryImpl
import com.example.course_schedule_for_chd_v002.domain.repository.ICheckInLocationRepository
import com.example.course_schedule_for_chd_v002.domain.repository.ICourseRepository
import com.example.course_schedule_for_chd_v002.service.calendar.CalendarSyncService
import com.example.course_schedule_for_chd_v002.service.checkin.CheckInTriggerCoordinator
import com.example.course_schedule_for_chd_v002.service.checkin.NlsHealthMonitor
import com.example.course_schedule_for_chd_v002.service.checkin.polling.TronClassPoller
import com.example.course_schedule_for_chd_v002.ui.screens.checkinassist.CheckInAssistViewModel
import com.example.course_schedule_for_chd_v002.ui.screens.checkinassist.TronClassConnectViewModel
import com.example.course_schedule_for_chd_v002.ui.screens.login.LoginViewModel
import com.example.course_schedule_for_chd_v002.ui.screens.schedule.ScheduleViewModel
import org.koin.android.ext.koin.androidContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * 应用级 Koin 模块
 * 提供 Repository、UseCase、ViewModel 等业务逻辑相关的依赖注入配置
 */
val appModule = module {
    // 用户偏好设置
    single { UserPreferences(androidContext()) }

    // HTML 解析器
    single { ScheduleHtmlParser() }

    // 日历同步服务
    single { CalendarSyncService(androidContext()) }

    // [v123] 协程调度器（named）：ViewModel/Repository 注入，单测可换 TestDispatcher（L2-build B 批契约）
    single<CoroutineDispatcher>(named("default")) { Dispatchers.Default }
    single<CoroutineDispatcher>(named("io")) { Dispatchers.IO }

    // Repository
    single<ICourseRepository> {
        CourseRepositoryImpl(
            casApi = get(),
            eamsApi = get(),
            cookieManager = get(),
            htmlParser = get(),
            userPreferences = get(),
            courseDao = get(),
            ioDispatcher = get(named("io"))
        )
    }

    // [v114] 签到位置 Repository
    single<ICheckInLocationRepository> { CheckInLocationRepositoryImpl(get()) }

    // [v114] 签到触发协调器（UI 模拟触发 + 后台通知监听共用）
    single { CheckInTriggerCoordinator(get(), get(), get(), get()) }

    // [v126] NLS 健康监控（心跳 / 疑似未绑判定 / requestRebind / 冷启动检查）
    single {
        NlsHealthMonitor(
            appContext = androidContext(),
            userPreferences = get()
        )
    }

    // [v129] B5 轮询器（baseUrl/token 从 DataStore 惰性读取，连接页更新后即生效）
    single {
        val prefs = get<com.example.course_schedule_for_chd_v002.data.local.preferences.UserPreferences>()
        TronClassPoller(
            baseUrl = { runBlocking { prefs.tronClassBaseUrl.firstOrNull() } },
            token = { runBlocking { prefs.tronClassToken.firstOrNull() } }
        )
    }

    // ViewModels
    viewModel { LoginViewModel(get(), get()) }
    // [v129] B5 畅课连接页
    viewModel { TronClassConnectViewModel(get()) }
    viewModel { params ->
        ScheduleViewModel(
            repository = get(),
            userPreferences = get(),
            semester = params.get(),
            calendarSyncService = get(),
            defaultDispatcher = get(named("default")),
            ioDispatcher = get(named("io"))
        )
    }

    // [v114] 签到辅助 ViewModel
    viewModel { CheckInAssistViewModel(get(), get(), get(), get(), get(), get()) }
}
