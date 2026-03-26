package com.example.course_schedule_for_chd_v002.di

import com.example.course_schedule_for_chd_v002.data.local.preferences.UserPreferences
import com.example.course_schedule_for_chd_v002.data.remote.parser.ScheduleHtmlParser
import com.example.course_schedule_for_chd_v002.data.repository.CourseRepositoryImpl
import com.example.course_schedule_for_chd_v002.domain.repository.ICourseRepository
import com.example.course_schedule_for_chd_v002.service.calendar.CalendarSyncService
import com.example.course_schedule_for_chd_v002.service.reminder.ReminderManager
import com.example.course_schedule_for_chd_v002.ui.screens.login.LoginViewModel
import com.example.course_schedule_for_chd_v002.ui.screens.schedule.ScheduleViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * 应用级 Koin 模块
 * 提供 Repository、UseCase、ViewModel 等业务逻辑相关的依赖注入配置
 */
val appModule = module {
    // 用户偏好设置
    single { UserPreferences(androidContext()) }

    // HTML 解析器
    single { ScheduleHtmlParser() }

    // [课程提醒] 提醒管理器
    single { ReminderManager(androidContext()) }

    // [课程提醒] 日历同步服务
    single { CalendarSyncService(androidContext()) }

    // Repository
    single<ICourseRepository> {
        CourseRepositoryImpl(
            casApi = get(),
            eamsApi = get(),
            cookieManager = get(),
            htmlParser = get(),
            userPreferences = get(),
            courseDao = get()
        )
    }

    // ViewModels
    viewModel { LoginViewModel(get(), get()) }  // 添加 UserPreferences 参数
    // [v61] ScheduleViewModel 需要 userPreferences、reminderManager 和 calendarSyncService 参数
    viewModel { params -> ScheduleViewModel(get(), get(), params.get(), get(), get()) }
}
