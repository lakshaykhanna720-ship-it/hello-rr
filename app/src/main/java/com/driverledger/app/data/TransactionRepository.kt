package com.driverledger.app.data

import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val dao: TransactionDao) {

    val allTransactions: Flow<List<TransactionEntity>> = dao.getAll()

    suspend fun addManualPayment(amount: Double) {
        dao.insert(TransactionEntity(amount = amount, type = TxnType.INCOME, source = "Manual"))
    }

    suspend fun addManualExpense(amount: Double) {
        dao.insert(TransactionEntity(amount = amount, type = TxnType.EXPENSE, source = "Manual"))
    }

    /**
     * Called by the notification listener. Skips the insert (returns false) if an
     * identical auto-detected payment was already logged in the last [windowMs].
     */
    suspend fun addAutoDetectedPaymentIfNew(
        amount: Double,
        source: String,
        windowMs: Long = 5_000
    ): Boolean {
        val since = System.currentTimeMillis() - windowMs
        if (dao.countSimilarSince(amount, source, since) > 0) return false
        dao.insert(TransactionEntity(amount = amount, type = TxnType.INCOME, source = source))
        return true
    }
}
