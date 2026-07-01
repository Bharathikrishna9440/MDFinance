package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.util.CurrencyFormatter

import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RecordPaymentScreen(
    loanCycleId: Int,
    viewModel: FinanceViewModel
) {
    val activeLoans by viewModel.allLoanCycles.collectAsStateWithLifecycle()
    val allPayments by viewModel.allPayments.collectAsStateWithLifecycle()
    
    val loan = activeLoans.find { it.id == loanCycleId }
    if (loan == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Active subscription not found.")
        }
        return
    }

    val customerList by viewModel.allCustomers.collectAsStateWithLifecycle()
    val customer = customerList.find { it.id == loan.customerId }

    val paymentsForCycle = allPayments.filter { it.loanCycleId == loanCycleId && it.status == "ACTIVE" && it.amountPaid > 0.0 && it.weekNumber > 0 }
    val nextSuggestWeek = (paymentsForCycle.maxOfOrNull { it.weekNumber } ?: 0) + 1

    var amount by remember { mutableStateOf(loan.weeklyAmount.toLong().toString()) }
    var isAmountTouched by remember { mutableStateOf(false) }
    var amountErrorText by remember { mutableStateOf<String?>(null) }
    var weekStr by remember { mutableStateOf(nextSuggestWeek.toString()) }
    
    // Notes represents Mode of Payment (defaults to Cash)
    var notes by remember { mutableStateOf("Cash") }
    val context = LocalContext.current
    val appColors = LocalAppThemeColors.current

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    var paymentDateStr by remember { mutableStateOf(sdf.format(Date())) }
    var isTimeSynced by remember { mutableStateOf(false) }
    var isSyncingTime by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val (onlineTime, synced) = com.example.util.OnlineTimeHelper.getOnlineTimeOrLocal()
        paymentDateStr = sdf.format(Date(onlineTime))
        isTimeSynced = synced
        isSyncingTime = false
    }

    // Dialog state for native picker
    val parsedTimestamp = try {
        sdf.parse(paymentDateStr)?.time ?: System.currentTimeMillis()
    } catch (e: Exception) {
        System.currentTimeMillis()
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
                paymentDateStr = sdf.format(Date(calendar.timeInMillis))
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
                paymentDateStr = sdf.format(Date(calendar.timeInMillis))
            },
            Calendar.getInstance().apply { timeInMillis = parsedTimestamp }.get(Calendar.HOUR_OF_DAY),
            Calendar.getInstance().apply { timeInMillis = parsedTimestamp }.get(Calendar.MINUTE),
            false
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Collect Instalment from ${customer?.name ?: "Client"}",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = ColorSlateDark
            )

            Text(
                text = "Total Contract: ₹${loan.loanAmount + loan.interestAmount}  | Paid: ₹${loan.paidAmount}",
                fontSize = 13.sp,
                color = Color.Gray
            )

            OutlinedTextField(
                value = amount,
                onValueChange = { input -> 
                    val filtered = input.filter { it.isDigit() }
                    amount = filtered
                    isAmountTouched = true
                    amountErrorText = null
                },
                label = { Text("Amount Collected (₹)") },
                isError = amountErrorText != null,
                supportingText = {
                    if (amountErrorText != null) {
                        Text(amountErrorText!!, color = MaterialTheme.colorScheme.error)
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedLabelColor = Color.Black,
                    unfocusedLabelColor = Color.Black,
                    focusedPlaceholderColor = Color.DarkGray,
                    unfocusedPlaceholderColor = Color.DarkGray,
                    focusedBorderColor = ColorSlateDark,
                    unfocusedBorderColor = Color(0xFFCBD5E1)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("payment_amount_input")
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused && !isAmountTouched && amount == loan.weeklyAmount.toLong().toString()) {
                            amount = ""
                            isAmountTouched = true
                        }
                    }
            )

            // Side-by-side Row containing Week Number & Mode of Collection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = weekStr,
                    onValueChange = { input -> 
                        val filtered = input.filter { it.isDigit() }
                        weekStr = filtered
                    },
                    label = { Text("Week Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedLabelColor = Color.Black,
                        unfocusedLabelColor = Color.Black,
                        focusedPlaceholderColor = Color.DarkGray,
                        unfocusedPlaceholderColor = Color.DarkGray,
                        focusedBorderColor = ColorSlateDark,
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier.weight(1.2f)
                )

                // Mode of Collection Box
                Column(
                    modifier = Modifier.weight(1.0f)
                ) {
                    Text(
                        text = "Mode of Payment",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(
                                color = if (notes == "Cash") Color(0xFFDCFCE7) else Color(0xFFDBEAFE),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (notes == "Cash") Color(0xFF16A34A) else Color(0xFF2563EB),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                notes = if (notes == "Cash") "Online" else "Cash"
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = notes.uppercase(Locale.getDefault()),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = if (notes == "Cash") Color(0xFF15803D) else Color(0xFF1D4ED8)
                        )
                    }
                }
            }

            // Shortcuts Row for easy dates offset selection
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

                shortcuts.forEach { (label, timestamp) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                            .clickable {
                                paymentDateStr = sdf.format(Date(timestamp))
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = ColorSlateDark)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = Color(0xFFE2E8F0))

            // 📅 side-by-side boxes for Collection Date and Collection Time with nice tactile effect
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Collection Date & Time",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorSlateDark
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
                                        paymentDateStr = sdf.format(Date(onlineTime))
                                        isTimeSynced = synced
                                        isSyncingTime = false
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "⚠️ OFFLINE (TAP TO SYNC)",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100)
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Date Box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(ColorSlateDark.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                        .border(1.5.dp, ColorSlateDark.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .clickable { datePickerDialog.show() }
                        .padding(12.dp)
                ) {
                    Column {
                        Text("Date", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(parsedTimestamp)),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorSlateDark
                        )
                    }
                }

                // Time Box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(ColorAccentBlue.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                        .border(1.5.dp, ColorAccentBlue.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .clickable { timePickerDialog.show() }
                        .padding(12.dp)
                ) {
                    Column {
                        Text("Time", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(parsedTimestamp)).uppercase(Locale.getDefault()),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorAccentBlue
                        )
                    }
                }
            }
        }

        Surface(
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.navigateBack() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorLossRed),
                    border = BorderStroke(1.dp, ColorLossRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancel")
                }

                ShiningButton(
                    text = "Record Payment",
                    onClick = {
                        val a = amount.toDoubleOrNull()
                        val w = weekStr.toIntOrNull() ?: nextSuggestWeek
                        val parsedDate = try {
                            sdf.parse(paymentDateStr)?.time ?: System.currentTimeMillis()
                        } catch (e: Exception) {
                            System.currentTimeMillis()
                        }

                        val totalDue = loan.loanAmount + loan.interestAmount
                        val outstanding = maxOf(0.0, totalDue - loan.paidAmount)

                        if (a == null) {
                            amountErrorText = "Please enter a valid amount."
                        } else if (a <= 0.0) {
                            amountErrorText = "Amount must be greater than zero."
                        } else if (a > outstanding) {
                            amountErrorText = "Amount exceeds outstanding balance (₹${CurrencyFormatter.format(outstanding)})"
                        } else {
                            amountErrorText = null
                            viewModel.recordWeeklyPayment(
                                context = context,
                                loanCycleId = loan.id,
                                amount = a,
                                weekNum = w,
                                notes = notes,
                                paymentDate = parsedDate
                            )
                            viewModel.navigateBack()
                        }
                    },
                    color = appColors.primaryAccent,
                    contentColor = Color.White,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_payment_button")
                )
            }
        }
    }
}
