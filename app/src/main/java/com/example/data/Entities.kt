package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String,
    val customOrder: Int = 0, // for custom sorting/ordering for collection route
    val collectionDay: String = "Monday", // Collection Day/Group (e.g., Monday, Tuesday, etc.)
    val createdAt: Long = System.currentTimeMillis(),
    val city: String = "",
    val smsWeeklyReminder: Boolean = true,
    val smsConfirmationOfEntry: Boolean = true,
    val autoWeeklySms: Boolean = false,
    val autoWeeklyWhatsapp: Boolean = false,
    val upiNameAlias: String = "",
    val preferredLanguage: String = "English",
    val uuid: String = java.util.UUID.randomUUID().toString(),
    val lastModified: Long = System.currentTimeMillis(),
    val syncedLastSavedAt: Long = 0L,
    val status: String = "ACTIVE"
) {
    @get:androidx.room.Ignore
    val customerCode: String
        get() = "WF-${collectionDay.take(3).uppercase(java.util.Locale.US)}-${String.format(java.util.Locale.US, "%03d", id)}"
}

@Entity(
    tableName = "loan_cycles",
    foreignKeys = [
        ForeignKey(
            entity = Customer::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["customerId"])]
)
data class LoanCycle(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val customerId: Int,
    val loanAmount: Double,         // Principal amount handed over (e.g., ₹10,000)
    val interestAmount: Double = 0.0, // Optional interest amount (e.g. ₹2,000)
    val weeklyAmount: Double,       // Promised collection amount per week (e.g. ₹1,200)
    val totalWeeks: Int = 10,       // Estimated tenure in weeks
    val startDate: Long = System.currentTimeMillis(),
    val status: String = "ACTIVE",   // "ACTIVE" or "PAID"
    val notes: String = "",
    val paidAmount: Double = 0.0,    // Cached total paid amount to avoid heavy count queries
    val uuid: String = java.util.UUID.randomUUID().toString(),
    val lastModified: Long = System.currentTimeMillis(),
    val deduction: Double = 0.0
)

@Entity(
    tableName = "weekly_payments",
    foreignKeys = [
        ForeignKey(
            entity = LoanCycle::class,
            parentColumns = ["id"],
            childColumns = ["loanCycleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["loanCycleId"])]
)
data class WeeklyPayment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val loanCycleId: Int,
    val amountPaid: Double,
    val paymentDate: Long = System.currentTimeMillis(),
    val weekNumber: Int,            // Week number for tracking, e.g. 1, 2, 3...
    val notes: String = "",
    val upiTxnId: String? = null,
    val uuid: String = java.util.UUID.randomUUID().toString(),
    val lastModified: Long = System.currentTimeMillis(),
    val status: String = "ACTIVE"
)

@Entity(tableName = "edit_logs")
data class EditLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val customerId: Int,
    val customerName: String,
    val actionType: String, // "CREATE_CUSTOMER", "EDIT_CUSTOMER", "DELETE_CUSTOMER", "CREATE_LOAN", "EDIT_LOAN", "DELETE_LOAN", "RECORD_PAYMENT", "EDIT_PAYMENT", "DELETE_PAYMENT"
    val actionDescription: String,
    val previousDataJson: String = "", // Holds serialized JSON data for rollback
    val uuid: String = java.util.UUID.randomUUID().toString()
)

@Entity(tableName = "cash_balance_logs")
data class CashBalanceLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: Long = System.currentTimeMillis(),
    val actualCash: Double,
    val systemCash: Double,
    val collectionAmount: Double,
    val disbursalAmount: Double,
    val expenses: Double,
    val notes: String = ""
)

