package com.driverledger.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAll(): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE amount = :amount
        AND LOWER(sender) = LOWER(:sender)
        AND timestamp >= :since
        AND type = 'INCOME'
        """
    )
    suspend fun countSimilarPaymentSince(
        amount: Double,
        sender: String,
        since: Long
    ): Int

    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE amount = :amount
        AND timestamp >= :since
        AND type = 'INCOME'
        AND source != 'Manual'
        """
    )
    suspend fun countSimilarAmountSince(
        amount: Double,
        since: Long
    ): Int
}
