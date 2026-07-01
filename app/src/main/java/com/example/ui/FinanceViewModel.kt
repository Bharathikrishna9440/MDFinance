package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.service.SmsService
import androidx.room.withTransaction
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

sealed class Screen {
    object Dashboard : Screen()
    data class CustomerDetail(val customerId: Int) : Screen()
    object AddCustomer : Screen()
    data class EditCustomer(val customerId: Int) : Screen()
    data class AddLoan(val customerId: Int) : Screen()
    data class EditLoan(val loanCycleId: Int) : Screen()
    data class RecordPayment(val loanCycleId: Int) : Screen()
    object Settings : Screen() // Settings screen with language and custom options
    object History : Screen() // Dedicated history / change logs screen
    object FullLedgerHistory : Screen()
    data class BulkEntry(val day: String) : Screen()
    data class Search(val day: String) : Screen()
    object AiChat : Screen()
    data class CalculationDetail(val type: String, val day: String) : Screen()
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val base64Image: String? = null
)

data class DeviceSyncStatus(
    val deviceId: String,
    val totalOutstanding: Double,
    val activeLoans: Int,
    val collectionRate: Double,
    val timestamp: Long,
    val hashCheck: String
)

data class ParsedImportItem(
    val name: String,
    val collectionDay: String,
    val loanAmt: Double,
    val amtReceived: Double,
    val phone: String,
    val city: String
)

data class ParseResult(
    val items: List<ParsedImportItem>,
    val errors: Int
)

data class ParsedUploadItem(
    val name: String,
    val phone: String,
    val city: String,
    val principal: Double,
    val interest: Double,
    val weekly: Double,
    val tenure: Int,
    val paymentReceived: Double,
    val notesStr: String
)

data class UnmappedPayment(
    val amount: Double,
    val sender: String,
    val txnId: String,
    val timestamp: Long
)

data class FirebaseEditLog(
    val uuid: String,
    val timestamp: Long,
    val actionType: String,
    val payload: String,
    val previousPayload: String = ""
)

data class FirebaseUser(
    val key: String,
    val username: String,
    val password: String,
    val role: String
)

data class MasterComparisonReport(
    val lastCheckedTimestamp: Long,
    val deviceId: String,
    val masterOutstanding: Double,
    val localOutstanding: Double,
    val outstandingMatches: Boolean,
    val masterCustomerCount: Int,
    val localCustomerCount: Int,
    val customerCountMatches: Boolean,
    val mismatchingCustomers: List<String>,
    val comparisonTriggerDetail: String
)

