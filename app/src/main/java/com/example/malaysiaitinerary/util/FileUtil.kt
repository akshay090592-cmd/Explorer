package com.example.malaysiaitinerary.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.UUID

object FileUtil {
    private val client = OkHttpClient()

    suspend fun downloadImage(context: Context, imageUrl: String): String? {
        return try {
            val request = Request.Builder().url(imageUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                
                val fileName = "img_${UUID.randomUUID()}.jpg"
                val file = File(context.filesDir, "images/$fileName")
                file.parentFile?.mkdirs()
                
                FileOutputStream(file).use { out ->
                    out.write(bytes)
                }
                file.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveBitmap(context: Context, bitmap: Bitmap): String? {
        return try {
            val fileName = "img_${UUID.randomUUID()}.jpg"
            val file = File(context.filesDir, "images/$fileName")
            file.parentFile?.mkdirs()
            
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
