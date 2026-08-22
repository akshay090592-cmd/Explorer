package com.example.malaysiaitinerary.data.repository

import com.example.malaysiaitinerary.data.local.dao.ExpenseDao
import com.example.malaysiaitinerary.data.local.dao.TripDao
import com.example.malaysiaitinerary.data.local.entity.Expense
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest

class ExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val tripDao: TripDao? = null
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val allExpenses: Flow<List<Expense>> = if (tripDao != null) {
        tripDao.getActiveTrip().flatMapLatest { activeTrip ->
            val tripId = activeTrip?.id ?: 1L
            expenseDao.getExpensesForTrip(tripId)
        }
    } else {
        expenseDao.getAllExpenses()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalSpentMYR: Flow<Double?> = if (tripDao != null) {
        tripDao.getActiveTrip().flatMapLatest { activeTrip ->
            val tripId = activeTrip?.id ?: 1L
            expenseDao.getTotalSpentMYRForTrip(tripId)
        }
    } else {
        expenseDao.getTotalSpentMYRForTrip(1L)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalSpentINR: Flow<Double?> = if (tripDao != null) {
        tripDao.getActiveTrip().flatMapLatest { activeTrip ->
            val tripId = activeTrip?.id ?: 1L
            expenseDao.getTotalSpentINRForTrip(tripId)
        }
    } else {
        expenseDao.getTotalSpentINRForTrip(1L)
    }

    fun getExpensesForTrip(tripId: Long): Flow<List<Expense>> = expenseDao.getExpensesForTrip(tripId)
    fun getTotalSpentMYRForTrip(tripId: Long): Flow<Double?> = expenseDao.getTotalSpentMYRForTrip(tripId)
    fun getTotalSpentINRForTrip(tripId: Long): Flow<Double?> = expenseDao.getTotalSpentINRForTrip(tripId)

    suspend fun insertExpense(expense: Expense) {
        expenseDao.insertExpense(expense)
    }

    suspend fun deleteExpense(expense: Expense) {
        expenseDao.deleteExpense(expense)
    }

    suspend fun deleteDemoExpenses() {
        expenseDao.deleteDemoExpenses()
    }

    suspend fun clearAll() {
        expenseDao.deleteAll()
    }
}

