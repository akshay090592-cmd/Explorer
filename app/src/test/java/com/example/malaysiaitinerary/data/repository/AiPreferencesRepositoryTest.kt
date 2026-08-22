package com.example.malaysiaitinerary.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class AiPreferencesRepositoryTest {

    @Test
    fun engineModeEnum_correctValues() {
        assertEquals("ON_DEVICE_GEMMA", AiEngineMode.ON_DEVICE_GEMMA.name)
        assertEquals("GEMINI_CLOUD", AiEngineMode.GEMINI_CLOUD.name)
    }

    @Test
    fun gemmaModelChoiceEnum_displayNameAndFilename() {
        val choice1b = GemmaModelChoice.GEMMA_4_1B_INT4
        assertEquals("gemma-4-1b-it-gpu-int4.bin", choice1b.filename)

        val choice2b = GemmaModelChoice.GEMMA_4_2B_INT4
        assertEquals("gemma-4-2b-it-gpu-int4.bin", choice2b.filename)
    }
}
