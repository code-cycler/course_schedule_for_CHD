package com.example.course_schedule_for_chd_v002.data.local.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.course_schedule_for_chd_v002.data.local.database.entity.CourseEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * CourseDao 单元测试
 * 测试 Room 数据库的 CRUD 操作
 */
@RunWith(AndroidJUnit4::class)
class CourseDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var courseDao: CourseDao

    @Before
    fun setup() {
        // 使用内存数据库，测试完成后自动清除
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries() // 仅用于测试
            .build()

        courseDao = database.courseDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    // ================ 插入测试 ================

    @Test
    fun insert_singleCourse_successfullyInserted() = runTest {
        // Given
        val course = createTestCourse(name = "高等数学", semester = "2024-2025-1")

        // When
        courseDao.insert(course)

        // Then
        val courses = courseDao.getBySemester("2024-2025-1")
        assertEquals(1, courses.size)
        assertEquals("高等数学", courses[0].name)
    }

    @Test
    fun insertAll_multipleCourses_successfullyInserted() = runTest {
        // Given
        val courses = listOf(
            createTestCourse(name = "高等数学", semester = "2024-2025-1"),
            createTestCourse(name = "大学英语", semester = "2024-2025-1"),
            createTestCourse(name = "线性代数", semester = "2024-2025-1")
        )

        // When
        courseDao.insertAll(courses)

        // Then
        val result = courseDao.getBySemester("2024-2025-1")
        assertEquals(3, result.size)
    }

    @Test
    fun insert_duplicateCourse_replacesExisting() = runTest {
        // Given
        val course1 = createTestCourse(id = 1, name = "高等数学", semester = "2024-2025-1")
        val course2 = createTestCourse(id = 1, name = "高等数学(修改)", semester = "2024-2025-1")

        // When
        courseDao.insert(course1)
        courseDao.insert(course2)

        // Then
        val courses = courseDao.getBySemester("2024-2025-1")
        assertEquals(1, courses.size)
        assertEquals("高等数学(修改)", courses[0].name)
    }

    // ================ 查询测试 ================

    @Test
    fun getBySemester_returnsCoursesForSemester() = runTest {
        // Given
        val courses2024_1 = listOf(
            createTestCourse(name = "高等数学", semester = "2024-2025-1"),
            createTestCourse(name = "大学英语", semester = "2024-2025-1")
        )
        val courses2024_2 = listOf(
            createTestCourse(name = "数据结构", semester = "2024-2025-2")
        )
        courseDao.insertAll(courses2024_1)
        courseDao.insertAll(courses2024_2)

        // When
        val result = courseDao.getBySemester("2024-2025-1")

        // Then
        assertEquals(2, result.size)
        assertTrue(result.all { it.semester == "2024-2025-1" })
    }

    @Test
    fun getBySemester_emptyResultForNonExistentSemester() = runTest {
        // Given
        courseDao.insert(createTestCourse(semester = "2024-2025-1"))

        // When
        val result = courseDao.getBySemester("2023-2024-2")

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun getBySemester_sortedByDayAndNode() = runTest {
        // Given
        val courses = listOf(
            createTestCourse(name = "周三课程", dayOfWeek = 3, startNode = 1, semester = "2024-2025-1"),
            createTestCourse(name = "周一课程1", dayOfWeek = 1, startNode = 3, semester = "2024-2025-1"),
            createTestCourse(name = "周一课程2", dayOfWeek = 1, startNode = 1, semester = "2024-2025-1")
        )
        courseDao.insertAll(courses)

        // When
        val result = courseDao.getBySemester("2024-2025-1")

        // Then
        assertEquals(3, result.size)
        // 应该按 dayOfWeek, startNode 排序
        assertEquals("周一课程2", result[0].name) // day=1, node=1
        assertEquals("周一课程1", result[1].name) // day=1, node=3
        assertEquals("周三课程", result[2].name)   // day=3
    }

    @Test
    fun getById_returnsCorrectCourse() = runTest {
        // Given
        val course = createTestCourse(id = 100, name = "测试课程")
        courseDao.insert(course)

        // When
        val result = courseDao.getById(100)

        // Then
        assertNotNull(result)
        assertEquals("测试课程", result?.name)
    }

    @Test
    fun getById_returnsNullForNonExistentId() = runTest {
        // When
        val result = courseDao.getById(999)

        // Then
        assertNull(result)
    }

    @Test
    fun getAllSemesters_returnsDistinctSemesters() = runTest {
        // Given
        courseDao.insertAll(listOf(
            createTestCourse(semester = "2024-2025-2"),
            createTestCourse(semester = "2024-2025-1"),
            createTestCourse(semester = "2024-2025-2"),
            createTestCourse(semester = "2023-2024-2")
        ))

        // When
        val result = courseDao.getAllSemesters()

        // Then
        assertEquals(3, result.size)
        // 应该是降序排列
        assertEquals("2024-2025-2", result[0])
    }

    // ================ 删除测试 ================

    @Test
    fun deleteBySemester_removesOnlyTargetSemester() = runTest {
        // Given
        courseDao.insertAll(listOf(
            createTestCourse(semester = "2024-2025-1"),
            createTestCourse(semester = "2024-2025-2")
        ))

        // When
        courseDao.deleteBySemester("2024-2025-1")

        // Then
        val sem1 = courseDao.getBySemester("2024-2025-1")
        val sem2 = courseDao.getBySemester("2024-2025-2")
        assertTrue(sem1.isEmpty())
        assertEquals(1, sem2.size)
    }

    @Test
    fun deleteAll_removesAllCourses() = runTest {
        // Given
        courseDao.insertAll(listOf(
            createTestCourse(semester = "2024-2025-1"),
            createTestCourse(semester = "2024-2025-2"),
            createTestCourse(semester = "2023-2024-1")
        ))

        // When
        courseDao.deleteAll()

        // Then
        val allSemesters = courseDao.getAllSemesters()
        assertTrue(allSemesters.isEmpty())
    }

    // ================ 辅助方法 ================

    private fun createTestCourse(
        id: Long = 0,
        name: String = "测试课程",
        teacher: String = "张老师",
        location: String = "A101",
        dayOfWeek: Int = 1,
        startWeek: Int = 1,
        endWeek: Int = 16,
        startNode: Int = 1,
        endNode: Int = 2,
        courseType: String = "必修",
        credit: Double = 2.0,
        remark: String = "",
        semester: String = "2024-2025-1"
    ): CourseEntity {
        return CourseEntity(
            id = id,
            name = name,
            teacher = teacher,
            location = location,
            dayOfWeek = dayOfWeek,
            startWeek = startWeek,
            endWeek = endWeek,
            startNode = startNode,
            endNode = endNode,
            courseType = courseType,
            credit = credit,
            remark = remark,
            semester = semester
        )
    }
}
