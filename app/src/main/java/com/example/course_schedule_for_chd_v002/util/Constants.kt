package com.example.course_schedule_for_chd_v002.util

/**
 * 应用常量定义
 */
object Constants {
    // 长安大学教务系统 URL
    object EamsUrls {
        const val BASE_URL = "http://bkjw.chd.edu.cn/"
        const val HOME_PAGE = "http://bkjw.chd.edu.cn/eams/home.action"
        const val COURSE_TABLE = "http://bkjw.chd.edu.cn/eams/courseTableForStd!courseTable.action"
    }

    // 统一身份认证 URL (CAS)
    object CasUrls {
        const val LOGIN_URL = "https://ids.chd.edu.cn/authserver/login"
        const val LOGIN_SERVICE = "http://bkjw.chd.edu.cn/eams/home.action"
        const val FULL_LOGIN_URL = "https://ids.chd.edu.cn/authserver/login?service=http://bkjw.chd.edu.cn/eams/home.action"
    }

    // 网络配置
    object Network {
        const val CONNECT_TIMEOUT = 30L
        const val READ_TIMEOUT = 30L
        const val WRITE_TIMEOUT = 30L
        const val MAX_RETRIES = 3
        const val RETRY_DELAY_MS = 1000L
    }

    // 课表配置
    object Schedule {
        const val MAX_WEEKS = 16
        const val MAX_NODES_PER_DAY = 12
    }
}
