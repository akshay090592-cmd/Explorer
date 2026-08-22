package com.example.malaysiaitinerary.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.malaysiaitinerary.data.local.AppDatabase
import com.example.malaysiaitinerary.data.local.entity.Expense
import com.example.malaysiaitinerary.data.local.entity.ItineraryItem
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object SyncManager {
    suspend fun exportSyncPackage(context: Context, database: AppDatabase) {
        val items = database.itineraryDao().getAllItems().first()
        val expenses = database.expenseDao().getAllExpenses().first()

        val root = JSONObject()
        val itemsArray = JSONArray()
        items.forEach { item ->
            val json = JSONObject()
            json.put("title", item.title)
            json.put("date", item.date)
            json.put("startTime", item.startTime)
            json.put("locationName", item.locationName)
            json.put("type", item.type)
            json.put("description", item.description)
            itemsArray.put(json)
        }
        root.put("items", itemsArray)

        val expensesArray = JSONArray()
        expenses.forEach { exp ->
            val json = JSONObject()
            json.put("amount", exp.amount)
            json.put("currency", exp.currency)
            json.put("category", exp.category)
            json.put("description", exp.description)
            expensesArray.put(json)
        }
        root.put("expenses", expensesArray)

        val file = File(context.cacheDir, "itinerary_sync.json")
        file.writeText(root.toString())

        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Sync Itinerary via Bluetooth/WiFi"))
    }

    suspend fun importSyncPackage(context: Context, jsonString: String, database: AppDatabase) {
        val root = JSONObject(jsonString)
        val itemsArray = root.getJSONArray("items")
        for (i in 0 until itemsArray.length()) {
            val json = itemsArray.getJSONObject(i)
            val item = ItineraryItem(
                title = json.getString("title"),
                date = json.getString("date"),
                startTime = json.getString("startTime"),
                locationName = json.getString("locationName"),
                type = json.getString("type"),
                description = json.getString("description"),
                googleMapsUrl = ""
            )
            database.itineraryDao().insertItem(item)
        }
        
        val expensesArray = root.optJSONArray("expenses") ?: JSONArray()
        for (i in 0 until expensesArray.length()) {
            val json = expensesArray.getJSONObject(i)
            val expense = Expense(
                amount = json.getDouble("amount"),
                currency = json.getString("currency"),
                category = json.getString("category"),
                description = json.getString("description"),
                convertedAmountMYR = json.getDouble("amount"), // Simplification
                convertedAmountINR = json.getDouble("amount") * 18.0, // Simplification (1 MYR ≈ 18 INR)
                date = "2026-03-24"
            )
            database.expenseDao().insertExpense(expense)
        }
    }
}
