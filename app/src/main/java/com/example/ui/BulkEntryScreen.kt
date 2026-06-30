package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkEntryScreen(
    day: String,
    viewModel: FinanceViewModel
) {
    val context = LocalContext.current
    val language by viewModel.language.collectAsStateWithLifecycle()
    val appColors = LocalAppThemeColors.current

    val allCustomers by viewModel.allCustomers.collectAsStateWithLifecycle()
    val activeLoans by viewModel.activeLoanCycles.collectAsStateWithLifecycle()
    val allPayments by viewModel.allPayments.collectAsStateWithLifecycle()

    val sdf = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()) }
    var paymentDateStr by remember { mutableStateOf(sdf.format(java.util.Date())) }
    var isTimeSynced by remember { mutableStateOf(false) }
    var isSyncingTime by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val (onlineTime, synced) = com.example.util.OnlineTimeHelper.getOnlineTimeOrLocal()
        paymentDateStr = sdf.format(java.util.Date(onlineTime))
        isTimeSynced = synced
        isSyncingTime = false
    }

    val parsedTimestamp = try {
        sdf.parse(paymentDateStr)?.time ?: System.currentTimeMillis()
    } catch (e: Exception) {
        System.currentTimeMillis()
    }

    val datePickerDialog = remember(parsedTimestamp) {
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val calendar = java.util.Calendar.getInstance().apply {
                    timeInMillis = parsedTimestamp
                    set(java.util.Calendar.YEAR, year)
                    set(java.util.Calendar.MONTH, month)
                    set(java.util.Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                paymentDateStr = sdf.format(java.util.Date(calendar.timeInMillis))
            },
            java.util.Calendar.getInstance().apply { timeInMillis = parsedTimestamp }.get(java.util.Calendar.YEAR),
            java.util.Calendar.getInstance().apply { timeInMillis = parsedTimestamp }.get(java.util.Calendar.MONTH),
            java.util.Calendar.getInstance().apply { timeInMillis = parsedTimestamp }.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    val timePickerDialog = remember(parsedTimestamp) {
        android.app.TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val calendar = java.util.Calendar.getInstance().apply {
                    timeInMillis = parsedTimestamp
                    set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                    set(java.util.Calendar.MINUTE, minute)
                }
                paymentDateStr = sdf.format(java.util.Date(calendar.timeInMillis))
            },
            java.util.Calendar.getInstance().apply { timeInMillis = parsedTimestamp }.get(java.util.Calendar.HOUR_OF_DAY),
            java.util.Calendar.getInstance().apply { timeInMillis = parsedTimestamp }.get(java.util.Calendar.MINUTE),
            false
        )
    }

    // Filter customers for this day
    val targetCustomers = remember(allCustomers, day) {
        allCustomers.filter { it.collectionDay.trim().equals(day.trim(), ignoreCase = true) && it.status == "ACTIVE" }
            .sortedBy { it.customOrder }
    }

    // Helper to check if a payment is from the selected date
    val isToday: (Long) -> Boolean = { timestamp ->
        val cal1 = java.util.Calendar.getInstance()
        cal1.timeInMillis = timestamp
        val cal2 = java.util.Calendar.getInstance()
        cal2.timeInMillis = parsedTimestamp
        cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
                cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
    }

    // Prepare active/inactive customers rows helper
    val rows = remember(targetCustomers, activeLoans, allPayments, parsedTimestamp) {
        targetCustomers.map { customer ->
            val activeLoan = activeLoans.find { it.customerId == customer.id && it.status == "ACTIVE" }
            if (activeLoan != null) {
                // Find active payment recorded today
                val todayPayment = allPayments.find {
                    it.loanCycleId == activeLoan.id &&
                    it.status == "ACTIVE" &&
                    isToday(it.paymentDate)
                }

                val cyclePayments = allPayments.filter { it.loanCycleId == activeLoan.id && it.status == "ACTIVE" && it.amountPaid > 0.0 && it.weekNumber > 0 }
                val nextSuggestWeek = if (todayPayment != null) {
                    todayPayment.weekNumber
                } else {
                    (cyclePayments.maxOfOrNull { it.weekNumber } ?: 0) + 1
                }

                BulkRowItem(
                    customer = customer,
                    activeLoan = activeLoan,
                    defaultWeek = nextSuggestWeek,
                    defaultAmount = activeLoan.weeklyAmount,
                    todayPayment = todayPayment
                )
            } else {
                BulkRowItem(
                    customer = customer,
                    activeLoan = null,
                    defaultWeek = 1,
                    defaultAmount = 0.0,
                    todayPayment = null
                )
            }
        }
    }

    // Keep states keyed by customer.id instead of loan cycle id and remember keyed on parsedTimestamp
    val amountStates = remember(rows, parsedTimestamp) {
        mutableStateMapOf<Int, String>().apply {
            rows.forEach { row ->
                if (row.activeLoan != null) {
                    if (row.todayPayment != null) {
                        put(row.customer.id, row.todayPayment.amountPaid.toInt().toString())
                    } else {
                        put(row.customer.id, "")
                    }
                }
            }
        }
    }

    val weekStates = remember(rows, parsedTimestamp) {
        mutableStateMapOf<Int, String>().apply {
            rows.forEach { row ->
                if (row.activeLoan != null) {
                    put(row.customer.id, row.defaultWeek.toString())
                }
            }
        }
    }

    val isCashStates = remember(rows, parsedTimestamp) {
        mutableStateMapOf<Int, Boolean>().apply {
            rows.forEach { row ->
                if (row.activeLoan != null) {
                    if (row.todayPayment != null) {
                        val isCash = row.todayPayment.notes.contains("Cash", ignoreCase = true) || !row.todayPayment.notes.contains("Online", ignoreCase = true)
                        put(row.customer.id, isCash)
                    } else {
                        put(row.customer.id, true) // true = Cash, false = Bank (Online)
                    }
                }
            }
        }
    }

    // Keyed by Customer.id for inactive customers' new loan Principal & Interest inputs
    val principalStates = remember(rows, parsedTimestamp) {
        mutableStateMapOf<Int, String>().apply {
            rows.forEach { row ->
                if (row.activeLoan == null) {
                    put(row.customer.id, "")
                }
            }
        }
    }

    val interestStates = remember(rows, parsedTimestamp) {
        mutableStateMapOf<Int, String>().apply {
            rows.forEach { row ->
                if (row.activeLoan == null) {
                    put(row.customer.id, "")
                }
            }
        }
    }

    val hasChangesToTodayPayments = remember(rows, amountStates, weekStates, isCashStates) {
        rows.any { row ->
            val todayPayment = row.todayPayment
            if (todayPayment != null) {
                val currentAmtStr = amountStates[row.customer.id] ?: ""
                val currentAmtDouble = currentAmtStr.toDoubleOrNull() ?: 0.0
                val originalAmtDouble = todayPayment.amountPaid

                val currentWeekStr = weekStates[row.customer.id] ?: ""
                val currentWeekInt = currentWeekStr.toIntOrNull() ?: row.defaultWeek
                val originalWeekInt = todayPayment.weekNumber

                val currentIsCash = isCashStates[row.customer.id] ?: true
                val originalIsCash = todayPayment.notes.contains("Cash", ignoreCase = true) || !todayPayment.notes.contains("Online", ignoreCase = true)

                (currentAmtDouble != originalAmtDouble) ||
                (currentWeekInt != originalWeekInt) ||
                (currentIsCash != originalIsCash)
            } else {
                false
            }
        }
    }

    if (rows.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = translate("No active or inactive customers found for this day.", language),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp)
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
        ) {

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = translate("Bulk Collection Date & Time", language),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (isSyncingTime) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = appColors.primaryAccent
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Syncing...",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Medium
                                )
                            } else if (isTimeSynced) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFE8F5E9))
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "🟢 ONLINE SYNCED",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFFFF3E0))
                                        .clickable {
                                            isSyncingTime = true
                                            coroutineScope.launch {
                                                val (onlineTime, synced) = com.example.util.OnlineTimeHelper.getOnlineTimeOrLocal()
                                                paymentDateStr = sdf.format(java.util.Date(onlineTime))
                                                isTimeSynced = synced
                                                isSyncingTime = false
                                            }
                                        }
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "⚠️ OFFLINE (TAP)",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE65100)
                                    )
                                }
                            }
                        }
                    }

                    // Side-by-side boxes for Date & Time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Date Box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFF1E293B).copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF1E293B).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .clickable { datePickerDialog.show() }
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(translate("Date", language), fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(parsedTimestamp)),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                            }
                        }

                        // Time Box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(appColors.primaryAccent.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .border(1.dp, appColors.primaryAccent.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .clickable { timePickerDialog.show() }
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(translate("Time", language), fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(parsedTimestamp)).uppercase(java.util.Locale.getDefault()),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = appColors.primaryAccent
                                )
                            }
                        }
                    }

                    // Shortcuts Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val now = System.currentTimeMillis()
                        val dayMs = 24 * 60 * 60 * 1000L
                        val shortcuts = listOf(
                            "Today" to now,
                            "Yesterday" to now - dayMs,
                            "2 Days" to now - (2 * dayMs),
                            "1 Week" to now - (7 * dayMs)
                        )

                        shortcuts.forEach { (lbl, ts) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                                    .clickable {
                                        paymentDateStr = sdf.format(java.util.Date(ts))
                                    }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(lbl, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                            }
                        }
                    }
                }
            }

                LazyColumn(
                    modifier = Modifier
                        .weight(1.0f)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    itemsIndexed(rows) { index, item ->
                        val customerId = item.customer.id
                        val activeLoan = item.activeLoan

                        if (activeLoan != null) {
                            val amountVal = amountStates[customerId] ?: ""
                            val weekVal = weekStates[customerId] ?: ""
                            val isCash = isCashStates[customerId] ?: true

                            // Keep card style plain white/grey
                            val cardBg = Color.White
                            val cardBorderColor = Color(0xFFE2E8F0)

                            Card(
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                border = BorderStroke(1.dp, cardBorderColor),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    // Row 1: "1. shaik ranjanbe" (i.e. number customOrder and customer name)
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${item.customer.customOrder}. ${item.customer.name}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Color(0xFF1E293B),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (item.customer.city.isNotBlank()) {
                                            Text(
                                                text = " (${item.customer.city})",
                                                fontSize = 11.sp,
                                                color = Color.Gray,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    // Row 2: logo, week number box (height up, width dynamic), amount box (width dynamic), red multiply icon in right corner
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 1. Logo of Cash vs Bank
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isCash) Color(0xFFE8F5E9) else Color(0xFFE1F5FE)
                                                )
                                                .clickable {
                                                    isCashStates[customerId] = !isCash
                                                }
                                        ) {
                                            Icon(
                                                imageVector = if (isCash) Icons.Default.Payments else Icons.Default.AccountBalance,
                                                contentDescription = if (isCash) "Cash Mode" else "Online Mode",
                                                tint = if (isCash) Color(0xFF2E7D32) else Color(0xFF0288D1),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // 2. Week number box (decreased width, increased height, dynamic width based on typing digits)
                                        val weekBoxWidth = maxOf(48.dp, (38 + 12 * weekVal.length).dp)
                                        OutlinedTextField(
                                            value = weekVal,
                                            onValueChange = { newVal -> weekStates[customerId] = newVal.filter { c -> c.isDigit() } },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            textStyle = LocalTextStyle.current.copy(
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                color = Color.Black
                                            ),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.Black,
                                                unfocusedTextColor = Color.Black,
                                                focusedBorderColor = Color.Black,
                                                unfocusedBorderColor = Color.Black,
                                                focusedContainerColor = Color.White,
                                                unfocusedContainerColor = Color.White
                                            ),
                                            singleLine = true,
                                            modifier = Modifier
                                                .width(weekBoxWidth)
                                                .height(64.dp)
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // 3. Amount box (initially 3 digits wide, expands as digits increase)
                                        val amountBoxWidth = maxOf(80.dp, (56 + 12 * amountVal.length).dp)
                                        OutlinedTextField(
                                            value = amountVal,
                                            onValueChange = { newVal -> amountStates[customerId] = newVal.filter { c -> c.isDigit() } },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            textStyle = LocalTextStyle.current.copy(
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.End,
                                                color = Color.Black
                                            ),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.Black,
                                                unfocusedTextColor = Color.Black,
                                                focusedBorderColor = Color.Black,
                                                unfocusedBorderColor = Color.Black,
                                                focusedContainerColor = Color.White,
                                                unfocusedContainerColor = Color.White
                                            ),
                                            singleLine = true,
                                            modifier = Modifier
                                                .width(amountBoxWidth)
                                                .height(64.dp)
                                        )

                                        Spacer(modifier = Modifier.weight(1.0f))

                                        // 4. Red multiply mark within mini rounded red circle
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFFEE2E2))
                                                .clickable {
                                                    amountStates[customerId] = "0" // Did not pay
                                                }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Didn't Pay",
                                                tint = Color(0xFFDC2626),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Inactive customer card with principal & interest inputs
                            val principalVal = principalStates[customerId] ?: ""
                            val interestVal = interestStates[customerId] ?: ""
                            val cardBg = Color.White
                            val cardBorderColor = Color(0xFFE2E8F0)

                            Card(
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                border = BorderStroke(1.2.dp, cardBorderColor),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    // Row 1: Name, Number and city badge
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${item.customer.customOrder}. ${item.customer.name}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = Color(0xFF1E293B),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (item.customer.city.isNotBlank()) {
                                                Text(
                                                    text = " (${item.customer.city})",
                                                    fontSize = 11.sp,
                                                    color = Color.Gray,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        // Badge indicating no active loan
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFFFEE2E2), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = translate("NO LOAN", language),
                                                color = Color(0xFFDC2626),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // Row 2: Principal & Interest Side-by-Side text inputs
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = principalVal,
                                            onValueChange = { newVal -> principalStates[customerId] = newVal.filter { it.isDigit() } },
                                            label = { Text(translate("Principal (₹)", language), fontSize = 11.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f).height(64.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.Black,
                                                unfocusedTextColor = Color.Black,
                                                focusedBorderColor = Color.Black,
                                                unfocusedBorderColor = Color.Black,
                                                focusedContainerColor = Color.White,
                                                unfocusedContainerColor = Color.White
                                            ),
                                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                        )
                                        OutlinedTextField(
                                            value = interestVal,
                                            onValueChange = { newVal -> interestStates[customerId] = newVal.filter { it.isDigit() } },
                                            label = { Text(translate("Interest (₹)", language), fontSize = 11.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f).height(64.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.Black,
                                                unfocusedTextColor = Color.Black,
                                                focusedBorderColor = Color.Black,
                                                unfocusedBorderColor = Color.Black,
                                                focusedContainerColor = Color.White,
                                                unfocusedContainerColor = Color.White
                                            ),
                                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Submit Row at the bottom
                Surface(
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            var paymentsRecorded = 0
                            var loansCreated = 0
                            
                            rows.forEach { item ->
                                val customerId = item.customer.id
                                val activeLoan = item.activeLoan

                                if (activeLoan != null) {
                                    // Process active payment entry
                                    val amtStr = amountStates[customerId] ?: ""
                                    val amtDouble = amtStr.toDoubleOrNull()
                                    val weekNumber = weekStates[customerId]?.toIntOrNull() ?: item.defaultWeek
                                    val isCashPayment = isCashStates[customerId] ?: true
                                    val paymentTypeNote = if (isCashPayment) "Cash" else "Online"

                                    val existingTodayPayment = item.todayPayment
                                    if (existingTodayPayment != null) {
                                        // There was already a payment recorded today (or on selected date)
                                        if (amtDouble != null && amtStr.isNotBlank() && amtDouble > 0.0) {
                                            val hasChangeObj = amtDouble != existingTodayPayment.amountPaid || 
                                                               weekNumber != existingTodayPayment.weekNumber || 
                                                               isCashPayment != (existingTodayPayment.notes.contains("Cash", ignoreCase = true) || !existingTodayPayment.notes.contains("Online", ignoreCase = true))
                                            
                                            if (hasChangeObj) {
                                                viewModel.editWeeklyPayment(
                                                    context = context,
                                                    paymentId = existingTodayPayment.id,
                                                    loanCycleId = activeLoan.id,
                                                    amount = amtDouble,
                                                    weekNum = weekNumber,
                                                    paymentDate = parsedTimestamp,
                                                    notes = paymentTypeNote
                                                )
                                                paymentsRecorded++
                                            }
                                        } else {
                                            // Amount was cleared or set to 0. We should delete this payment!
                                            viewModel.deletePayment(existingTodayPayment.id, activeLoan.id)
                                            paymentsRecorded++
                                        }
                                    } else {
                                        // Standard recording
                                        if (amtDouble != null && amtStr.isNotBlank() && amtDouble >= 0.0) {
                                            viewModel.recordWeeklyPayment(
                                                context = context,
                                                loanCycleId = activeLoan.id,
                                                amount = amtDouble,
                                                weekNum = weekNumber,
                                                notes = paymentTypeNote,
                                                paymentDate = parsedTimestamp
                                            )
                                            paymentsRecorded++
                                        }
                                    }
                                } else {
                                    // Process inactive borrower new loan disbursement
                                    val pStr = principalStates[customerId] ?: ""
                                    val iStr = interestStates[customerId] ?: ""
                                    val pDouble = pStr.toDoubleOrNull()
                                    val iDouble = iStr.toDoubleOrNull() ?: 0.0

                                    if (pDouble != null && pDouble > 0.0) {
                                        val tenureWeeks = 10
                                        val weeklyInstalment = (pDouble + iDouble) / tenureWeeks
                                        val loanNotes = translate("Bulk Disbursed", language)
                                        viewModel.createLoanCycle(
                                            context = context,
                                            customerId = customerId,
                                            amount = pDouble,
                                            interest = iDouble,
                                            weeklyInstalment = weeklyInstalment,
                                            tenureWeeks = tenureWeeks,
                                            notes = loanNotes,
                                            startDate = parsedTimestamp
                                        )
                                        loansCreated++
                                    }
                                }
                            }

                            if (paymentsRecorded > 0 || loansCreated > 0) {
                                val msg = when (language) {
                                    "Tamil" -> "தொகுதி பதிவு வெற்றிகரமாக சேமிக்கப்பட்டது! ($paymentsRecorded கட்டணங்கள், $loansCreated புதிய கடன்கள் பற்று போடப்பட்டது)"
                                    "Hindi" -> "बल्क प्रविष्टि सफलतापूर्वक सहेजी गई! ($paymentsRecorded भुगतान दर्ज किए गए, $loansCreated नए ऋण वितरित किए गए)"
                                    "Telugu" -> "బల్క్ ఎంట్రీ విజయవంతంగా సేవ్ చేయబడింది! ($paymentsRecorded చెల్లింపులు రికార్డ్ చేయబడ్డాయి, $loansCreated కొత్త రుణాలు పంపిణీ చేయబడ్డాయి)"
                                    else -> "Bulk entry saved successfully! ($paymentsRecorded payments recorded, $loansCreated new loans disbursed)"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                viewModel.navigateBack()
                            } else {
                                val msg = when (language) {
                                    "Tamil" -> "பதிவு செய்ய தேவையான விவரங்கள் எதுவும் உள்ளிடப்படவில்லை!"
                                    "Hindi" -> "कोई वैध भुगतान राशि या नई ऋण राशि दर्ज नहीं की गई!"
                                    "Telugu" -> "నమోదు చేయడానికి ఖచ్చితమైన వివరాలు ఏవీ నమోదు చేయబడలేదు!"
                                    else -> "No valid payment amounts or new loan amounts entered!"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = appColors.primaryAccent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(50.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = "Submit")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasChangesToTodayPayments) {
                                translate("UPDATE THE ENTRIES", language)
                            } else {
                                translate("RECORD BULK PAYMENTS", language)
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

data class BulkRowItem(
    val customer: Customer,
    val activeLoan: LoanCycle?,
    val defaultWeek: Int,
    val defaultAmount: Double,
    val todayPayment: WeeklyPayment? = null
)
