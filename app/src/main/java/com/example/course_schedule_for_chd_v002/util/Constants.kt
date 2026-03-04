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
        // service 参数必须使用 URL 编码格式
        const val FULL_LOGIN_URL = "https://ids.chd.edu.cn/authserver/login?service=http%3A%2F%2Fbkjw.chd.edu.cn%2Feams%2Fhome.action"
        // WebView 登录入口 - 直接访问教务系统根路径，系统会自动跳转
        const val WEBVIEW_ENTRY_URL = "http://bkjw.chd.edu.cn/eams"
        // 课表页面 URL - 用于检测用户是否进入课表页面
        const val COURSE_TABLE_URL = "http://bkjw.chd.edu.cn/eams/courseTableForStd!courseTable.action"
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
        const val MAX_WEEKS = 25  // 学期最多25周
        const val MAX_NODES_PER_DAY = 11  // [v38] 从12改为11，学校实际每天11节课
    }

    // beangle 框架配置 [v48]
    object BeangleConfig {
        // 数据加载超时设置
        const val DATA_LOAD_TIMEOUT_MS = 10000L  // 10秒超时
        const val DATA_CHECK_INTERVAL_MS = 500L  // 每500ms检测一次
        const val MAX_RETRY_ATTEMPTS = 20  // 最多检测20次 (10秒)

        // 页面类型标识
        const val PAGE_TYPE_CAS_LOGIN = "cas_login"
        const val PAGE_TYPE_EAMS_HOME = "eams_home"
        const val PAGE_TYPE_COURSE_TABLE = "course_table"
        const val PAGE_TYPE_UNKNOWN = "unknown"
    }
}