@OptIn(kotlinx.coroutines.FlowPreview::class)
class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    val bypassLogin = false // Set to false to re-enable login page

    private val db = AppDatabase.getDatabase(application)
    private val repository = FinanceRepository(db.collectionDao())
    private val prefs = application.getSharedPreferences("weekly_finance_prefs", Context.MODE_PRIVATE)

    val firebaseDbUrl = MutableStateFlow(prefs.getString("firebase_db_url", "") ?: "")

    fun updateFirebaseDbUrl(url: String) {
        prefs.edit().putString("firebase_db_url", url.trim()).apply()
        firebaseDbUrl.value = url.trim()
        startFirebaseSyncListening()
    }

    private val _isLoggedIn = MutableStateFlow(if (bypassLogin) true else prefs.getBoolean("is_logged_in", false))
    val isLoggedIn = _isLoggedIn.asStateFlow()

    private val _isDemoMode = MutableStateFlow(prefs.getBoolean("is_demo_mode", false))
    val isDemoMode = _isDemoMode.asStateFlow()

    private val _currentUser = MutableStateFlow(if (bypassLogin) com.example.util.SecureConfig.adminUsername.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() } else (prefs.getString("current_user", "") ?: ""))
    val currentUser = _currentUser.asStateFlow()

    private val _currentUserRole = MutableStateFlow(if (bypassLogin) "USER" else (prefs.getString("current_role", "USER") ?: "USER"))
    val currentUserRole = _currentUserRole.asStateFlow()

    private val _hasCompletedDeviceSetup = MutableStateFlow(if (bypassLogin) true else prefs.getBoolean("has_completed_device_setup", false))
    val hasCompletedDeviceSetup = _hasCompletedDeviceSetup.asStateFlow()

    private val _firebaseUsers = MutableStateFlow<List<FirebaseUser>>(emptyList())
    val firebaseUsers = _firebaseUsers.asStateFlow()

    private val _isLoadingUsers = MutableStateFlow(false)
    val isLoadingUsers = _isLoadingUsers.asStateFlow()

    private val _username = MutableStateFlow(if (bypassLogin) com.example.util.SecureConfig.adminUsername.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() } else (prefs.getString("current_user", "Device User") ?: "Device User"))
    val username = _username.asStateFlow()

    private val _deviceId = MutableStateFlow(
        prefs.getString("device_id", null) ?: run {
            val newId = "Device_" + java.util.UUID.randomUUID().toString().take(6).uppercase(Locale.US)
            prefs.edit().putString("device_id", newId).apply()
            newId
        }
    )
    val deviceId = _deviceId.asStateFlow()

    private val _syncFilesCount = MutableStateFlow(0)
    val syncFilesCount = _syncFilesCount.asStateFlow()

    private val _syncAddedCount = MutableStateFlow(0)
    val syncAddedCount = _syncAddedCount.asStateFlow()

    private val _isOffline = MutableStateFlow(true)
    val isOffline = _isOffline.asStateFlow()

    val isFirstLaunchOfflineBlocked = MutableStateFlow(false)

    val isReconcilingMaster = MutableStateFlow(true)
    val reconciliationProgress = MutableStateFlow("Starting Ledger Reconciliation...")

    private var connectivityCallback: android.net.ConnectivityManager.NetworkCallback? = null

    private var isFirebaseConnectedSnapshot = false

    private fun updateOfflineStatus() {
        val sysConnected = isNetworkConnected()
        
        // We are offline if system internet is not active
        val offline = !sysConnected
        _isOffline.value = offline

        // Block on first launch if system internet is not connected
        isFirstLaunchOfflineBlocked.value = false
    }

    private val outboxFile = java.io.File(application.filesDir, "offline_queue.json")

    private val _unsyncedEditUuids = MutableStateFlow<Set<String>>(emptySet())
    val unsyncedEditUuids = _unsyncedEditUuids.asStateFlow()

    private val _firebaseSyncStatus = MutableStateFlow("Disconnected")
    val firebaseSyncStatus = _firebaseSyncStatus.asStateFlow()

    private val _firebaseSyncError = MutableStateFlow<String?>(null)
    val firebaseSyncError = _firebaseSyncError.asStateFlow()

    private val _unsyncedLogCount = MutableStateFlow(0)
    val unsyncedLogCount = _unsyncedLogCount.asStateFlow()

    private val _syncOutstandingVerified = MutableStateFlow(true)
    val syncOutstandingVerified = _syncOutstandingVerified.asStateFlow()

    private val _syncStatsText = MutableStateFlow("No verification performed yet.")
    val syncStatsText = _syncStatsText.asStateFlow()

    private val _smsReaderPaused = MutableStateFlow(prefs.getBoolean("sms_reader_paused", false))
    val smsReaderPaused = _smsReaderPaused.asStateFlow()

    private val _autoEntryPassing = MutableStateFlow(prefs.getBoolean("auto_entry_passing", true))
    val autoEntryPassing = _autoEntryPassing.asStateFlow()

    private val _upiLinkSharing = MutableStateFlow(prefs.getBoolean("upi_link_sharing", true))
    val upiLinkSharing = _upiLinkSharing.asStateFlow()

    private val _unmappedPayments = MutableStateFlow<List<UnmappedPayment>>(emptyList())
    val unmappedPayments = _unmappedPayments.asStateFlow()

    private val _connectedDevices = MutableStateFlow<List<DeviceSyncStatus>>(emptyList())
    val connectedDevices = _connectedDevices.asStateFlow()

    private val _masterComparison = MutableStateFlow<MasterComparisonReport?>(null)
    val masterComparison = _masterComparison.asStateFlow()

    private val _liveCloudJson = MutableStateFlow<String?>(null)
    val liveCloudJson = _liveCloudJson.asStateFlow()

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "unmapped_payments") {
            loadUnmappedPayments()
        } else if (key == "cached_devices_metrics") {
            loadConnectedDevices()

        } else if (key == "sms_reader_paused") {
            _smsReaderPaused.value = prefs.getBoolean("sms_reader_paused", false)
        } else if (key == "auto_entry_passing") {
            _autoEntryPassing.value = prefs.getBoolean("auto_entry_passing", true)
        } else if (key == "upi_link_sharing") {
            _upiLinkSharing.value = prefs.getBoolean("upi_link_sharing", true)
        } else if (key == "upi_link") {
            _upiLink.value = prefs.getString("upi_link", "upi://pay?pa=9440736893@ptyes&pn=Muneeswaran%20P") ?: "upi://pay?pa=9440736893@ptyes&pn=Muneeswaran%20P"

        } else if (key == "username") {
            val name = prefs.getString("username", "Device User") ?: "Device User"
            _username.value = name
            repository.deviceUsername = name
        }
    }

    fun loadUnmappedPayments() {
        val queueStr = prefs.getString("unmapped_payments", "[]") ?: "[]"
        try {
            val arr = org.json.JSONArray(queueStr)
            val list = mutableListOf<UnmappedPayment>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                if (obj != null) {
                    list.add(
                        UnmappedPayment(
                            amount = obj.optDouble("amount", 0.0),
                            sender = obj.optString("sender", "UPI Payer"),
                            txnId = obj.optString("txnId", ""),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }
            _unmappedPayments.value = list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadConnectedDevices() {
        val jsonStr = prefs.getString("cached_devices_metrics", "") ?: ""
        val list = mutableListOf<DeviceSyncStatus>()
        if (jsonStr.isNotEmpty()) {
            try {
                val arr = org.json.JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        DeviceSyncStatus(
                            deviceId = obj.optString("deviceId", "Unknown"),
                            totalOutstanding = obj.optDouble("totalOutstanding", 0.0),
                            activeLoans = obj.optInt("activeLoans", 0),
                            collectionRate = obj.optDouble("collectionRate", 0.0),
                            timestamp = obj.optLong("timestamp", 0L),
                            hashCheck = obj.optString("hashCheck", "")
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _connectedDevices.value = list
    }

    fun ignoreUnmappedPayment(txnId: String) {
        val ignoredStr = prefs.getString("ignored_upi_txns", "[]") ?: "[]"
        try {
            val ignoredArray = org.json.JSONArray(ignoredStr)
            var exists = false
            for (i in 0 until ignoredArray.length()) {
                if (ignoredArray.optString(i) == txnId) {
                    exists = true
                    break
                }
            }
            if (!exists) {
                ignoredArray.put(txnId)
                prefs.edit().putString("ignored_upi_txns", ignoredArray.toString()).apply()
            }

            val queueStr = prefs.getString("unmapped_payments", "[]") ?: "[]"
            val queue = org.json.JSONArray(queueStr)
            val newQueue = org.json.JSONArray()
            for (i in 0 until queue.length()) {
                val obj = queue.optJSONObject(i)
                if (obj != null && obj.optString("txnId") != txnId) {
                    newQueue.put(obj)
                }
            }
            prefs.edit().putString("unmapped_payments", newQueue.toString()).apply()
            loadUnmappedPayments()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun mapUnmappedPaymentToCustomer(customerId: Int, txnId: String, amount: Double, linkAlias: Boolean) {
        viewModelScope.launch {
            try {
                val customer = repository.getCustomerById(customerId) ?: return@launch
                val activeLoan = repository.getActiveLoanCycleForCustomer(customerId) ?: return@launch

                val payments = repository.getPaymentsForCycle(activeLoan.id).firstOrNull() ?: emptyList()
                val nextWeekNum = payments.size + 1

                val payment = WeeklyPayment(
                    loanCycleId = activeLoan.id,
                    amountPaid = amount,
                    paymentDate = System.currentTimeMillis(),
                    weekNumber = nextWeekNum,
                    notes = "Recorded manually from unmapped queue (UTR: $txnId)",
                    upiTxnId = txnId
                )

                repository.addWeeklyPayment(payment)

                if (linkAlias) {
                    val queueStr = prefs.getString("unmapped_payments", "[]") ?: "[]"
                    val queue = org.json.JSONArray(queueStr)
                    var parsedSender = ""
                    for (i in 0 until queue.length()) {
                        val obj = queue.optJSONObject(i)
                        if (obj != null && obj.optString("txnId") == txnId) {
                            parsedSender = obj.optString("sender", "")
                            break
                        }
                    }
                    if (parsedSender.isNotBlank()) {
                        repository.updateCustomer(customer.copy(upiNameAlias = parsedSender))
                    }
                }

                repository.addEditLog(
                    EditLog(
                        customerId = customerId,
                        customerName = customer.name,
                        actionType = "RECORD_PAYMENT",
                        actionDescription = "Manually linked UPI payment of ₹$amount (UTR: $txnId) to ${customer.name}"
                    )
                )

                if (customer.smsConfirmationOfEntry) {
                    val smsPaused = prefs.getBoolean("sms_paused", false)
                    val simSelection = prefs.getString("sim_selection", "SIM 1") ?: "SIM 1"
                    val upiIdValue = prefs.getString("upi_id", "9440736893@ptyes") ?: "9440736893@ptyes"
                    val entryTemplate = getSmsPaymentTemplate(customer.preferredLanguage)

                    val updatedLoan = repository.getActiveLoanCycleForCustomer(customerId) ?: activeLoan

                    SmsService.triggerPaymentEntrySms(
                        context = getApplication(),
                        customer = customer,
                        loan = updatedLoan,
                        paymentAmount = amount,
                        weekNum = nextWeekNum,
                        template = entryTemplate,
                        upiId = upiIdValue,
                        smsPaused = smsPaused,
                        simSelection = simSelection
                    )
                }

                val queueStr = prefs.getString("unmapped_payments", "[]") ?: "[]"
                val queue = org.json.JSONArray(queueStr)
                val newQueue = org.json.JSONArray()
                for (i in 0 until queue.length()) {
                    val obj = queue.optJSONObject(i)
                    if (obj != null && obj.optString("txnId") != txnId) {
                        newQueue.put(obj)
                    }
                }
                prefs.edit().putString("unmapped_payments", newQueue.toString()).apply()
                loadUnmappedPayments()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Navigation Stack
    private val _screenBackstack = MutableStateFlow<List<Screen>>(
        listOf(Screen.Dashboard)
    )
    val screenBackstack: StateFlow<List<Screen>> = _screenBackstack.asStateFlow()
    val currentScreen: StateFlow<Screen> = _screenBackstack
        .map { it.lastOrNull() ?: Screen.Dashboard }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Screen.Dashboard)

    // Scroll state preservation for the Dashboard list
    var dashboardScrollIndex = 0
    var dashboardScrollOffset = 0

    // Map to preserve scroll position state per day group
    private val scrollIndexes = mutableMapOf<String, Int>()
    private val scrollOffsets = mutableMapOf<String, Int>()

    fun getScrollIndexForDay(day: String): Int {
        if (day == "Home") return dashboardScrollIndex
        return scrollIndexes[day] ?: 0
    }

    fun getScrollOffsetForDay(day: String): Int {
        if (day == "Home") return dashboardScrollOffset
        return scrollOffsets[day] ?: 0
    }

    fun saveScrollStateForDay(day: String, index: Int, offset: Int) {
        if (day == "Home") {
            dashboardScrollIndex = index
            dashboardScrollOffset = offset
        } else {
            scrollIndexes[day] = index
            scrollOffsets[day] = offset
        }
    }

    fun clearScrollStates() {
        dashboardScrollIndex = 0
        dashboardScrollOffset = 0
        scrollIndexes.clear()
        scrollOffsets.clear()
    }

    fun navigateTo(screen: Screen) {
        val current = _screenBackstack.value.toMutableList()
        current.add(screen)
        _screenBackstack.value = current
    }

    fun replaceTopScreen(screen: Screen) {
        val current = _screenBackstack.value.toMutableList()
        if (current.isNotEmpty()) {
            current[current.size - 1] = screen
            _screenBackstack.value = current
        } else {
            current.add(screen)
            _screenBackstack.value = current
        }
    }

    fun navigateBack() {
        val current = _screenBackstack.value.toMutableList()
        if (current.size > 1) {
            current.removeAt(current.size - 1)
            _screenBackstack.value = current
        }
    }

    // Direct navigation back to Home (Dashboard)
    fun navigateToHome() {
        _screenBackstack.value = listOf(Screen.Dashboard)
        _selectedDay.value = "Home"
    }

    fun canGoBack(): Boolean = _screenBackstack.value.size > 1

    // State flows
    val allCustomers: StateFlow<List<Customer>> = repository.allCustomers
        .map { list ->
            list.filter { it.status.uppercase() != "DELETED" }.map { customer ->
                if (customer.collectionDay.trim().equals("Sunday Morning", ignoreCase = true)) {
                    customer.copy(collectionDay = "Sunday mrg")
                } else if (customer.collectionDay.trim().equals("Sunday Evening", ignoreCase = true)) {
                    customer.copy(collectionDay = "Sunday eve")
                } else {
                    customer
                }
            }
        }
        .flowOn(kotlinx.coroutines.Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allLoanCycles: StateFlow<List<LoanCycle>> = combine(
        repository.allLoanCycles,
        repository.allPayments
    ) { loans, payments ->
        val activePaymentsByLoan = payments.filter { it.status.uppercase() != "DELETED" }.groupBy { it.loanCycleId }
        loans.filter { it.status.uppercase() != "DELETED" }.map { loan ->
            val actualPaid = activePaymentsByLoan[loan.id]?.sumOf { it.amountPaid } ?: 0.0
            val totalToBePaid = loan.loanAmount + loan.interestAmount
            val liveStatus = if (actualPaid >= totalToBePaid) "PAID" else "ACTIVE"
            loan.copy(paidAmount = actualPaid, status = liveStatus)
        }
    }
    .flowOn(kotlinx.coroutines.Dispatchers.IO)
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeLoanCycles: StateFlow<List<LoanCycle>> = allLoanCycles
        .map { list -> list.filter { it.status == "ACTIVE" } }
        .flowOn(kotlinx.coroutines.Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allPayments: StateFlow<List<WeeklyPayment>> = repository.allPayments
        .map { list -> list.filter { it.status.uppercase() != "DELETED" } }
        .flowOn(kotlinx.coroutines.Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Raw records for backup / Master Database
    val rawCustomersFromDb: StateFlow<List<Customer>> = repository.allCustomers
        .map { list ->
            list.map { customer ->
                if (customer.collectionDay.trim().equals("Sunday Morning", ignoreCase = true)) {
                    customer.copy(collectionDay = "Sunday mrg")
                } else if (customer.collectionDay.trim().equals("Sunday Evening", ignoreCase = true)) {
                    customer.copy(collectionDay = "Sunday eve")
                } else {
                    customer
                }
            }
        }
        .flowOn(kotlinx.coroutines.Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val rawLoanCyclesFromDb: StateFlow<List<LoanCycle>> = repository.allLoanCycles
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val rawPaymentsFromDb: StateFlow<List<WeeklyPayment>> = repository.allPayments
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allCashBalanceLogs: StateFlow<List<CashBalanceLog>> = repository.allCashBalanceLogs
        .flowOn(kotlinx.coroutines.Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun logCashBalance(actualCash: Double, systemCash: Double, collectionAmount: Double, disbursalAmount: Double, expenses: Double, date: Long? = null, logId: Int = 0) {
        viewModelScope.launch {
            repository.addCashBalanceLog(
                CashBalanceLog(
                    id = logId,
                    date = date ?: System.currentTimeMillis(),
                    actualCash = actualCash,
                    systemCash = systemCash,
                    collectionAmount = collectionAmount,
                    disbursalAmount = disbursalAmount,
                    expenses = expenses,
                    notes = ""
                )
            )
        }
    }

    fun deleteCashBalanceLog(log: CashBalanceLog) {
        viewModelScope.launch {
            repository.deleteCashBalanceLog(log)
        }
    }

    val allEditLogs: StateFlow<List<EditLog>> = repository.allEditLogs
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun getLoanCyclesForCustomer(customerId: Int): Flow<List<LoanCycle>> {
        return repository.getLoanCyclesForCustomer(customerId).map { list -> list.filter { it.status.uppercase() != "DELETED" } }
    }

    fun getPaymentsForCycle(loanCycleId: Int): Flow<List<WeeklyPayment>> {
        return repository.getPaymentsForCycle(loanCycleId).map { list -> list.filter { it.status.uppercase() != "DELETED" } }
    }

    // Settings States
    private val _simSelection = MutableStateFlow(prefs.getString("sim_selection", "SIM 1") ?: "SIM 1")
    val simSelection = _simSelection.asStateFlow()

    private val _language = MutableStateFlow(prefs.getString("language", "English") ?: "English")
    val language = _language.asStateFlow()

    private val _upiId = MutableStateFlow(prefs.getString("upi_id", "9440736893@ptyes") ?: "9440736893@ptyes")
    val upiId = _upiId.asStateFlow()

    private val _upiLink = MutableStateFlow(prefs.getString("upi_link", "upi://pay?pa=9440736893@ptyes&pn=Muneeswaran%20P") ?: "upi://pay?pa=9440736893@ptyes&pn=Muneeswaran%20P")
    val upiLink = _upiLink.asStateFlow()

    private val _qrImageUri = MutableStateFlow(prefs.getString("qr_image_uri", "") ?: "")
    val qrImageUri = _qrImageUri.asStateFlow()

    private val _businessName = MutableStateFlow(prefs.getString("business_name", "Muneeswaran P") ?: "Muneeswaran P")
    val businessName = _businessName.asStateFlow()

    private val _statementCustomizationCode = MutableStateFlow(
        prefs.getString(
            "statement_customization_code",
            "TITLE=COLLECTION STATEMENT REPORT\nFOOTER=Please retain this statement for your verification.\nCOLOR_START=#0F172A\nCOLOR_END=#1E1B4B\nTHEME_BORDER_COLOR=#4F46E5"
        ) ?: "TITLE=COLLECTION STATEMENT REPORT\nFOOTER=Please retain this statement for your verification.\nCOLOR_START=#0F172A\nCOLOR_END=#1E1B4B\nTHEME_BORDER_COLOR=#4F46E5"
    )
    val statementCustomizationCode = _statementCustomizationCode.asStateFlow()

    private val _smsPaused = MutableStateFlow(prefs.getBoolean("sms_paused", false))
    val smsPaused = _smsPaused.asStateFlow()

    private val _fontSizeScale = MutableStateFlow(prefs.getFloat("font_size_scale", 1.15f))
    val fontSizeScale = _fontSizeScale.asStateFlow()

    private val _selectedTheme = MutableStateFlow("Sleek Slate")
    val selectedTheme = _selectedTheme.asStateFlow()

    private val _customAppName = MutableStateFlow(prefs.getString("custom_app_name", "MD Finance") ?: "MD Finance")
    val customAppName = _customAppName.asStateFlow()

    private val _customAppLogo = MutableStateFlow(prefs.getString("custom_app_logo", "CurrencyRupee") ?: "CurrencyRupee")
    val customAppLogo = _customAppLogo.asStateFlow()

    private val _customerSortMode = MutableStateFlow(prefs.getString("customer_sort_mode", "ROUTE") ?: "ROUTE")
    val customerSortMode = _customerSortMode.asStateFlow()

    private val _autoBackupEnabled = MutableStateFlow(prefs.getBoolean("auto_backup_enabled", true))
    val autoBackupEnabled = _autoBackupEnabled.asStateFlow()

    private val _autoUpdateEnabled = MutableStateFlow(prefs.getBoolean("auto_update_enabled", true))
    val autoUpdateEnabled = _autoUpdateEnabled.asStateFlow()

    private val _forceUpdateEnabled = MutableStateFlow(prefs.getBoolean("force_update_enabled", false))
    val forceUpdateEnabled = _forceUpdateEnabled.asStateFlow()

    private val _pauseUpdatesEnabled = MutableStateFlow(prefs.getBoolean("pause_updates_enabled", false))
    val pauseUpdatesEnabled = _pauseUpdatesEnabled.asStateFlow()

    private val _googleContactsSyncEnabled = MutableStateFlow(prefs.getBoolean("google_contacts_sync_enabled", false))
    val googleContactsSyncEnabled = _googleContactsSyncEnabled.asStateFlow()

    private val _googleContactsSelectedAccount = MutableStateFlow(prefs.getString("google_contacts_selected_account", "") ?: "")
    val googleContactsSelectedAccount = _googleContactsSelectedAccount.asStateFlow()

    private val _googleContactsAccountsList = MutableStateFlow<List<String>>(emptyList())
    val googleContactsAccountsList = _googleContactsAccountsList.asStateFlow()

    private val _isGoogleDriveBackupLoading = MutableStateFlow(false)
    val isGoogleDriveBackupLoading = _isGoogleDriveBackupLoading.asStateFlow()

    private val _googleDriveBackupStatusMessage = MutableStateFlow<String?>(null)
    val googleDriveBackupStatusMessage = _googleDriveBackupStatusMessage.asStateFlow()

    // Offline Mode & AI States
    private val _offlineModeEnabled = MutableStateFlow(prefs.getBoolean("offline_mode_enabled", false))
    val offlineModeEnabled = _offlineModeEnabled.asStateFlow()

    private val _aiEnabled = MutableStateFlow(prefs.getBoolean("ai_enabled", true))
    val aiEnabled = _aiEnabled.asStateFlow()

    private val _aiChatMessages = MutableStateFlow<List<ChatMessage>>(listOf(
        ChatMessage(
            text = "Hello! I am Easwar, your MD Finance AI Assistant. I have real-time access to all local borrower logs, active loans, and collections. How can I help you in Tamil or English today?\n\nவணக்கம்! நான் ஈஸ்வர், உங்களின் எம்.டி பைனான்ஸ் ஏஐ உதவியாளர். கடன் தகவல்கள், வசூல் விவரங்கள் குறித்து எதை வேண்டுமானாலும் என்னிடம் கேளுங்கள்!",
            isUser = false
        )
    ))
    val aiChatMessages = _aiChatMessages.asStateFlow()

    private val _aiChatLoading = MutableStateFlow(false)
    val aiChatLoading = _aiChatLoading.asStateFlow()

    private val _aiSpeechLanguage = MutableStateFlow(prefs.getString("ai_speech_language", "") ?: "")
    val aiSpeechLanguage = _aiSpeechLanguage.asStateFlow()

    private val _aiThinkingMode = MutableStateFlow(prefs.getString("ai_thinking_mode", "Low Latency") ?: "Low Latency")
    val aiThinkingMode = _aiThinkingMode.asStateFlow()

    private val _aiSearchGrounding = MutableStateFlow(prefs.getBoolean("ai_search_grounding", false))
    val aiSearchGrounding = _aiSearchGrounding.asStateFlow()

    private val _aiImageGenMode = MutableStateFlow(prefs.getBoolean("ai_image_gen_mode", false))
    val aiImageGenMode = _aiImageGenMode.asStateFlow()

    fun setAiThinkingMode(mode: String) {
        _aiThinkingMode.value = mode
        prefs.edit().putString("ai_thinking_mode", mode).apply()
    }

    fun setAiSearchGrounding(enabled: Boolean) {
        _aiSearchGrounding.value = enabled
        prefs.edit().putBoolean("ai_search_grounding", enabled).apply()
    }

    fun setAiImageGenMode(enabled: Boolean) {
        _aiImageGenMode.value = enabled
        prefs.edit().putBoolean("ai_image_gen_mode", enabled).apply()
    }

    fun setOfflineModeEnabled(enabled: Boolean) {
        _offlineModeEnabled.value = enabled
        prefs.edit().putBoolean("offline_mode_enabled", enabled).apply()
        if (enabled) {
            _firebaseSyncStatus.value = "Offline Mode Active"
        } else {
            startFirebaseSyncListening()
        }
    }

    fun setAiEnabled(enabled: Boolean) {
        _aiEnabled.value = enabled
        prefs.edit().putBoolean("ai_enabled", enabled).apply()
    }

    fun setAiSpeechLanguage(lang: String) {
        _aiSpeechLanguage.value = lang
        prefs.edit().putString("ai_speech_language", lang).apply()
    }

    fun clearAiChat() {
        _aiChatMessages.value = listOf(
            ChatMessage(
                text = "Chat history cleared. I am Easwar, how can I help you today?\n\nஉரையாடல் வரலாறு அழிக்கப்பட்டது. நான் ஈஸ்வர், இன்று உங்களுக்கு எவ்வாறு உதவ முடியும்?",
                isUser = false
            )
        )
    }

    fun getLocalDataContextForAi(): String {
        val customersList = allCustomers.value.filter { it.status.uppercase() != "DELETED" }
        val loansList = allLoanCycles.value.filter { it.status.uppercase() != "DELETED" }
        val paymentsList = allPayments.value.filter { it.status.uppercase() != "DELETED" }

        val sb = java.lang.StringBuilder()
        sb.append("System Role: You are Easwar, a highly specialized Android finance AI assistant for MD Finance (owned by Muneeswaran P). ")
        sb.append("You have 100% full real-time visibility of local borrower data, loans, and receipt entries in the local offline database.\n")
        sb.append("Current local system context for querying live records:\n")
        sb.append("- Total Borrowers: ${customersList.size}\n")
        val activeLoans = loansList.filter { it.status == "ACTIVE" }
        val settledLoans = loansList.filter { it.status == "PAID" }
        sb.append("- Active Loans Count: ${activeLoans.size}\n")
        sb.append("- Settled/Paid Loans Count: ${settledLoans.size}\n")
        val totalDisbursed = loansList.sumOf { it.loanAmount - it.deduction }
        val totalInterest = loansList.sumOf { it.interestAmount }
        val totalPaid = paymentsList.sumOf { it.amountPaid }
        val totalOutstanding = activeLoans.sumOf { (it.loanAmount + it.interestAmount) - it.paidAmount }
        sb.append("- Total Principal Disbursed: ₹${totalDisbursed}\n")
        sb.append("- Total Interest Generated: ₹${totalInterest}\n")
        sb.append("- Total Instalments Collected: ₹${totalPaid}\n")
        sb.append("- Total Active Outstanding Balance (Loans outstanding): ₹${totalOutstanding}\n\n")

        sb.append("Borrowers List, Active Loans Outstanding, and Route Schedules:\n")
        customersList.forEachIndexed { index, cust ->
            val custLoans = loansList.filter { it.customerId == cust.id }
            val activeCustLoans = custLoans.filter { it.status == "ACTIVE" }
            sb.append("${index + 1}. Borrower: ${cust.name} (Phone: ${cust.phone}, City: ${cust.city}, Route Day: ${cust.collectionDay})\n")
            if (activeCustLoans.isNotEmpty()) {
                activeCustLoans.forEach { loan ->
                    val loanTotal = loan.loanAmount + loan.interestAmount
                    val remaining = loanTotal - loan.paidAmount
                    sb.append("   - Active Loan [Cycle ID ${loan.id}]: Principal ₹${loan.loanAmount}, Interest ₹${loan.interestAmount}, Disbursed on ${loan.startDate}, Remaining Outstanding ₹${remaining}\n")
                    val loanPayments = paymentsList.filter { it.loanCycleId == loan.id }
                    if (loanPayments.isNotEmpty()) {
                        val paymentsSummary = loanPayments.joinToString { "Week ${it.weekNumber}: ₹${it.amountPaid}" }
                        sb.append("     Receipts logs: $paymentsSummary\n")
                    } else {
                        sb.append("     No weekly receipt logs yet.\n")
                    }
                }
            } else {
                sb.append("   - No active loans outstanding.\n")
            }
        }
        return sb.toString()
    }

    fun sendChatMessageToEaswar(messageText: String) {
        if (messageText.isBlank()) return

        // Append user chat message
        val userMsg = ChatMessage(text = messageText, isUser = true)
        _aiChatMessages.value = _aiChatMessages.value + userMsg

        if (offlineModeEnabled.value) {
            _aiChatMessages.value = _aiChatMessages.value + ChatMessage(
                text = "Easwar is offline. Realtime AI network requests are deactivated in Offline Mode. Please disable Offline Mode in settings to resume.\n\nஈஸ்வர் ஆஃப்லைனில் உள்ளார். ஆஃப்லைன் பயன்முறையில் ஏஐ இயங்காது. உரையாடலைத் தொடர, தயவுசெய்து அமைப்புகளில் ஆஃப்லைன் பயன்முறையை முடக்கவும்.",
                isUser = false
            )
            return
        }

        _aiChatLoading.value = true

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val apiKey = com.example.BuildConfig.GEMINI_API_KEY
                if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                    _aiChatMessages.value = _aiChatMessages.value + ChatMessage(
                        text = "Gemini API key is not configured in Secrets. Please configure GEMINI_API_KEY to start conversing with Easwar.",
                        isUser = false
                    )
                    _aiChatLoading.value = false
                    return@launch
                }

                val isImgMode = _aiImageGenMode.value ||
                                messageText.contains("generate image", ignoreCase = true) ||
                                messageText.contains("draw", ignoreCase = true) ||
                                messageText.contains("create image", ignoreCase = true) ||
                                messageText.contains("photo of", ignoreCase = true) ||
                                messageText.contains("image of", ignoreCase = true)

                val modelName = when {
                    isImgMode -> "gemini-2.5-flash-image"
                    _aiThinkingMode.value == "High Thinking" -> "gemini-3.1-pro-preview"
                    else -> "gemini-3.5-flash"
                }

                val systemContext = getLocalDataContextForAi()
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

                val requestObj = org.json.JSONObject()

                // System Instruction block configures Gemini's personality and local database context
                if (!isImgMode) {
                    val systemInstructionObj = org.json.JSONObject()
                    val savedInstruction = prefs.getString("ai_system_instruction", "Your name is Easwar. You are Muneeswaran P's finance assistant. Always answer the user accurately using Tamil or English based on preference. Keep responses clear and compact. Refer to borrower names and outstanding numbers directly from the context.")
                    val systemPart = org.json.JSONObject().put("text", systemContext + "\nUser Instructions:\n" + savedInstruction)
                    systemInstructionObj.put("parts", org.json.JSONArray().put(systemPart))
                    requestObj.put("systemInstruction", systemInstructionObj)
                }

                // Populate past conversation history
                val contentsArray = org.json.JSONArray()
                _aiChatMessages.value.forEach { msg ->
                    val contentObj = org.json.JSONObject()
                    contentObj.put("role", if (msg.isUser) "user" else "model")
                    val partObj = org.json.JSONObject()
                    if (msg.base64Image != null && !msg.isUser) {
                        // Image part structure
                        val inlineDataObj = org.json.JSONObject().apply {
                            put("mimeType", "image/png")
                            put("data", msg.base64Image)
                        }
                        partObj.put("inlineData", inlineDataObj)
                    } else {
                        partObj.put("text", msg.text)
                    }
                    contentObj.put("parts", org.json.JSONArray().put(partObj))
                    contentsArray.put(contentObj)
                }
                requestObj.put("contents", contentsArray)

                // Configure generation properties
                val configObj = org.json.JSONObject()
                if (isImgMode) {
                    val responseModalitiesArr = org.json.JSONArray().put("TEXT").put("IMAGE")
                    configObj.put("responseModalities", responseModalitiesArr)

                    val imageConfigObj = org.json.JSONObject()
                    imageConfigObj.put("aspectRatio", "1:1")
                    imageConfigObj.put("imageSize", "1K")
                    configObj.put("imageConfig", imageConfigObj)
                } else {
                    configObj.put("temperature", if (_aiThinkingMode.value == "High Thinking") 0.5f else 0.15f)
                    if (_aiThinkingMode.value == "High Thinking") {
                        val thinkingConfigObj = org.json.JSONObject()
                        thinkingConfigObj.put("thinkingLevel", "high")
                        configObj.put("thinkingConfig", thinkingConfigObj)
                    }
                }
                requestObj.put("generationConfig", configObj)

                // Inject Google Search Grounding Tool
                if (_aiSearchGrounding.value && !isImgMode) {
                    val toolsArray = org.json.JSONArray()
                    val toolOption = org.json.JSONObject()
                    toolOption.put("googleSearch", org.json.JSONObject())
                    toolsArray.put(toolOption)
                    requestObj.put("tools", toolsArray)
                }

                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val requestBody = requestObj.toString().toRequestBody(mediaType)
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorMsg = response.body?.string() ?: "Unknown error"
                        _aiChatMessages.value = _aiChatMessages.value + ChatMessage(
                            text = "Easwar could not respond (Server Code ${response.code}). Details: $errorMsg",
                            isUser = false
                        )
                    } else {
                        val responseBodyStr = response.body?.string() ?: ""
                        val responseJson = org.json.JSONObject(responseBodyStr)
                        val candidates = responseJson.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val candidate = candidates.getJSONObject(0)
                            val content = candidate.optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                var replyText = ""
                                var base64Img: String? = null

                                for (i in 0 until parts.length()) {
                                    val part = parts.getJSONObject(i)
                                    if (part.has("text")) {
                                        replyText += part.optString("text", "")
                                    }
                                    val inlineData = part.optJSONObject("inlineData")
                                    if (inlineData != null) {
                                        val mimeType = inlineData.optString("mimeType", "")
                                        if (mimeType.startsWith("image/")) {
                                            base64Img = inlineData.optString("data", "")
                                        }
                                    }
                                }

                                // Handle Google Search Grounding Metadata citation linking
                                val groundingMetadata = candidate.optJSONObject("groundingMetadata")
                                if (groundingMetadata != null) {
                                    val groundingChunks = groundingMetadata.optJSONArray("groundingChunks")
                                    if (groundingChunks != null && groundingChunks.length() > 0) {
                                        val sourcesSb = java.lang.StringBuilder("\n\n🌐 **Google Search Grounded Reference Sources:**")
                                        for (idx in 0 until groundingChunks.length()) {
                                            val chunk = groundingChunks.getJSONObject(idx)
                                            val web = chunk.optJSONObject("web")
                                            if (web != null) {
                                                val title = web.optString("title", "Resource Webpage")
                                                val uri = web.optString("uri", "")
                                                if (uri.isNotBlank()) {
                                                    sourcesSb.append("\n- [$title]($uri)")
                                                }
                                            }
                                        }
                                        replyText += sourcesSb.toString()
                                    }
                                }

                                if (replyText.isNotBlank() || base64Img != null) {
                                    _aiChatMessages.value = _aiChatMessages.value + ChatMessage(
                                        text = replyText,
                                        isUser = false,
                                        base64Image = base64Img
                                    )
                                } else {
                                    _aiChatMessages.value = _aiChatMessages.value + ChatMessage(
                                        text = "Empty response received or response content was filtered.",
                                        isUser = false
                                    )
                                }
                            } else {
                                _aiChatMessages.value = _aiChatMessages.value + ChatMessage(
                                    text = "Invalid content parsed from server response.",
                                    isUser = false
                                )
                            }
                        } else {
                            _aiChatMessages.value = _aiChatMessages.value + ChatMessage(
                                text = "No candidates returned from server.",
                                isUser = false
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _aiChatMessages.value = _aiChatMessages.value + ChatMessage(
                    text = "Connection failure: ${e.localizedMessage ?: "Failed to connect to AI server. Please verify your internet connection."}",
                    isUser = false
                )
            } finally {
                _aiChatLoading.value = false
            }
        }
    }

    private val autoBackupDateFormatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
    }

    private val isAutoBackupRunning = java.util.concurrent.atomic.AtomicBoolean(false)

    fun getDefaultNewLoanTemplate(lang: String): String {
        return when (lang) {
            "Tamil" -> "அன்புள்ள {name},\nபுதிய கடன் கணக்கு தொடங்கப்பட்டுள்ளது,\nவார தவணை: ₹{weekly}, மொத்த நிலுவை: ₹{outstanding},\nசெலுத்த வேண்டிய முகவரி: {upi_link}\nநன்றி!"
            "Hindi" -> "प्रिय {name},\nनया ऋण खाता बनाया गया है,\nसाप्ताहिक किस्त: ₹{weekly}, कुल बकाया: ₹{outstanding},\nभुगतान लिंक: {upi_link}\nधन्यवाद!"
            "Telugu" -> "ప్రియమైన {name},\nకొత్త రుణ ఖాతా సృష్టించబడింది,\nవారపు వాయిదా: ₹{weekly}, బకాయి: ₹{outstanding},\nచెల్లింపు లింక్: {upi_link}\nధన్యవాదాలు!"
            else -> "Hi {name},\nNew Account Created,\nWeekly Instalment: ₹{weekly}, Outstanding: ₹{outstanding},\nIf you wish to pay: {upi_link} \nThank you!"
        }
    }

    fun getDefaultPaymentTemplate(lang: String): String {
        return when (lang) {
            "Tamil" -> "அன்புள்ள {name},\nவாரம் {week}-க்கான தவணைத் தொகை ₹{p_amount} பெறப்பட்டது,\nமீதமுள்ள நிலுவை: ₹{outstanding},\nநன்றி!"
            "Hindi" -> "प्रिय {name},\nसप्ताह {week} के लिए ₹{p_amount} की किस्त प्राप्त हुई,\nशेष बकाया: ₹{outstanding},\nधन्यवाद!"
            "Telugu" -> "ప్రియమైన {name},\nవారం {week} కొరకు ₹{p_amount} వాయిదా స్వీకరించబడింది,\nమిగిలిన బకాయి: ₹{outstanding},\nధన్యవాదాలు!"
            else -> "Hi {name},\nReceived ₹{p_amount} for Week {week},\nRemaining Balance: ₹{outstanding},\nThank you!"
        }
    }

    fun getDefaultReminderTemplate(lang: String): String {
        return when (lang) {
            "Tamil" -> "அன்புள்ள {name},\nஉங்கள் கடன் கணக்கிற்கான வாரத் தவணை ₹{weekly} செலுத்த வேண்டியுள்ளது,\nமீதித் தொகை: ₹{outstanding},\nதயவுசெய்து UPI மூலம் செலுத்தவும்: {upi_link}\nநன்றி!"
            "Hindi" -> "प्रिय {name},\nआपके ऋण खाते के लिए ₹{weekly} की साप्ताहिक किस्त लंबित है,\nशेष राशि: ₹{outstanding},\nकृपया UPI के माध्यम से भुगतान करें: {upi_link}\nधन्यवाद!"
            "Telugu" -> "ప్రియమైన {name},\nమీ రుణ ఖాతాకు సంబంధించిన వారపు వాయిదా ₹{weekly} బకాయి ఉంది,\nమిగిలిన మొత్తం: ₹{outstanding},\nదయచేసి UPI ద్వారా చెల్లించండి: {upi_link}\nధన్యవాదాలు!"
            else -> "Hi {name}, \nWeekly instalment of ₹{weekly} is pending for your weekly account,\nBalance: ₹{outstanding},\nPlease pay using UPI : {upi_link} \nThank you!"
        }
    }

    fun getDefaultWhatsappTemplate(lang: String): String {
        return when (lang) {
            "Tamil" -> "அன்புள்ள {name},\nவாராந்திர நினைவூட்டல்,\nஉங்கள் வார தவணைத் தொகை ₹{weekly} செலுத்த வேண்டிய நாள் இன்று,\nநிலுவைத் தொகை: *₹{outstanding}*,\nதயவுசெய்து UPI மூலம் செலுத்தவும்: {upi_link}\nநன்றி!"
            "Hindi" -> "प्रिय {name},\nसाप्ताहिक अनुस्मारक (Reminder),\nआपकी साप्ताहिक किस्त ₹{weekly} देय है,\nकुल बकाया राशि: *₹{outstanding}*,\nकृपया UPI के माध्यम से भुगतान करें: {upi_link}\nधन्यवाद!"
            "Telugu" -> "ప్రియమైన {name},\nవారపు రిమైండర్,\nమీ వారపు వాయిదా ₹{weekly} చెల్లించవలసి ఉంది,\nమొత్తం బకాయి: *₹{outstanding}*,\nదయచేసి UPI ద్వారా చెల్లించండి: {upi_link}\nధన్యవాదాలు!"
            else -> "Hi {name},\nWeekly Reminder,\nYour weekly collection of ₹{weekly} is due,\nOutstanding Balance: *₹{outstanding}*,\nPlease pay using UPI: {upi_link}\nThank you!"
        }
    }

    fun getSmsNewLoanTemplate(lang: String): String {
        val legacyKey = if (lang == "English") "sms_new_loan_template" else ""
        return if (legacyKey.isNotEmpty() && prefs.contains(legacyKey)) {
            prefs.getString(legacyKey, getDefaultNewLoanTemplate(lang)) ?: getDefaultNewLoanTemplate(lang)
        } else {
            prefs.getString("sms_new_loan_template_$lang", getDefaultNewLoanTemplate(lang)) ?: getDefaultNewLoanTemplate(lang)
        }
    }

    fun getSmsPaymentTemplate(lang: String): String {
        val legacyKey = if (lang == "English") "sms_payment_template" else ""
        val v = if (legacyKey.isNotEmpty() && prefs.contains(legacyKey)) {
            prefs.getString(legacyKey, getDefaultPaymentTemplate(lang)) ?: getDefaultPaymentTemplate(lang)
        } else {
            prefs.getString("sms_payment_template_$lang", getDefaultPaymentTemplate(lang)) ?: getDefaultPaymentTemplate(lang)
        }
        return if (v == getDefaultPaymentTemplate(lang) && lang == "English" && prefs.contains("sms_payment_entry_template")) {
            prefs.getString("sms_payment_entry_template", v) ?: v
        } else v
    }

    fun getSmsReminderTemplate(lang: String): String {
        val legacyKey = if (lang == "English") "sms_reminder_template" else ""
        return if (legacyKey.isNotEmpty() && prefs.contains(legacyKey)) {
            prefs.getString(legacyKey, getDefaultReminderTemplate(lang)) ?: getDefaultReminderTemplate(lang)
        } else {
            prefs.getString("sms_reminder_template_$lang", getDefaultReminderTemplate(lang)) ?: getDefaultReminderTemplate(lang)
        }
    }

    fun getWhatsappReminderTemplate(lang: String): String {
        val legacyKey = if (lang == "English") "whatsapp_reminder_template" else ""
        return if (legacyKey.isNotEmpty() && prefs.contains(legacyKey)) {
            prefs.getString(legacyKey, getDefaultWhatsappTemplate(lang)) ?: getDefaultWhatsappTemplate(lang)
        } else {
            prefs.getString("whatsapp_reminder_template_$lang", getDefaultWhatsappTemplate(lang)) ?: getDefaultWhatsappTemplate(lang)
        }
    }

    private val _currentTemplateLanguage = MutableStateFlow("English")
    val currentTemplateLanguage = _currentTemplateLanguage.asStateFlow()

    fun setTemplateLanguage(lang: String) {
        _currentTemplateLanguage.value = lang
        _smsNewLoanTemplate.value = getSmsNewLoanTemplate(lang)
        _smsPaymentTemplate.value = getSmsPaymentTemplate(lang)
        _smsReminderTemplate.value = getSmsReminderTemplate(lang)
        _whatsappReminderTemplate.value = getWhatsappReminderTemplate(lang)
    }

    // Customizable templates
    private val _smsNewLoanTemplate = MutableStateFlow(getSmsNewLoanTemplate("English"))
    val smsNewLoanTemplate = _smsNewLoanTemplate.asStateFlow()

    private val _smsPaymentTemplate = MutableStateFlow(getSmsPaymentTemplate("English"))
    val smsPaymentTemplate = _smsPaymentTemplate.asStateFlow()

    private val _smsReminderTemplate = MutableStateFlow(getSmsReminderTemplate("English"))
    val smsReminderTemplate = _smsReminderTemplate.asStateFlow()

    private val _whatsappReminderTemplate = MutableStateFlow(getWhatsappReminderTemplate("English"))
    val whatsappReminderTemplate = _whatsappReminderTemplate.asStateFlow()

    // Comma-separated dynamic collection groups. Default has sunday morning/evening and no friday!
    private val defaultGroups = "Monday,Tuesday,Wednesday,Thursday,Saturday,Sunday mrg,Sunday eve"
    private val _collectionGroups = MutableStateFlow(
        (prefs.getString("collection_groups_list", defaultGroups) ?: defaultGroups)
            .replace("Sunday Morning", "Sunday mrg")
            .replace("Sunday Evening", "Sunday eve")
            .split(",")
            .filter { it.isNotBlank() }
    )
    val collectionGroups = _collectionGroups.asStateFlow()

    // Sync variables removed
    val driveSyncEnabled = kotlinx.coroutines.flow.MutableStateFlow(false).asStateFlow()
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val _isExportImportLoading = MutableStateFlow(false)
    val isExportImportLoading = _isExportImportLoading.asStateFlow()

    private fun signInFirebaseAnonymouslySilently() {
        try {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            auth.signInAnonymously()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        android.util.Log.d("Firebase", "Silent App-Only Connection Established.")
                    } else {
                        android.util.Log.e("Firebase", "Connection failed: ${task.exception?.message}")
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val _syncPaused = MutableStateFlow(false)
    val syncPaused = _syncPaused.asStateFlow()

    fun setSyncPaused(paused: Boolean) {
        _syncPaused.value = paused
        prefs.edit().putBoolean("is_sync_paused", paused).apply()
        if (!paused) {
            signInFirebaseAnonymouslySilently()
            startFirebaseSyncListening()
            flushOfflineOutbox()
            if (currentUserRole.value == "ADMIN") {
                uploadLocalDataToFirebaseCloud()
            }
        } else {
            _firebaseSyncStatus.value = "Live Sync Paused"
            try {
                val rtdb = FirebaseDatabase.getInstance(com.example.util.SecureConfig.firebaseDatabaseUrl)
                val ref = rtdb.getReference("ledger_csv")
                firebaseListener?.let { ref.removeEventListener(it) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private var isRestoringFromJson = false

    init {
        // Force the JVM default timezone to Asia/Kolkata so that all formatting and start of day checks are fully synchronized across different physical devices
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Kolkata"))
        val savedSyncPaused = prefs.getBoolean("is_sync_paused", false)
        _syncPaused.value = savedSyncPaused
        
        val savedIsLoggedIn = if (bypassLogin) true else prefs.getBoolean("is_logged_in", false)
        val savedUser = if (bypassLogin) com.example.util.SecureConfig.adminUsername.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() } else (prefs.getString("current_user", "") ?: "")
        val savedRole = if (bypassLogin) "USER" else (prefs.getString("current_role", "USER") ?: "USER")

        _isLoggedIn.value = savedIsLoggedIn
        _currentUser.value = savedUser
        _username.value = savedUser
        _currentUserRole.value = savedRole
        repository.deviceUsername = savedUser
        
        repository.deviceUsername = _username.value
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        loadUnmappedPayments()
        loadConnectedDevices()
        _unsyncedLogCount.value = 0

        registerNetworkCallback()
        signInFirebaseAnonymouslySilently()
        startFirebaseSyncListening()
        viewModelScope.launch {
            try {
                triggerAutoBackupIfNeeded()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        viewModelScope.launch {
            try {
                triggerDatabaseRescanAndRepair()
                isReconcilingMaster.value = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        viewModelScope.launch {
            try {
                combine(
                    repository.allCustomers,
                    repository.allLoanCycles,
                    repository.allPayments
                ) { _, _, _ -> }
                    .debounce(3000)
                    .collect {
                        if (!isRestoringFromJson) {
                            markDirty()
                        }
                    }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun retryConnectivityForFirstLaunch() {
        try {
            updateOfflineStatus()
            isFirstLaunchOfflineBlocked.value = false
            _isOffline.value = false
            runMasterReconciliation()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        try {
            connectivityCallback?.let { callback ->
                val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                cm.unregisterNetworkCallback(callback)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }



    fun updateGoogleAccount(context: Context) {
        // Obsolete with custom Web App sync
    }

    fun markDirty() {
        if (currentUserRole.value == "ADMIN" && !syncPaused.value) {
            uploadLocalDataToFirebaseCloud()
        }
    }

    // Settings setters
    fun setUsername(name: String) {
        _username.value = name
        prefs.edit().putString("username", name).apply()
        repository.deviceUsername = name
        markDirty()
    }

    fun triggerDatabaseRescanAndRepair() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                _isExportImportLoading.value = true
                // Ensure all entities have correct UUIDs
                repository.populateMissingUuids()

                // Recalculate all loan cycle payments and statuses
                val finalLoans = db.collectionDao().getAllLoanCyclesOnce()
                val finalPayments = db.collectionDao().getAllPaymentsOnce()
                for (loan in finalLoans) {
                    val sumPaid = finalPayments.filter { it.loanCycleId == loan.id && it.status.uppercase() != "DELETED" }.sumOf { it.amountPaid }
                    val targetAmount = loan.loanAmount + loan.interestAmount
                    val computedStatus = if (sumPaid >= targetAmount) "PAID" else "ACTIVE"
                    if (loan.paidAmount != sumPaid || loan.status != computedStatus) {
                        db.collectionDao().updateLoanCycle(
                            loan.copy(paidAmount = sumPaid, status = computedStatus)
                        )
                    }
                }

                _syncFilesCount.value = db.collectionDao().getAllCustomersOnce().size
                // Record self-healing scan audit event
                val nameLocal = prefs.getString("username", "Device User") ?: "Device User"
                db.collectionDao().insertEditLog(
                    com.example.data.EditLog(
                        id = 0,
                        timestamp = System.currentTimeMillis(),
                        customerId = 0,
                        customerName = "System",
                        actionType = "DATABASE_REPAIR",
                        actionDescription = "Self-heal database rescan triggered by '$nameLocal': re-calculated and verified totals/outstanding for all active loan cycles.",
                        previousDataJson = "",
                        uuid = java.util.UUID.randomUUID().toString()
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isExportImportLoading.value = false
                markDirty()
            }
        }
    }

    fun recalibrateCalculationsSilent() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                repository.populateMissingUuids()
                val finalLoans = db.collectionDao().getAllLoanCyclesOnce()
                val finalPayments = db.collectionDao().getAllPaymentsOnce()
                for (loan in finalLoans) {
                    val sumPaid = finalPayments.filter { it.loanCycleId == loan.id && it.status.uppercase() != "DELETED" }.sumOf { it.amountPaid }
                    val targetAmount = loan.loanAmount + loan.interestAmount
                    val computedStatus = if (sumPaid >= targetAmount) "PAID" else "ACTIVE"
                    if (loan.paidAmount != sumPaid || loan.status != computedStatus) {
                        db.collectionDao().updateLoanCycle(
                            loan.copy(paidAmount = sumPaid, status = computedStatus)
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }



    private val isSyncingFromFirebase = false
    private var firebaseListener: ValueEventListener? = null

    fun startFirebaseSyncListening() {
        if (_isDemoMode.value) {
            _firebaseSyncStatus.value = "Demo Mode (Offline)"
            return
        }
        try {
            val rtdb = FirebaseDatabase.getInstance(com.example.util.SecureConfig.firebaseDatabaseUrl)
            val ref = rtdb.getReference("ledger_csv")

            firebaseListener?.let { ref.removeEventListener(it) }

            if (offlineModeEnabled.value) {
                _firebaseSyncStatus.value = "Offline Mode Active"
                return
            }

            if (syncPaused.value) {
                _firebaseSyncStatus.value = "Paused"
                return
            }

            if (currentUserRole.value != "USER") {
                _firebaseSyncStatus.value = "Standalone (Main Device)"
                return
            }

            _firebaseSyncStatus.value = "Connecting..."

            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (currentUserRole.value != "USER") return
                    if (syncPaused.value) return

                    val csvText = snapshot.getValue(String::class.java)
                    if (csvText.isNullOrBlank()) {
                        _firebaseSyncStatus.value = "Cloud Empty"
                        return
                    }

                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            _isSyncing.value = true
                            _firebaseSyncStatus.value = "Loading Cloud Ledger..."
                            val dao = db.collectionDao()
                            db.withTransaction {
                                dao.deleteAllPayments()
                                dao.deleteAllLoanCycles()
                                dao.deleteAllCustomers()
                            }
                            com.example.util.CsvBackupHelper.importCsvIntoDay(getApplication(), csvText, "ALL", db)
                            _firebaseSyncStatus.value = "Synced with Cloud"
                            _isSyncing.value = false
                        } catch (e: Exception) {
                            e.printStackTrace()
                            _firebaseSyncStatus.value = "Sync Error: ${e.localizedMessage}"
                            _isSyncing.value = false
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    _firebaseSyncStatus.value = error.message
                }
            }

            firebaseListener = listener
            ref.addValueEventListener(listener)
        } catch (e: Exception) {
            e.printStackTrace()
            _firebaseSyncStatus.value = "RTDB Init Failed"
        }
    }

    fun uploadLocalDataToFirebaseCloud() {
        if (isRestoringFromJson) return
        if (offlineModeEnabled.value) {
            _firebaseSyncStatus.value = "Offline Mode Active"
            return
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                _isSyncing.value = true
                val customersList = db.collectionDao().getAllCustomersOnce()
                val loanCyclesList = db.collectionDao().getAllLoanCyclesOnce()
                val paymentsList = db.collectionDao().getAllPaymentsOnce()
                val cashBalanceLogsList = db.collectionDao().getAllCashBalanceLogsOnce()

                val csvString = com.example.util.CsvBackupHelper.generateCsvString(
                    customers = customersList,
                    loanCycles = loanCyclesList,
                    payments = paymentsList,
                    dayFilter = "ALL",
                    cashBalanceLogs = cashBalanceLogsList
                )
                val rtdb = FirebaseDatabase.getInstance(com.example.util.SecureConfig.firebaseDatabaseUrl)
                val ref = rtdb.getReference("ledger_csv")
                val task = ref.setValue(csvString)
                com.google.android.gms.tasks.Tasks.await(task)
                _firebaseSyncStatus.value = "Synced (${customersList.size} borrowers)"
            } catch (e: Exception) {
                e.printStackTrace()
                val realError = if (e is java.util.concurrent.ExecutionException) e.cause ?: e else e
                _firebaseSyncStatus.value = "Cloud Sync Error: ${realError.message}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun forceUploadToFirebaseCloud() {
        if (offlineModeEnabled.value) {
            _firebaseSyncStatus.value = "Offline Mode Active"
            return
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                _isSyncing.value = true
                _firebaseSyncStatus.value = "Force Overwriting Cloud..."
                val customersList = db.collectionDao().getAllCustomersOnce()
                val loanCyclesList = db.collectionDao().getAllLoanCyclesOnce()
                val paymentsList = db.collectionDao().getAllPaymentsOnce()
                val cashBalanceLogsList = db.collectionDao().getAllCashBalanceLogsOnce()

                val csvString = com.example.util.CsvBackupHelper.generateCsvString(
                    customers = customersList,
                    loanCycles = loanCyclesList,
                    payments = paymentsList,
                    dayFilter = "ALL",
                    cashBalanceLogs = cashBalanceLogsList
                )
                val rtdb = FirebaseDatabase.getInstance(com.example.util.SecureConfig.firebaseDatabaseUrl)
                val ref = rtdb.getReference("ledger_csv")
                val task = ref.setValue(csvString)
                com.google.android.gms.tasks.Tasks.await(task)
                _firebaseSyncStatus.value = "Cloud Overwritten successfully!"
            } catch (e: Exception) {
                e.printStackTrace()
                val realError = if (e is java.util.concurrent.ExecutionException) e.cause ?: e else e
                _firebaseSyncStatus.value = "Force Upload failed: ${realError.message}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun login(username: String, password: String, context: Context, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (username.trim().lowercase(Locale.US) == "demo") {
            prefs.edit().apply {
                putBoolean("is_logged_in", true)
                putString("current_user", "Demo")
                putString("current_role", "USER")
                putBoolean("is_demo_mode", true)
                apply()
            }
            _isLoggedIn.value = true
            _isDemoMode.value = true
            _currentUser.value = "Demo"
            _currentUserRole.value = "ADMIN"
            _username.value = "Demo"
            AppDatabase.resetDatabaseInstances()
            (context as? android.app.Activity)?.recreate()
            onSuccess()
        } else if (username.trim().lowercase(Locale.US) == com.example.util.SecureConfig.adminUsername && password == com.example.util.SecureConfig.adminPassword) {
            val capUsername = com.example.util.SecureConfig.adminUsername.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            prefs.edit().apply {
                putBoolean("is_logged_in", true)
                putString("current_user", capUsername)
                putString("current_role", "ADMIN")
                putBoolean("is_demo_mode", false)
                apply()
            }
            _isLoggedIn.value = true
            _isDemoMode.value = false
            _currentUser.value = capUsername
            _currentUserRole.value = "ADMIN"
            _username.value = capUsername
            AppDatabase.resetDatabaseInstances()
            (context as? android.app.Activity)?.recreate()
            onSuccess()
        } else {
            onError("Invalid username or password!")
        }
    }

    fun signOut(context: Context) {
        prefs.edit().apply {
            putBoolean("is_logged_in", false)
            putString("current_user", "")
            putBoolean("has_completed_device_setup", false)
            putBoolean("is_demo_mode", false)
            apply()
        }
        _isLoggedIn.value = false
        _isDemoMode.value = false
        _currentUser.value = ""
        _username.value = ""
        _hasCompletedDeviceSetup.value = false
        AppDatabase.resetDatabaseInstances()
        (context as? android.app.Activity)?.recreate()
    }

    fun updateUserRole(role: String, context: Context) {
        prefs.edit().putString("current_role", role).apply()
        _currentUserRole.value = role
        AppDatabase.resetDatabaseInstances()
        (context as? android.app.Activity)?.recreate()
    }

    fun completeDeviceSetup(
        role: String,
        context: Context,
        uriString: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            if (role == "ADMIN") {
                _isExportImportLoading.value = true
                try {
                    val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            var stringContent = ""
                            if (uriString != null) {
                                val uri = android.net.Uri.parse(uriString)
                                val contentResolver = context.contentResolver
                                val inputStream = contentResolver.openInputStream(uri) ?: throw Exception("Could not open backup file")
                                val bytes = inputStream.readBytes()
                                stringContent = String(bytes, java.nio.charset.StandardCharsets.UTF_8).trim()
                            } else {
                                if (_isDemoMode.value) {
                                    stringContent = "" // allow empty in demo mode
                                } else {
                                    val rtdb = FirebaseDatabase.getInstance(com.example.util.SecureConfig.firebaseDatabaseUrl)
                                    val ref = rtdb.getReference("ledger_csv")
                                    val snapshot = com.google.android.gms.tasks.Tasks.await(ref.get())
                                    val cloudCsv = snapshot.value as? String
                                    if (cloudCsv.isNullOrBlank()) {
                                        throw Exception("No backup file selected and no backup found in Cloud.")
                                    }
                                    stringContent = cloudCsv.trim()
                                }
                            }

                            var success = true
                            if (stringContent.isNotBlank()) {
                                // 1. Import locally
                                success = com.example.util.CsvBackupHelper.importCsvIntoDay(
                                    context = context,
                                    csvText = stringContent,
                                    dayGroup = "ALL",
                                    db = db
                                )
                            }

                            if (success) {
                                triggerDatabaseRescanAndRepair()
                                
                                // 2. Revise cloud data too accordingly
                                val customersList = db.collectionDao().getAllCustomersOnce()
                                val loanCyclesList = db.collectionDao().getAllLoanCyclesOnce()
                                val paymentsList = db.collectionDao().getAllPaymentsOnce()
                                val cashBalanceLogsList = db.collectionDao().getAllCashBalanceLogsOnce()

                                val csvString = com.example.util.CsvBackupHelper.generateCsvString(
                                    customers = customersList,
                                    loanCycles = loanCyclesList,
                                    payments = paymentsList,
                                    dayFilter = "ALL",
                                    cashBalanceLogs = cashBalanceLogsList
                                )
                                
                                if (!offlineModeEnabled.value) {
                                    try {
                                        val rtdb = FirebaseDatabase.getInstance(com.example.util.SecureConfig.firebaseDatabaseUrl)
                                        val ref = rtdb.getReference("ledger_csv")
                                        val task = ref.setValue(csvString)
                                        com.google.android.gms.tasks.Tasks.await(task)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                true
                            } else {
                                false
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            throw e
                        }
                    }

                    if (result) {
                        // 3. Complete Setup
                        prefs.edit().apply {
                            putString("current_role", "ADMIN")
                            putBoolean("has_completed_device_setup", true)
                            apply()
                        }
                        _currentUserRole.value = "ADMIN"
                        _hasCompletedDeviceSetup.value = true
                        AppDatabase.resetDatabaseInstances()
                        onSuccess()
                    } else {
                        onError("Failed parsing CSV data. Please verify column structure matches instructions exactly.")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    onError(e.message ?: "Unknown setup restoration error")
                } finally {
                    _isExportImportLoading.value = false
                }
            } else {
                // Additional Device ("USER")
                prefs.edit().apply {
                    putString("current_role", "USER")
                    putBoolean("has_completed_device_setup", true)
                    apply()
                }
                _currentUserRole.value = "USER"
                _hasCompletedDeviceSetup.value = true
                AppDatabase.resetDatabaseInstances()
                onSuccess()
            }
        }
    }

    fun getFirebaseSyncUser(): String {
        return "muneeswaran"
    }

    fun runMasterReconciliation() {
        isReconcilingMaster.value = false
        isFirstLaunchOfflineBlocked.value = false
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            checkAndRoundExistingAmountsDb()
        }
    }

    private fun isNetworkConnected(): Boolean {
        return false
    }

    fun runMasterComparisonCheck(masterJsonStr: String, triggerDetail: String = "Auto Sync Check") {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val backupObj = org.json.JSONObject(masterJsonStr)
                val customersArray = backupObj.optJSONArray("customers") ?: org.json.JSONArray()
                val loansArray = backupObj.optJSONArray("loanCycles") ?: org.json.JSONArray()

                // Parse master customer UUIDs and details
                val masterLoansMap = mutableMapOf<String, Pair<Double, String>>() // loanUuid -> (outstanding, customerUuid)
                
                // Populate active loans outstanding from master
                for (i in 0 until loansArray.length()) {
                    val lObj = loansArray.getJSONObject(i)
                    val status = lObj.optString("status", "ACTIVE")
                    val uuid = lObj.optString("uuid", "")
                    var custUuid = lObj.optString("customerUuid", "")
                    if (custUuid.isEmpty() && lObj.has("customerId")) {
                        val cid = lObj.optInt("customerId", -1)
                        if (cid != -1) {
                            for (j in 0 until customersArray.length()) {
                                val cObj = customersArray.getJSONObject(j)
                                if (cObj.optInt("id", -1) == cid) {
                                    custUuid = cObj.optString("uuid", "")
                                    break
                                }
                            }
                        }
                    }
                    if (status.uppercase() == "ACTIVE" && uuid.isNotEmpty()) {
                        val amt = lObj.optDouble("loanAmount", 0.0)
                        val interest = lObj.optDouble("interestAmount", 0.0)
                        val paid = lObj.optDouble("paidAmount", 0.0)
                        val outstanding = maxOf(0.0, (amt + interest) - paid)
                        masterLoansMap[uuid] = Pair(outstanding, custUuid)
                    }
                }

                // Associate loans with master customers
                val masterCustOutstandingMap = mutableMapOf<String, Double>()
                for (i in 0 until customersArray.length()) {
                    val cObj = customersArray.getJSONObject(i)
                    val uuid = cObj.optString("uuid", "")
                    if (uuid.isNotEmpty()) {
                        masterCustOutstandingMap[uuid] = 0.0
                    }
                }
                for ((_, pair) in masterLoansMap) {
                    val outstanding = pair.first
                    val custUuid = pair.second
                    if (masterCustOutstandingMap.containsKey(custUuid)) {
                        masterCustOutstandingMap[custUuid] = (masterCustOutstandingMap[custUuid] ?: 0.0) + outstanding
                    }
                }

                val masterCustomerCount = customersArray.length()
                val masterOutstanding = masterLoansMap.values.sumOf { it.first }

                // Get local count and details
                val localCustomers = db.collectionDao().getAllCustomersOnce()
                val localLoans = db.collectionDao().getAllLoanCyclesOnce()
                
                val localCustCount = localCustomers.size
                val localOutstanding = localLoans.filter { it.status.uppercase() == "ACTIVE" }.sumOf { (it.loanAmount + it.interestAmount) - it.paidAmount }

                val mismatches = mutableListOf<String>()

                val unsyncedList = getOfflineOutbox()

                // Deep check each customer
                for (i in 0 until customersArray.length()) {
                    val cObj = customersArray.getJSONObject(i)
                    val uuid = cObj.optString("uuid", "")
                    val name = cObj.optString("name", "Unknown")
                    if (uuid.isEmpty()) continue

                    val localCust = localCustomers.find { it.uuid == uuid }
                    val masterOut = masterCustOutstandingMap[uuid] ?: 0.0

                    if (localCust == null) {
                        mismatches.add("Missing: '$name' exists in Cloud Master but not local.")
                    } else {
                        // Calculate local active outstanding for this specific customer
                        val custLoans = localLoans.filter { it.customerId == localCust.id && it.status.uppercase() == "ACTIVE" }
                        val localOut = custLoans.sumOf { (it.loanAmount + it.interestAmount) - it.paidAmount }
                        if (Math.abs(localOut - masterOut) > 1.0) {
                            val isCustomerUnsynced = unsyncedList.any { 
                                it.optString("payload").contains(localCust.uuid) || 
                                it.optString("actionType").contains("PAYMENT") || 
                                it.optString("actionType").contains("LOAN")
                            }
                            if (!isCustomerUnsynced && unsyncedList.isEmpty()) {
                                mismatches.add("Diff - '$name': ₹$localOut local vs ₹$masterOut cloud.")
                            }
                        }
                    }
                }

                // Check for local customers not in cloud master
                for (localCust in localCustomers) {
                    val hasInMaster = (0 until customersArray.length()).any {
                        customersArray.getJSONObject(it).optString("uuid", "") == localCust.uuid
                    }
                    if (!hasInMaster) {
                        val isPendingCreation = unsyncedList.any { 
                            it.optString("actionType") == "CREATE_CUSTOMER" && 
                            it.optString("payload").contains(localCust.uuid)
                        }
                        if (!isPendingCreation && unsyncedList.isEmpty()) {
                            mismatches.add("Unsynced: '${localCust.name}' exists locally only.")
                        }
                    }
                }

                _masterComparison.value = MasterComparisonReport(
                    lastCheckedTimestamp = System.currentTimeMillis(),
                    deviceId = deviceId.value,
                    masterOutstanding = masterOutstanding,
                    localOutstanding = localOutstanding,
                    outstandingMatches = Math.abs(localOutstanding - masterOutstanding) < 2.0 || unsyncedList.isNotEmpty(),
                    masterCustomerCount = masterCustomerCount,
                    localCustomerCount = localCustCount,
                    customerCountMatches = masterCustomerCount == localCustCount || unsyncedList.isNotEmpty(),
                    mismatchingCustomers = mismatches.take(15),
                    comparisonTriggerDetail = triggerDetail
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun pushToDeadLetterQueue(log: FirebaseEditLog, reason: String) {
        // Local only, bypass DLQ
    }

    private fun migratePayloadSchema(payloadObj: org.json.JSONObject): org.json.JSONObject {
        val version = payloadObj.optInt("v", 1)
        if (version == 1) {
            // Version 1 to Version 2 migration rules:
            // "method" or "payment_method" defaults to "Cash" for payment logs
            if (payloadObj.has("amountPaid") && !payloadObj.has("payment_method")) {
                payloadObj.put("payment_method", "Cash")
            }
            // Default "preferredLanguage" to "English" for old customer logs
            if (payloadObj.has("name") && !payloadObj.has("preferredLanguage")) {
                payloadObj.put("preferredLanguage", "English")
            }
            payloadObj.put("v", 2)
        }
        return payloadObj
    }

    private suspend fun applySingleEditLogLocally(log: FirebaseEditLog): Boolean {
        return try {
            val rawPayload = org.json.JSONObject(log.payload)
            val payloadObj = migratePayloadSchema(rawPayload)
            when (log.actionType) {
                "CREATE_CUSTOMER", "EDIT_CUSTOMER" -> {
                    val custUuid = payloadObj.optString("uuid")
                    if (custUuid.isBlank()) return false
                    val existingCusts = db.collectionDao().getAllCustomersOnce()
                    val existing = existingCusts.find { it.uuid == custUuid }

                    if (existing != null && existing.lastModified >= log.timestamp) {
                        // Timestamp Resolution - Don't overwrite newer local updates with older sync logs
                        return true
                    }
                    
                    val c = Customer(
                        id = existing?.id ?: 0,
                        name = payloadObj.optString("name"),
                        phone = payloadObj.optString("phone"),
                        customOrder = payloadObj.optInt("customOrder", existing?.customOrder ?: 0),
                        collectionDay = payloadObj.optString("collectionDay", "Monday"),
                        createdAt = payloadObj.optLong("createdAt", System.currentTimeMillis()),
                        city = payloadObj.optString("city", ""),
                        smsWeeklyReminder = payloadObj.optBoolean("smsWeeklyReminder", true),
                        smsConfirmationOfEntry = payloadObj.optBoolean("smsConfirmationOfEntry", true),
                        autoWeeklySms = payloadObj.optBoolean("autoWeeklySms", false),
                        autoWeeklyWhatsapp = payloadObj.optBoolean("autoWeeklyWhatsapp", false),
                        upiNameAlias = payloadObj.optString("upiNameAlias", ""),
                        preferredLanguage = payloadObj.optString("preferredLanguage", "English"),
                        uuid = custUuid,
                        lastModified = log.timestamp, // Maintain timestamp sync
                        syncedLastSavedAt = payloadObj.optLong("syncedLastSavedAt", existing?.syncedLastSavedAt ?: 0L),
                        status = payloadObj.optString("status", existing?.status ?: "ACTIVE")
                    )
                    if (existing != null) {
                        repository.updateCustomer(c)
                    } else {
                        repository.addCustomer(c)
                    }
                    true
                }
                "DELETE_CUSTOMER" -> {
                    val custUuid = payloadObj.optString("customerUuid")
                    val existingC = db.collectionDao().getAllCustomersOnce().find { it.uuid == custUuid }
                    if (existingC != null) {
                        if (existingC.lastModified >= log.timestamp) {
                            return true
                        }
                        repository.deleteCustomer(existingC)
                        true
                    } else false
                }
                "CREATE_LOAN", "EDIT_LOAN" -> {
                    val loanUuid = payloadObj.optString("uuid")
                    val custUuid = payloadObj.optString("customerUuid")
                    val existingCust = db.collectionDao().getAllCustomersOnce().find { it.uuid == custUuid }
                    
                    // Orphaned Record Fail-Safe: customer does not exist or was deleted, route to Dead Letter Queue
                    if (existingCust == null || existingCust.status.uppercase() == "DELETED") {
                        pushToDeadLetterQueue(log, "Dependent customer UUID $custUuid not found or was DELETED")
                        return true
                    }

                    val existingLoans = db.collectionDao().getAllLoanCyclesOnce()
                    val existingL = existingLoans.find { it.uuid == loanUuid }

                    if (existingL != null && existingL.lastModified >= log.timestamp) {
                        // Timestamp Resolution Collision check
                        return true
                    }
                    
                    val l = LoanCycle(
                        id = existingL?.id ?: 0,
                        customerId = existingCust.id,
                        loanAmount = payloadObj.optDouble("loanAmount"),
                        interestAmount = payloadObj.optDouble("interestAmount", 0.0),
                        weeklyAmount = payloadObj.optDouble("weeklyAmount"),
                        totalWeeks = payloadObj.optInt("totalWeeks", 10),
                        startDate = payloadObj.optLong("startDate", System.currentTimeMillis()),
                        status = payloadObj.optString("status", existingL?.status ?: "ACTIVE"),
                        notes = payloadObj.optString("notes", ""),
                        paidAmount = payloadObj.optDouble("paidAmount", 0.0),
                        uuid = loanUuid,
                        lastModified = log.timestamp
                    )
                    if (existingL != null) {
                        repository.updateLoanCycle(l)
                    } else {
                        repository.addLoanCycle(l)
                    }
                    true
                }
                "DELETE_LOAN" -> {
                    val loanUuid = payloadObj.optString("loanUuid")
                    val existingL = db.collectionDao().getAllLoanCyclesOnce().find { it.uuid == loanUuid }
                    if (existingL != null) {
                        if (existingL.lastModified >= log.timestamp) {
                            return true
                        }
                        repository.deleteLoanCycle(existingL)
                        true
                    } else false
                }
                "RECORD_PAYMENT", "EDIT_PAYMENT" -> {
                    val pUuid = payloadObj.optString("uuid")
                    val lUuid = payloadObj.optString("loanUuid")
                    val existingLoan = db.collectionDao().getAllLoanCyclesOnce().find { it.uuid == lUuid }

                    // Orphaned Record Fail-Safe: loan loop lookup failed, route immediately to Dead Letter Queue
                    if (existingLoan == null || existingLoan.status.uppercase() == "DELETED") {
                        pushToDeadLetterQueue(log, "Dependent LoanCycle with UUID $lUuid not found or is DELETED")
                        return true
                    }

                    val parentCustomer = db.collectionDao().getCustomerById(existingLoan.customerId)
                    if (parentCustomer == null || parentCustomer.status.uppercase() == "DELETED") {
                        pushToDeadLetterQueue(log, "Dependent Parent Customer with ID ${existingLoan.customerId} not found or is DELETED")
                        return true
                    }
                    
                    val existingPayments = db.collectionDao().getAllPaymentsOnce()
                    val existingP = existingPayments.find { it.uuid == pUuid }

                    if (existingP != null && existingP.lastModified >= log.timestamp) {
                        // Timestamp Resolution Collision check
                        return true
                    }
                    
                    val p = WeeklyPayment(
                        id = existingP?.id ?: 0,
                        loanCycleId = existingLoan.id,
                        amountPaid = payloadObj.optDouble("amountPaid"),
                        paymentDate = payloadObj.optLong("paymentDate", System.currentTimeMillis()),
                        weekNumber = payloadObj.optInt("weekNumber"),
                        notes = payloadObj.optString("notes", ""),
                        upiTxnId = if (payloadObj.has("upiTxnId") && !payloadObj.isNull("upiTxnId") && payloadObj.optString("upiTxnId").isNotEmpty()) payloadObj.optString("upiTxnId") else null,
                        uuid = pUuid,
                        lastModified = log.timestamp,
                        status = payloadObj.optString("status", existingP?.status ?: "ACTIVE")
                    )
                    if (existingP != null) {
                        repository.updateWeeklyPayment(p.id, p.loanCycleId, p.amountPaid, p.weekNumber, p.paymentDate, p.notes, p.upiTxnId)
                    } else {
                        repository.addWeeklyPayment(p)
                    }
                    true
                }
                "DELETE_PAYMENT" -> {
                    val pUuid = payloadObj.optString("paymentUuid")
                    val lUuid = payloadObj.optString("loanUuid")
                    val existingP = db.collectionDao().getAllPaymentsOnce().find { it.uuid == pUuid }
                    val existingLoan = db.collectionDao().getAllLoanCyclesOnce().find { it.uuid == lUuid }
                    if (existingP != null && existingLoan != null) {
                        if (existingP.lastModified >= log.timestamp) {
                            return true
                        }
                        repository.removeWeeklyPayment(existingP.id, existingLoan.id)
                        true
                    } else false
                }
                "CLEAR_DATABASE" -> {
                    db.withTransaction {
                        db.collectionDao().deleteAllPayments()
                        db.collectionDao().deleteAllLoanCycles()
                        db.collectionDao().deleteAllCustomers()
                        db.collectionDao().deleteAllCashBalanceLogs()
                    }
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun checkAndRoundExistingAmountsDb() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val dbDao = db.collectionDao()
                var databaseChanged = false

                val loans = dbDao.getAllLoanCyclesOnce()
                for (loan in loans) {
                    var needsUpdate = false
                    var loanAmount = loan.loanAmount
                    var interestAmount = loan.interestAmount
                    var weeklyAmount = loan.weeklyAmount
                    var paidAmount = loan.paidAmount

                    if (loanAmount % 1.0 != 0.0) {
                        loanAmount = Math.round(loanAmount / 10.0) * 10.0
                        needsUpdate = true
                    }
                    if (interestAmount % 1.0 != 0.0) {
                        interestAmount = Math.round(interestAmount / 10.0) * 10.0
                        needsUpdate = true
                    }
                    if (weeklyAmount % 1.0 != 0.0) {
                        weeklyAmount = Math.round(weeklyAmount / 10.0) * 10.0
                        needsUpdate = true
                    }
                    if (paidAmount % 1.0 != 0.0) {
                        paidAmount = Math.round(paidAmount / 10.0) * 10.0
                        needsUpdate = true
                    }

                    if (needsUpdate) {
                        val roundedLoan = loan.copy(
                            loanAmount = loanAmount,
                            interestAmount = interestAmount,
                            weeklyAmount = weeklyAmount,
                            paidAmount = paidAmount,
                            lastModified = System.currentTimeMillis()
                        )
                        dbDao.updateLoanCycle(roundedLoan)
                        databaseChanged = true
                    }
                }

                val payments = dbDao.getAllPaymentsOnce()
                for (payment in payments) {
                    var needsUpdate = false
                    var amountPaid = payment.amountPaid

                    if (amountPaid % 1.0 != 0.0) {
                        amountPaid = Math.round(amountPaid / 10.0) * 10.0
                        needsUpdate = true
                    }

                    if (needsUpdate) {
                        val roundedPayment = payment.copy(
                            amountPaid = amountPaid,
                            lastModified = System.currentTimeMillis()
                        )
                        dbDao.insertPayment(roundedPayment)
                        databaseChanged = true
                    }
                }

                if (databaseChanged) {
                    val currentBackupJson = getFullBackupJson()
                    _liveCloudJson.value = currentBackupJson
                    runMasterComparisonCheck(currentBackupJson, "Auto Rounding Migration")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun forceUploadLocalMasterToCloud(onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (_isDemoMode.value) {
            onError("Upload is disabled in Offline Tester Mode.")
            return
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val currentLoggedInUser = currentUser.value.trim()
                val currentRole = prefs.getString("current_role", "USER") ?: "USER"
                val isAdmin = currentRole.trim().uppercase(Locale.US) == "ADMIN" || currentLoggedInUser.equals(com.example.util.SecureConfig.adminUsername, ignoreCase = true)
                if (!isAdmin) {
                    throw Exception("Only Main Device is authorized to update the Master Database.")
                }

                _isExportImportLoading.value = true
                val currentBackupJson = getFullBackupJson()
                _liveCloudJson.value = currentBackupJson
                
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    onError(e.message ?: "Failed to save local master.")
                }
            } finally {
                _isExportImportLoading.value = false
            }
        }
    }

    private fun writeTextAtomic(file: java.io.File, text: String) {
        val tempFile = java.io.File(file.parent, "queue_temp.json")
        try {
            tempFile.writeText(text)
            if (file.exists()) {
                file.delete()
            }
            tempFile.renameTo(file)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    fun getOfflineOutbox(): List<org.json.JSONObject> {
        val list = mutableListOf<org.json.JSONObject>()
        try {
            if (prefs.contains("offline_outbox_queue_v1")) {
                val legacyStr = prefs.getString("offline_outbox_queue_v1", "[]") ?: "[]"
                if (legacyStr != "[]") {
                    try {
                        writeTextAtomic(outboxFile, legacyStr)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                prefs.edit().remove("offline_outbox_queue_v1").apply()
            }

            if (!outboxFile.exists()) {
                outboxFile.createNewFile()
                writeTextAtomic(outboxFile, "[]")
            }

            val outboxStr = outboxFile.readText()
            val arr = org.json.JSONArray(outboxStr.ifEmpty { "[]" })
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                if (obj != null) {
                    list.add(obj)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _unsyncedEditUuids.value = list.map { it.optString("uuid") }.toSet()
        return list
    }

    private fun saveOfflineOutbox(list: List<org.json.JSONObject>) {
        try {
            val arr = org.json.JSONArray()
            for (obj in list) {
                arr.put(obj)
            }
            if (!outboxFile.exists()) {
                outboxFile.createNewFile()
            }
            writeTextAtomic(outboxFile, arr.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _unsyncedLogCount.value = list.size
        _unsyncedEditUuids.value = list.map { it.optString("uuid") }.toSet()
    }

    fun flushOfflineOutbox() {
        _isSyncing.value = false
        _unsyncedLogCount.value = getOfflineOutbox().size
    }

    private fun getCrashproofOutboxFileName(userId: String): String {
        val sdf = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
        val formattedDate = sdf.format(java.util.Date())
        var baseName = "${formattedDate}_${userId}.json"
        val outboxDir = java.io.File(getApplication<Application>().filesDir, "edit_outbox")
        if (!outboxDir.exists()) {
            outboxDir.mkdirs()
        }
        var file = java.io.File(outboxDir, baseName)
        var counter = 1
        while (file.exists()) {
            baseName = "${formattedDate}_${counter}_${userId}.json"
            file = java.io.File(outboxDir, baseName)
            counter++
        }
        return file.name
    }

    fun pushFirebaseEditLog(
        actionType: String,
        payloadJson: String,
        previousPayloadJson: String = "",
        actionUuid: String = java.util.UUID.randomUUID().toString()
    ) {
        // App is complete offline, bypass cloud synchronization outbox
    }

    fun setSimSelection(sim: String) {
        _simSelection.value = sim
        prefs.edit().putString("sim_selection", sim).apply()
        markDirty()
    }

    fun setLanguage(lang: String) {
        val old = _language.value
        _language.value = lang
        prefs.edit().putString("language", lang).apply()
        markDirty()
        logPreferenceChange("language", old, lang)
    }

    fun setUpiId(id: String) {
        val old = _upiId.value
        _upiId.value = id
        prefs.edit().putString("upi_id", id).apply()
        markDirty()
        logPreferenceChange("upi_id", old, id)
    }

    fun setUpiLink(link: String) {
        val old = _upiLink.value
        _upiLink.value = link
        prefs.edit().putString("upi_link", link).apply()
        markDirty()
        logPreferenceChange("upi_link", old, link)
    }

    fun setBusinessName(name: String) {
        val old = _businessName.value
        _businessName.value = name
        prefs.edit().putString("business_name", name).apply()
        markDirty()
        logPreferenceChange("business_name", old, name)
    }

    fun setDeviceId(id: String) {
        _deviceId.value = id
        prefs.edit().putString("device_id", id).apply()
        // No firebase sync or markDirty() since this setting does not sync with firebase
    }

    fun setStatementCustomizationCode(code: String) {
        _statementCustomizationCode.value = code
        prefs.edit().putString("statement_customization_code", code).apply()
        markDirty()
    }

    fun setQrImageUri(uriStr: String) {
        _qrImageUri.value = uriStr
        prefs.edit().putString("qr_image_uri", uriStr).apply()
        markDirty()
    }

    fun saveSelectedQrCode(context: Context, uri: Uri) {
        try {
            val cacheFile = java.io.File(context.cacheDir, "custom_payment_qr.png")
            context.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            val providerUri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", cacheFile
            )
            val uriStr = providerUri.toString()
            _qrImageUri.value = uriStr
            prefs.edit().putString("qr_image_uri", uriStr).apply()
            markDirty()
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(context, "Failed to save QR Code: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun setSmsNewLoanTemplate(template: String, lang: String = _currentTemplateLanguage.value) {
        prefs.edit().putString("sms_new_loan_template_$lang", template).apply()
        if (lang == "English") {
            prefs.edit().putString("sms_new_loan_template", template).apply()
        }
        if (_currentTemplateLanguage.value == lang) {
            _smsNewLoanTemplate.value = template
        }
        markDirty()
    }

    fun setSmsPaymentTemplate(template: String, lang: String = _currentTemplateLanguage.value) {
        prefs.edit().putString("sms_payment_template_$lang", template).apply()
        if (lang == "English") {
            prefs.edit().putString("sms_payment_template", template).apply()
        }
        if (_currentTemplateLanguage.value == lang) {
            _smsPaymentTemplate.value = template
        }
        markDirty()
    }

    fun setCustomerSortMode(mode: String) {
        _customerSortMode.value = mode
        prefs.edit().putString("customer_sort_mode", mode).apply()
    }

    fun setSmsReminderTemplate(template: String, lang: String = _currentTemplateLanguage.value) {
        prefs.edit().putString("sms_reminder_template_$lang", template).apply()
        if (lang == "English") {
            prefs.edit().putString("sms_reminder_template", template).apply()
        }
        if (_currentTemplateLanguage.value == lang) {
            _smsReminderTemplate.value = template
        }
        markDirty()
    }

    fun setWhatsappReminderTemplate(template: String, lang: String = _currentTemplateLanguage.value) {
        prefs.edit().putString("whatsapp_reminder_template_$lang", template).apply()
        if (lang == "English") {
            prefs.edit().putString("whatsapp_reminder_template", template).apply()
        }
        if (_currentTemplateLanguage.value == lang) {
            _whatsappReminderTemplate.value = template
        }
        markDirty()
    }

    fun updateCollectionGroups(groups: List<String>) {
        _collectionGroups.value = groups
        val str = groups.joinToString(",")
        prefs.edit().putString("collection_groups_list", str).apply()
        markDirty()
    }

    fun setSmsPaused(paused: Boolean) {
        _smsPaused.value = paused
        prefs.edit().putBoolean("sms_paused", paused).apply()
        markDirty()
    }

    fun setFontSizeScale(scale: Float) {
        _fontSizeScale.value = scale
        prefs.edit().putFloat("font_size_scale", scale).apply()
        markDirty()
    }

    fun setSelectedTheme(theme: String) {
        _selectedTheme.value = "Sleek Slate"
        prefs.edit().putString("selected_theme", "Sleek Slate").apply()
        markDirty()
    }

    fun setCustomAppName(name: String) {
        _customAppName.value = name
        prefs.edit().putString("custom_app_name", name).apply()
        markDirty()
    }

    fun setCustomAppLogo(logo: String) {
        _customAppLogo.value = logo
        prefs.edit().putString("custom_app_logo", logo).apply()
        markDirty()
    }




    fun setSmsReaderPaused(paused: Boolean) {
        _smsReaderPaused.value = paused
        prefs.edit().putBoolean("sms_reader_paused", paused).apply()
        markDirty()
    }

    fun setAutoEntryPassing(enabled: Boolean) {
        _autoEntryPassing.value = enabled
        prefs.edit().putBoolean("auto_entry_passing", enabled).apply()
        markDirty()
    }

    fun setUpiLinkSharing(enabled: Boolean) {
        val old = _upiLinkSharing.value
        _upiLinkSharing.value = enabled
        prefs.edit().putBoolean("upi_link_sharing", enabled).apply()
        markDirty()
        logPreferenceChange("upi_link_sharing", old.toString(), enabled.toString())
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        _autoBackupEnabled.value = enabled
        prefs.edit().putBoolean("auto_backup_enabled", enabled).apply()
        markDirty()
        if (enabled) {
            viewModelScope.launch {
                triggerAutoBackupIfNeeded()
            }
        }
    }

    fun setAutoUpdateEnabled(enabled: Boolean) {
        _autoUpdateEnabled.value = enabled
        prefs.edit().putBoolean("auto_update_enabled", enabled).apply()
    }

    fun setForceUpdateEnabled(enabled: Boolean) {
        _forceUpdateEnabled.value = enabled
        prefs.edit().putBoolean("force_update_enabled", enabled).apply()
    }

    fun setPauseUpdatesEnabled(enabled: Boolean) {
        _pauseUpdatesEnabled.value = enabled
        prefs.edit().putBoolean("pause_updates_enabled", enabled).apply()
    }

    fun setGoogleContactsSyncEnabled(enabled: Boolean) {
        _googleContactsSyncEnabled.value = enabled
        prefs.edit().putBoolean("google_contacts_sync_enabled", enabled).apply()
    }

    fun setGoogleContactsSelectedAccount(account: String) {
        _googleContactsSelectedAccount.value = account
        prefs.edit().putString("google_contacts_selected_account", account).apply()
    }

    fun fetchGoogleAccounts() {
        val accounts = com.example.util.GoogleContactsSyncHelper.getGoogleAccounts(getApplication())
        _googleContactsAccountsList.value = accounts
        if (accounts.isNotEmpty() && _googleContactsSelectedAccount.value.isBlank()) {
            setGoogleContactsSelectedAccount(accounts[0])
        }
    }

    fun syncAllBorrowersToGoogleContacts(
        context: Context,
        onProgress: (Int, Int) -> Unit,
        onComplete: (Boolean, String) -> Unit
    ) {
        val account = _googleContactsSelectedAccount.value
        if (account.isBlank()) {
            onComplete(false, "No Google Account selected")
            return
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val customersList = db.collectionDao().getAllCustomersOnce().filter { it.phone.isNotBlank() }
                val total = customersList.size
                if (total == 0) {
                    onComplete(true, "No customers with valid phone numbers found.")
                    return@launch
                }

                var successCount = 0
                for ((index, cust) in customersList.withIndex()) {
                    val ok = com.example.util.GoogleContactsSyncHelper.syncCustomerToGoogleContacts(context, cust, account)
                    if (ok) {
                        successCount++
                    }
                    onProgress(index + 1, total)
                }
                onComplete(true, "Successfully synced $successCount of $total contacts.")
            } catch (e: Exception) {
                android.util.Log.e("FinanceViewModel", "Error syncing all contacts", e)
                onComplete(false, e.localizedMessage ?: "Unknown error occurred")
            }
        }
    }

    fun clearGoogleDriveBackupStatusMessage() {
        _googleDriveBackupStatusMessage.value = null
    }

    fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    private fun registerNetworkCallback() {
        val connectivityManager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val request = android.net.NetworkRequest.Builder()
            .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivityManager.registerNetworkCallback(request, object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    super.onAvailable(network)
                    viewModelScope.launch {
                        triggerAutoBackupIfNeeded()
                    }
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun triggerAutoBackupIfNeeded() {
        val isEnabled = prefs.getBoolean("auto_backup_enabled", true)
        if (!isEnabled) return

        val todayStr = autoBackupDateFormatter.format(java.util.Date())
        val lastBackupDate = prefs.getString("last_auto_backup_date", "") ?: ""
        if (lastBackupDate == todayStr) {
            return
        }

        if (!isNetworkAvailable()) {
            return
        }

        if (!isAutoBackupRunning.compareAndSet(false, true)) {
            return
        }

        try {
            val customersList = db.collectionDao().getAllCustomersOnce()
            val loanCyclesList = db.collectionDao().getAllLoanCyclesOnce()
            val paymentsList = db.collectionDao().getAllPaymentsOnce()
            val cashBalanceLogsList = db.collectionDao().getAllCashBalanceLogsOnce()

            val csvContent = com.example.util.CsvBackupHelper.generateCsvString(
                customers = customersList,
                loanCycles = loanCyclesList,
                payments = paymentsList,
                dayFilter = "ALL",
                cashBalanceLogs = cashBalanceLogsList
            )

            val (success, responseString) = uploadCsvToGoogleScript(csvContent, isManual = false)
            if (success) {
                prefs.edit().putString("last_auto_backup_date", todayStr).apply()
                android.util.Log.d("AutoBackup", "Auto backup succeeded: $responseString")
            } else {
                android.util.Log.e("AutoBackup", "Auto backup failed: $responseString")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("AutoBackup", "Error during auto backup: ${e.message}")
        } finally {
            isAutoBackupRunning.set(false)
        }
    }

    private suspend fun uploadCsvToGoogleScript(csvContent: String, isManual: Boolean): Pair<Boolean, String> {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val sdf = java.text.SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", java.util.Locale.US)
        val formattedTime = sdf.format(java.util.Date())
        val fileName = "finance_all_backup_$formattedTime.csv"

        val baseUrl = com.example.util.SecureConfig.googleScriptUrl
        val url = "$baseUrl?filename=${java.net.URLEncoder.encode(fileName, "UTF-8")}&manual=$isManual"

        val body = csvContent.toRequestBody("text/csv; charset=utf-8".toMediaTypeOrNull())

        val request = okhttp3.Request.Builder()
            .url(url)
            .post(body)
            .header("Content-Type", "text/csv; charset=utf-8")
            .build()

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: ""
                        Pair(true, bodyStr)
                    } else {
                        Pair(false, "Server returned code: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Pair(false, e.localizedMessage ?: "Unknown network error")
            }
        }
    }

    fun sendManualBackupToGoogleDrive() {
        if (offlineModeEnabled.value) {
            _googleDriveBackupStatusMessage.value = "Error: Cannot backup in Offline Mode"
            return
        }
        viewModelScope.launch {
            _isGoogleDriveBackupLoading.value = true
            _googleDriveBackupStatusMessage.value = "Generating CSV..."
            try {
                val customersList = db.collectionDao().getAllCustomersOnce()
                val loanCyclesList = db.collectionDao().getAllLoanCyclesOnce()
                val paymentsList = db.collectionDao().getAllPaymentsOnce()
                val cashBalanceLogsList = db.collectionDao().getAllCashBalanceLogsOnce()

                val csvContent = com.example.util.CsvBackupHelper.generateCsvString(
                    customers = customersList,
                    loanCycles = loanCyclesList,
                    payments = paymentsList,
                    dayFilter = "ALL",
                    cashBalanceLogs = cashBalanceLogsList
                )

                _googleDriveBackupStatusMessage.value = "Uploading to Google Drive..."
                val (success, responseString) = uploadCsvToGoogleScript(csvContent, isManual = true)
                if (success) {
                    _googleDriveBackupStatusMessage.value = "Backup uploaded successfully: Drive file saved!"
                    val todayStr = autoBackupDateFormatter.format(java.util.Date())
                    prefs.edit().putString("last_auto_backup_date", todayStr).apply()
                } else {
                    _googleDriveBackupStatusMessage.value = "Failed to upload: $responseString"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _googleDriveBackupStatusMessage.value = "Error: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                _isGoogleDriveBackupLoading.value = false
            }
        }
    }

    fun moveCollectionGroupUp(groupName: String) {
        val current = _collectionGroups.value.toMutableList()
        val index = current.indexOf(groupName)
        if (index > 0) {
            val temp = current[index]
            current[index] = current[index - 1]
            current[index - 1] = temp
            updateCollectionGroups(current)
        }
    }

    fun moveCollectionGroupDown(groupName: String) {
        val current = _collectionGroups.value.toMutableList()
        val index = current.indexOf(groupName)
        if (index >= 0 && index < current.size - 1) {
            val temp = current[index]
            current[index] = current[index + 1]
            current[index + 1] = temp
            updateCollectionGroups(current)
        }
    }

    fun deleteCollectionGroupAndCustomers(groupName: String) {
        viewModelScope.launch {
            val customersInGroup = allCustomers.value.filter { it.collectionDay.trim().equals(groupName.trim(), ignoreCase = true) }
            for (customer in customersInGroup) {
                try {
                    val loans = allLoanCycles.value.filter { it.customerId == customer.id }
                    val loanIds = loans.map { it.id }.toSet()
                    val payments = allPayments.value.filter { it.loanCycleId in loanIds }

                    val packObj = org.json.JSONObject().apply {
                        put("customer", org.json.JSONObject(customerToJson(customer)))
                        val loansArr = org.json.JSONArray()
                        loans.forEach { l -> loansArr.put(org.json.JSONObject(loanCycleToJson(l))) }
                        put("loans", loansArr)
                        val paymentsArr = org.json.JSONArray()
                        payments.forEach { p -> paymentsArr.put(org.json.JSONObject(weeklyPaymentToJson(p))) }
                        put("payments", paymentsArr)
                    }

                    repository.deleteCustomer(customer)
                    repository.addEditLog(
                        EditLog(
                            customerId = customer.id,
                            customerName = customer.name,
                            actionType = "DELETE_CUSTOMER",
                            actionDescription = "Deleted customer ${customer.name} (from group $groupName)",
                            previousDataJson = packObj.toString()
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            val newList = collectionGroups.value.toMutableList().apply { remove(groupName) }
            updateCollectionGroups(newList)
        }
    }

    fun deleteCollectionGroupAndTransferCustomers(groupName: String, destinationGroup: String) {
        viewModelScope.launch {
            val customersInGroup = allCustomers.value.filter { it.collectionDay.trim().equals(groupName.trim(), ignoreCase = true) }
            for (customer in customersInGroup) {
                try {
                    val previousJson = customerToJson(customer)
                    repository.updateCustomer(customer.copy(collectionDay = destinationGroup))
                    repository.addEditLog(
                        EditLog(
                            customerId = customer.id,
                            customerName = customer.name,
                            actionType = "EDIT_CUSTOMER",
                            actionDescription = "Transferred ${customer.name} from $groupName to $destinationGroup",
                            previousDataJson = previousJson
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            val newList = collectionGroups.value.toMutableList().apply { remove(groupName) }
            updateCollectionGroups(newList)
        }
    }

    fun renameCollectionGroup(oldName: String, newName: String) {
        viewModelScope.launch {
            val trimmedNewName = newName.trim()
            if (trimmedNewName.isBlank()) return@launch

            // Normalization check for Tuesday or Sundays
            val normalizedNewName = when (trimmedNewName.lowercase(java.util.Locale.getDefault())) {
                "tueasday", "tuesday" -> "Tuesday"
                "sunday morning", "sunday mrg", "mrg", "sunday morning (sunday mrg)", "sunday morning (mrg)" -> "Sunday mrg"
                "sunday evening", "sunday eve", "eve", "sunday evening (sunday eve)", "sunday evening (eve)" -> "Sunday eve"
                else -> trimmedNewName.split(" ").joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                }
            }

            // 1. Rename the group in collection groups list
            val current = _collectionGroups.value.map {
                if (it.trim().equals(oldName.trim(), ignoreCase = true)) {
                    normalizedNewName
                } else {
                    it
                }
            }.distinct()
            updateCollectionGroups(current)

            // 2. Update all customers whose collectionDay is oldName to normalizedNewName
            val customersInGroup = allCustomers.value.filter { it.collectionDay.trim().equals(oldName.trim(), ignoreCase = true) }
            for (customer in customersInGroup) {
                try {
                    repository.updateCustomer(customer.copy(collectionDay = normalizedNewName))
                    repository.addEditLog(
                        EditLog(
                            customerId = customer.id,
                            customerName = customer.name,
                            actionType = "EDIT_CUSTOMER",
                            actionDescription = "Renamed collection group from $oldName to $normalizedNewName for customer ${customer.name}"
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun resetAllSettingsAndTemplates() {
        setUsername("Device User")
        setSimSelection("SIM 1")
        setLanguage("English")
        setUpiId("9440736893@ptyes")
        setUpiLink("upi://pay?pa=9440736893@ptyes&pn=Muneeswaran%20P")
        setBusinessName("Muneeswaran P")
        setQrImageUri("")
        setSmsNewLoanTemplate("Hi {name},\nNew Account Created,\nWeekly Instalment: ₹{weekly}, Outstanding: ₹{outstanding},\nIf you wish to pay: {upi} \nThank you!")
        setSmsPaymentTemplate("Hi {name},\nReceived ₹{p_amount} for Week {week},\nRemaining Balance: ₹{outstanding},\nThank you!")
        setSmsReminderTemplate("Hi {name}, \nWeekly instalment of ₹{weekly} is pending for your weekly account,\nBalance: ₹{outstanding},\nPlease pay using UPI : {upi} \nThank you!")
        setWhatsappReminderTemplate("Hi {name},\nWeekly Reminder,\nYour weekly collection of ₹{weekly} is due,\nOutstanding Balance: *₹{outstanding}*,\nPlease pay using UPI: {upi}\nThank you!")
        val defaults = "Monday,Tuesday,Wednesday,Thursday,Saturday,Sunday mrg,Sunday eve"
        updateCollectionGroups(defaults.split(","))
        setSmsPaused(false)
        setFontSizeScale(1.0f)
        setSelectedTheme("Sleek Slate")
    }

    fun clearAllDatabaseData() {
        viewModelScope.launch {
            repository.restoreBackup(emptyList(), emptyList(), emptyList())
            repository.clearEditLogs()
            prefs.edit().remove("last_sheets_sync_time").apply()
            resetAllSettingsAndTemplates()
            if (!isSyncingFromFirebase) {
                pushFirebaseEditLog("CLEAR_DATABASE", "{}")
            }
        }
    }

    // Day Selector - "Home" by default!
    private val _selectedDay = MutableStateFlow("Home")
    val selectedDay: StateFlow<String> = _selectedDay.asStateFlow()

    fun selectDay(day: String) {
        _selectedDay.value = day
        dashboardScrollIndex = 0
        dashboardScrollOffset = 0
    }

    // Search and Filters
    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    fun updateSearchText(text: String) {
        _searchText.value = text
        dashboardScrollIndex = 0
        dashboardScrollOffset = 0
    }

    // Customer & Loan mapping for dashboard overview (separated by selectedDay)
    val customerOverviewList: StateFlow<List<CustomerCollectionItem>> = combine(
        allCustomers,
        activeLoanCycles,
        allLoanCycles,
        allPayments,
        selectedDay,
        searchText,
        customerSortMode
    ) { array ->
        @Suppress("UNCHECKED_CAST")
        val customers = array[0] as List<Customer>
        @Suppress("UNCHECKED_CAST")
        val activeLoans = array[1] as List<LoanCycle>
        @Suppress("UNCHECKED_CAST")
        val allLoans = array[2] as List<LoanCycle>
        @Suppress("UNCHECKED_CAST")
        val payments = array[3] as List<WeeklyPayment>
        val day = array[4] as String
        val search = array[5] as String
        val sortMode = array[6] as String

        val filteredByDay = if (day.equals("Home", ignoreCase = true)) {
            customers
        } else {
            customers.filter { it.collectionDay.equals(day, ignoreCase = true) }
        }
        
        // Sort by customOrder
        val sortedByOrder = filteredByDay.sortedBy { it.customOrder }
        
        // Fast indexing optimization O(N + M + P)
        val activeLoansByCustomer = activeLoans.groupBy { it.customerId }
        val allLoansByCustomer = allLoans.groupBy { it.customerId }
        val loanCustomerIdMap = allLoans.associate { it.id to it.customerId }
        val paymentsByCustomer = payments.groupBy { loanCustomerIdMap[it.loanCycleId] }

        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfToday = cal.timeInMillis

        val mapped = sortedByOrder.mapIndexed { idx, customer ->
            val customerPayments = paymentsByCustomer[customer.id] ?: emptyList()
            val customerActiveLoans = (activeLoansByCustomer[customer.id] ?: emptyList()).map { loan ->
                val actualPaid = customerPayments.filter { it.loanCycleId == loan.id && it.status.uppercase() != "DELETED" }.sumOf { it.amountPaid }
                loan.copy(paidAmount = actualPaid)
            }
            val customerAllLoans = allLoansByCustomer[customer.id] ?: emptyList()
            val latestPayment = customerPayments.maxByOrNull { it.paymentDate }
            
            val customerTodaysPayments = customerPayments.filter { it.paymentDate >= startOfToday && it.status == "ACTIVE" }
                .associate { it.loanCycleId to it.amountPaid }
            val customerTodaysDisbursed = customerAllLoans.filter { it.startDate >= startOfToday }

            CustomerCollectionItem(
                customer = customer,
                activeLoans = customerActiveLoans,
                lastPaymentDate = latestPayment?.paymentDate,
                lastPaymentAmount = latestPayment?.amountPaid,
                originalGroupIndex = idx + 1,
                todaysPayments = customerTodaysPayments,
                todaysDisbursedLoans = customerTodaysDisbursed
            )
        }
        
        val filtered = if (search.isBlank()) {
            mapped
        } else {
            mapped.filter {
                it.customer.name.contains(search, ignoreCase = true) ||
                it.customer.phone.contains(search, ignoreCase = true) ||
                it.customer.city.contains(search, ignoreCase = true)
            }
        }

        when (sortMode) {
            "NAME" -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.customer.name })
            "OUTSTANDING_DESC" -> filtered.sortedByDescending { item ->
                item.activeLoans.sumOf { (it.loanAmount + it.interestAmount) - it.paidAmount }
            }
            "WEEKLY_DUE_DESC" -> filtered.sortedByDescending { item ->
                item.activeLoans.sumOf { it.weeklyAmount }
            }
            "LAST_PAYMENT_ASC" -> filtered.sortedWith(
                compareBy<CustomerCollectionItem> { it.lastPaymentDate != null }
                    .thenBy { it.lastPaymentDate ?: 0L }
            )
            "LAST_PAYMENT_DESC" -> filtered.sortedWith(
                compareBy<CustomerCollectionItem> { it.lastPaymentDate == null }
                    .thenByDescending { it.lastPaymentDate ?: 0L }
            )
            else -> filtered // Already sorted by customOrder
        }
    }
    .flowOn(kotlinx.coroutines.Dispatchers.IO)
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Customer Actions
    fun createCustomer(
        name: String,
        phone: String,
        collectionDay: String,
        city: String = "",
        smsWeeklyReminder: Boolean = true,
        smsConfirmationOfEntry: Boolean = true,
        autoWeeklySms: Boolean = false,
        autoWeeklyWhatsapp: Boolean = false,
        upiNameAlias: String = "",
        preferredLanguage: String = "English"
    ) {
        viewModelScope.launch {
            if (currentUserRole.value == "USER") return@launch
            val noPhone = phone.trim().isEmpty()
            val finalSmsWeekly = if (noPhone) false else smsWeeklyReminder
            val finalSmsConf = if (noPhone) false else smsConfirmationOfEntry
            val finalAutoSms = if (noPhone) false else autoWeeklySms
            val finalAutoWa = if (noPhone) false else autoWeeklyWhatsapp

            val maxOrder = allCustomers.value.maxOfOrNull { it.customOrder } ?: 0
            val cust = Customer(
                name = name,
                phone = phone,
                customOrder = maxOrder + 1,
                collectionDay = collectionDay,
                city = city,
                smsWeeklyReminder = finalSmsWeekly,
                smsConfirmationOfEntry = finalSmsConf,
                autoWeeklySms = finalAutoSms,
                autoWeeklyWhatsapp = finalAutoWa,
                upiNameAlias = upiNameAlias,
                preferredLanguage = preferredLanguage
            )
            val newId = repository.addCustomer(cust)
            if (googleContactsSyncEnabled.value && googleContactsSelectedAccount.value.isNotBlank()) {
                val email = googleContactsSelectedAccount.value
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.util.GoogleContactsSyncHelper.syncCustomerToGoogleContacts(
                        getApplication(),
                        cust.copy(id = newId.toInt()),
                        email
                    )
                }
            }
            if (!isSyncingFromFirebase) {
                pushFirebaseEditLog("CREATE_CUSTOMER", customerToJson(cust.copy(id = newId.toInt())), "", cust.uuid)
                val params = android.os.Bundle().apply {
                    putString("customer_name", cust.name)
                    putString("collection_day", cust.collectionDay)
                }
                com.example.network.FirebaseAnalyticsManager.logEvent("create_customer", params)
            }
            repository.addEditLog(
                EditLog(
                    customerId = newId.toInt(),
                    customerName = name,
                    actionType = "CREATE_CUSTOMER",
                    actionDescription = "Created customer $name ($collectionDay)"
                )
            )
            createOrUpdateCustomerFiles(getApplication(), newId.toInt())
            markDirty()
        }
    }

    fun createOrUpdateCustomerFiles(context: android.content.Context, customerId: Int) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val customer = repository.getCustomerById(customerId) ?: return@launch
                val loans = repository.getLoanCyclesForCustomer(customer.id).firstOrNull() ?: emptyList()
                val activeLoan = loans.find { it.status == "ACTIVE" }
                val payments = repository.allPayments.firstOrNull() ?: emptyList()
                
                // Create directory structure
                val groupsDir = context.getExternalFilesDir("Groups") ?: return@launch
                val dayDir = java.io.File(groupsDir, customer.collectionDay)
                if (!dayDir.exists()) dayDir.mkdirs()
                
                val safeName = customer.name.replace(Regex("[^a-zA-Z0-9_ -]"), "").trim().replace(" ", "_")
                val customerFolder = java.io.File(dayDir, "${customer.customerCode}_$safeName")
                if (!customerFolder.exists()) customerFolder.mkdirs()
                
                // details.txt
                val txtFile = java.io.File(customerFolder, "details.txt")
                val txtContent = buildString {
                    appendLine("==================================================")
                    appendLine("        WEEKLY FINANCE - CUSTOMER CODE PAGE       ")
                    appendLine("==================================================")
                    appendLine("Customer Code:      ${customer.customerCode}")
                    appendLine("Full Name:          ${customer.name}")
                    appendLine("Phone Number:       ${customer.phone.ifBlank { "N/A" }}")
                    appendLine("City Location:      ${customer.city.ifBlank { "N/A" }}")
                    appendLine("Collection Group:   ${customer.collectionDay}")
                    appendLine("Preferred Lang:     ${customer.preferredLanguage}")
                    appendLine("UPI Name Alias:     ${customer.upiNameAlias.ifBlank { "N/A" }}")
                    appendLine("Created At:         ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(customer.createdAt))}")
                    appendLine("--------------------------------------------------")
                    appendLine("                  CONTRACT DETAILS                ")
                    appendLine("--------------------------------------------------")
                    if (activeLoan != null) {
                        appendLine("Loan Status:        ACTIVE")
                        appendLine("Loan Principal:     ₹${activeLoan.loanAmount}")
                        appendLine("Interest Amount:    ₹${activeLoan.interestAmount}")
                        appendLine("Total Outstanding:  ₹${activeLoan.loanAmount + activeLoan.interestAmount}")
                        appendLine("Weekly Installment: ₹${activeLoan.weeklyAmount}")
                        appendLine("Tenure Weeks:       ${activeLoan.totalWeeks}")
                        appendLine("Total Paid So Far:  ₹${activeLoan.paidAmount}")
                        val remaining = maxOf(0.0, (activeLoan.loanAmount + activeLoan.interestAmount) - activeLoan.paidAmount)
                        appendLine("Balance Remaining:  ₹$remaining")
                        appendLine("Contract Start:     ${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(activeLoan.startDate))}")
                    } else {
                        appendLine("Loan Status:        NO ACTIVE CONTRACT")
                    }
                    appendLine("--------------------------------------------------")
                    appendLine("                PAYMENT TRANSACTION HISTORY       ")
                    appendLine("--------------------------------------------------")
                    if (activeLoan != null) {
                        val matchingPayments = payments.filter { it.loanCycleId == activeLoan.id && it.status.uppercase() != "DELETED" }
                            .sortedBy { it.weekNumber }
                        if (matchingPayments.isEmpty()) {
                            appendLine("No payments recorded yet.")
                        } else {
                            appendLine("WkNo | Date       | InstAmount (₹) | Notes / Upi ID")
                            appendLine("-----+------------+----------------+--------------------")
                            for (p in matchingPayments) {
                                val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(p.paymentDate))
                                appendLine(String.format(java.util.Locale.US, "%-4d | %s | %-14.2f | %s", p.weekNumber, dateStr, p.amountPaid, p.notes.ifBlank { "Cash" }))
                            }
                        }
                    } else {
                        appendLine("No transaction details available.")
                    }
                    appendLine("==================================================")
                }
                txtFile.writeText(txtContent)
                
                // details.json
                val jsonFile = java.io.File(customerFolder, "details.json")
                val jsonContent = """
                    {
                      "customerCode": "${customer.customerCode}",
                      "id": ${customer.id},
                      "name": "${customer.name.replace("\"", "\\\"")}",
                      "phone": "${customer.phone}",
                      "city": "${customer.city.replace("\"", "\\\"")}",
                      "collectionDay": "${customer.collectionDay}",
                      "preferredLanguage": "${customer.preferredLanguage}",
                      "upiNameAlias": "${customer.upiNameAlias.replace("\"", "\\\"")}",
                      "createdAt": ${customer.createdAt},
                      "activeLoan": ${if (activeLoan != null) """{
                        "loanAmount": ${activeLoan.loanAmount},
                        "interestAmount": ${activeLoan.interestAmount},
                        "weeklyAmount": ${activeLoan.weeklyAmount},
                        "totalWeeks": ${activeLoan.totalWeeks},
                        "paidAmount": ${activeLoan.paidAmount},
                        "startDate": ${activeLoan.startDate}
                      }""" else "null"}
                    }
                """.trimIndent()
                jsonFile.writeText(jsonContent)

                // details.html
                val htmlFile = java.io.File(customerFolder, "details.html")
                val htmlContent = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <meta charset="utf-8">
                    <title>Customer Code Page - ${customer.customerCode}</title>
                    <style>
                        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f8fafc; color: #1e293b; margin: 0; padding: 20px; }
                        .card { background-color: #ffffff; max-width: 650px; margin: 0 auto; border: 1px solid #e2e8f0; border-radius: 12px; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1); overflow: hidden; }
                        .header { background-color: #0f172a; color: #ffffff; padding: 24px; text-align: center; }
                        .header h1 { margin: 0; font-size: 24px; letter-spacing: 1px; }
                        .header p { margin: 8px 0 0 0; color: #94a3b8; font-size: 14px; font-weight: bold; }
                        .content { padding: 24px; }
                        .section-title { font-size: 16px; font-weight: bold; border-left: 4px solid #3b82f6; padding-left: 8px; margin: 24px 0 12px 0; color: #0f172a; }
                        .info-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; padding: 8px 0; }
                        .info-item { display: flex; flex-direction: column; }
                        .info-label { font-size: 11px; color: #64748b; font-weight: bold; text-transform: uppercase; }
                        .info-value { font-size: 14px; font-weight: 600; margin-top: 2px; }
                        table { width: 100%; border-collapse: collapse; margin-top: 12px; font-size: 13px; }
                        th { background-color: #f1f5f9; text-align: left; padding: 10px; color: #475569; font-weight: bold; }
                        td { padding: 10px; border-bottom: 1px solid #e2e8f0; }
                        .footer { background-color: #f1f5f9; padding: 12px; text-align: center; font-size: 12px; color: #64748b; }
                    </style>
                    </head>
                    <body>
                    <div class="card">
                        <div class="header">
                            <h1>WEEKLY FINANCE</h1>
                            <p>CUSTOMER PROFILE CODE: ${customer.customerCode}</p>
                        </div>
                        <div class="content">
                            <div class="section-title">Customer Contact & Info</div>
                            <div class="info-grid">
                                <div class="info-item"><span class="info-label">Full Name</span><span class="info-value">${customer.name}</span></div>
                                <div class="info-item"><span class="info-label">Phone</span><span class="info-value">${customer.phone.ifBlank { "N/A" }}</span></div>
                                <div class="info-item"><span class="info-label">City</span><span class="info-value">${customer.city.ifBlank { "N/A" }}</span></div>
                                <div class="info-item"><span class="info-label">Collection Group</span><span class="info-value">${customer.collectionDay}</span></div>
                                <div class="info-item"><span class="info-label">UPI ID Alias</span><span class="info-value">${customer.upiNameAlias.ifBlank { "N/A" }}</span></div>
                                <div class="info-item"><span class="info-label">Preferred Language</span><span class="info-value">${customer.preferredLanguage}</span></div>
                            </div>
                            
                            <div class="section-title">Active Contract Information</div>
                            ${if (activeLoan != null) """
                            <div class="info-grid">
                                <div class="info-item"><span class="info-label">Loan Handed Over</span><span class="info-value">₹${activeLoan.loanAmount}</span></div>
                                <div class="info-item"><span class="info-label">Interest Charges</span><span class="info-value">₹${activeLoan.interestAmount}</span></div>
                                <div class="info-item"><span class="info-label">Total Outstanding</span><span class="info-value">₹${activeLoan.loanAmount + activeLoan.interestAmount}</span></div>
                                <div class="info-item"><span class="info-label">Weekly Installment</span><span class="info-value">₹${activeLoan.weeklyAmount}</span></div>
                                <div class="info-item"><span class="info-label">Paid So Far</span><span class="info-value">₹${activeLoan.paidAmount}</span></div>
                                <div class="info-item"><span class="info-label">Remaining Balance</span><span class="info-value">₹${maxOf(0.0, (activeLoan.loanAmount + activeLoan.interestAmount) - activeLoan.paidAmount)}</span></div>
                            </div>
                            """ else """<p style="font-size: 14px; margin: 0; color: #64748b;">No active loan contract found for this borrower.</p>"""}
                            
                            <div class="section-title">Installment Collections History</div>
                            ${if (activeLoan != null) {
                                val matchingPayments = payments.filter { it.loanCycleId == activeLoan.id && it.status.uppercase() != "DELETED" }.sortedBy { it.weekNumber }
                                if (matchingPayments.isEmpty()) {
                                    """<p style="font-size: 13px; margin: 0; color: #64748b;">No collections recorded yet for this active loan contract.</p>"""
                                } else {
                                    val sb = java.lang.StringBuilder()
                                    sb.append("<table><thead><tr><th>Week</th><th>Date</th><th>Amount Paid</th><th>Reference / Mode</th></tr></thead><tbody>")
                                    for (p in matchingPayments) {
                                        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(p.paymentDate))
                                        sb.append("<tr><td>Wk ${p.weekNumber}</td><td>$dateStr</td><td><strong>₹${p.amountPaid}</strong></td><td>${p.notes.ifBlank { "Cash" }}</td></tr>")
                                    }
                                    sb.append("</tbody></table>")
                                    sb.toString()
                                }
                            } else """<p style="font-size: 13px; margin: 0; color: #64748b;">N/A</p>"""}
                        </div>
                        <div class="footer">
                            Generated by Weekly Finance app. Verified Offline Storage.
                        </div>
                    </div>
                    </body>
                    </html>
                """.trimIndent()
                htmlFile.writeText(htmlContent)
                
            } catch (e: Exception) {
                android.util.Log.e("WeeklyFinance", "Error creating customer folder/code_page files: ${e.message}")
            }
        }
    }

    fun bulkImportFromCsv(
        rawCsvText: String,
        onResult: (successCount: Int, formatErrorCount: Int) -> Unit
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            val parseResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                if (rawCsvText.isBlank()) {
                    return@withContext ParseResult(emptyList(), 0)
                }

                val lines = rawCsvText.lines().map { it.trim() }.filter { it.isNotBlank() }
                val parsedItems = mutableListOf<ParsedImportItem>()
                var errors = 0

                lines.forEach { line ->
                    val parts = line.split(",").map { it.trim() }
                    if (parts.size >= 2) {
                        val name = parts.getOrNull(0) ?: ""
                        val grp = parts.getOrNull(1) ?: ""

                        if (name.isBlank() || grp.isBlank()) {
                            errors++
                        } else {
                            try {
                                val rawFormattedGrp = grp.split(" ").joinToString(" ") { word ->
                                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                                }
                                val formattedGrp = when (rawFormattedGrp.trim().lowercase(Locale.getDefault())) {
                                    "tueasday", "tuesday" -> "Tuesday"
                                    "sunday morning", "sunday mrg", "mrg", "sunday morning (sunday mrg)", "sunday morning (mrg)" -> "Sunday mrg"
                                    "sunday evening", "sunday eve", "eve", "sunday evening (sunday eve)", "sunday evening (eve)" -> "Sunday eve"
                                    else -> rawFormattedGrp
                                }

                                val loanAmtStr = parts.getOrNull(2) ?: ""
                                val amtReceivedStr = parts.getOrNull(3) ?: ""
                                val phone = parts.getOrNull(4) ?: ""
                                val city = parts.getOrNull(5) ?: ""

                                val loanAmt = loanAmtStr.toDoubleOrNull() ?: 0.0
                                val amtReceived = amtReceivedStr.toDoubleOrNull() ?: 0.0

                                parsedItems.add(
                                    ParsedImportItem(
                                        name = name,
                                        collectionDay = formattedGrp,
                                        loanAmt = loanAmt,
                                        amtReceived = amtReceived,
                                        phone = phone,
                                        city = city
                                    )
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                                errors++
                            }
                        }
                    } else {
                        errors++
                    }
                }
                ParseResult(parsedItems, errors)
            }

            if (parseResult.items.isEmpty() && parseResult.errors == 0) {
                onResult(0, 0)
                return@launch
            }

            val dbResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                var success = 0
                var errors = parseResult.errors

                val currentGroups = _collectionGroups.value.toMutableList()
                val distinctGroupsToAdd = parseResult.items.map { it.collectionDay }.distinct()
                var groupsChanged = false

                distinctGroupsToAdd.forEach { formattedGrp ->
                    val exists = currentGroups.any { it.trim().equals(formattedGrp, ignoreCase = true) }
                    if (!exists) {
                        currentGroups.add(formattedGrp)
                        groupsChanged = true
                    }
                }

                if (groupsChanged) {
                    val daysString = currentGroups.distinct().joinToString(",")
                    _collectionGroups.value = currentGroups.distinct()
                    prefs.edit().putString("collection_groups_list", daysString).apply()
                }

                var maxOrder = allCustomers.value.maxOfOrNull { it.customOrder } ?: 0

                parseResult.items.forEach { item ->
                    try {
                        maxOrder++
                        val cust = Customer(
                            name = item.name,
                            phone = item.phone,
                            customOrder = maxOrder,
                            collectionDay = item.collectionDay,
                            city = item.city,
                            smsWeeklyReminder = true,
                            smsConfirmationOfEntry = true,
                            autoWeeklySms = false,
                            autoWeeklyWhatsapp = false
                        )
                        val customerId = repository.addCustomer(cust).toInt()

                        repository.addEditLog(
                            EditLog(
                                customerId = customerId,
                                customerName = item.name,
                                actionType = "CREATE_CUSTOMER",
                                actionDescription = "Created customer ${item.name} (${item.collectionDay}) via bulk import"
                            )
                        )

                        if (item.loanAmt > 0.0) {
                            val interest = item.loanAmt * 0.10
                            val totalWeeks = 10
                            val weeklyAmount = (item.loanAmt + interest) / totalWeeks

                            val loanObj = LoanCycle(
                                customerId = customerId,
                                loanAmount = item.loanAmt,
                                interestAmount = interest,
                                weeklyAmount = weeklyAmount,
                                totalWeeks = totalWeeks,
                                status = "ACTIVE",
                                notes = "Bulk Imported Loan",
                                paidAmount = 0.0
                            )
                            val loanCycleId = repository.addLoanCycle(loanObj).toInt()

                            repository.addEditLog(
                                EditLog(
                                    customerId = customerId,
                                    customerName = item.name,
                                    actionType = "CREATE_LOAN",
                                    actionDescription = "Created active loan of ₹${item.loanAmt} (Interest: ₹$interest) for ${item.name}",
                                    previousDataJson = loanCycleId.toString()
                                )
                            )

                            if (item.amtReceived > 0.0) {
                                repository.addWeeklyPayment(
                                    WeeklyPayment(
                                        loanCycleId = loanCycleId,
                                        amountPaid = item.amtReceived,
                                        weekNumber = 1,
                                        paymentDate = System.currentTimeMillis(),
                                        notes = "Collected during bulk import"
                                    )
                                )

                                repository.addEditLog(
                                    EditLog(
                                        customerId = customerId,
                                        customerName = item.name,
                                        actionType = "RECORD_PAYMENT",
                                        actionDescription = "Recorded ₹${item.amtReceived} collection for Week 1 of ${item.name}"
                                    )
                                )
                            }
                        }
                        success++
                    } catch (e: Exception) {
                        e.printStackTrace()
                        errors++
                    }
                }
                Pair(success, errors)
            }

            triggerDatabaseRescanAndRepair()
            onResult(dbResult.first, dbResult.second)
        }
    }

    fun editCustomer(
        customerId: Int,
        name: String,
        phone: String,
        collectionDay: String,
        city: String = "",
        smsWeeklyReminder: Boolean = true,
        smsConfirmationOfEntry: Boolean = true,
        autoWeeklySms: Boolean = false,
        autoWeeklyWhatsapp: Boolean = false,
        upiNameAlias: String = "",
        preferredLanguage: String = "English"
    ) {
        viewModelScope.launch {
            if (currentUserRole.value == "USER") return@launch
            val existing = repository.getCustomerById(customerId)
            if (existing != null) {
                val noPhone = phone.trim().isEmpty()
                val finalSmsWeekly = if (noPhone) false else smsWeeklyReminder
                val finalSmsConf = if (noPhone) false else smsConfirmationOfEntry
                val finalAutoSms = if (noPhone) false else autoWeeklySms
                val finalAutoWa = if (noPhone) false else autoWeeklyWhatsapp

                val previousJson = customerToJson(existing)
                val updatedObj = existing.copy(
                    name = name,
                    phone = phone,
                    collectionDay = collectionDay,
                    city = city,
                    smsWeeklyReminder = finalSmsWeekly,
                    smsConfirmationOfEntry = finalSmsConf,
                    autoWeeklySms = finalAutoSms,
                    autoWeeklyWhatsapp = finalAutoWa,
                    upiNameAlias = upiNameAlias,
                    preferredLanguage = preferredLanguage
                )
                val updatedJson = customerToJson(updatedObj)
                repository.updateCustomer(updatedObj)
                if (googleContactsSyncEnabled.value && googleContactsSelectedAccount.value.isNotBlank()) {
                    val email = googleContactsSelectedAccount.value
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        com.example.util.GoogleContactsSyncHelper.syncCustomerToGoogleContacts(
                            getApplication(),
                            updatedObj,
                            email
                        )
                    }
                }
                if (!isSyncingFromFirebase) {
                    pushFirebaseEditLog("EDIT_CUSTOMER", updatedJson, previousJson, updatedObj.uuid)
                    val params = android.os.Bundle().apply {
                        putString("customer_name", updatedObj.name)
                        putString("collection_day", updatedObj.collectionDay)
                    }
                    com.example.network.FirebaseAnalyticsManager.logEvent("edit_customer", params)
                }
                val changeInfo = buildDetailedCustomerChangeDesc(previousJson, updatedJson)
                repository.addEditLog(
                    EditLog(
                        customerId = customerId,
                        customerName = name,
                        actionType = "EDIT_CUSTOMER",
                        actionDescription = "Updated customer details of $name: ${changeInfo.second}",
                        previousDataJson = previousJson
                    )
                )
                createOrUpdateCustomerFiles(getApplication(), customerId)
                markDirty()
            }
        }
    }

    fun deleteCustomer(customer: Customer) {
        viewModelScope.launch {
            if (currentUserRole.value == "USER") return@launch
            try {
                val loans = allLoanCycles.value.filter { it.customerId == customer.id }
                val loanIds = loans.map { it.id }.toSet()
                val payments = allPayments.value.filter { it.loanCycleId in loanIds }

                val packObj = org.json.JSONObject().apply {
                    put("customer", org.json.JSONObject(customerToJson(customer)))
                    val loansArr = org.json.JSONArray()
                    loans.forEach { l -> loansArr.put(org.json.JSONObject(loanCycleToJson(l))) }
                    put("loans", loansArr)
                    val paymentsArr = org.json.JSONArray()
                    payments.forEach { p -> paymentsArr.put(org.json.JSONObject(weeklyPaymentToJson(p))) }
                    put("payments", paymentsArr)
                }

                val deletedCustomer = customer.copy(status = "DELETED")
                repository.updateCustomer(deletedCustomer)
                loans.forEach { l ->
                    repository.updateLoanCycle(l.copy(status = "DELETED"))
                }
                payments.forEach { p ->
                    repository.insertWeeklyPayment(p.copy(status = "DELETED"))
                }
                if (!isSyncingFromFirebase) {
                    val pLoad = org.json.JSONObject().apply {
                        put("customerUuid", customer.uuid)
                    }.toString()
                    pushFirebaseEditLog("DELETE_CUSTOMER", pLoad, packObj.toString(), customer.uuid)
                }
                repository.addEditLog(
                    EditLog(
                        customerId = customer.id,
                        customerName = customer.name,
                        actionType = "DELETE_CUSTOMER",
                        actionDescription = "Deleted customer ${customer.name} (Phone: ${customer.phone.ifBlank { "N/A" }}, City: ${customer.city.ifBlank { "N/A" }}, Group: ${customer.collectionDay}) along with ${loans.size} active/completed loan cycles",
                        previousDataJson = packObj.toString()
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            markDirty()
            // Go back to dashboard if we are deleting currently viewed customer
            when (val screen = currentScreen.value) {
                is Screen.CustomerDetail -> {
                    if (screen.customerId == customer.id) {
                        navigateBack()
                    }
                }
                else -> {}
            }
        }
    }

    // Swapping Custom Sorting Orders specifically within current active list group
    fun moveCustomerUp(item: CustomerCollectionItem) {
        viewModelScope.launch {
            val day = selectedDay.value
            val listForDay = allCustomers.value.filter {
                day == "Home" || it.collectionDay.equals(day, ignoreCase = true)
            }.sortedBy { it.customOrder }

            val indexInDay = listForDay.indexOfFirst { it.id == item.customer.id }
            if (indexInDay > 0) {
                val prevInDay = listForDay[indexInDay - 1]
                val prevOrder = prevInDay.customOrder
                val selfOrder = item.customer.customOrder

                repository.updateCustomerOrder(item.customer.id, prevOrder)
                repository.updateCustomerOrder(prevInDay.id, selfOrder)
            }
        }
    }

    fun moveCustomerDown(item: CustomerCollectionItem) {
        viewModelScope.launch {
            val day = selectedDay.value
            val listForDay = allCustomers.value.filter {
                day == "Home" || it.collectionDay.equals(day, ignoreCase = true)
            }.sortedBy { it.customOrder }

            val indexInDay = listForDay.indexOfFirst { it.id == item.customer.id }
            if (indexInDay != -1 && indexInDay < listForDay.size - 1) {
                val nextInDay = listForDay[indexInDay + 1]
                val nextOrder = nextInDay.customOrder
                val selfOrder = item.customer.customOrder

                repository.updateCustomerOrder(item.customer.id, nextOrder)
                repository.updateCustomerOrder(nextInDay.id, selfOrder)
            }
        }
    }

    fun reorderCustomerToPosition(item: CustomerCollectionItem, targetPosition: Int) {
        viewModelScope.launch {
            val day = selectedDay.value
            val listForDay = allCustomers.value.filter {
                day == "Home" || it.collectionDay.equals(day, ignoreCase = true)
            }.sortedBy { it.customOrder }.toMutableList()

            val currentIndex = listForDay.indexOfFirst { it.id == item.customer.id }
            if (currentIndex == -1) return@launch

            val itemToMove = listForDay.removeAt(currentIndex)
            val targetIndex = (targetPosition - 1).coerceIn(0, listForDay.size)
            listForDay.add(targetIndex, itemToMove)

            listForDay.forEachIndexed { i, customer ->
                repository.updateCustomerOrder(customer.id, i + 1)
            }
        }
    }

    // Loan Cycle Actions & Calculations
    fun createLoanCycle(
        context: Context,
        customerId: Int,
        amount: Double,
        interest: Double,
        weeklyInstalment: Double,
        tenureWeeks: Int,
        notes: String,
        startDate: Long = System.currentTimeMillis(),
        deduction: Double = 0.0
    ) {
        viewModelScope.launch {
            if (currentUserRole.value == "USER") return@launch
            val loanObj = LoanCycle(
                customerId = customerId,
                loanAmount = amount,
                interestAmount = interest,
                weeklyAmount = weeklyInstalment,
                totalWeeks = tenureWeeks,
                status = "ACTIVE",
                notes = notes,
                paidAmount = 0.0,
                startDate = startDate,
                deduction = deduction
            )
            val cycleId = repository.addLoanCycle(loanObj)
            val customer = repository.getCustomerById(customerId)
            if (customer != null && !isSyncingFromFirebase) {
                val pObj = org.json.JSONObject(loanCycleToJson(loanObj.copy(id = cycleId.toInt())))
                pObj.put("customerUuid", customer.uuid)
                pushFirebaseEditLog("CREATE_LOAN", pObj.toString(), "", loanObj.uuid)
            }

            val customerNameObj = customer?.name ?: "Customer"
            repository.addEditLog(
                EditLog(
                    customerId = customerId,
                    customerName = customerNameObj,
                    actionType = "CREATE_LOAN",
                    actionDescription = "Created loan of ₹$amount for $customerNameObj (deduction: ₹$deduction)",
                    previousDataJson = cycleId.toString()
                )
            )

            // Auto-trigger confirmation entry SMS if customer settings allow
            if (customer != null && customer.smsConfirmationOfEntry) {
                val dummyLoan = LoanCycle(
                    id = cycleId.toInt(),
                    customerId = customerId,
                    loanAmount = amount,
                    interestAmount = interest,
                    weeklyAmount = weeklyInstalment,
                    totalWeeks = tenureWeeks,
                    paidAmount = 0.0,
                    deduction = deduction
                )
                triggerNewLoanSms(context, customer, dummyLoan)
            }
            createOrUpdateCustomerFiles(context, customerId)
            markDirty()
        }
    }

    fun markLoanCycleSettled(loanCycleId: Int) {
        viewModelScope.launch {
            if (currentUserRole.value == "USER") return@launch
            val cycle = repository.getLoanCycleById(loanCycleId)
            if (cycle != null) {
                val previousJson = loanCycleToJson(cycle)
                val updatedCycle = cycle.copy(status = "PAID")
                repository.updateLoanCycle(updatedCycle)
                val customer = repository.getCustomerById(cycle.customerId)
                if (customer != null && !isSyncingFromFirebase) {
                    val pObj = org.json.JSONObject(loanCycleToJson(updatedCycle))
                    pObj.put("customerUuid", customer.uuid)
                    pushFirebaseEditLog("EDIT_LOAN", pObj.toString(), previousJson, cycle.uuid)
                }
                
                val customerNameObj = customer?.name ?: "Customer"
                repository.addEditLog(
                    EditLog(
                        customerId = cycle.customerId,
                        customerName = customerNameObj,
                        actionType = "EDIT_LOAN",
                        actionDescription = "Marked loan of ₹${cycle.loanAmount} as Settled for $customerNameObj",
                        previousDataJson = previousJson
                    )
                )
                markDirty()
            }
        }
    }

    fun deleteLoanCycle(cycle: LoanCycle) {
        viewModelScope.launch {
            if (currentUserRole.value == "USER") return@launch
            try {
                val payments = allPayments.value.filter { it.loanCycleId == cycle.id }
                val packObj = org.json.JSONObject().apply {
                    put("loan", org.json.JSONObject(loanCycleToJson(cycle)))
                    val paymentsArr = org.json.JSONArray()
                    payments.forEach { p -> paymentsArr.put(org.json.JSONObject(weeklyPaymentToJson(p))) }
                    put("payments", paymentsArr)
                }

                val deletedLoan = cycle.copy(status = "DELETED")
                repository.updateLoanCycle(deletedLoan)
                payments.forEach { p ->
                    repository.insertWeeklyPayment(p.copy(status = "DELETED"))
                }
                if (!isSyncingFromFirebase) {
                    val pLoad = org.json.JSONObject().apply {
                        put("loanUuid", cycle.uuid)
                    }.toString()
                    pushFirebaseEditLog("DELETE_LOAN", pLoad, packObj.toString(), cycle.uuid)
                }

                val customerNameObj = repository.getCustomerById(cycle.customerId)?.name ?: "Customer"
                repository.addEditLog(
                    EditLog(
                        customerId = cycle.customerId,
                        customerName = customerNameObj,
                        actionType = "DELETE_LOAN",
                        actionDescription = "Deleted active loan cycle of ₹${cycle.loanAmount} for $customerNameObj (Interest: ₹${cycle.interestAmount}, Weekly Amount: ₹${cycle.weeklyAmount}, Notes: ${cycle.notes.ifBlank { "N/A" }})",
                        previousDataJson = packObj.toString()
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            markDirty()
        }
    }

    fun updateLoanCycle(
        loanCycleId: Int,
        amount: Double,
        interest: Double,
        weeklyInstalment: Double,
        tenureWeeks: Int,
        notes: String,
        startDate: Long? = null,
        deduction: Double = 0.0
    ) {
        viewModelScope.launch {
            if (currentUserRole.value == "USER") return@launch
            val existing = repository.getLoanCycleById(loanCycleId)
            if (existing != null) {
                val previousJson = loanCycleToJson(existing)
                val updatedLoan = existing.copy(
                    loanAmount = amount,
                    interestAmount = interest,
                    weeklyAmount = weeklyInstalment,
                    totalWeeks = tenureWeeks,
                    notes = notes,
                    startDate = startDate ?: existing.startDate,
                    deduction = deduction
                )
                val updatedJson = loanCycleToJson(updatedLoan)
                repository.updateLoanCycle(updatedLoan)
                val customer = repository.getCustomerById(existing.customerId)
                if (customer != null && !isSyncingFromFirebase) {
                    val pObj = org.json.JSONObject(updatedJson)
                    pObj.put("customerUuid", customer.uuid)
                    pushFirebaseEditLog("EDIT_LOAN", pObj.toString(), previousJson, existing.uuid)
                }
                val changeInfo = buildDetailedLoanChangeDesc(previousJson, updatedJson)
                val customerNameObj = customer?.name ?: "Customer"
                repository.addEditLog(
                    EditLog(
                        customerId = existing.customerId,
                        customerName = customerNameObj,
                        actionType = "EDIT_LOAN",
                        actionDescription = "Updated loan details of $customerNameObj: ${changeInfo.second}",
                        previousDataJson = previousJson
                    )
                )
                markDirty()
            }
        }
    }

    // Record Payment & Trigger SMS
    fun recordWeeklyPayment(
        context: Context,
        loanCycleId: Int,
        amount: Double,
        weekNum: Int,
        notes: String,
        paymentDate: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            if (currentUserRole.value == "USER") return@launch
            val actualWeekNum = if (amount <= 0.0 || notes == "UNPAID") 0 else weekNum
            val wp = WeeklyPayment(
                loanCycleId = loanCycleId,
                amountPaid = amount,
                weekNumber = actualWeekNum,
                paymentDate = paymentDate,
                notes = notes
            )
            val paymentId = repository.addWeeklyPayment(wp)

            // Trigger payment confirmation automatically
            val loan = repository.getLoanCycleById(loanCycleId)
            if (loan != null) {
                val customer = repository.getCustomerById(loan.customerId)
                if (customer != null) {
                    if (!isSyncingFromFirebase) {
                        val pObj = org.json.JSONObject(weeklyPaymentToJson(wp.copy(id = paymentId.toInt())))
                        pObj.put("loanUuid", loan.uuid)
                        pushFirebaseEditLog("RECORD_PAYMENT", pObj.toString(), "", wp.uuid)
                    }
                    repository.addEditLog(
                        EditLog(
                            customerId = customer.id,
                            customerName = customer.name,
                            actionType = "RECORD_PAYMENT",
                            actionDescription = "Recorded ₹$amount payment for week $weekNum of ${customer.name}",
                            previousDataJson = paymentId.toString()
                        )
                    )
                    if (customer.smsConfirmationOfEntry) {
                        triggerPaymentEntrySms(context, customer, loan, amount, weekNum)
                    }
                    createOrUpdateCustomerFiles(context, customer.id)
                    markDirty()
                }
            }
        }
    }

    fun editWeeklyPayment(
        context: Context,
        paymentId: Int,
        loanCycleId: Int,
        amount: Double,
        weekNum: Int,
        paymentDate: Long,
        notes: String
    ) {
        viewModelScope.launch {
            if (currentUserRole.value == "USER") return@launch
            val allP = allPayments.value
            val existingP = allP.find { it.id == paymentId }
            val previousJson = if (existingP != null) weeklyPaymentToJson(existingP) else ""

            val updatedP = WeeklyPayment(
                id = paymentId,
                loanCycleId = loanCycleId,
                amountPaid = amount,
                weekNumber = weekNum,
                paymentDate = paymentDate,
                notes = notes,
                uuid = existingP?.uuid ?: java.util.UUID.randomUUID().toString()
            )
            val updatedJson = weeklyPaymentToJson(updatedP)
            repository.updateWeeklyPayment(paymentId, loanCycleId, amount, weekNum, paymentDate, notes)
            val loan = repository.getLoanCycleById(loanCycleId)
            if (loan != null) {
                val customer = repository.getCustomerById(loan.customerId)
                if (customer != null) {
                    if (!isSyncingFromFirebase) {
                        val pObj = org.json.JSONObject(updatedJson)
                        pObj.put("loanUuid", loan.uuid)
                        pushFirebaseEditLog("EDIT_PAYMENT", pObj.toString(), previousJson, updatedP.uuid)
                    }
                    val changeInfo = buildDetailedPaymentChangeDesc(previousJson, updatedJson)
                    repository.addEditLog(
                        EditLog(
                            customerId = customer.id,
                            customerName = customer.name,
                            actionType = "EDIT_PAYMENT",
                            actionDescription = "Updated payment of week $weekNum (to ₹$amount) for ${customer.name}: ${changeInfo.second}",
                            previousDataJson = previousJson
                        )
                    )
                    if (customer.smsConfirmationOfEntry) {
                        triggerPaymentEntrySms(context, customer, loan, amount, weekNum)
                    }
                    markDirty()
                }
            }
        }
    }

    fun bulkUploadCustomers(
        collectionDay: String,
        csvText: String,
        onSuccess: (count: Int) -> Unit,
        onError: (msg: String) -> Unit
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            try {
                val parsedData = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val lines = csvText.lines().filter { it.isNotBlank() }
                    if (lines.isEmpty()) {
                        return@withContext null
                    }

                    val items = mutableListOf<ParsedUploadItem>()
                    val startIdx = if (lines[0].contains("name", ignoreCase = true) || lines[0].contains("phone", ignoreCase = true) || lines[0].contains("client", ignoreCase = true)) {
                        1
                    } else {
                        0
                    }

                    for (i in startIdx until lines.size) {
                        val line = lines[i]
                        val cols = com.example.domain.CsvParser.parseCsvLine(line)

                        if (cols.isEmpty()) continue

                        val name = cols[0]
                        if (name.isBlank()) continue

                        val phone = cols.getOrNull(1) ?: ""
                        val city = cols.getOrNull(2) ?: ""
                        val principal = cols.getOrNull(3)?.toDoubleOrNull() ?: 0.0
                        val interest = cols.getOrNull(4)?.toDoubleOrNull() ?: 0.0
                        val weekly = cols.getOrNull(5)?.toDoubleOrNull() ?: 0.0
                        val tenure = cols.getOrNull(6)?.toIntOrNull() ?: 10
                        val paymentReceived = cols.getOrNull(7)?.toDoubleOrNull() ?: 0.0
                        val notesStr = cols.getOrNull(8) ?: ""

                        items.add(
                            ParsedUploadItem(
                                name = name,
                                phone = phone,
                                city = city,
                                principal = principal,
                                interest = interest,
                                weekly = weekly,
                                tenure = tenure,
                                paymentReceived = paymentReceived,
                                notesStr = notesStr
                            )
                        )
                    }
                    items
                }

                if (parsedData == null) {
                    onError("No data found")
                    return@launch
                }

                val successCount = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    var success = 0
                    db.withTransaction {
                        val customers = allCustomers.value
                        var currentMaxOrder = customers.maxOfOrNull { it.customOrder } ?: 0

                        parsedData.forEach { item ->
                            currentMaxOrder++

                            val customerIdObj = repository.addCustomer(
                                Customer(
                                    name = item.name,
                                    phone = item.phone,
                                    customOrder = currentMaxOrder,
                                    collectionDay = collectionDay,
                                    city = item.city,
                                    smsWeeklyReminder = true,
                                    smsConfirmationOfEntry = true
                                )
                            )

                            if (item.principal > 0.0) {
                                val safeTenure = if (item.tenure > 0) item.tenure else 10
                                val totalAmt = item.principal + item.interest
                                val isPaid = item.paymentReceived >= totalAmt
                                val loanStatus = if (isPaid) "PAID" else "ACTIVE"

                                val loanId = repository.addLoanCycle(
                                    LoanCycle(
                                        customerId = customerIdObj.toInt(),
                                        loanAmount = item.principal,
                                        interestAmount = item.interest,
                                        weeklyAmount = if (item.weekly > 0.0) item.weekly else totalAmt / safeTenure,
                                        totalWeeks = safeTenure,
                                        status = loanStatus,
                                        notes = item.notesStr,
                                        paidAmount = item.paymentReceived
                                    )
                                )

                                if (item.paymentReceived > 0.0) {
                                    repository.addWeeklyPayment(
                                        WeeklyPayment(
                                            loanCycleId = loanId.toInt(),
                                            amountPaid = item.paymentReceived,
                                            paymentDate = System.currentTimeMillis(),
                                            weekNumber = 1,
                                            notes = "Bulk Import Sync Received"
                                        )
                                    )
                                }
                            }
                            success++
                        }
                    }
                    success
                }

                onSuccess(successCount)
            } catch (e: Exception) {
                e.printStackTrace()
                onError(e.message ?: "Processing failed")
            }
        }
    }

    fun deletePayment(paymentId: Int, loanCycleId: Int) {
        viewModelScope.launch {
            if (currentUserRole.value == "USER") return@launch
            val allP = allPayments.value
            val existingP = allP.find { it.id == paymentId }
            val previousJson = if (existingP != null) weeklyPaymentToJson(existingP) else ""

            val loan = repository.getLoanCycleById(loanCycleId)
            val customerNameObj = if (loan != null) {
                repository.getCustomerById(loan.customerId)?.name ?: "Customer"
            } else {
                "Customer"
            }
            val customerIdObj = loan?.customerId ?: 0

            repository.removeWeeklyPayment(paymentId, loanCycleId)
            val targetUuid = existingP?.uuid
            if (targetUuid != null && loan != null && !isSyncingFromFirebase) {
                val pLoad = org.json.JSONObject().apply {
                    put("paymentUuid", targetUuid)
                    put("loanUuid", loan.uuid)
                }.toString()
                pushFirebaseEditLog("DELETE_PAYMENT", pLoad, previousJson, targetUuid)
            }

            repository.addEditLog(
                EditLog(
                    customerId = customerIdObj,
                    customerName = customerNameObj,
                    actionType = "DELETE_PAYMENT",
                    actionDescription = "Deleted a payment of ₹${existingP?.amountPaid ?: 0.0} for $customerNameObj",
                    previousDataJson = previousJson
                )
            )
            markDirty()
        }
    }

    // Direct automatic dispatch helper or intent fallback
    fun sendSmsIntent(context: Context, phone: String, text: String) {
        com.example.service.SmsService.sendSmsIntent(
            context = context,
            phone = phone,
            text = text,
            smsPaused = smsPaused.value,
            simSelection = simSelection.value
        )
    }

    // Format and triggers
    fun triggerNewLoanSms(context: Context, customer: Customer, loan: LoanCycle) {
        com.example.service.SmsService.triggerNewLoanSms(
            context = context,
            customer = customer,
            loan = loan,
            template = getSmsNewLoanTemplate(customer.preferredLanguage),
            upiId = upiId.value,
            smsPaused = smsPaused.value,
            simSelection = simSelection.value
        )
    }

    fun triggerPaymentEntrySms(context: Context, customer: Customer, loan: LoanCycle, paymentAmount: Double, weekNum: Int) {
        com.example.service.SmsService.triggerPaymentEntrySms(
            context = context,
            customer = customer,
            loan = loan,
            paymentAmount = paymentAmount,
            weekNum = weekNum,
            template = getSmsPaymentTemplate(customer.preferredLanguage),
            upiId = upiId.value,
            smsPaused = smsPaused.value,
            simSelection = simSelection.value
        )
    }

    fun triggerManualReminderSms(context: Context, customer: Customer, loan: LoanCycle) {
        com.example.service.SmsService.triggerManualReminderSms(
            context = context,
            customer = customer,
            loan = loan,
            template = getSmsReminderTemplate(customer.preferredLanguage),
            upiId = upiId.value,
            smsPaused = smsPaused.value,
            simSelection = simSelection.value
        )
    }

    fun triggerWhatsappReminder(context: Context, customer: Customer, loan: LoanCycle) {
        com.example.service.SmsService.triggerWhatsappReminder(
            context = context,
            customer = customer,
            loan = loan,
            template = getWhatsappReminderTemplate(customer.preferredLanguage),
            upiId = upiId.value,
            qrImageUri = qrImageUri.value
        )
    }

    // Stats Calculations for Dashboard reporting (Filtered by selectedDay)
    val dashboardStats: StateFlow<DashboardStats> = combine(
        allCustomers,
        activeLoanCycles,
        allLoanCycles,
        allPayments,
        selectedDay
    ) { customers, activeLoans, allLoans, payments, day ->
        val isAllDays = day.equals("Home", ignoreCase = true)
        val groupCustomers = if (isAllDays) customers else customers.filter { it.collectionDay.equals(day, ignoreCase = true) }
        val groupCustomerIds = groupCustomers.map { it.id }.toSet()

        val groupActiveLoans = activeLoans.filter { it.customerId in groupCustomerIds }
        val groupAllLoans = allLoans.filter { it.customerId in groupCustomerIds }

        val activeLoansMapped = groupActiveLoans.map { loan ->
            val actualPaid = payments.filter { it.loanCycleId == loan.id && it.status.uppercase() != "DELETED" }.sumOf { it.amountPaid }
            loan.copy(paidAmount = actualPaid)
        }

        // Core dynamic outstanding interest receivable and principal calculations
        val totalPrincipalOut = activeLoansMapped.sumOf { it.loanAmount }
        val totalInterestOut = activeLoansMapped.sumOf { it.interestAmount }
        
        // Cascade accounting calculation engine to split Principal and Interest metrics cleanly
        var oPrincipal = 0.0
        var oInterest = 0.0
        activeLoansMapped.forEach { loan ->
            val paid = loan.paidAmount
            val principal = loan.loanAmount
            val interest = loan.interestAmount
            
            if (paid <= principal) {
                // Payment hasn't fully cleared the principal yet
                oPrincipal += (principal - paid)
                oInterest += interest
            } else {
                // Principal is fully cleared; the remainder decreases interest receivable
                oPrincipal += 0.0
                val remainderForInterest = paid - principal
                oInterest += maxOf(0.0, interest - remainderForInterest)
            }
        }

        val totalOutstanding = (totalPrincipalOut + totalInterestOut) - activeLoansMapped.sumOf { it.paidAmount }

        // Calculate midnight today
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = cal.timeInMillis

        // Today's collections for active day group
        val groupLoanCycleIds = groupAllLoans.map { it.id }.toSet()
        val groupTodayCollection = payments
            .filter { it.paymentDate >= startOfToday && it.loanCycleId in groupLoanCycleIds }
            .sumOf { it.amountPaid }

        // Today's collections globally (all groups)
        val globalTodayCollection = payments
            .filter { it.paymentDate >= startOfToday }
            .sumOf { it.amountPaid }

        // Today's interest/profit globally (all groups)
        val loanMap = allLoans.associateBy { it.id }
        val globalTodayDeductions = allLoans
            .filter { it.startDate >= startOfToday }
            .sumOf { it.deduction }
        val globalTodayInterest = payments
            .filter { it.paymentDate >= startOfToday }
            .sumOf { p ->
                val loan = loanMap[p.loanCycleId]
                if (loan != null) {
                    val total = loan.loanAmount + loan.interestAmount
                    val ratio = if (total > 0.0) loan.interestAmount / total else 0.0
                    p.amountPaid * ratio
                } else {
                    0.0
                }
            }

        // Today's interest/profit active day group
        val groupTodayDeductions = groupAllLoans
            .filter { it.startDate >= startOfToday }
            .sumOf { it.deduction }
        val groupTodayInterest = payments
            .filter { it.paymentDate >= startOfToday && it.loanCycleId in groupLoanCycleIds }
            .sumOf { p ->
                val loan = loanMap[p.loanCycleId]
                if (loan != null) {
                    val total = loan.loanAmount + loan.interestAmount
                    val ratio = if (total > 0.0) loan.interestAmount / total else 0.0
                    p.amountPaid * ratio
                } else {
                    0.0
                }
            }

        // Today's disbursed loans (loans created today)
        val globalTodayDisbursed = allLoans
            .filter { it.startDate >= startOfToday }
            .sumOf { it.loanAmount - it.deduction }

        val groupTodayDisbursed = groupAllLoans
            .filter { it.startDate >= startOfToday }
            .sumOf { it.loanAmount - it.deduction }

        val activeCount = groupActiveLoans.size
        val paidOffCount = groupAllLoans.count { it.status == "PAID" }

        DashboardStats(
            totalActiveLoansAmount = totalPrincipalOut + totalInterestOut,
            totalOutstandingDue = totalOutstanding,
            todaysCollectedAmount = globalTodayCollection,
            groupTodaysCollectedAmount = groupTodayCollection,
            todaysDisbursedAmount = globalTodayDisbursed,
            groupTodaysDisbursedAmount = groupTodayDisbursed,
            activeCyclesCount = activeCount,
            paidOffCyclesCount = paidOffCount,
            outstandingPrincipal = oPrincipal,
            outstandingInterest = oInterest,
            todaysInterestAmount = globalTodayInterest,
            groupTodaysInterestAmount = groupTodayInterest,
            todaysDeductionsAmount = globalTodayDeductions,
            groupTodaysDeductionsAmount = groupTodayDeductions
        )
    }
    .flowOn(kotlinx.coroutines.Dispatchers.IO)
    .stateIn(viewModelScope, SharingStarted.Eagerly, DashboardStats())

    // ----------------------------------------------------
    // App Data Backup & Restore (Export / Import Excel)
    // ----------------------------------------------------
    private fun findActivity(context: android.content.Context): android.app.Activity? {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) {
                return ctx
            }
            ctx = ctx.baseContext
        }
        return null
    }

    fun exportCsvGroupBackup(context: android.content.Context, groupName: String) {
        if (_isDemoMode.value) {
            android.widget.Toast.makeText(context, "Export is disabled in Offline Tester Mode.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch {
            _isExportImportLoading.value = true
            try {
                val customersList = db.collectionDao().getAllCustomersOnce()
                val loanCyclesList = db.collectionDao().getAllLoanCyclesOnce()
                val paymentsList = db.collectionDao().getAllPaymentsOnce()
                val cashBalanceLogsList = db.collectionDao().getAllCashBalanceLogsOnce()

                val csvContent = com.example.util.CsvBackupHelper.generateCsvString(
                    customers = customersList,
                    loanCycles = loanCyclesList,
                    payments = paymentsList,
                    dayFilter = groupName,
                    cashBalanceLogs = cashBalanceLogsList
                )

                val destinationFile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.cacheDir.mkdirs()
                    val sdf = java.text.SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", java.util.Locale.getDefault())
                    val formattedTime = sdf.format(java.util.Date())
                    val filePrefix = "finance_${groupName.replace(" ", "_")}_$formattedTime.csv"

                    val destFile = java.io.File(context.cacheDir, filePrefix)
                    destFile.writeText(csvContent, Charsets.UTF_8)
                    destFile
                }

                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    destinationFile
                )
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Weekly Collection Khata - $groupName - CSV Export")
                    clipData = android.content.ClipData.newRawUri("Backup File", uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = android.content.Intent.createChooser(intent, "Export $groupName CSV")
                chooser.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)

                val activity = findActivity(context)
                if (activity != null) {
                    activity.startActivity(chooser)
                } else {
                    context.startActivity(chooser)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val errMsg = e.localizedMessage ?: e.toString()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Export CSV failed: $errMsg", android.widget.Toast.LENGTH_LONG).show()
                }
            } finally {
                _isExportImportLoading.value = false
            }
        }
    }



    fun importCsvGroupBackup(
        context: android.content.Context,
        uriString: String,
        groupName: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (_isDemoMode.value) {
            onError("Import is disabled in Offline Tester Mode.")
            return
        }
        viewModelScope.launch {
            _isExportImportLoading.value = true
            try {
                val uri = android.net.Uri.parse(uriString)
                val contentResolver = context.contentResolver
                val inputStream = contentResolver.openInputStream(uri) ?: throw Exception("Could not open backup file")

                val bytes = inputStream.readBytes()
                val stringContent = String(bytes, java.nio.charset.StandardCharsets.UTF_8).trim()

                val success = com.example.util.CsvBackupHelper.importCsvIntoDay(
                    context = context,
                    csvText = stringContent,
                    dayGroup = groupName,
                    db = db
                )

                if (success) {
                    triggerDatabaseRescanAndRepair()
                    onSuccess()
                } else {
                    onError("Failed parsing CSV data. Please verify column structure matches instructions exactly.")
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                onError(e.message ?: "Unknown backup restoration error")
            } finally {
                _isExportImportLoading.value = false
            }
        }
    }

    fun clearAllLocalData(context: android.content.Context, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                _isExportImportLoading.value = true
                val db = com.example.data.AppDatabase.getDatabase(context)
                db.withTransaction {
                    db.collectionDao().deleteAllPayments()
                    db.collectionDao().deleteAllLoanCycles()
                    db.collectionDao().deleteAllCustomers()
                    db.collectionDao().deleteAllEditLogs()
                    db.collectionDao().deleteAllCashBalanceLogs()
                }
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val errMsg = e.localizedMessage ?: e.toString()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onError(errMsg)
                }
            } finally {
                _isExportImportLoading.value = false
            }
        }
    }

    fun exportLedgerReport(
        context: android.content.Context,
        filteredCustomers: List<Customer>,
        loanCycles: List<LoanCycle>,
        payments: List<WeeklyPayment>,
        dayFilter: String,
        statusFilter: String,
        isDateFilterEnabled: Boolean = false,
        fromDateStr: String = "",
        toDateStr: String = ""
    ) {
        viewModelScope.launch {
            try {
                _isExportImportLoading.value = true
                val csvBuilder = java.lang.StringBuilder()
                
                // Title & Filter Details metadata at the top of the spreadsheet
                csvBuilder.append("WEEKLY FINANCE KHATA LEDGER REPORT\n")
                val displayDayFilter = if (dayFilter.trim().equals("Home", ignoreCase = true)) "Dashboard" else dayFilter
                csvBuilder.append("Group Filter: ,$displayDayFilter\n")
                csvBuilder.append("Status Filter: ,$statusFilter\n")
                if (isDateFilterEnabled) {
                    csvBuilder.append("Date Range Filter: ,$fromDateStr to $toDateStr\n")
                }
                csvBuilder.append("Generated On: ,${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n\n")
                
                // Column headers
                csvBuilder.append("Sl No.,Client Name,Phone,City,Collection Day,Loan Amount (₹),Interest (₹),Total Target (₹),Paid in Period (₹),Overall Paid (₹),Balance Left (₹),Status,Loan Start Date\n")
                
                var slNo = 1
                for (customer in filteredCustomers) {
                    val matchingCycles = loanCycles.filter { it.customerId == customer.id }
                    if (matchingCycles.isEmpty()) {
                        // Just dump customer profile details
                        csvBuilder.append("$slNo,")
                        csvBuilder.append("\"${customer.name.replace("\"", "\"\"")}\",")
                        csvBuilder.append("\"${customer.phone}\",")
                        csvBuilder.append("\"${customer.city.replace("\"", "\"\"")}\",")
                        csvBuilder.append("${customer.collectionDay},")
                        csvBuilder.append("0.0,0.0,0.0,0.0,0.0,0.0,NO ACTIVE LOANS,N/A\n")
                        slNo++
                    } else {
                        for (cycle in matchingCycles) {
                            val startDateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(cycle.startDate))
                            val targetAmount = cycle.loanAmount + cycle.interestAmount
                            val balanceLeft = maxOf(0.0, targetAmount - cycle.paidAmount)
                            
                            val bounds = if (isDateFilterEnabled) {
                                try {
                                    val sdfParser = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                                    val fromDate = sdfParser.parse(fromDateStr)
                                    val toDate = sdfParser.parse(toDateStr)
                                    if (fromDate != null && toDate != null) {
                                        val fromCal = java.util.Calendar.getInstance().apply {
                                            time = fromDate
                                            set(java.util.Calendar.HOUR_OF_DAY, 0)
                                            set(java.util.Calendar.MINUTE, 0)
                                            set(java.util.Calendar.SECOND, 0)
                                            set(java.util.Calendar.MILLISECOND, 0)
                                        }
                                        val toCal = java.util.Calendar.getInstance().apply {
                                            time = toDate
                                            set(java.util.Calendar.HOUR_OF_DAY, 23)
                                            set(java.util.Calendar.MINUTE, 59)
                                            set(java.util.Calendar.SECOND, 59)
                                            set(java.util.Calendar.MILLISECOND, 999)
                                        }
                                        Pair(fromCal.timeInMillis, toCal.timeInMillis)
                                    } else null
                                } catch (e: Exception) {
                                    null
                                }
                            } else null

                            val paidInPeriod = if (bounds != null) {
                                payments.filter { it.loanCycleId == cycle.id && it.paymentDate >= bounds.first && it.paymentDate <= bounds.second }.sumOf { it.amountPaid }
                            } else {
                                cycle.paidAmount
                            }
                            
                            csvBuilder.append("$slNo,")
                            csvBuilder.append("\"${customer.name.replace("\"", "\"\"")}\",")
                            csvBuilder.append("\"${customer.phone}\",")
                            csvBuilder.append("\"${customer.city.replace("\"", "\"\"")}\",")
                            csvBuilder.append("${customer.collectionDay},")
                            csvBuilder.append("${cycle.loanAmount},")
                            csvBuilder.append("${cycle.interestAmount},")
                            csvBuilder.append("$targetAmount,")
                            csvBuilder.append("$paidInPeriod,")
                            csvBuilder.append("${cycle.paidAmount},")
                            csvBuilder.append("$balanceLeft,")
                            csvBuilder.append("${cycle.status},")
                            csvBuilder.append("$startDateStr\n")
                            slNo++
                        }
                    }
                }
                
                // Output CSV string to the shared cache
                val reportFile = java.io.File(context.cacheDir, "weekly_finance_ledger_report.csv")
                reportFile.writeText(csvBuilder.toString(), charset = kotlin.text.Charsets.UTF_8)
                
                // FileProvider share intent to enable seamless downloading / sharing / saving to drive or local folders
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    reportFile
                )
                
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "MD Finance Report")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                val chooser = android.content.Intent.createChooser(intent, "Download / Share CSV Ledger")
                chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
                
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(context, "Failed to generate report: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            } finally {
                _isExportImportLoading.value = false
            }
        }
    }

    suspend fun getFullBackupJson(collectionGroup: String? = null): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val allC = db.collectionDao().getAllCustomersOnce()
        val allL = db.collectionDao().getAllLoanCyclesOnce()
        val valP = db.collectionDao().getAllPaymentsOnce()
        val allA = db.collectionDao().getAllEditLogsOnce()
        val allCash = db.collectionDao().getAllCashBalanceLogsOnce()

        val isSelective = !collectionGroup.isNullOrBlank() && collectionGroup != "All Groups (Full Backup)"

        val customersList = if (isSelective) {
            allC.filter { it.collectionDay.trim().equals(collectionGroup!!.trim(), ignoreCase = true) }
        } else {
            allC
        }

        val customerIds = customersList.map { it.id }.toSet()

        val loanCyclesList = if (isSelective) {
            allL.filter { customerIds.contains(it.customerId) }
        } else {
            allL
        }

        val loanCycleIds = loanCyclesList.map { it.id }.toSet()

        val paymentsList = if (isSelective) {
            valP.filter { loanCycleIds.contains(it.loanCycleId) }
        } else {
            valP
        }

        val editList = if (isSelective) {
            allA.filter { customerIds.contains(it.customerId) }
        } else {
            allA
        }

        val backupObj = org.json.JSONObject()
        backupObj.put("deviceLastModified", System.currentTimeMillis())

        val customersArray = org.json.JSONArray()
        for (c in customersList) {
            val obj = org.json.JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("phone", c.phone)
                put("customOrder", c.customOrder)
                put("collectionDay", c.collectionDay)
                put("createdAt", c.createdAt)
                put("city", c.city)
                put("smsWeeklyReminder", c.smsWeeklyReminder)
                put("smsConfirmationOfEntry", c.smsConfirmationOfEntry)
                put("autoWeeklySms", c.autoWeeklySms)
                put("autoWeeklyWhatsapp", c.autoWeeklyWhatsapp)
                put("upiNameAlias", c.upiNameAlias)
                put("preferredLanguage", c.preferredLanguage)
                put("uuid", c.uuid)
                put("status", c.status)
            }
            customersArray.put(obj)
        }
        backupObj.put("customers", customersArray)

        val loansArray = org.json.JSONArray()
        for (l in loanCyclesList) {
            val parentCust = customersList.find { it.id == l.customerId }
            val obj = org.json.JSONObject().apply {
                put("id", l.id)
                put("customerId", l.customerId)
                put("customerUuid", parentCust?.uuid ?: "")
                put("loanAmount", l.loanAmount)
                put("interestAmount", l.interestAmount)
                put("weeklyAmount", l.weeklyAmount)
                put("totalWeeks", l.totalWeeks)
                put("startDate", l.startDate)
                put("status", l.status)
                put("notes", l.notes)
                put("paidAmount", l.paidAmount)
                put("uuid", l.uuid)
            }
            loansArray.put(obj)
        }
        backupObj.put("loanCycles", loansArray)

        val paymentsArray = org.json.JSONArray()
        for (p in paymentsList) {
            val obj = org.json.JSONObject().apply {
                put("id", p.id)
                put("loanCycleId", p.loanCycleId)
                put("amountPaid", p.amountPaid)
                put("paymentDate", p.paymentDate)
                put("weekNumber", p.weekNumber)
                put("notes", p.notes)
                put("upiTxnId", p.upiTxnId ?: "")
                put("uuid", p.uuid)
                put("status", p.status)
            }
            paymentsArray.put(obj)
        }
        backupObj.put("payments", paymentsArray)

        val editLogsArray = org.json.JSONArray()
        // DONT EXPORT EDIT LOG JUST CUSTOMER DATA ONLY:
        // Edit logs are completely excluded from exported backups as requested by the user
        backupObj.put("editLogs", editLogsArray)
        
        val cashBalanceLogsArray = org.json.JSONArray()
        if (!isSelective) {
            for (cashLog in allCash) {
                val obj = org.json.JSONObject().apply {
                    put("id", cashLog.id)
                    put("date", cashLog.date)
                    put("actualCash", cashLog.actualCash)
                    put("systemCash", cashLog.systemCash)
                    put("collectionAmount", cashLog.collectionAmount)
                    put("disbursalAmount", cashLog.disbursalAmount)
                    put("expenses", cashLog.expenses)
                }
                cashBalanceLogsArray.put(obj)
            }
        }
        backupObj.put("cashBalanceLogs", cashBalanceLogsArray)

        backupObj.put("backup_scope", collectionGroup ?: "All Groups (Full Backup)")

        val prefsObj = org.json.JSONObject().apply {
            put("username", username.value)
            put("sim_selection", simSelection.value)
            put("language", language.value)
            put("upi_id", upiId.value)
            put("upi_link", upiLink.value)
            put("qr_image_uri", qrImageUri.value)
            put("business_name", businessName.value)
            put("sms_paused", smsPaused.value)
            put("font_size_scale", fontSizeScale.value.toDouble())
            put("sms_new_loan_template", smsNewLoanTemplate.value)
            put("sms_payment_template", smsPaymentTemplate.value)
            put("sms_reminder_template", smsReminderTemplate.value)
            put("whatsapp_reminder_template", whatsappReminderTemplate.value)
            put("collection_groups_list", collectionGroups.value.joinToString(","))
            put("soundbox_enabled", true)
            put("soundbox_language", "English")
            put("sms_reader_paused", smsReaderPaused.value)
            put("auto_entry_passing", autoEntryPassing.value)
            put("upi_link_sharing", upiLinkSharing.value)
            // Google Drive settings excluded
        }
        backupObj.put("preferences", prefsObj)

        // Calculate expected outstanding and entity counts for verification check
        val backupTotalOutstanding = loanCyclesList.filter { it.status.uppercase() == "ACTIVE" }.sumOf { (it.loanAmount + it.interestAmount) - it.paidAmount }
        backupObj.put("meta_total_outstanding", backupTotalOutstanding)
        backupObj.put("meta_customers_count", customersList.size)
        backupObj.put("meta_loans_count", loanCyclesList.size)
        backupObj.put("meta_payments_count", paymentsList.size)

        backupObj.toString(4)
    }

    fun applyManualJsonEdit(jsonStr: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val obj = org.json.JSONObject(jsonStr)
                if (!obj.has("customers")) {
                    throw Exception("Invalid format: Missing 'customers' collection.")
                }
                
                isRestoringFromJson = true
                restoreFullBackupFromJson(jsonStr)

                _liveCloudJson.value = jsonStr
                runMasterComparisonCheck(jsonStr, "Manual JSON Direct Edit")

                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    onError(e.message ?: "Unknown error while processing manual backup application.")
                }
            } finally {
                isRestoringFromJson = false
            }
        }
    }

    suspend fun restoreFullBackupFromJson(jsonString: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        isRestoringFromJson = true
        try {
            val backupObj = org.json.JSONObject(jsonString)
            performCleanDbRestoreAndVerify(backupObj)
        } finally {
            isRestoringFromJson = false
        }
    }

    private suspend fun performCleanDbRestoreAndVerify(backupObj: org.json.JSONObject) {
        val customersArray = backupObj.optJSONArray("customers") ?: org.json.JSONArray()
        val loansArray = backupObj.optJSONArray("loanCycles") ?: org.json.JSONArray()
        val paymentsArray = backupObj.optJSONArray("payments") ?: org.json.JSONArray()

        // 1. Check & dynamically add missing groups/collection days
        val currentGroups = _collectionGroups.value.toMutableList()
        var updatedGroups = false
        
        for (i in 0 until customersArray.length()) {
            val cObj = customersArray.getJSONObject(i)
            val dayRaw = cObj.optString("collectionDay", "Monday").trim()
            if (dayRaw.isNotBlank()) {
                val day = when (dayRaw.lowercase(java.util.Locale.getDefault())) {
                    "tueasday", "tuesday" -> "Tuesday"
                    "sunday morning", "sunday mrg", "mrg", "sunday morning (sunday mrg)", "sunday morning (mrg)" -> "Sunday mrg"
                    "sunday evening", "sunday eve", "eve", "sunday evening (sunday eve)", "sunday evening (eve)" -> "Sunday eve"
                    else -> dayRaw.split(" ").joinToString(" ") { word ->
                        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                    }
                }
                val exists = currentGroups.any { it.trim().equals(day, ignoreCase = true) }
                if (!exists) {
                    currentGroups.add(day)
                    updatedGroups = true
                }
            }
        }
        
        if (updatedGroups) {
            val daysString = currentGroups.distinct().joinToString(",")
            _collectionGroups.value = currentGroups.distinct()
            prefs.edit().putString("collection_groups_list", daysString).apply()
        }

        // 2. Perform clean database wipe (ONLY wipe groups that are actually present in the incoming file to prevent deletion in other lists)
        val incomingGroups = mutableSetOf<String>()
        for (i in 0 until customersArray.length()) {
            val cObj = customersArray.getJSONObject(i)
            val dayRaw = cObj.optString("collectionDay", "").trim()
            if (dayRaw.isNotBlank()) {
                val normalizedDay = when (dayRaw.lowercase(java.util.Locale.getDefault())) {
                    "tueasday", "tuesday" -> "Tuesday"
                    "sunday morning", "sunday mrg", "mrg", "sunday morning (sunday mrg)", "sunday morning (mrg)" -> "Sunday mrg"
                    "sunday evening", "sunday eve", "eve", "sunday evening (sunday eve)", "sunday evening (eve)" -> "Sunday eve"
                    else -> dayRaw.split(" ").joinToString(" ") { word ->
                        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                    }
                }
                incomingGroups.add(normalizedDay)
            }
        }

        db.withTransaction {
            if (incomingGroups.isNotEmpty()) {
                val allC = db.collectionDao().getAllCustomersOnce()
                val targetCustomers = allC.filter { incomingGroups.contains(it.collectionDay) }
                val targetCustomerIds = targetCustomers.map { it.id }.toSet()
                
                val allL = db.collectionDao().getAllLoanCyclesOnce()
                val targetLoans = allL.filter { targetCustomerIds.contains(it.customerId) }
                val targetLoanIds = targetLoans.map { it.id }.toSet()
                
                val allP = db.collectionDao().getAllPaymentsOnce()
                val targetPayments = allP.filter { targetLoanIds.contains(it.loanCycleId) }
                
                for (p in targetPayments) {
                    db.collectionDao().deletePayment(p)
                }
                for (l in targetLoans) {
                    db.collectionDao().deleteLoanCycle(l)
                }
                for (c in targetCustomers) {
                    db.collectionDao().deleteCustomer(c)
                }
            } else {
                db.collectionDao().deleteAllPayments()
                db.collectionDao().deleteAllLoanCycles()
                db.collectionDao().deleteAllCustomers()
                db.collectionDao().deleteAllEditLogs()
                db.collectionDao().deleteAllCashBalanceLogs()
            }

            // Map to store oldCustomerId -> newCustomerId to prevent primary key clashing between daily lists during import
            val customerIdMap = mutableMapOf<Int, Int>()

            // Restore Customers (auto-generate new IDs to prevent overwriting existing lists)
            for (i in 0 until customersArray.length()) {
                val cObj = customersArray.getJSONObject(i)
                val customerUuid = cObj.optString("uuid", "")
                val oldId = cObj.getInt("id")
                val dayRaw = cObj.optString("collectionDay", "Monday").trim()
                val normalizedDay = when (dayRaw.lowercase(java.util.Locale.getDefault())) {
                    "tueasday", "tuesday" -> "Tuesday"
                    "sunday morning", "sunday mrg", "mrg", "sunday morning (sunday mrg)", "sunday morning (mrg)" -> "Sunday mrg"
                    "sunday evening", "sunday eve", "eve", "sunday evening (sunday eve)", "sunday evening (eve)" -> "Sunday eve"
                    else -> dayRaw.split(" ").joinToString(" ") { word ->
                        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                    }
                }
                val newCustomer = Customer(
                    id = 0, // Let SQLite/Room auto-generate clean unique ID to avoid clashing
                    name = cObj.getString("name"),
                    phone = cObj.getString("phone"),
                    customOrder = cObj.optInt("customOrder", 0),
                    collectionDay = normalizedDay,
                    createdAt = cObj.optLong("createdAt", System.currentTimeMillis()),
                    city = cObj.optString("city", ""),
                    smsWeeklyReminder = cObj.optBoolean("smsWeeklyReminder", true),
                    smsConfirmationOfEntry = cObj.optBoolean("smsConfirmationOfEntry", true),
                    autoWeeklySms = cObj.optBoolean("autoWeeklySms", false),
                    autoWeeklyWhatsapp = cObj.optBoolean("autoWeeklyWhatsapp", false),
                    upiNameAlias = cObj.optString("upiNameAlias", ""),
                    preferredLanguage = cObj.optString("preferredLanguage", "English"),
                    status = cObj.optString("status", "ACTIVE"),
                    uuid = if (customerUuid.isNotEmpty()) customerUuid else java.util.UUID.randomUUID().toString()
                )
                val newId = db.collectionDao().insertCustomer(newCustomer).toInt()
                customerIdMap[oldId] = newId
            }

            // Map to store oldLoanId -> newLoanId to update foreign keys correctly
            val loanIdMap = mutableMapOf<Int, Int>()

            // Restore Loan Cycles (auto-generate new IDs to prevent overwriting existing lists)
            for (j in 0 until loansArray.length()) {
                val lObj = loansArray.getJSONObject(j)
                val loanUuid = lObj.optString("uuid", "")
                val oldLoanId = lObj.getInt("id")
                val oldCustomerId = lObj.getInt("customerId")
                val newCustomerId = customerIdMap[oldCustomerId] ?: oldCustomerId
                val newLoan = LoanCycle(
                    id = 0, // Let SQLite/Room auto-generate clean unique ID to avoid clashing
                    customerId = newCustomerId,
                    loanAmount = lObj.getDouble("loanAmount"),
                    interestAmount = lObj.optDouble("interestAmount", 0.0),
                    weeklyAmount = lObj.getDouble("weeklyAmount"),
                    totalWeeks = lObj.optInt("totalWeeks", 10),
                    startDate = lObj.optLong("startDate", System.currentTimeMillis()),
                    status = lObj.optString("status", "ACTIVE"),
                    notes = lObj.optString("notes", ""),
                    paidAmount = lObj.optDouble("paidAmount", 0.0),
                    uuid = if (loanUuid.isNotEmpty()) loanUuid else java.util.UUID.randomUUID().toString()
                )
                val newLoanId = db.collectionDao().insertLoanCycle(newLoan).toInt()
                loanIdMap[oldLoanId] = newLoanId
            }

            // Restore Payments (auto-generate new IDs to prevent overwriting existing lists)
            for (k in 0 until paymentsArray.length()) {
                val pObj = paymentsArray.getJSONObject(k)
                val paymentUuid = pObj.optString("uuid", "")
                val oldLoanId = pObj.getInt("loanCycleId")
                val newLoanId = loanIdMap[oldLoanId] ?: oldLoanId
                val newPayment = WeeklyPayment(
                    id = 0, // Let SQLite/Room auto-generate clean unique ID to avoid clashing
                    loanCycleId = newLoanId,
                    amountPaid = pObj.getDouble("amountPaid"),
                    paymentDate = pObj.optLong("paymentDate", System.currentTimeMillis()),
                    weekNumber = pObj.getInt("weekNumber"),
                    notes = pObj.optString("notes", ""),
                    upiTxnId = if (pObj.has("upiTxnId") && !pObj.isNull("upiTxnId")) pObj.getString("upiTxnId") else null,
                    uuid = if (paymentUuid.isNotEmpty()) paymentUuid else java.util.UUID.randomUUID().toString(),
                    status = pObj.optString("status", "ACTIVE")
                )
                db.collectionDao().insertPayment(newPayment)
            }

            // Final verification step to recalculate paidAmount and status for ALL loan cycles in database to prevent double counting
            val finalLoans = db.collectionDao().getAllLoanCyclesOnce()
            val finalPayments = db.collectionDao().getAllPaymentsOnce()
            
            for (loan in finalLoans) {
                val sumPaid = finalPayments.filter { it.loanCycleId == loan.id && it.status.uppercase() != "DELETED" }.sumOf { it.amountPaid }
                val targetAmount = loan.loanAmount + loan.interestAmount
                val shouldBePaid = sumPaid >= targetAmount
                val computedStatus = if (shouldBePaid) "PAID" else "ACTIVE"
                
                if (loan.paidAmount != sumPaid || loan.status != computedStatus) {
                    db.collectionDao().updateLoanCycle(
                        loan.copy(
                            paidAmount = sumPaid,
                            status = computedStatus
                        )
                    )
                }
            }

            // --- INTEGRITY & CROSS-VERIFICATION ENGINE ---
            val metaOutstanding = backupObj.optDouble("meta_total_outstanding", -1.0)
            val metaCustCount = backupObj.optInt("meta_customers_count", -1)
            
            val finalCustomers = db.collectionDao().getAllCustomersOnce()
            val finalLoansVerify = db.collectionDao().getAllLoanCyclesOnce()

            val scope = backupObj.optString("backup_scope", "All Groups (Full Backup)")
            val isSelectiveRestore = !scope.isNullOrBlank() && scope != "All Groups (Full Backup)"

            val verifyCustomers = if (isSelectiveRestore) {
                finalCustomers.filter { it.collectionDay.trim().equals(scope.trim(), ignoreCase = true) }
            } else {
                finalCustomers
            }
            val verifyCustIds = verifyCustomers.map { it.id }.toSet()
            val verifyLoans = if (isSelectiveRestore) {
                finalLoansVerify.filter { verifyCustIds.contains(it.customerId) }
            } else {
                finalLoansVerify
            }
            
            val localOutstanding = verifyLoans.filter { it.status.uppercase() == "ACTIVE" }.sumOf { (it.loanAmount + it.interestAmount) - it.paidAmount }
            val localCustCount = verifyCustomers.size
            
            val outstandingMatches = if (metaOutstanding >= 0.0) {
                Math.abs(localOutstanding - metaOutstanding) < 1.0
            } else {
                true
            }
            
            val filesMatches = if (metaCustCount >= 0) {
                localCustCount == metaCustCount
            } else {
                true
            }
            
            _syncFilesCount.value = localCustCount
            _syncAddedCount.value = customersArray.length()
            _syncOutstandingVerified.value = outstandingMatches
            
            if (outstandingMatches && filesMatches) {
                _syncStatsText.value = "Tally Success! Outstanding (Verified): ₹$localOutstanding, Files Count: $localCustCount."
            } else {
                _syncStatsText.value = "Tally Warning! Local: ₹$localOutstanding ($localCustCount files) vs Expected: ₹$metaOutstanding ($metaCustCount files). Running Healing..."
                triggerDatabaseRescanAndRepair()
            }

            // Add sync timings device-specific Auditing
            val senderDev = backupObj.optJSONObject("preferences")?.optString("username", "Remote Device") ?: "Remote Device"
            
            db.collectionDao().insertEditLog(
                com.example.data.EditLog(
                    id = 0,
                    timestamp = System.currentTimeMillis(),
                    customerId = 0,
                    customerName = "System",
                    actionType = "SYNC_VERIFY",
                    actionDescription = "Sync Verification completed on info from '$senderDev'. Local outstanding total: ₹$localOutstanding (Target verification total: ₹$metaOutstanding). Integrity check: ${if (outstandingMatches) "CORRECT" else "MISMATCH WARNING - RUNNING SELF-HEAL"}.",
                    previousDataJson = "",
                    uuid = java.util.UUID.randomUUID().toString()
                )
            )
            // Restore Cash Balance Logs if full restore and not partial
            if (incomingGroups.isEmpty()) {
                val cashBalanceLogsArray = backupObj.optJSONArray("cashBalanceLogs") ?: org.json.JSONArray()
                for (i in 0 until cashBalanceLogsArray.length()) {
                    val cObj = cashBalanceLogsArray.getJSONObject(i)
                    val newCashLog = com.example.data.CashBalanceLog(
                        id = 0,
                        date = cObj.optLong("date", System.currentTimeMillis()),
                        actualCash = cObj.optDouble("actualCash", 0.0),
                        systemCash = cObj.optDouble("systemCash", 0.0),
                        collectionAmount = cObj.optDouble("collectionAmount", 0.0),
                        disbursalAmount = cObj.optDouble("disbursalAmount", 0.0),
                        expenses = cObj.optDouble("expenses", 0.0)
                    )
                    db.collectionDao().insertCashBalanceLog(newCashLog)
                }
            }
        }

        // 3. Preferences deserialization - non-destructive update to global properties
        val prefsObj = backupObj.optJSONObject("preferences")
        if (prefsObj != null) {
            val edit = prefs.edit()
            
            if (prefsObj.has("sim_selection")) {
                val sim = prefsObj.getString("sim_selection")
                edit.putString("sim_selection", sim)
                _simSelection.value = sim
            }
            if (prefsObj.has("language")) {
                val lang = prefsObj.getString("language")
                edit.putString("language", lang)
                _language.value = lang
            }
            if (prefsObj.has("upi_id")) {
                val upi = prefsObj.getString("upi_id")
                edit.putString("upi_id", upi)
                _upiId.value = upi
            }
            if (prefsObj.has("upi_link")) {
                val link = prefsObj.getString("upi_link")
                edit.putString("upi_link", link)
                _upiLink.value = link
            }
            if (prefsObj.has("qr_image_uri")) {
                val qr = prefsObj.getString("qr_image_uri")
                edit.putString("qr_image_uri", qr)
                _qrImageUri.value = qr
            }
            if (prefsObj.has("business_name")) {
                val name = prefsObj.getString("business_name")
                edit.putString("business_name", name)
                _businessName.value = name
            }
            if (prefsObj.has("sms_paused")) {
                val paused = prefsObj.getBoolean("sms_paused")
                edit.putBoolean("sms_paused", paused)
                _smsPaused.value = paused
            }
            if (prefsObj.has("font_size_scale")) {
                val scale = prefsObj.getDouble("font_size_scale").toFloat()
                edit.putFloat("font_size_scale", scale)
                _fontSizeScale.value = scale
            }
            if (prefsObj.has("sms_new_loan_template")) {
                val t = prefsObj.getString("sms_new_loan_template")
                if (t.isNotBlank()) {
                    edit.putString("sms_new_loan_template", t)
                    _smsNewLoanTemplate.value = t
                }
            }
            if (prefsObj.has("sms_payment_template")) {
                val t = prefsObj.getString("sms_payment_template")
                if (t.isNotBlank()) {
                    edit.putString("sms_payment_template", t)
                    _smsPaymentTemplate.value = t
                }
            }
            if (prefsObj.has("sms_reminder_template")) {
                val t = prefsObj.getString("sms_reminder_template")
                if (t.isNotBlank()) {
                    edit.putString("sms_reminder_template", t)
                    _smsReminderTemplate.value = t
                }
            }
            if (prefsObj.has("whatsapp_reminder_template")) {
                val t = prefsObj.getString("whatsapp_reminder_template")
                if (t.isNotBlank()) {
                    edit.putString("whatsapp_reminder_template", t)
                    _whatsappReminderTemplate.value = t
                }
            }

            if (prefsObj.has("soundbox_enabled")) {
                val enabled = prefsObj.getBoolean("soundbox_enabled")
                edit.putBoolean("soundbox_enabled", enabled)
            }
            if (prefsObj.has("soundbox_language")) {
                val soundboxLang = prefsObj.getString("soundbox_language")
                edit.putString("soundbox_language", soundboxLang)
            }
            if (prefsObj.has("sms_reader_paused")) {
                val paused = prefsObj.getBoolean("sms_reader_paused")
                edit.putBoolean("sms_reader_paused", paused)
                _smsReaderPaused.value = paused
            }
            if (prefsObj.has("auto_entry_passing")) {
                val enabled = prefsObj.getBoolean("auto_entry_passing")
                edit.putBoolean("auto_entry_passing", enabled)
                _autoEntryPassing.value = enabled
            }
            if (prefsObj.has("upi_link_sharing")) {
                val enabled = prefsObj.getBoolean("upi_link_sharing")
                edit.putBoolean("upi_link_sharing", enabled)
                _upiLinkSharing.value = enabled
            }

            // For collection groups list, we merge dynamically
            val daysString = currentGroups.distinct().joinToString(",")
            edit.putString("collection_groups_list", daysString)
            
            edit.apply()
        }
    }

    private suspend fun oldBypassTryDummy(backupObj: org.json.JSONObject, customersArray: org.json.JSONArray, loansArray: org.json.JSONArray, paymentsArray: org.json.JSONArray, currentGroups: List<String>) {
        try {

            // Run robust UUID self-heal and populator before querying
            try {
                repository.populateMissingUuids()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Load existing entities database-wide via efficient one-shot queries to support deduplicated merging
            val existingCustomers = db.collectionDao().getAllCustomersOnce().toMutableList()
            val existingLoans = db.collectionDao().getAllLoanCyclesOnce().toMutableList()
            val existingPayments = db.collectionDao().getAllPaymentsOnce().toMutableList()
            val existingAuditLogs = db.collectionDao().getAllEditLogsOnce().toMutableList()

            // 2. Perform incremental inserts/merges keeping connections intact
            db.withTransaction {
                // Collect deleted UUIDs from incoming edit logs to execute database deletion sync
                val deletedCustomerUuids = HashSet<String>()
                val deletedLoanUuids = HashSet<String>()
                val deletedPaymentUuids = HashSet<String>()

                val auditLogsArrayForDeletes = backupObj.optJSONArray("editLogs") ?: backupObj.optJSONArray("edit_logs") ?: backupObj.optJSONArray("auditLogs") ?: backupObj.optJSONArray("audit_logs") ?: org.json.JSONArray()
                for (idx in 0 until auditLogsArrayForDeletes.length()) {
                    val logObj = auditLogsArrayForDeletes.getJSONObject(idx)
                    val actType = logObj.optString("actionType", "")
                    val previousJsonStr = logObj.optString("previousDataJson", "")
                    if (previousJsonStr.isNotBlank()) {
                        try {
                            when (actType) {
                                "DELETE_CUSTOMER" -> {
                                    val obj = org.json.JSONObject(previousJsonStr)
                                    val cObj = obj.optJSONObject("customer")
                                    if (cObj != null) {
                                        val u = cObj.optString("uuid", "")
                                        if (u.isNotEmpty()) deletedCustomerUuids.add(u)
                                    }
                                    val loansArr = obj.optJSONArray("loans")
                                    if (loansArr != null) {
                                        for (j in 0 until loansArr.length()) {
                                            val lu = loansArr.getJSONObject(j).optString("uuid", "")
                                            if (lu.isNotEmpty()) deletedLoanUuids.add(lu)
                                        }
                                    }
                                    val paymentsArr = obj.optJSONArray("payments")
                                    if (paymentsArr != null) {
                                        for (j in 0 until paymentsArr.length()) {
                                            val pu = paymentsArr.getJSONObject(j).optString("uuid", "")
                                            if (pu.isNotEmpty()) deletedPaymentUuids.add(pu)
                                        }
                                    }
                                }
                                "DELETE_LOAN" -> {
                                    val obj = org.json.JSONObject(previousJsonStr)
                                    val lObj = obj.optJSONObject("loan")
                                    if (lObj != null) {
                                        val u = lObj.optString("uuid", "")
                                        if (u.isNotEmpty()) deletedLoanUuids.add(u)
                                    }
                                    val paymentsArr = obj.optJSONArray("payments")
                                    if (paymentsArr != null) {
                                        for (j in 0 until paymentsArr.length()) {
                                            val pu = paymentsArr.getJSONObject(j).optString("uuid", "")
                                            if (pu.isNotEmpty()) deletedPaymentUuids.add(pu)
                                        }
                                    }
                                }
                                "DELETE_PAYMENT" -> {
                                    val obj = org.json.JSONObject(previousJsonStr)
                                    val u = if (obj.has("uuid")) {
                                        obj.optString("uuid", "")
                                    } else {
                                        obj.optJSONObject("payment")?.optString("uuid", "") ?: ""
                                    }
                                    if (u.isNotEmpty()) deletedPaymentUuids.add(u)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                // Delete customer from Room and remove from existingCustomers
                val customersToDelete = existingCustomers.filter { it.uuid.isNotEmpty() && deletedCustomerUuids.contains(it.uuid) }
                for (c in customersToDelete) {
                    db.collectionDao().deleteCustomer(c)
                    existingCustomers.remove(c)
                }

                // Delete loans from Room and remove from existingLoans
                val loansToDelete = existingLoans.filter { it.uuid.isNotEmpty() && deletedLoanUuids.contains(it.uuid) }
                for (l in loansToDelete) {
                    db.collectionDao().deleteLoanCycle(l)
                    existingLoans.remove(l)
                }

                // Delete payments from Room and remove from existingPayments
                val paymentsToDelete = existingPayments.filter { it.uuid.isNotEmpty() && deletedPaymentUuids.contains(it.uuid) }
                for (p in paymentsToDelete) {
                    db.collectionDao().deletePayment(p)
                    existingPayments.remove(p)
                }

                val customerIdMap = HashMap<Int, Int>()
                val loanIdMap = java.util.HashMap<Int, Int>()

                val processedCustomerUuids = HashSet<String>()
                val processedLoanUuids = HashSet<String>()
                val processedPaymentUuids = HashSet<String>()
                val processedPaymentSignatures = HashSet<String>()
                val processedAuditLogUuids = HashSet<String>()
                val processedAuditLogSignatures = HashSet<String>()

                for (i in 0 until customersArray.length()) {
                    val cObj = customersArray.getJSONObject(i)
                    val customerUuid = cObj.optString("uuid", "")
                    if (customerUuid.isNotEmpty() && deletedCustomerUuids.contains(customerUuid)) {
                        continue // Skip deleted customers
                    }
                    val oldCustomerId = cObj.getInt("id")
                    if (customerUuid.isNotEmpty()) {
                        if (processedCustomerUuids.contains(customerUuid)) {
                            val existing = existingCustomers.find { it.uuid == customerUuid } ?: existingCustomers.find { it.name.trim().equals(cObj.getString("name").trim(), ignoreCase = true) }
                            if (existing != null) {
                                customerIdMap[oldCustomerId] = existing.id
                            }
                            continue // Skip duplicate customer record in JSON
                        }
                        processedCustomerUuids.add(customerUuid)
                    }

                    val dayRaw = cObj.optString("collectionDay", "Monday").trim()
                    val normalizedDay = when (dayRaw.lowercase(java.util.Locale.getDefault())) {
                        "tueasday", "tuesday" -> "Tuesday"
                        "sunday morning", "sunday mrg", "mrg", "sunday morning (sunday mrg)", "sunday morning (mrg)" -> "Sunday mrg"
                        "sunday evening", "sunday eve", "eve", "sunday evening (sunday eve)", "sunday evening (eve)" -> "Sunday eve"
                        else -> dayRaw.split(" ").joinToString(" ") { word ->
                            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                        }
                    }

                    val existingCustomer = if (customerUuid.isNotEmpty()) {
                        existingCustomers.find { it.uuid == customerUuid } ?: existingCustomers.find { it.name.trim().equals(cObj.getString("name").trim(), ignoreCase = true) }
                    } else {
                        existingCustomers.find { it.name.trim().equals(cObj.getString("name").trim(), ignoreCase = true) }
                    }

                    val newCustomerId: Int
                    if (existingCustomer != null) {
                        newCustomerId = existingCustomer.id
                        val updatedCustomer = existingCustomer.copy(
                            name = cObj.getString("name"),
                            phone = cObj.optString("phone", existingCustomer.phone),
                            customOrder = cObj.optInt("customOrder", existingCustomer.customOrder),
                            collectionDay = normalizedDay,
                            city = cObj.optString("city", existingCustomer.city),
                            smsWeeklyReminder = cObj.optBoolean("smsWeeklyReminder", existingCustomer.smsWeeklyReminder),
                            smsConfirmationOfEntry = cObj.optBoolean("smsConfirmationOfEntry", existingCustomer.smsConfirmationOfEntry),
                            autoWeeklySms = cObj.optBoolean("autoWeeklySms", existingCustomer.autoWeeklySms),
                            autoWeeklyWhatsapp = cObj.optBoolean("autoWeeklyWhatsapp", existingCustomer.autoWeeklyWhatsapp),
                            syncedLastSavedAt = cObj.optLong("syncedLastSavedAt", existingCustomer.syncedLastSavedAt),
                            lastModified = cObj.optLong("lastModified", existingCustomer.lastModified),
                            status = cObj.optString("status", existingCustomer.status),
                            uuid = if (customerUuid.isNotEmpty()) customerUuid else existingCustomer.uuid
                        )
                        db.collectionDao().updateCustomer(updatedCustomer)
                        val idx = existingCustomers.indexOf(existingCustomer)
                        if (idx != -1) {
                            existingCustomers[idx] = updatedCustomer
                        }
                    } else {
                        val newCustomer = Customer(
                            id = 0, // let Room autogenerate SQL ID
                            name = cObj.getString("name"),
                            phone = cObj.optString("phone", ""),
                            customOrder = cObj.optInt("customOrder", 0),
                            collectionDay = normalizedDay,
                            createdAt = cObj.optLong("createdAt", System.currentTimeMillis()),
                            city = cObj.optString("city", ""),
                            smsWeeklyReminder = cObj.optBoolean("smsWeeklyReminder", true),
                            smsConfirmationOfEntry = cObj.optBoolean("smsConfirmationOfEntry", true),
                            autoWeeklySms = cObj.optBoolean("autoWeeklySms", false),
                            autoWeeklyWhatsapp = cObj.optBoolean("autoWeeklyWhatsapp", false),
                            uuid = if (customerUuid.isNotEmpty()) customerUuid else java.util.UUID.randomUUID().toString(),
                            syncedLastSavedAt = cObj.optLong("syncedLastSavedAt", 0L),
                            lastModified = cObj.optLong("lastModified", System.currentTimeMillis()),
                            status = cObj.optString("status", "ACTIVE")
                        )
                        newCustomerId = db.collectionDao().insertCustomer(newCustomer).toInt()
                        existingCustomers.add(newCustomer.copy(id = newCustomerId))
                    }
                    customerIdMap[oldCustomerId] = newCustomerId
                }

                for (j in 0 until loansArray.length()) {
                    val lObj = loansArray.getJSONObject(j)
                    val loanUuid = lObj.optString("uuid", "")
                    if (loanUuid.isNotEmpty() && deletedLoanUuids.contains(loanUuid)) {
                        continue // Skip deleted loans
                    }
                    val oldCustomerId = lObj.getInt("customerId")
                    val newCustomerId = customerIdMap[oldCustomerId] ?: continue
                    val oldLoanId = lObj.getInt("id")

                    if (loanUuid.isNotEmpty()) {
                        if (processedLoanUuids.contains(loanUuid)) {
                            val existing = existingLoans.find { it.uuid == loanUuid } ?: existingLoans.find { it.customerId == newCustomerId && it.startDate == lObj.optLong("startDate", 0L) }
                            if (existing != null) {
                                loanIdMap[oldLoanId] = existing.id
                            }
                            continue // Skip duplicate loan in JSON
                        }
                        processedLoanUuids.add(loanUuid)
                    }

                    val existingLoan = if (loanUuid.isNotEmpty()) {
                        existingLoans.find { it.uuid == loanUuid } ?: existingLoans.find { it.customerId == newCustomerId && it.startDate == lObj.optLong("startDate", 0L) }
                    } else {
                        existingLoans.find { it.customerId == newCustomerId && it.startDate == lObj.optLong("startDate", 0L) }
                    }

                    val newLoanId: Int
                    if (existingLoan != null) {
                        newLoanId = existingLoan.id
                        val updatedLoan = existingLoan.copy(
                            customerId = newCustomerId,
                            loanAmount = lObj.getDouble("loanAmount"),
                            interestAmount = lObj.optDouble("interestAmount", existingLoan.interestAmount),
                            weeklyAmount = lObj.getDouble("weeklyAmount"),
                            totalWeeks = lObj.optInt("totalWeeks", existingLoan.totalWeeks),
                            startDate = lObj.optLong("startDate", existingLoan.startDate),
                            status = lObj.optString("status", existingLoan.status),
                            notes = lObj.optString("notes", existingLoan.notes),
                            uuid = if (loanUuid.isNotEmpty()) loanUuid else existingLoan.uuid
                        )
                        repository.updateLoanCycle(updatedLoan)
                        val idx = existingLoans.indexOf(existingLoan)
                        if (idx != -1) {
                            existingLoans[idx] = updatedLoan
                        }
                    } else {
                        val newLoan = LoanCycle(
                            id = 0, // let Room autogenerate SQL ID
                            customerId = newCustomerId,
                            loanAmount = lObj.getDouble("loanAmount"),
                            interestAmount = lObj.optDouble("interestAmount", 0.0),
                            weeklyAmount = lObj.getDouble("weeklyAmount"),
                            totalWeeks = lObj.optInt("totalWeeks", 10),
                            startDate = lObj.optLong("startDate", System.currentTimeMillis()),
                            status = lObj.optString("status", "ACTIVE"),
                            notes = lObj.optString("notes", ""),
                            paidAmount = 0.0,
                            uuid = if (loanUuid.isNotEmpty()) loanUuid else java.util.UUID.randomUUID().toString()
                        )
                        newLoanId = repository.addLoanCycle(newLoan).toInt()
                        existingLoans.add(newLoan.copy(id = newLoanId))
                    }
                    loanIdMap[oldLoanId] = newLoanId
                }

                for (k in 0 until paymentsArray.length()) {
                    val pObj = paymentsArray.getJSONObject(k)
                    val paymentUuid = pObj.optString("uuid", "")
                    if (paymentUuid.isNotEmpty() && deletedPaymentUuids.contains(paymentUuid)) {
                        continue // Skip deleted payments
                    }
                    val oldLoanId = pObj.getInt("loanCycleId")
                    val newLoanId = loanIdMap[oldLoanId] ?: continue

                    val paymentSignature = "${newLoanId}_${pObj.getInt("weekNumber")}_${pObj.optDouble("amountPaid")}_${pObj.optLong("paymentDate")}"
                    if (paymentUuid.isNotEmpty()) {
                        if (processedPaymentUuids.contains(paymentUuid) || processedPaymentSignatures.contains(paymentSignature)) {
                            continue // Skip duplicate payment
                        }
                        processedPaymentUuids.add(paymentUuid)
                        processedPaymentSignatures.add(paymentSignature)
                    } else {
                        if (processedPaymentSignatures.contains(paymentSignature)) {
                            continue // Skip duplicate payment
                        }
                        processedPaymentSignatures.add(paymentSignature)
                    }
                    val existingPayment = if (paymentUuid.isNotEmpty()) {
                        existingPayments.find { it.uuid == paymentUuid } ?: existingPayments.find { it.loanCycleId == newLoanId && it.weekNumber == pObj.getInt("weekNumber") && it.paymentDate == pObj.optLong("paymentDate") }
                    } else {
                        existingPayments.find { it.loanCycleId == newLoanId && it.weekNumber == pObj.getInt("weekNumber") && it.paymentDate == pObj.optLong("paymentDate") }
                    }

                    if (existingPayment != null) {
                        val updatedPayment = existingPayment.copy(
                            loanCycleId = newLoanId,
                            amountPaid = pObj.getDouble("amountPaid"),
                            paymentDate = pObj.optLong("paymentDate", existingPayment.paymentDate),
                            weekNumber = pObj.getInt("weekNumber"),
                            notes = pObj.optString("notes", existingPayment.notes),
                            upiTxnId = if (pObj.has("upiTxnId") && !pObj.isNull("upiTxnId")) pObj.getString("upiTxnId") else existingPayment.upiTxnId,
                            status = pObj.optString("status", existingPayment.status),
                            uuid = if (paymentUuid.isNotEmpty()) paymentUuid else existingPayment.uuid
                        )
                        db.collectionDao().insertPayment(updatedPayment)
                        val idx = existingPayments.indexOf(existingPayment)
                        if (idx != -1) {
                            existingPayments[idx] = updatedPayment
                        }
                    } else {
                        val newPayment = WeeklyPayment(
                            id = 0, // let Room autogenerate SQL ID
                            loanCycleId = newLoanId,
                            amountPaid = pObj.getDouble("amountPaid"),
                            paymentDate = pObj.optLong("paymentDate", System.currentTimeMillis()),
                            weekNumber = pObj.getInt("weekNumber"),
                            notes = pObj.optString("notes", ""),
                            upiTxnId = if (pObj.has("upiTxnId") && !pObj.isNull("upiTxnId")) pObj.getString("upiTxnId") else null,
                            uuid = if (paymentUuid.isNotEmpty()) paymentUuid else java.util.UUID.randomUUID().toString(),
                            status = pObj.optString("status", "ACTIVE")
                        )
                        val newId = db.collectionDao().insertPayment(newPayment).toInt()
                        existingPayments.add(newPayment.copy(id = newId))
                    }
                }

                // Restore/merge Edit Logs
                val auditLogsArray = backupObj.optJSONArray("editLogs") ?: backupObj.optJSONArray("edit_logs") ?: backupObj.optJSONArray("auditLogs") ?: backupObj.optJSONArray("audit_logs") ?: org.json.JSONArray()
                for (i in 0 until auditLogsArray.length()) {
                    val aObj = auditLogsArray.getJSONObject(i)
                    val auditUuid = aObj.optString("uuid", "")
                    val auditSignature = "${aObj.optLong("timestamp")}_${aObj.optString("actionType")}_${aObj.optInt("customerId")}"
                    if (auditUuid.isNotEmpty()) {
                        if (processedAuditLogUuids.contains(auditUuid) || processedAuditLogSignatures.contains(auditSignature)) {
                            continue // Skip duplicate edit log
                        }
                        processedAuditLogUuids.add(auditUuid)
                        processedAuditLogSignatures.add(auditSignature)
                    } else {
                        if (processedAuditLogSignatures.contains(auditSignature)) {
                            continue // Skip duplicate edit log
                        }
                        processedAuditLogSignatures.add(auditSignature)
                    }

                    val existingAudit = if (auditUuid.isNotEmpty()) {
                        existingAuditLogs.find { it.uuid == auditUuid }
                    } else {
                        existingAuditLogs.find { it.timestamp == aObj.optLong("timestamp") && it.actionType == aObj.optString("actionType") }
                    }

                    if (existingAudit == null) {
                        val newAudit = EditLog(
                            id = 0,
                            timestamp = aObj.optLong("timestamp", System.currentTimeMillis()),
                            customerId = aObj.optInt("customerId", 0),
                            customerName = aObj.optString("customerName", ""),
                            actionType = aObj.optString("actionType", ""),
                            actionDescription = aObj.optString("actionDescription", ""),
                            previousDataJson = aObj.optString("previousDataJson", ""),
                            uuid = if (auditUuid.isNotEmpty()) auditUuid else java.util.UUID.randomUUID().toString()
                        )
                        val insertedId = db.collectionDao().insertEditLog(newAudit).toInt()
                        existingAuditLogs.add(newAudit.copy(id = insertedId))
                    }
                }

                // Final verification step to recalculate paidAmount and status for ALL loan cycles in database to prevent double counting
                val finalLoans = db.collectionDao().getAllLoanCyclesOnce()
                val finalPayments = db.collectionDao().getAllPaymentsOnce()
                
                for (loan in finalLoans) {
                    val sumPaid = finalPayments.filter { it.loanCycleId == loan.id && it.status.uppercase() != "DELETED" }.sumOf { it.amountPaid }
                    val targetAmount = loan.loanAmount + loan.interestAmount
                    val shouldBePaid = sumPaid >= targetAmount
                    val computedStatus = if (shouldBePaid) "PAID" else "ACTIVE"
                    
                    if (loan.paidAmount != sumPaid || loan.status != computedStatus) {
                        db.collectionDao().updateLoanCycle(
                            loan.copy(
                                paidAmount = sumPaid,
                                status = computedStatus
                            )
                        )
                    }
                }

                // --- INTEGRITY & CROSS-VERIFICATION ENGINE ---
                val metaOutstanding = backupObj.optDouble("meta_total_outstanding", -1.0)
                val metaCustCount = backupObj.optInt("meta_customers_count", -1)
                
                val finalCustomers = db.collectionDao().getAllCustomersOnce()
                val finalLoansVerify = db.collectionDao().getAllLoanCyclesOnce()
                
                val localOutstanding = finalLoansVerify.filter { it.status.uppercase() == "ACTIVE" }.sumOf { (it.loanAmount + it.interestAmount) - it.paidAmount }
                val localCustCount = finalCustomers.size
                
                val outstandingMatches = if (metaOutstanding >= 0.0) {
                    Math.abs(localOutstanding - metaOutstanding) < 1.0
                } else {
                    true
                }
                
                val filesMatches = if (metaCustCount >= 0) {
                    localCustCount == metaCustCount
                } else {
                    true
                }
                
                _syncFilesCount.value = localCustCount
                _syncAddedCount.value = customersArray.length()
                _syncOutstandingVerified.value = outstandingMatches
                
                if (outstandingMatches && filesMatches) {
                    _syncStatsText.value = "Tally Success! Outstanding (Verified): ₹$localOutstanding, Files Count: $localCustCount."
                } else {
                    _syncStatsText.value = "Tally Warning! Local: ₹$localOutstanding ($localCustCount files) vs Expected: ₹$metaOutstanding ($metaCustCount files). Running Healing..."
                    // Automatically trigger database self-healing repair!
                    triggerDatabaseRescanAndRepair()
                }

                // Add sync timings device-specific Auditing
                val senderDev = backupObj.optJSONObject("preferences")?.optString("username", "Remote Device") ?: "Remote Device"
                
                db.collectionDao().insertEditLog(
                    com.example.data.EditLog(
                        id = 0,
                        timestamp = System.currentTimeMillis(),
                        customerId = 0,
                        customerName = "System",
                        actionType = "SYNC_VERIFY",
                        actionDescription = "Sync Verification completed on info from '$senderDev'. Local outstanding total: ₹$localOutstanding (Target verification total: ₹$metaOutstanding). Integrity check: ${if (outstandingMatches) "CORRECT" else "MISMATCH WARNING - RUNNING SELF-HEAL"}.",
                        previousDataJson = "",
                        uuid = java.util.UUID.randomUUID().toString()
                    )
                )
                // Restore Cash Balance Logs
                val cashBalanceLogsArray = backupObj.optJSONArray("cashBalanceLogs")
                if (cashBalanceLogsArray != null) {
                    db.collectionDao().deleteAllCashBalanceLogs()
                    for (i in 0 until cashBalanceLogsArray.length()) {
                        val cObj = cashBalanceLogsArray.getJSONObject(i)
                        val newCashLog = com.example.data.CashBalanceLog(
                            id = 0,
                            date = cObj.optLong("date", System.currentTimeMillis()),
                            actualCash = cObj.optDouble("actualCash", 0.0),
                            systemCash = cObj.optDouble("systemCash", 0.0),
                            collectionAmount = cObj.optDouble("collectionAmount", 0.0),
                            disbursalAmount = cObj.optDouble("disbursalAmount", 0.0),
                            expenses = cObj.optDouble("expenses", 0.0)
                        )
                        db.collectionDao().insertCashBalanceLog(newCashLog)
                    }
                }
            }

            // 3. Preferences deserialization - non-destructive update to global properties
            val prefsObj = backupObj.optJSONObject("preferences")
            if (prefsObj != null) {
                val edit = prefs.edit()
                
                if (prefsObj.has("sim_selection")) {
                    val sim = prefsObj.getString("sim_selection")
                    edit.putString("sim_selection", sim)
                    _simSelection.value = sim
                }
                if (prefsObj.has("language")) {
                    val lang = prefsObj.getString("language")
                    edit.putString("language", lang)
                    _language.value = lang
                }
                if (prefsObj.has("upi_id")) {
                    val upi = prefsObj.getString("upi_id")
                    edit.putString("upi_id", upi)
                    _upiId.value = upi
                }
                if (prefsObj.has("upi_link")) {
                    val link = prefsObj.getString("upi_link")
                    edit.putString("upi_link", link)
                    _upiLink.value = link
                }
                if (prefsObj.has("qr_image_uri")) {
                    val qr = prefsObj.getString("qr_image_uri")
                    edit.putString("qr_image_uri", qr)
                    _qrImageUri.value = qr
                }
                if (prefsObj.has("business_name")) {
                    val name = prefsObj.getString("business_name")
                    edit.putString("business_name", name)
                    _businessName.value = name
                }
                if (prefsObj.has("sms_paused")) {
                    val paused = prefsObj.getBoolean("sms_paused")
                    edit.putBoolean("sms_paused", paused)
                    _smsPaused.value = paused
                }
                if (prefsObj.has("font_size_scale")) {
                    val scale = prefsObj.getDouble("font_size_scale").toFloat()
                    edit.putFloat("font_size_scale", scale)
                    _fontSizeScale.value = scale
                }
                if (prefsObj.has("sms_new_loan_template")) {
                    val t = prefsObj.getString("sms_new_loan_template")
                    if (t.isNotBlank()) {
                        edit.putString("sms_new_loan_template", t)
                        _smsNewLoanTemplate.value = t
                    }
                }
                if (prefsObj.has("sms_payment_template")) {
                    val t = prefsObj.getString("sms_payment_template")
                    if (t.isNotBlank()) {
                        edit.putString("sms_payment_template", t)
                        _smsPaymentTemplate.value = t
                    }
                }
                if (prefsObj.has("sms_reminder_template")) {
                    val t = prefsObj.getString("sms_reminder_template")
                    if (t.isNotBlank()) {
                        edit.putString("sms_reminder_template", t)
                        _smsReminderTemplate.value = t
                    }
                }
                if (prefsObj.has("whatsapp_reminder_template")) {
                    val t = prefsObj.getString("whatsapp_reminder_template")
                    if (t.isNotBlank()) {
                        edit.putString("whatsapp_reminder_template", t)
                        _whatsappReminderTemplate.value = t
                    }
                }

                if (prefsObj.has("soundbox_enabled")) {
                    val enabled = prefsObj.getBoolean("soundbox_enabled")
                    edit.putBoolean("soundbox_enabled", enabled)
                }
                if (prefsObj.has("soundbox_language")) {
                    val soundboxLang = prefsObj.getString("soundbox_language")
                    edit.putString("soundbox_language", soundboxLang)
                }
                if (prefsObj.has("sms_reader_paused")) {
                    val paused = prefsObj.getBoolean("sms_reader_paused")
                    edit.putBoolean("sms_reader_paused", paused)
                    _smsReaderPaused.value = paused
                }
                if (prefsObj.has("auto_entry_passing")) {
                    val enabled = prefsObj.getBoolean("auto_entry_passing")
                    edit.putBoolean("auto_entry_passing", enabled)
                    _autoEntryPassing.value = enabled
                }
                if (prefsObj.has("upi_link_sharing")) {
                    val enabled = prefsObj.getBoolean("upi_link_sharing")
                    edit.putBoolean("upi_link_sharing", enabled)
                    _upiLinkSharing.value = enabled
                }

                
                // For collection groups list, we merge dynamically
                val daysString = currentGroups.distinct().joinToString(",")
                edit.putString("collection_groups_list", daysString)
                
                edit.apply()
            }
        } finally {
            isRestoringFromJson = false
        }
    }

    suspend fun restoreSingleCustomerCluster(payload: org.json.JSONObject) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val cObj = payload.optJSONObject("customer") ?: return@withContext
            val customerUuid = cObj.getString("uuid")
            if (customerUuid.isBlank()) return@withContext

            val cloudSavedAt = payload.optLong("lastSavedAt", 0L)

            db.withTransaction {
                val existing = db.collectionDao().getAllCustomersOnce().find { it.uuid == customerUuid }
                val customerId: Int
                if (existing != null) {
                    customerId = existing.id
                    val updated = existing.copy(
                        name = cObj.getString("name"),
                        phone = cObj.optString("phone", existing.phone),
                        customOrder = cObj.optInt("customOrder", existing.customOrder),
                        collectionDay = cObj.optString("collectionDay", existing.collectionDay),
                        city = cObj.optString("city", existing.city),
                        smsWeeklyReminder = cObj.optBoolean("smsWeeklyReminder", existing.smsWeeklyReminder),
                        smsConfirmationOfEntry = cObj.optBoolean("smsConfirmationOfEntry", existing.smsConfirmationOfEntry),
                        autoWeeklySms = cObj.optBoolean("autoWeeklySms", existing.autoWeeklySms),
                        autoWeeklyWhatsapp = cObj.optBoolean("autoWeeklyWhatsapp", existing.autoWeeklyWhatsapp),
                        syncedLastSavedAt = cloudSavedAt,
                        lastModified = cloudSavedAt
                    )
                    db.collectionDao().updateCustomer(updated)
                } else {
                    val newC = Customer(
                        id = 0,
                        name = cObj.getString("name"),
                        phone = cObj.optString("phone", ""),
                        customOrder = cObj.optInt("customOrder", 0),
                        collectionDay = cObj.optString("collectionDay", "Monday"),
                        createdAt = cObj.optLong("createdAt", System.currentTimeMillis()),
                        city = cObj.optString("city", ""),
                        smsWeeklyReminder = cObj.optBoolean("smsWeeklyReminder", true),
                        smsConfirmationOfEntry = cObj.optBoolean("smsConfirmationOfEntry", true),
                        autoWeeklySms = cObj.optBoolean("autoWeeklySms", false),
                        autoWeeklyWhatsapp = cObj.optBoolean("autoWeeklyWhatsapp", false),
                        uuid = customerUuid,
                        syncedLastSavedAt = cloudSavedAt,
                        lastModified = cloudSavedAt
                    )
                    customerId = db.collectionDao().insertCustomer(newC).toInt()
                }

                val localLoans = db.collectionDao().getAllLoanCyclesOnce().filter { it.customerId == customerId }
                for (l in localLoans) {
                    db.collectionDao().deleteLoanCycle(l)
                }

                val loansArr = payload.optJSONArray("loans") ?: org.json.JSONArray()
                val paymentsArr = payload.optJSONArray("payments") ?: org.json.JSONArray()

                val oldToNewLoanId = HashMap<Int, Int>()

                for (i in 0 until loansArr.length()) {
                    val lObj = loansArr.getJSONObject(i)
                    val oldLoanId = lObj.getInt("id")
                    val newL = LoanCycle(
                        id = 0,
                        customerId = customerId,
                        loanAmount = lObj.getDouble("loanAmount"),
                        interestAmount = lObj.optDouble("interestAmount", 0.0),
                        weeklyAmount = lObj.getDouble("weeklyAmount"),
                        totalWeeks = lObj.optInt("totalWeeks", 10),
                        startDate = lObj.optLong("startDate", System.currentTimeMillis()),
                        status = lObj.optString("status", "ACTIVE"),
                        notes = lObj.optString("notes", ""),
                        paidAmount = lObj.optDouble("paidAmount", 0.0),
                        uuid = lObj.optString("uuid", java.util.UUID.randomUUID().toString())
                    )
                    val insertedLoanId = db.collectionDao().insertLoanCycle(newL).toInt()
                    oldToNewLoanId[oldLoanId] = insertedLoanId
                }

                for (i in 0 until paymentsArr.length()) {
                    val pObj = paymentsArr.getJSONObject(i)
                    val oldLoanId = pObj.getInt("loanCycleId")
                    val newLoanId = oldToNewLoanId[oldLoanId] ?: continue
                    val p = WeeklyPayment(
                        id = 0,
                        loanCycleId = newLoanId,
                        amountPaid = pObj.getDouble("amountPaid"),
                        paymentDate = pObj.optLong("paymentDate", System.currentTimeMillis()),
                        weekNumber = pObj.optInt("weekNumber", 1),
                        notes = pObj.optString("notes", ""),
                        upiTxnId = if (pObj.has("upiTxnId") && !pObj.isNull("upiTxnId")) pObj.getString("upiTxnId") else null,
                        uuid = pObj.optString("uuid", java.util.UUID.randomUUID().toString())
                    )
                    db.collectionDao().insertPayment(p)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearEditLogs() {
        viewModelScope.launch {
            repository.clearEditLogs()
        }
    }

    fun revertEditLog(log: EditLog, context: Context) {
        viewModelScope.launch {
            try {
                val json = log.previousDataJson
                when (log.actionType) {
                    "CREATE_CUSTOMER" -> {
                        val customer = repository.getCustomerById(log.customerId)
                        if (customer != null) {
                            repository.deleteCustomer(customer)
                            Toast.makeText(context, "Reverted: Deleted customer ${customer.name}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Customer already deleted", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "EDIT_CUSTOMER" -> {
                        if (json.isNotBlank()) {
                            val oldCustomer = jsonToCustomer(json)
                            repository.updateCustomer(oldCustomer)
                            Toast.makeText(context, "Reverted: Restored customer details for ${oldCustomer.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "DELETE_CUSTOMER" -> {
                        if (json.isNotBlank()) {
                            val obj = org.json.JSONObject(json)
                            val cObj = obj.getJSONObject("customer")
                            val customer = jsonToCustomer(cObj.toString())
                            repository.addCustomer(customer)
                            
                            val loansArray = obj.getJSONArray("loans")
                            for (i in 0 until loansArray.length()) {
                                val loan = jsonToLoanCycle(loansArray.getJSONObject(i).toString())
                                repository.addLoanCycle(loan)
                            }
                            
                            val paymentsArray = obj.getJSONArray("payments")
                            for (i in 0 until paymentsArray.length()) {
                                val payment = jsonToWeeklyPayment(paymentsArray.getJSONObject(i).toString())
                                repository.addWeeklyPayment(payment)
                            }
                            
                            Toast.makeText(context, "Reverted: Restored customer ${customer.name} and their history", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "CREATE_LOAN" -> {
                        val loanId = log.previousDataJson.toIntOrNull() ?: 0
                        val loan = repository.getLoanCycleById(loanId)
                        if (loan != null) {
                            repository.deleteLoanCycle(loan)
                            Toast.makeText(context, "Reverted: Deleted active loan of ₹${loan.loanAmount}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "EDIT_LOAN" -> {
                        if (json.isNotBlank()) {
                            val oldLoan = jsonToLoanCycle(json)
                            repository.updateLoanCycle(oldLoan)
                            Toast.makeText(context, "Reverted: Restored loan cycle details", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "DELETE_LOAN" -> {
                        if (json.isNotBlank()) {
                            val obj = org.json.JSONObject(json)
                            val lObj = obj.getJSONObject("loan")
                            val loan = jsonToLoanCycle(lObj.toString())
                            repository.addLoanCycle(loan)
                            
                            val paymentsArray = obj.getJSONArray("payments")
                            for (i in 0 until paymentsArray.length()) {
                                val payment = jsonToWeeklyPayment(paymentsArray.getJSONObject(i).toString())
                                repository.addWeeklyPayment(payment)
                            }
                            Toast.makeText(context, "Reverted: Restored deleted loan", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "RECORD_PAYMENT" -> {
                        val paymentId = log.previousDataJson.toIntOrNull() ?: 0
                        val p = allPayments.value.find { it.id == paymentId }
                        if (p != null) {
                            repository.removeWeeklyPayment(p.id, p.loanCycleId)
                            Toast.makeText(context, "Reverted: Removed payment for week ${p.weekNumber}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Payment already removed", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "EDIT_PAYMENT" -> {
                        if (json.isNotBlank()) {
                            val oldPayment = jsonToWeeklyPayment(json)
                            repository.updateWeeklyPayment(
                                paymentId = oldPayment.id,
                                loanCycleId = oldPayment.loanCycleId,
                                newAmount = oldPayment.amountPaid,
                                newWeekNumber = oldPayment.weekNumber,
                                newDate = oldPayment.paymentDate,
                                newNotes = oldPayment.notes
                            )
                            Toast.makeText(context, "Reverted: Restored weekly payment details", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "DELETE_PAYMENT" -> {
                        if (json.isNotBlank()) {
                            val oldPayment = jsonToWeeklyPayment(json)
                            repository.addWeeklyPayment(oldPayment)
                            Toast.makeText(context, "Reverted: Restored payment of ₹${oldPayment.amountPaid}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "EDIT_PREFERENCES" -> {
                        if (json.isNotBlank()) {
                            val obj = org.json.JSONObject(json)
                            val key = obj.optString("key")
                            val oldValue = obj.optString("oldValue")
                            when (key) {
                                "upi_id" -> {
                                    _upiId.value = oldValue
                                    prefs.edit().putString("upi_id", oldValue).apply()
                                }
                                "upi_link" -> {
                                    _upiLink.value = oldValue
                                    prefs.edit().putString("upi_link", oldValue).apply()
                                }
                                "upi_link_sharing" -> {
                                    val b = oldValue.toBoolean()
                                    _upiLinkSharing.value = b
                                    prefs.edit().putBoolean("upi_link_sharing", b).apply()
                                }
                                "business_name" -> {
                                    _businessName.value = oldValue
                                    prefs.edit().putString("business_name", oldValue).apply()
                                }
                                "language" -> {
                                    _language.value = oldValue
                                    prefs.edit().putString("language", oldValue).apply()
                                }
                            }
                            Toast.makeText(context, "Reverted preference '$key' to '$oldValue'", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                repository.deleteEditLog(log)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error reverting: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun logPreferenceChange(key: String, oldValue: String, newValue: String) {
        if (oldValue == newValue) return
        viewModelScope.launch {
            val desc = "Preference changed: $key from '$oldValue' to '$newValue'"
            val previousObj = org.json.JSONObject().apply {
                put("key", key)
                put("oldValue", oldValue)
                put("newValue", newValue)
            }.toString()
            
            repository.addEditLog(
                EditLog(
                    customerId = 0,
                    customerName = "System Preferences",
                    actionType = "EDIT_PREFERENCES",
                    actionDescription = desc,
                    previousDataJson = previousObj
                )
            )
            val pObj = org.json.JSONObject().apply {
                put("key", key)
                put("oldValue", oldValue)
                put("newValue", newValue)
            }
            pushFirebaseEditLog("EDIT_PREFERENCES", pObj.toString(), previousObj)
        }
    }

    fun logAnalyticsEvent(eventName: String, params: android.os.Bundle? = null) {
        // No-op for offline only operation
    }

    fun retryConnectivityAndFlush() {
        // No-op for offline only operation
    }

    fun buildDetailedCustomerChangeDesc(prevJsonStr: String, currJsonStr: String): Pair<Int, String> {
        if (prevJsonStr.isBlank() || currJsonStr.isBlank()) return Pair(0, "")
        try {
            val prev = org.json.JSONObject(prevJsonStr)
            val curr = org.json.JSONObject(currJsonStr)
            val changes = mutableListOf<String>()
            val fields = listOf(
                "name" to "Name",
                "phone" to "Phone",
                "collectionDay" to "Collection Day",
                "city" to "City",
                "upiNameAlias" to "UPI Alias",
                "preferredLanguage" to "Language",
                "smsWeeklyReminder" to "SMS Weekly",
                "smsConfirmationOfEntry" to "SMS Check-In",
                "autoWeeklySms" to "Auto SMS",
                "autoWeeklyWhatsapp" to "Auto Whatsapp"
            )
            for ((key, label) in fields) {
                if (prev.has(key) && curr.has(key)) {
                    val pVal = prev.get(key).toString().trim()
                    val cVal = curr.get(key).toString().trim()
                    if (pVal != cVal) {
                        changes.add("$label: '$pVal' ➔ '$cVal'")
                    }
                }
            }
            val count = changes.size
            val desc = if (count > 0) {
                "($count factors edited) " + changes.joinToString(", ")
            } else {
                "(0 factors edited) No fields modified"
            }
            return Pair(count, desc)
        } catch (e: Exception) {
            e.printStackTrace()
            return Pair(0, "")
        }
    }

    fun buildDetailedLoanChangeDesc(prevJsonStr: String, currJsonStr: String): Pair<Int, String> {
        if (prevJsonStr.isBlank() || currJsonStr.isBlank()) return Pair(0, "")
        try {
            val prev = org.json.JSONObject(prevJsonStr)
            val curr = org.json.JSONObject(currJsonStr)
            val changes = mutableListOf<String>()
            val fields = listOf(
                "loanAmount" to "Principal Amount",
                "interestAmount" to "Interest Amount",
                "weeklyAmount" to "Weekly Installment",
                "totalWeeks" to "Total Weeks",
                "notes" to "Notes"
            )
            for ((key, label) in fields) {
                if (prev.has(key) && curr.has(key)) {
                    val pVal = prev.get(key).toString().trim()
                    val cVal = curr.get(key).toString().trim()
                    if (pVal != cVal) {
                        val pFormatted = if (key.endsWith("Amount")) "₹$pVal" else pVal
                        val cFormatted = if (key.endsWith("Amount")) "₹$cVal" else cVal
                        changes.add("$label: $pFormatted ➔ $cFormatted")
                    }
                }
            }
            val count = changes.size
            val desc = if (count > 0) {
                "($count factors edited) " + changes.joinToString(", ")
            } else {
                "(0 factors edited) No fields modified"
            }
            return Pair(count, desc)
        } catch (e: Exception) {
            e.printStackTrace()
            return Pair(0, "")
        }
    }

    fun buildDetailedPaymentChangeDesc(prevJsonStr: String, currJsonStr: String): Pair<Int, String> {
        if (prevJsonStr.isBlank() || currJsonStr.isBlank()) return Pair(0, "")
        try {
            val prev = org.json.JSONObject(prevJsonStr)
            val curr = org.json.JSONObject(currJsonStr)
            val changes = mutableListOf<String>()
            val fields = listOf(
                "amountPaid" to "Amount Paid",
                "weekNumber" to "Week Number",
                "notes" to "Notes",
                "upiTxnId" to "UPI Transaction ID"
            )
            for ((key, label) in fields) {
                if (prev.has(key) && curr.has(key)) {
                    val pVal = prev.get(key).toString().trim()
                    val cVal = curr.get(key).toString().trim()
                    if (pVal != cVal) {
                        val pFormatted = if (key == "amountPaid") "₹$pVal" else pVal
                        val cFormatted = if (key == "amountPaid") "₹$cVal" else cVal
                        changes.add("$label: $pFormatted ➔ $cFormatted")
                    }
                }
            }
            val count = changes.size
            val desc = if (count > 0) {
                "($count factors edited) " + changes.joinToString(", ")
            } else {
                "(0 factors edited) No fields modified"
            }
            return Pair(count, desc)
        } catch (e: Exception) {
            e.printStackTrace()
            return Pair(0, "")
        }
    }

    private fun customerToJson(customer: Customer): String {
        return org.json.JSONObject().apply {
            put("v", 1)
            put("id", customer.id)
            put("name", customer.name)
            put("phone", customer.phone)
            put("customOrder", customer.customOrder)
            put("collectionDay", customer.collectionDay)
            put("createdAt", customer.createdAt)
            put("city", customer.city)
            put("smsWeeklyReminder", customer.smsWeeklyReminder)
            put("smsConfirmationOfEntry", customer.smsConfirmationOfEntry)
            put("autoWeeklySms", customer.autoWeeklySms)
            put("autoWeeklyWhatsapp", customer.autoWeeklyWhatsapp)
            put("upiNameAlias", customer.upiNameAlias)
            put("preferredLanguage", customer.preferredLanguage)
            put("uuid", customer.uuid)
            put("status", customer.status)
        }.toString()
    }

    private fun jsonToCustomer(jsonStr: String): Customer {
        val obj = org.json.JSONObject(jsonStr)
        return Customer(
            id = obj.optInt("id", 0),
            name = obj.optString("name", ""),
            phone = obj.optString("phone", ""),
            customOrder = obj.optInt("customOrder", 0),
            collectionDay = obj.optString("collectionDay", "Monday"),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            city = obj.optString("city", ""),
            smsWeeklyReminder = obj.optBoolean("smsWeeklyReminder", true),
            smsConfirmationOfEntry = obj.optBoolean("smsConfirmationOfEntry", true),
            autoWeeklySms = obj.optBoolean("autoWeeklySms", false),
            autoWeeklyWhatsapp = obj.optBoolean("autoWeeklyWhatsapp", false),
            upiNameAlias = obj.optString("upiNameAlias", ""),
            preferredLanguage = obj.optString("preferredLanguage", "English"),
            uuid = obj.optString("uuid", java.util.UUID.randomUUID().toString()),
            status = obj.optString("status", "ACTIVE")
        )
    }

    private fun loanCycleToJson(loan: LoanCycle): String {
        return org.json.JSONObject().apply {
            put("v", 1)
            put("id", loan.id)
            put("customerId", loan.customerId)
            put("loanAmount", loan.loanAmount)
            put("interestAmount", loan.interestAmount)
            put("weeklyAmount", loan.weeklyAmount)
            put("totalWeeks", loan.totalWeeks)
            put("startDate", loan.startDate)
            put("status", loan.status)
            put("notes", loan.notes)
            put("paidAmount", loan.paidAmount)
            put("uuid", loan.uuid)
            put("deduction", loan.deduction)
        }.toString()
    }

    private fun jsonToLoanCycle(jsonStr: String): LoanCycle {
        val obj = org.json.JSONObject(jsonStr)
        return LoanCycle(
            id = obj.optInt("id", 0),
            customerId = obj.optInt("customerId", 0),
            loanAmount = obj.optDouble("loanAmount", 0.0),
            interestAmount = obj.optDouble("interestAmount", 0.0),
            weeklyAmount = obj.optDouble("weeklyAmount", 0.0),
            totalWeeks = obj.optInt("totalWeeks", 10),
            startDate = obj.optLong("startDate", System.currentTimeMillis()),
            status = obj.optString("status", "ACTIVE"),
            notes = obj.optString("notes", ""),
            paidAmount = obj.optDouble("paidAmount", 0.0),
            uuid = obj.optString("uuid", java.util.UUID.randomUUID().toString()),
            deduction = obj.optDouble("deduction", 0.0)
        )
    }

    private fun weeklyPaymentToJson(payment: WeeklyPayment): String {
        return org.json.JSONObject().apply {
            put("v", 1)
            put("id", payment.id)
            put("loanCycleId", payment.loanCycleId)
            put("amountPaid", payment.amountPaid)
            put("paymentDate", payment.paymentDate)
            put("weekNumber", payment.weekNumber)
            put("notes", payment.notes)
            put("upiTxnId", payment.upiTxnId ?: "")
            put("uuid", payment.uuid)
            put("status", payment.status)
        }.toString()
    }

    private fun jsonToWeeklyPayment(jsonStr: String): WeeklyPayment {
        val obj = org.json.JSONObject(jsonStr)
        val txnId = obj.optString("upiTxnId", "")
        return WeeklyPayment(
            id = obj.optInt("id", 0),
            loanCycleId = obj.optInt("loanCycleId", 0),
            amountPaid = obj.optDouble("amountPaid", 0.0),
            paymentDate = obj.optLong("paymentDate", System.currentTimeMillis()),
            weekNumber = obj.optInt("weekNumber", 1),
            notes = obj.optString("notes", ""),
            upiTxnId = if (txnId.isEmpty()) null else txnId,
            uuid = obj.optString("uuid", java.util.UUID.randomUUID().toString()),
            status = obj.optString("status", "ACTIVE")
        )
    }

    fun logout(context: Context) {
        prefs.edit().apply {
            putBoolean("is_logged_in", false)
            putString("current_user", "")
            putBoolean("has_completed_device_setup", false)
            putBoolean("is_demo_mode", false)
            apply()
        }
        _isDemoMode.value = false
        AppDatabase.resetDatabaseInstances()
        (context as? android.app.Activity)?.recreate()
    }
}

// Data structures representing current states helper
data class CustomerCollectionItem(
    val customer: Customer,
    val activeLoans: List<LoanCycle> = emptyList(),
    val lastPaymentDate: Long? = null,
    val lastPaymentAmount: Double? = null,
    val originalGroupIndex: Int = 0,
    val todaysPayments: Map<Int, Double> = emptyMap(),
    val todaysDisbursedLoans: List<LoanCycle> = emptyList()
)

data class DashboardStats(
    val totalActiveLoansAmount: Double = 0.0,
    val totalOutstandingDue: Double = 0.0,
    val todaysCollectedAmount: Double = 0.0,
    val groupTodaysCollectedAmount: Double = 0.0,
    val todaysDisbursedAmount: Double = 0.0,
    val groupTodaysDisbursedAmount: Double = 0.0,
    val activeCyclesCount: Int = 0,
    val paidOffCyclesCount: Int = 0,
    val outstandingPrincipal: Double = 0.0,
    val outstandingInterest: Double = 0.0,
    val todaysInterestAmount: Double = 0.0,
    val groupTodaysInterestAmount: Double = 0.0,
    val todaysDeductionsAmount: Double = 0.0,
    val groupTodaysDeductionsAmount: Double = 0.0
)
