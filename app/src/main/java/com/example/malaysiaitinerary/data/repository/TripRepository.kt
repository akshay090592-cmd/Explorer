package com.example.malaysiaitinerary.data.repository

import com.example.malaysiaitinerary.data.local.dao.DocumentDao
import com.example.malaysiaitinerary.data.local.dao.ExpenseDao
import com.example.malaysiaitinerary.data.local.dao.ItineraryDao
import com.example.malaysiaitinerary.data.local.dao.TripDao
import com.example.malaysiaitinerary.data.local.entity.Trip
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class TripRepository(
    private val tripDao: TripDao,
    private val itineraryDao: ItineraryDao,
    private val expenseDao: ExpenseDao,
    private val documentDao: DocumentDao
) {
    val allTrips: Flow<List<Trip>> = tripDao.getAllTrips()
    val activeTrip: Flow<Trip?> = tripDao.getActiveTrip()

    suspend fun getActiveTripSync(): Trip? = tripDao.getActiveTripSync()

    suspend fun createNewTrip(name: String, destination: String, startDate: String, endDate: String, isSample: Boolean = false): Long {
        tripDao.deactivateAllTrips()
        val trip = Trip(
            name = name,
            destination = destination,
            startDate = startDate,
            endDate = endDate,
            isSample = isSample,
            isActive = true
        )
        return tripDao.insertTrip(trip)
    }

    suspend fun setActiveTrip(tripId: Long) {
        tripDao.setActiveTrip(tripId)
    }

    suspend fun updateTripDetails(tripId: Long, name: String, destination: String, startDate: String, endDate: String) {
        tripDao.updateTripDetails(tripId, name, destination, startDate, endDate)
    }

    suspend fun deleteTrip(tripId: Long) {
        val activeTrip = tripDao.getActiveTripSync()
        val wasActive = (activeTrip?.id == tripId)

        itineraryDao.deleteItemsForTrip(tripId)
        expenseDao.deleteExpensesForTrip(tripId)
        documentDao.deleteDocumentsForTrip(tripId)
        tripDao.deleteTrip(tripId)

        if (wasActive) {
            val remainingTrip = tripDao.getActiveTripSync()
            if (remainingTrip == null) {
                val allRemaining = tripDao.getAllTrips().firstOrNull()
                if (!allRemaining.isNullOrEmpty()) {
                    tripDao.setActiveTrip(allRemaining.first().id)
                }
            }
        }
    }

    suspend fun resumeTripWithNewStartDate(tripId: Long, newStartDateStr: String) {
        val trip = tripDao.getTripById(tripId) ?: return
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        val oldStartDate = try { LocalDate.parse(trip.startDate, dateFormatter) } catch (e: Exception) { LocalDate.now() }
        val oldEndDate = try { LocalDate.parse(trip.endDate, dateFormatter) } catch (e: Exception) { oldStartDate.plusDays(3) }
        val newStartDate = try { LocalDate.parse(newStartDateStr, dateFormatter) } catch (e: Exception) { LocalDate.now() }

        val daysOffset = ChronoUnit.DAYS.between(oldStartDate, newStartDate)
        val newEndDate = oldEndDate.plusDays(daysOffset)

        // Update Trip Dates
        tripDao.updateTripDates(tripId, newStartDate.toString(), newEndDate.toString())

        // Adjust Itinerary Item Dates
        val items = itineraryDao.getItemsForTripSync(tripId)
        val updatedItems = items.map { item ->
            val itemDate = try { LocalDate.parse(item.date, dateFormatter) } catch (e: Exception) { oldStartDate }
            item.copy(date = itemDate.plusDays(daysOffset).toString())
        }
        itineraryDao.insertItems(updatedItems)

        // Adjust Expense Dates
        val expenses = expenseDao.getExpensesForTripSync(tripId)
        expenses.forEach { expense ->
            val expDate = try { LocalDate.parse(expense.date, dateFormatter) } catch (e: Exception) { oldStartDate }
            expenseDao.insertExpense(expense.copy(date = expDate.plusDays(daysOffset).toString()))
        }

        // Adjust Document Dates
        val docs = documentDao.getDocumentsForTripSync(tripId)
        docs.forEach { doc ->
            if (doc.date != null) {
                val docDate = try { LocalDate.parse(doc.date, dateFormatter) } catch (e: Exception) { oldStartDate }
                documentDao.insertDocument(doc.copy(date = docDate.plusDays(daysOffset).toString()))
            }
        }

        // Activate the trip
        tripDao.setActiveTrip(tripId)
    }
}
