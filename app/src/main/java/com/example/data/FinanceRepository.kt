package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class FinanceRepository(private val dao: CollectionDao) {

    val allCustomers: Flow<List<Customer>> = dao.getAllCustomers()
    val activeLoanCycles: Flow<List<LoanCycle>> = dao.getActiveLoanCycles()
    val allLoanCycles: Flow<List<LoanCycle>> = dao.getAllLoanCycles()
    val allPayments: Flow<List<WeeklyPayment>> = dao.getAllPayments()
    val allEditLogs: Flow<List<EditLog>> = dao.getAllEditLogs()
    val allCashBalanceLogs: Flow<List<CashBalanceLog>> = dao.getAllCashBalanceLogsFlow()

    suspend fun addCashBalanceLog(log: CashBalanceLog): Long {
        return dao.insertCashBalanceLog(log)
    }

    suspend fun updateCashBalanceLog(log: CashBalanceLog) {
        dao.updateCashBalanceLog(log)
    }

    suspend fun deleteCashBalanceLog(log: CashBalanceLog) {
        dao.deleteCashBalanceLog(log)
    }

    suspend fun getLastCashBalanceLog(): CashBalanceLog? {
        return dao.getLastCashBalanceLog()
    }

    var deviceUsername: String = "Device User"

    suspend fun addEditLog(log: EditLog): Long {
        val decoratedDesc = if (log.actionDescription.contains(" (by ")) {
            log.actionDescription
        } else {
            "${log.actionDescription} (by $deviceUsername)"
        }
        return dao.insertEditLog(log.copy(actionDescription = decoratedDesc))
    }

    suspend fun deleteEditLog(log: EditLog) {
        dao.deleteEditLog(log)
    }

    suspend fun clearEditLogs() {
        dao.deleteAllEditLogs()
    }

    suspend fun getCustomerById(id: Int): Customer? = dao.getCustomerById(id)

    suspend fun addCustomer(customer: Customer): Long {
        val withTime = customer.copy(lastModified = System.currentTimeMillis())
        return dao.insertCustomer(withTime)
    }

    suspend fun updateCustomer(customer: Customer) {
        val withTime = customer.copy(lastModified = System.currentTimeMillis())
        dao.updateCustomer(withTime)
    }

    suspend fun deleteCustomer(customer: Customer) {
        dao.deleteCustomer(customer)
    }

    suspend fun updateCustomerOrder(id: Int, order: Int) {
        dao.updateCustomerOrder(id, order)
        updateCustomerLastModified(id)
    }

    fun getLoanCyclesForCustomer(customerId: Int): Flow<List<LoanCycle>> {
        return dao.getLoanCyclesForCustomer(customerId)
    }

    suspend fun getActiveLoanCycleForCustomer(customerId: Int): LoanCycle? {
        return dao.getActiveLoanCycleForCustomer(customerId)
    }

    suspend fun getLoanCycleById(id: Int): LoanCycle? = dao.getLoanCycleById(id)

    suspend fun addLoanCycle(cycle: LoanCycle): Long {
        val withTime = cycle.copy(lastModified = System.currentTimeMillis())
        val insertedId = dao.insertLoanCycle(withTime)
        updateCustomerLastModified(cycle.customerId)
        return insertedId
    }

    suspend fun updateLoanCycle(cycle: LoanCycle) {
        val withTime = cycle.copy(lastModified = System.currentTimeMillis())
        dao.updateLoanCycle(withTime)
        updateCustomerLastModified(cycle.customerId)
    }

    suspend fun deleteLoanCycle(cycle: LoanCycle) {
        dao.deleteLoanCycle(cycle)
        updateCustomerLastModified(cycle.customerId)
    }

    fun getPaymentsForCycle(loanCycleId: Int): Flow<List<WeeklyPayment>> {
        return dao.getPaymentsForCycle(loanCycleId)
    }

    suspend fun addWeeklyPayment(payment: WeeklyPayment): Long {
        return dao.addWeeklyPaymentTx(payment)
    }

    suspend fun removeWeeklyPayment(paymentId: Int, loanCycleId: Int) {
        dao.removeWeeklyPaymentTx(paymentId, loanCycleId)
    }

    suspend fun getPaymentCountByUpiTxnId(txnId: String): Int {
        return dao.getPaymentCountByUpiTxnId(txnId)
    }

    suspend fun getPaymentByUpiTxnId(txnId: String): WeeklyPayment? {
        return dao.getPaymentByUpiTxnId(txnId)
    }

    suspend fun updateWeeklyPayment(
        paymentId: Int,
        loanCycleId: Int,
        newAmount: Double,
        newWeekNumber: Int,
        newDate: Long,
        newNotes: String,
        upiTxnId: String? = null
    ) {
        dao.updateWeeklyPaymentTx(
            paymentId = paymentId,
            loanCycleId = loanCycleId,
            newAmount = newAmount,
            newWeekNumber = newWeekNumber,
            newDate = newDate,
            newNotes = newNotes,
            upiTxnId = upiTxnId
        )
    }

    suspend fun insertWeeklyPayment(payment: WeeklyPayment) {
        dao.insertPayment(payment)
    }

    private suspend fun updateCustomerLastModified(customerId: Int) {
        val customer = dao.getCustomerById(customerId)
        if (customer != null) {
            dao.updateCustomer(customer.copy(lastModified = System.currentTimeMillis()))
        }
    }

    suspend fun restoreBackup(
        customers: List<Customer>,
        loanCycles: List<LoanCycle>,
        payments: List<WeeklyPayment>
    ) {
        dao.restoreBackupTx(customers, loanCycles, payments)
    }

    suspend fun populateMissingUuids() {
        // 1. Customers
        val customersList = dao.getAllCustomersOnce()
        val customerUuidsSeen = mutableSetOf<String>()
        for (c in customersList) {
            if (c.uuid.isBlank() || c.uuid == "") {
                val newUuid = java.util.UUID.randomUUID().toString()
                dao.updateCustomer(c.copy(uuid = newUuid))
                customerUuidsSeen.add(newUuid)
            } else {
                if (customerUuidsSeen.contains(c.uuid)) {
                    // Delete duplicate customer record to self-heal DB
                    dao.deleteCustomer(c)
                } else {
                    customerUuidsSeen.add(c.uuid)
                }
            }
        }

        // 2. Loans
        val loansList = dao.getAllLoanCyclesOnce()
        val loanUuidsSeen = mutableSetOf<String>()
        for (l in loansList) {
            if (l.uuid.isBlank() || l.uuid == "") {
                val newUuid = java.util.UUID.randomUUID().toString()
                dao.updateLoanCycle(l.copy(uuid = newUuid))
                loanUuidsSeen.add(newUuid)
            } else {
                if (loanUuidsSeen.contains(l.uuid)) {
                    // Delete duplicate loan cycle to self-heal DB
                    dao.deleteLoanCycle(l)
                } else {
                    loanUuidsSeen.add(l.uuid)
                }
            }
        }

        // 3. Payments
        val paymentsList = dao.getAllPaymentsOnce()
        val paymentUuidsSeen = mutableSetOf<String>()
        val paymentSignaturesSeen = mutableSetOf<String>()
        for (p in paymentsList) {
            val signature = "${p.loanCycleId}_${p.weekNumber}_${p.amountPaid}_${p.paymentDate}"
            if (p.uuid.isBlank() || p.uuid == "") {
                val newUuid = java.util.UUID.randomUUID().toString()
                dao.insertPayment(p.copy(uuid = newUuid))
                paymentUuidsSeen.add(newUuid)
                paymentSignaturesSeen.add(signature)
            } else {
                if (paymentUuidsSeen.contains(p.uuid) || paymentSignaturesSeen.contains(signature)) {
                    // Delete duplicate payment to self-heal DB
                    dao.deletePayment(p)
                } else {
                    paymentUuidsSeen.add(p.uuid)
                    paymentSignaturesSeen.add(signature)
                }
            }
        }

        // 4. Edit Logs
        val auditList = dao.getAllEditLogsOnce()
        val auditUuidsSeen = mutableSetOf<String>()
        val auditSignaturesSeen = mutableSetOf<String>()
        for (a in auditList) {
            val signature = "${a.timestamp}_${a.actionType}_${a.customerId}"
            if (a.uuid.isBlank() || a.uuid == "") {
                val newUuid = java.util.UUID.randomUUID().toString()
                dao.insertEditLog(a.copy(uuid = newUuid))
                auditUuidsSeen.add(newUuid)
                auditSignaturesSeen.add(signature)
            } else {
                if (auditUuidsSeen.contains(a.uuid) || auditSignaturesSeen.contains(signature)) {
                    // Delete duplicate edit log to self-heal DB
                    dao.deleteEditLog(a)
                } else {
                    auditUuidsSeen.add(a.uuid)
                    auditSignaturesSeen.add(signature)
                }
            }
        }
    }

    // --- New Local-First Storage Architecture & Cloud Sync Repository Layer ---
    private var microfinanceDatabase: MicrofinanceDatabase? = null
    private var microfinanceDao: MicrofinanceDao? = null

    fun initMicrofinance(context: android.content.Context) {
        if (microfinanceDatabase == null) {
            val db = MicrofinanceDatabase.getDatabase(context)
            microfinanceDatabase = db
            microfinanceDao = db.microfinanceDao()
        }
    }

    fun getMicrofinanceDao(context: android.content.Context): MicrofinanceDao {
        initMicrofinance(context)
        return microfinanceDao!!
    }

    // Modern local storage queries using Flow
    fun getAllCustomerEntities(context: android.content.Context): Flow<List<CustomerEntity>> {
        return getMicrofinanceDao(context).getAllCustomers()
    }

    fun filterCustomerEntitiesByRoute(context: android.content.Context, route: String): Flow<List<CustomerEntity>> {
        return getMicrofinanceDao(context).filterCustomersByRoute(route)
    }

    fun getTransactionHistories(context: android.content.Context): Flow<List<TransactionEntity>> {
        return getMicrofinanceDao(context).getTransactionHistories()
    }
}

