package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSetupScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val language by viewModel.language.collectAsStateWithLifecycle()
    val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
    val colors = getThemeColors(selectedTheme)
    val isExportImportLoading by viewModel.isExportImportLoading.collectAsStateWithLifecycle()

    var selectedRole by remember { mutableStateOf("ADMIN") } // "ADMIN" = Main Device, "USER" = Additional Device
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // CSV File picker launcher
    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            selectedFileName = getFileNameFromUri(context, uri)
            errorMsg = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.mainBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Icon / Header
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colors.primaryAccent.copy(alpha = 0.1f),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.SettingsSuggest,
                        contentDescription = "Setup Icon",
                        tint = colors.primaryAccent,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Title
            Text(
                text = translate("Device Setup Required", language),
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.darkBg,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("device_setup_title")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = translate("Pick Device Role & Setup", language),
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 1. Selector Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Card A: Main Device
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            selectedRole = "ADMIN"
                            errorMsg = null
                        }
                        .testTag("setup_main_device_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedRole == "ADMIN") colors.primaryAccent.copy(alpha = 0.08f) else Color.White
                    ),
                    border = BorderStroke(
                        width = 2.dp,
                        color = if (selectedRole == "ADMIN") colors.primaryAccent else Color.Gray.copy(alpha = 0.2f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        RadioButton(
                            selected = selectedRole == "ADMIN",
                            onClick = {
                                selectedRole = "ADMIN"
                                errorMsg = null
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = colors.primaryAccent),
                            modifier = Modifier.testTag("setup_main_device_radio")
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = "Main Device Icon",
                            tint = if (selectedRole == "ADMIN") colors.primaryAccent else Color.Gray,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = translate("Main Device (Admin)", language),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.darkBg,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Card B: Additional Device
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            selectedRole = "USER"
                            errorMsg = null
                        }
                        .testTag("setup_additional_device_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedRole == "USER") colors.primaryAccent.copy(alpha = 0.08f) else Color.White
                    ),
                    border = BorderStroke(
                        width = 2.dp,
                        color = if (selectedRole == "USER") colors.primaryAccent else Color.Gray.copy(alpha = 0.2f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        RadioButton(
                            selected = selectedRole == "USER",
                            onClick = {
                                selectedRole = "USER"
                                errorMsg = null
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = colors.primaryAccent),
                            modifier = Modifier.testTag("setup_additional_device_radio")
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Icon(
                            imageVector = Icons.Default.TabletAndroid,
                            contentDescription = "Additional Device Icon",
                            tint = if (selectedRole == "USER") colors.primaryAccent else Color.Gray,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = translate("Additional Device (Read-Only)", language),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.darkBg,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 2. Explanations Area
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    if (selectedRole == "ADMIN") {
                        Text(
                            text = translate("Main Device: Import backup spreadsheet to init database, synchronizing to cloud database.", language),
                            fontSize = 13.sp,
                            color = Color(0xFF475569),
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Upload button area
                        Button(
                            onClick = { csvPickerLauncher.launch("*/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.secondaryAccent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setup_select_file_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = "Select file",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = translate("Select Ledger Backup File", language),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (selectedFileName != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.primaryAccent.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = selectedFileName ?: "",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.darkBg,
                                    modifier = Modifier.testTag("setup_selected_file_name")
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = translate("No local backup file selected. Setup will try to restore from Cloud or start fresh (in test mode).", language),
                                fontSize = 12.sp,
                                color = Color(0xFF475569),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Text(
                            text = translate("Additional Device: Access accounts in read-only mode directly from cloud database.", language),
                            fontSize = 13.sp,
                            color = Color(0xFF475569),
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (errorMsg != null) {
                Text(
                    text = errorMsg ?: "",
                    color = Color.Red,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // 3. Confirm trigger Button
            Button(
                onClick = {
                    viewModel.completeDeviceSetup(
                        role = selectedRole,
                        context = context,
                        uriString = selectedFileUri?.toString(),
                        onSuccess = {
                            Toast.makeText(context, translate("Device Setup Complete!", language), Toast.LENGTH_LONG).show()
                        },
                        onError = { err ->
                            errorMsg = err
                        }
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("setup_confirm_button")
            ) {
                Text(
                    text = translate("Start Setup", language),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }

        // Blocking Loading indicator
        if (isExportImportLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        CircularProgressIndicator(
                            color = colors.primaryAccent,
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = translate("Importing and Overwriting Master Cloud database...", language),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryAccent,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// Open Display Name Resolver Helper
fun getFileNameFromUri(context: android.content.Context, uri: android.net.Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "backup_ledger.csv"
}
