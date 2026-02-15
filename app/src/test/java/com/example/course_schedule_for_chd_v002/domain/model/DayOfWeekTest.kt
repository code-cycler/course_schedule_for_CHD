package com.example.course_schedule_for_chd_v002.domain.model

import org.junit.Assert.*
import org.junit.Test

class DayOfWeekTest {

    @Test
    fun `fromValue returns correct day for valid value`() {
        assertEquals(DayOfWeek.MONDAY, DayOfWeek.fromValue(1))
        assertEquals(DayOfWeek.TUESDAY, DayOfWeek.fromValue(2))
        assertEquals(DayOfWeek.WEDNESDAY, DayOfWeek.fromValue(3))
        assertEquals(DayOfWeek.THURSDAY, DayOfWeek.fromValue(4))
        assertEquals(DayOfWeek.FRIDAY, DayOfWeek.fromValue(5))
        assertEquals(DayOfWeek.SATURDAY, DayOfWeek.fromValue(6))
        assertEquals(DayOfWeek.SUNDAY, DayOfWeek.fromValue(7))
    }

    @Test
    fun `fromValue returns MONDAY for invalid value`() {
        assertEquals(DayOfWeek.MONDAY, DayOfWeek.fromValue(0))
        assertEquals(DayOfWeek.MONDAY, DayOfWeek.fromValue(8))
        assertEquals(DayOfWeek.MONDAY, DayOfWeek.fromValue(-1))
    }

    @Test
    fun `value property returns correct integer`() {
        assertEquals(1, DayOfWeek.MONDAY.value)
        assertEquals(2, DayOfWeek.TUESDAY.value)
        assertEquals(7, DayOfWeek.SUNDAY.value)
    }

    @Test
    fun `entries contains all seven days`() {
        assertEquals(7, DayOfWeek.entries.size)
    }
}
