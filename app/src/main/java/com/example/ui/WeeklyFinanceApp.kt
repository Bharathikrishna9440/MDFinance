package com.example.ui

import android.net.Uri
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import java.io.File
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.ApiException
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.BorderStroke
import coil.compose.AsyncImage
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.example.data.Customer
import com.example.data.LoanCycle
import com.example.data.WeeklyPayment
import java.text.SimpleDateFormat
import java.util.*

fun showDateTimePicker(
    context: android.content.Context,
    currentMills: Long,
    onDateTimeSelected: (Long) -> Unit
) {
    val calendar = Calendar.getInstance().apply { timeInMillis = currentMills }
    try {
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                
                android.app.TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        calendar.set(Calendar.MINUTE, minute)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
                        onDateTimeSelected(calendar.timeInMillis)
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    false
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun shareStatementImageToWhatsapp(
    context: android.content.Context,
    bitmap: android.graphics.Bitmap,
    customerName: String,
    phoneNumber: String?
) {
    try {
        val safeName = customerName.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val file = java.io.File(context.cacheDir, "Statement_${safeName}.png")
        java.io.FileOutputStream(file).use { os ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, os)
        }

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        var cleanPhone = phoneNumber?.filter { it.isDigit() } ?: ""
        if (cleanPhone.isNotBlank()) {
            if (cleanPhone.length == 10) {
                cleanPhone = "91$cleanPhone"
            }
        }

        val text = "Outstanding statement for $customerName."

        // 1. Try WhatsApp
        val whatsappIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_TEXT, text)
            if (cleanPhone.isNotBlank()) {
                putExtra("jid", "$cleanPhone@s.whatsapp.net")
            }
            clipData = android.content.ClipData.newRawUri("", uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage("com.whatsapp")
        }

        // 2. Try WhatsApp Business
        val whatsappBusinessIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_TEXT, text)
            if (cleanPhone.isNotBlank()) {
                putExtra("jid", "$cleanPhone@s.whatsapp.net")
            }
            clipData = android.content.ClipData.newRawUri("", uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage("com.whatsapp.w4b")
        }

        // 3. Fallback generic sharing
        val genericIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_TEXT, text)
            clipData = android.content.ClipData.newRawUri("", uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(whatsappIntent)
        } catch (e1: Exception) {
            try {
                context.startActivity(whatsappBusinessIntent)
            } catch (e2: Exception) {
                try {
                    val chooser = android.content.Intent.createChooser(genericIntent, "Share with WhatsApp")
                    context.startActivity(chooser)
                } catch (e3: Exception) {
                    if (cleanPhone.isNotBlank()) {
                        val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=${android.net.Uri.encode(text)}"
                        val intentWeb = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        context.startActivity(intentWeb)
                    } else {
                        val url = "https://api.whatsapp.com/send?text=${android.net.Uri.encode(text)}"
                        val intentWeb = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        context.startActivity(intentWeb)
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        android.widget.Toast.makeText(context, "Failed to share: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

// Custom Vibrant Theme & Dynamic Palette Definitions:
data class AppThemeColors(
    val mainBg: Color = Color(0xFFF8FAFC),
    val darkBg: Color = Color(0xFF0F172A),
    val primaryAccent: Color = Color(0xFF4F46E5),
    val secondaryAccent: Color = Color(0xFF0D9488),
    val tertiaryAccent: Color = Color(0xFFEF4444),
    val gradientColors: List<Color> = listOf(Color(0xFF0F172A), Color(0xFF4F46E5)),
    val gainGreenLight: Color = Color(0xFFF0FDF4),
    val lossRedLight: Color = Color(0xFFFEF2F2),
    val isDark: Boolean = false,
    val textOnHeader: Color = Color.White,
    val headerCardBg: List<Color> = listOf(Color(0xFF0F172A), Color(0xFF4F46E5)),
    val headerCardBorder: Color = Color.Transparent,
    val todayCollectionBg: Color = Color(0xFF0D9488),
    val todayDueCreatedBg: Color = Color(0xFF4F46E5),
    val todayInterestBg: Color = Color(0xFF8B5CF6)
)

val LocalAppThemeColors = staticCompositionLocalOf { AppThemeColors() }

val ColorGainGreen: Color @Composable get() = LocalAppThemeColors.current.secondaryAccent
val ColorGainGreenLight: Color @Composable get() = LocalAppThemeColors.current.gainGreenLight
val ColorLossRed: Color @Composable get() = LocalAppThemeColors.current.tertiaryAccent
val ColorLossRedLight: Color @Composable get() = LocalAppThemeColors.current.lossRedLight
val ColorCardBg: Color = Color(0xFFFFFBFE)
val ColorSlateDark: Color @Composable get() = LocalAppThemeColors.current.darkBg
val ColorAccentBlue: Color @Composable get() = LocalAppThemeColors.current.primaryAccent

fun getThemeColors(themeName: String): AppThemeColors {
    return when (themeName) {
        "Modern Minimalist" -> AppThemeColors(
            mainBg = Color(0xFFF8F9FA),
            darkBg = Color(0xFFF1F5F9),
            primaryAccent = Color(0xFF2563EB), // sharp Royal Blue
            secondaryAccent = Color(0xFF10B981), // Emerald Green
            tertiaryAccent = Color(0xFFEF4444),
            gradientColors = listOf(Color.White, Color.White),
            gainGreenLight = Color(0xFFECFDF5),
            lossRedLight = Color(0xFFFEF2F2),
            isDark = false,
            textOnHeader = Color(0xFF0F172A), // Crisp, dark charcoal
            headerCardBg = listOf(Color.White, Color.White),
            headerCardBorder = Color(0xFFE2E8F0), // Subtle, thin gray borders instead of shadows
            todayCollectionBg = Color(0xFF10B981),
            todayDueCreatedBg = Color(0xFF2563EB),
            todayInterestBg = Color(0xFF6366F1)
        )
        "Professional Financial" -> AppThemeColors(
            mainBg = Color(0xFFECEFF1), // Soft slate/gray background so top bar pops
            darkBg = Color(0xFF0F172A),
            primaryAccent = Color(0xFF1E3A8A), // deep Navy Blue
            secondaryAccent = Color(0xFF059669), // Trust Green
            tertiaryAccent = Color(0xFFDC2626), // sharp Red
            gradientColors = listOf(Color(0xFF0F172A), Color(0xFF1E3A8A)), // deep Navy Blue to Slate Gray/Navy gradient
            gainGreenLight = Color(0xFFECFDF5),
            lossRedLight = Color(0xFFFFF1F2),
            isDark = false,
            textOnHeader = Color.White,
            headerCardBg = listOf(Color(0xFF0F172A), Color(0xFF1E3A8A)),
            headerCardBorder = Color(0xFF1E3A8A).copy(alpha = 0.5f),
            todayCollectionBg = Color(0xFF059669),
            todayDueCreatedBg = Color(0xFFD97706), // subtle Amber (creates distinction from green todayCollection)
            todayInterestBg = Color(0xFF475569)
        )
        "True Dark Mode" -> AppThemeColors(
            mainBg = Color(0xFF121212), // Deep charcoal
            darkBg = Color(0xFF1E1E1E), // matte gray
            primaryAccent = Color(0xFF00E5FF), // Neon/Electric Blue
            secondaryAccent = Color(0xFF00E676), // bright Mint Green
            tertiaryAccent = Color(0xFFFF1744), // Neon Red
            gradientColors = listOf(Color(0xFF1E1E1E), Color(0xFF2C2C2C)), // lighter matte gray
            gainGreenLight = Color(0xFF1B5E20),
            lossRedLight = Color(0xFFB71C1C),
            isDark = true,
            textOnHeader = Color.White,
            headerCardBg = listOf(Color(0xFF1E1E1E), Color(0xFF232323)),
            headerCardBorder = Color(0xFF00E5FF).copy(alpha = 0.2f),
            todayCollectionBg = Color(0xFF00E676),
            todayDueCreatedBg = Color(0xFF00E5FF),
            todayInterestBg = Color(0xFFD500F9)
        )
        "Luxury Gold" -> AppThemeColors(
            mainBg = Color(0xFFFBF9F4),
            darkBg = Color(0xFF0F172A),
            primaryAccent = Color(0xFFD97706),
            secondaryAccent = Color(0xFF10B981),
            tertiaryAccent = Color(0xFFF43F5E),
            gradientColors = listOf(Color(0xFF0F172A), Color(0xFFD97706)),
            gainGreenLight = Color(0xFFECFDF5),
            lossRedLight = Color(0xFFFFF1F2),
            headerCardBg = listOf(Color(0xFF0F172A), Color(0xFFD97706))
        )
        "Stripe Cobalt" -> AppThemeColors(
            mainBg = Color(0xFFF8FAFC),
            darkBg = Color(0xFF0A2540),
            primaryAccent = Color(0xFF635BFF),
            secondaryAccent = Color(0xFF00D4B2),
            tertiaryAccent = Color(0xFFE25C5C),
            gradientColors = listOf(Color(0xFF0A2540), Color(0xFF635BFF)),
            gainGreenLight = Color(0xFFF0FDF4),
            lossRedLight = Color(0xFFFFF5F5),
            headerCardBg = listOf(Color(0xFF0A2540), Color(0xFF635BFF))
        )
        "Forest Teal" -> AppThemeColors(
            mainBg = Color(0xFFF4F7F6),
            darkBg = Color(0xFF162A2B),
            primaryAccent = Color(0xFF2A7B6B),
            secondaryAccent = Color(0xFF80A299),
            tertiaryAccent = Color(0xFFD95C50),
            gradientColors = listOf(Color(0xFF162A2B), Color(0xFF2A7B6B)),
            gainGreenLight = Color(0xFFF1F7F6),
            lossRedLight = Color(0xFFFDF4F2),
            headerCardBg = listOf(Color(0xFF162A2B), Color(0xFF2A7B6B))
        )
        else -> AppThemeColors( // Sleek Slate
            mainBg = Color(0xFFF8FAFC),
            darkBg = Color(0xFF0F172A),
            primaryAccent = Color(0xFF4F46E5),
            secondaryAccent = Color(0xFF0D9488),
            tertiaryAccent = Color(0xFFEF4444),
            gradientColors = listOf(Color(0xFF0F172A), Color(0xFF4F46E5)),
            gainGreenLight = Color(0xFFF0FDF4),
            lossRedLight = Color(0xFFFEF2F2),
            headerCardBg = listOf(Color(0xFF0F172A), Color(0xFF4F46E5))
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyFinanceApp(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    val backstack by viewModel.screenBackstack.collectAsStateWithLifecycle()
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val activeDay by viewModel.selectedDay.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val fontSizeScale by viewModel.fontSizeScale.collectAsStateWithLifecycle()
    val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
    val customAppName by viewModel.customAppName.collectAsStateWithLifecycle()
    val customAppLogo by viewModel.customAppLogo.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()

    val canGoBack = backstack.size > 1 || (currentScreen == Screen.Dashboard && activeDay != "Home")

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            // Auto sync features fully disabled
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Handle back button clicks
    BackHandler(enabled = canGoBack) {
        if (currentScreen == Screen.Dashboard && activeDay != "Home") {
            viewModel.selectDay("Home")
        } else {
            viewModel.navigateBack()
        }
    }

    val currentDensity = LocalDensity.current
    val colors = getThemeColors(selectedTheme)
    CompositionLocalProvider(
        LocalAppThemeColors provides colors,
        LocalDensity provides Density(
            density = currentDensity.density,
            fontScale = currentDensity.fontScale * fontSizeScale
        )
    ) {
        var showWelcomeSplash by remember { mutableStateOf(false) }

        val isReconcilingMaster by viewModel.isReconcilingMaster.collectAsStateWithLifecycle()
        val reconciliationProgress by viewModel.reconciliationProgress.collectAsStateWithLifecycle()

        if (isReconcilingMaster) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F172A))
                    .testTag("reconciliation_splash_screen"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    CircularProgressIndicator(
                        color = colors.primaryAccent,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(56.dp).testTag("reconciliation_spinner")
                    )
                    
                    Text(
                        text = "MD Finance",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = reconciliationProgress,
                        fontSize = 14.sp,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("reconciliation_progress_text")
                    )
                }
            }
        } else if (showWelcomeSplash) {
            WelcomeSplashScreen(
                customAppName = customAppName,
                customAppLogo = customAppLogo,
                onDismiss = { showWelcomeSplash = false }
            )
        } else {
            val hasCompletedSetup by viewModel.hasCompletedDeviceSetup.collectAsStateWithLifecycle()

            // ---- GLOBAL REAL-TIME APP VERSION / OTA UPDATE HANDLER ----
            val updateStatus by com.example.network.FirebaseUpdateManager.updateStatus.collectAsStateWithLifecycle()
            val latestCode by com.example.network.FirebaseUpdateManager.latestVersionCode.collectAsStateWithLifecycle()
            val latestName by com.example.network.FirebaseUpdateManager.latestVersionName.collectAsStateWithLifecycle()
            var showUpdateDialog by remember { mutableStateOf(true) }

            LaunchedEffect(updateStatus) {
                if (updateStatus == com.example.network.UpdateStatus.UPDATE_AVAILABLE || 
                    updateStatus == com.example.network.UpdateStatus.DOWNLOADED) {
                    showUpdateDialog = true
                }
            }

            if (showUpdateDialog && (updateStatus == com.example.network.UpdateStatus.UPDATE_AVAILABLE || 
                                     updateStatus == com.example.network.UpdateStatus.DOWNLOADED)) {
                AlertDialog(
                    onDismissRequest = { showUpdateDialog = false },
                    containerColor = Color.White,
                    titleContentColor = Color.Black,
                    textContentColor = Color.Black,
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 8.dp,
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = colors.primaryAccent,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = translate("Software Update Available", language),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color(0xFF1E293B)
                            )
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = translate("A newer version of the application is available for installation.", language),
                                fontSize = 14.sp,
                                color = Color(0xFF475569)
                            )
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${translate("Latest Version", language)}: v$latestCode ($latestName)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF1E293B)
                                    )
                                    val statusText = if (updateStatus == com.example.network.UpdateStatus.DOWNLOADED) {
                                        translate("Status: Download completed! Ready to install.", language)
                                    } else {
                                        translate("Status: Automatic download in progress...", language)
                                    }
                                    Text(
                                        text = statusText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (updateStatus == com.example.network.UpdateStatus.DOWNLOADED) Color(0xFF059669) else Color(0xFFD97706)
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent),
                            shape = RoundedCornerShape(8.dp),
                            onClick = {
                                if (updateStatus == com.example.network.UpdateStatus.DOWNLOADED) {
                                    com.example.network.FirebaseUpdateManager.triggerInstall(context, latestCode)
                                } else {
                                    Toast.makeText(context, "Download is in progress. Installation will launch automatically when finished.", Toast.LENGTH_LONG).show()
                                }
                            }
                        ) {
                            Text(
                                text = if (updateStatus == com.example.network.UpdateStatus.DOWNLOADED) translate("Install Now", language) else translate("Downloading...", language),
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            shape = RoundedCornerShape(8.dp),
                            onClick = { showUpdateDialog = false }
                        ) {
                            Text(
                                text = translate("Later", language),
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                )
            }

            if (!isLoggedIn) {
                LoginScreen(viewModel = viewModel)
            } else if (!hasCompletedSetup) {
                DeviceSetupScreen(viewModel = viewModel)
            } else {
                val allCustomers by viewModel.allCustomers.collectAsStateWithLifecycle()
                val allLoanCycles by viewModel.allLoanCycles.collectAsStateWithLifecycle()
            val isExportImportLoading by viewModel.isExportImportLoading.collectAsStateWithLifecycle()
            val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
            val unsyncedLogCount by viewModel.unsyncedLogCount.collectAsStateWithLifecycle()
            val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

            if (isExportImportLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f))
                        .clickable(enabled = false) {} // block click events completely
                        .testTag("full_screen_blocking_loading_overlay"),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 36.dp, horizontal = 24.dp)
                        ) {
                            CircularProgressIndicator(
                                color = colors.primaryAccent,
                                strokeWidth = 4.5.dp,
                                modifier = Modifier.size(60.dp)
                            )
                            Spacer(modifier = Modifier.height(28.dp))
                            Text(
                                text = translate("MASTER DATABASE & LEDGER DATA INTEGRATION...", language),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primaryAccent,
                                letterSpacing = 1.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = translate("Exporting master backup database files or integrating heavy ledger updates. Please hold on and do not close the application or use navigation controls.", language),
                                fontSize = 12.sp,
                                color = Color(0xFF64748B),
                                lineHeight = 18.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            Scaffold(
                topBar = {
                    if (currentScreen !is Screen.CalculationDetail) {
                        TopAppBar(
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (currentScreen is Screen.Dashboard) {
                                        AppLogoContainer(
                                            logoName = customAppLogo,
                                            modifier = Modifier.size(24.dp),
                                            tintColor = getLogoRealColor(customAppLogo)
                                        )
                                        Text(
                                            text = customAppName,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.SansSerif,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    } else {
                                        val screen = currentScreen
                                        val screenTitleKey = when (screen) {
                                            is Screen.Dashboard -> "Welcome"
                                            is Screen.CustomerDetail -> "Customer Dashboard"
                                            is Screen.AddCustomer -> "Add Customer"
                                            is Screen.EditCustomer -> "Edit Customer"
                                            is Screen.AddLoan -> "New Loan Cycle"
                                            is Screen.EditLoan -> "Edit Loan Cycle"
                                            is Screen.RecordPayment -> "Collect Instalment"
                                            is Screen.Settings -> "Settings"
                                            is Screen.History -> "Activity History"
                                            is Screen.FullLedgerHistory -> "Daily Ledger History"
                                            is Screen.BulkEntry -> "Bulk Entry"
                                            is Screen.Search -> "Search Customers"
                                            else -> "Welcome"
                                        }
                                        val translatedTitle = translate(screenTitleKey, language)
                                        val displayTitle = when (screen) {
                                            is Screen.Dashboard -> "$translatedTitle $currentUser"
                                            is Screen.BulkEntry -> "$translatedTitle - ${translate(screen.day, language)}"
                                            else -> translatedTitle
                                        }
                                        Text(
                                            text = displayTitle,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.SansSerif,
                                            color = Color.White
                                        )
                                    }
                                }
                            },
                            navigationIcon = {
                                if (canGoBack) {
                                    IconButton(onClick = {
                                        if (currentScreen == Screen.Dashboard && activeDay != "Home") {
                                            viewModel.selectDay("Home")
                                        } else {
                                            viewModel.navigateBack()
                                        }
                                    }) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back",
                                            tint = Color.White
                                        )
                                    }
                                }
                            },
                            actions = {
                                val aiEnabled by viewModel.aiEnabled.collectAsStateWithLifecycle()
                                if (aiEnabled) {
                                    IconButton(
                                        onClick = { viewModel.navigateTo(Screen.AiChat) },
                                        modifier = Modifier.testTag("top_bar_ai_chat_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = "Easwar AI companion logo",
                                            tint = Color(0xFF6EE7B7)
                                        )
                                    }
                                }
                                // Dashboard Button option displayed everywhere
                                IconButton(onClick = { viewModel.navigateToHome() }) {
                                    Icon(
                                        imageVector = Icons.Default.Home,
                                        contentDescription = "Dashboard Button",
                                        tint = Color.White
                                    )
                                }
                                if (currentScreen == Screen.Dashboard) {
                                    val masterComparison by viewModel.masterComparison.collectAsStateWithLifecycle()
                                    val isMismatched = masterComparison?.let {
                                        !it.outstandingMatches || !it.customerCountMatches || it.mismatchingCustomers.isNotEmpty()
                                    } ?: false

                                    IconButton(onClick = { viewModel.navigateTo(Screen.Settings) }) {
                                        Box {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = "Settings Screen Link",
                                                tint = Color.White
                                            )
                                            if (isMismatched) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .align(Alignment.TopEnd)
                                                        .clip(CircleShape)
                                                        .background(Color.Red)
                                                )
                                            }
                                        }
                                    }
                                } else if (currentScreen is Screen.CustomerDetail) {
                                    val custId = (currentScreen as Screen.CustomerDetail).customerId
                                    val customer = allCustomers.find { it.id == custId }
                                    val activeLoan = allLoanCycles.find { it.customerId == custId && it.status == "ACTIVE" }
                                    if (customer != null) {
                                        var showMenu by remember { mutableStateOf(false) }
                                        var showDeleteCustomerDialog by remember { mutableStateOf(false) }
                                        var showDeleteLoanCycleDialog by remember { mutableStateOf(false) }

                                        if (showDeleteCustomerDialog) {
                                            AlertDialog(
                                                onDismissRequest = { showDeleteCustomerDialog = false },
                                                containerColor = Color.White,
                                                titleContentColor = Color.Black,
                                                textContentColor = Color.Black,
                                                title = { Text(translate("Delete Customer Profiling?", language), fontWeight = FontWeight.Bold, color = Color.Black) },
                                                text = { Text("Are you sure you want to delete ${customer.name}? This will permanently delete this customer's profile, active contracts, and all payment records. This action cannot be undone!", color = Color.Black) },
                                                confirmButton = {
                                                    Button(
                                                        onClick = {
                                                            showDeleteCustomerDialog = false
                                                            viewModel.deleteCustomer(customer)
                                                            viewModel.navigateToHome()
                                                            Toast.makeText(context, "Deleted profile for ${customer.name}", Toast.LENGTH_SHORT).show()
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = ColorLossRed)
                                                    ) {
                                                        Text(translate("Delete", language))
                                                    }
                                                },
                                                dismissButton = {
                                                    TextButton(onClick = { showDeleteCustomerDialog = false }) {
                                                        Text(translate("Cancel", language), color = Color.Black)
                                                    }
                                                }
                                            )
                                        }

                                        if (showDeleteLoanCycleDialog && activeLoan != null) {
                                            AlertDialog(
                                                onDismissRequest = { showDeleteLoanCycleDialog = false },
                                                containerColor = Color.White,
                                                titleContentColor = Color.Black,
                                                textContentColor = Color.Black,
                                                title = { Text(translate("Delete Active Loan Cycle?", language), fontWeight = FontWeight.Bold, color = Color.Black) },
                                                text = { Text("Are you sure you want to delete this active loan cycle worth ₹${activeLoan.loanAmount + activeLoan.interestAmount}? This will also delete all its payment history entries. This action cannot be undone!", color = Color.Black) },
                                                confirmButton = {
                                                    Button(
                                                        onClick = {
                                                            showDeleteLoanCycleDialog = false
                                                            viewModel.deleteLoanCycle(activeLoan)
                                                            Toast.makeText(context, "Deleted active loan cycle for ${customer.name}", Toast.LENGTH_SHORT).show()
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = ColorLossRed)
                                                    ) {
                                                        Text(translate("Delete", language))
                                                    }
                                                },
                                                dismissButton = {
                                                    TextButton(onClick = { showDeleteLoanCycleDialog = false }) {
                                                        Text(translate("Cancel", language), color = Color.Black)
                                                    }
                                                }
                                            )
                                        }

                                        Box {
                                            IconButton(onClick = { showMenu = true }) {
                                                Icon(
                                                    imageVector = Icons.Default.MoreVert,
                                                    contentDescription = "Customer Actions",
                                                    tint = Color.White
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = showMenu,
                                                onDismissRequest = { showMenu = false }
                                            ) {
                                                // Edit Customer Details
                                                DropdownMenuItem(
                                                    text = { Text("Edit Customer Profile") },
                                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                                    onClick = {
                                                        showMenu = false
                                                        viewModel.navigateTo(Screen.EditCustomer(custId))
                                                    }
                                                )
                                                
                                                // Edit Active Cycle if exists
                                                if (activeLoan != null) {
                                                    DropdownMenuItem(
                                                        text = { Text("Edit Active Loan Cycle") },
                                                        leadingIcon = { Icon(Icons.Default.CurrencyExchange, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                                        onClick = {
                                                            showMenu = false
                                                            viewModel.navigateTo(Screen.EditLoan(activeLoan.id))
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Delete Active Loan Cycle", color = ColorLossRed) },
                                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ColorLossRed, modifier = Modifier.size(16.dp)) },
                                                        onClick = {
                                                            showMenu = false
                                                            showDeleteLoanCycleDialog = true
                                                        }
                                                    )
                                                }
                                                
                                                HorizontalDivider()
                                                // Delete Customer Profile
                                                DropdownMenuItem(
                                                    text = { Text("Delete Customer", color = ColorLossRed) },
                                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ColorLossRed, modifier = Modifier.size(16.dp)) },
                                                    onClick = {
                                                        showMenu = false
                                                        showDeleteCustomerDialog = true
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = ColorSlateDark
                            )
                        )
                    }
                },
                containerColor = colors.mainBg,
                modifier = modifier.fillMaxSize()
            ) { innerPadding ->
                val configuration = LocalContext.current.resources.configuration
                val isWideScreen = configuration.screenWidthDp >= 720
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .background(if (isWideScreen) colors.mainBg.copy(alpha = 0.9f) else colors.mainBg),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (isWideScreen) {
                                    Modifier
                                        .widthIn(max = 840.dp)
                                        .padding(16.dp)
                                        .shadow(8.dp, RoundedCornerShape(16.dp))
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(colors.mainBg)
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        CylindricalTurnContainer(
                            targetState = currentScreen,
                            directionProvider = { from, to ->
                                val wFrom = when (from) {
                                    is Screen.Dashboard -> 0
                                    is Screen.History -> 3
                                    is Screen.Settings -> 4
                                    is Screen.CustomerDetail -> 10
                                    is Screen.AddCustomer -> 11
                                    is Screen.EditCustomer -> 12
                                    is Screen.AddLoan -> 13
                                    is Screen.EditLoan -> 14
                                    is Screen.RecordPayment -> 15
                                    is Screen.FullLedgerHistory -> 16
                                    is Screen.BulkEntry -> 20
                                    is Screen.Search -> 21
                                    is Screen.CalculationDetail -> 22
                                    else -> 5
                                }
                                val wTo = when (to) {
                                    is Screen.Dashboard -> 0
                                    is Screen.History -> 3
                                    is Screen.Settings -> 4
                                    is Screen.CustomerDetail -> 10
                                    is Screen.AddCustomer -> 11
                                    is Screen.EditCustomer -> 12
                                    is Screen.AddLoan -> 13
                                    is Screen.EditLoan -> 14
                                    is Screen.RecordPayment -> 15
                                    is Screen.FullLedgerHistory -> 16
                                    is Screen.BulkEntry -> 20
                                    is Screen.Search -> 21
                                    is Screen.CalculationDetail -> 22
                                    else -> 5
                                }
                                if (wTo >= wFrom) 1 else -1
                            },
                            modifier = Modifier.fillMaxSize()
                        ) { target ->
                            when (target) {
                                is Screen.Dashboard -> {
                                    DashboardScreen(viewModel = viewModel)
                                }
                                is Screen.CustomerDetail -> {
                                    CustomerDetailScreen(
                                        customerId = target.customerId,
                                        viewModel = viewModel
                                    )
                                }
                                is Screen.AddCustomer -> {
                                    AddCustomerScreen(viewModel = viewModel)
                                }
                                is Screen.EditCustomer -> {
                                    EditCustomerScreen(
                                        customerId = target.customerId,
                                        viewModel = viewModel
                                    )
                                }
                                is Screen.AddLoan -> {
                                    AddLoanScreen(
                                        customerId = target.customerId,
                                        viewModel = viewModel
                                    )
                                }
                                is Screen.EditLoan -> {
                                    EditLoanScreen(
                                        loanCycleId = target.loanCycleId,
                                        viewModel = viewModel
                                    )
                                }
                                is Screen.RecordPayment -> {
                                    RecordPaymentScreen(
                                        loanCycleId = target.loanCycleId,
                                        viewModel = viewModel
                                    )
                                }

                                is Screen.Settings -> {
                                    SettingsScreen(viewModel = viewModel)
                                }
                                is Screen.History -> {
                                    HistoryScreen(viewModel = viewModel)
                                }
                                is Screen.FullLedgerHistory -> {
                                    FullLedgerHistoryScreen(viewModel = viewModel)
                                }
                                is Screen.BulkEntry -> {
                                    BulkEntryScreen(day = target.day, viewModel = viewModel)
                                }
                                is Screen.Search -> {
                                    SearchScreen(day = target.day, viewModel = viewModel)
                                }
                                is Screen.AiChat -> {
                                    AiChatScreen(viewModel = viewModel)
                                }
                                is Screen.CalculationDetail -> {
                                    CalculationDetailScreen(
                                        type = target.type,
                                        day = target.day,
                                        viewModel = viewModel
                                    )
                                }

                            }
                        }
                    }
                }
            }
            }
        }
    }
}

