package com.example.course_schedule_for_chd_v002.di

import com.example.course_schedule_for_chd_v002.data.local.database.AppDatabase
import com.example.course_schedule_for_chd_v002.data.local.database.CourseDao
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * 数据库 Koin 模块
 * 提供 Room 数据库相关的依赖注入配置
 */
val databaseModule = module {
    // Room 数据库（单例） - 使用 AppDatabase.getDatabase() 统一管理迁移策略
    single<AppDatabase> { AppDatabase.getDatabase(androidContext()) }

    // Course DAO
    single<CourseDao> { get<AppDatabase>().courseDao() }
}
