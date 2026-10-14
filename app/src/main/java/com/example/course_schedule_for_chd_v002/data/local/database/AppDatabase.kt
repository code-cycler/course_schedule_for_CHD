package com.example.course_schedule_for_chd_v002.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.course_schedule_for_chd_v002.data.local.database.entity.CheckInLocationEntity
import com.example.course_schedule_for_chd_v002.data.local.database.entity.CourseEntity
import com.example.course_schedule_for_chd_v002.domain.model.CheckInLocation
import com.example.course_schedule_for_chd_v002.util.AppLogger
import com.example.course_schedule_for_chd_v002.util.CheckInLocationBackup
import com.example.course_schedule_for_chd_v002.util.RoomsJson
import java.io.File

/**
 * 应用数据库
 * 使用 Room 持久化库
 */
@Database(
    entities = [CourseEntity::class, CheckInLocationEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
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
         * [v126] 2 → 3：linkedRoomName 单值列 → linkedRoomNames 集合列（TEXT 存 JSON 数组）。
         *
         * 步骤（ADR-0007 + grill W01 Q3）：
         * 1. **迁移前备份**：全部旧位置行导出为带时间戳 JSON（应用私有目录；位置记忆原文由
         *    UserPreferences 迁移时自行备份，DataStore 异步读不适合放同步 migrate）。
         * 2. 建新表 → 逐行转换搬数据（旧单值 → 单元素 JSON 数组，null/空 → 空数组；转换用
         *    [RoomsJson.fromSingleRoom] 纯函数，JVM 单测可测）→ 删旧表 → 改名。
         *    minSdk 28 的 SQLite（3.22）无 DROP COLUMN，必须走建新表路径。
         */
        fun createMigration23(appContext: Context): Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ① 读取全部旧行（备份 + 搬数据共用）
                data class OldRow(
                    val id: Long, val name: String, val lat: Double, val lng: Double,
                    val accuracy: Float?, val note: String?, val courseId: String?,
                    val roomName: String?, val createdAt: Long
                )
                val oldRows = mutableListOf<OldRow>()
                db.query("SELECT id, name, latitude, longitude, accuracyRadiusMeters, note, linkedCourseId, linkedRoomName, createdAt FROM checkin_locations").use { cursor ->
                    while (cursor.moveToNext()) {
                        oldRows.add(
                            OldRow(
                                id = cursor.getLong(0),
                                name = cursor.getString(1),
                                lat = cursor.getDouble(2),
                                lng = cursor.getDouble(3),
                                accuracy = if (cursor.isNull(4)) null else cursor.getFloat(4),
                                note = if (cursor.isNull(5)) null else cursor.getString(5),
                                courseId = if (cursor.isNull(6)) null else cursor.getString(6),
                                roomName = if (cursor.isNull(7)) null else cursor.getString(7),
                                createdAt = cursor.getLong(8)
                            )
                        )
                    }
                }

                // ② 迁移前备份（grill W01 Q3：用户真实标定数据零损失的兜底）
                runCatching {
                    val backup = CheckInLocationBackup.serialize(
                        oldRows.map {
                            CheckInLocation(
                                id = it.id, name = it.name, latitude = it.lat, longitude = it.lng,
                                accuracyRadiusMeters = it.accuracy, note = it.note,
                                linkedCourseId = it.courseId,
                                linkedRoomNames = RoomsJson.decode(RoomsJson.fromSingleRoom(it.roomName)),
                                createdAt = it.createdAt
                            )
                        }
                    )
                    val file = File(
                        File(appContext.filesDir, "backup").apply { mkdirs() },
                        "checkin_locations_v2_${System.currentTimeMillis()}.json"
                    )
                    file.writeText(backup)
                    AppLogger.i(TAG, "[v126] 迁移前备份完成: ${file.name}（${oldRows.size} 条）")
                }.onFailure {
                    AppLogger.e(TAG, "[v126] 迁移前备份失败（继续迁移，数据在 Room 事务内）: ${it.message}")
                }

                // ③ 建新表 → 逐行搬 → 删旧 → 改名
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS checkin_locations_new (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        accuracyRadiusMeters REAL,
                        note TEXT,
                        linkedCourseId TEXT,
                        linkedRoomNames TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("DELETE FROM checkin_locations_new")
                oldRows.forEach { row ->
                    val roomsJson = RoomsJson.fromSingleRoom(row.roomName)
                    db.execSQL(
                        "INSERT INTO checkin_locations_new (id, name, latitude, longitude, accuracyRadiusMeters, note, linkedCourseId, linkedRoomNames, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        arrayOf(
                            row.id, row.name, row.lat, row.lng, row.accuracy, row.note,
                            row.courseId, roomsJson, row.createdAt
                        )
                    )
                }
                db.execSQL("DROP TABLE checkin_locations")
                db.execSQL("ALTER TABLE checkin_locations_new RENAME TO checkin_locations")
            }
        }

        /**
         * [优化] 数据库 Migration 列表
         * 当 schema 变更时，在此添加正式 Migration 而非销毁数据
         */
        private fun buildMigrations(context: Context): Array<Migration> = arrayOf(
            MIGRATION_1_2,
            createMigration23(context.applicationContext)
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
                    .addMigrations(*buildMigrations(context))
                    // [移除] .fallbackToDestructiveMigration() - 使用正式迁移保护数据
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private const val TAG = "AppDatabase"
    }
}
