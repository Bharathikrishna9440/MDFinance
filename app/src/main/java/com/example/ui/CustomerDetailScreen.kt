package com.example.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Customer
import com.example.data.LoanCycle
import com.example.data.WeeklyPayment
import com.example.util.CurrencyFormatter

import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val WhatsAppIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "WhatsAppIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = androidx.compose.ui.graphics.SolidColor(Color(0xFF22C55E)),
        strokeLineWidth = 1.5f,
        fill = null
    ) {
        // Standard WhatsApp outer speech bubble shape as outline
        moveTo(12.003f, 4.078f)
        curveTo(7.628f, 4.078f, 4.078f, 7.628f, 4.078f, 12.003f)
        curveTo(4.078f, 13.403f, 4.444f, 14.719f, 5.084f, 15.878f)
        lineTo(4.078f, 19.922f)
        lineTo(8.216f, 18.844f)
        curveTo(9.344f, 19.459f, 10.634f, 19.928f, 12.003f, 19.928f)
        curveTo(16.378f, 19.928f, 19.922f, 16.378f, 19.922f, 12.003f)
        curveTo(19.922f, 7.628f, 16.378f, 4.078f, 12.003f, 4.078f)
        close()
    }.path(
        fill = androidx.compose.ui.graphics.SolidColor(Color(0xFF22C55E)),
        stroke = null,
        strokeLineWidth = 0f
    ) {
        // Professional WhatsApp telephone receiver cutout shape
        moveTo(15.835f, 16.128f)
        curveTo(15.588f, 16.825f, 14.621f, 17.411f, 14.169f, 17.469f)
        curveTo(13.716f, 17.527f, 13.235f, 17.579f, 11.263f, 16.761f)
        curveTo(8.736f, 15.714f, 7.123f, 13.128f, 6.997f, 12.96f)
        curveTo(6.87f, 12.792f, 5.972f, 11.595f, 5.972f, 10.358f)
        curveTo(5.972f, 9.121f, 6.622f, 8.512f, 6.855f, 8.267f)
        curveTo(7.088f, 8.022f, 7.365f, 7.962f, 7.535f, 7.962f)
        curveTo(7.705f, 7.962f, 7.875f, 7.962f, 8.025f, 7.971f)
        curveTo(8.185f, 7.98f, 8.4f, 7.91f, 8.612f, 8.423f)
        curveTo(8.832f, 8.954f, 9.362f, 10.254f, 9.427f, 10.388f)
        curveTo(9.491f, 10.521f, 9.533f, 10.676f, 9.443f, 10.855f)
        curveTo(9.353f, 11.035f, 9.308f, 11.146f, 9.173f, 11.305f)
        curveTo(9.038f, 11.463f, 8.89f, 11.659f, 8.77f, 11.78f)
        curveTo(8.635f, 11.917f, 8.492f, 12.068f, 8.65f, 12.34f)
        curveTo(8.808f, 12.612f, 9.354f, 13.497f, 10.157f, 14.214f)
        curveTo(10.96f, 14.931f, 11.637f, 15.154f, 11.845f, 15.257f)
        curveTo(12.053f, 15.36f, 12.177f, 15.347f, 12.301f, 15.205f)
        curveTo(12.425f, 15.063f, 12.833f, 14.586f, 12.975f, 14.375f)
        curveTo(13.117f, 14.165f, 13.259f, 14.2f, 13.454f, 14.272f)
        curveTo(13.649f, 14.343f, 14.691f, 14.856f, 14.904f, 14.962f)
        curveTo(15.117f, 15.069f, 15.259f, 15.121f, 15.311f, 15.211f)
        curveTo(15.363f, 15.301f, 15.363f, 15.732f, 15.116f, 16.429f)
        close()
    }.build()
}

