package com.example.malaysiaitinerary.data.repository

import com.example.malaysiaitinerary.data.local.dao.DocumentDao
import com.example.malaysiaitinerary.data.local.dao.TripDao
import com.example.malaysiaitinerary.data.local.entity.Document
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest

class DocumentRepository(
    private val documentDao: DocumentDao,
    private val tripDao: TripDao? = null
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val allDocuments: Flow<List<Document>> = if (tripDao != null) {
        tripDao.getActiveTrip().flatMapLatest { activeTrip ->
            val tripId = activeTrip?.id ?: 1L
            documentDao.getDocumentsForTrip(tripId)
        }
    } else {
        documentDao.getAllDocuments()
    }

    fun getDocumentsForTrip(tripId: Long): Flow<List<Document>> {
        return documentDao.getDocumentsForTrip(tripId)
    }

    fun getDocumentsByItineraryItem(itemId: Int): Flow<List<Document>> {
        return documentDao.getDocumentsByItineraryItem(itemId)
    }

    suspend fun insertDocument(document: Document): Long {
        return documentDao.insertDocument(document)
    }

    suspend fun deleteDocument(document: Document) {
        documentDao.deleteDocument(document)
    }

    suspend fun deleteDemoDocuments() {
        documentDao.deleteDemoDocuments()
    }

    suspend fun clearAll() {
        documentDao.deleteAll()
    }
}

