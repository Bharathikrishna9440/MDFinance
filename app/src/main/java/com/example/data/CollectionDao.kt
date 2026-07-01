package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    // Customers
    @Query("SELECT * FROM customers ORDER BY customOrder ASC, name ASC")
    fun getAllCustomers(): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomerById(id: Int): Customer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: Customer): Long

    @Update
    suspend fun updateCustomer(customer: Customer)

    @Delete
    suspend fun deleteCustomer(customer: Customer)

    @Query("UPDATE customers SET customOrder = :newOrder WHERE id = :id")
    suspend fun updateCustomerOrder(id: Int, newOrder: Int)

    // Loan Cycles
    @Query("""
        SELECT 
            id, 
            customerId, 
            loanAmount, 
            interestAmount, 
            weeklyAmount, 
            totalWeeks, 
            startDate, 
            status, 
            notes, 
            COALESCE((SELECT SUM(amountPaid) FROM weekly_payments WHERE loanCycleId = loan_cycles.id AND status != 'DELETED'), 0.0) AS paidAmount, 
            uuid, 
            lastModified,
            deduction
        FROM loan_cycles 
        WHERE customerId = :customerId 
        ORDER BY startDate DESC
    """)
    fun getLoanCyclesForCustomer(customerId: Int): Flow<List<LoanCycle>>

    @Query("""
        SELECT 
            id, 
            customerId, 
            loanAmount, 
            interestAmount, 
            weeklyAmount, 
            totalWeeks, 
            startDate, 
            status, 
            notes, 
            COALESCE((SELECT SUM(amountPaid) FROM weekly_payments WHERE loanCycleId = loan_cycles.id AND status != 'DELETED'), 0.0) AS paidAmount, 
            uuid, 
            lastModified,
            deduction
        FROM loan_cycles 
        WHERE customerId = :customerId AND status = 'ACTIVE' 
        LIMIT 1
    """)
    suspend fun getActiveLoanCycleForCustomer(customerId: Int): LoanCycle?

    @Query("""
        SELECT 
            id, 
            customerId, 
            loanAmount, 
            interestAmount, 
            weeklyAmount, 
            totalWeeks, 
            startDate, 
            status, 
            notes, 
            COALESCE((SELECT SUM(amountPaid) FROM weekly_payments WHERE loanCycleId = loan_cycles.id AND status != 'DELETED'), 0.0) AS paidAmount, 
            uuid, 
            lastModified,
            deduction
        FROM loan_cycles 
        WHERE status = 'ACTIVE'
    """)
    fun getActiveLoanCycles(): Flow<List<LoanCycle>>

    @Query("""
        SELECT 
            id, 
            customerId, 
            loanAmount, 
            interestAmount, 
            weeklyAmount, 
            totalWeeks, 
            startDate, 
            status, 
            notes, 
            COALESCE((SELECT SUM(amountPaid) FROM weekly_payments WHERE loanCycleId = loan_cycles.id AND status != 'DELETED'), 0.0) AS paidAmount, 
            uuid, 
            lastModified,
            deduction
        FROM loan_cycles 
        ORDER BY startDate DESC
    """)
    fun getAllLoanCycles(): Flow<List<LoanCycle>>

    @Query("""
        SELECT 
            id, 
            customerId, 
            loanAmount, 
            interestAmount, 
            weeklyAmount, 
            totalWeeks, 
            startDate, 
            status, 
            notes, 
            COALESCE((SELECT SUM(amountPaid) FROM weekly_payments WHERE loanCycleId = loan_cycles.id AND status != 'DELETED'), 0.0) AS paidAmount, 
            uuid, 
            lastModified,
            deduction
        FROM loan_cycles 
        WHERE id = :id
    """)
    suspend fun getLoanCycleById(id: Int): LoanCycle?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoanCycle(loanCycle: LoanCycle): Long

    @Update
    suspend fun updateLoanCycle(loanCycle: LoanCycle)

    @Delete
    suspend fun deleteLoanCycle(loanCycle: LoanCycle)

    // Payments
    @Query("SELECT * FROM weekly_payments WHERE loanCycleId = :loanCycleId ORDER BY paymentDate DESC")
    fun getPaymentsForCycle(loanCycleId: Int): Flow<List<WeeklyPayment>>

    @Query("SELECT * FROM weekly_payments ORDER BY paymentDate DESC")
    fun getAllPayments(): Flow<List<WeeklyPayment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: WeeklyPayment): Long

    @Delete
    suspend fun deletePayment(payment: WeeklyPayment)

    @Query("DELETE FROM customers")
    suspend fun deleteAllCustomers()

    @Query("DELETE FROM loan_cycles")
    suspend fun deleteAllLoanCycles()

    @Query("DELETE FROM weekly_payments")
    suspend fun deleteAllPayments()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomers(customers: List<Customer>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoanCycles(loanCycles: List<LoanCycle>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayments(payments: List<WeeklyPayment>)

    @Query("SELECT * FROM weekly_payments WHERE id = :id LIMIT 1")
    suspend fun getPaymentById(id: Int): WeeklyPayment?

    @Query("DELETE FROM weekly_payments WHERE id = :id")
    suspend fun deletePaymentById(id: Int)

    @Query("SELECT COUNT(*) FROM weekly_payments WHERE upiTxnId = :txnId")
    suspend fun getPaymentCountByUpiTxnId(txnId: String): Int

    @Query("SELECT * FROM weekly_payments WHERE upiTxnId = :txnId LIMIT 1")
    suspend fun getPaymentByUpiTxnId(txnId: String): WeeklyPayment?

    // Edit Logs
    @Query("SELECT * FROM edit_logs ORDER BY timestamp DESC")
    fun getAllEditLogs(): Flow<List<EditLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEditLog(log: EditLog): Long

    @Delete
    suspend fun deleteEditLog(log: EditLog)

    @Query("DELETE FROM edit_logs")
    suspend fun deleteAllEditLogs()

    @Query("SELECT * FROM customers")
    suspend fun getAllCustomersOnce(): List<Customer>

    @Query("""
        SELECT 
            id, 
            customerId, 
            loanAmount, 
            interestAmount, 
            weeklyAmount, 
            totalWeeks, 
            startDate, 
            status, 
            notes, 
            COALESCE((SELECT SUM(amountPaid) FROM weekly_payments WHERE loanCycleId = loan_cycles.id AND status != 'DELETED'), 0.0) AS paidAmount, 
            uuid, 
            lastModified,
            deduction
        FROM loan_cycles
    """)
    suspend fun getAllLoanCyclesOnce(): List<LoanCycle>

    @Query("SELECT * FROM weekly_payments")
    suspend fun getAllPaymentsOnce(): List<WeeklyPayment>

    @Query("SELECT * FROM edit_logs")
    suspend fun getAllEditLogsOnce(): List<EditLog>

    @Query("SELECT * FROM weekly_payments WHERE loanCycleId = :loanCycleId")
    suspend fun getPaymentsForCycleOnce(loanCycleId: Int): List<WeeklyPayment>

    @Transaction
    suspend fun updateCustomerLastModifiedTx(customerId: Int) {
        val customer = getCustomerById(customerId)
        if (customer != null) {
            updateCustomer(customer.copy(lastModified = System.currentTimeMillis()))
        }
    }

    @Transaction
    suspend fun addWeeklyPaymentTx(payment: WeeklyPayment): Long {
        val withTime = payment.copy(lastModified = System.currentTimeMillis())
        val insertedId = insertPayment(withTime)
        val cycle = getLoanCycleById(payment.loanCycleId)
        if (cycle != null) {
            val updatedPaidAmount = cycle.paidAmount + payment.amountPaid
            val isFullyPaid = updatedPaidAmount >= (cycle.loanAmount + cycle.interestAmount)
            val updatedCycle = cycle.copy(
                paidAmount = updatedPaidAmount,
                status = if (isFullyPaid) "PAID" else cycle.status,
                lastModified = System.currentTimeMillis()
            )
            updateLoanCycle(updatedCycle)
            updateCustomerLastModifiedTx(cycle.customerId)
        }
        return insertedId
    }

    @Transaction
    suspend fun removeWeeklyPaymentTx(paymentId: Int, loanCycleId: Int) {
        val targetPayment = getPaymentById(paymentId)
        if (targetPayment != null) {
            val deletedPayment = targetPayment.copy(status = "DELETED", lastModified = System.currentTimeMillis())
            insertPayment(deletedPayment)
        }

        val cycle = getLoanCycleById(loanCycleId)
        if (cycle != null) {
            val payments = getPaymentsForCycleOnce(loanCycleId)
            val remainingAmount = payments.filter { it.id != paymentId && it.status.uppercase() != "DELETED" }.sumOf { it.amountPaid }
            
            val target = cycle.loanAmount + cycle.interestAmount
            val newStatus = if (remainingAmount < target && cycle.status == "PAID") "ACTIVE" else cycle.status
            
            val updatedCycle = cycle.copy(
                paidAmount = remainingAmount,
                status = newStatus,
                lastModified = System.currentTimeMillis()
            )
            updateLoanCycle(updatedCycle)
            updateCustomerLastModifiedTx(cycle.customerId)
        }
    }

    @Transaction
    suspend fun updateWeeklyPaymentTx(
        paymentId: Int,
        loanCycleId: Int,
        newAmount: Double,
        newWeekNumber: Int,
        newDate: Long,
        newNotes: String,
        upiTxnId: String?
    ) {
        val existing = getPaymentById(paymentId)
        val updated = WeeklyPayment(
            id = paymentId,
            loanCycleId = loanCycleId,
            amountPaid = newAmount,
            weekNumber = newWeekNumber,
            paymentDate = newDate,
            notes = newNotes,
            upiTxnId = upiTxnId ?: existing?.upiTxnId,
            lastModified = System.currentTimeMillis()
        )
        insertPayment(updated)

        val cycle = getLoanCycleById(loanCycleId)
        if (cycle != null) {
            val payments = getPaymentsForCycleOnce(loanCycleId)
            val mappedPayments = payments.map { if (it.id == paymentId) updated else it }
            val totalPaid = mappedPayments.filter { it.status.uppercase() != "DELETED" }.sumOf { it.amountPaid }
            val target = cycle.loanAmount + cycle.interestAmount
            val isPaid = totalPaid >= target
            
            val updatedCycle = cycle.copy(
                paidAmount = totalPaid,
                status = if (isPaid) "PAID" else "ACTIVE",
                lastModified = System.currentTimeMillis()
            )
            updateLoanCycle(updatedCycle)
            updateCustomerLastModifiedTx(cycle.customerId)
        }
    }

    @Transaction
    suspend fun restoreBackupTx(
        customers: List<Customer>,
        loanCycles: List<LoanCycle>,
        payments: List<WeeklyPayment>
    ) {
        deleteAllPayments()
        deleteAllLoanCycles()
        deleteAllCustomers()
        
        insertCustomers(customers)
        insertLoanCycles(loanCycles)
        insertPayments(payments)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashBalanceLog(log: CashBalanceLog): Long

    @Update
    suspend fun updateCashBalanceLog(log: CashBalanceLog)

    @Delete
    suspend fun deleteCashBalanceLog(log: CashBalanceLog)

    @Query("SELECT * FROM cash_balance_logs ORDER BY date DESC LIMIT 1")
    suspend fun getLastCashBalanceLog(): CashBalanceLog?

    @Query("SELECT * FROM cash_balance_logs ORDER BY date DESC")
    fun getAllCashBalanceLogsFlow(): Flow<List<CashBalanceLog>>

    @Query("SELECT * FROM cash_balance_logs")
    suspend fun getAllCashBalanceLogsOnce(): List<CashBalanceLog>

    @Query("DELETE FROM cash_balance_logs")
    suspend fun deleteAllCashBalanceLogs()
}
