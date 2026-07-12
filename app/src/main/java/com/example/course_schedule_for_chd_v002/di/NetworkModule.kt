package com.example.course_schedule_for_chd_v002.di

import com.example.course_schedule_for_chd_v002.data.remote.api.CasApi
import com.example.course_schedule_for_chd_v002.data.remote.api.EamsApi
import com.example.course_schedule_for_chd_v002.data.remote.client.CookieManager
import com.example.course_schedule_for_chd_v002.data.remote.client.EamsClient
import org.koin.dsl.module

/**
 * 网络 Koin 模块
 * 提供网络相关的依赖注入配置
 */
val networkModule = module {
    // Cookie 管理器（单例）
    single { CookieManager() }

    // HTTP 客户端封装
    single { EamsClient(get()) }

    // OkHttp 客户端
    single { get<EamsClient>().okHttpClient }

    // CAS 认证 API
    single { CasApi(get()) }

    // 教务系统 API
    single { EamsApi(get()) }
}
