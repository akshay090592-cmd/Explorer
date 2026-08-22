package com.example.malaysiaitinerary.ui.expense

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.malaysiaitinerary.data.local.entity.Expense
import com.example.malaysiaitinerary.data.repository.ExpenseRepository
import com.example.malaysiaitinerary.util.ExchangeRateUtil
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.malaysiaitinerary.data.repository.TripRepository

data class CategorySummary(
    val category: String,
    val totalMYR: Double,
    val totalINR: Double,
    val percentage: Float,
    val count: Int
)

class ExpenseViewModel(
    private val repository: ExpenseRepository,
    private val tripRepository: TripRepository? = null
) : ViewModel() {

    val expenses: StateFlow<List<Expense>> = repository.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalSpentMYR: StateFlow<Double?> = repository.totalSpentMYR
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalSpentINR: StateFlow<Double?> = repository.totalSpentINR
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val categorySummaries: StateFlow<List<CategorySummary>> = expenses.map { list ->
        val totalMYR = list.sumOf { it.convertedAmountMYR }
        if (totalMYR <= 0.0) {
            emptyList()
        } else {
            list.groupBy { it.category }
                .map { (category, items) ->
                    val catMYR = items.sumOf { it.convertedAmountMYR }
                    val catINR = items.sumOf { it.convertedAmountINR }
                    CategorySummary(
                        category = category,
                        totalMYR = catMYR,
                        totalINR = catINR,
                        percentage = ((catMYR / totalMYR) * 100).toFloat(),
                        count = items.size
                    )
                }
                .sortedByDescending { it.totalMYR }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addExpense(context: Context, amount: Double, currency: String, category: String, date: String, description: String) {
        viewModelScope.launch {
            val activeTripId = tripRepository?.getActiveTripSync()?.id ?: 1L
            val rateMYRtoUSD = ExchangeRateUtil.getExchangeRateMYRtoUSD(context)
            val rateMYRtoINR = ExchangeRateUtil.getExchangeRateMYRtoINR(context)
            
            // Convert to MYR base
            val convertedAmountMYR = when (currency.uppercase()) {
                "USD" -> amount / rateMYRtoUSD
                "INR" -> amount / rateMYRtoINR
                else -> amount
            }

            // Convert to INR base
            val convertedAmountINR = convertedAmountMYR * rateMYRtoINR

            val expense = Expense(
                amount = amount,
                currency = currency.uppercase(),
                convertedAmountMYR = convertedAmountMYR,
                convertedAmountINR = convertedAmountINR,
                category = category,
                date = date,
                description = description.ifBlank { "Travel Expense" },
                tripId = activeTripId
            )
            repository.insertExpense(expense)
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }

    fun generateCsvExport(): String {
        val list = expenses.value
        val sb = StringBuilder()
        sb.append("ID,Date,Description,Category,Original Amount,Currency,Amount (MYR),Amount (INR)\n")
        list.forEach { exp ->
            sb.append("${exp.id},\"${exp.date}\",\"${exp.description.replace("\"", "\"\"")}\",\"${exp.category}\",${exp.amount},${exp.currency},${exp.convertedAmountMYR},${exp.convertedAmountINR}\n")
        }
        return sb.toString()
    }
}

class ExpenseViewModelFactory(
    private val repository: ExpenseRepository,
    private val tripRepository: TripRepository? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExpenseViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ExpenseViewModel(repository, tripRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
