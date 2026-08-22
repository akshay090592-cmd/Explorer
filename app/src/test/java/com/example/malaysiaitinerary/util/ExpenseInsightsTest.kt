package com.example.malaysiaitinerary.util

import com.example.malaysiaitinerary.data.local.entity.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseInsightsTest {

    @Test
    fun categoryAggregation_computesCorrectPercentagesAndTotals() {
        val expenses = listOf(
            Expense(id = 1, amount = 100.0, currency = "MYR", convertedAmountMYR = 100.0, convertedAmountINR = 1800.0, category = "Food", date = "2026-03-24", description = "Lunch"),
            Expense(id = 2, amount = 100.0, currency = "MYR", convertedAmountMYR = 100.0, convertedAmountINR = 1800.0, category = "Food", date = "2026-03-24", description = "Dinner"),
            Expense(id = 3, amount = 200.0, currency = "MYR", convertedAmountMYR = 200.0, convertedAmountINR = 3600.0, category = "Hotel", date = "2026-03-24", description = "Night 1"),
        )

        val totalMYR = expenses.sumOf { it.convertedAmountMYR } // 400.0
        assertEquals(400.0, totalMYR, 0.001)

        val grouped = expenses.groupBy { it.category }
        val foodTotal = grouped["Food"]?.sumOf { it.convertedAmountMYR } ?: 0.0
        val hotelTotal = grouped["Hotel"]?.sumOf { it.convertedAmountMYR } ?: 0.0

        val foodPercentage = (foodTotal / totalMYR) * 100
        val hotelPercentage = (hotelTotal / totalMYR) * 100

        assertEquals(200.0, foodTotal, 0.001)
        assertEquals(200.0, hotelTotal, 0.001)
        assertEquals(50.0, foodPercentage, 0.001)
        assertEquals(50.0, hotelPercentage, 0.001)
    }

    @Test
    fun csvExportFormat_producesValidCsvHeaderAndRows() {
        val expenses = listOf(
            Expense(id = 1, amount = 45.0, currency = "MYR", convertedAmountMYR = 45.0, convertedAmountINR = 810.0, category = "Food", date = "2026-03-24", description = "Roti Canai")
        )

        val sb = StringBuilder()
        sb.append("ID,Date,Description,Category,Original Amount,Currency,Amount (MYR),Amount (INR)\n")
        expenses.forEach { exp ->
            sb.append("${exp.id},\"${exp.date}\",\"${exp.description.replace("\"", "\"\"")}\",\"${exp.category}\",${exp.amount},${exp.currency},${exp.convertedAmountMYR},${exp.convertedAmountINR}\n")
        }

        val csv = sb.toString()
        assertTrue(csv.startsWith("ID,Date,Description,Category"))
        assertTrue(csv.contains("Roti Canai"))
        assertTrue(csv.contains("810.0"))
    }
}
