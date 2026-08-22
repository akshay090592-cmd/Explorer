package com.example.malaysiaitinerary.data.repository

import com.example.malaysiaitinerary.data.local.dao.WeatherDao
import com.example.malaysiaitinerary.data.local.entity.WeatherLog
import kotlinx.coroutines.flow.Flow

class WeatherRepository(private val weatherDao: WeatherDao) {
    val allWeather: Flow<List<WeatherLog>> = weatherDao.getAllWeather()

    suspend fun getWeatherForDate(date: String): WeatherLog? {
        return weatherDao.getWeatherForDate(date)
    }

    suspend fun insertWeather(weather: WeatherLog) {
        weatherDao.insertWeather(weather)
    }

    suspend fun insertAll(weatherList: List<WeatherLog>) {
        weatherDao.insertAll(weatherList)
    }
}
