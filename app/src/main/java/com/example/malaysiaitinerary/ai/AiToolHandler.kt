package com.example.malaysiaitinerary.ai

import com.example.malaysiaitinerary.data.local.entity.Document
import com.example.malaysiaitinerary.data.local.entity.Expense
import com.example.malaysiaitinerary.data.local.entity.ItineraryItem
import com.example.malaysiaitinerary.data.repository.DocumentRepository
import com.example.malaysiaitinerary.data.repository.ExpenseRepository
import com.example.malaysiaitinerary.data.repository.ItineraryRepository
import com.example.malaysiaitinerary.util.LocationImageFetcher

import com.example.malaysiaitinerary.data.repository.TripRepository

class AiToolHandler(
    private val itineraryRepository: ItineraryRepository,
    private val documentRepository: DocumentRepository,
    private val expenseRepository: ExpenseRepository,
    private val tripRepository: TripRepository? = null,
    private val imageFetcher: LocationImageFetcher = LocationImageFetcher()
) {

    suspend fun executeToolCall(call: AiToolCall): String {
        return try {
            val defaultTomorrow = java.time.LocalDate.now().plusDays(1).toString()
            val activeTrip = tripRepository?.getActiveTripSync()
            val activeTripId = activeTrip?.id ?: 1L

            when (call.toolName) {
                "addItineraryItem" -> {
                    val date = call.arguments["date"] ?: call.arguments["day"] ?: defaultTomorrow
                    val title = call.arguments["title"] ?: call.arguments["name"] ?: call.arguments["activity"] ?: call.arguments["item"] ?: "New Trip Activity"
                    val location = call.arguments["location"] ?: call.arguments["locationName"] ?: call.arguments["place"] ?: "Malaysia"
                    val startTime = call.arguments["startTime"] ?: call.arguments["time"] ?: "10:00"
                    val type = (call.arguments["type"] ?: call.arguments["category"] ?: "ACTIVITY").uppercase()
                    val description = call.arguments["description"] ?: call.arguments["note"] ?: call.arguments["details"] ?: "Added via AI Agent"
                    val mapsUrl = "https://www.google.com/maps/search/?api=1&query=${location.replace(" ", "+")}"

                    val fetchedImageUrl = imageFetcher.fetchImageForLocation(location, mapsUrl)

                    val item = ItineraryItem(
                        date = date,
                        title = title,
                        locationName = location,
                        startTime = startTime,
                        description = description,
                        type = type,
                        googleMapsUrl = mapsUrl,
                        imageUrl = fetchedImageUrl,
                        isDemo = false,
                        tripId = activeTripId
                    )
                    itineraryRepository.insertItem(item)
                    val imgSuffix = if (fetchedImageUrl != null) " (with location photo)" else ""
                    "Added itinerary item: $title at $location on $date$imgSuffix"
                }

                "addExpense" -> {
                    val rawAmount = call.arguments["amount"] ?: call.arguments["cost"] ?: call.arguments["price"] ?: "50"
                    val amount = Regex("(\\d+(?:\\.\\d+)?)").find(rawAmount)?.groupValues?.get(1)?.toDoubleOrNull() ?: 50.0
                    val currency = call.arguments["currency"] ?: "MYR"
                    val category = call.arguments["category"] ?: "General"
                    val date = call.arguments["date"] ?: call.arguments["day"] ?: defaultTomorrow
                    val note = call.arguments["note"] ?: call.arguments["description"] ?: "Logged via AI Agent"

                    val expense = Expense(
                        amount = amount,
                        currency = currency,
                        convertedAmountMYR = amount,
                        convertedAmountINR = amount * 20.0,
                        category = category,
                        date = date,
                        description = note,
                        isDemo = false,
                        tripId = activeTripId
                    )
                    expenseRepository.insertExpense(expense)
                    "Logged expense: $amount $currency for $category"
                }

                "addDocument" -> {
                    val fileName = call.arguments["fileName"] ?: call.arguments["title"] ?: call.arguments["name"] ?: "Imported_Document.pdf"
                    val type = (call.arguments["type"] ?: "TICKET").uppercase()
                    val date = call.arguments["date"] ?: call.arguments["day"] ?: defaultTomorrow
                    val location = call.arguments["location"] ?: call.arguments["place"]

                    val document = Document(
                        fileName = fileName,
                        filePath = call.arguments["filePath"] ?: "",
                        type = type,
                        date = date,
                        location = location,
                        isDemo = false,
                        tripId = activeTripId
                    )
                    documentRepository.insertDocument(document)
                    "Added document $fileName to Briefcase"
                }

                else -> "Executed tool ${call.toolName}"
            }

        } catch (e: Exception) {
            "Failed to execute tool ${call.toolName}: ${e.localizedMessage}"
        }
    }
}
