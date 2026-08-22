package com.example.malaysiaitinerary.data.local.dao

import androidx.room.*
import com.example.malaysiaitinerary.data.local.entity.ItineraryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ItineraryDao {
    @Query("SELECT * FROM itinerary_items ORDER BY date ASC, startTime ASC")
    fun getAllItems(): Flow<List<ItineraryItem>>

    @Query("SELECT * FROM itinerary_items WHERE tripId = :tripId ORDER BY date ASC, startTime ASC")
    fun getItemsForTrip(tripId: Long): Flow<List<ItineraryItem>>

    @Query("SELECT * FROM itinerary_items WHERE tripId = :tripId ORDER BY date ASC, startTime ASC")
    suspend fun getItemsForTripSync(tripId: Long): List<ItineraryItem>

    @Query("SELECT * FROM itinerary_items WHERE date = :date ORDER BY startTime ASC")
    fun getItemsByDate(date: String): Flow<List<ItineraryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ItineraryItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ItineraryItem): Long

    @Update
    suspend fun updateItem(item: ItineraryItem)

    @Delete
    suspend fun deleteItem(item: ItineraryItem)

    @Query("DELETE FROM itinerary_items")
    suspend fun deleteAll()

    @Query("DELETE FROM itinerary_items WHERE isDemo = 1")
    suspend fun deleteDemoItems()

    @Query("DELETE FROM itinerary_items WHERE tripId = :tripId")
    suspend fun deleteItemsForTrip(tripId: Long)

    @Query("SELECT COUNT(*) FROM itinerary_items")
    suspend fun getItemCount(): Int

    @Query("SELECT COUNT(*) FROM itinerary_items WHERE isDemo = 0")
    suspend fun getNonDemoCount(): Int

    @Query("SELECT COUNT(*) FROM itinerary_items WHERE isDemo = 0")
    fun getNonDemoCountFlow(): kotlinx.coroutines.flow.Flow<Int>
}

