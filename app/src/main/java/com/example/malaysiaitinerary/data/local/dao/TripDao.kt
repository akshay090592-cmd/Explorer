package com.example.malaysiaitinerary.data.local.dao

import androidx.room.*
import com.example.malaysiaitinerary.data.local.entity.Trip
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY createdAt DESC")
    fun getAllTrips(): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE isActive = 1 LIMIT 1")
    fun getActiveTrip(): Flow<Trip?>

    @Query("SELECT * FROM trips WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveTripSync(): Trip?

    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1")
    suspend fun getTripById(id: Long): Trip?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: Trip): Long

    @Query("UPDATE trips SET isActive = 0")
    suspend fun deactivateAllTrips()

    @Query("UPDATE trips SET isActive = 1 WHERE id = :tripId")
    suspend fun activateTripInternal(tripId: Long)

    @Transaction
    suspend fun setActiveTrip(tripId: Long) {
        deactivateAllTrips()
        activateTripInternal(tripId)
    }

    @Query("UPDATE trips SET startDate = :startDate, endDate = :endDate WHERE id = :tripId")
    suspend fun updateTripDates(tripId: Long, startDate: String, endDate: String)

    @Query("UPDATE trips SET name = :name, destination = :destination, startDate = :startDate, endDate = :endDate WHERE id = :tripId")
    suspend fun updateTripDetails(tripId: Long, name: String, destination: String, startDate: String, endDate: String)

    @Query("DELETE FROM trips WHERE id = :tripId")
    suspend fun deleteTrip(tripId: Long)

    @Query("SELECT COUNT(*) FROM trips")
    suspend fun getTripCount(): Int
}
