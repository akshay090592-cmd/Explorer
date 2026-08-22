package com.example.malaysiaitinerary.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val currency: String, // e.g. "MYR", "USD", "INR"
    val convertedAmountMYR: Double,
    val convertedAmountINR: Double,
    val category: String, // "Food", "Transport", "Shopping", "Other"
    val date: String, // "YYYY-MM-DD"
    val description: String,
    val itineraryItemId: Int? = null,
    val isDemo: Boolean = false,
    val tripId: Long = 1L
)

