package com.example.malaysiaitinerary.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weather_logs")
data class WeatherLog(
    @PrimaryKey val date: String, // "YYYY-MM-DD"
    val tempMax: Double,
    val tempMin: Double,
    val weatherCode: Int,
    val lastUpdated: Long
)
