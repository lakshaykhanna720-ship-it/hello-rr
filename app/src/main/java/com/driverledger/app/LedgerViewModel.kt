package com.driverledger.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.driverledger.app.data.AppDatabase
import com.driverledger.app.data.TransactionRepository
import com.driverledger.app.data.TxnType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class LedgerViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = TransactionRepository(
        AppDatabase.getInstance(application).transactionDao()
    )

    private val transactions = repo.allTransactions.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    val todayIncomeTotal: StateFlow<Double> = transactions.map { list ->
        val start = startOfDayMillis()
        list.filter { it.type == TxnType.INCOME && it.timestamp >= start }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val todayPaymentCount: StateFlow<Int> = transactions.map { list ->
        val start = startOfDayMillis()
        list.count { it.type == TxnType.INCOME && it.timestamp >= start }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val weekIncomeTotal: StateFlow<Double> = transactions.map { list ->
        val start = startOfWeekMillis()
        list.filter { it.type == TxnType.INCOME && it.timestamp >= start }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val weekExpenseTotal: StateFlow<Double> = transactions.map { list ->
        val start = startOfWeekMillis()
        list.filter { it.type == TxnType.EXPENSE && it.timestamp >= start }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    fun addPayment(amount: Double) {
        if (amount <= 0) return
        viewModelScope.launch { repo.addManualPayment(amount) }
    }

    fun addExpense(amount: Double) {
        if (amount <= 0) return
        viewModelScope.launch { repo.addManualExpense(amount) }
    }

    private fun startOfDayMillis(): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun startOfWeekMillis(): Long =
        Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
