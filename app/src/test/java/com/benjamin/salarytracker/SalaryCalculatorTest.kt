package com.benjamin.salarytracker

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class SalaryCalculatorTest {

    @Test
    fun testWeeklyOvertimeCalculations_PaidMode() {
        val job = Job(
            name = "Test Job",
            hourlyRateBrut = 10.0,
            weeklyContractHours = 35.0,
            overtimeMode = OvertimeMode.PAYEE
        )

        val monday = LocalDate.of(2023, 10, 2)
        val entries = listOf(
            DayEntry("1", job.id, monday, LocalTime.of(8, 0), LocalTime.of(18, 0), 60), // 9h
            DayEntry("2", job.id, monday.plusDays(1), LocalTime.of(8, 0), LocalTime.of(18, 0), 60), // 9h
            DayEntry("3", job.id, monday.plusDays(2), LocalTime.of(8, 0), LocalTime.of(18, 0), 60), // 9h
            DayEntry("4", job.id, monday.plusDays(3), LocalTime.of(8, 0), LocalTime.of(18, 0), 60), // 9h
            DayEntry("5", job.id, monday.plusDays(4), LocalTime.of(8, 0), LocalTime.of(18, 0), 60)  // 9h
        ) // Total 45h

        val stats = SalaryCalculator.calculateStats(job, entries)

        assertEquals(35.0, stats.normalHours, 0.01)
        assertEquals(8.0, stats.overtime25, 0.01)
        assertEquals(2.0, stats.overtime50, 0.01)
        assertEquals(480.0, stats.totalBrut, 0.01)
        assertEquals(0.0, stats.livretHours, 0.01)
    }

    @Test
    fun testWeeklyOvertimeCalculations_CapitalizedMode() {
        val job = Job(
            name = "Test Job",
            hourlyRateBrut = 10.0,
            weeklyContractHours = 35.0,
            overtimeMode = OvertimeMode.CAPITALISEE,
            livretThreshold = 43.0
        )

        val monday = LocalDate.of(2023, 10, 2)
        val entries = listOf(
            DayEntry("1", job.id, monday, LocalTime.of(8, 0), LocalTime.of(18, 0), 60), // 9h
            DayEntry("2", job.id, monday.plusDays(1), LocalTime.of(8, 0), LocalTime.of(18, 0), 60), // 9h
            DayEntry("3", job.id, monday.plusDays(2), LocalTime.of(8, 0), LocalTime.of(18, 0), 60), // 9h
            DayEntry("4", job.id, monday.plusDays(3), LocalTime.of(8, 0), LocalTime.of(18, 0), 60), // 9h
            DayEntry("5", job.id, monday.plusDays(4), LocalTime.of(8, 0), LocalTime.of(18, 0), 60)  // 9h
        ) // Total 45h

        val stats = SalaryCalculator.calculateStats(job, entries)

        // Threshold 43h. Total 45h.
        // Livret = 45 - 43 = 2h.
        // Effective Overtime for pay = (43 - 35) = 8h at +25%.
        assertEquals(35.0, stats.normalHours, 0.01)
        assertEquals(8.0, stats.overtime25, 0.01)
        assertEquals(0.0, stats.overtime50, 0.01)
        assertEquals(2.0, stats.livretHours, 0.01)
        
        // (35 * 10) + (8 * 12.5) = 350 + 100 = 450
        assertEquals(450.0, stats.totalBrut, 0.01)
    }
}