// Dynamic 3D Cylindrical sequence layout engine
fun getOrderedDaysList(collectionGroups: List<String>): List<String> {
    val canonicalOrder = listOf(
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", 
        "Sunday mrg", "Sunday eve"
    )
    
    val availableGroups = canonicalOrder.filter { group ->
        collectionGroups.any { it.trim().equals(group.trim(), ignoreCase = true) }
    }
    
    val customGroups = collectionGroups.filter { cg ->
        !canonicalOrder.any { co -> co.trim().equals(cg.trim(), ignoreCase = true) }
    }.sorted()
    
    return listOf("Home") + availableGroups + customGroups
}

fun getLogoIcon(logoName: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (logoName) {
        "TrendingUp" -> Icons.Default.TrendingUp
        "AccountBalance" -> Icons.Default.AccountBalance
        "Wallet" -> Icons.Default.Wallet
        "Paid" -> Icons.Default.Paid
        "Percent" -> Icons.Default.Percent
        "Star" -> Icons.Default.Star
        "Calculate" -> Icons.Default.Calculate
        "MonetizationOn" -> Icons.Default.MonetizationOn
        else -> Icons.Default.CurrencyRupee
    }
}

fun getLogoRealColor(logoName: String): Color {
    return when (logoName) {
        "MonetizationOn" -> Color(0xFFFBC02D) // Gold
        "CurrencyRupee" -> Color(0xFF2E7D32) // Emerald Green
        "Wallet" -> Color(0xFF8D6E63) // Leather Brown
        "AccountBalance" -> Color(0xFF1976D2) // Corporate Blue
        "TrendingUp" -> Color(0xFF4CAF50) // Up Trend Green
        "Paid" -> Color(0xFFFFA000) // Radiant Gold/Orange
        "Calculate" -> Color(0xFF546E7A) // Accountant Slate Gray
        "Star" -> Color(0xFFFFC107) // Yellow Star
        else -> Color(0xFF2E7D32) // Emerald Green Rupee
    }
}

