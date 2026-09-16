package com.driverledger.app.data

import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val dao: TransactionDao) {

    val allTransactions: Flow<List<TransactionEntity>> = dao.getAll()

    suspend fun addManualPayment(amount: Double) {
        dao.insert(
            TransactionEntity(
                amount = amount,
                type = TxnType.INCOME,
                source = "Manual"
            )
        )
    }

    suspend fun addManualExpense(amount: Double) {
        dao.insert(
            TransactionEntity(
                amount = amount,
                type = TxnType.EXPENSE,
                source = "Manual"
            )
        )
    }

    suspend fun addAutoDetectedPaymentIfNew(
        amount: Double,
        source: String,
        sender: String = "",
        windowMs: Long = 10_000
    ): Boolean {

        val since = System.currentTimeMillis() - windowMs
        val cleanSender = sender.trim()

        if (cleanSender.isNotEmpty()) {
            if (
                dao.countSimilarPaymentSince(
                    amount = amount,
                    sender = cleanSender,
                    since = since
                ) > 0
            ) {
                return false
            }
        } else {
            if (
                dao.countSimilarAmountSince(
                    amount = amount,
                    since = since
                ) > 0
            ) {
                return false
            }
        }

        dao.insert(
            TransactionEntity(
                amount = amount,
                type = TxnType.INCOME,
                source = source,
                sender = cleanSender
            )
        )

        return true
    }
}
