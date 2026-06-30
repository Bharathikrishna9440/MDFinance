package com.example.ui

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.network.FirebaseAnalyticsManager
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.ui.platform.testTag

@Composable
fun FirebaseDashboardSubPage(
    language: String,
    viewModel: FinanceViewModel,
    appColors: AppThemeColors,
    context: Context
) {
    val scrollState = rememberScrollState()

    // Remote config states
    val welcomeMessage by com.example.network.FirebaseRemoteConfigManager.welcomeMessage.collectAsStateWithLifecycle()
    val defaultInterestRate by com.example.network.FirebaseRemoteConfigManager.defaultInterestRate.collectAsStateWithLifecycle()
    val enableUpiFeatures by com.example.network.FirebaseRemoteConfigManager.enableUpiFeatures.collectAsStateWithLifecycle()
    val lastFetchTime by com.example.network.FirebaseRemoteConfigManager.lastFetchTime.collectAsStateWithLifecycle()

    // FCM States
    var fcmToken by remember { mutableStateOf("") }
    var isFetchingToken by remember { mutableStateOf(false) }

    // Analytics state
    var customEventName by remember { mutableStateOf("") }
    var lastLoggedEvent by remember { mutableStateOf<String?>(null) }

    // Load FCM Token
    LaunchedEffect(Unit) {
        isFetchingToken = true
        try {
            // First check preference cache
            var token = com.example.network.MyFirebaseMessagingService.getSavedFcmToken(context)
            if (token.isBlank()) {
                // Fetch directly from FCM SDK
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    isFetchingToken = false
                    if (task.isSuccessful) {
                        fcmToken = task.result ?: ""
                    }
                }
            } else {
                fcmToken = token
                isFetchingToken = false
            }
        } catch (e: Exception) {
            isFetchingToken = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Core Banner Info
        Card(
            colors = CardDefaults.cardColors(containerColor = appColors.primaryAccent.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, appColors.primaryAccent.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = "Cloud Setup",
                        tint = appColors.primaryAccent
                    )
                    Text(
                        text = "Firebase Unified Operations Control",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.Black
                    )
                }
                Text(
                    text = "This dashboard houses live integrations of Firebase Analytics, In-App Messaging, Remote Config, Cloud Messaging, and Crashlytics diagnostics for real-time monitoring.",
                    fontSize = 12.sp,
                    color = Color.DarkGray
                )
            }
        }

        // Firebase Auth & RTDB Connection Status Card
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
                    text = "Cloud Ledger Connection Status",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.Black
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auth Status:", fontSize = 12.sp, color = Color.Gray)
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    val authText = if (currentUser != null) {
                        "Signed In (${currentUser.email ?: "Anonymous"})"
                    } else {
                        "Not Signed In"
                    }
                    Text(
                        text = authText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (currentUser != null) Color(0xFF16A34A) else Color(0xFFDC2626)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("RTDB Endpoint:", fontSize = 12.sp, color = Color.Gray)
                    val rtdbUrl = com.example.util.SecureConfig.firebaseDatabaseUrl.take(28) + "..."
                    Text(
                        text = rtdbUrl,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.DarkGray
                    )
                }
            }
        }

        // Firebase Remote Config Live Status
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
                    Text(
                        text = "Firebase Remote Config Params",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.Black
                    )
                    IconButton(
                        onClick = {
                            com.example.network.FirebaseRemoteConfigManager.initializeAndFetch()
                            Toast.makeText(context, "Force fetched Remote Config!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync Config",
                            tint = appColors.primaryAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Text(
                    text = "Live configuration settings updated dynamically over the air:",
                    fontSize = 11.sp,
                    color = Color.Gray
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Welcome Prompt:", fontSize = 12.sp, color = Color.DarkGray)
                        Text(welcomeMessage, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Default Interest:", fontSize = 12.sp, color = Color.DarkGray)
                        Text("$defaultInterestRate%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("UPI Features Enabled:", fontSize = 12.sp, color = Color.DarkGray)
                        Text(
                            text = if (enableUpiFeatures) "YES" else "NO",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (enableUpiFeatures) Color(0xFF16A34A) else Color(0xFFDC2626)
                        )
                    }

                    if (lastFetchTime > 0) {
                        val dateStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(lastFetchTime))
                        Text(
                            text = "Last sync timestamp: $dateStr",
                            fontSize = 10.sp,
                            color = Color.Gray,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }
        }

        // Firebase Cloud Messaging (FCM) Card
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
                    text = "Firebase Cloud Messaging (FCM) Device Token",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.Black
                )

                Text(
                    text = "Copy this registration token to push direct test notifications from your Firebase console to this physical device:",
                    fontSize = 11.sp,
                    color = Color.Gray
                )

                if (isFetchingToken) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.CenterHorizontally),
                        color = appColors.primaryAccent
                    )
                } else if (fcmToken.isNotBlank()) {
                    OutlinedTextField(
                        value = fcmToken,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = android.content.ClipData.newPlainText("fcm_token", fcmToken)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "FCM Token copied!", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy FCM Token",
                                    tint = appColors.primaryAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    )
                } else {
                    Text(
                        text = "FCM registration token pending. Click below to regenerate.",
                        fontSize = 11.sp,
                        color = Color.Red
                    )
                }

                Button(
                    onClick = {
                        isFetchingToken = true
                        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                            isFetchingToken = false
                            if (task.isSuccessful) {
                                fcmToken = task.result ?: ""
                                Toast.makeText(context, "FCM Token refreshed successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "FCM Token refresh failed.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("FORCE REFRESH DEVICE TOKEN", fontSize = 11.sp, color = Color.DarkGray, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Firebase Analytics & In-App Messaging Custom event logger
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
                    text = "Firebase Analytics & In-App Message Triggers",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.Black
                )
                Text(
                    text = "Log custom testing events directly to Firebase Analytics. If you configure In-App messages triggered by specific event campaigns, firing them here will launch the visual overlay prompt instantly.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )

                OutlinedTextField(
                    value = customEventName,
                    onValueChange = { customEventName = it },
                    label = { Text("Custom Event Name") },
                    placeholder = { Text("e.g. user_signed_up, campaign_trigger") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("custom_analytics_event_input")
                )

                Button(
                    onClick = {
                        if (customEventName.isNotBlank()) {
                            val cleanName = customEventName.trim().replace("\\s+".toRegex(), "_")
                            FirebaseAnalyticsManager.logEvent(cleanName)
                            lastLoggedEvent = cleanName
                            customEventName = ""
                            Toast.makeText(context, "Logged Event: $cleanName to Firebase Analytics!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Event name cannot be blank.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = appColors.primaryAccent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("log_custom_event_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Insights,
                        contentDescription = "Analytics Log",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("LOG EVENT NOW", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                lastLoggedEvent?.let { name ->
                    Text(
                        text = "Last successfully logged: \"$name\"",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF16A34A)
                    )
                }
            }
        }

        // Firebase Crashlytics Diagnostic Card
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
                    text = "Firebase Crashlytics Diagnostic Tools",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.Black
                )
                Text(
                    text = "Verify your Firebase Crashlytics installation by reporting mock exceptions or initiating a simulated crash (the app will close and report the stack trace on the next launch).",
                    fontSize = 11.sp,
                    color = Color.Gray
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            try {
                                throw RuntimeException("Diagnostics mock exception reported to Firebase Crashlytics.")
                            } catch (e: Exception) {
                                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e)
                                Toast.makeText(context, "Non-fatal reported to Crashlytics!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).testTag("log_non_fatal_crashlytics_btn")
                    ) {
                        Text("REPORT NON-FATAL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Button(
                        onClick = {
                            // Intentionally throw uncaught exception to test real-world Crashlytics collection
                            Toast.makeText(context, "Initiating simulated fatal crash...", Toast.LENGTH_SHORT).show()
                            throw RuntimeException("Simulated App Crash: Firebase Crashlytics is successfully verified!")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).testTag("trigger_fatal_crash_btn")
                    ) {
                        Text("TRIGGER CRASH", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
