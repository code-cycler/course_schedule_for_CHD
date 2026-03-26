import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.course_schedule_for_chd_v002"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.course_schedule_for_chd_v002"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // 启用代码压缩和资源压缩
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }

    // 排除不必要的资源文件以减小 APK 体积
    packaging {
        resources {
            excludes += setOf(
                // 排除多语言支持（只保留中文和英文）
                "values-af/**",
                "values-am/**",
                "values-ar/**",
                "values-as/**",
                "values-az/**",
                "values-be/**",
                "values-bg/**",
                "values-bn/**",
                "values-bs/**",
                "values-ca/**",
                "values-cs/**",
                "values-da/**",
                "values-de/**",  // 德语
                "values-el/**",
                "values-en-rAU/**",
                "values-en-rCA/**",
                "values-en-rGB/**",
                "values-en-rIN/**",
                "values-en-rXC/**",
                "values-eo/**",
                "values-es/**",  // 西班牙语
                "values-et/**",
                "values-eu/**",
                "values-fa/**",
                "values-fi/**",
                "values-fr/**",  // 法语
                "values-gl/**",
                "values-gu/**",
                "values-hi/**",
                "values-hr/**",
                "values-hu/**",
                "values-hy/**",
                "values-in/**",
                "values-is/**",
                "values-it/**",  // 意大利语
                "values-iw/**",
                "values-ja/**",  // 日语
                "values-ka/**",
                "values-kk/**",
                "values-km/**",
                "values-kn/**",
                "values-ko/**",  // 韩语
                "values-ky/**",
                "values-lo/**",
                "values-lt/**",
                "values-lv/**",
                "values-mk/**",
                "values-ml/**",
                "values-mn/**",
                "values-mr/**",
                "values-ms/**",
                "values-my/**",
                "values-nb/**",
                "values-ne/**",
                "values-nl/**",  // 荷兰语
                "values-or/**",
                "values-pa/**",
                "values-pl/**",  // 波兰语
                "values-pt/**",  // 葡萄牙语
                "values-ro/**",
                "values-ru/**",  // 俄语
                "values-si/**",
                "values-sk/**",
                "values-sl/**",
                "values-sq/**",
                "values-sr/**",
                "values-sv/**",
                "values-sw/**",
                "values-ta/**",
                "values-te/**",
                "values-th/**",
                "values-tl/**",
                "values-tr/**",  // 土耳其语
                "values-uk/**",
                "values-ur/**",
                "values-uz/**",
                "values-vi/**",
                "values-zh-rCN/**",  // 保留简体中文
                "values-zh-rHK/**",
                "values-zh-rTW/**"
            )
        }
    }

}

// APK 命名规则配置
// 格式: 课程表_CHD_v{版本名}_{构建类型}_{日期}_{时间}.apk
// 示例: 课程表_CHD_v1.0_release_20260302_143025.apk
tasks.register("renameApk") {
    group = "build"
    description = "Rename APK with version, build type and timestamp"

    doLast {
        val dateFormat = SimpleDateFormat("yyyyMMdd")
        val timeFormat = SimpleDateFormat("HHmmss")
        val now = Date()
        val dateStr = dateFormat.format(now)
        val timeStr = timeFormat.format(now)

        val appName = "课程表_CHD"
        val version = "v${android.defaultConfig.versionName}"

        // 处理 debug APK
        val debugApkDir = file("build/outputs/apk/debug")
        if (debugApkDir.exists()) {
            debugApkDir.listFiles()?.filter { it.extension == "apk" }?.forEach { apkFile ->
                val buildType = "debug"
                val newName = "${appName}_${version}_${buildType}_${dateStr}_${timeStr}.apk"
                val newFile = File(apkFile.parent, newName)
                apkFile.renameTo(newFile)
                println("[OK] Renamed: ${newFile.absolutePath}")
            }
        }

        // 处理 release APK
        val releaseApkDir = file("build/outputs/apk/release")
        if (releaseApkDir.exists()) {
            releaseApkDir.listFiles()?.filter { it.extension == "apk" }?.forEach { apkFile ->
                val buildType = "release"
                val newName = "${appName}_${version}_${buildType}_${dateStr}_${timeStr}.apk"
                val newFile = File(apkFile.parent, newName)
                apkFile.renameTo(newFile)
                println("[OK] Renamed: ${newFile.absolutePath}")
            }
        }
    }
}

// 在 assemble 任务完成后自动执行重命名
afterEvaluate {
    tasks.findByName("assembleDebug")?.finalizedBy("renameApk")
    tasks.findByName("assembleRelease")?.finalizedBy("renameApk")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // 网络请求
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.scalars)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.jsoup)

    // Room 数据库
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // 导航
    implementation(libs.navigation.compose)

    // ViewModel
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // 协程
    implementation(libs.kotlinx.coroutines.android)

    // DataStore
    implementation(libs.datastore.preferences)

    // Koin DI
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    // 测试依赖
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mock.webserver)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}