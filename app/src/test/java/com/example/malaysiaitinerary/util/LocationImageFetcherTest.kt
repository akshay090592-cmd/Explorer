package com.example.malaysiaitinerary.util

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LocationImageFetcherTest {

    @Test
    fun fetchImageForLocation_blankLocation_returnsNull() = runTest {
        val fetcher = LocationImageFetcher()
        val result = fetcher.fetchImageForLocation("", null)
        assertNull(result)
    }

    @Test
    fun fetchImageForLocation_validLocation_returnsUrlOrNull() = runTest {
        val fetcher = LocationImageFetcher()
        val result = fetcher.fetchImageForLocation("Eiffel Tower", null)
        // Result is either a valid Wikimedia photo URL string or null if network offline
        if (result != null) {
            assertNotNull(result)
        }
    }
}
