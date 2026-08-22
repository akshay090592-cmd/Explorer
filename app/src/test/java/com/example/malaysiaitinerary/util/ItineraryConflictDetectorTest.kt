package com.example.malaysiaitinerary.util

import com.example.malaysiaitinerary.data.local.entity.ItineraryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class ItineraryConflictDetectorTest {

    private fun createTestItem(
        id: Int,
        date: String,
        startTime: String,
        title: String = "Test Activity"
    ): ItineraryItem {
        return ItineraryItem(
            id = id,
            date = date,
            title = title,
            locationName = "Location $id",
            startTime = startTime,
            description = "Description",
            googleMapsUrl = "",
            type = "ACTIVITY"
        )
    }

    @Test
    fun parseTime_valid24HourFormats_parsesCorrectly() {
        val t1 = ItineraryConflictDetector.parseTime("14:30")
        assertEquals(LocalTime.of(14, 30), t1)

        val t2 = ItineraryConflictDetector.parseTime("09:00")
        assertEquals(LocalTime.of(9, 0), t2)
    }

    @Test
    fun parseTime_valid12HourAmPmFormats_parsesCorrectly() {
        val t1 = ItineraryConflictDetector.parseTime("2:30 PM")
        assertEquals(LocalTime.of(14, 30), t1)

        val t2 = ItineraryConflictDetector.parseTime("09:15 AM")
        assertEquals(LocalTime.of(9, 15), t2)
    }

    @Test
    fun parseTime_timeRangeFormat_parsesStartTime() {
        val t1 = ItineraryConflictDetector.parseTime("10:00 AM - 12:00 PM")
        assertEquals(LocalTime.of(10, 0), t1)

        val t2 = ItineraryConflictDetector.parseTime("14:00 - 16:00")
        assertEquals(LocalTime.of(14, 0), t2)
    }

    @Test
    fun findConflictingItemIds_overlappingItemsSameDate_flagsBothItems() {
        val item1 = createTestItem(1, "2026-03-24", "10:00 AM", "Batu Caves Tour")
        val item2 = createTestItem(2, "2026-03-24", "10:30 AM", "KL Tower Sky Deck") // Overlaps default 60 min window
        val item3 = createTestItem(3, "2026-03-24", "16:00", "Evening Jalan Alor Dinner") // 4 hours later, no overlap

        val conflictingIds = ItineraryConflictDetector.findConflictingItemIds(listOf(item1, item2, item3))

        assertEquals(2, conflictingIds.size)
        assertTrue(conflictingIds.contains(1))
        assertTrue(conflictingIds.contains(2))
        assertFalse(conflictingIds.contains(3))
    }

    @Test
    fun findConflictingItemIds_overlappingTimeDifferentDates_noConflict() {
        val item1 = createTestItem(1, "2026-03-24", "10:00 AM", "Batu Caves")
        val item2 = createTestItem(2, "2026-03-25", "10:00 AM", "Penang Street Art") // Same time, different date

        val conflictingIds = ItineraryConflictDetector.findConflictingItemIds(listOf(item1, item2))

        assertTrue(conflictingIds.isEmpty())
    }

    @Test
    fun findConflictingItemIds_distinctNonOverlappingTimes_noConflict() {
        val item1 = createTestItem(1, "2026-03-24", "09:00 AM", "Breakfast")
        val item2 = createTestItem(2, "2026-03-24", "12:00 PM", "Lunch")
        val item3 = createTestItem(3, "2026-03-24", "15:00", "Sightseeing")
        val item4 = createTestItem(4, "2026-03-24", "19:00", "Dinner")

        val conflictingIds = ItineraryConflictDetector.findConflictingItemIds(listOf(item1, item2, item3, item4))

        assertTrue(conflictingIds.isEmpty())
    }
}
