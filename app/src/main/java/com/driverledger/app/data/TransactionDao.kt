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

    /**
     * Notification listeners can fire more than once for the same underlying payment
     * (e.g. the app updates the same notification). This checks whether we already
     * logged an auto-detected transaction with the same amount/source very recently,
     * so we don't double-count it.
     */
    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE amount = :amount AND source = :source AND timestamp >= :since
        """
    )
    suspend fun countSimilarSince(amount: Double, source: String, since: Long): Int
}
