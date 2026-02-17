package com.example.course_schedule_for_chd_v002

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.course_schedule_for_chd_v002.di.appModule
import com.example.course_schedule_for_chd_v002.di.databaseModule
import com.example.course_schedule_for_chd_v002.di.networkModule
import com.example.course_schedule_for_chd_v002.ui.navigation.AppNavigation
import com.example.course_schedule_for_chd_v002.ui.theme.Course_schedule_for_CHD_v002Theme
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * 应用主Activity
 * 作为应用入口点，初始化依赖注入和导航
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化 Koin 依赖注入
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@MainActivity)
            modules(
                networkModule,
                databaseModule,
                appModule
            )
        }

        enableEdgeToEdge()
        setContent {
            Course_schedule_for_CHD_v002Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavigation(navController = navController)
                }
            }
        }
    }
}
