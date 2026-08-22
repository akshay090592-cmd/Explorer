package com.example.malaysiaitinerary.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "itinerary_items")
data class ItineraryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String, // YYYY-MM-DD
    val title: String,
    val locationName: String,
    val startTime: String, // HH:mm
    val endTime: String? = null,
    val description: String,
    val type: String, // ACTIVITY, MEAL, TRANSIT, ACCOMMODATION
    val googleMapsUrl: String = "",
    val imageUrl: String? = null,
    val vegOptions: String? = null,
    val nonVegOptions: String? = null,
    val isDemo: Boolean = false,
    val tripId: Long = 1L
)