private fun shareLocalCustomerFile(context: android.content.Context, customer: Customer, fileExtension: String, viewModel: FinanceViewModel) {
    try {
        val groupsDir = context.getExternalFilesDir("Groups") ?: return
        val dayDir = java.io.File(groupsDir, customer.collectionDay)
        val safeName = customer.name.replace(Regex("[^a-zA-Z0-9_ -]"), "").trim().replace(" ", "_")
        val customerFolder = java.io.File(dayDir, "${customer.customerCode}_$safeName")
        val file = java.io.File(customerFolder, "details.$fileExtension")
        
        if (!file.exists()) {
            Toast.makeText(context, "Preparing dynamic files...", Toast.LENGTH_SHORT).show()
            viewModel.createOrUpdateCustomerFiles(context, customer.id)
        }
        
        if (file.exists()) {
            val fileUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val mimeType = when (fileExtension) {
                "txt" -> "text/plain"
                "html" -> "text/html"
                "json" -> "application/json"
                else -> "*/*"
            }
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share Code Page File (.$fileExtension)"))
        } else {
            Toast.makeText(context, "Preparing ledger details. Please try sharing again in a moment.", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to share: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

data class CalendarWeek(
    val mondayTime: Long,
    val sundayTime: Long,
    val assignedMonth: Int // 0..11
)

fun getCalendarWeeksForYear(year: Int): List<CalendarWeek> {
    val weeks = mutableListOf<CalendarWeek>()
    
    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, Calendar.JANUARY)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    
    while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
        cal.add(Calendar.DAY_OF_YEAR, -1)
    }
    
    val nextYear = year + 1
    
    while (true) {
        val mondayTime = cal.timeInMillis
        val monthCounts = IntArray(12) { 0 }
        
        val tempCal = Calendar.getInstance().apply { timeInMillis = mondayTime }
        for (i in 0 until 7) {
            val y = tempCal.get(Calendar.YEAR)
            val m = tempCal.get(Calendar.MONTH)
            if (y == year && m in 0..11) {
                monthCounts[m]++
            }
            tempCal.add(Calendar.DAY_OF_YEAR, 1)
        }
        
        tempCal.add(Calendar.DAY_OF_YEAR, -1)
        val sundayTime = tempCal.timeInMillis
        
        var assignedMonth = -1
        var maxCount = 0
        for (m in 0..11) {
            if (monthCounts[m] > maxCount) {
                maxCount = monthCounts[m]
                assignedMonth = m
            }
        }
        
        if (assignedMonth != -1 && maxCount >= 4) {
            weeks.add(CalendarWeek(mondayTime, sundayTime, assignedMonth))
        }
        
        cal.add(Calendar.DAY_OF_YEAR, 7)
        
        if (cal.get(Calendar.YEAR) >= nextYear) {
            val checkCal = Calendar.getInstance().apply { timeInMillis = cal.timeInMillis }
            var daysInNextYear = 0
            for (i in 0 until 7) {
                if (checkCal.get(Calendar.YEAR) >= nextYear) {
                    daysInNextYear++
                }
                checkCal.add(Calendar.DAY_OF_YEAR, 1)
            }
            if (daysInNextYear >= 4) {
                break
            }
        }
    }
    
    return weeks
}

@Composable
fun PaymentCalendarMatrix(payments: List<WeeklyPayment>, loanCycles: List<LoanCycle>) {
    val past12Months = remember {
        val list = mutableListOf<Pair<Int, Int>>() // Pair(monthIndex 0..11, year)
        val startCal = Calendar.getInstance().apply {
            add(Calendar.MONTH, -11)
        }
        for (i in 0 until 12) {
            list.add(Pair(startCal.get(Calendar.MONTH), startCal.get(Calendar.YEAR)))
            startCal.add(Calendar.MONTH, 1)
        }
        list
    }
    
    val allWeeksInPast12Months = remember(past12Months) {
        val yearsNeeded = past12Months.map { it.second }.distinct()
        val weeksList = mutableListOf<Triple<CalendarWeek, Int, Int>>() // Triple(week, monthIndex, year)
        for (yr in yearsNeeded) {
            val weeksOfYr = getCalendarWeeksForYear(yr)
            for (week in weeksOfYr) {
                weeksList.add(Triple(week, week.assignedMonth, yr))
            }
        }
        weeksList
    }
    
    val groupedWeeksByMonthYear = remember(allWeeksInPast12Months, past12Months) {
        past12Months.map { (m, y) ->
            allWeeksInPast12Months.filter { it.second == m && it.third == y }.map { it.first }
        }
    }

    // Helper to determine cover period 
    fun isWeekCoveredByLoan(startMs: Long, endMs: Long, loan: LoanCycle, payments: List<WeeklyPayment>): Boolean {
        val coverStart = loan.startDate
        val coverEnd = if (loan.status.uppercase() == "ACTIVE") {
            System.currentTimeMillis()
        } else {
            // PAID
            val loanPayments = payments.filter { it.loanCycleId == loan.id }
            loanPayments.maxOfOrNull { it.paymentDate } ?: loan.startDate
        }
        return endMs >= coverStart && startMs <= coverEnd
    }

    // Dynamic calculations representing the calendar map's visible space
    val counts = remember(groupedWeeksByMonthYear, payments, loanCycles) {
        var totalWeeksCount = 0
        var weeksPaidCount = 0
        groupedWeeksByMonthYear.forEach { weeksOfThisMonth ->
            weeksOfThisMonth.forEach { week ->
                val startMs = week.mondayTime
                val endMs = week.mondayTime + 7L * 24 * 3600 * 1000 - 1
                
                val isCovered = loanCycles.any { loan ->
                    isWeekCoveredByLoan(startMs, endMs, loan, payments)
                }
                
                if (isCovered) {
                    totalWeeksCount++
                    val isPaid = payments.any { p -> p.status == "ACTIVE" && p.amountPaid > 0.0 && p.paymentDate in startMs..endMs }
                    if (isPaid) {
                        weeksPaidCount++
                    }
                }
            }
        }
        Pair(weeksPaidCount, totalWeeksCount)
    }
    
    val weeksPaid = counts.first
    val totalWeeks = counts.second

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Calendar Map",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorSlateDark
                )
                if (totalWeeks > 0) {
                    Text(
                        text = "Weeks Paid: $weeksPaid / $totalWeeks",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ColorSlateDark
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (colIndex in 0..11) {
                    val weeksOfThisMonth = groupedWeeksByMonthYear[colIndex]
                    val monthYearPair = past12Months[colIndex]
                    val monthNumberString = (monthYearPair.first + 1).toString()
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = monthNumberString,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                        for (weekIndex in 0..4) {
                            if (weekIndex < weeksOfThisMonth.size) {
                                val week = weeksOfThisMonth[weekIndex]
                                val startMs = week.mondayTime
                                val endMs = week.mondayTime + 7L * 24 * 3600 * 1000 - 1
                                
                                val isCovered = loanCycles.any { loan ->
                                    isWeekCoveredByLoan(startMs, endMs, loan, payments)
                                }
                                val isPaid = payments.any { p ->
                                    p.status == "ACTIVE" && p.amountPaid > 0.0 && p.paymentDate in startMs..endMs
                                }
                                
                                val dotColor = if (isCovered) {
                                    if (isPaid) ColorGainGreen else ColorLossRed
                                } else {
                                    Color(0xFFCBD5E1) // Grey out point if no active cycle represents this duration
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(
                                            color = dotColor,
                                            shape = CircleShape
                                        )
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(
                                            color = Color.Transparent,
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerDetailScreen(
    customerId: Int,
    viewModel: FinanceViewModel
) {
    val appColors = LocalAppThemeColors.current
    val context = LocalContext.current
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Active, 1 = Past History

    LaunchedEffect(customerId) {
        viewModel.recalibrateCalculationsSilent()
    }
    
    val customerList: List<Customer> by viewModel.allCustomers.collectAsStateWithLifecycle()
    val loanCycles: List<LoanCycle> by viewModel.getLoanCyclesForCustomer(customerId).collectAsStateWithLifecycle(initialValue = emptyList())
    val upiLinkVal by viewModel.upiLink.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val currentUserRole by viewModel.currentUserRole.collectAsStateWithLifecycle()
    
    val customer = customerList.find { it.id == customerId }
    if (customer == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Customer details not found.")
        }
        return
    }

    var showDeleteCustomerDialog by remember { mutableStateOf(false) }
    var deletingCycleTarget by remember { mutableStateOf<LoanCycle?>(null) }
    var deletingPaymentTarget by remember { mutableStateOf<WeeklyPayment?>(null) }
    var editingPayment by remember { mutableStateOf<WeeklyPayment?>(null) }

    // Direct Interaction Heads-up state variables
    var showCallAlert by remember { mutableStateOf(false) }
    var showSmsAlert by remember { mutableStateOf(false) }
    var showUpiAlert by remember { mutableStateOf(false) }

    // Heads-up Dialogs
    if (showCallAlert) {
        AlertDialog(
            onDismissRequest = { showCallAlert = false },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.Black,
            title = { Text("Call Confirmation", fontWeight = FontWeight.Bold, color = Color.Black) },
            text = { Text("Are you sure you want to call ${customer.name} now?", color = Color.Black) },
            confirmButton = {
                Button(
                    onClick = {
                        showCallAlert = false
                        try {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:${customer.phone}")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error making call: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorGainGreen)
                ) {
                    Text("Call Directly")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCallAlert = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSmsAlert) {
        val firstActive = loanCycles.firstOrNull { it.status == "ACTIVE" }
        AlertDialog(
            onDismissRequest = { showSmsAlert = false },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.Black,
            title = { Text("SMS Confirmation", fontWeight = FontWeight.Bold, color = Color.Black) },
            text = { Text("Are you sure you want to send a collection reminder SMS to ${customer.name}?", color = Color.Black) },
            confirmButton = {
                Button(
                    onClick = {
                        showSmsAlert = false
                        if (firstActive != null) {
                            viewModel.triggerManualReminderSms(context, customer, firstActive)
                        } else {
                            try {
                                val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("smsto:${customer.phone}")
                                    putExtra("sms_body", "Hi ${customer.name}, this is a gentle reminder regarding your outstanding weekly balance.")
                                }
                                context.startActivity(smsIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error sending SMS: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorAccentBlue)
                ) {
                    Text("Send SMS")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSmsAlert = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showUpiAlert) {
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
        val rawPhone = customer.phone.replace("[^0-9]".toRegex(), "")
        val cleanPhone = if (rawPhone.length >= 10) rawPhone.takeLast(10) else rawPhone
        val defaultUpi = if (customer.upiNameAlias.contains("@")) {
            customer.upiNameAlias.trim()
        } else if (cleanPhone.isNotEmpty()) {
            "${cleanPhone}@ybl"
        } else {
            ""
        }
        var upiIdToPay by remember(customer) { mutableStateOf(defaultUpi) }

        val upiTitle = translate("UPI Payment", language)
        val upiText = when (language) {
            "Tamil" -> "${customer.name}-க்கு பணம் செலுத்த UPI செயலியைத் திறக்கலாமா? முன்னிருப்பாக வாடிக்கையாளரின் தொலைபேசி எண் UPI முகவரியாக (${cleanPhone}@ybl) பயன்படுத்தப்படும். திறந்ததும், நீங்கள் தொகையை உள்ளிட்டு பணம் செலுத்தலாம்."
            "Hindi" -> "क्या आप ${customer.name} को भुगतान करने के लिए UPI ऐप खोलना चाहते हैं? डिफ़ॉल्ट रूप से, ग्राहक का मोबाइल नंबर UPI पता (${cleanPhone}@ybl) के रूप में उपयोग किया जाएगा। एक बार UPI ऐप खुलने के बाद, आप भुगतान करने के लिए राशि दर्ज कर सकते हैं।"
            "Telugu" -> "${customer.name} కి చెల్లించడానికి UPI యాప్‌ని తెరవాలా? డిఫాల్ట్‌గా, కస్టమర్ మొబైల్ నంబర్ UPI చిరునామా (${cleanPhone}@ybl)గా ఉపయోగించబడుతుంది. UPI యాప్ తెరిచిన తర్వాత, మీరు చెల్లించాల్సిన మొత్తాన్ని టైప్ చేయవచ్చు."
            else -> "Open your UPI app to pay ${customer.name}? By default, it will use the customer's mobile number UPI address (${cleanPhone}@ybl). Once the UPI app opens, you can type the amount to pay."
        }
        val upiConfirm = translate("Open UPI App", language)
        val upiCancel = translate("Cancel", language)

        AlertDialog(
            onDismissRequest = { showUpiAlert = false },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.Black,
            title = { Text(upiTitle, fontWeight = FontWeight.Bold, color = Color.Black) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(upiText, color = Color.Black, fontSize = 14.sp)
                    
                    OutlinedTextField(
                        value = upiIdToPay,
                        onValueChange = { upiIdToPay = it },
                        label = { Text(translate("Customer UPI ID (VPA)", language)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedBorderColor = Color(0xFF0284C7),
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color(0xFF0284C7),
                            unfocusedLabelColor = Color.Gray
                        )
                    )
                    
                    if (cleanPhone.isNotEmpty()) {
                        Text(
                            text = translate("Quick Suffix:", language),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val suffixes = listOf("@ybl", "@upi", "@ptyes", "@okaxis")
                            suffixes.forEach { suffix ->
                                val targetVpa = "${cleanPhone}$suffix"
                                val isSelected = upiIdToPay == targetVpa
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = if (isSelected) Color(0xFF0284C7) else Color(0xFFF1F5F9),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clickable { upiIdToPay = targetVpa }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = suffix,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isSelected) Color.White else Color.Black
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUpiAlert = false
                        val cleanUpi = upiIdToPay.trim()
                        if (cleanUpi.isBlank()) {
                            Toast.makeText(context, "UPI ID cannot be empty", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        try {
                            // Copy to Clipboard as automatic fallback so they can paste it if any app is restrictive
                            clipboardManager.setText(androidx.compose.ui.text.buildAnnotatedString { append(cleanUpi) })
                            Toast.makeText(context, "UPI ID copied: $cleanUpi", Toast.LENGTH_LONG).show()

                            // Build simplified P2P UPI link to avoid "payment not allowed" merchant restrictions
                            val intentUri = Uri.Builder()
                                .scheme("upi")
                                .authority("pay")
                                .appendQueryParameter("pa", cleanUpi)
                                .build()
                            
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = intentUri
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Launch UPI Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Text(upiConfirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpiAlert = false }) {
                    Text(upiCancel, color = Color.Black)
                }
            }
        )
    }

    if (showDeleteCustomerDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteCustomerDialog = false },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.Black,
            title = { Text("Confirm Customer Deletion", fontWeight = FontWeight.Bold, color = Color.Black) },
            text = { Text("Are you sure you want to delete ${customer.name}? This will permanently delete this customer's profile, active contracts, and all payment records. This action cannot be undone!", color = Color.Black) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCustomer(customer)
                        showDeleteCustomerDialog = false
                        viewModel.navigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorLossRed)
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteCustomerDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = ColorLossRed)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (deletingCycleTarget != null) {
        val target = deletingCycleTarget!!
        AlertDialog(
            onDismissRequest = { deletingCycleTarget = null },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.Black,
            title = { Text("Delete Loan Contract", fontWeight = FontWeight.Bold, color = Color.Black) },
            text = { Text("Are you sure you want to delete this loan cycle worth ₹${target.loanAmount + target.interestAmount}? This will also delete all its payment history entries.", color = Color.Black) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteLoanCycle(target)
                        deletingCycleTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorLossRed)
                ) {
                    Text("Delete Contract")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deletingCycleTarget = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = ColorLossRed)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (deletingPaymentTarget != null) {
        val targetPayment = deletingPaymentTarget!!
        AlertDialog(
            onDismissRequest = { deletingPaymentTarget = null },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.Black,
            title = { Text("Delete Instalment Entry", fontWeight = FontWeight.Bold, color = Color.Black) },
            text = { Text("Are you sure you want to delete this payment record? This will reduce the total paid amount and increase outstanding dues.", color = Color.Black) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePayment(targetPayment.id, targetPayment.loanCycleId)
                        deletingPaymentTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorLossRed)
                ) {
                    Text("Delete Entry")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deletingPaymentTarget = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = ColorLossRed)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (editingPayment != null) {
        val entry = editingPayment!!
        var amtText by remember(entry.id) { mutableStateOf(entry.amountPaid.toLong().toString()) }
        var wkNumText by remember(entry.id) { mutableStateOf(entry.weekNumber.toString()) }
        var noteText by remember(entry.id) { mutableStateOf(entry.notes.ifBlank { "Cash" }) }
        
        val sdfEdit = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
        var dateText by remember(entry.id) { mutableStateOf(sdfEdit.format(Date(entry.paymentDate))) }

        // Date & Time Picker triggers
        val parsedTimestamp = try {
            sdfEdit.parse(dateText)?.time ?: entry.paymentDate
        } catch (e: Exception) {
            entry.paymentDate
        }

        val datePickerDialog = remember(parsedTimestamp) {
            android.app.DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    val calendar = Calendar.getInstance().apply {
                        timeInMillis = parsedTimestamp
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    }
                    dateText = sdfEdit.format(Date(calendar.timeInMillis))
                },
                Calendar.getInstance().apply { timeInMillis = parsedTimestamp }.get(Calendar.YEAR),
                Calendar.getInstance().apply { timeInMillis = parsedTimestamp }.get(Calendar.MONTH),
                Calendar.getInstance().apply { timeInMillis = parsedTimestamp }.get(Calendar.DAY_OF_MONTH)
            )
        }

        val timePickerDialog = remember(parsedTimestamp) {
            android.app.TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    val calendar = Calendar.getInstance().apply {
                        timeInMillis = parsedTimestamp
                        set(Calendar.HOUR_OF_DAY, hourOfDay)
                        set(Calendar.MINUTE, minute)
                    }
                    dateText = sdfEdit.format(Date(calendar.timeInMillis))
                },
                Calendar.getInstance().apply { timeInMillis = parsedTimestamp }.get(Calendar.HOUR_OF_DAY),
                Calendar.getInstance().apply { timeInMillis = parsedTimestamp }.get(Calendar.MINUTE),
                false
            )
        }

        AlertDialog(
            onDismissRequest = { editingPayment = null },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.Black,
            title = { Text("Edit Instalment Entry", fontWeight = FontWeight.Bold, color = Color.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amtText,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() }
                            amtText = filtered
                        },
                        label = { Text("Amount Collected (₹)", color = Color.Gray) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedLabelColor = Color.DarkGray,
                            unfocusedLabelColor = Color.Gray,
                            focusedPlaceholderColor = Color.Gray,
                            unfocusedPlaceholderColor = Color.Gray,
                            focusedBorderColor = appColors.primaryAccent,
                            unfocusedBorderColor = Color(0xFFCBD5E1),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = wkNumText,
                        onValueChange = { input ->
                            wkNumText = input.filter { it.isDigit() }
                        },
                        label = { Text("Week Number", color = Color.Gray) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedLabelColor = Color.DarkGray,
                            unfocusedLabelColor = Color.Gray,
                            focusedPlaceholderColor = Color.Gray,
                            unfocusedPlaceholderColor = Color.Gray,
                            focusedBorderColor = appColors.primaryAccent,
                            unfocusedBorderColor = Color(0xFFCBD5E1),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Change/Edit Date & Time option row
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = {},
                        label = { Text("Payment Date & Time", color = Color.Gray) },
                        readOnly = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedLabelColor = Color.DarkGray,
                            unfocusedLabelColor = Color.Gray,
                            focusedPlaceholderColor = Color.Gray,
                            unfocusedPlaceholderColor = Color.Gray,
                            focusedBorderColor = appColors.primaryAccent,
                            unfocusedBorderColor = Color(0xFFCBD5E1),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { datePickerDialog.show() },
                        trailingIcon = {
                            Row {
                                IconButton(onClick = { datePickerDialog.show() }) {
                                    Icon(Icons.Default.DateRange, contentDescription = "Pick Date", tint = Color.Black)
                                }
                                IconButton(onClick = { timePickerDialog.show() }) {
                                    Icon(Icons.Default.AccessTime, contentDescription = "Pick Time", tint = Color.Black)
                                }
                            }
                        }
                    )

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Mode of Payment",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.DarkGray,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .background(
                                    color = if (noteText == "Cash") Color(0xFF16A34A).copy(alpha = 0.2f) else Color(0xFF2563EB).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = 1.5.dp,
                                    color = if (noteText == "Cash") Color(0xFF16A34A) else Color(0xFF2563EB),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    noteText = if (noteText == "Cash") "Online" else "Cash"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = noteText.uppercase(Locale.getDefault()),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = if (noteText == "Cash") Color(0xFF16A34A) else Color(0xFF2563EB)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedAmt = amtText.toDoubleOrNull()
                        val parsedWk = wkNumText.toIntOrNull()
                        if (parsedAmt == null || parsedAmt <= 0.0) {
                            Toast.makeText(context, "Please enter a valid collection amount greater than 0.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (parsedWk == null || parsedWk <= 0) {
                            Toast.makeText(context, "Please enter a valid week number greater than 0.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val parsedDt = try {
                            sdfEdit.parse(dateText)?.time ?: entry.paymentDate
                        } catch (e: Exception) {
                            entry.paymentDate
                        }
                        viewModel.editWeeklyPayment(
                            context = context,
                            paymentId = entry.id,
                            loanCycleId = entry.loanCycleId,
                            amount = parsedAmt,
                            weekNum = parsedWk,
                            paymentDate = parsedDt,
                            notes = noteText
                        )
                        editingPayment = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorGainGreen)
                ) {
                    Text("Save Changes", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { editingPayment = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.DarkGray)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    val allPayments by viewModel.allPayments.collectAsStateWithLifecycle()

    val liveLoanCycles = remember(loanCycles, allPayments) {
        loanCycles.map { lc ->
            val livePaid = allPayments.filter { it.loanCycleId == lc.id && it.status.uppercase() != "DELETED" }.sumOf { it.amountPaid }
            lc.copy(paidAmount = livePaid)
        }
    }
    val activeLoans = liveLoanCycles.filter { it.status == "ACTIVE" }
    val paidHistory = liveLoanCycles.filter { it.status == "PAID" }

    val customerLoanCycleIds = remember(loanCycles) { loanCycles.map { it.id }.toSet() }
    val customerPayments = allPayments.filter { it.loanCycleId in customerLoanCycleIds && it.status == "ACTIVE" }

    val overviewList by viewModel.customerOverviewList.collectAsStateWithLifecycle()
    val currentIndex = overviewList.indexOfFirst { it.customer.id == customerId }
    val hasPrevious = currentIndex > 0
    val hasNext = currentIndex != -1 && currentIndex < overviewList.size - 1

    val previousCustomerId = if (hasPrevious) overviewList[currentIndex - 1].customer.id else null
    val nextCustomerId = if (hasNext) overviewList[currentIndex + 1].customer.id else null

    val prevCustomerName = if (hasPrevious) overviewList[currentIndex - 1].customer.name else null
    val nextCustomerName = if (hasNext) overviewList[currentIndex + 1].customer.name else null

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        ParticleBackground(
            modifier = Modifier.matchParentSize().background(appColors.primaryAccent.copy(alpha = 0.03f))
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
        // Visual indicator showing previous/next customer for convenient switching
        if (prevCustomerName != null || nextCustomerName != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (prevCustomerName != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(ColorSlateDark.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                            .clickable { viewModel.replaceTopScreen(Screen.CustomerDetail(previousCustomerId!!)) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Previous Customer", tint = ColorSlateDark, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = prevCustomerName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorSlateDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 110.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Text(
                    text = "Navigator",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.SemiBold
                )

                if (nextCustomerName != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(ColorSlateDark.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                            .clickable { viewModel.replaceTopScreen(Screen.CustomerDetail(nextCustomerId!!)) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = nextCustomerName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorSlateDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 110.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = "Next Customer", tint = ColorSlateDark, modifier = Modifier.size(14.dp))
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
            }
        }

        // REDESIGNED: Home Dashboard Style Customer outstanding ledger card
        val activeLoanReal = activeLoans.firstOrNull()
        val activeLoan = activeLoanReal ?: LoanCycle(
            id = -1,
            customerId = customer.id,
            loanAmount = 0.0,
            interestAmount = 0.0,
            weeklyAmount = 0.0,
            totalWeeks = 10,
            startDate = 0L,
            status = "NONE",
            notes = "",
            paidAmount = 0.0,
            uuid = "",
            lastModified = 0L
        )

        val lastPayment = remember(customerPayments) {
            customerPayments
                .filter { it.status != "DELETED" }
                .maxByOrNull { it.paymentDate }
        }

        val lastPaymentInfo = remember(lastPayment) {
            lastPayment?.let { lp ->
                val sdfDateOnly = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val payDate = Date(lp.paymentDate)
                val payDateStr = sdfDateOnly.format(payDate)
                
                // Format time
                val sdfTime = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val payTimeStr = sdfTime.format(payDate)

                val nowCal = Calendar.getInstance()
                val payCal = Calendar.getInstance().apply { timeInMillis = lp.paymentDate }
                
                val relativeDay = if (nowCal.get(Calendar.YEAR) == payCal.get(Calendar.YEAR) &&
                    nowCal.get(Calendar.DAY_OF_YEAR) == payCal.get(Calendar.DAY_OF_YEAR)) {
                    "Today"
                } else {
                    val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DATE, -1) }
                    if (yesterdayCal.get(Calendar.YEAR) == payCal.get(Calendar.YEAR) &&
                        yesterdayCal.get(Calendar.DAY_OF_YEAR) == payCal.get(Calendar.DAY_OF_YEAR)) {
                        "Yesterday"
                    } else {
                        payDateStr
                    }
                }
                
                "₹${lp.amountPaid.toLong()} on $relativeDay at $payTimeStr (Week ${lp.weekNumber})"
            }
        }

        if (true) {
            val totalToBePaid = activeLoan.loanAmount + activeLoan.interestAmount
            val remaining = maxOf(0.0, totalToBePaid - activeLoan.paidAmount)
            val progress = if (totalToBePaid > 0) (activeLoan.paidAmount / totalToBePaid).toFloat() else 0f

            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            colors = appColors.headerCardBg
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .border(1.dp, appColors.headerCardBorder, RoundedCornerShape(16.dp))
            ) {
                // Gradient box contents
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            // "name of customer top left with green colour text"
                            Text(
                                text = customer.name.uppercase(Locale.ROOT),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = appColors.textOnHeader,
                                letterSpacing = 1.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            if (customer.phone.isNotBlank()) {
                                Text(
                                    text = "Ph: ${customer.phone}",
                                    fontSize = 11.sp,
                                    color = if (appColors.isDark) Color.LightGray else appColors.textOnHeader.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Dividing Lime green animated progress bar between outstanding and principal+interest text
                    val limeColor = Color(0xFF84CC16) // Lime Green
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = limeColor,
                        trackColor = if (appColors.isDark) Color.White.copy(alpha = 0.2f) else appColors.textOnHeader.copy(alpha = 0.15f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // "amt receivable bottom left"
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = translate("Amt Receivable (Due)", language),
                                fontSize = 12.sp,
                                color = if (appColors.isDark) Color.LightGray else appColors.textOnHeader.copy(alpha = 0.7f)
                            )
                            AnimatedNumberText(
                                targetValue = remaining,
                                prefix = "₹",
                                fontSize = 28.sp, // Prominent metric hierarchy
                                fontWeight = FontWeight.Black,
                                color = appColors.textOnHeader,
                                delayMillis = 0,
                                durationMillis = 1600
                            )
                        }

                        // "amt disbursed principle+int format bottom right"
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text(
                                text = translate("Collected Amount", language),
                                fontSize = 11.sp,
                                color = if (appColors.isDark) Color.LightGray else appColors.textOnHeader.copy(alpha = 0.7f)
                            )
                            AnimatedNumberText(
                                targetValue = activeLoan.paidAmount,
                                prefix = "₹",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = appColors.textOnHeader,
                                delayMillis = 150,
                                durationMillis = 1600
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(
                        color = if (appColors.isDark) Color.DarkGray else appColors.textOnHeader.copy(alpha = 0.15f),
                        thickness = 0.5.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = translate("Borrowed Principal", language),
                                fontSize = 11.sp,
                                color = if (appColors.isDark) Color.LightGray else appColors.textOnHeader.copy(alpha = 0.7f)
                            )
                            AnimatedNumberText(
                                targetValue = activeLoan.loanAmount,
                                prefix = "₹",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = appColors.textOnHeader,
                                delayMillis = 300,
                                durationMillis = 1600
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text(
                                text = translate("Weekly Interest", language),
                                fontSize = 11.sp,
                                color = if (appColors.isDark) Color.LightGray else appColors.textOnHeader.copy(alpha = 0.7f)
                            )
                            AnimatedNumberText(
                                targetValue = activeLoan.interestAmount,
                                prefix = "₹",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = appColors.textOnHeader,
                                delayMillis = 450,
                                durationMillis = 1600
                            )
                        }
                    }

                    if (activeLoan.deduction > 0.0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = translate("Deducted Charges", language),
                                    fontSize = 11.sp,
                                    color = if (appColors.isDark) Color.LightGray else appColors.textOnHeader.copy(alpha = 0.7f)
                                )
                                AnimatedNumberText(
                                    targetValue = activeLoan.deduction,
                                    prefix = "₹",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = appColors.textOnHeader,
                                    delayMillis = 500,
                                    durationMillis = 1600
                                )
                            }
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text(
                                    text = translate("Actual Cash Disbursed", language),
                                    fontSize = 11.sp,
                                    color = if (appColors.isDark) Color.LightGray else appColors.textOnHeader.copy(alpha = 0.7f)
                                )
                                AnimatedNumberText(
                                    targetValue = activeLoan.loanAmount - activeLoan.deduction,
                                    prefix = "₹",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = appColors.textOnHeader,
                                    delayMillis = 550,
                                    durationMillis = 1600
                                )
                            }
                        }
                    }
                }
            }

            // Expected instalment indicator below the dashboard card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Expected Collection Target:",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.SemiBold
                )
                AnimatedNumberText(
                    targetValue = activeLoan.weeklyAmount,
                    prefix = "₹",
                    suffix = " / week",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    delayMillis = 600,
                    durationMillis = 1600
                )
            }



            // MINI UTILITY BOX: Centering shortcuts same color, other shade
            val hasPhone = !customer.phone.isNullOrBlank() && customer.phone.filter { it.isDigit() }.length >= 10
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = appColors.headerCardBg.map { it.copy(alpha = 0.85f) }
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        1.dp,
                        appColors.headerCardBorder.copy(alpha = 0.75f),
                        RoundedCornerShape(12.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasPhone) {
                        // 1. Call Logo
                        IconButton(onClick = { showCallAlert = true }) {
                            Icon(Icons.Default.Call, contentDescription = "Call Customer", tint = Color(0xFF4ADE80), modifier = Modifier.size(22.dp))
                        }
                        
                        // 2. SMS Logo
                        IconButton(onClick = { showSmsAlert = true }) {
                            Icon(Icons.Default.Sms, contentDescription = "SMS Remind", tint = Color(0xFF60A5FA), modifier = Modifier.size(22.dp))
                        }

                        // 3. WhatsApp Logo
                        IconButton(onClick = {
                            viewModel.triggerWhatsappReminder(context, customer, activeLoan)
                        }) {
                            Icon(WhatsAppIcon, contentDescription = "WhatsApp Remind", tint = Color.Unspecified, modifier = Modifier.size(24.dp))
                        }

                        // 4. UPI Pay Logo
                        IconButton(onClick = { showUpiAlert = true }) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(Color(0xFF0284C7), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "UPI",
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    // 4. File Statement Icon
                    IconButton(onClick = {
                        val statementBitmap = generateCustomerStatementBitmap(
                            context = context,
                            businessName = viewModel.businessName.value,
                            customerName = customer.name,
                            collectionDay = customer.collectionDay,
                            activeLoan = activeLoan,
                            payments = customerPayments.filter { it.loanCycleId == activeLoan.id },
                            themeName = viewModel.selectedTheme.value,
                            customizationCode = viewModel.statementCustomizationCode.value,
                            customerPhone = customer.phone
                        )
                        shareStatementImageToWhatsapp(context, statementBitmap, customer.name, customer.phone)
                    }) {
                        Icon(Icons.Default.ListAlt, contentDescription = "Share Active Statement", tint = Color(0xFFFBBF24), modifier = Modifier.size(24.dp))
                    }
                }
            }

            // Animated slide settings notification tag below the box
            var slideState by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                while(true) {
                    slideState = !slideState
                    delay(3000)
                }
            }
            val slideOffset by animateDpAsState(
                targetValue = if (slideState) 8.dp else (-8).dp,
                animationSpec = tween(durationMillis = 2000, easing = EaseInOutSine),
                label = "slide"
            )

            if ((customer.smsWeeklyReminder || customer.smsConfirmationOfEntry) && customer.phone.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationX = slideOffset.toPx() }
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val labelText = if (customer.smsWeeklyReminder && customer.smsConfirmationOfEntry) {
                        "🔔 Receives both Automated Reminders & Entry Notifications"
                    } else if (customer.smsWeeklyReminder) {
                        "✉️ Receives Weekly Auto SMS Reminders"
                    } else {
                        "💬 Receives Confirmation Entry SMS"
                    }
                    Text(
                        text = labelText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorAccentBlue
                    )
                }
            }
        }
        if (activeLoanReal == null) {
            // No active loan cycle state
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "No Active Cycle for ${customer.name}",
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (currentUserRole != "USER") Spacer(modifier = Modifier.width(8.dp))
                    if (currentUserRole != "USER") Button(
                        onClick = { viewModel.navigateTo(Screen.AddLoan(customer.id)) },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorAccentBlue),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Start First Loan Cycle", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        // Horizontal visual divider calendar grid
        PaymentCalendarMatrix(payments = customerPayments, loanCycles = loanCycles)

        Spacer(modifier = Modifier.height(12.dp))

        // Tab Content
        if (activeTab == 0) {
            if (activeLoans.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    activeLoans.forEach { targetActiveLoan ->
                        val activePayments = customerPayments.filter { it.loanCycleId == targetActiveLoan.id }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                ActiveLoanSection(
                                    activeLoan = targetActiveLoan,
                                    payments = activePayments,
                                    onAddPaymentClicked = { viewModel.navigateTo(Screen.RecordPayment(targetActiveLoan.id)) },
                                    onMarkSettledClicked = { viewModel.markLoanCycleSettled(targetActiveLoan.id) },
                                    onEditPayment = { payment -> editingPayment = payment },
                                    onDeletePayment = { payment -> deletingPaymentTarget = payment },
                                    onDeleteCycle = { deletingCycleTarget = targetActiveLoan },
                                    onEditCycleClicked = { viewModel.navigateTo(Screen.EditLoan(targetActiveLoan.id)) },
                                    currentUserRole = currentUserRole,
                                    onShareClicked = {
                                        val statementBitmap = generateCustomerStatementBitmap(
                                            context = context,
                                            businessName = viewModel.businessName.value,
                                            customerName = customer.name,
                                            collectionDay = customer.collectionDay,
                                            activeLoan = targetActiveLoan,
                                            payments = activePayments,
                                            themeName = viewModel.selectedTheme.value,
                                            customizationCode = viewModel.statementCustomizationCode.value,
                                            customerPhone = customer.phone
                                        )
                                        shareStatementImageToWhatsapp(context, statementBitmap, customer.name, customer.phone)
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No Active Loan Cycles", color = Color.Gray)
                }
            }
        } else {
            // Past Loans history page
            if (paidHistory.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No past completed accounts.", color = Color.Gray)
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    paidHistory.forEach { historicCycle ->
                        val historicPayments = customerPayments.filter { it.loanCycleId == historicCycle.id }
                        HistoricLoanCard(
                            historicCycle = historicCycle,
                            payments = historicPayments,
                            onDelete = { deletingCycleTarget = historicCycle }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Khatabook ledger timeline tabs (moved below active card / past history entries)
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = Color.Transparent,
            contentColor = ColorSlateDark,
            modifier = Modifier.testTag("accounts_past_history_toggle")
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text("Active Accounts (${activeLoans.size})", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text("Past History (${paidHistory.size})", fontWeight = FontWeight.Bold) }
            )
        }
    }

        // UP ARROWMARK WHEN I CLICK MOVE TO THAT DAYS DASHBOARD OR COSTOMERS DASHBOARD THIS SHOULD BE SHOWN ONLY IN DAY PAGES AND IN CUSTOMERDSHBOARD WHERE THERE R MORE THAN 10 COLLECTION ENTRIES
        if (customerPayments.size > 10 && scrollState.value > 300) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(48.dp)
                    .background(appColors.primaryAccent, CircleShape)
                    .clickable {
                        coroutineScope.launch {
                            scrollState.animateScrollTo(0)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Scroll to top",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun ActiveLoanSection(
    activeLoan: LoanCycle,
    payments: List<WeeklyPayment>,
    onAddPaymentClicked: () -> Unit,
    onMarkSettledClicked: () -> Unit,
    onEditPayment: (WeeklyPayment) -> Unit,
    onDeletePayment: (WeeklyPayment) -> Unit,
    onDeleteCycle: () -> Unit,
    onEditCycleClicked: () -> Unit,
    onShareClicked: () -> Unit,
    currentUserRole: String = "ADMIN"
) {
    val totalToBePaid = activeLoan.loanAmount + activeLoan.interestAmount
    val remaining = totalToBePaid - activeLoan.paidAmount
    val appColors = LocalAppThemeColors.current

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        if (currentUserRole != "USER") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShiningButton(
                    onClick = onAddPaymentClicked,
                    color = appColors.primaryAccent,
                    contentColor = Color.White,
                    modifier = Modifier.weight(1.0f),
                    text = "+ Collections"
                )

                if (remaining <= 0) {
                    ShiningButton(
                        onClick = onMarkSettledClicked,
                        color = appColors.secondaryAccent,
                        contentColor = Color.White,
                        text = "Settle Account"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Transaction History",
                fontWeight = FontWeight.Bold,
                color = ColorSlateDark,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text("Receipt History", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(6.dp))

        val nonZeroPayments = remember(payments) { payments.filter { it.amountPaid > 0.0 } }
        if (nonZeroPayments.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No payments recorded yet.", fontSize = 12.sp, color = Color.Gray)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                nonZeroPayments.forEach { payment ->
                    PaymentHistoryRow(
                        payment = payment,
                        onEdit = { onEditPayment(payment) },
                        onDelete = { onDeletePayment(payment) },
                        currentUserRole = currentUserRole
                    )
                }
            }
        }
    }
}

@Composable
fun PaymentHistoryRow(
    payment: WeeklyPayment,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    currentUserRole: String = "ADMIN"
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left continuous vertical accent line representing credit success
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(6.dp)
                    .background(ColorGainGreen)
            )

            Row(
                modifier = Modifier
                    .weight(1.0f)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1.0f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Badge for Week Title
                        Box(
                            modifier = Modifier
                                .background(ColorGainGreenLight, RoundedCornerShape(6.dp))
                                .border(1.dp, ColorGainGreen.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "Week ${payment.weekNumber}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorGainGreen,
                                letterSpacing = 0.5.sp
                            )
                        }

                        // Amount Received display with visual green indicator
                        Text(
                            text = "₹${CurrencyFormatter.format(payment.amountPaid)}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ColorGainGreen
                        )
                        
                        Text(
                            text = "Paid",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Gray
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Date and Time displayed in distinct visual chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Date Chip
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(ColorSlateDark.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                .border(1.dp, ColorSlateDark.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = ColorSlateDark.copy(alpha = 0.6f),
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(payment.paymentDate)),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorSlateDark
                            )
                        }

                        // Time Chip
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(ColorAccentBlue.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                .border(1.dp, ColorAccentBlue.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = ColorAccentBlue.copy(alpha = 0.7f),
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(payment.paymentDate)).uppercase(Locale.getDefault()),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorAccentBlue
                            )
                        }

                        // Notes/Method Label if existing
                        if (payment.notes.isNotEmpty()) {
                            val notesColor = if (payment.notes.uppercase(Locale.getDefault()) == "CASH") Color(0xFF15803D) else Color(0xFF1D4ED8)
                            val notesBgColor = if (payment.notes.uppercase(Locale.getDefault()) == "CASH") Color(0xFFDCFCE7) else Color(0xFFDBEAFE)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(notesBgColor, RoundedCornerShape(6.dp))
                                    .border(1.dp, notesColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = payment.notes,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = notesColor
                                )
                            }
                        }
                    }
                }

                // Options action button
                if (currentUserRole != "USER") {
                    var expandedMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { expandedMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Payment options",
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = expandedMenu,
                            onDismissRequest = { expandedMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Entry") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    expandedMenu = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Entry", color = ColorLossRed) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ColorLossRed, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    expandedMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoricLoanCard(historicCycle: LoanCycle, payments: List<WeeklyPayment>, onDelete: () -> Unit) {
    val totalPaid = historicCycle.paidAmount
    val totalExpected = historicCycle.loanAmount + historicCycle.interestAmount
    
    val lastPayment = payments.maxByOrNull { it.paymentDate }
    val lastPaymentDate = lastPayment?.paymentDate ?: historicCycle.startDate
    val diffMs = lastPaymentDate - historicCycle.startDate
    val diffDays = (diffMs / (1000L * 60 * 60 * 24)).coerceAtLeast(0).toInt()
    val actualWeeks = diffDays / 7.0
    val durationLabel = if (diffDays == 0) {
        "Paid in Same Day"
    } else {
        "$diffDays Days (~${String.format(Locale.getDefault(), "%.1f", actualWeeks)} Weeks)"
    }
    
    val flatRate = if (historicCycle.loanAmount > 0) (historicCycle.interestAmount / historicCycle.loanAmount) * 100.0 else 0.0
    val effectiveDays = maxOf(1, diffDays)
    val annualizedRate = flatRate * (365.0 / effectiveDays)

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Settled Contract",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorGainGreen
                    )
                    Text(
                        text = "Date: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(historicCycle.startDate))}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete from history", tint = ColorLossRed, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Disbursed Amount", fontSize = 11.sp, color = Color.Gray)
                    Text("₹${CurrencyFormatter.format(historicCycle.loanAmount)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorSlateDark)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Total Paid Back", fontSize = 11.sp, color = Color.Gray)
                    Text("₹${CurrencyFormatter.format(totalPaid)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorGainGreen)
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE2E8F0)))
            Spacer(modifier = Modifier.height(10.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text("Actual Tenure Taken", fontSize = 11.sp, color = Color.Gray)
                    Text(durationLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ColorSlateDark)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Interest % Paid", fontSize = 11.sp, color = Color.Gray)
                    Text(
                        text = "${String.format(Locale.getDefault(), "%.1f", flatRate)}% Flat",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorSlateDark
                    )
                    if (diffDays > 0) {
                        Text(
                            text = "(${String.format(Locale.getDefault(), "%.1f", annualizedRate)}% p.a. speed)",
                            fontSize = 10.sp,
                            color = ColorAccentBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

fun generateCustomerStatementBitmap(
    context: android.content.Context,
    businessName: String,
    customerName: String,
    collectionDay: String,
    activeLoan: com.example.data.LoanCycle,
    payments: List<com.example.data.WeeklyPayment>,
    themeName: String,
    customizationCode: String,
    customerPhone: String
): android.graphics.Bitmap {
    val lastPayments = payments.filter { it.amountPaid > 0.0 }.takeLast(30).reversed()
    val width = 800
    val dynamicRowHeight = if (lastPayments.isEmpty()) 100f else (lastPayments.size * 45f)
    val height = maxOf(800, (540f + dynamicRowHeight + 150f).toInt())
    
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    
    // Draw Background
    val bgPaint = android.graphics.Paint().apply {
        color = 0xFFF8FAFC.toInt() // Slate 50
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
    
    // Header Paint
    val headerPaint = android.graphics.Paint().apply {
        color = 0xFF0F172A.toInt() // Dark Slate 900
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawRect(0f, 0f, width.toFloat(), 180f, headerPaint)
    
    // Title
    val titlePaint = android.graphics.Paint().apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 36f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        isAntiAlias = true
    }
    canvas.drawText(businessName.ifEmpty { "MD FINANCE" }, 40f, 75f, titlePaint)
    
    val subtitlePaint = android.graphics.Paint().apply {
        color = 0xFF94A3B8.toInt()
        textSize = 20f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        isAntiAlias = true
    }
    canvas.drawText("Official Customer Loan Statement", 40f, 115f, subtitlePaint)
    
    // Date
    val datePaint = android.graphics.Paint().apply {
        color = 0xFF94A3B8.toInt()
        textSize = 18f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.RIGHT
    }
    val df = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
    canvas.drawText(df.format(java.util.Date()), width.toFloat() - 40f, 75f, datePaint)
    
    // Customer Info Card (Left Box)
    val cardPaint = android.graphics.Paint().apply {
        color = 0xFFFFFFFF.toInt()
        style = android.graphics.Paint.Style.FILL
    }
    val borderPaint = android.graphics.Paint().apply {
        color = 0xFFE2E8F0.toInt()
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2f
    }
    canvas.drawRoundRect(40f, 210f, 380f, 390f, 16f, 16f, cardPaint)
    canvas.drawRoundRect(40f, 210f, 380f, 390f, 16f, 16f, borderPaint)
    
    val textPaint = android.graphics.Paint().apply {
        color = 0xFF1E293B.toInt()
        textSize = 22f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        isAntiAlias = true
    }
    
    val labelPaint = android.graphics.Paint().apply {
        color = 0xFF64748B.toInt()
        textSize = 18f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        isAntiAlias = true
    }
    
    // Changed "Client Profile" to "Customer Details"
    canvas.drawText("Customer Details", 60f, 245f, textPaint)
    
    canvas.drawText("Name: $customerName", 60f, 285f, labelPaint)
    canvas.drawText("Phone: $customerPhone", 60f, 315f, labelPaint)
    canvas.drawText("Collection Day: $collectionDay", 60f, 345f, labelPaint)
    
    // Loan details card (Right Box)
    canvas.drawRoundRect(410f, 210f, width.toFloat() - 40f, 410f, 16f, 16f, cardPaint)
    canvas.drawRoundRect(410f, 210f, width.toFloat() - 40f, 410f, 16f, 16f, borderPaint)
    
    canvas.drawText("Loan Summary", 430f, 245f, textPaint)
    
    val amtPaid = payments.sumOf { it.amountPaid }.toLong()
    val amtDisbursedVal = (activeLoan.loanAmount - activeLoan.deduction).toLong()
    val totalContractVal = (activeLoan.loanAmount + activeLoan.interestAmount).toLong()
    val outstandingVal = totalContractVal - amtPaid
    
    canvas.drawText("Amount of Dispersal: ₹$amtDisbursedVal", 430f, 280f, labelPaint)
    canvas.drawText("Total Contract Value: ₹$totalContractVal", 430f, 310f, labelPaint)
    canvas.drawText("Amt Paid: ₹$amtPaid", 430f, 340f, labelPaint)
    canvas.drawText("Outstanding: ₹$outstandingVal", 430f, 370f, labelPaint)
    
    // Payments Table Title (Simplified to "Transaction History")
    canvas.drawText("Transaction History", 40f, 440f, textPaint)
    
    // Table Header
    val thPaint = android.graphics.Paint().apply {
        color = 0xFFF1F5F9.toInt()
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawRect(40f, 460f, width.toFloat() - 40f, 505f, thPaint)
    canvas.drawRect(40f, 460f, width.toFloat() - 40f, 505f, borderPaint)
    
    val thTextPaint = android.graphics.Paint().apply {
        color = 0xFF334155.toInt()
        textSize = 18f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        isAntiAlias = true
    }
    canvas.drawText("#", 60f, 490f, thTextPaint)
    canvas.drawText("Date", 120f, 490f, thTextPaint)
    canvas.drawText("Amnt Rec.", 400f, 490f, thTextPaint)
    canvas.drawText("UPI / Cash", 600f, 490f, thTextPaint)
    
    var currentY = 540f
    
    val rowPaint = android.graphics.Paint().apply {
        color = 0xFF334155.toInt()
        textSize = 18f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        isAntiAlias = true
    }
    
    val boldRowPaint = android.graphics.Paint().apply {
        color = 0xFF16A34A.toInt() // Green
        textSize = 18f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        isAntiAlias = true
    }
    
    val rowDateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
    lastPayments.forEachIndexed { index, p ->
        val pDateStr = rowDateFormat.format(java.util.Date(p.paymentDate))
        val isOnline = !p.upiTxnId.isNullOrEmpty() || 
                       p.notes.contains("Online", ignoreCase = true) || 
                       p.notes.contains("UPI", ignoreCase = true) || 
                       p.notes.contains("GPay", ignoreCase = true) || 
                       p.notes.contains("Bank", ignoreCase = true)
        val pModeStr = if (isOnline) "Online" else "Cash"
        canvas.drawText((index + 1).toString(), 60f, currentY, rowPaint)
        canvas.drawText(pDateStr, 120f, currentY, rowPaint)
        canvas.drawText("₹${p.amountPaid.toLong()}", 400f, currentY, boldRowPaint)
        canvas.drawText(pModeStr, 600f, currentY, rowPaint)
        
        // draw divider
        canvas.drawLine(40f, currentY + 15f, width.toFloat() - 40f, currentY + 15f, borderPaint)
        currentY += 45f
    }
    
    if (lastPayments.isEmpty()) {
        canvas.drawText("No payments found.", 120f, 550f, rowPaint)
        currentY = 600f
    }
    
    // Add "THANK YOU" at the end of the transaction history
    val thankYouY = currentY + 50f
    val thankYouPaint = android.graphics.Paint().apply {
        color = 0xFF1E293B.toInt()
        textSize = 24f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    canvas.drawText("THANK YOU", (width / 2).toFloat(), thankYouY, thankYouPaint)
    
    return bitmap
}
