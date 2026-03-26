package com.example.course_schedule_for_chd_v002.service.calendar

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import com.example.course_schedule_for_chd_v002.domain.model.Campus
import com.example.course_schedule_for_chd_v002.domain.model.Course
import com.example.course_schedule_for_chd_v002.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 系统日历同步服务
 *
 * 将课程同步到 Android 系统日历
 * [v98] 支持根据校区使用不同的上课时间
 */
class CalendarSyncService(private val context: Context) {

    companion object {
        private const val TAG = "CalendarSyncService"

        // 日历账户名称
        private const val CALENDAR_ACCOUNT_NAME = "course_schedule_chd"
        // 日历账户类型
        private const val CALENDAR_ACCOUNT_TYPE = CalendarContract.ACCOUNT_TYPE_LOCAL

        // 应用专用日历名称
        private const val CALENDAR_DISPLAY_NAME = "长安大学课程表"

        /**
         * [v98] 渭水校区上课时间配置
         * 第1节从 8:30 开始
         */
        val CLASS_TIMES_WEISHUI = mapOf(
            1 to Pair("08:30", "09:15"),   // 第1节 08:30-09:15
            2 to Pair("09:20", "10:05"),   // 第2节 09:20-10:05
            3 to Pair("10:25", "11:10"),   // 第3节 10:25-11:10 (大课间20分钟)
            4 to Pair("11:15", "12:00"),   // 第4节 11:15-12:00
            5 to Pair("14:00", "14:45"),   // 第5节 14:00-14:45
            6 to Pair("14:50", "15:35"),   // 第6节 14:50-15:35
            7 to Pair("15:55", "16:40"),   // 第7节 15:55-16:40 (大课间20分钟)
            8 to Pair("16:45", "17:30"),   // 第8节 16:45-17:30
            9 to Pair("19:00", "19:45"),   // 第9节 19:00-19:45
            10 to Pair("19:50", "20:35"),  // 第10节 19:50-20:35
            11 to Pair("20:40", "21:25")   // 第11节 20:40-21:25
        )

        /**
         * [v98] 本部校区上课时间配置
         * 第1节从 8:00 开始
         */
        val CLASS_TIMES_BENBU = mapOf(
            1 to Pair("08:00", "08:45"),   // 第1节 08:00-08:45
            2 to Pair("08:55", "09:40"),   // 第2节 08:55-09:40
            3 to Pair("10:10", "10:55"),   // 第3节 10:10-10:55 (大课间30分钟)
            4 to Pair("11:05", "11:50"),   // 第4节 11:05-11:50
            5 to Pair("14:00", "14:45"),   // 第5节 14:00-14:45
            6 to Pair("14:55", "15:40"),   // 第6节 14:55-15:40
            7 to Pair("16:00", "16:45"),   // 第7节 16:00-16:45
            8 to Pair("16:55", "17:40"),   // 第8节 16:55-17:40
            9 to Pair("19:00", "19:45"),   // 第9节 19:00-19:45
            10 to Pair("19:55", "20:40"),  // 第10节 19:55-20:40
            11 to Pair("20:50", "21:35")   // 第11节 20:50-21:35
        )

        /**
         * [v98] 根据校区获取上课时间配置
         */
        fun getClassTimes(campus: Campus): Map<Int, Pair<String, String>> {
            return when (campus) {
                Campus.WEISHUI -> CLASS_TIMES_WEISHUI
                Campus.BENBU -> CLASS_TIMES_BENBU
            }
        }
    }

    private val contentResolver: ContentResolver = context.contentResolver

    /**
     * 获取或创建应用专用日历
     * @return 日历ID，如果创建失败返回 null
     */
    suspend fun getOrCreateCalendarId(): Long? = withContext(Dispatchers.IO) {
        try {
            // 先查询是否已存在日历
            val existingId = queryCalendarId()
            if (existingId != null) {
                Log.i(TAG, "已存在应用日历: ID=$existingId")
                return@withContext existingId
            }

            // 创建新日历
            val newId = createCalendar()
            if (newId != null) {
                Log.i(TAG, "已创建应用专用日历: ID=$newId")
            }
            return@withContext newId
        } catch (e: Exception) {
            Log.e(TAG, "获取或创建日历失败", e)
            null
        }
    }

