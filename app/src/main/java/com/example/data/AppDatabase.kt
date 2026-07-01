package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.sqlcipher.database.SupportFactory

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Customer::class, LoanCycle::class, WeeklyPayment::class, EditLog::class, CashBalanceLog::class],
    version = 13,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun collectionDao(): CollectionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        @Volatile
        private var IN_MEMORY_INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Placeholder migrate
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Placeholder migrate
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Placeholder migrate
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add upiNameAlias column to customers table
                db.execSQL("ALTER TABLE customers ADD COLUMN upiNameAlias TEXT NOT NULL DEFAULT ''")
                // Add upiTxnId column to weekly_payments table
                db.execSQL("ALTER TABLE weekly_payments ADD COLUMN upiTxnId TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN preferredLanguage TEXT NOT NULL DEFAULT 'English'")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE loan_cycles ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE weekly_payments ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE audit_logs ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN lastModified INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE customers ADD COLUMN syncedLastSavedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE loan_cycles ADD COLUMN lastModified INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE weekly_payments ADD COLUMN lastModified INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'")
                db.execSQL("ALTER TABLE weekly_payments ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audit_logs RENAME TO edit_logs")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE loan_cycles ADD COLUMN deduction REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `cash_balance_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `date` INTEGER NOT NULL, 
                        `actualCash` REAL NOT NULL, 
                        `systemCash` REAL NOT NULL, 
                        `collectionAmount` REAL NOT NULL, 
                        `disbursalAmount` REAL NOT NULL, 
                        `expenses` REAL NOT NULL, 
                        `notes` TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            val prefs = context.getSharedPreferences("weekly_finance_prefs", Context.MODE_PRIVATE)
            val isDemo = prefs.getBoolean("is_demo_mode", false)
            val currentRole = prefs.getString("current_role", "USER") ?: "USER"
            
            val isDemoMode = isDemo
            val isReadOnlyUser = currentRole == "USER"

            val decodedByteArray = android.util.Base64.decode("TURiQDI0MDgwNw==", android.util.Base64.DEFAULT)
            val dbPasswordBytes = if (com.example.BuildConfig.DB_PASSWORD.isNotBlank() && com.example.BuildConfig.DB_PASSWORD != "PLACEHOLDER") {
                com.example.BuildConfig.DB_PASSWORD.toByteArray(Charsets.UTF_8)
            } else {
                decodedByteArray
            }
            val factory = SupportFactory(dbPasswordBytes)

            return when {
                isDemoMode -> {
                    IN_MEMORY_INSTANCE ?: synchronized(this) {
                        IN_MEMORY_INSTANCE ?: Room.inMemoryDatabaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java
                        )
                        .openHelperFactory(factory)
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
                        .fallbackToDestructiveMigration(true)
                        .build().also { IN_MEMORY_INSTANCE = it }
                    }
                }
                isReadOnlyUser -> {
                    INSTANCE ?: synchronized(this) {
                        INSTANCE ?: Room.databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "weekly_finance_user_cache_db"
                        )
                        .openHelperFactory(factory)
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
                        .fallbackToDestructiveMigration(true)
                        .build().also { INSTANCE = it }
                    }
                }
                else -> {
                    INSTANCE ?: synchronized(this) {
                        INSTANCE ?: Room.databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "weekly_finance_collection_db"
                        )
                        .openHelperFactory(factory)
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
                        .fallbackToDestructiveMigration(true)
                        .build().also { INSTANCE = it }
                    }
                }
            }
        }

        fun resetDatabaseInstances() {
            synchronized(this) {
                try {
                    IN_MEMORY_INSTANCE?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                IN_MEMORY_INSTANCE = null
                
                try {
                    INSTANCE?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                INSTANCE = null
            }
        }
    }
}
