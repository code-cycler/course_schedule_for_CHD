package com.example.course_schedule_for_chd_v002.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.course_schedule_for_chd_v002.data.local.database.entity.CheckInLocationEntity
import com.example.course_schedule_for_chd_v002.data.local.database.entity.CourseEntity

/**
 * 应用数据库
 * 使用 Room 持久化库
 */
@Database(
    entities = [CourseEntity::class, CheckInLocationEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    /**
     * 获取课程数据访问对象
     */
    abstract fun courseDao(): CourseDao

    /**
     * [v114] 获取签到位置数据访问对象
     */
    abstract fun checkInLocationDao(): CheckInLocationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * [v114] 1 → 2：新增签到位置表（不影响现有 courses 数据）
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS checkin_locations (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        accuracyRadiusMeters REAL,
                        note TEXT,
                        linkedCourseId TEXT,
                        linkedRoomName TEXT,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * [优化] 数据库 Migration 列表
         * 当 schema 变更时，在此添加正式 Migration 而非销毁数据
         */
        private val MIGRATIONS = arrayOf<Migration>(
            MIGRATION_1_2
        )

        /**
         * 获取数据库实例
         * @param context 应用上下文
         * @return 数据库实例
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "course_schedule.db"
                )
                    .addMigrations(*MIGRATIONS)
                    // [移除] .fallbackToDestructiveMigration() - 使用正式迁移保护数据
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
