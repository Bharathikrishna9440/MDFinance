package com.example.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BackupSubPage(
    language: String,
    viewModel: FinanceViewModel,
    appColors: AppThemeColors,
    collectionGroups: List<String>,
    context: Context,
    currentUserRole: String = "ADMIN"
) {
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var groupToImportInto by remember { mutableStateOf<String?>(null) }

    val msgAllDataDeleted = translate("All data successfully deleted!", language)
    val msgDeleteFailed = translate("Delete failed:", language)
    val msgSuccessTail = translate("successfully imported!", language)
    val msgImportFailedHead = translate("Import failed:", language)
    val msgAllDaysLedger = translate("All days ledger", language)

    // CSV File picker launcher
    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val targetGroup = groupToImportInto
        if (uri != null && targetGroup != null) {
            viewModel.importCsvGroupBackup(
                context = context,
                uriString = uri.toString(),
                groupName = targetGroup,
                onSuccess = {
                    val displayGroupName = if (targetGroup == "ALL") msgAllDaysLedger else targetGroup
                    Toast.makeText(context, "$displayGroupName $msgSuccessTail", Toast.LENGTH_LONG).show()
                },
                onError = { err ->
                    Toast.makeText(context, "$msgImportFailedHead $err", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = {
                Text(
                    text = translate("⚠️ Delete All Local Data?", language),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.Red
                )
            },
            text = {
                Text(
                    text = translate("WARNING: This will completely delete all clients, loan records, edits, and transaction histories from this local device. This is irreversible. You can import your saved backups later to restore.", language),
                    fontSize = 12.sp,
                    color = Color.Black
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllDialog = false
                        viewModel.clearAllLocalData(
                            context = context,
                            onSuccess = {
                                Toast.makeText(context, msgAllDataDeleted, Toast.LENGTH_LONG).show()
                            },
                            onError = { err ->
                                Toast.makeText(context, "$msgDeleteFailed $err", Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
                ) {
                    Text(translate("Delete All Data", language), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteAllDialog = false },
                    border = BorderStroke(1.dp, ColorSlateDark)
                ) {
                    Text(translate("Cancel", language), color = ColorSlateDark)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(12.dp)
        )
    }

    // Dynamic sheets fix: Strict ordering structure implementation to guarantee separate Sunday sub-sheets survive distinct grouping queries
    val defaultGroups = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Saturday", "Sunday mrg", "Sunday eve")
    val dynamicGroups = remember(collectionGroups) {
        val sanitizedIncoming = collectionGroups.map { it.trim() }
            .filter { 
                !it.equals("Friday", ignoreCase = true) && 
                !it.equals("Sunday", ignoreCase = true) &&
                !it.equals("Sunday Morning", ignoreCase = true) && 
                !it.equals("Sunday Evening", ignoreCase = true) 
            }
        
        // Append default groups first to safeguard structural layout precedence rules
        (defaultGroups + sanitizedIncoming).distinctBy { it.lowercase(java.util.Locale.US) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Security Banner Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                     imageVector = Icons.Default.CloudOff,
                     contentDescription = "Offline Secure Icon",
                     tint = appColors.primaryAccent,
                     modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = translate("Fully Offline Local Backup Engine", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = ColorSlateDark
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = translate("All data is loaded locally using standard CSV formats. No public clouds, databases or external URLs are connected to your ledger data.", language),
                        fontSize = 11.sp,
                        color = Color.DarkGray,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // Universal Full Backup & Restore Card (No blue box or status labels as requested)
        Card(
            colors = CardDefaults.cardColors(containerColor = appColors.primaryAccent.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.5.dp, appColors.primaryAccent),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = translate("Universal Full Ledger CSV Backup", language),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.Black
                )

                Text(
                    text = if (currentUserRole == "USER") 
                        translate("Export all active days' customers, loans, and weekly payments in a single unified CSV spreadsheet template cleanly.", language)
                    else 
                        translate("Export or import all active days' customers, loans, and weekly payments in a single unified CSV spreadsheet template cleanly.", language),
                    fontSize = 11.sp,
                    color = Color.DarkGray,
                    lineHeight = 15.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Export ALL CSV Button
                    OutlinedButton(
                        onClick = { viewModel.exportCsvGroupBackup(context, "ALL") },
                        border = BorderStroke(1.5.dp, appColors.primaryAccent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = if (currentUserRole == "USER") Modifier.fillMaxWidth() else Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Export All icon",
                            tint = appColors.primaryAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = translate("Export All Days", language),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = appColors.primaryAccent
                        )
                    }

                    if (currentUserRole != "USER") {
                        // Import ALL CSV Button
                        Button(
                            onClick = {
                                groupToImportInto = "ALL"
                                csvPickerLauncher.launch("*/*")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = appColors.primaryAccent),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileUpload,
                                contentDescription = "Import All icon",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = translate("Import All Days", language),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Firebase Cloud Sync Card
        val syncPaused by viewModel.syncPaused.collectAsStateWithLifecycle()
        val firebaseSyncStatus by viewModel.firebaseSyncStatus.collectAsStateWithLifecycle()

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = translate("Firebase Real-time Cloud Sync", language),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.Black
                )

                Text(
                    text = translate("Synchronize your ledger entries, customers, payments, and audits instantly with other devices connected to your Firebase Realtime Database cloud.", language),
                    fontSize = 11.sp,
                    color = Color.DarkGray,
                    lineHeight = 15.sp
                )

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // Toggle Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = translate("Cloud Integration Status", language),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = ColorSlateDark
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            val dotColor = when {
                                syncPaused -> Color.Gray
                                firebaseSyncStatus.contains("Error", ignoreCase = true) || firebaseSyncStatus.contains("Failed", ignoreCase = true) -> Color.Red
                                firebaseSyncStatus.contains("Synced", ignoreCase = true) -> Color(0xFF10B981) // emerald green
                                else -> Color(0xFFF59E0B) // amber
                            }
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(color = dotColor, shape = CircleShape)
                            )
                            val statusTextColor = when {
                                syncPaused -> Color.Gray
                                firebaseSyncStatus.contains("Error", ignoreCase = true) || firebaseSyncStatus.contains("Failed", ignoreCase = true) -> Color.Red
                                firebaseSyncStatus.contains("Synced", ignoreCase = true) -> Color(0xFF047857) // dark green
                                else -> Color(0xFFB45309) // dark amber
                            }
                            Text(
                                text = translate(firebaseSyncStatus, language),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusTextColor
                            )
                        }
                    }

                    Switch(
                        checked = !syncPaused,
                        onCheckedChange = { isChecked ->
                            viewModel.setSyncPaused(!isChecked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = appColors.primaryAccent,
                            checkedTrackColor = appColors.primaryAccent.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.testTag("firebase_sync_pause_resume_switch")
                    )
                }

                if (!syncPaused) {
                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    // Manual Synchronize Now / Fetch or static Upload Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (currentUserRole == "USER") Arrangement.Center else Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.startFirebaseSyncListening() },
                            border = BorderStroke(1.5.dp, appColors.primaryAccent),
                            shape = RoundedCornerShape(8.dp),
                            modifier = if (currentUserRole == "USER") Modifier.fillMaxWidth() else Modifier.weight(1f)
                        ) {
                            Text(
                                text = translate("Fetch from Cloud", language),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = appColors.primaryAccent
                            )
                        }

                        if (currentUserRole != "USER") {
                            Button(
                                onClick = { viewModel.forceUploadToFirebaseCloud() },
                                colors = ButtonDefaults.buttonColors(containerColor = appColors.primaryAccent),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = translate("Upload to Cloud", language),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // Google Drive / Apps Script Backup Card
        val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsStateWithLifecycle()
        val isGoogleDriveBackupLoading by viewModel.isGoogleDriveBackupLoading.collectAsStateWithLifecycle()
        val googleDriveBackupStatusMessage by viewModel.googleDriveBackupStatusMessage.collectAsStateWithLifecycle()

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = translate("Google Drive Remote Backup", language),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.Black
                )

                Text(
                    text = translate("Automatically upload daily CSV ledger backups directly to your secure Google Drive. Pause/continue daily automatic backups or trigger an immediate upload below.", language),
                    fontSize = 11.sp,
                    color = Color.DarkGray,
                    lineHeight = 15.sp
                )

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // Toggle Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = translate("Daily Automatic Upload", language),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = ColorSlateDark
                        )
                        Text(
                            text = translate("Toggle automatic back-up on daily app start or internet reconnection", language),
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = autoBackupEnabled,
                        onCheckedChange = { viewModel.setAutoBackupEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = appColors.primaryAccent,
                            checkedTrackColor = appColors.primaryAccent.copy(alpha = 0.3f)
                        )
                    )
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // Immediate Backup trigger Button
                Button(
                    onClick = { viewModel.sendManualBackupToGoogleDrive() },
                    colors = ButtonDefaults.buttonColors(containerColor = appColors.primaryAccent),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isGoogleDriveBackupLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isGoogleDriveBackupLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = googleDriveBackupStatusMessage ?: translate("Uploading...", language),
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Cloud Upload Icon",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = translate("Send a Backup to Google Drive", language),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }

                // Status Message display
                if (!isGoogleDriveBackupLoading && googleDriveBackupStatusMessage != null) {
                    Text(
                        text = googleDriveBackupStatusMessage!!,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (googleDriveBackupStatusMessage!!.contains("success", ignoreCase = true) || googleDriveBackupStatusMessage!!.contains("saved", ignoreCase = true)) {
                            Color(0xFF0F766E) // success teal
                        } else {
                            Color(0xFFDC2626) // error red
                        },
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    
                    // Clear the message after display
                    LaunchedEffect(googleDriveBackupStatusMessage) {
                        kotlinx.coroutines.delay(5000)
                        viewModel.clearGoogleDriveBackupStatusMessage()
                    }
                }
            }
        }

        // CSV Copy Format Card
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
        val msgTemplateHeaderCopied = translate("Excel/CSV template header copied to clipboard!", language)
        val termCopyFormat = translate("Copy Format", language)
        val termTitle = translate("📋 CSV Import Format Template", language)
        val termDesc = translate("To restore or batch import your ledger correctly, use a spreadsheet tool (like Excel) containing these exact column headers in the first row:", language)
        val termSchemaRules = translate("Mandatory Column Schema Rules:", language)

        val rule0 = translate("0: Customer UUID", language)
        val rule1 = translate("1: Route No (Sort Order)", language)
        val rule2 = translate("2: Client Name", language)
        val rule3 = translate("3: Phone Number", language)
        val rule4 = translate("4: City", language)
        val rule5 = translate("5: SMS Settings (Weekly Reminder & Entry Confirmation)", language)
        val rule6 = translate("6: Loan ID (UUID)", language)
        val rule7 = translate("7: Amount Disbursed", language)
        val rule8 = translate("8: Principle (₹)", language)
        val rule9 = translate("9: Interest (₹)", language)
        val rule10 = translate("10: Date of Dispersal", language)
        val rule11to70 = translate("11 to 70: Week 1 to 30 Date/Time & Amount Received (paired)", language)
        val rule71 = translate("71: Collection Day", language)
        val schemaList = remember(language) {
            listOf(rule0, rule1, rule2, rule3, rule4, rule5, rule6, rule7, rule8, rule9, rule10, rule11to70, rule71)
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = termTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.Black
                    )
                    
                    Button(
                        onClick = {
                            val csvHeaderBuilder = StringBuilder("Customer UUID,Route No (Sort Order),Client Name,Phone Number,City,SMS Settings (Weekly & Entry Confirmation),Loan ID (UUID),Amount Disbursed,Principle (₹),Interest (₹),Date of Dispersal")
                            for (w in 1..30) {
                                csvHeaderBuilder.append(",Week $w Date & Time,Week $w Amt Received")
                            }
                            csvHeaderBuilder.append(",Collection Day")
                            val fullHeaderString = csvHeaderBuilder.toString()
                            
                            clipboardManager.setText(androidx.compose.ui.text.buildAnnotatedString { append(fullHeaderString) })
                            Toast.makeText(context, msgTemplateHeaderCopied, Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = appColors.primaryAccent),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = termCopyFormat,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                }

                Text(
                    text = termDesc,
                    fontSize = 11.sp,
                    color = Color.DarkGray,
                    lineHeight = 15.sp
                )

                // Render a list of columns so user can see what columns are in the structure
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = termSchemaRules,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = ColorSlateDark
                        )
                        schemaList.forEach { translatedItem ->
                            Text(
                                text = "• $translatedItem",
                                fontSize = 10.sp,
                                color = Color.DarkGray,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = translate("Active Day Route Sheets", language),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = ColorSlateDark,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        // Render each active group row with its combined Import / Export CSV triggers
        dynamicGroups.forEach { group ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = group,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.Black
                    )

                    Text(
                        text = translate("Manage offline backups specifically for $group. You can export current balances to CSV, edit in spreadsheets, and restore it back directly.", language),
                        fontSize = 11.sp,
                        color = Color.Gray,
                        lineHeight = 14.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Export CSV Button
                        OutlinedButton(
                            onClick = { viewModel.exportCsvGroupBackup(context, group) },
                            border = BorderStroke(1.5.dp, appColors.primaryAccent),
                            shape = RoundedCornerShape(8.dp),
                            modifier = if (currentUserRole == "USER") Modifier.fillMaxWidth() else Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Export icon",
                                tint = appColors.primaryAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = translate("Export CSV", language),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = appColors.primaryAccent
                            )
                        }

                        if (currentUserRole != "USER") {
                            // Import CSV Button
                            Button(
                                onClick = {
                                    groupToImportInto = group
                                    csvPickerLauncher.launch("*/*")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = appColors.primaryAccent),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileUpload,
                                    contentDescription = "Import icon",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = translate("Import CSV", language),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        if (currentUserRole != "USER") {
            Spacer(modifier = Modifier.height(4.dp))

            // Reset Card at bottom
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5F5)),
                border = BorderStroke(1.dp, Color(0xFFFEB2B2)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = translate("🚨 Danger Zone", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.Red
                    )
                    Text(
                        text = translate("Wiping local database clears all customer ledgers, weekly schedules, edit logs, and interest records permanently.", language),
                        fontSize = 11.sp,
                        color = Color.DarkGray,
                        lineHeight = 15.sp
                    )
                    Button(
                        onClick = { showDeleteAllDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(translate("Wipe All Local Ledger Data", language), fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
