package com.example.network

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import com.google.firebase.database.FirebaseDatabase
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.util.SecureConfig

enum class UpdateStatus {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    DOWNLOADING,
    DOWNLOADED,
    FAILED
}

object FirebaseUpdateManager {
    private const val TAG = "FirebaseUpdate"
    private var downloadReceiver: BroadcastReceiver? = null
    private var enqueuedDownloadId: Long = -1L

    // Live observable state flow for real-time UI tracking
    private val _updateStatus = MutableStateFlow(UpdateStatus.IDLE)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    private val _latestVersionCode = MutableStateFlow(1L)
    val latestVersionCode: StateFlow<Long> = _latestVersionCode.asStateFlow()

    private val _latestVersionName = MutableStateFlow("")
    val latestVersionName: StateFlow<String> = _latestVersionName.asStateFlow()

    private val _updateError = MutableStateFlow<String?>(null)
    val updateError: StateFlow<String?> = _updateError.asStateFlow()

    fun checkForCloudUpdates(context: Context, manualCheck: Boolean = false) {
        val prefs = context.getSharedPreferences("weekly_finance_prefs", Context.MODE_PRIVATE)
        val pauseUpdates = prefs.getBoolean("pause_updates_enabled", false)
        if (pauseUpdates && !manualCheck) {
            Log.i(TAG, "Background update check paused by preference.")
            return
        }

        val autoUpdate = prefs.getBoolean("auto_update_enabled", true)
        if (!autoUpdate && !manualCheck) {
            Log.i(TAG, "Automatic updates are disabled. Skipping update check.")
            return
        }

        // Set state to checking
        _updateStatus.value = UpdateStatus.CHECKING
        _updateError.value = null

        val database = try {
            FirebaseDatabase.getInstance(SecureConfig.firebaseDatabaseUrl)
        } catch (e: Exception) {
            FirebaseDatabase.getInstance()
        }
        val configRef = database.getReference("update_config")

        val currentVersionCode = try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(pInfo)
        } catch (e: Exception) {
            1L
        }

        val runningFirebaseVersion = prefs.getLong("running_firebase_version", currentVersionCode)