    /**
     * 查询已存在的应用日历
     */
    private fun queryCalendarId(): Long? {
        val uri: Uri = CalendarContract.Calendars.CONTENT_URI
        val projection = arrayOf(
            CalendarContract.Calendars._ID
        )
        val selection = "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ${CalendarContract.Calendars.ACCOUNT_TYPE} = ?"
        val selectionArgs = arrayOf(CALENDAR_ACCOUNT_NAME, CALENDAR_ACCOUNT_TYPE)

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                if (idIndex >= 0) {
                    return cursor.getLong(idIndex)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "查询日历失败", e)
        } finally {
            cursor?.close()
        }
        return null
    }

    /**
     * 创建新日历
     */
    private fun createCalendar(): Long? {
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, CALENDAR_ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CALENDAR_ACCOUNT_TYPE)
            put(CalendarContract.Calendars.NAME, CALENDAR_DISPLAY_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_DISPLAY_NAME)
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF4CAF50.toInt()) // 绿色
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, CALENDAR_ACCOUNT_NAME)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.VISIBLE, 1)
        }

        return try {
            val uri = CalendarContract.Calendars.CONTENT_URI
            // 使用异步插入方式
            val resultUri = contentResolver.insert(
                uri.buildUpon()
                    .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, CALENDAR_ACCOUNT_NAME)
                    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CALENDAR_ACCOUNT_TYPE)
                    .build(),
                values
            )
            val id = resultUri?.lastPathSegment?.toLongOrNull()
            Log.i(TAG, "创建日历结果: URI=$resultUri, ID=$id")
            id
        } catch (e: Exception) {
            Log.e(TAG, "创建日历失败", e)
            null
        }
    }

    /**
     * [v98] 同步课程到系统日历
     * [v99 Debug] 添加关键日期计算 debug 日志
     *
     * @param courses 课程列表
     * @param semesterStartDate 学期开始日期 (格式: yyyy-MM-dd)
     * @param campus 校区，默认渭水校区
     * @return 同步结果: Pair<成功数, 失败数>
     */
    suspend fun syncCoursesToCalendar(
        courses: List<Course>,
        semesterStartDate: String,
        campus: Campus = Campus.WEISHUI
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        Log.i(TAG, "========== [v99] syncCoursesToCalendar 开始 ==========")
        Log.i(TAG, "[v99 参数] 课程数: ${courses.size}")
        Log.i(TAG, "[v99 参数] 学期开始日期: $semesterStartDate")
        Log.i(TAG, "[v99 参数] 校区: ${campus.displayName}")

        if (courses.isEmpty() || semesterStartDate.isBlank()) {
            Log.w(TAG, "[v99] 课程列表或学期开始日期为空，跳过同步")
            return@withContext Pair(0, 0)
        }

        // 获取或创建日历
        val calendarId = getOrCreateCalendarId()
        if (calendarId == null) {
            Log.e(TAG, "[v99] 无法获取或创建日历")
            return@withContext Pair(0, courses.size)
        }
        Log.i(TAG, "[v99] 日历ID: $calendarId")

        // 删除该日历的所有旧事件
        deleteCalendarEvents(calendarId)
        Log.i(TAG, "[v99] 已删除日历中的旧事件")

        var successCount = 0
        var failCount = 0

        // [v98] 同步课程
        courses.forEach { course ->
            try {
                // 计算该课程的所有上课日期
                val courseDates = calculateCourseDates(course, semesterStartDate)
                if (courseDates.isEmpty()) {
                    Log.w(TAG, "[v99] 课程 ${course.name} 没有上课日期")
                    failCount++
                    return@forEach
                }

                // 为每个上课日期创建日历事件
                for ((date, startNode, endNode) in courseDates) {
                    // [v98] 传入校区参数
                    val created = createCalendarEvent(course, date, startNode, endNode, calendarId, campus)
                    if (created) {
                        successCount++
                    } else {
                        failCount++
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "[v99] 同步课程 ${course.name} 到日历失败", e)
                failCount++
            }
        }

        Log.i(TAG, "[v99] 同步完成: 成功 $successCount, 失败 $failCount")
        Log.i(TAG, "========== [v99] syncCoursesToCalendar 结束 ==========")
        return@withContext Pair(successCount, failCount)
    }

    /**
     * 计算课程的所有上课日期
     * [v98] 修复：使用 getActiveWeeks() 获取实际有课的周次，而非简单遍历范围
     * [v99 Debug] 添加关键日期计算 debug 日志
     *
     * @param course 课程
     * @param semesterStartDate 学期开始日期
     * @return 课程的上课日期列表，包含日期、开始节次、结束节次
     */
    private fun calculateCourseDates(
        course: Course,
        semesterStartDate: String
    ): List<Triple<LocalDate, Int, Int>> {
        Log.i(TAG, "========== [v99] calculateCourseDates 开始 ==========")
        Log.i(TAG, "[v99 课程] 名称: ${course.name}")
        Log.i(TAG, "[v99 课程] 星期: ${course.dayOfWeek.value} (1=周一, 7=周日)")
        Log.i(TAG, "[v99 课程] 节次: ${course.startNode}-${course.endNode}")
        Log.i(TAG, "[v99 课程] 周次范围: ${course.startWeek}-${course.endWeek}")
        Log.i(TAG, "[v99 参数] 学期开始日期: $semesterStartDate")

        val result = mutableListOf<Triple<LocalDate, Int, Int>>()

        try {
            // 计算课程在该周的日期（dayOfWeek 1=周一，7=周日）
            val courseDayOfWeek = course.dayOfWeek.value // 获取枚举的Int值

            // [v98] 获取课程的实际活跃周（使用位图精确判断）
            val activeWeeks = course.getActiveWeeks()

            // 如果有位图信息，使用活跃周列表；否则回退到范围遍历
            val weeksToProcess = if (activeWeeks.isNotEmpty()) {
                Log.i(TAG, "[v99 活跃周] 使用位图，列表: $activeWeeks")
                activeWeeks
            } else {
                Log.i(TAG, "[v99 活跃周] 无位图，使用范围: ${course.startWeek}..${course.endWeek}")
                (course.startWeek..course.endWeek).toList()
            }

            // 为每一周创建一个事件
            for (week in weeksToProcess) {
                // 计算该周的日期
                val weekStart = TimeUtils.calculateWeekStartDate(semesterStartDate, week)
                Log.i(TAG, "[v99 日期计算] 第${week}周 -> 周一: $weekStart (学期开始=$semesterStartDate)")

                if (weekStart != null) {
                    val date = weekStart.plusDays((courseDayOfWeek - 1).toLong())
                    result.add(Triple(date, course.startNode, course.endNode))
                    Log.i(TAG, "[v99 日期计算] 第${week}周周${courseDayOfWeek} -> 课程日期: $date")
                } else {
                    Log.w(TAG, "[v99 日期计算] 第${week}周周一计算失败，跳过")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[v99] 计算课程日期失败", e)
        }

        Log.i(TAG, "[v99] 课程 ${course.name} 共 ${result.size} 个上课日期")
        Log.i(TAG, "========== [v99] calculateCourseDates 结束 ==========")
        return result
    }

    /**
     * [v98] 为单个课程创建日历事件
     * [v99 Debug] 添加关键时间计算 debug 日志
     *
     * @param course 课程
     * @param date 上课日期
     * @param startNode 开始节次
     * @param endNode 结束节次
     * @param calendarId 日历ID
     * @param campus 校区，决定上课时间
     * @return 是否创建成功
     */
    private fun createCalendarEvent(
        course: Course,
        date: LocalDate,
        startNode: Int,
        endNode: Int,
        calendarId: Long,
        campus: Campus
    ): Boolean {
        Log.i(TAG, "========== [v99] createCalendarEvent 开始 ==========")
        Log.i(TAG, "[v99 事件] 课程: ${course.name}")
        Log.i(TAG, "[v99 事件] 日期: $date")
        Log.i(TAG, "[v99 事件] 节次: $startNode-$endNode")
        Log.i(TAG, "[v99 事件] 校区: ${campus.displayName}")

        // [v98] 根据校区获取开始和结束时间
        val classTimes = getClassTimes(campus)
        val startTimeStr = classTimes[startNode]?.first ?: "08:00"
        val endTimeStr = classTimes[endNode]?.second ?: "08:45"
        Log.i(TAG, "[v99 时间] 第${startNode}节: $startTimeStr, 第${endNode}节结束: $endTimeStr")

        val startTime = LocalTime.parse(startTimeStr)
        val endTime = LocalTime.parse(endTimeStr)

        val startDateTime = date.atTime(startTime)
        val endDateTime = date.atTime(endTime)
        Log.i(TAG, "[v99 最终时间] 开始: $startDateTime, 结束: $endDateTime")

        val timeZone = ZoneId.systemDefault()
        val startMillis = startDateTime.atZone(timeZone).toInstant().toEpochMilli()
        val endMillis = endDateTime.atZone(timeZone).toInstant().toEpochMilli()
        Log.i(TAG, "[v99 时间戳] 开始: $startMillis, 结束: $endMillis")

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, course.name)
            put(CalendarContract.Events.DESCRIPTION, course.remark ?: "")
            put(CalendarContract.Events.EVENT_LOCATION, course.location)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, timeZone.id)
            put(CalendarContract.Events.HAS_ALARM, 1)
        }

        return try {
            val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            if (uri != null) {
                Log.i(TAG, "[v99 成功] 创建事件: ${course.name} @ $date $startTimeStr-$endTimeStr")
                Log.i(TAG, "========== [v99] createCalendarEvent 成功 ==========")
                true
            } else {
                Log.w(TAG, "[v99 失败] 创建事件返回空URI: ${course.name}")
                Log.i(TAG, "========== [v99] createCalendarEvent 失败 ==========")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "[v99 异常] 创建日历事件失败: ${course.name}", e)
            Log.i(TAG, "========== [v99] createCalendarEvent 异常 ==========")
            false
        }
    }

    /**
     * 删除指定日历的所有事件
     */
    private fun deleteCalendarEvents(calendarId: Long) {
        try {
            val selection = "${CalendarContract.Events.CALENDAR_ID} = ?"
            val selectionArgs = arrayOf(calendarId.toString())
            contentResolver.delete(CalendarContract.Events.CONTENT_URI, selection, selectionArgs)
            Log.i(TAG, "已删除日历 $calendarId 的所有事件")
        } catch (e: Exception) {
            Log.e(TAG, "删除日历事件失败", e)
        }
    }

    /**
     * 删除应用创建的日历
     */
    suspend fun deleteCalendar(): Boolean = withContext(Dispatchers.IO) {
        try {
            val calendarId = queryCalendarId()
            if (calendarId != null) {
                val selection = "${CalendarContract.Calendars._ID} = ?"
                val selectionArgs = arrayOf(calendarId.toString())
                val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
                    .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, CALENDAR_ACCOUNT_NAME)
                    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CALENDAR_ACCOUNT_TYPE)
                    .build()
                val deleted = contentResolver.delete(uri, selection, selectionArgs)
                Log.i(TAG, "删除日历结果: $deleted")
                deleted > 0
            } else {
                Log.i(TAG, "没有找到应用日历")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "删除日历失败", e)
            false
        }
    }
}