@Composable
fun AppLogoContainer(
    logoName: String,
    modifier: Modifier = Modifier,
    tintColor: Color = Color.White
) {
    val context = LocalContext.current
    if (logoName == "CUSTOM") {
        val customBitmap = remember(logoName) {
            val file = File(context.filesDir, "app_logo_custom.png")
            if (file.exists()) {
                try {
                    BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            } else null
        }
        if (customBitmap != null) {
            Image(
                bitmap = customBitmap,
                contentDescription = "App Custom Logo",
                modifier = modifier
            )
        } else {
            Icon(
                imageVector = Icons.Default.MonetizationOn,
                contentDescription = "Fallback App Logo",
                tint = tintColor,
                modifier = modifier
            )
        }
    } else {
        Icon(
            imageVector = getLogoIcon(logoName),
            contentDescription = "App Logo",
            tint = tintColor,
            modifier = modifier
        )
    }
}

@Composable
fun WelcomeSplashScreen(
    customAppName: String,
    customAppLogo: String,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(5000)
        onDismiss()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "welcome")
    val color1 by infiniteTransition.animateColor(
        initialValue = Color(0xFF4F46E5),
        targetValue = Color(0xFF10B981),
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "color1"
    )
    val color2 by infiniteTransition.animateColor(
        initialValue = Color(0xFFF59E0B),
        targetValue = Color(0xFFEC4899),
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "color2"
    )

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(400.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(color1.copy(alpha = 0.15f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        brush = Brush.linearGradient(colors = listOf(color1, color2)),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "WELCOME",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                    color = Color.White,
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(
                            color = Color.White,
                            shape = CircleShape
                        )
                        .border(3.dp, Color(0xFFCBD5E1), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    AppLogoContainer(
                        logoName = customAppLogo,
                        modifier = Modifier.size(60.dp),
                        tintColor = getLogoRealColor(customAppLogo)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = customAppName.uppercase(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "Simple Ledger Manager",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.height(100.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Icon(
                        imageVector = Icons.Default.BackHand,
                        contentDescription = "Left hand",
                        tint = Color(0xFFFFC09F),
                        modifier = Modifier
                            .size(72.dp)
                            .graphicsLayer {
                                rotationZ = 45f + (scale * 5f)
                                translationX = (scale * 10f) - 10f
                            }
                    )
                    Icon(
                        imageVector = Icons.Default.BackHand,
                        contentDescription = "Right hand",
                        tint = Color(0xFFFFC09F),
                        modifier = Modifier
                            .size(72.dp)
                            .graphicsLayer {
                                rotationZ = -45f - (scale * 5f)
                                rotationY = 180f
                                translationX = -((scale * 10f) - 10f)
                            }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Gaining Trust through Ledger Transparency",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}
