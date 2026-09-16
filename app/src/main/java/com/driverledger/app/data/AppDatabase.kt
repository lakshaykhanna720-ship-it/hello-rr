package com.driverledger.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun fromTxnType(value: TxnType): String = value.name

    @TypeConverter
    fun toTxnType(value: String): TxnType = TxnType.valueOf(value)
}

@Database(
    entities = [TransactionEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    companion object {

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    ALTER TABLE transactions
                    ADD COLUMN sender TEXT NOT NULL DEFAULT ''
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "driver_ledger.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
