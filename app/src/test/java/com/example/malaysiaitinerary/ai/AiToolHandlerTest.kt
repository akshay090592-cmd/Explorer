package com.example.malaysiaitinerary.ai

import com.example.malaysiaitinerary.data.local.dao.DocumentDao
import com.example.malaysiaitinerary.data.local.dao.ExpenseDao
import com.example.malaysiaitinerary.data.local.dao.ItineraryDao
import com.example.malaysiaitinerary.data.local.entity.Document
import com.example.malaysiaitinerary.data.local.entity.Expense
import com.example.malaysiaitinerary.data.local.entity.ItineraryItem
import com.example.malaysiaitinerary.data.repository.DocumentRepository
import com.example.malaysiaitinerary.data.repository.ExpenseRepository
import com.example.malaysiaitinerary.data.repository.ItineraryRepository
import com.example.malaysiaitinerary.util.LocationImageFetcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeItineraryDao : ItineraryDao {
    private val list = mutableListOf<ItineraryItem>()
    override fun getAllItems(): Flow<List<ItineraryItem>> = flowOf(list)
    override fun getItemsForTrip(tripId: Long): Flow<List<ItineraryItem>> = flowOf(list.filter { it.tripId == tripId })
    override suspend fun getItemsForTripSync(tripId: Long): List<ItineraryItem> = list.filter { it.tripId == tripId }
    override fun getItemsByDate(date: String): Flow<List<ItineraryItem>> = flowOf(list.filter { it.date == date })
    override suspend fun insertItems(items: List<ItineraryItem>) { list.addAll(items) }
    override suspend fun insertItem(item: ItineraryItem): Long { list.add(item); return list.size.toLong() }
    override suspend fun updateItem(item: ItineraryItem) {}
    override suspend fun deleteItem(item: ItineraryItem) {}
    override suspend fun deleteItemsForTrip(tripId: Long) { list.removeAll { it.tripId == tripId } }
    override suspend fun deleteAll() { list.clear() }
    override suspend fun deleteDemoItems() { list.removeAll { it.isDemo } }
    override suspend fun getItemCount(): Int = list.size
    override suspend fun getNonDemoCount(): Int = list.count { !it.isDemo }
    override fun getNonDemoCountFlow(): Flow<Int> = flowOf(list.count { !it.isDemo })
}

class FakeDocumentDao : DocumentDao {
    private val list = mutableListOf<Document>()
    override fun getAllDocuments(): Flow<List<Document>> = flowOf(list)
    override fun getDocumentsForTrip(tripId: Long): Flow<List<Document>> = flowOf(list.filter { it.tripId == tripId })
    override suspend fun getDocumentsForTripSync(tripId: Long): List<Document> = list.filter { it.tripId == tripId }
    override fun getDocumentsByItineraryItem(itemId: Int): Flow<List<Document>> = flowOf(list.filter { it.itineraryItemId == itemId })
    override suspend fun insertDocument(document: Document): Long { list.add(document); return list.size.toLong() }
    override suspend fun deleteDocument(document: Document) {}
    override suspend fun deleteDocumentsForTrip(tripId: Long) { list.removeAll { it.tripId == tripId } }
    override suspend fun deleteAll() { list.clear() }
    override suspend fun deleteDemoDocuments() { list.removeAll { it.isDemo } }
}

class FakeExpenseDao : ExpenseDao {
    private val list = mutableListOf<Expense>()
    override fun getAllExpenses(): Flow<List<Expense>> = flowOf(list)
    override fun getExpensesForTrip(tripId: Long): Flow<List<Expense>> = flowOf(list.filter { it.tripId == tripId })
    override suspend fun getExpensesForTripSync(tripId: Long): List<Expense> = list.filter { it.tripId == tripId }
    override fun getTotalSpentMYRForTrip(tripId: Long): Flow<Double?> = flowOf(100.0)
    override fun getTotalSpentINRForTrip(tripId: Long): Flow<Double?> = flowOf(2000.0)
    override suspend fun insertExpense(expense: Expense) { list.add(expense) }
    override suspend fun deleteExpense(expense: Expense) {}
    override suspend fun deleteExpensesForTrip(tripId: Long) { list.removeAll { it.tripId == tripId } }
    override suspend fun deleteAll() { list.clear() }
    override suspend fun deleteDemoExpenses() { list.removeAll { it.isDemo } }
}

class FakeLocationImageFetcher : LocationImageFetcher() {
    override suspend fun fetchImageForLocation(locationName: String, googleMapsUrl: String?): String? {
        return "https://images.wikimedia.org/sample_$locationName.jpg"
    }
}

class AiToolHandlerTest {

    @Test
    fun executeToolCall_addItineraryItem_success() = runTest {
        val itineraryRepo = ItineraryRepository(FakeItineraryDao())
        val documentRepo = DocumentRepository(FakeDocumentDao())
        val expenseRepo = ExpenseRepository(FakeExpenseDao())
        val toolHandler = AiToolHandler(
            itineraryRepository = itineraryRepo,
            documentRepository = documentRepo,
            expenseRepository = expenseRepo,
            tripRepository = null,
            imageFetcher = FakeLocationImageFetcher()
        )

        val toolCall = AiToolCall(
            toolName = "addItineraryItem",
            arguments = mapOf(
                "date" to "2026-03-24",
                "title" to "Visit Petronas Towers",
                "location" to "Petronas Twin Towers",
                "startTime" to "10:00",
                "description" to "Photo session and observation deck",
                "type" to "ACTIVITY"
            )
        )

        val result = toolHandler.executeToolCall(toolCall)
        assertTrue(result.contains("Added itinerary item") || result.contains("Petronas"))
    }
}
