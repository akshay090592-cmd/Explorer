package com.example.malaysiaitinerary.ai

import java.io.File

data class ExtractedDocumentResult(
    val title: String,
    val type: String, // FLIGHT, ACCOMMODATION, TICKET, MEAL, ID, OTHERS
    val date: String? = null, // YYYY-MM-DD
    val location: String? = null,
    val startTime: String? = null,
    val description: String? = null,
    val expenseAmount: Double? = null,
    val currency: String? = null
)

data class StructuredItineraryPlan(
    val tripTitle: String,
    val destination: String,
    val days: List<DayPlan>
)

data class DayPlan(
    val dayNumber: Int,
    val date: String,
    val items: List<PlanItem>
)

data class PlanItem(
    val title: String,
    val locationName: String,
    val startTime: String,
    val description: String,
    val type: String, // ACTIVITY, MEAL, TRANSIT, ACCOMMODATION
    val googleMapsUrl: String? = null,
    val vegOptions: String? = null,
    val nonVegOptions: String? = null
)

data class AiResponse(
    val messageText: String,
    val toolCalls: List<AiToolCall> = emptyList()
)

data class AiToolCall(
    val toolName: String,
    val arguments: Map<String, String>
)

interface AiEngine {
    val engineName: String

    suspend fun parseDocument(file: File, mimeType: String): Result<ExtractedDocumentResult>

    suspend fun chat(
        userPrompt: String,
        chatHistory: List<Pair<String, String>>,
        tripSummaryContext: String,
        imageBitmap: android.graphics.Bitmap? = null,
        audioBytes: ByteArray? = null
    ): Result<AiResponse>

    suspend fun generateItinerary(
        destination: String,
        daysCount: Int,
        preferences: String,
        enableSearchGrounding: Boolean
    ): Result<StructuredItineraryPlan>
}
