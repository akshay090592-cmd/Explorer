package com.example.malaysiaitinerary.data.local.dao

import androidx.room.*
import com.example.malaysiaitinerary.data.local.entity.WeatherLog
import kotlinx.coroutines.flow.Flow

@Dao
interface WeatherDao {
    @Query("SELECT * FROM weather_logs WHERE date = :date")
    suspend fun getWeatherForDate(date: String): WeatherLog?

    @Query("SELECT * FROM weather_logs ORDER BY date ASC")
    fun getAllWeather(): Flow<List<WeatherLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeather(weather: WeatherLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(weatherList: List<WeatherLog>)
}
