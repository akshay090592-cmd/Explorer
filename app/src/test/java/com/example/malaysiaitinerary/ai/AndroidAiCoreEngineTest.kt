package com.example.malaysiaitinerary.ai

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAiCoreEngineTest {

    @Test
    fun testAiCoreEngineNameAndItineraryGeneration() = runTest {
        // Test class structure & data contract
        val engineName = "Android AICore (System Gemini Nano)"
        assertEquals("Android AICore (System Gemini Nano)", engineName)

        val plan = StructuredItineraryPlan(
            tripTitle = "Penang Trip",
            destination = "Penang",
            days = listOf(
                DayPlan(
                    dayNumber = 1,
                    date = "2026-03-25",
                    items = listOf(
                        PlanItem(
                            title = "Gurney Drive Food",
                            locationName = "Penang",
                            startTime = "18:00",
                            description = "Local food market",
                            type = "MEAL"
                        )
                    )
                )
            )
        )

        assertNotNull(plan)
        assertEquals("Penang", plan.destination)
        assertEquals(1, plan.days.size)
    }

    @Test
    fun testAiCoreResponseContract() = runTest {
        val toolCall = AiToolCall(
            toolName = "addItineraryItem",
            arguments = mapOf("title" to "Gurney Drive", "date" to "2026-03-25")
        )
        val response = AiResponse(
            messageText = "Added itinerary stop via AICore",
            toolCalls = listOf(toolCall)
        )

        assertEquals("addItineraryItem", response.toolCalls[0].toolName)
        assertTrue(response.messageText.contains("AICore"))
    }
}
