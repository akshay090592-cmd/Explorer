package com.example.malaysiaitinerary.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class Document(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val filePath: String,
    val type: String, // FLIGHT, HOTEL, PASSPORT, TICKET
    val date: String? = null, // YYYY-MM-DD
    val location: String? = null,
    val itineraryItemId: Int? = null, // Link to activity if it's a ticket
    val isDemo: Boolean = false,
    val tripId: Long = 1L
)

