package com.example.malaysiaitinerary.data.local.dao

import androidx.room.*
import com.example.malaysiaitinerary.data.local.entity.Expense
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE tripId = :tripId ORDER BY date DESC")
    fun getExpensesForTrip(tripId: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE tripId = :tripId ORDER BY date DESC")
    suspend fun getExpensesForTripSync(tripId: Long): List<Expense>

    @Query("SELECT SUM(convertedAmountMYR) FROM expenses WHERE tripId = :tripId")
    fun getTotalSpentMYRForTrip(tripId: Long): Flow<Double?>

    @Query("SELECT SUM(convertedAmountINR) FROM expenses WHERE tripId = :tripId")
    fun getTotalSpentINRForTrip(tripId: Long): Flow<Double?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense)

    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()

    @Query("DELETE FROM expenses WHERE isDemo = 1")
    suspend fun deleteDemoExpenses()

    @Query("DELETE FROM expenses WHERE tripId = :tripId")
    suspend fun deleteExpensesForTrip(tripId: Long)
}

