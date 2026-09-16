package com.driverledger.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TxnType { INCOME, EXPENSE }

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val type: TxnType,
    val source: String,
    val sender: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
