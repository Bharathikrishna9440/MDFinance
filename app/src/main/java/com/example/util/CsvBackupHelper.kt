package com.example.util

import android.content.Context
import androidx.room.withTransaction
import com.example.data.*
import java.io.File
import java.lang.StringBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object CsvBackupHelper {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /**
     * Escapes standard CSV values in accordance with RFC-4180.
     */
    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
        return value
    }

    /**
     * Generates a CSV file content for a list of customers belonging to a specific collection day.
     * If dayFilter is "ALL" (case-insensitive), it exports all customers (excluding Friday).
     */
    fun generateCsvString(
        customers: List<Customer>,
        loanCycles: List<LoanCycle>,
        payments: List<WeeklyPayment>,
        dayFilter: String
    ): String {
        val activeCustomers = customers.filter { it.status.uppercase() != "DELETED" }
        val validLoanCycles = loanCycles.filter { it.status.uppercase() != "DELETED" }
        val validPayments = payments.filter { it.status.uppercase() != "DELETED" }

        val filteredCustomers = if (dayFilter.trim().equals("ALL", ignoreCase = true)) {
            activeCustomers.filter { !it.collectionDay.trim().equals("Friday", ignoreCase = true) }
        } else {
            activeCustomers.filter { it.collectionDay.trim().equals(dayFilter.trim(), ignoreCase = true) }
        }

        val groupCustomerIds = filteredCustomers.map { it.id }.toSet()
        val totalActiveLoans = validLoanCycles.count { it.customerId in groupCustomerIds && it.status == "ACTIVE" }

        val s = StringBuilder()
        
        s.append("# Total Customers: ${filteredCustomers.size}, Total Active Loans: $totalActiveLoans\n")
        s.append("# Exported On: ${dateTimeFormat.format(Date())}\n")
        
        // 1. Title / Header following mandatory schema rules (No UPI handle column)
        s.append("Customer UUID,Route No (Sort Order),Client Name,Phone Number,City,SMS Settings (Weekly & Entry Confirmation),Loan ID (UUID),Amount Disbursed,Principle (₹),Interest (₹),Date of Dispersal")
        for (w in 1..30) {
            s.append(",Week $w Date & Time,Week $w Amt Received")
        }
        s.append(",Collection Day\n")

        for (cust in filteredCustomers) {
            val safeUuid = escapeCsv(cust.uuid)
            val customOrder = cust.customOrder.toString()
            val safeName = escapeCsv(cust.name)
            val safePhone = escapeCsv(cust.phone)
            val safeCity = escapeCsv(cust.city)
            val smsSettings = "Weekly Reminder: ${if (cust.smsWeeklyReminder) "YES" else "NO"}, Entry Confirmation: ${if (cust.smsConfirmationOfEntry) "YES" else "NO"}"
            val safeSmsSettings = escapeCsv(smsSettings)

            val custCycles = validLoanCycles.filter { it.customerId == cust.id }.sortedByDescending { it.id }

            if (custCycles.isEmpty()) {
                s.append("$safeUuid,$customOrder,$safeName,$safePhone,$safeCity,$safeSmsSettings,")
                s.append(",,,,") 
                for (w in 1..30) {
                    s.append(",,")
                }
                s.append(",${escapeCsv(cust.collectionDay)}\n")
            } else {
                for (cycle in custCycles) {
                    val loanUuid = cycle.uuid
                    val amountDisbursed = (cycle.loanAmount - cycle.deduction).toString()
                    val principle = cycle.loanAmount.toString()
                    val interest = cycle.interestAmount.toString()
                    val dispersalDate = dateFormat.format(Date(cycle.startDate))

                    s.append("$safeUuid,$customOrder,$safeName,$safePhone,$safeCity,$safeSmsSettings,")
                    s.append("${escapeCsv(loanUuid)},$amountDisbursed,$principle,$interest,$dispersalDate")

                    val cyclePayments = validPayments.filter { it.loanCycleId == cycle.id }

                    for (w in 1..30) {
                        val p = cyclePayments.find { it.weekNumber == w }
                        if (p != null) {
                            val pDate = dateTimeFormat.format(Date(p.paymentDate))
                            val pAmt = p.amountPaid.toString()
                            s.append(",${escapeCsv(pDate)},$pAmt")
                        } else {
                            s.append(",,")
                        }
                    }
                    s.append(",${escapeCsv(cust.collectionDay)}\n")
                }
            }
        }

        return s.toString()
    }

    /**
     * Robust RFC-4180 parser to split entries correctly.
     */
    fun parseCsvRows(csvText: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        var currentField = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < csvText.length) {
            val c = csvText[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < csvText.length && csvText[i + 1] == '"') {
                        currentField.append('"')
                        i++ 
                    } else {
                        inQuotes = false
                    }
                } else {
                    currentField.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> {
                        currentRow.add(currentField.toString())
                        currentField = StringBuilder()
                    }
                    '\n', '\r' -> {
                        currentRow.add(currentField.toString())
                        if (currentRow.isNotEmpty() && currentRow.any { it.isNotBlank() }) {
                            result.add(currentRow.toList())
                        }
                        currentRow.clear()
                        currentField = StringBuilder()
                        if (c == '\r' && i + 1 < csvText.length && csvText[i + 1] == '\n') {
                            i++
                        }
                    }
                    else -> currentField.append(c)
                }
            }
            i++
        }
        if (currentRow.isNotEmpty() || currentField.isNotEmpty()) {
            currentRow.add(currentField.toString())
            if (currentRow.isNotEmpty() && currentRow.any { it.isNotBlank() }) {
                result.add(currentRow.toList())
            }
        }
        return result
    }

    private fun generateRandomDateBeforeOneYear(): Long {
        val oneYearInMs = 365L * 24L * 60L * 60L * 1000L
        val randomDaysInMs = (1..60).random().toLong() * 24L * 60L * 60L * 1000L
        val randomTimeInMs = (0..86399).random().toLong() * 1000L
        return System.currentTimeMillis() - oneYearInMs - randomDaysInMs - randomTimeInMs
    }

    private fun parseDateStringWithFallback(dateStr: String, fallback: () -> Long): Long {
        if (dateStr.isBlank()) return fallback()
        return try {
            dateFormat.parse(dateStr)?.time ?: fallback()
        } catch (e: Exception) {
            try {
                dateTimeFormat.parse(dateStr)?.time ?: fallback()
            } catch (e2: Exception) {
                dateStr.toLongOrNull() ?: fallback()
            }
        }
    }

    /**
     * Imports a CSV into a specific day's records, or all groups dynamically from the file.
     */
    suspend fun importCsvIntoDay(
        context: Context,
        csvText: String,
        dayGroup: String,
        db: AppDatabase
    ): Boolean {
        return try {
            val rows = parseCsvRows(csvText)
            if (rows.isEmpty()) throw Exception("Selected backup file is empty.")

            var headerIndex = -1
            for (idx in rows.indices) {
                val row = rows[idx]
                if (row.getOrNull(2)?.contains("Client Name", ignoreCase = true) == true) {
                    headerIndex = idx
                    break
                }
            }

            val dataRows = if (headerIndex != -1) {
                rows.drop(headerIndex + 1)
            } else {
                rows
            }

            var expectedCustomers = -1
            var expectedActiveLoans = -1

            for (row in rows) {
                val rowStr = row.joinToString(",")
                if (rowStr.contains("Total Customers", ignoreCase = true) && 
                    (rowStr.contains("Total Active", ignoreCase = true) || rowStr.contains("Total_Active", ignoreCase = true))
                ) {
                    try {
                        val tcRegex = "Total\\s*Customers\\s*[=:]?\\s*(\\d+)".toRegex(RegexOption.IGNORE_CASE)
                        val taRegex = "Total\\s*Active\\s*(?:Loans)?\\s*[=:]?\\s*(\\d+)".toRegex(RegexOption.IGNORE_CASE)
                        
                        tcRegex.find(rowStr)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
                            expectedCustomers = it
                        }
                        taRegex.find(rowStr)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
                            expectedActiveLoans = it
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            val importedCustomerUuids = mutableSetOf<String>()
            val importedActiveLoanUuids = mutableSetOf<String>()

            val allCustomersCache = db.collectionDao().getAllCustomersOnce().toMutableList()
            val allLoansCache = db.collectionDao().getAllLoanCyclesOnce().toMutableList()
            val allPaymentsCache = db.collectionDao().getAllPaymentsOnce().toMutableList()

            db.withTransaction {
                val dao = db.collectionDao()

                for (row in dataRows) {
                    val clientName = row.getOrNull(2)?.trim() ?: ""
                    if (clientName.isBlank() || clientName.equals("Client Name", ignoreCase = true) || clientName.contains("Total Customers", ignoreCase = true)) continue

                    val rowGroup = if (dayGroup.trim().equals("ALL", ignoreCase = true)) {
                        val parsedVal = row.lastOrNull()?.trim() ?: ""
                        val validDays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Saturday", "Sunday mrg", "Sunday eve")
                        val matchedDay = validDays.find { it.equals(parsedVal, ignoreCase = true) }
                        
                        // Strict validation matching dynamic pairing lengths (11 + 2 * 30 = 71 -> index 71 is collection day)
                        matchedDay ?: row.getOrNull(71)?.trim()?.let { col71 ->
                            validDays.find { it.equals(col71, ignoreCase = true) }
                        } ?: "Monday"
                    } else {
                        dayGroup
                    }

                    // Rule 1 Check: Friday must be completely ignored/excluded from sheets and operations
                    if (rowGroup.trim().equals("Friday", ignoreCase = true)) continue

                    val customerUuid = row.getOrNull(0)?.trim().let { if (it.isNullOrBlank()) UUID.randomUUID().toString() else it }
                    importedCustomerUuids.add(customerUuid)

                    val customOrder = row.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
                    val phone = row.getOrNull(3)?.trim() ?: ""
                    val city = row.getOrNull(4)?.trim() ?: ""
                    val smsSettingsStr = row.getOrNull(5)?.trim() ?: ""

                    var smsWeekly = false
                    var smsConf = false
                    if (smsSettingsStr.isNotBlank()) {
                        smsWeekly = smsSettingsStr.contains("Weekly Reminder: YES", ignoreCase = true)
                        smsConf = smsSettingsStr.contains("Entry Confirmation: YES", ignoreCase = true)
                    }

                    var existingCustomer = allCustomersCache.find { it.uuid == customerUuid }
                    if (existingCustomer == null && customerUuid.isBlank()) {
                        existingCustomer = allCustomersCache.find { 
                            it.name.trim().equals(clientName.trim(), ignoreCase = true) && 
                            it.collectionDay.trim().equals(rowGroup.trim(), ignoreCase = true) 
                        }
                    }

                    val finalCustomerId: Int
                    if (existingCustomer != null) {
                        finalCustomerId = existingCustomer.id
                        val updatedCustomer = existingCustomer.copy(
                            name = clientName,
                            phone = phone,
                            city = city,
                            collectionDay = rowGroup,
                            customOrder = customOrder,
                            smsWeeklyReminder = smsWeekly,
                            smsConfirmationOfEntry = smsConf,
                            lastModified = System.currentTimeMillis()
                        )
                        dao.updateCustomer(updatedCustomer)
                        val cacheIndex = allCustomersCache.indexOfFirst { it.id == finalCustomerId }
                        if (cacheIndex != -1) {
                            allCustomersCache[cacheIndex] = updatedCustomer
                        }
                    } else {
                        val newCustomer = Customer(
                            name = clientName,
                            phone = phone,
                            city = city,
                            customOrder = customOrder,
                            collectionDay = rowGroup,
                            smsWeeklyReminder = smsWeekly,
                            smsConfirmationOfEntry = smsConf,
                            uuid = customerUuid,
                            lastModified = System.currentTimeMillis()
                        )
                        val insertedId = dao.insertCustomer(newCustomer).toInt()
                        finalCustomerId = insertedId
                        allCustomersCache.add(newCustomer.copy(id = insertedId))
                    }

                    // Schema layout parsing mapping definitions
                    val loanUuid = row.getOrNull(6)?.trim()
                    val amountDisbursed = row.getOrNull(7)?.trim()?.toDoubleOrNull() ?: 0.0
                    val principle = row.getOrNull(8)?.trim()?.toDoubleOrNull() ?: amountDisbursed
                    val interest = row.getOrNull(9)?.trim()?.toDoubleOrNull() ?: 0.0
                    val deduction = maxOf(0.0, principle - amountDisbursed)
                    val dispersalDateStr = row.getOrNull(10)?.trim() ?: ""

                    if (!loanUuid.isNullOrBlank() || principle > 0.0 || amountDisbursed > 0.0 || interest > 0.0) {
                        val finalLoanUuid = if (loanUuid.isNullOrBlank()) UUID.randomUUID().toString() else loanUuid

                        var existingLoan = allLoansCache.find { it.uuid == finalLoanUuid }
                        if (existingLoan == null && loanUuid.isNullOrBlank()) {
                            existingLoan = allLoansCache.find { it.customerId == finalCustomerId && it.status == "ACTIVE" }
                        }

                        val maxPaymentWeek = (1..30).filter { w ->
                            val amtColIdx = 12 + 2 * (w - 1)
                            val pAmtStr = row.getOrNull(amtColIdx)?.trim() ?: ""
                            (pAmtStr.toDoubleOrNull() ?: 0.0) > 0.0
                        }.maxOrNull() ?: 0

                        val finalTotalWeeks = if (existingLoan != null) {
                            existingLoan.totalWeeks
                        } else {
                            maxOf(10, maxPaymentWeek)
                        }

                        val finalWeeklyAmount = if (existingLoan != null) {
                            existingLoan.weeklyAmount
                        } else {
                            (principle + interest) / finalTotalWeeks.toDouble()
                        }

                        val finalLoanId: Int
                        if (existingLoan != null) {
                            finalLoanId = existingLoan.id
                            val updatedLoan = existingLoan.copy(
                                loanAmount = principle,
                                interestAmount = interest,
                                weeklyAmount = finalWeeklyAmount,
                                totalWeeks = finalTotalWeeks,
                                startDate = parseDateStringWithFallback(dispersalDateStr) { generateRandomDateBeforeOneYear() },
                                lastModified = System.currentTimeMillis(),
                                deduction = deduction
                            )
                            dao.updateLoanCycle(updatedLoan)
                            val cacheIdx = allLoansCache.indexOfFirst { it.id == finalLoanId }
                            if (cacheIdx != -1) {
                                allLoansCache[cacheIdx] = updatedLoan
                            }
                        } else {
                            val newLoan = LoanCycle(
                                customerId = finalCustomerId,
                                loanAmount = principle,
                                interestAmount = interest,
                                weeklyAmount = finalWeeklyAmount,
                                totalWeeks = finalTotalWeeks,
                                startDate = parseDateStringWithFallback(dispersalDateStr) { generateRandomDateBeforeOneYear() },
                                status = "ACTIVE",
                                uuid = finalLoanUuid,
                                lastModified = System.currentTimeMillis(),
                                deduction = deduction
                            )
                            val insertedId = dao.insertLoanCycle(newLoan).toInt()
                            finalLoanId = insertedId
                            allLoansCache.add(newLoan.copy(id = insertedId))
                        }

                        var sumPaid = 0.0
                        val existingPayments = allPaymentsCache.filter { it.loanCycleId == finalLoanId }

                        for (w in 1..30) {
                            val dateColIdx = 11 + 2 * (w - 1)
                            val amtColIdx = 12 + 2 * (w - 1)

                            val pDateStr = row.getOrNull(dateColIdx)?.trim() ?: ""
                            val pAmtStr = row.getOrNull(amtColIdx)?.trim() ?: ""
                            val pAmt = pAmtStr.toDoubleOrNull() ?: 0.0

                            val existingP = existingPayments.find { it.weekNumber == w && it.status.uppercase() != "DELETED" }
                            if (pAmt > 0.0) {
                                sumPaid += pAmt
                                if (existingP != null) {
                                    val updatedP = existingP.copy(
                                        amountPaid = pAmt,
                                        paymentDate = parseDateStringWithFallback(pDateStr) { generateRandomDateBeforeOneYear() },
                                        lastModified = System.currentTimeMillis()
                                    )
                                    dao.insertPayment(updatedP)
                                    val idx = allPaymentsCache.indexOfFirst { it.id == existingP.id }
                                    if (idx != -1) {
                                        allPaymentsCache[idx] = updatedP
                                    }
                                } else {
                                    val newP = WeeklyPayment(
                                        loanCycleId = finalLoanId,
                                        amountPaid = pAmt,
                                        paymentDate = parseDateStringWithFallback(pDateStr) { generateRandomDateBeforeOneYear() },
                                        weekNumber = w,
                                        uuid = UUID.randomUUID().toString(),
                                        lastModified = System.currentTimeMillis(),
                                        status = "ACTIVE"
                                    )
                                    val insertedId = dao.insertPayment(newP).toInt()
                                    allPaymentsCache.add(newP.copy(id = insertedId))
                                }
                            } else {
                                if (existingP != null) {
                                    val updatedP = existingP.copy(
                                        status = "DELETED",
                                        lastModified = System.currentTimeMillis()
                                    )
                                    dao.insertPayment(updatedP)
                                    val idx = allPaymentsCache.indexOfFirst { it.id == existingP.id }
                                    if (idx != -1) {
                                        allPaymentsCache[idx] = updatedP
                                    }
                                }
                            }
                        }

                        val currentLoan = allLoansCache.find { it.id == finalLoanId }
                        if (currentLoan != null) {
                            val targetSettleAmount = currentLoan.loanAmount + currentLoan.interestAmount
                            val isPaidOff = sumPaid >= targetSettleAmount
                            val isActive = !isPaidOff
                            val updatedLoan = currentLoan.copy(
                                paidAmount = sumPaid,
                                status = if (isPaidOff) "PAID" else "ACTIVE",
                                lastModified = System.currentTimeMillis()
                            )
                            dao.updateLoanCycle(updatedLoan)
                            val cacheIdx = allLoansCache.indexOfFirst { it.id == finalLoanId }
                            if (cacheIdx != -1) {
                                allLoansCache[cacheIdx] = updatedLoan
                            }
                            if (isActive) {
                                importedActiveLoanUuids.add(finalLoanUuid)
                            }
                        }
                    }
                }

                if (expectedCustomers != -1 && importedCustomerUuids.size != expectedCustomers) {
                    android.util.Log.w("CsvImport", "Verification count error: Metadata expected $expectedCustomers clients, parsed ${importedCustomerUuids.size}.")
                }
                if (expectedActiveLoans != -1 && importedActiveLoanUuids.size != expectedActiveLoans) {
                    android.util.Log.w("CsvImport", "Verification count error: Metadata expected $expectedActiveLoans active loans, parsed ${importedActiveLoanUuids.size}.")
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
