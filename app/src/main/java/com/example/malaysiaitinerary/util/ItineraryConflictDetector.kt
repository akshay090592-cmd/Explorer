package com.example.malaysiaitinerary.util

import com.example.malaysiaitinerary.data.local.entity.ItineraryItem
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object ItineraryConflictDetector {

    private val TIME_FORMATTERS = listOf(
        DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("H:mm", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    )

    fun parseTime(raw: String): LocalTime? {
        val firstPart = raw.split("-")[0].trim()
        for (formatter in TIME_FORMATTERS) {
            try {
                return LocalTime.parse(firstPart, formatter)
            } catch (_: Exception) {
            }
        }
        return null
    }

    /**
     * Finds items on the same day that overlap in time (assuming average default duration of 60 min if unspecified).
     */
    fun findConflictingItemIds(items: List<ItineraryItem>): Set<Int> {
        val conflictingIds = mutableSetOf<Int>()
        val groupedByDate = items.groupBy { it.date }

        for ((_, dayItems) in groupedByDate) {
            val timedItems = dayItems.mapNotNull { item ->
                val time = parseTime(item.startTime)
                if (time != null) item to time else null
            }.sortedBy { it.second }

            for (i in 0 until timedItems.size) {
                val (item1, time1) = timedItems[i]
                val end1 = time1.plusMinutes(60) // default 60 min window

                for (j in i + 1 until timedItems.size) {
                    val (item2, time2) = timedItems[j]
                    if (time2.isBefore(end1)) {
                        conflictingIds.add(item1.id)
                        conflictingIds.add(item2.id)
                    }
                }
            }
        }

        return conflictingIds
    }
}
