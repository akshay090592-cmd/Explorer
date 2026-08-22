package com.example.malaysiaitinerary.data.local.dao

import androidx.room.*
import com.example.malaysiaitinerary.data.local.entity.Document
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY date DESC, id DESC")
    fun getAllDocuments(): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE tripId = :tripId ORDER BY date DESC, id DESC")
    fun getDocumentsForTrip(tripId: Long): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE tripId = :tripId ORDER BY date DESC, id DESC")
    suspend fun getDocumentsForTripSync(tripId: Long): List<Document>

    @Query("SELECT * FROM documents WHERE itineraryItemId = :itemId")
    fun getDocumentsByItineraryItem(itemId: Int): Flow<List<Document>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: Document): Long

    @Delete
    suspend fun deleteDocument(document: Document)

    @Query("DELETE FROM documents")
    suspend fun deleteAll()

    @Query("DELETE FROM documents WHERE isDemo = 1")
    suspend fun deleteDemoDocuments()

    @Query("DELETE FROM documents WHERE tripId = :tripId")
    suspend fun deleteDocumentsForTrip(tripId: Long)
}

