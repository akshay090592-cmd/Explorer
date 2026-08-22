package com.example.malaysiaitinerary.util

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern

object MapsScraper {
    private val client = OkHttpClient()

    data class MapDetails(
        val name: String?,
        val imageUrl: String?,
        val url: String
    )

    suspend fun fetchDetails(url: String): MapDetails {
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return MapDetails(null, null, url)
                
                val html = response.body?.string() ?: return MapDetails(null, null, url)
                
                // Extremely basic extraction using regex for OG tags (often present in Google Maps links)
                val name = extractTag(html, "property=\"og:title\"") ?: extractTag(html, "name=\"title\"")
                val imageUrl = extractTag(html, "property=\"og:image\"") ?: extractTag(html, "name=\"image\"")
                
                MapDetails(name, imageUrl, url)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            MapDetails(null, null, url)
        }
    }

    private fun extractTag(html: String, tag: String): String? {
        val pattern = Pattern.compile("$tag\\s+content=\"([^\"]+)\"")
        val matcher = pattern.matcher(html)
        return if (matcher.find()) matcher.group(1) else null
    }
}
