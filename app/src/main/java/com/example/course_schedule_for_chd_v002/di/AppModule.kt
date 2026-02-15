package com.example.course_schedule_for_chd_v002.di

import com.example.course_schedule_for_chd_v002.data.local.preferences.UserPreferences
import com.example.course_schedule_for_chd_v002.data.remote.parser.ScheduleHtmlParser
import com.example.course_schedule_for_chd_v002.data.repository.CourseRepositoryImpl
import com.example.course_schedule_for_chd_v002.domain.repository.ICourseRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * 应用级 Koin 模块
 * 提供 Repository、UseCase 等业务逻辑相关的依赖注入配置
 */
val appModule = module {
    // 用户偏好设置
    single { UserPreferences(androidContext()) }

    // HTML 解析器
    single { ScheduleHtmlParser() }

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
}
