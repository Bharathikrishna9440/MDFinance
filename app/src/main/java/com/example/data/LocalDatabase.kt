package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase

data class WeekPayment(
    val weekNumber: Int,
    val paymentDate: Long,
    val paymentMode: String // restricted to "Cash" or "Bank"
) {
    fun toJsonObject(): org.json.JSONObject {
        val obj = org.json.JSONObject()
        obj.put("weekNumber", weekNumber)
        obj.put("paymentDate", paymentDate)
        obj.put("paymentMode", paymentMode)
        return obj
    }

    companion object {
        fun fromJsonObject(obj: org.json.JSONObject): WeekPayment {
            return WeekPayment(
                weekNumber = obj.optInt("weekNumber", 1),
                paymentDate = obj.optLong("paymentDate", 0L),
                paymentMode = obj.optString("paymentMode", "Cash")
            )
        }
    }
}

@Entity(tableName = "customer_entities")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val customerId: Int = 0,
    val name: String,
    val mobileNumber: String = "",
    val city: String = "",
    val smsSettingEnabled1: Boolean = false,
    val smsSettingEnabled2: Boolean = false,
    val languageSelection: String = "English",
    val loanId: String = "",
    val routeId: String = "",
    val routeDay: String = "Monday", // "Sunday mrg", "Sunday eve", "Monday", "Tuesday", "Wednesday", "Thursday", "Saturday"
    val dispersalDate: Long = 0L,
    val principalDispersalAmount: Double = 0.0,
    val interestDispersalAmount: Double = 0.0,
    val currentBalance: Double = 0.0,
    val weekPaymentsJson: String = defaultWeekPaymentsJson()
) {
    val initialBalance: Double get() = principalDispersalAmount + interestDispersalAmount

    fun getWeekPaymentsList(): List<WeekPayment> {
        val list = mutableListOf<WeekPayment>()
        try {
            if (weekPaymentsJson.isNotBlank()) {
                val arr = org.json.JSONArray(weekPaymentsJson)
                for (i in 0 until arr.length()) {
                    list.add(WeekPayment.fromJsonObject(arr.getJSONObject(i)))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Self-heal/Guarantee exactly 30 chronological elements
        if (list.size < 30) {
            val existingWeeks = list.map { it.weekNumber }.toSet()
            for (w in 1..30) {
                if (!existingWeeks.contains(w)) {
                    list.add(WeekPayment(weekNumber = w, paymentDate = 0L, paymentMode = "Cash"))
                }
            }
            list.sortBy { it.weekNumber }
        }
        return list.take(30)
    }

    fun copyWithUpdatedPayment(weekNum: Int, pDate: Long, pMode: String): CustomerEntity {
        val list = getWeekPaymentsList().toMutableList()
        val index = list.indexOfFirst { it.weekNumber == weekNum }
        val updatedItem = WeekPayment(weekNumber = weekNum, paymentDate = pDate, paymentMode = pMode)
        if (index != -1) {
            list[index] = updatedItem
        } else {
            list.add(updatedItem)
        }
        list.sortBy { it.weekNumber }
        val serialized = weekPaymentsListToJson(list.take(30))
        return this.copy(weekPaymentsJson = serialized)
    }

    companion object {
        fun weekPaymentsListToJson(list: List<WeekPayment>): String {
            val arr = org.json.JSONArray()
            for (item in list) {
                arr.put(item.toJsonObject())
            }
            return arr.toString()
        }

        fun defaultWeekPaymentsJson(): String {
            val defaultList = (1..30).map { WeekPayment(weekNumber = it, paymentDate = 0L, paymentMode = "Cash") }
            return weekPaymentsListToJson(defaultList)
        }
    }
}

@Entity(tableName = "transaction_entities")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val transactionId: Int = 0,
    val syncUuid: String,
    val customerId: Int,
    val amountCollected: Double,
    val timestamp: Long = System.currentTimeMillis()
)

@Database(
    entities = [CustomerEntity::class, TransactionEntity::class],
    version = 2, // Upgraded version for extended database fields
    exportSchema = false
)
abstract class MicrofinanceDatabase : RoomDatabase() {
    abstract fun microfinanceDao(): MicrofinanceDao

    companion object {
        @Volatile
        private var INSTANCE: MicrofinanceDatabase? = null

        fun getDatabase(context: Context): MicrofinanceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MicrofinanceDatabase::class.java,
                    "microfinance_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
