package com.example.malaysiaitinerary.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

data class ParsedReceipt(
    val amount: Double? = null,
    val currency: String = "MYR",
    val date: String? = null,
    val category: String = "Food",
    val merchant: String = ""
)

object ReceiptParser {

    private val CURRENCY_PATTERNS = listOf(
        Pattern.compile("""(?i)(?:RM|MYR)\s*([0-9]+(?:[.,][0-9]{2})?)"""),
        Pattern.compile("""(?i)(?:₹|INR|RS\.?)\s*([0-9]+(?:[.,][0-9]{2})?)"""),
        Pattern.compile("""(?i)(?:\$|USD)\s*([0-9]+(?:[.,][0-9]{2})?)"""),
        Pattern.compile("""(?i)(?:TOTAL|AMOUNT|SUBTOTAL|GRAND\s*TOTAL|NET)\s*[:=]?\s*(?:RM|MYR|INR|₹|\$)?\s*([0-9]+(?:[.,][0-9]{2})?)""")
    )

    private val DATE_PATTERNS = listOf(
        Pattern.compile("""\b(202[4-9]-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12][0-9]|3[01]))\b"""), // YYYY-MM-DD
        Pattern.compile("""\b((?:0[1-9]|[12][0-9]|3[01])-(?:0[1-9]|1[0-2])-(?:202[4-9]|2[4-9]))\b"""), // DD-MM-YYYY
        Pattern.compile("""\b((?:0[1-9]|[12][0-9]|3[01])/(?:0[1-9]|1[0-2])/(?:202[4-9]|2[4-9]))\b"""), // DD/MM/YYYY
        Pattern.compile("""\b((?:0[1-9]|1[0-2])/(?:0[1-9]|[12][0-9]|3[01])/(?:202[4-9]|2[4-9]))\b""") // MM/DD/YYYY
    )

    private val TRANSPORT_KEYWORDS = listOf("grab", "taxi", "transit", "klia", "express", "myrapid", "rail", "flight", "airasia", "malaysia airlines", "fuel", "petronas", "shell", "fare", "ride")
    private val LODGING_KEYWORDS = listOf("hotel", "resort", "inn", "suites", "hostel", "airbnb", "stay", "villa", "wolo")
    private val SHOPPING_KEYWORDS = listOf("mall", "mart", "supermarket", "7-eleven", "watsons", "guardian", "store", "boutique", "uniqlo", "zara", "shirt", "clothing", "apparel", "shopping")
    private val FOOD_KEYWORDS = listOf("restaurant", "cafe", "coffee", "bistro", "bar", "kitchen", "bakery", "mcdonald", "kfc", "starbucks", "nasi", "roti", "food", "dining", "grill", "eatery", "hawker", "satay", "teow")

    fun parse(ocrText: String): ParsedReceipt {
        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return ParsedReceipt()
        }

        var detectedAmount: Double? = null
        var detectedCurrency = "MYR"

        // Detect currency type from full text
        val upperText = ocrText.uppercase()
        if (upperText.contains("INR") || upperText.contains("₹") || upperText.contains("RUPEE")) {
            detectedCurrency = "INR"
        } else if (upperText.contains("USD") || upperText.contains("US$")) {
            detectedCurrency = "USD"
        } else {
            detectedCurrency = "MYR"
        }

        // Try extracting amount with regex
        for (pattern in CURRENCY_PATTERNS) {
            val matcher = pattern.matcher(ocrText)
            var highestFound: Double? = null
            while (matcher.find()) {
                val group = matcher.group(1)
                val cleanNumber = group?.replace(",", "")?.toDoubleOrNull()
                if (cleanNumber != null && (highestFound == null || cleanNumber > highestFound)) {
                    highestFound = cleanNumber
                }
            }
            if (highestFound != null) {
                detectedAmount = highestFound
                break
            }
        }

        // Fallback: look for generic float amounts on lines with 'TOTAL'
        if (detectedAmount == null) {
            val totalLine = lines.find { it.contains("TOTAL", ignoreCase = true) || it.contains("AMOUNT", ignoreCase = true) }
            if (totalLine != null) {
                val numMatcher = Pattern.compile("""([0-9]+\.[0-9]{2})""").matcher(totalLine)
                if (numMatcher.find()) {
                    detectedAmount = numMatcher.group(1)?.toDoubleOrNull()
                }
            }
        }

        // Extract Date
        var detectedDate: String? = null
        for (pattern in DATE_PATTERNS) {
            val matcher = pattern.matcher(ocrText)
            if (matcher.find()) {
                val rawDate = matcher.group(1)
                detectedDate = normalizeDate(rawDate)
                if (detectedDate != null) break
            }
        }
        if (detectedDate == null) {
            detectedDate = LocalDate.now().toString()
        }

        // Detect merchant / title from top lines
        val merchantCandidate = lines.map { line ->
            line.replace(Regex("(?i)\\b(receipt|tax invoice|invoice|official)\\b"), "").trim()
        }.firstOrNull { it.length in 3..40 } ?: "Travel Expense"

        // Detect category
        val category = detectCategory(ocrText)

        return ParsedReceipt(
            amount = detectedAmount,
            currency = detectedCurrency,
            date = detectedDate,
            category = category,
            merchant = merchantCandidate
        )
    }

    private fun detectCategory(text: String): String {
        val lower = text.lowercase()
        return when {
            TRANSPORT_KEYWORDS.any { lower.contains(it) } -> "Transport"
            LODGING_KEYWORDS.any { lower.contains(it) } -> "Hotel"
            SHOPPING_KEYWORDS.any { lower.contains(it) } -> "Shopping"
            FOOD_KEYWORDS.any { lower.contains(it) } -> "Food"
            else -> "Food"
        }
    }

    private fun normalizeDate(raw: String?): String? {
        if (raw == null) return null
        return try {
            if (raw.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
                raw
            } else if (raw.contains("-")) {
                val parts = raw.split("-")
                if (parts.size == 3 && parts[0].length <= 2) {
                    val p0 = parts[0].padStart(2, '0')
                    val p1 = parts[1].padStart(2, '0')
                    val p2 = if (parts[2].length == 2) "20${parts[2]}" else parts[2]
                    "$p2-$p1-$p0"
                } else raw
            } else if (raw.contains("/")) {
                val parts = raw.split("/")
                if (parts.size == 3) {
                    val p0 = parts[0].padStart(2, '0')
                    val p1 = parts[1].padStart(2, '0')
                    val p2 = if (parts[2].length == 2) "20${parts[2]}" else parts[2]
                    "$p2-$p1-$p0"
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
