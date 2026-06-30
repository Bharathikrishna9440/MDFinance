package com.example.service

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern
import java.util.Locale

class NotificationListener : NotificationListenerService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onCreate() {
        super.onCreate()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        // Disabled UPI Integrations and notification readers by user request
        return
    }

    private fun parseTransaction(text: String, isPaytm: Boolean): Triple<Double, String, String>? {
        // Find 12-digit UPI UTR
        val utrMatcher = Pattern.compile("\\b\\d{12}\\b").matcher(text)
        val txnId = if (utrMatcher.find()) utrMatcher.group() else ""

        // Find Amount
        // Support: Rs. 1200, Rs 1200, ₹1200, ₹ 1200, is credited with 1200.00 etc
        val amtMatcher = Pattern.compile("(?:Rs\\.?|₹)\\s*([\\d,]+(?:\\.\\d+)?)").matcher(text)
        var amount = 0.0
        if (amtMatcher.find()) {
            val amtStr = amtMatcher.group(1)?.replace(",", "") ?: ""
            amount = amtStr.toDoubleOrNull() ?: 0.0
        } else {
            // Also search for credited / received amount text
            val fallbackAmtMatcher = Pattern.compile("credited with ([\\d,]+(?:\\.\\d+)?)").matcher(text.lowercase())
            if (fallbackAmtMatcher.find()) {
                val amtStr = fallbackAmtMatcher.group(1)?.replace(",", "") ?: ""
                amount = amtStr.toDoubleOrNull() ?: 0.0
            }
        }

        if (amount <= 0.0) return null

        // Find sender / payer name
        var senderName = "UPI Payer"
        if (isPaytm) {
            // Received ₹100 from John Doe
            val paytmMatcher1 = Pattern.compile("Received (?:Rs\\.?|₹)\\s*[\\d,.]+\\s+from ([^,.]+)", Pattern.CASE_INSENSITIVE).matcher(text)
            if (paytmMatcher1.find()) {
                senderName = paytmMatcher1.group(1)?.trim() ?: "UPI Payer"
            } else {
                val paytmMatcher2 = Pattern.compile("payment of (?:Rs\\.?|₹)\\s*[\\d,.]+\\s+from ([^,.]+)", Pattern.CASE_INSENSITIVE).matcher(text)
                if (paytmMatcher2.find()) {
                    senderName = paytmMatcher2.group(1)?.trim() ?: "UPI Payer"
                }
            }
        } else {
            // Bank of India / SMS style: credited with Rs. 1200.00 by UPI Ref No 499912345678 on 12-06-2026 ...
            // Or Transfer from John Doe
            val boiMatcher = Pattern.compile("(?:from|transfer by|ref|no)\\s+(?:\\d{12})?\\s*/?\\s*([^,/\\n]+)", Pattern.CASE_INSENSITIVE).matcher(text)
            if (boiMatcher.find()) {
                senderName = boiMatcher.group(1)?.trim() ?: "UPI Payer"
            }
        }

        // Clean trailing prepositions or timestamp indicators from SMS parser
        senderName = senderName
            .replace("(?i)\\bon\\b".toRegex(), "")
            .replace("(?i)\\bdate\\b".toRegex(), "")
            .replace("(?i)\\bval\\b".toRegex(), "")
            .replace("[0-9]{2}-[0-9]{2}-[0-9]{2,4}".toRegex(), "") // Strip dates
            .trim()

        // Clean senderName from common trailing SMS noise
        if (senderName.uppercase().contains("ON DATE") || senderName.uppercase().contains("REF NO") || senderName.uppercase().contains("UPI REF")) {
            senderName = "UPI Payer"
        }

        return Triple(amount, txnId, senderName)
    }

    private suspend fun processTransaction(amount: Double, txnId: String, senderName: String) {
        val db = AppDatabase.getDatabase(applicationContext)
        val repo = FinanceRepository(db.collectionDao())

        // 1. Check duplicate checks
        val count = repo.getPaymentCountByUpiTxnId(txnId)
        if (count > 0) {
            Log.d("NotificationListener", "Discarding duplicate transaction: $txnId")
            return
        }

        // Check if txn is in the ignored list inside SharedPreferences
        val prefs = applicationContext.getSharedPreferences("weekly_finance_prefs", android.content.Context.MODE_PRIVATE)
        val ignoredStr = prefs.getString("ignored_upi_txns", "[]") ?: "[]"
        val ignoredArray = try { JSONArray(ignoredStr) } catch(e: Exception) { JSONArray() }
        for (i in 0 until ignoredArray.length()) {
            if (ignoredArray.optString(i) == txnId) {
                Log.d("NotificationListener", "Current transaction is ignored: $txnId")
                return
            }
        }

        // 2. Try match customer
        val autoEntryPassing = prefs.getBoolean("auto_entry_passing", true)
        val customers = repo.allCustomers.firstOrNull() ?: emptyList()
        val matchedCustomer = customers.find {
            it.upiNameAlias.trim().equals(senderName.trim(), ignoreCase = true) ||
            it.name.trim().equals(senderName.trim(), ignoreCase = true)
        }

        if (matchedCustomer != null && autoEntryPassing) {
            val activeLoan = repo.getActiveLoanCycleForCustomer(matchedCustomer.id)
            if (activeLoan != null) {
                // Auto-record payment!
                val payments = repo.getPaymentsForCycle(activeLoan.id).firstOrNull() ?: emptyList()
                val nextWeekNum = payments.size + 1

                val payment = WeeklyPayment(
                    loanCycleId = activeLoan.id,
                    amountPaid = amount,
                    paymentDate = System.currentTimeMillis(),
                    weekNumber = nextWeekNum,
                    notes = "Auto-recorded via notification (UTR: $txnId)",
                    upiTxnId = txnId
                )

                repo.addWeeklyPayment(payment)

                repo.addEditLog(
                    EditLog(
                        customerId = matchedCustomer.id,
                        customerName = matchedCustomer.name,
                        actionType = "RECORD_PAYMENT",
                        actionDescription = "Auto-recorded UPI payment of ₹$amount (UTR: $txnId) for ${matchedCustomer.name}"
                    )
                )

                val editPrefs = applicationContext.getSharedPreferences("weekly_finance_prefs", android.content.Context.MODE_PRIVATE)
                editPrefs.edit().putLong("last_local_modification_time", System.currentTimeMillis()).apply()


                // Trigger confirmation SMS if customer configuration has it enabled, and SMS are not globally paused
                if (matchedCustomer.smsConfirmationOfEntry) {
                    val smsPaused = prefs.getBoolean("sms_paused", false)
                    val simSelection = prefs.getString("sim_selection", "SIM 1") ?: "SIM 1"
                    val upiId = prefs.getString("upi_id", "9440736893@ptyes") ?: "9440736893@ptyes"
                    val customerLang = matchedCustomer.preferredLanguage
                    val userTemplate = prefs.getString("sms_payment_template_$customerLang", "")
                    val entryTemplate = if (!userTemplate.isNullOrBlank()) userTemplate else {
                        if (customerLang == "English") {
                            prefs.getString("sms_payment_template", "")?.takeIf { it.isNotBlank() } ?: "Hi {name},\nReceived ₹{p_amount} for Week {week},\nRemaining Balance: ₹{outstanding},\nThank you!"
                        } else {
                            when (customerLang) {
                                "Tamil" -> "அன்புள்ள {name},\nவாரம் {week}-க்கான தவணைத் தொகை ₹{p_amount} பெறப்பட்டது,\nமீதமுள்ள நிலுவை: ₹{outstanding},\nநன்றி!"
                                "Hindi" -> "प्रिय {name},\nसप्ताह {week} के लिए ₹{p_amount} की किस्त प्राप्त हुई,\nशेष बकाया: ₹{outstanding},\nधन्यवाद!"
                                "Telugu" -> "ప్రియమైన {name},\nవారం {week} కొరకు ₹{p_amount} వాయిదా స్వీకరించబడింది,\nమిగిలిన బకాయి: ₹{outstanding},\nధన్యవాదాలు!"
                                else -> "Hi {name},\nReceived ₹{p_amount} for Week {week},\nRemaining Balance: ₹{outstanding},\nThank you!"
                            }
                        }
                    }

                    // We need to re-fetch activeLoan as its paidAmount has been updated by addWeeklyPayment
                    val updatedLoan = repo.getActiveLoanCycleForCustomer(matchedCustomer.id) ?: activeLoan

                    SmsService.triggerPaymentEntrySms(
                        context = applicationContext,
                        customer = matchedCustomer,
                        loan = updatedLoan,
                        paymentAmount = amount,
                        weekNum = nextWeekNum,
                        template = entryTemplate,
                        upiId = upiId,
                        smsPaused = smsPaused,
                        simSelection = simSelection
                    )
                }
                return
            }
        }

        // 3. Fallback: Add to unmapped payments queue in SharedPreferences
        val queueStr = prefs.getString("unmapped_payments", "[]") ?: "[]"
        val queue = try { JSONArray(queueStr) } catch(e: Exception) { JSONArray() }

        // Prevent duplicates in queue
        var isAlreadyInQueue = false
        for (i in 0 until queue.length()) {
            val obj = queue.optJSONObject(i)
            if (obj != null && obj.optString("txnId") == txnId) {
                isAlreadyInQueue = true
                break
            }
        }

        if (!isAlreadyInQueue) {
            val newItem = JSONObject().apply {
                put("amount", amount)
                put("sender", senderName)
                put("txnId", txnId)
                put("timestamp", System.currentTimeMillis())
            }
            queue.put(newItem)
            prefs.edit().putString("unmapped_payments", queue.toString()).apply()
            Log.d("NotificationListener", "Added unmapped payment of ₹$amount from $senderName to queue")
        }
    }
}
