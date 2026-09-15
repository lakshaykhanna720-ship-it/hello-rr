package com.driverledger.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TxnType { INCOME, EXPENSE }

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val type: TxnType,
    /** Where this row came from: "Manual", "GPay auto", "PhonePe auto", "Paytm auto" */
    val source: String,
    val timestamp: Long = System.currentTimeMillis()
)
