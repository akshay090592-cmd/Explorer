package com.example.malaysiaitinerary.data.repository

import com.example.malaysiaitinerary.data.local.dao.ItineraryDao
import com.example.malaysiaitinerary.data.local.dao.TripDao
import com.example.malaysiaitinerary.data.local.entity.ItineraryItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest

class ItineraryRepository(
    private val itineraryDao: ItineraryDao,
    private val tripDao: TripDao? = null
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val allItems: Flow<List<ItineraryItem>> = if (tripDao != null) {
        tripDao.getActiveTrip().flatMapLatest { activeTrip ->
            val tripId = activeTrip?.id ?: 1L
            itineraryDao.getItemsForTrip(tripId)
        }
    } else {
        itineraryDao.getAllItems()
    }

    fun getItemsForTrip(tripId: Long): Flow<List<ItineraryItem>> {
        return itineraryDao.getItemsForTrip(tripId)
    }

    fun getItemsByDate(date: String): Flow<List<ItineraryItem>> {
        return itineraryDao.getItemsByDate(date)
    }

    suspend fun insertItems(items: List<ItineraryItem>) {
        itineraryDao.insertItems(items)
    }

    suspend fun insertItem(item: ItineraryItem): Long {
        return itineraryDao.insertItem(item)
    }

    suspend fun updateItem(item: ItineraryItem) {
        itineraryDao.updateItem(item)
    }

    suspend fun deleteItem(item: ItineraryItem) {
        itineraryDao.deleteItem(item)
    }

    suspend fun deleteDemoItems() {
        itineraryDao.deleteDemoItems()
    }

    suspend fun clearAll() {
        itineraryDao.deleteAll()
    }

    fun getNonDemoCountFlow(): Flow<Int> = itineraryDao.getNonDemoCountFlow()
}

