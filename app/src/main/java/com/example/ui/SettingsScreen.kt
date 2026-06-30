package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: FinanceViewModel) {
    val context = LocalContext.current
    val appColors = LocalAppThemeColors.current
    val language by viewModel.language.collectAsStateWithLifecycle()
    val upiId by viewModel.upiId.collectAsStateWithLifecycle()
    val upiLink by viewModel.upiLink.collectAsStateWithLifecycle()
    val qrImageUri by viewModel.qrImageUri.collectAsStateWithLifecycle()
    val businessName by viewModel.businessName.collectAsStateWithLifecycle()
    
    val qrPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.saveSelectedQrCode(context, uri)
        }
    }

    val simSelection by viewModel.simSelection.collectAsStateWithLifecycle()
    val collectionGroups by viewModel.collectionGroups.collectAsStateWithLifecycle()
    val allCustomers by viewModel.allCustomers.collectAsStateWithLifecycle()
    val smsPaused by viewModel.smsPaused.collectAsStateWithLifecycle()
    val smsReaderPaused by viewModel.smsReaderPaused.collectAsStateWithLifecycle()
    val autoEntryPassing by viewModel.autoEntryPassing.collectAsStateWithLifecycle()
    val upiLinkSharing by viewModel.upiLinkSharing.collectAsStateWithLifecycle()

    val currentUsername by viewModel.username.collectAsStateWithLifecycle()
    val currentUserRole by viewModel.currentUserRole.collectAsStateWithLifecycle()

    var dayToDeleteTarget by remember { mutableStateOf<String?>(null) }
    var showDeleteDayDialog by remember { mutableStateOf(false) }
    var deleteDayActionChoice by remember { mutableStateOf("TRANSFER") } // "TRANSFER" or "BULK_DELETE"
    var transferGroupSelected by remember { mutableStateOf("") }

    var showRenameDayDialog by remember { mutableStateOf(false) }
    var dayToRenameTarget by remember { mutableStateOf<String?>(null) }
    var renameDayNewName by remember { mutableStateOf("") }

    val targetGroupCustomerCount = remember(dayToDeleteTarget, allCustomers) {
        val target = dayToDeleteTarget ?: ""
        allCustomers.count { it.collectionDay.trim().equals(target.trim(), ignoreCase = true) }
    }

    var showResetSettingsDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }

    val smsNewLoanTemplate by viewModel.smsNewLoanTemplate.collectAsStateWithLifecycle()
    val smsPaymentTemplate by viewModel.smsPaymentTemplate.collectAsStateWithLifecycle()
    val smsReminderTemplate by viewModel.smsReminderTemplate.collectAsStateWithLifecycle()
    val whatsappReminderTemplate by viewModel.whatsappReminderTemplate.collectAsStateWithLifecycle()
    val currentTemplateLang by viewModel.currentTemplateLanguage.collectAsStateWithLifecycle()

    var customGroupName by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    var activeSubPage by remember { mutableStateOf<String?>(null) }

    // Sync states removed

    val fontSizeScale by viewModel.fontSizeScale.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val msgCompilingBackup = translate("Generating and transmitting YAML snapshot to Cloud Backup...", language)
    val msgBackupUploaded = translate("Cloud YAML Ledger backup uploaded successfully!", language)
    val msgBackupUploadFailed = translate("Cloud YAML Backup upload failed. Check App Script configuration or network connection.", language)

    val appsScriptCode = ""

    val modifierScroll = if (activeSubPage == "audit") {
        Modifier.fillMaxSize()
    } else {
        Modifier.fillMaxSize().verticalScroll(scrollState)
    }

    Column(
        modifier = modifierScroll.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (activeSubPage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { activeSubPage = null },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = translate("Back", language),
                    tint = ColorSlateDark,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = when (activeSubPage) {
                        "language" -> translate("App Language", language)
                        "font" -> translate("App Font Size Option", language)
                        "business" -> "Business Profile & UPI"
                        "groups" -> "Collection Groups (Days)"
                        "sim" -> "SIM Card Preference"
                        "automation" -> "Ledger & SMS Automation"
                        "templates" -> "Message & Reminder Templates"
                        "backup" -> translate("App Data Backup & Restore (JSON)", language)
                        "reset" -> "System Reset & Maintenance"
                        "audit" -> translate("System Audit Logs", language)
                        "users_management" -> translate("User Account Management", language)
                        "system_update" -> translate("System Auto-Update Settings", language)
                        "contacts_sync" -> translate("Google Contacts Sync Settings", language)
                        "firebase_dashboard" -> "Firebase Operations & Dashboard"
                        else -> translate("Settings", language)
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = ColorSlateDark
                )
            }
            HorizontalDivider(color = Color(0xFFCBD5E1))
        } else {
            // Main title row and logo removed as requested
        }

        if (activeSubPage == null) {
            // 1. Offline Mode Main Toggle
            val offlineModeEnabled by viewModel.offlineModeEnabled.collectAsStateWithLifecycle()
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("settings_offline_mode_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFFEE2E2), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = translate("Offline Mode", language),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = ColorSlateDark
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = translate("Disconnect Firebase, Drive & AI network syncs", language),
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = offlineModeEnabled,
                        onCheckedChange = { viewModel.setOfflineModeEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFDC2626)
                        ),
                        modifier = Modifier.testTag("settings_offline_mode_switch")
                    )
                }
            }

            // 2. AI (Easwar) Enable/Disable Toggle
            val aiEnabled by viewModel.aiEnabled.collectAsStateWithLifecycle()
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("settings_ai_enabled_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFE0F2FE), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = translate("Easwar AI Assistant", language),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = ColorSlateDark
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = translate("Enable/Disable Easwar AI chatbot in top ribbon", language),
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = aiEnabled,
                        onCheckedChange = { viewModel.setAiEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF0284C7)
                        ),
                        modifier = Modifier.testTag("settings_ai_enabled_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsMenuCard(
                title = translate("App Language", language),
                subtitle = translate("English / Tamil option selection", language),
                iconBgColor = Color(0xFFF1F5F9),
                customIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "A",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "அ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            modifier = Modifier.padding(start = 1.dp)
                        )
                    }
                },
                onClick = { activeSubPage = "language" },
                testTag = "settings_language_btn"
            )

            SettingsMenuCard(
                title = translate("App Font Size Option", language),
                subtitle = translate("Normal, Medium, Large, Extra Large text choices", language),
                iconBgColor = Color(0xFFF1F5F9),
                customIcon = {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "A",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "A",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            modifier = Modifier.padding(horizontal = 1.dp)
                        )
                        Text(
                            text = "A",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }
                },
                onClick = { activeSubPage = "font" },
                testTag = "settings_font_btn"
            )

            SettingsMenuCard(
                title = "Business Profile",
                subtitle = "Manage merchant identification and notification footer text",
                icon = Icons.Default.Home,
                iconTint = Color(0xFF0F766E),
                iconBgColor = Color(0xFFCCFBF1),
                onClick = { activeSubPage = "business" },
                testTag = "settings_business_btn"
            )

            SettingsMenuCard(
                title = "Collection Groups (Days)",
                subtitle = "Reorder, rename and configure active day route circles",
                icon = Icons.Default.List,
                iconTint = Color(0xFF7C3AED),
                iconBgColor = Color(0xFFF3E8FF),
                onClick = { activeSubPage = "groups" },
                testTag = "settings_groups_btn"
            )

            SettingsMenuCard(
                title = "SIM Card Preference",
                subtitle = "Select outbound SMS transaction delivery slots",
                icon = Icons.Default.SimCard,
                iconTint = Color(0xFFD946EF),
                iconBgColor = Color(0xFFFDF4FF),
                onClick = { activeSubPage = "sim" },
                testTag = "settings_sim_btn"
            )

            SettingsMenuCard(
                title = "Message & Reminder Templates",
                subtitle = "Customize SMS, WhatsApp confirmations and dynamic tags",
                icon = Icons.Default.Email,
                iconTint = Color(0xFFEA580C),
                iconBgColor = Color(0xFFFFEDD5),
                onClick = { activeSubPage = "templates" },
                testTag = "settings_templates_btn"
            )

            SettingsMenuCard(
                title = translate("Local Data Backups & Restore", language),
                subtitle = translate("Export or import local JSON/YAML backup files", language),
                icon = Icons.Default.ImportExport,
                iconTint = Color(0xFF2563EB),
                iconBgColor = Color(0xFFDBEAFE),
                onClick = { activeSubPage = "backup" },
                testTag = "settings_backup_btn"
            )

            SettingsMenuCard(
                title = translate("System Audit Logs", language),
                subtitle = translate("View internal database event logs and change history", language),
                icon = Icons.Default.History,
                iconTint = Color(0xFF475569),
                iconBgColor = Color(0xFFF1F5F9),
                onClick = { activeSubPage = "audit" },
                testTag = "settings_audit_btn"
            )

            SettingsMenuCard(
                title = "Recalibrate All Calculations",
                subtitle = "Force fresh live calculations and verify outstanding balances for all borrow records",
                icon = Icons.Default.Calculate,
                iconTint = Color(0xFF0284C7),
                iconBgColor = Color(0xFFE0F2FE),
                onClick = {
                    viewModel.triggerDatabaseRescanAndRepair()
                    Toast.makeText(context, "Calculations successfully recalibrated from live receipt logs!", Toast.LENGTH_SHORT).show()
                },
                testTag = "settings_recalibrate_btn"
            )

            SettingsMenuCard(
                title = "System Reset & Maintenance",
                subtitle = "Perform standard cleanups and system defaults restore",
                icon = Icons.Default.Refresh,
                iconTint = Color(0xFFDC2626),
                iconBgColor = Color(0xFFFEE2E2),
                onClick = { activeSubPage = "reset" },
                testTag = "settings_reset_btn"
            )

            SettingsMenuCard(
                title = translate("Device Type", language),
                subtitle = translate("Switch between Main Device and Additional Device", language),
                icon = Icons.Default.AccountBox,
                iconTint = Color(0xFF8B5CF6),
                iconBgColor = Color(0xFFEDE9FE),
                onClick = { activeSubPage = "type_selection" },
                testTag = "settings_user_type_btn"
            )

            SettingsMenuCard(
                title = translate("System Auto-Update Settings", language),
                subtitle = translate("Check for OTA updates, toggle auto-update, pause, and force settings", language),
                icon = Icons.Default.SystemUpdate,
                iconTint = Color(0xFF10B981),
                iconBgColor = Color(0xFFD1FAE5),
                onClick = { activeSubPage = "system_update" },
                testTag = "settings_system_update_btn"
            )

            SettingsMenuCard(
                title = translate("Google Contacts Sync Settings", language),
                subtitle = translate("Sync borrower contact information directly to selected Google account", language),
                icon = Icons.Default.ContactPage,
                iconTint = Color(0xFF3B82F6),
                iconBgColor = Color(0xFFDBEAFE),
                onClick = { activeSubPage = "contacts_sync" },
                testTag = "settings_contacts_sync_btn"
            )

            SettingsMenuCard(
                title = "Firebase Unified Operations",
                subtitle = "Manage live Analytics, In-App Campaigns, Remote Config, Push Alerts & Diagnostics",
                icon = Icons.Default.Cloud,
                iconTint = Color(0xFFF59E0B),
                iconBgColor = Color(0xFFFEF3C7),
                onClick = { activeSubPage = "firebase_dashboard" },
                testTag = "settings_firebase_dashboard_btn"
            )

            SettingsMenuCard(
                title = translate("Sign Out", language),
                subtitle = translate("Disconnect and sign out of this session", language),
                icon = Icons.Default.Logout,
                iconTint = Color(0xFFEF4444),
                iconBgColor = Color(0xFFFEE2E2),
                onClick = { showLogoutConfirmDialog = true },
                testTag = "settings_sign_out_btn"
            )
        } else {
            when (activeSubPage) {
                "language" -> LanguageSubPage(language = language, viewModel = viewModel, appColors = appColors)
                "font" -> FontSizeSubPage(language = language, fontSizeScale = fontSizeScale, viewModel = viewModel, appColors = appColors)
                "business" -> BusinessUpiSubPage(
                    language = language,
                    viewModel = viewModel,
                    appColors = appColors,
                    businessName = businessName,
                    upiId = upiId,
                    upiLink = upiLink,
                    qrImageUri = qrImageUri,
                    qrPickerLauncher = qrPickerLauncher
                )
                "groups" -> CollectionGroupsSubPage(
                    language = language,
                    viewModel = viewModel,
                    appColors = appColors,
                    collectionGroups = collectionGroups,
                    customGroupName = customGroupName,
                    onCustomGroupNameChange = { customGroupName = it },
                    onRenameClick = {
                        dayToRenameTarget = it
                        renameDayNewName = it
                        showRenameDayDialog = true
                    },
                    onDeleteClick = {
                        dayToDeleteTarget = it
                        showDeleteDayDialog = true
                    }
                )
                "sim" -> SIMSubPage(
                    language = language,
                    viewModel = viewModel,
                    appColors = appColors,
                    simSelection = simSelection
                )
                "templates" -> TemplatesSubPage(
                    language = language,
                    viewModel = viewModel,
                    appColors = appColors,
                    smsPaused = smsPaused,
                    currentTemplateLang = currentTemplateLang,
                    smsNewLoanTemplate = smsNewLoanTemplate,
                    smsPaymentTemplate = smsPaymentTemplate,
                    smsReminderTemplate = smsReminderTemplate,
                    whatsappReminderTemplate = whatsappReminderTemplate
                )

                "backup" -> BackupSubPage(
                    language = language,
                    viewModel = viewModel,
                    appColors = appColors,
                    collectionGroups = collectionGroups,
                    context = context,
                    currentUserRole = currentUserRole
                )
                "reset" -> ResetSubPage(
                    language = language,
                    onResetTemplatesClick = { showResetSettingsDialog = true },
                    onResetAllClick = { showClearDataDialog = true },
                    appColors = appColors
                )
                "audit" -> {
                    HistoryScreen(
                        viewModel = viewModel,
                        paddingValues = PaddingValues(0.dp)
                    )
                }
                "type_selection" -> {
                    UserTypeSubPage(
                        language = language,
                        currentUserRole = currentUserRole,
                        viewModel = viewModel,
                        context = context,
                        appColors = appColors
                    )
                }
                "system_update" -> {
                    SystemUpdateSubPage(
                        language = language,
                        viewModel = viewModel,
                        appColors = appColors,
                        context = context
                    )
                }
                "contacts_sync" -> {
                    GoogleContactsSyncSubPage(
                        language = language,
                        viewModel = viewModel,
                        appColors = appColors,
                        context = context
                    )
                }
                "firebase_dashboard" -> {
                    FirebaseDashboardSubPage(
                        language = language,
                        viewModel = viewModel,
                        appColors = appColors,
                        context = context
                    )
                }
            }
        }
    }

    if (showResetSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showResetSettingsDialog = false },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.Black,
            title = { Text("Reset Settings & Templates?", color = appColors.primaryAccent) },
            text = { Text("This will reset all message templates, business name, language preferences, and default weekdays list back to the original factory starting settings. This will NOT delete any customer or transaction data.", color = Color.Black) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = appColors.primaryAccent),
                    onClick = {
                        viewModel.resetAllSettingsAndTemplates()
                        showResetSettingsDialog = false
                        Toast.makeText(context, "System settings reset to defaults!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Confirm Reset", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showResetSettingsDialog = false }
                ) {
                    Text("Cancel", color = Color.Black)
                }
            }
        )
    }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.Black,
            title = { Text("DANGER: WIPE DATABASE?", color = ColorLossRed) },
            text = { Text("This action is completely IRREVERSIBLE. It will permanently delete every single customer, loan cycle, payment history, and event logs from this phone storage. Export backup files first if you wish to preserve balances.", color = Color.Black) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = ColorLossRed),
                    onClick = {
                        viewModel.clearAllDatabaseData()
                        showClearDataDialog = false
                        Toast.makeText(context, "All user data deleted permanently!", Toast.LENGTH_LONG).show()
                    }
                ) {
                    Text("YES, WIPE ALL DATA", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showClearDataDialog = false }
                ) {
                    Text("Cancel", color = Color.Black)
                }
            }
        )
    }

    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.Black,
            title = { Text(translate("Confirm Logout", language), color = appColors.primaryAccent) },
            text = { Text(translate("Are you sure you want to sign out from the application?", language), color = Color.Black) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = appColors.primaryAccent),
                    onClick = {
                        showLogoutConfirmDialog = false
                        viewModel.logout(context)
                    }
                ) {
                    Text(translate("Yes, Logout", language), color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showLogoutConfirmDialog = false }
                ) {
                    Text(translate("Cancel", language), color = Color.Black)
                }
            }
        )
    }

    if (showDeleteDayDialog && dayToDeleteTarget != null) {
        val target = dayToDeleteTarget!!
        val count = targetGroupCustomerCount
        val otherGroups = collectionGroups.filter { !it.trim().equals(target.trim(), ignoreCase = true) }

        AlertDialog(
            onDismissRequest = { showDeleteDayDialog = false },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.Black,
            title = { Text("Delete Collection Group: $target?", color = ColorLossRed) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Are you sure you want to delete this collection group?",
                        fontSize = 14.sp,
                        color = Color.DarkGray
                    )

                    if (count > 0) {
                        Surface(
                            color = ColorLossRed.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Warning: There are $count active customer(s) registered under this group day.",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = ColorLossRed
                                )
                                Text(
                                    text = "Please choose what to do with their files:",
                                    fontSize = 12.sp,
                                    color = Color.DarkGray,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { deleteDayActionChoice = "TRANSFER" }
                                    .background(
                                        if (deleteDayActionChoice == "TRANSFER") Color(0xFFF1F5F9) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = deleteDayActionChoice == "TRANSFER",
                                    onClick = { deleteDayActionChoice = "TRANSFER" },
                                    colors = RadioButtonDefaults.colors(selectedColor = ColorAccentBlue)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Transfer Customers to another group", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ColorSlateDark)
                                    Text("Safe choice. No customer data or loan records will be deleted.", fontSize = 11.sp, color = Color.Gray)
                                }
                            }

                            if (deleteDayActionChoice == "TRANSFER") {
                                if (otherGroups.isNotEmpty()) {
                                    Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                                        var dropDownExpanded by remember { mutableStateOf(false) }
                                        if (transferGroupSelected.isEmpty() || !otherGroups.contains(transferGroupSelected)) {
                                            transferGroupSelected = otherGroups.first()
                                        }

                                        OutlinedButton(
                                            onClick = { dropDownExpanded = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(text = "Transfer to: $transferGroupSelected", color = Color.Black)
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Black)
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = dropDownExpanded,
                                            onDismissRequest = { dropDownExpanded = false }
                                        ) {
                                            otherGroups.forEach { otherGrp ->
                                                DropdownMenuItem(
                                                    text = { Text(otherGrp, color = Color.Black) },
                                                    onClick = {
                                                        transferGroupSelected = otherGrp
                                                        dropDownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "No other target group days exist to transfer. Create another group first.",
                                        fontSize = 11.sp,
                                        color = Color.Red,
                                        modifier = Modifier.padding(start = 16.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { deleteDayActionChoice = "BULK_DELETE" }
                                    .background(
                                        if (deleteDayActionChoice == "BULK_DELETE") Color(0xFFF1F5F9) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = deleteDayActionChoice == "BULK_DELETE",
                                    onClick = { deleteDayActionChoice = "BULK_DELETE" },
                                    colors = RadioButtonDefaults.colors(selectedColor = ColorLossRed)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Bulk delete all registered customers", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ColorLossRed)
                                    Text("DANGER Choice. Will wipe customer records and historical loan data for this collection day.", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = ColorLossRed),
                    onClick = {
                        val transferTo = if (deleteDayActionChoice == "TRANSFER" && otherGroups.isNotEmpty()) transferGroupSelected else null
                        if (transferTo != null) {
                            viewModel.deleteCollectionGroupAndTransferCustomers(target, transferTo)
                        } else {
                            viewModel.deleteCollectionGroupAndCustomers(target)
                        }
                        showDeleteDayDialog = false
                        dayToDeleteTarget = null
                        Toast.makeText(context, "Successfully deleted collection group $target", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Delete Group")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showDeleteDayDialog = false
                        dayToDeleteTarget = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRenameDayDialog && dayToRenameTarget != null) {
        val target = dayToRenameTarget!!
        AlertDialog(
            onDismissRequest = {
                showRenameDayDialog = false
                dayToRenameTarget = null
            },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.Black,
            title = { Text("Rename Group: $target?", color = Color.Black) },
            text = {
                OutlinedTextField(
                    value = renameDayNewName,
                    onValueChange = { renameDayNewName = it },
                    label = { Text("New Group Name", color = Color.Gray) },
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedBorderColor = ColorSlateDark,
                        unfocusedBorderColor = Color(0xFFCBD5E1),
                        focusedLabelColor = Color.DarkGray,
                        unfocusedLabelColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = appColors.primaryAccent),
                    onClick = {
                        val trimmed = renameDayNewName.trim()
                        if (trimmed.isNotBlank() && trimmed != target) {
                            viewModel.renameCollectionGroup(target, trimmed)
                            showRenameDayDialog = false
                            dayToRenameTarget = null
                            Toast.makeText(context, "Successfully renamed group day!", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showRenameDayDialog = false
                        dayToRenameTarget = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }


}

@Composable
fun SettingsMenuCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    customIcon: @Composable (() -> Unit)? = null,
    iconTint: Color? = null,
    iconBgColor: Color? = null,
    onClick: () -> Unit,
    testTag: String = ""
) {
    val appColors = LocalAppThemeColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bg = iconBgColor ?: appColors.primaryAccent.copy(alpha = 0.1f)
            val tint = iconTint ?: appColors.primaryAccent
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(bg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (customIcon != null) {
                    customIcon()
                } else if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = ColorSlateDark
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun UserTypeSubPage(
    language: String,
    currentUserRole: String,
    viewModel: FinanceViewModel,
    context: android.content.Context,
    appColors: AppThemeColors
) {
    var selectedRole by remember { mutableStateOf(currentUserRole) }
    val isDemoMode = viewModel.currentUser.value == "Demo"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = translate("Select your device access role:", language),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = ColorSlateDark
        )

        if (isDemoMode) {
            Surface(
                color = Color(0xFFFEF3C7),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = translate("Temporary demo sessions are locked to Main Device access.", language),
                    color = Color(0xFF92400E),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Card 1: Admin
        Card(
            onClick = { if (!isDemoMode) selectedRole = "ADMIN" },
            colors = CardDefaults.cardColors(
                containerColor = if (selectedRole == "ADMIN") appColors.primaryAccent.copy(alpha = 0.08f) else Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(
                1.5.dp,
                if (selectedRole == "ADMIN") appColors.primaryAccent else Color.Gray.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedRole == "ADMIN",
                    onClick = { if (!isDemoMode) selectedRole = "ADMIN" },
                    colors = RadioButtonDefaults.colors(selectedColor = appColors.primaryAccent),
                    enabled = !isDemoMode
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = translate("Main Device", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = ColorSlateDark
                    )
                    Text(
                        text = translate("Full read/write permissions. Edits are immediately written locally and synchronized live to the Realtime Database.", language),
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Card 2: User
        Card(
            onClick = { if (!isDemoMode) selectedRole = "USER" },
            colors = CardDefaults.cardColors(
                containerColor = if (selectedRole == "USER") appColors.primaryAccent.copy(alpha = 0.08f) else Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(
                1.5.dp,
                if (selectedRole == "USER") appColors.primaryAccent else Color.Gray.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedRole == "USER",
                    onClick = { if (!isDemoMode) selectedRole = "USER" },
                    colors = RadioButtonDefaults.colors(selectedColor = appColors.primaryAccent),
                    enabled = !isDemoMode
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = translate("Additional Device", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = ColorSlateDark
                    )
                    Text(
                        text = translate("Read-only access. Cannot make any local edits or record entries. Ledger updates are streamed in real time from the cloud.", language),
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Save Button
        Button(
            onClick = {
                viewModel.updateUserRole(selectedRole, context)
            },
            colors = ButtonDefaults.buttonColors(containerColor = appColors.primaryAccent),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !isDemoMode && selectedRole != currentUserRole
        ) {
            Text(
                text = translate("Apply and Save Role", language),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White
            )
        }
    }
}



