package com.example.malaysiaitinerary.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class GeminiCloudEngine(
    private val apiKey: String
) : AiEngine {

    override val engineName: String = "Google Gemini Cloud (gemini-flash-latest)"

    override suspend fun parseDocument(file: File, mimeType: String): Result<ExtractedDocumentResult> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(Exception("Gemini API key is not configured in Settings."))
            }

            val generativeModel = GenerativeModel(
                modelName = "gemini-flash-latest",
                apiKey = apiKey
            )

            val prompt = """
                Extract structured travel info from this document/image into JSON.
                Keys required:
                - title: (String e.g. "Flight Booking", "Hotel WOLO Voucher")
                - type: (String: "FLIGHT", "ACCOMMODATION", "TICKET", "MEAL", "ID", "OTHERS")
                - date: (YYYY-MM-DD or null)
                - location: (Location string or null)
                - startTime: (HH:MM or null)
                - description: (Summary string)
                - expenseAmount: (Number or null)
                - currency: (3-letter currency code or null)
            """.trimIndent()

            val bitmap = loadBitmapFromFile(file, mimeType)
            val response = if (bitmap != null) {
                val inputContent = content {
                    image(bitmap)
                    text(prompt)
                }
                generativeModel.generateContent(inputContent)
            } else {
                val textContent = file.readText()
                val inputContent = content {
                    text("$prompt\n\nDocument Text:\n$textContent")
                }
                generativeModel.generateContent(inputContent)
            }

            val responseText = response.text ?: ""
            val json = parseJsonObject(responseText)

            val result = ExtractedDocumentResult(
                title = json.optString("title", file.nameWithoutExtension),
                type = json.optString("type", "TICKET").uppercase(),
                date = json.optString("date", null).takeIf { it != "null" && it.isNotBlank() },
                location = json.optString("location", null).takeIf { it != "null" && it.isNotBlank() },
                startTime = json.optString("startTime", null).takeIf { it != "null" && it.isNotBlank() },
                description = json.optString("description", "Extracted via Gemini"),
                expenseAmount = if (json.has("expenseAmount") && !json.isNull("expenseAmount")) json.optDouble("expenseAmount") else null,
                currency = json.optString("currency", null).takeIf { it != "null" && it.isNotBlank() }
            )

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun chat(
        userPrompt: String,
        chatHistory: List<Pair<String, String>>,
        tripSummaryContext: String,
        imageBitmap: Bitmap?,
        audioBytes: ByteArray?
    ): Result<AiResponse> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(Exception("Gemini API key is not configured."))
            }

            val generativeModel = GenerativeModel(
                modelName = "gemini-flash-latest",
                apiKey = apiKey
            )

            val systemContext = """
                You are Explorer AI, an expert travel companion.
                Trip Context: $tripSummaryContext
                If user wants to modify database items, append JSON tool commands at the end wrapped in [TOOLS]...[/TOOLS].
                Tools available:
                - addItineraryItem(date: YYYY-MM-DD, title: String, location: String, startTime: HH:MM, type: ACTIVITY|MEAL|TRANSIT|ACCOMMODATION, description: String)
                - addExpense(amount: String, currency: String, category: String, date: YYYY-MM-DD, note: String)
            """.trimIndent()

            val fullPrompt = "$systemContext\n\nUser request: $userPrompt"

            val inputContent = content {
                if (imageBitmap != null) {
                    image(imageBitmap)
                }
                if (audioBytes != null) {
                    blob("audio/wav", audioBytes)
                }
                text(fullPrompt)
            }

            val response = generativeModel.generateContent(inputContent)
            val rawOutput = response.text ?: "I am ready to help with your trip!"

            val toolCalls = mutableListOf<AiToolCall>()
            var cleanText = rawOutput

            if (rawOutput.contains("[TOOLS]") && rawOutput.contains("[/TOOLS]")) {
                val toolsStr = rawOutput.substringAfter("[TOOLS]").substringBefore("[/TOOLS]").trim()
                cleanText = rawOutput.substringBefore("[TOOLS]").trim()
                try {
                    val jsonArray = JSONArray(toolsStr)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val name = obj.optString("toolName", obj.optString("name"))
                        val argsObj = obj.optJSONObject("arguments") ?: JSONObject()
                        val argsMap = mutableMapOf<String, String>()
                        argsObj.keys().forEach { key ->
                            argsMap[key] = argsObj.optString(key)
                        }
                        if (name.isNotBlank()) {
                            toolCalls.add(AiToolCall(name, argsMap))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            Result.success(AiResponse(messageText = cleanText, toolCalls = toolCalls))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun generateItinerary(
        destination: String,
        daysCount: Int,
        preferences: String,
        enableSearchGrounding: Boolean
    ): Result<StructuredItineraryPlan> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(Exception("Gemini API key is required for itinerary generation."))
            }

            val generativeModel = GenerativeModel(
                modelName = "gemini-flash-latest",
                apiKey = apiKey
            )

            val prompt = """
                Create a complete $daysCount-day travel itinerary for $destination focusing on $preferences.
                Use real places and recommendations. Output ONLY valid JSON matching this schema:
                {
                  "tripTitle": "$daysCount-Day $destination Exploration",
                  "destination": "$destination",
                  "days": [
                    {
                      "dayNumber": 1,
                      "date": "2026-04-01",
                      "items": [
                        {
                          "title": "Visit Spot",
                          "locationName": "Real Place Name",
                          "startTime": "09:00",
                          "description": "Details about spot",
                          "type": "ACTIVITY",
                          "googleMapsUrl": "https://www.google.com/maps/search/?api=1&query=Real+Place+Name",
                          "vegOptions": "Vegetarian options if meal",
                          "nonVegOptions": "Non-veg options if meal"
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent()

            val response = generativeModel.generateContent(prompt)
            val json = parseJsonObject(response.text ?: "")

            val daysList = mutableListOf<DayPlan>()
            val daysArray = json.optJSONArray("days") ?: JSONArray()
            for (i in 0 until daysArray.length()) {
                val dayObj = daysArray.getJSONObject(i)
                val itemsList = mutableListOf<PlanItem>()
                val itemsArray = dayObj.optJSONArray("items") ?: JSONArray()
                for (j in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(j)
                    itemsList.add(
                        PlanItem(
                            title = itemObj.optString("title", "Activity"),
                            locationName = itemObj.optString("locationName", destination),
                            startTime = itemObj.optString("startTime", "09:00"),
                            description = itemObj.optString("description", ""),
                            type = itemObj.optString("type", "ACTIVITY").uppercase(),
                            googleMapsUrl = itemObj.optString("googleMapsUrl").takeIf { it.isNotBlank() },
                            vegOptions = itemObj.optString("vegOptions").takeIf { it.isNotBlank() },
                            nonVegOptions = itemObj.optString("nonVegOptions").takeIf { it.isNotBlank() }
                        )
                    )
                }
                daysList.add(
                    DayPlan(
                        dayNumber = dayObj.optInt("dayNumber", i + 1),
                        date = dayObj.optString("date", "2026-04-0${i + 1}"),
                        items = itemsList
                    )
                )
            }

            val plan = StructuredItineraryPlan(
                tripTitle = json.optString("tripTitle", "$daysCount-Day $destination Tour"),
                destination = destination,
                days = daysList
            )

            Result.success(plan)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun loadBitmapFromFile(file: File, mimeType: String): Bitmap? {
        return try {
            if (mimeType.contains("pdf", ignoreCase = true) || file.extension.equals("pdf", ignoreCase = true)) {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val page = renderer.openPage(0)
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                pfd.close()
                bitmap
            } else {
                BitmapFactory.decodeFile(file.absolutePath)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseJsonObject(input: String): JSONObject {
        val jsonStr = when {
            input.contains("{") && input.contains("}") -> input.substring(input.indexOf("{"), input.lastIndexOf("}") + 1)
            else -> input
        }
        return try {
            JSONObject(jsonStr)
        } catch (e: Exception) {
            JSONObject()
        }
    }
}
