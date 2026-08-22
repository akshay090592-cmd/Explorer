package com.example.malaysiaitinerary.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

open class LocationImageFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
) {

    open suspend fun fetchImageForLocation(locationName: String, googleMapsUrl: String? = null): String? = withContext(Dispatchers.IO) {
        if (locationName.isBlank()) return@withContext null

        // 1. Try fetching OpenGraph meta tag image if googleMapsUrl is provided
        if (!googleMapsUrl.isNullOrBlank() && googleMapsUrl.startsWith("http")) {
            val ogImage = extractOgImage(googleMapsUrl)
            if (!ogImage.isNullOrBlank()) return@withContext ogImage
        }

        // 2. Query Wikimedia Commons API (100% Free, open, no API key required)
        val wikimediaImage = queryWikimediaImage(locationName)
        if (!wikimediaImage.isNullOrBlank()) return@withContext wikimediaImage

        // 3. Fallback to OpenStreetMap / Free Static Map if coordinates detected
        val coordsImage = tryExtractStaticMapFromUrl(googleMapsUrl)
        if (!coordsImage.isNullOrBlank()) return@withContext coordsImage

        null
    }

    private fun queryWikimediaImage(searchTerm: String): String? {
        return try {
            val encodedQuery = URLEncoder.encode(searchTerm, "UTF-8")
            val url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch=$encodedQuery&gsrlimit=1&prop=pageimages&pithumbsize=800&format=json"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "ExplorerTravelApp/1.0 (OpenSource Offline Travel Companion)")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null

            val json = JSONObject(body)
            val queryObj = json.optJSONObject("query") ?: return null
            val pagesObj = queryObj.optJSONObject("pages") ?: return null

            val pageKey = pagesObj.keys().next() ?: return null
            val pageItem = pagesObj.getJSONObject(pageKey)
            val thumbnailObj = pageItem.optJSONObject("thumbnail") ?: return null

            thumbnailObj.optString("source", null)
        } catch (e: Exception) {
            null
        }
    }

    private fun extractOgImage(urlStr: String): String? {
        return try {
            val request = Request.Builder()
                .url(urlStr)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return null

            val ogRegex = """<meta\s+property=["']og:image["']\s+content=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
            val match = ogRegex.find(html)
            match?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }

    private fun tryExtractStaticMapFromUrl(urlStr: String?): String? {
        if (urlStr == null) return null
        return try {
            val coordRegex = """@(-?\d+\.\d+),(-?\d+\.\d+)""".toRegex()
            val match = coordRegex.find(urlStr)
            if (match != null) {
                val lat = match.groupValues[1]
                val lng = match.groupValues[2]
                // OpenStreetMap / OpenStaticMap Tile
                "https://static-maps.yandex.ru/1.x/?ll=$lng,$lat&z=15&l=map&size=600,300"
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
