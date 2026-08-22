package com.example.malaysiaitinerary.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

object ExchangeRateUtil {
    private const val PREFS_NAME = "exchange_rate_prefs"
    private const val RATE_USD_KEY = "myr_to_usd_rate"
    private const val RATE_INR_KEY = "myr_to_inr_rate"
    
    // Default fallback rates
    private const val DEFAULT_USD_RATE = 4.7f 
    private const val DEFAULT_INR_RATE = 18.0f // 1 MYR ≈ 18 INR

    suspend fun getExchangeRateMYRtoUSD(context: Context): Float = getRate(context, "USD", RATE_USD_KEY, DEFAULT_USD_RATE)
    suspend fun getExchangeRateMYRtoINR(context: Context): Float = getRate(context, "INR", RATE_INR_KEY, DEFAULT_INR_RATE)

    private suspend fun getRate(context: Context, currency: String, key: String, default: Float): Float = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val response = URL("https://open.er-api.com/v6/latest/MYR").readText()
            val jsonObject = JSONObject(response)
            if (jsonObject.getString("result") == "success") {
                val rates = jsonObject.getJSONObject("rates")
                val rate = rates.getDouble(currency).toFloat()
                
                prefs.edit().putFloat(key, rate).apply()
                return@withContext rate
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        prefs.getFloat(key, default)
    }
}
