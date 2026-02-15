package com.example.course_schedule_for_chd_v002.domain.model

import org.junit.Assert.*
import org.junit.Test

class SemesterTest {

    @Test
    fun `toString returns correct format`() {
        val semester = Semester(2024, 1)
        assertEquals("2024-2025-1", semester.toString())
    }

    @Test
    fun `toString returns correct format for second term`() {
        val semester = Semester(2024, 2)
        assertEquals("2024-2025-2", semester.toString())
    }

    @Test
    fun `fromString parses valid semester string`() {
        val semester = Semester.fromString("2024-2025-1")
        assertEquals(2024, semester.year)
        assertEquals(1, semester.term)
    }

    @Test
    fun `fromString parses second term`() {
        val semester = Semester.fromString("2023-2024-2")
        assertEquals(2023, semester.year)
        assertEquals(2, semester.term)
    }

    @Test
    fun `fromString returns default for invalid format`() {
        val semester = Semester.fromString("invalid")
        assertEquals(2024, semester.year)
        assertEquals(1, semester.term)
    }

    @Test
    fun `fromString returns default for incomplete format`() {
        val semester = Semester.fromString("2024-2025")
        assertEquals(2024, semester.year)
        assertEquals(1, semester.term)
    }
}
