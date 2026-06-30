package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.LoanCycle
import com.example.data.WeeklyPayment
import java.text.SimpleDateFormat
import java.util.*

data class PaymentDetailItem(
    val customerName: String,
    val customerCode: String,
    val amount: Double,
    val weekNumber: Int,
    val notes: String,
    val upiTxnId: String?
)

data class DisbursalDetailItem(
    val customerName: String,
    val customerCode: String,
    val loanAmount: Double,
    val deduction: Double,
    val actualDisbursed: Double,
    val interestAmount: Double,
    val weeklyAmount: Double,
    val tenureWeeks: Int
)

data class ProfitDetailItem(
    val customerName: String,
    val customerCode: String,
    val source: String,
    val amount: Double,
    val details: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculationDetailScreen(
    type: String, // "COLLECTION", "DISBURSAL", "PROFIT"
    day: String,  // "Home", "Monday", "Tuesday", etc.
    viewModel: FinanceViewModel
) {
    val appColors = LocalAppThemeColors.current
    val language by viewModel.language.collectAsStateWithLifecycle()
    val allCustomers by viewModel.allCustomers.collectAsStateWithLifecycle()
    val allLoanCycles by viewModel.allLoanCycles.collectAsStateWithLifecycle()
    val allPayments by viewModel.allPayments.collectAsStateWithLifecycle()

    // Parse data based on start of today
    val startOfToday = remember {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        cal.timeInMillis
    }

    val isHome = remember(day) { day.equals("Home", ignoreCase = true) }

    // Computations
    val paymentsToday = remember(allPayments, startOfToday) {
        allPayments.filter { it.paymentDate >= startOfToday && it.status == "ACTIVE" }
    }

    val loansToday = remember(allLoanCycles, startOfToday) {
        allLoanCycles.filter { l -> l.startDate >= startOfToday && l.status != "DELETED" }
    }

    // Title and stats setup
    val screenTitle = when (type) {
        "COLLECTION" -> translate("Today's Collections", language)
        "DISBURSAL" -> translate("Today's Disbursals", language)
        else -> translate("Today's Profit", language)
    }

    val dashboardSubtitle = if (isHome) {
        translate("Global / All Groups", language)
    } else {
        translate("$day Collection Group", language)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = screenTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Text(
                            text = dashboardSubtitle,
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("calc_detail_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ColorSlateDark)
            )
        },
        containerColor = appColors.mainBg
    ) { innerPadding ->
        when (type) {
            "COLLECTION" -> {
                val filteredPayments = remember(paymentsToday, allCustomers, allLoanCycles, day, isHome) {
                    paymentsToday.filter { p ->
                        val l = allLoanCycles.find { it.id == p.loanCycleId } ?: return@filter false
                        val c = allCustomers.find { it.id == l.customerId } ?: return@filter false
                        isHome || c.collectionDay.trim().equals(day.trim(), ignoreCase = true)
                    }.map { p ->
                        val l = allLoanCycles.find { it.id == p.loanCycleId }!!
                        val c = allCustomers.find { it.id == l.customerId }!!
                        PaymentDetailItem(
                            customerName = c.name,
                            customerCode = c.customerCode,
                            amount = p.amountPaid,
                            weekNumber = p.weekNumber,
                            notes = p.notes,
                            upiTxnId = p.upiTxnId
                        )
                    }
                }

                val totalSum = filteredPayments.sumOf { it.amount }

                CollectionDetailLayout(
                    items = filteredPayments,
                    totalSum = totalSum,
                    padding = innerPadding,
                    appColors = appColors,
                    language = language
                )
            }
            "DISBURSAL" -> {
                val filteredLoans = remember(loansToday, allCustomers, day, isHome) {
                    loansToday.filter { l ->
                        val c = allCustomers.find { it.id == l.customerId } ?: return@filter false
                        isHome || c.collectionDay.trim().equals(day.trim(), ignoreCase = true)
                    }.map { l ->
                        val c = allCustomers.find { it.id == l.customerId }!!
                        DisbursalDetailItem(
                            customerName = c.name,
                            customerCode = c.customerCode,
                            loanAmount = l.loanAmount,
                            deduction = l.deduction,
                            actualDisbursed = l.loanAmount - l.deduction,
                            interestAmount = l.interestAmount,
                            weeklyAmount = l.weeklyAmount,
                            tenureWeeks = l.totalWeeks
                        )
                    }
                }

                val totalSum = filteredLoans.sumOf { it.actualDisbursed }

                DisbursalDetailLayout(
                    items = filteredLoans,
                    totalSum = totalSum,
                    padding = innerPadding,
                    appColors = appColors,
                    language = language
                )
            }
            else -> { // PROFIT
                val filteredProfitItems = remember(loansToday, paymentsToday, allCustomers, allLoanCycles, day, isHome) {
                    val profitDeductions = loansToday.filter { l ->
                        val c = allCustomers.find { it.id == l.customerId } ?: return@filter false
                        (isHome || c.collectionDay.trim().equals(day.trim(), ignoreCase = true)) && l.deduction > 0.0
                    }.map { l ->
                        val c = allCustomers.find { it.id == l.customerId }!!
                        ProfitDetailItem(
                            customerName = c.name,
                            customerCode = c.customerCode,
                            source = "Upfront Deduction",
                            amount = l.deduction,
                            details = "Upfront realized interest on ₹${l.loanAmount.toLong()} loan cycle"
                        )
                    }

                    val profitPayments = paymentsToday.filter { p ->
                        val l = allLoanCycles.find { it.id == p.loanCycleId } ?: return@filter false
                        val c = allCustomers.find { it.id == l.customerId } ?: return@filter false
                        isHome || c.collectionDay.trim().equals(day.trim(), ignoreCase = true)
                    }.map { p ->
                        val l = allLoanCycles.find { it.id == p.loanCycleId }!!
                        val c = allCustomers.find { it.id == l.customerId }!!
                        val total = l.loanAmount + l.interestAmount
                        val ratio = if (total > 0.0) l.interestAmount / total else 0.0
                        val interestPortion = p.amountPaid * ratio
                        ProfitDetailItem(
                            customerName = c.name,
                            customerCode = c.customerCode,
                            source = "Collected Interest Share",
                            amount = interestPortion,
                            details = "Interest component of ₹${p.amountPaid.toLong()} instalment payment"
                        )
                    }.filter { it.amount > 0.0 }

                    profitDeductions + profitPayments
                }

                val totalSum = filteredProfitItems.sumOf { it.amount }

                ProfitDetailLayout(
                    items = filteredProfitItems,
                    totalSum = totalSum,
                    padding = innerPadding,
                    appColors = appColors,
                    language = language
                )
            }
        }
    }
}

@Composable
fun CollectionDetailLayout(
    items: List<PaymentDetailItem>,
    totalSum: Double,
    padding: PaddingValues,
    appColors: AppThemeColors,
    language: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)
    ) {
        // Hero Card showing Total
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                        )
                    )
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = translate("TOTAL COLLECTIONS", language),
                        color = Color.LightGray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "₹ ${String.format(Locale.US, "%,.2f", totalSum)}",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 32.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF22C55E).copy(alpha = 0.15f),
                        border = RowBorderStroke(Color(0xFF22C55E))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = Color(0xFF22C55E),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${items.size} " + translate("Instalments Received", language),
                                color = Color(0xFF22C55E),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = translate("Instalment Log Breakdown", language),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = if (appColors.isDark) Color.White else Color.Black,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = translate("No collections made today.", language),
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (appColors.isDark) Color(0xFF1E293B) else Color.White
                        ),
                        border = RowBorderStroke(if (appColors.isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
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
                                    text = item.customerName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (appColors.isDark) Color.White else Color.Black
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        color = (if (appColors.isDark) Color(0xFF475569) else Color(0xFFF1F5F9)),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = item.customerCode,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                            color = if (appColors.isDark) Color.LightGray else Color.DarkGray
                                        )
                                    }
                                    Text(
                                        text = translate("Week", language) + " ${item.weekNumber}",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                                if (item.notes.isNotBlank() || !item.upiTxnId.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = if (!item.upiTxnId.isNullOrBlank()) "UPI ID: ${item.upiTxnId}" else item.notes,
                                        fontSize = 11.sp,
                                        color = if (appColors.isDark) Color.LightGray else Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Text(
                                text = "₹ ${String.format(Locale.US, "%,.0f", item.amount)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF22C55E)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DisbursalDetailLayout(
    items: List<DisbursalDetailItem>,
    totalSum: Double,
    padding: PaddingValues,
    appColors: AppThemeColors,
    language: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)
    ) {
        // Hero Card showing Total
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF1E1B4B), Color(0xFF312E81))
                        )
                    )
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = translate("TOTAL NET CASH DISBURSED", language),
                        color = Color.LightGray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "₹ ${String.format(Locale.US, "%,.2f", totalSum)}",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 32.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF43F5E).copy(alpha = 0.15f),
                        border = RowBorderStroke(Color(0xFFF43F5E))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TrendingDown,
                                contentDescription = null,
                                tint = Color(0xFFF43F5E),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${items.size} " + translate("Contracts Disbursed", language),
                                color = Color(0xFFF43F5E),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = translate("Disbursed Loans Breakdown", language),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = if (appColors.isDark) Color.White else Color.Black,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = translate("No disbursals made today.", language),
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (appColors.isDark) Color(0xFF1E293B) else Color.White
                        ),
                        border = RowBorderStroke(if (appColors.isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.customerName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (appColors.isDark) Color.White else Color.Black
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Surface(
                                        color = (if (appColors.isDark) Color(0xFF475569) else Color(0xFFF1F5F9)),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.wrapContentSize()
                                    ) {
                                        Text(
                                            text = item.customerCode,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                            color = if (appColors.isDark) Color.LightGray else Color.DarkGray
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "₹ ${String.format(Locale.US, "%,.0f", item.actualDisbursed)}",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        color = Color(0xFFEF4444)
                                    )
                                    Text(
                                        text = translate("Net Disbursed", language),
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = if (appColors.isDark) Color(0xFF334155) else Color(0xFFF1F5F9))
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(translate("Loan Principal", language), fontSize = 11.sp, color = Color.Gray)
                                    Text("₹ ${String.format(Locale.US, "%,.0f", item.loanAmount)}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (appColors.isDark) Color.White else Color.Black)
                                }
                                Column {
                                    Text(translate("Interest", language), fontSize = 11.sp, color = Color.Gray)
                                    Text("₹ ${String.format(Locale.US, "%,.0f", item.interestAmount)}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (appColors.isDark) Color.White else Color.Black)
                                }
                                Column {
                                    Text(translate("Deduction", language), fontSize = 11.sp, color = Color.Gray)
                                    Text("₹ ${String.format(Locale.US, "%,.0f", item.deduction)}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (appColors.isDark) Color.White else Color.Black)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(translate("Weekly Due", language), fontSize = 11.sp, color = Color.Gray)
                                    Text("₹ ${String.format(Locale.US, "%,.0f", item.weeklyAmount)} /wk", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (appColors.isDark) Color.White else Color.Black)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfitDetailLayout(
    items: List<ProfitDetailItem>,
    totalSum: Double,
    padding: PaddingValues,
    appColors: AppThemeColors,
    language: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)
    ) {
        // Hero Card showing Total
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF581C87), Color(0xFF6B21A8))
                        )
                    )
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = translate("TOTAL REALIZED PROFIT", language),
                        color = Color.LightGray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "₹ ${String.format(Locale.US, "%,.2f", totalSum)}",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 32.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFA855F7).copy(alpha = 0.15f),
                        border = RowBorderStroke(Color(0xFFA855F7))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Percent,
                                contentDescription = null,
                                tint = Color(0xFFA855F7),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${items.size} " + translate("Profit Elements Today", language),
                                color = Color(0xFFA855F7),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = translate("Profit Generation Log", language),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = if (appColors.isDark) Color.White else Color.Black,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.MonetizationOn,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = translate("No profit recorded today.", language),
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (appColors.isDark) Color(0xFF1E293B) else Color.White
                        ),
                        border = RowBorderStroke(if (appColors.isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = item.customerName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (appColors.isDark) Color.White else Color.Black
                                    )
                                    Surface(
                                        color = if (item.source.contains("Deduction")) Color(0xFFA855F7).copy(alpha = 0.15f) else Color(0xFF22C55E).copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = translate(item.source, language),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (item.source.contains("Deduction")) Color(0xFFA855F7) else Color(0xFF22C55E),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        color = (if (appColors.isDark) Color(0xFF475569) else Color(0xFFF1F5F9)),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = item.customerCode,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                            color = if (appColors.isDark) Color.LightGray else Color.DarkGray
                                        )
                                    }
                                    Text(
                                        text = item.details,
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Text(
                                text = "₹ ${String.format(Locale.US, "%,.1f", item.amount)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFFA855F7)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RowBorderStroke(color: Color) = BorderStroke(1.dp, color.copy(alpha = 0.3f))
