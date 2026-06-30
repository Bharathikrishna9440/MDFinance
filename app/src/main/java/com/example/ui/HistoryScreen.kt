package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Calendar
import com.example.util.CurrencyFormatter

import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues(16.dp)
) {
    val appColors = LocalAppThemeColors.current
    val editLogs by viewModel.allEditLogs.collectAsStateWithLifecycle()
    val allCustomers by viewModel.allCustomers.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val unsyncedUuids by viewModel.unsyncedEditUuids.collectAsStateWithLifecycle()
    val unsyncedCount by viewModel.unsyncedLogCount.collectAsStateWithLifecycle()
    val syncStatus by viewModel.firebaseSyncStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sdf = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Fully Offline Secure Mode Banner Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFF0FDF4)
            ),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(
                1.dp,
                Color(0xFFBBF7D0)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.OfflinePin,
                    contentDescription = "Offline Secure Mode Active",
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = translate("Fully Offline Audit Trails", language),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF14532D)
                    )
                    Text(
                        text = translate("All additions, edits, and installment removals are securely logged on this device.", language),
                        fontSize = 11.sp,
                        color = Color(0xFF166534)
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = translate("System Edit Logs", language),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            if (editLogs.isNotEmpty()) {
                TextButton(
                    onClick = { viewModel.clearEditLogs() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear Logs",
                        tint = Color.Red,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = translate("Clear Logs", language),
                        color = Color.Red,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (editLogs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray
                    )
                    Text(
                        text = translate("No activity recorded yet.", language),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(editLogs, key = { it.id }) { log ->
                    val customerExists = remember(allCustomers) { allCustomers.any { it.id == log.customerId } }
                    val deletedToastMsg = translate("Customer was deleted. Revert deletion to view profile.", language)
                    
                    val cardBgColor = when {
                        log.actionType.contains("DELETE") -> Color(0xFFFEF2F2) // Light Red
                        log.actionType.contains("CREATE") || log.actionType.contains("RECORD") -> Color(0xFFF0FDF4) // Light Green
                        else -> Color(0xFFEFF6FF) // Light Blue
                    }
                    
                    val accentColor = when {
                        log.actionType.contains("DELETE") -> Color(0xFFEF4444)
                        log.actionType.contains("CREATE") || log.actionType.contains("RECORD") -> Color(0xFF22C55E)
                        else -> Color(0xFF3B82F6)
                    }

                    val icon = when {
                        log.actionType.contains("DELETE") -> Icons.Default.Delete
                        log.actionType.contains("CREATE") || log.actionType.contains("RECORD") -> Icons.Default.CheckCircle
                        else -> Icons.Default.Edit
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (customerExists) {
                                    viewModel.navigateTo(Screen.CustomerDetail(log.customerId))
                                } else {
                                    Toast.makeText(context, deletedToastMsg, Toast.LENGTH_LONG).show()
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.15f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(accentColor.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    val parts = log.actionDescription.split(": ")
                                    if (parts.size > 1) {
                                        Text(
                                            text = parts[0],
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black
                                        )
                                        // Parse detailed change factors
                                        val changeInfo = parts[1]
                                        val factorsListString = changeInfo.substringAfter(")").trim()
                                        val factorCount = changeInfo.substringBefore(")", "0").replace("(", "").trim()
                                        
                                        if (factorsListString.isNotEmpty()) {
                                            Column(
                                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp, start = 4.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = "($factorCount)",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = accentColor
                                                )
                                                factorsListString.split(", ").forEach { factor ->
                                                     Row(
                                                         verticalAlignment = Alignment.CenterVertically,
                                                         horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                     ) {
                                                         Text(
                                                             text = "•",
                                                             fontSize = 12.sp,
                                                             color = accentColor,
                                                             fontWeight = FontWeight.Bold
                                                         )
                                                         Text(
                                                             text = factor,
                                                             fontSize = 12.sp,
                                                             color = Color.DarkGray,
                                                             fontWeight = FontWeight.Medium
                                                         )
                                                     }
                                                }
                                            }
                                        } else {
                                            Text(
                                                text = changeInfo,
                                                fontSize = 12.sp,
                                                color = Color.DarkGray,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = log.actionDescription,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black
                                        )
                                    }
                                    val isUnsynced = log.uuid in unsyncedUuids
                                    val statusDotColor = when {
                                        isUnsynced -> Color(0xFFEF4444) // Red
                                        log.customerName == "Firebase Sync" || log.actionType == "SYNC_VERIFY" -> Color.Gray // Grey
                                        else -> Color(0xFF22C55E) // Green
                                    }
                                    val statusText = when {
                                        isUnsynced -> translate("Pending Sync", language)
                                        log.customerName == "Firebase Sync" || log.actionType == "SYNC_VERIFY" -> translate("System Sync", language)
                                        else -> translate("Synced", language)
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(statusDotColor, shape = androidx.compose.foundation.shape.CircleShape)
                                        )
                                        Text(
                                            text = sdf.format(java.util.Date(log.timestamp)) + " • " + statusText,
                                            fontSize = 11.sp,
                                            color = Color.DarkGray
                                        )
                                        if (customerExists) {
                                            Text(
                                                text = "• " + translate("View Profile", language),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = appColors.primaryAccent
                                            )
                                        } else {
                                            Text(
                                                text = "• " + translate("Click Revert to Restore", language),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color.Red
                                            )
                                        }
                                    }
                                }
                            }

                            // Revert Button
                            IconButton(
                                onClick = { viewModel.revertEditLog(log, context) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color.White, shape = RoundedCornerShape(16.dp))
                                    .border(1.dp, Color.LightGray, shape = RoundedCornerShape(16.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Undo Action",
                                    tint = Color.DarkGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}



data class DailyStats(
    val dateLabel: String,
    val collection: Double,
    val dueCreated: Double,
    val interestCollected: Double
)

@Composable
fun FullLedgerHistoryScreen(viewModel: FinanceViewModel) {
    val allPayments by viewModel.allPayments.collectAsStateWithLifecycle()
    val allLoanCycles by viewModel.allLoanCycles.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val appColors = LocalAppThemeColors.current

    var hideEmptyDays by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Support Days, Months, Year format, with Date Range always visible!
    var activeFilter by remember { mutableStateOf("30D") } // "30D", "3M", "6M", "1Y"
    var customFromDate by remember {
        mutableStateOf(
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }.run {
                SimpleDateFormat("dd/MM/yyyy", Locale.US).format(time)
            }
        )
    }
    var customToDate by remember {
        mutableStateOf(
            SimpleDateFormat("dd/MM/yyyy", Locale.US).format(java.util.Date())
        )
    }

    // Cache computed list for performance based strictly on customFromDate & customToDate
    val rawDailyStatsList = remember(allPayments, allLoanCycles, language, customFromDate, customToDate) {
        val list = mutableListOf<DailyStats>()
        val currentLocale = when(language) {
            "Tamil" -> Locale("ta")
            "Hindi" -> Locale("hi", "IN")
            "Telugu" -> Locale("te", "IN")
            else -> Locale.US
        }
        val sdf = SimpleDateFormat("dd-MMM-yyyy", currentLocale)
        val dayFormat = SimpleDateFormat("EEEE", currentLocale)
        val loanMap = allLoanCycles.associateBy { it.id }
        
        val sdfParser = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        val fromCal = Calendar.getInstance()
        val toCal = Calendar.getInstance()
        try {
            sdfParser.parse(customFromDate)?.let { fromCal.time = it }
        } catch (e: Exception) {
            fromCal.add(Calendar.DAY_OF_YEAR, -30)
        }
        try {
            sdfParser.parse(customToDate)?.let { toCal.time = it }
        } catch (e: Exception) {}
        
        // Normalize times to capture full day boundaries
        fromCal.set(Calendar.HOUR_OF_DAY, 0)
        fromCal.set(Calendar.MINUTE, 0)
        fromCal.set(Calendar.SECOND, 0)
        fromCal.set(Calendar.MILLISECOND, 0)
        
        toCal.set(Calendar.HOUR_OF_DAY, 23)
        toCal.set(Calendar.MINUTE, 59)
        toCal.set(Calendar.SECOND, 59)
        toCal.set(Calendar.MILLISECOND, 999)
        
        val tempCal = Calendar.getInstance().apply { timeInMillis = toCal.timeInMillis }
        while (tempCal.timeInMillis >= fromCal.timeInMillis) {
            val startMs = Calendar.getInstance().apply {
                timeInMillis = tempCal.timeInMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            val endMs = Calendar.getInstance().apply {
                timeInMillis = tempCal.timeInMillis
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis
            
            val dayPayments = allPayments.filter { it.paymentDate in startMs..endMs }
            val dayPaymentsSum = dayPayments.sumOf { it.amountPaid }
            val dayLoanSum = allLoanCycles.filter { it.startDate in startMs..endMs }.sumOf { it.loanAmount }
            val dayInterestSum = dayPayments.sumOf { p ->
                val loan = loanMap[p.loanCycleId]
                if (loan != null) {
                    val total = loan.loanAmount + loan.interestAmount
                    val ratio = if (total > 0.0) loan.interestAmount / total else 0.0
                    p.amountPaid * ratio
                } else {
                    0.0
                }
            }
            
            val dateLabel = "${sdf.format(tempCal.time)} (${dayFormat.format(tempCal.time)})"
            list.add(DailyStats(dateLabel, dayPaymentsSum, dayLoanSum, dayInterestSum))
            
            tempCal.add(Calendar.DAY_OF_YEAR, -1)
            if (list.size > 2000) break // safety guard
        }
        list
    }

    val filteredList = remember(rawDailyStatsList, hideEmptyDays, searchQuery) {
        rawDailyStatsList.filter { stats ->
            // Search filter
            val matchesSearch = searchQuery.isBlank() || stats.dateLabel.contains(searchQuery, ignoreCase = true)
            // Empty filter
            val matchesEmpty = !hideEmptyDays || (stats.collection > 0 || stats.dueCreated > 0 || stats.interestCollected > 0)
            matchesSearch && matchesEmpty
        }
    }

    val totalCollectionsSum = remember(filteredList) { filteredList.sumOf { it.collection } }
    val totalDuesCreatedSum = remember(filteredList) { filteredList.sumOf { it.dueCreated } }
    val totalInterestSum = remember(filteredList) { filteredList.sumOf { it.interestCollected } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Range & Hide Switch Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Days / Months / Year limit selector in a scrollable container
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
            ) {
                val filterOptions = listOf(
                    Triple("30D", translate("30 Days", language), "30D"),
                    Triple("3M", translate("3 Months", language), "3M"),
                    Triple("6M", translate("6 Months", language), "6M"),
                    Triple("1Y", translate("1 Year", language), "1Y")
                )
                
                filterOptions.forEach { opt ->
                    val isSelected = activeFilter == opt.first
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isSelected) appColors.primaryAccent else Color(0xFFF1F5F9),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                val sdfParser = SimpleDateFormat("dd/MM/yyyy", Locale.US)
                                val cal = Calendar.getInstance()
                                val toStr = sdfParser.format(cal.time)
                                when (opt.first) {
                                    "30D" -> cal.add(Calendar.DAY_OF_YEAR, -30)
                                    "3M" -> cal.add(Calendar.MONTH, -3)
                                    "6M" -> cal.add(Calendar.MONTH, -6)
                                    "1Y" -> cal.add(Calendar.YEAR, -1)
                                }
                                val fromStr = sdfParser.format(cal.time)
                                customFromDate = fromStr
                                customToDate = toStr
                                activeFilter = opt.first
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = opt.second,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else Color.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Hide Empty Switch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = translate("Hide Empty", language),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black.copy(alpha = 0.7f)
                )
                Switch(
                    checked = hideEmptyDays,
                    onCheckedChange = { hideEmptyDays = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = appColors.primaryAccent,
                        checkedTrackColor = appColors.primaryAccent.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.scale(0.75f)
                )
            }
        }

        // Custom Selection Date Pickers - ALWAYS VISIBLE NOW
        val context = LocalContext.current
        fun showDatePickerDialog(currentVal: String, onSelected: (String) -> Unit) {
            try {
                val sdfParser = SimpleDateFormat("dd/MM/yyyy", Locale.US)
                val calendar = Calendar.getInstance()
                try {
                    sdfParser.parse(currentVal)?.let { calendar.time = it }
                } catch (e: Exception) {}
                
                android.app.DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        val newCal = Calendar.getInstance()
                        newCal.set(Calendar.YEAR, year)
                        newCal.set(Calendar.MONTH, month)
                        newCal.set(Calendar.DAY_OF_MONTH, day)
                        val formattedDate = sdfParser.format(newCal.time)
                        onSelected(formattedDate)
                        activeFilter = "" // Clear the quick filter state when manual dates are selected
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                ).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { showDatePickerDialog(customFromDate) { customFromDate = it } },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors.primaryAccent),
                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 6.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = translate("From Date", language),
                        fontSize = 9.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = customFromDate,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }

            OutlinedButton(
                onClick = { showDatePickerDialog(customToDate) { customToDate = it } },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors.primaryAccent),
                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 6.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = translate("To Date", language),
                        fontSize = 9.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = customToDate,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    translate("Search date or day name...", language),
                    fontSize = 13.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            },
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = appColors.primaryAccent,
                unfocusedBorderColor = Color(0xFFCBD5E1)
            )
        )

        // Summary Statistics Box
        Card(
            colors = CardDefaults.cardColors(containerColor = appColors.darkBg),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = translate("Total Collections", language),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "₹${CurrencyFormatter.format(totalCollectionsSum)}",
                        color = Color(0xFF4ADE80), // Light green
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(
                    modifier = Modifier
                        .height(35.dp)
                        .width(1.dp)
                        .background(Color.White.copy(alpha = 0.15f))
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = translate("Total Dues Created", language),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "₹${CurrencyFormatter.format(totalDuesCreatedSum)}",
                        color = Color(0xFF60A5FA), // Light blue
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(
                    modifier = Modifier
                        .height(35.dp)
                        .width(1.dp)
                        .background(Color.White.copy(alpha = 0.15f))
                )

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = translate("Total Profit", language),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "₹${CurrencyFormatter.format(totalInterestSum)}",
                        color = Color(0xFFC084FC), // Light purple
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End
                    )
                }
            }
        }

        // Daily Ledger Table
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Table Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFC))
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = translate("Date / Day", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color.Black.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1.3f)
                    )
                    Text(
                        text = translate("Collections", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color(0xFF15803D),
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = translate("Disbursed", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color(0xFF1D4ED8),
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = translate("Profit", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color(0xFF7E22CE),
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(0.9f)
                    )
                }

                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = translate("No daily records matched.", language),
                            color = Color.Gray,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(filteredList) { idx, stats ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (idx % 2 == 1) Color(0xFFF8FAFC) else Color.White)
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stats.dateLabel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Black,
                                    modifier = Modifier.weight(1.3f)
                                )
                                Text(
                                    text = if (stats.collection > 0) "₹${CurrencyFormatter.format(stats.collection)}" else "—",
                                    fontSize = 12.sp,
                                    fontWeight = if (stats.collection > 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (stats.collection > 0) Color(0xFF16A34A) else Color.Gray,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = if (stats.dueCreated > 0) "₹${CurrencyFormatter.format(stats.dueCreated)}" else "—",
                                    fontSize = 12.sp,
                                    fontWeight = if (stats.dueCreated > 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (stats.dueCreated > 0) Color(0xFF2563EB) else Color.Gray,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = if (stats.interestCollected > 0) "₹${CurrencyFormatter.format(stats.interestCollected)}" else "—",
                                    fontSize = 12.sp,
                                    fontWeight = if (stats.interestCollected > 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (stats.interestCollected > 0) Color(0xFF9333EA) else Color.Gray,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(0.9f)
                                )
                            }
                            if (idx < filteredList.size - 1) {
                                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}
