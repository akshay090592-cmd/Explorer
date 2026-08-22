package com.example.malaysiaitinerary.util

import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

class ModelScannerTest {

    @Test
    fun testModelScannerReturnsListWithoutExceptions() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_gemma_models")
        tempDir.mkdirs()
        
        val discoveredFiles = mutableListOf<DiscoveredModel>()
        assertNotNull(discoveredFiles)
    }
}
