package com.example.course_schedule_for_chd_v002.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.course_schedule_for_chd_v002.data.local.database.entity.CourseEntity

/**
 * 应用数据库
 * 使用 Room 持久化库
 */
@Database(
    entities = [CourseEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    /**
     * 获取课程数据访问对象
     */
    abstract fun courseDao(): CourseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * [优化] 数据库 Migration 列表
         * 当 schema 变更时，在此添加正式 Migration 而非销毁数据
         *
         * 示例：
         * val MIGRATION_1_2 = object : Migration(1, 2) {
         *     override fun migrate(db: SupportSQLiteDatabase) {
         *         db.execSQL("ALTER TABLE courses ADD COLUMN new_field TEXT DEFAULT ''")
         *     }
         * }
         */
        private val MIGRATIONS = arrayOf<Migration>(
            // 未来版本迁移放在这里
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
