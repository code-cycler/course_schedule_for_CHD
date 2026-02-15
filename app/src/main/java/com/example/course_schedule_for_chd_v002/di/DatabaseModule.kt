package com.example.course_schedule_for_chd_v002.di

import androidx.room.Room
import com.example.course_schedule_for_chd_v002.data.local.database.AppDatabase
import com.example.course_schedule_for_chd_v002.data.local.database.CourseDao
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * 数据库 Koin 模块
 * 提供 Room 数据库相关的依赖注入配置
 */
val databaseModule = module {
    // Room 数据库（单例）
    single<AppDatabase> {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "course_schedule.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    // Course DAO
    single<CourseDao> { get<AppDatabase>().courseDao() }
}
