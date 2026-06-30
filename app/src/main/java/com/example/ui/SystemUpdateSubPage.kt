package com.example.ui

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.network.FirebaseUpdateManager
import com.example.network.UpdateStatus
import androidx.compose.ui.platform.testTag

@Composable
fun SystemUpdateSubPage(
    language: String,
    viewModel: FinanceViewModel,
    appColors: AppThemeColors,
    context: Context
) {
    val autoUpdateEnabled by viewModel.autoUpdateEnabled.collectAsStateWithLifecycle()
    val forceUpdateEnabled by viewModel.forceUpdateEnabled.collectAsStateWithLifecycle()
    val pauseUpdatesEnabled by viewModel.pauseUpdatesEnabled.collectAsStateWithLifecycle()

    // Observe Firebase update states
    val updateStatus by FirebaseUpdateManager.updateStatus.collectAsStateWithLifecycle()
    val latestVersionCode by FirebaseUpdateManager.latestVersionCode.collectAsStateWithLifecycle()
    val latestVersionName by FirebaseUpdateManager.latestVersionName.collectAsStateWithLifecycle()
    val updateError by FirebaseUpdateManager.updateError.collectAsStateWithLifecycle()

    val currentVersionCode = try {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pInfo.longVersionCode
        } else {
            pInfo.versionCode.toLong()
        }
    } catch (e: Exception) {
        1L
    }

    val currentVersionName = try {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        pInfo.versionName ?: "1.0.0"
    } catch (e: Exception) {
        "1.0.0"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Version Info & Update Status Box (MANDATORY BOX WITH STUDYING VERSION NAME, CODE, ETC.)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth().testTag("version_metadata_box")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = translate("Application Version Details", language),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            text = translate("Current details of the installed application package", language),
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = appColors.primaryAccent,
                        modifier = Modifier.size(28.dp)
                    )
                }

                HorizontalDivider(color = Color(0xFFE2E8F0))

                // Grid layout for Version Code & Name
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Version Name box
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = translate("Version Name", language),
                                fontSize = 11.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentVersionName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = appColors.primaryAccent
                            )
                        }
                    }

                    // Version Code box
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = translate("Version Code", language),
                                fontSize = 11.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "#$currentVersionCode",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = appColors.primaryAccent
                            )
                        }
                    }
                }

                // Dynamic Live Update Status Panel (checking, downloading, downloaded, etc.)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    when (updateStatus) {
                        UpdateStatus.CHECKING -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = appColors.primaryAccent,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = translate("Checking for new update on Firebase Cloud...", language),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = appColors.primaryAccent
                                )
                            }
                        }
                        UpdateStatus.DOWNLOADING -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFFF59E0B),
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = translate("Downloading update version v$latestVersionCode... Please wait.", language),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFD97706)
                                )
                            }
                        }
                        UpdateStatus.DOWNLOADED -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDone,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = translate("New update fully downloaded & ready!", language),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF047857)
                                    )
                                }
                                Button(
                                    onClick = { FirebaseUpdateManager.triggerInstall(context, latestVersionCode) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = translate("INSTALL NOW", language),
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                        UpdateStatus.UPDATE_AVAILABLE -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        tint = Color(0xFF3B82F6),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = translate("New version available: v$latestVersionCode (Name: $latestVersionName)", language),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1D4ED8)
                                    )
                                }
                            }
                        }
                        UpdateStatus.UP_TO_DATE -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = translate("Your application is up to date!", language),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF047857)
                                )
                            }
                        }
                        UpdateStatus.FAILED -> {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = translate("Update query or download failed.", language),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Red
                                )
                                if (!updateError.isNullOrEmpty()) {
                                    Text(
                                        text = updateError ?: "",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                        else -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = translate("Standby / Idle", language),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.DarkGray
                                )
                            }
                        }
                    }
                }
            }
        }

        // Manual Check Button Card
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
                    text = translate("Check for Updates Manually", language),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.Black
                )
                Text(
                    text = translate("Instantly query Firebase RTDB for any newer APK releases and start automated installation with secure rollback backup.", language),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Button(
                    onClick = {
                        Toast.makeText(context, "Contacting Firebase Realtime Database...", Toast.LENGTH_SHORT).show()
                        FirebaseUpdateManager.checkForCloudUpdates(context, manualCheck = true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = appColors.primaryAccent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("check_updates_manual_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Check",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = translate("CHECK FOR UPDATES NOW", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }
        }

        // Settings Toggles Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = translate("OTA Auto-Update Parameters", language),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.Black
                )

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // Toggle 1: Auto-Update Enabled
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = translate("Auto Download & Install", language),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                        Text(
                            text = translate("Periodically checks and initiates background download when updates exist.", language),
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = autoUpdateEnabled,
                        onCheckedChange = { viewModel.setAutoUpdateEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = appColors.primaryAccent
                        ),
                        modifier = Modifier.testTag("auto_update_enabled_switch")
                    )
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // Toggle 2: Force Updates
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = translate("Force Updates", language),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                        Text(
                            text = translate("Aggressively request installation immediately upon new release detection.", language),
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = forceUpdateEnabled,
                        onCheckedChange = { viewModel.setForceUpdateEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = appColors.primaryAccent
                        ),
                        modifier = Modifier.testTag("force_update_enabled_switch")
                    )
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // Toggle 3: Pause All Updates
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = translate("Pause All Updates", language),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                        Text(
                            text = translate("Temporarily halt background checks and automated OTA downloads.", language),
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = pauseUpdatesEnabled,
                        onCheckedChange = { viewModel.setPauseUpdatesEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = appColors.primaryAccent
                        ),
                        modifier = Modifier.testTag("pause_updates_enabled_switch")
                    )
                }
            }
        }
    }
}
