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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AddLoanScreen(
    customerId: Int,
    viewModel: FinanceViewModel
) {
    val appColors = LocalAppThemeColors.current
    var loanAmount by remember { mutableStateOf("") }
    var interestAmount by remember { mutableStateOf("") }
    var deductionAmount by remember { mutableStateOf("") }
    var weeklyInstalment by remember { mutableStateOf("") }
    var tenureWeeks by remember { mutableStateOf("10") }
    var notes by remember { mutableStateOf("") }
    var disbursalMode by remember { mutableStateOf("Cash") }
    var isWeeklyInstalmentManuallyEdited by remember { mutableStateOf(false) }

    var loanAmountError by remember { mutableStateOf<String?>(null) }
    var interestError by remember { mutableStateOf<String?>(null) }
    var deductionError by remember { mutableStateOf<String?>(null) }
    var instalmentError by remember { mutableStateOf<String?>(null) }
    var tenureError by remember { mutableStateOf<String?>(null) }

    var loanTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    var isTimeSynced by remember { mutableStateOf(false) }
    var isSyncingTime by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val (onlineTime, synced) = com.example.util.OnlineTimeHelper.getOnlineTimeOrLocal()
        loanTimestamp = onlineTime
        isTimeSynced = synced
        isSyncingTime = false
    }

    val context = LocalContext.current

    val datePickerDialog = remember(loanTimestamp) {
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = loanTimestamp
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                loanTimestamp = calendar.timeInMillis
            },
            Calendar.getInstance().apply { timeInMillis = loanTimestamp }.get(Calendar.YEAR),
            Calendar.getInstance().apply { timeInMillis = loanTimestamp }.get(Calendar.MONTH),
            Calendar.getInstance().apply { timeInMillis = loanTimestamp }.get(Calendar.DAY_OF_MONTH)
        )
    }

    val timePickerDialog = remember(loanTimestamp) {
        android.app.TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = loanTimestamp
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                }
                loanTimestamp = calendar.timeInMillis
            },
            Calendar.getInstance().apply { timeInMillis = loanTimestamp }.get(Calendar.HOUR_OF_DAY),
            Calendar.getInstance().apply { timeInMillis = loanTimestamp }.get(Calendar.MINUTE),
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
            Text("Give Loan: Create New Cycle", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ColorSlateDark)

            OutlinedTextField(
                value = loanAmount,
                onValueChange = { input -> 
                    val filtered = input.filter { it.isDigit() }
                    loanAmount = filtered
                    loanAmountError = null
                    if (!isWeeklyInstalmentManuallyEdited) {
                        val p = filtered.toDoubleOrNull() ?: 0.0
                        val w = tenureWeeks.toIntOrNull() ?: 0
                        if (w > 0) {
                            weeklyInstalment = Math.round(p / w).toString()
                        } else {
                            weeklyInstalment = ""
                        }
                    }
                },
                label = { Text("Loan Principal (₹)") },
                placeholder = { Text("E.g., 10000") },
                isError = loanAmountError != null,
                supportingText = {
                    if (loanAmountError != null) {
                        Text(loanAmountError!!, color = MaterialTheme.colorScheme.error)
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
                    .testTag("loan_principal_input")
            )

            OutlinedTextField(
                value = interestAmount,
                onValueChange = { input -> 
                    val filtered = input.filter { it.isDigit() }
                    interestAmount = filtered
                    interestError = null
                },
                label = { Text("Interest (₹)") },
                placeholder = { Text("E.g., 2000 (Set 0 if none)") },
                isError = interestError != null,
                supportingText = {
                    if (interestError != null) {
                        Text(interestError!!, color = MaterialTheme.colorScheme.error)
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
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = deductionAmount,
                onValueChange = { input -> 
                    val filtered = input.filter { it.isDigit() }
                    deductionAmount = filtered
                    deductionError = null
                },
                label = { Text("Deduction (Realized Profit) (₹)") },
                placeholder = { Text("E.g., 500 (Set 0 if none)") },
                isError = deductionError != null,
                supportingText = {
                    if (deductionError != null) {
                        Text(deductionError!!, color = MaterialTheme.colorScheme.error)
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
                modifier = Modifier.fillMaxWidth().testTag("loan_deduction_input")
            )

            OutlinedTextField(
                value = weeklyInstalment,
                onValueChange = { input -> 
                    val filtered = input.filter { it.isDigit() }
                    weeklyInstalment = filtered
                    instalmentError = null
                    isWeeklyInstalmentManuallyEdited = true
                },
                label = { Text("Per week collection amount (₹)") },
                placeholder = { Text("E.g., 1200") },
                isError = instalmentError != null,
                supportingText = {
                    if (instalmentError != null) {
                        Text(instalmentError!!, color = MaterialTheme.colorScheme.error)
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(8.dp), singleLine = true,
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
                    .testTag("loan_weekly_instalment_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = tenureWeeks,
                    onValueChange = { input -> 
                        val filtered = input.filter { it.isDigit() }
                        tenureWeeks = filtered
                        tenureError = null
                        if (!isWeeklyInstalmentManuallyEdited) {
                            val p = loanAmount.toDoubleOrNull() ?: 0.0
                            val w = filtered.toIntOrNull() ?: 0
                            if (w > 0) {
                                weeklyInstalment = Math.round(p / w).toString()
                            } else {
                                weeklyInstalment = ""
                            }
                        }
                    },
                    label = { Text("Weeks tenure") },
                    isError = tenureError != null,
                    supportingText = {
                        if (tenureError != null) {
                            Text(tenureError!!, color = MaterialTheme.colorScheme.error)
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
                    modifier = Modifier.weight(1.0f)
                )

                Column(
                    modifier = Modifier.weight(1.0f)
                ) {
                    Text(
                        text = "Disbursal Mode",
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
                                color = if (disbursalMode == "Cash") Color(0xFFDCFCE7) else Color(0xFFDBEAFE),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (disbursalMode == "Cash") Color(0xFF16A34A) else Color(0xFF2563EB),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                disbursalMode = if (disbursalMode == "Cash") "Online" else "Cash"
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = disbursalMode.uppercase(Locale.getDefault()),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = if (disbursalMode == "Cash") Color(0xFF15803D) else Color(0xFF1D4ED8)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Remarks (Optional)") },
                placeholder = { Text("Remarks details") },
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
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = Color(0xFFE2E8F0))

            // 📅 Date & Time Selection side-by-side boxes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Disbursal Date & Time",
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
                                        loanTimestamp = onlineTime
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
                        .border(1.dp, ColorSlateDark.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .clickable { datePickerDialog.show() }
                        .padding(12.dp)
                ) {
                    Column {
                        Text("Date", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(loanTimestamp)),
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
                        .border(1.dp, ColorAccentBlue.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .clickable { timePickerDialog.show() }
                        .padding(12.dp)
                ) {
                    Column {
                        Text("Time", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(loanTimestamp)).uppercase(Locale.getDefault()),
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
                    text = "Disburse Loan",
                    onClick = {
                        val p = loanAmount.toDoubleOrNull()
                        val interestVal = interestAmount.toDoubleOrNull() ?: 0.0
                        val deductionVal = deductionAmount.toDoubleOrNull() ?: 0.0
                        val t = tenureWeeks.toIntOrNull()
                        val w = if (weeklyInstalment.isBlank()) {
                            if (p != null && t != null && t > 0) ((p + interestVal) / t) else 0.0
                        } else {
                            weeklyInstalment.toDoubleOrNull()
                        }

                        var hasError = false
                        
                        if (p == null || p <= 0.0) {
                            loanAmountError = "Loan Principal must be greater than 0"
                            hasError = true
                        } else {
                            loanAmountError = null
                        }

                        if (interestAmount.isNotBlank() && (interestAmount.toDoubleOrNull() == null || interestVal < 0.0)) {
                            interestError = "Interest Fee must be greater than or equal to 0"
                            hasError = true
                        } else {
                            interestError = null
                        }

                        if (deductionAmount.isNotBlank() && (deductionAmount.toDoubleOrNull() == null || deductionVal < 0.0)) {
                            deductionError = "Deduction must be greater than or equal to 0"
                            hasError = true
                        } else {
                            deductionError = null
                        }

                        if (t == null || t <= 0) {
                            tenureError = "Tenure must be greater than 0"
                            hasError = true
                        } else {
                            tenureError = null
                        }

                        if (w == null || w <= 0.0) {
                            instalmentError = "Weekly target installment must be greater than 0"
                            hasError = true
                        } else {
                            instalmentError = null
                        }

                        if (!hasError) {
                            viewModel.createLoanCycle(
                                context = context,
                                customerId = customerId,
                                amount = p!!,
                                interest = interestVal,
                                weeklyInstalment = w!!,
                                tenureWeeks = t!!,
                                notes = if (disbursalMode == "Online") "Online - $notes" else notes,
                                startDate = loanTimestamp,
                                deduction = deductionVal
                            )
                            viewModel.navigateBack()
                        }
                    },
                    color = appColors.primaryAccent,
                    contentColor = Color.White,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_loan_button")
                )
            }
        }
    }
}
