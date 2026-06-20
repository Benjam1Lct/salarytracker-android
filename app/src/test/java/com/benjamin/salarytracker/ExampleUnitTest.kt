package com.benjamin.salarytracker

import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.time.LocalDate
import java.time.LocalTime

class ExampleUnitTest {
    @Test
    fun testSerialization() {
        val job = Job(
            id = "test-job-id",
            name = "Test Job",
            hourlyRateBrut = 15.0,
            startDate = LocalDate.of(2023, 1, 1),
            endDate = LocalDate.of(2023, 12, 31)
        )
        val entry = DayEntry(
            id = "test-entry-id",
            jobId = "test-job-id",
            date = LocalDate.of(2023, 6, 20),
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(17, 0),
            pauseMinutes = 60
        )
        
        // Let's mimic LocalDb structure
        // Since LocalDb is private to LocalDataService, we can test the models directly
        val bos = ByteArrayOutputStream()
        val oos = ObjectOutputStream(bos)
        
        try {
            println("Job class: ${job.javaClass.name}")
            println("Job interfaces: ${job.javaClass.interfaces.map { it.name }}")
            println("Is Job Serializable? ${job is java.io.Serializable}")
            oos.writeObject(job)
            oos.writeObject(entry)
            oos.flush()
            
            val bytes = bos.toByteArray()
            val bis = ByteArrayInputStream(bytes)
            val ois = ObjectInputStream(bis)
            
            val deserializedJob = ois.readObject() as Job
            val deserializedEntry = ois.readObject() as DayEntry
            
            assertEquals(job.id, deserializedJob.id)
            assertEquals(job.startDate, deserializedJob.startDate)
            assertEquals(entry.id, deserializedEntry.id)
            assertEquals(entry.date, deserializedEntry.date)
            println("Serialization test passed successfully!")
        } catch (e: Exception) {
            e.printStackTrace()
            fail("Serialization failed: ${e.message}")
        }
    }
}