        configRef.get().addOnSuccessListener { snapshot ->
            val latestCode = snapshot.child("versionId").getValue(Long::class.java) ?: currentVersionCode
            val latestName = snapshot.child("versionName").getValue(String::class.java) ?: "1.0.$latestCode"
            val apkDownloadUrl = snapshot.child("apkFileId").getValue(String::class.java) ?: ""

            _latestVersionCode.value = latestCode
            _latestVersionName.value = latestName

            if (latestCode > runningFirebaseVersion && apkDownloadUrl.isNotEmpty()) {
                Log.i(TAG, "New Update Detected! v$latestCode > v$runningFirebaseVersion")
                
                // Check if we have already downloaded this specific update file
                val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "mdfinance-update-$latestCode.apk")
                val downloadedCode = prefs.getLong("downloaded_version_code", -1L)

                if (file.exists() && downloadedCode == latestCode) {
                    _updateStatus.value = UpdateStatus.DOWNLOADED
                    Log.i(TAG, "Update file already downloaded and ready: v$latestCode")
                } else {
                    _updateStatus.value = UpdateStatus.UPDATE_AVAILABLE
                    Toast.makeText(context, "New Update Detected! Creating emergency backups...", Toast.LENGTH_LONG).show()

                    // Perform robust backup before starting download
                    val coroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                    coroutineScope.launch {
                        try {
                            val db = com.example.data.AppDatabase.getDatabase(context)
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

                            val backupDir = File(context.filesDir, "update_backups")
                            if (!backupDir.exists()) {
                                backupDir.mkdirs()
                            }
                            val sdf = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                            val timestamp = sdf.format(java.util.Date())
                            val backupFile = File(backupDir, "finance_ALL_backup_before_update_$timestamp.csv")
                            backupFile.writeText(csvString, Charsets.UTF_8)
                            Log.i(TAG, "Backup successfully saved locally to: ${backupFile.absolutePath}")

                            val latestBackupFile = File(context.filesDir, "finance_ALL_latest_backup.csv")
                            latestBackupFile.writeText(csvString, Charsets.UTF_8)

                            // Upload backup securely to RTDB
                            val rtdb = FirebaseDatabase.getInstance(SecureConfig.firebaseDatabaseUrl)
                            val ref = rtdb.getReference("ledger_csv")
                            val task = ref.setValue(csvString)
                            com.google.android.gms.tasks.Tasks.await(task)
                            Log.i(TAG, "Cloud ledger backup successfully uploaded and synchronized.")

                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                Toast.makeText(context, "Backups finalized! Launching automatic background downloader...", Toast.LENGTH_SHORT).show()
                                executeApkDownload(context.applicationContext, apkDownloadUrl, latestCode)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Backup procedure failed: ${e.message}", e)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                Toast.makeText(context, "Backup failed but proceeding with update...", Toast.LENGTH_SHORT).show()
                                executeApkDownload(context.applicationContext, apkDownloadUrl, latestCode)
                            }
                        }
                    }
                }
            } else {
                Log.i(TAG, "Application is fully up to date.")
                _updateStatus.value = UpdateStatus.UP_TO_DATE
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to read update config: ${e.message}")
            _updateStatus.value = UpdateStatus.FAILED
            _updateError.value = e.message
        }
    }

    private fun executeApkDownload(context: Context, url: String, latestCode: Long) {
        _updateStatus.value = UpdateStatus.DOWNLOADING
        try {
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "mdfinance-update-$latestCode.apk")
            if (file.exists()) {
                file.delete()
            }

            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("MD Finance Update")
                .setDescription("Downloading update version v$latestCode...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(file))
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            
            if (downloadReceiver != null) {
                try {
                    context.unregisterReceiver(downloadReceiver)
                } catch (e: Exception) {
                    // Ignore
                }
            }

            downloadReceiver = object : BroadcastReceiver() {
                override fun onReceive(recvContext: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                    if (id == enqueuedDownloadId && id != -1L) {
                        Log.i(TAG, "Update download completed! Initializing APK Installation...")
                        _updateStatus.value = UpdateStatus.DOWNLOADED
                        
                        // Save successfully downloaded code to SharedPreferences
                        val sharedPrefs = recvContext.getSharedPreferences("weekly_finance_prefs", Context.MODE_PRIVATE)
                        sharedPrefs.edit().putLong("downloaded_version_code", latestCode).apply()

                        Toast.makeText(recvContext, "Download Completed! Opening update installer...", Toast.LENGTH_LONG).show()
                        triggerInstall(recvContext, latestCode)

                        try {
                            recvContext.unregisterReceiver(this)
                        } catch (e: Exception) {
                            // Ignore
                        }
                        downloadReceiver = null
                    }
                }
            }

            enqueuedDownloadId = downloadManager.enqueue(request)
            
            androidx.core.content.ContextCompat.registerReceiver(
                context,
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )

            Toast.makeText(context, "Download started! Progress is in the notification bar.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule download", e)
            _updateStatus.value = UpdateStatus.FAILED
            _updateError.value = e.message
            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun triggerInstall(context: Context, latestCode: Long) {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "mdfinance-update-$latestCode.apk")
        if (file.exists()) {
            try {
                // Save the running firebase version so we don't get stuck in an install loop
                val sharedPrefs = context.getSharedPreferences("weekly_finance_prefs", Context.MODE_PRIVATE)
                sharedPrefs.edit().putLong("running_firebase_version", latestCode).apply()

                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    val apkUri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                context.startActivity(installIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start installation activity", e)
                Toast.makeText(context, "Package installer error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "Update APK file not found on device.", Toast.LENGTH_SHORT).show()
        }
    }
}
