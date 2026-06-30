package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.testTag

@Composable
fun GoogleContactsSyncSubPage(
    language: String,
    viewModel: FinanceViewModel,
    appColors: AppThemeColors,
    context: Context
) {
    val syncEnabled by viewModel.googleContactsSyncEnabled.collectAsStateWithLifecycle()
    val selectedAccount by viewModel.googleContactsSelectedAccount.collectAsStateWithLifecycle()
    val accountsList by viewModel.googleContactsAccountsList.collectAsStateWithLifecycle()

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val writeOk = permissions[Manifest.permission.WRITE_CONTACTS] == true
        val readOk = permissions[Manifest.permission.READ_CONTACTS] == true
        permissionGranted = writeOk && readOk
        if (permissionGranted) {
            viewModel.fetchGoogleAccounts()
            Toast.makeText(context, "Contacts permissions granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permissions denied. Cannot sync contacts.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (permissionGranted) {
            viewModel.fetchGoogleAccounts()
        }
    }

    var showDropdown by remember { mutableStateOf(false) }
    var manualAccountEmail by remember { mutableStateOf(selectedAccount) }

    // Sync progress tracking states
    var isSyncingAll by remember { mutableStateOf(false) }
    var syncProgressCurrent by remember { mutableStateOf(0) }
    var syncProgressTotal by remember { mutableStateOf(0) }
    var syncResultMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Permission Status Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (permissionGranted) Color(0xFFF0FDF4) else Color(0xFFFEF2F2)
            ),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, if (permissionGranted) Color(0xFFBBF7D0) else Color(0xFFFCA5A5)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (permissionGranted) Icons.Default.CheckCircle else Icons.Default.Info,
                    contentDescription = "Permission Status",
                    tint = if (permissionGranted) Color(0xFF16A34A) else Color(0xFFDC2626),
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (permissionGranted) translate("Contacts Permission Active", language) else translate("Contacts Permission Required", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (permissionGranted) Color(0xFF15803D) else Color(0xFF991B1B)
                    )
                    Text(
                        text = if (permissionGranted) translate("The app has secure system access to update your contact books.", language) else translate("Please grant read/write permissions to synchronize client details directly to your account contacts.", language),
                        fontSize = 12.sp,
                        color = if (permissionGranted) Color(0xFF166534) else Color(0xFF7F1D1D)
                    )
                }
                if (!permissionGranted) {
                    Button(
                        onClick = {
                            launcher.launch(
                                arrayOf(
                                    Manifest.permission.READ_CONTACTS,
                                    Manifest.permission.WRITE_CONTACTS,
                                    Manifest.permission.GET_ACCOUNTS
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("request_contacts_permission_btn")
                    ) {
                        Text(
                            text = translate("GRANT", language),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Feature Toggle Card
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = translate("Enable Contacts Sync", language),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.Black
                        )
                        Text(
                            text = translate("When enabled, creating or editing client mobile numbers automatically pushes correct details to Google Contacts.", language),
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = syncEnabled,
                        onCheckedChange = {
                            if (!permissionGranted && it) {
                                Toast.makeText(context, "Grant permissions first to enable syncing.", Toast.LENGTH_LONG).show()
                            } else {
                                viewModel.setGoogleContactsSyncEnabled(it)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = appColors.primaryAccent
                        ),
                        modifier = Modifier.testTag("contacts_sync_enabled_switch")
                    )
                }

                if (syncEnabled) {
                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    Text(
                        text = translate("Select Google Sync Account", language),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color.DarkGray
                    )

                    if (accountsList.isNotEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedCard(
                                onClick = { showDropdown = true },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                modifier = Modifier.fillMaxWidth().testTag("google_account_dropdown_trigger")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = selectedAccount.ifBlank { translate("Select Google Account", language) },
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (selectedAccount.isBlank()) Color.Gray else Color.Black
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Dropdown"
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showDropdown,
                                onDismissRequest = { showDropdown = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                accountsList.forEach { account ->
                                    DropdownMenuItem(
                                        text = { Text(account, fontSize = 14.sp) },
                                        onClick = {
                                            viewModel.setGoogleContactsSelectedAccount(account)
                                            manualAccountEmail = account
                                            showDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Manual Email input fallback
                    OutlinedTextField(
                        value = manualAccountEmail,
                        onValueChange = {
                            manualAccountEmail = it
                            viewModel.setGoogleContactsSelectedAccount(it)
                        },
                        label = { Text(translate("Google Account Email Address", language)) },
                        placeholder = { Text("e.g. name@gmail.com") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("google_account_manual_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.fetchGoogleAccounts()
                                Toast.makeText(context, "Refreshed Google accounts list.", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("refresh_google_accounts_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(translate("Refresh Accounts List", language), fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Full Sync Command Card
        if (syncEnabled && selectedAccount.isNotBlank()) {
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
                        text = translate("Manual Full Database Sync", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.Black
                    )
                    Text(
                        text = translate("Sync all existing clients with active phone numbers directly to Google Contacts now. This resolves any unsynced names or newly updated telephone entries.", language),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    if (isSyncingAll) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val progress = if (syncProgressTotal > 0) syncProgressCurrent.toFloat() / syncProgressTotal else 0f
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = appColors.primaryAccent,
                                trackColor = Color(0xFFE2E8F0)
                            )
                            Text(
                                text = "Syncing contact $syncProgressCurrent of $syncProgressTotal...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = appColors.primaryAccent
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                isSyncingAll = true
                                syncResultMessage = null
                                viewModel.syncAllBorrowersToGoogleContacts(
                                    context = context,
                                    onProgress = { current, total ->
                                        syncProgressCurrent = current
                                        syncProgressTotal = total
                                    },
                                    onComplete = { success, msg ->
                                        isSyncingAll = false
                                        syncResultMessage = msg
                                    }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = appColors.primaryAccent),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("sync_all_contacts_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync Now",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = translate("SYNC ALL BORROWERS NOW", language),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                        }
                    }

                    syncResultMessage?.let { msg ->
                        Text(
                            text = msg,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (msg.contains("success", ignoreCase = true) || msg.contains("synced", ignoreCase = true)) Color(0xFF16A34A) else Color(0xFFDC2626)
                        )
                    }
                }
            }
        }
    }
}
